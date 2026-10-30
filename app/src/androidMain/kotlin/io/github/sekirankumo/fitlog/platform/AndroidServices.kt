package io.github.sekirankumo.fitlog.platform

import android.app.UiModeManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.sekirankumo.fitlog.PlatformDeps
import io.github.sekirankumo.fitlog.background.AndroidHealthSync
import io.github.sekirankumo.fitlog.background.Notifications
import io.github.sekirankumo.fitlog.background.PlanGenerateWorker
import io.github.sekirankumo.fitlog.background.WorkScheduler
import io.github.sekirankumo.fitlog.data.ThemeMode
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.data.db.MIGRATION_SQL
import io.github.sekirankumo.fitlog.data.db.SqlMigration
import io.github.sekirankumo.fitlog.data.db.SqlRoomMigration
import io.github.sekirankumo.fitlog.update.AndroidUpdateInstaller
import io.github.sekirankumo.fitlog.voice.SherpaSpeechEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.aiDataStore by preferencesDataStore(name = "ai_settings")

/** 安卓的迁移：Room 在安卓上用系统 SQLite（旧接口），两种写法都实现 */
private class AndroidSqlMigration(sql: SqlMigration) : SqlRoomMigration(sql) {
    override fun migrate(db: SupportSQLiteDatabase) {
        sql.statements.forEach { db.execSQL(it) }
    }
}

/** name 只在测试里换 */
fun createDatabase(context: Context, name: String = AppDatabase.FILE_NAME): AppDatabase =
    Room.databaseBuilder<AppDatabase>(context, context.getDatabasePath(name).absolutePath)
        .addMigrations(*MIGRATION_SQL.map { AndroidSqlMigration(it) }.toTypedArray())
        .build()

/** 安卓上的全部平台实现 */
fun androidPlatformDeps(context: Context): PlatformDeps {
    val app = context.applicationContext
    AndroidPlatform.context = app
    val okHttp = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
    val http = HttpClient(OkHttp) {
        engine { preconfigured = okHttp }
    }
    return PlatformDeps(
        database = createDatabase(app),
        aiSettingsStore = app.aiDataStore,
        prefs = SharedPrefs(app),
        httpClient = http,
        notifier = object : Notifier {
            override fun show(id: Int, channel: String, title: String, text: String, open: String) =
                Notifications.show(app, id, channel, title, text, open)
        },
        reminders = object : ReminderScheduler {
            override fun schedule(minuteOfDay: Int?) = WorkScheduler.scheduleReminder(app, minuteOfDay)
        },
        planJobs = WorkManagerPlanJobs(app),
        backupStore = DownloadsBackupStore(app),
        healthSync = { bodyDao, kv -> AndroidHealthSync(app, bodyDao, kv) },
        speech = SherpaSpeechEngine(app, okHttp),
        updateInstaller = AndroidUpdateInstaller(app, okHttp),
        onThemeChanged = { applySystemNightMode(app, it) },
    )
}

private class SharedPrefs(context: Context) : SyncPrefs {
    private val sp = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    override fun getString(key: String): String? = sp.getString(key, null)
    override fun putString(key: String, value: String) = sp.edit().putString(key, value).apply()
    override fun getBoolean(key: String, default: Boolean): Boolean = sp.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = sp.edit().putBoolean(key, value).apply()
}

/**
 * 安卓 12 起把选择也告诉系统：启动画面、系统的选日期 / 时间对话框也跟着用同一套颜色。
 * 系统会自己记住，下次打开不用再设。
 */
private fun applySystemNightMode(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val ui = context.getSystemService(UiModeManager::class.java) ?: return
    runCatching {
        ui.setApplicationNightMode(
            when (mode) {
                ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }
}

/** 生成训练计划交给 WorkManager：切出 App 也会继续 */
private class WorkManagerPlanJobs(private val context: Context) : PlanJobs {
    override fun enqueue(args: String): String = PlanGenerateWorker.enqueue(context, args).toString()

    override val current: Flow<PlanJob?> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PlanGenerateWorker.UNIQUE).map { infos ->
            infos.firstOrNull()?.let { info ->
                PlanJob(
                    id = info.id.toString(),
                    status = when (info.state) {
                        WorkInfo.State.SUCCEEDED -> JobStatus.Succeeded
                        WorkInfo.State.FAILED -> JobStatus.Failed
                        WorkInfo.State.CANCELLED -> JobStatus.Cancelled
                        else -> JobStatus.Running
                    },
                    error = info.outputData.getString(PlanGenerateWorker.KEY_ERROR),
                )
            }
        }
}

/** 自动备份写到「下载/薄肌来」文件夹，卸载 App 也不会丢 */
private class DownloadsBackupStore(private val context: Context) : BackupStore {
    private val resolver get() = context.contentResolver
    private val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    override suspend fun write(name: String, text: String): Boolean {
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(name, RELATIVE_PATH),
            null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null }
        val uri = existing ?: resolver.insert(collection, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
        }) ?: return false
        resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } ?: return false
        return true
    }

    override suspend fun prune(keep: Int) {
        val ids = mutableListOf<Long>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '薄肌来-备份-%'",
            arrayOf(RELATIVE_PATH),
            "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        ids.drop(keep).forEach { id ->
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }

    private companion object {
        val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/薄肌来/"
    }
}
