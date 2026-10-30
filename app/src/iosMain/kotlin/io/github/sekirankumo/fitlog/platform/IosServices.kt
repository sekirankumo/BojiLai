package io.github.sekirankumo.fitlog.platform

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.PlatformDeps
import io.github.sekirankumo.fitlog.background.AppleHealthSync
import io.github.sekirankumo.fitlog.data.ThemeMode
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.MIGRATION_SQL
import io.github.sekirankumo.fitlog.data.db.SqlRoomMigration
import io.github.sekirankumo.fitlog.ios.IosPlatform
import io.github.sekirankumo.fitlog.update.UpdateInfo
import io.github.sekirankumo.fitlog.update.UpdateInstaller
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.now
import io.github.sekirankumo.fitlog.util.nowMillis
import io.github.sekirankumo.fitlog.util.plusDays
import io.github.sekirankumo.fitlog.util.toEpochDay
import io.github.sekirankumo.fitlog.voice.IosSpeechEngine
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToFile
import kotlin.uuid.Uuid

private val bridge get() = IosPlatform.bridge

private fun dir(kind: ULong): String {
    val path = NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).first() as String
    NSFileManager.defaultManager.createDirectoryAtPath(path, true, null, null)
    return path
}

/** 数据库、AI 设置放在 Application Support（不会出现在「文件」App 里） */
private val appSupportDir: String get() = dir(NSApplicationSupportDirectory)

/** Documents 会在「文件」App 里显示为「我的 iPhone › 薄肌来」，只放自动备份 */
private val documentsDir: String get() = dir(NSDocumentDirectory)

private fun join(dir: String, name: String): String = (dir as NSString).stringByAppendingPathComponent(name)

/** iOS 上的全部平台实现；container 建好后再交给需要它的服务 */
fun iosPlatformDeps(containerRef: () -> AppContainer): PlatformDeps {
    val database = Room.databaseBuilder<AppDatabase>(name = join(appSupportDir, AppDatabase.FILE_NAME))
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*MIGRATION_SQL.map { SqlRoomMigration(it) }.toTypedArray())
        .build()
    val http = HttpClient(Darwin) {
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 180_000
        }
    }
    return PlatformDeps(
        database = database,
        aiSettingsStore = PreferenceDataStoreFactory.createWithPath(
            produceFile = { join(appSupportDir, "ai_settings.preferences_pb").toPath() },
        ),
        prefs = UserDefaultsPrefs(),
        httpClient = http,
        notifier = object : Notifier {
            override fun show(id: Int, channel: String, title: String, text: String, open: String) =
                bridge.showNotification("n$id", title, text, open)
        },
        reminders = IosReminders(containerRef),
        planJobs = InAppPlanJobs(containerRef),
        backupStore = DocumentsBackupStore(),
        healthSync = { bodyDao, kv -> AppleHealthSync(bodyDao, kv) },
        speech = IosSpeechEngine(),
        updateInstaller = IosUpdateInstaller(),
        onThemeChanged = { bridge.setInterfaceStyle(it.ordinal) },
    )
}

private class UserDefaultsPrefs : SyncPrefs {
    private val d = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = d.stringForKey(key)
    override fun putString(key: String, value: String) = d.setObject(value, key)
    override fun getBoolean(key: String, default: Boolean): Boolean =
        if (d.objectForKey(key) == null) default else d.boolForKey(key)

    override fun putBoolean(key: String, value: Boolean) = d.setBool(value, key)
}

/**
 * 训练提醒：iOS 不能像安卓那样到点再查计划，所以提前把接下来两周有训练的日子排好本地通知，
 * 每次打开 App 和改提醒时间时重新排。
 */
class IosReminders(private val containerRef: () -> AppContainer) : ReminderScheduler {
    override fun schedule(minuteOfDay: Int?) {
        val container = containerRef()
        container.appScope.launch {
            // 先取消旧的再排新的
            suspendCancellableCoroutine { cont -> bridge.cancelNotifications(PREFIX) { cont.resume(Unit) } }
            if (minuteOfDay == null) return@launch
            val today = LocalDate.now()
            val days = runCatching { container.plans.range(today, today.plusDays(DAYS_AHEAD)) }.getOrDefault(emptyList())
            val now = nowMillis()
            for (day in days) {
                if (day.status != DayStatus.PLANNED) continue
                val date = LocalDate.fromEpochDays(day.date)
                val at = TimeUtil.toEpoch(date.atTime(minuteOfDay / 60, minuteOfDay % 60))
                if (at <= now) continue
                val count = container.plans.exercisesOf(day).size
                bridge.scheduleNotification(
                    "$PREFIX${date.toEpochDay()}",
                    "今天要练：${day.title}",
                    "共 $count 个动作，练完记得打卡",
                    "training",
                    at.toDouble(),
                )
            }
        }
    }

    private companion object {
        const val PREFIX = "reminder-"
        const val DAYS_AHEAD = 14
    }
}

/**
 * 生成训练计划：在 App 里直接跑。切到后台后 iOS 只再给大约 30 秒，
 * 所以界面上会提示生成时留在 App 里；完成或失败都会发通知。
 */
private class InAppPlanJobs(private val containerRef: () -> AppContainer) : PlanJobs {
    override val current = MutableStateFlow<PlanJob?>(null)

    override fun enqueue(args: String): String {
        val id = Uuid.random().toString()
        val container = containerRef()
        current.value = PlanJob(id, JobStatus.Running)
        container.appScope.launch {
            val task = bridge.beginBackgroundTask()
            try {
                val json = runCatching { Json.parseToJsonElement(args).jsonObject }.getOrNull() ?: error("参数错误")
                val tool = container.tools["generate_training_plan"] ?: error("找不到生成工具")
                val (outcome, _) = container.operations.runWrite(tool, json)
                container.notifier.show(1003, Notifier.CHANNEL_PLAN, "训练计划生成好了", outcome.result, "training")
                current.value = PlanJob(id, JobStatus.Succeeded)
                container.reminders.schedule(container.profile.current().reminderMinuteOfDay)
            } catch (e: CancellationException) {
                current.value = PlanJob(id, JobStatus.Cancelled)
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: e.toString()
                container.notifier.show(1003, Notifier.CHANNEL_PLAN, "训练计划生成失败", msg, "generate_plan")
                current.value = PlanJob(id, JobStatus.Failed, msg)
            } finally {
                bridge.endBackgroundTask(task)
            }
        }
        return id
    }
}

/** 自动备份写到 App 的 Documents（「文件」App 里的「我的 iPhone › 薄肌来」） */
private class DocumentsBackupStore : BackupStore {
    override suspend fun write(name: String, text: String): Boolean =
        (text as NSString).writeToFile(join(documentsDir, name), true, NSUTF8StringEncoding, null)

    override suspend fun prune(keep: Int) {
        val fm = NSFileManager.defaultManager
        val names = fm.contentsOfDirectoryAtPath(documentsDir, null).orEmpty()
            .map { it as String }
            .filter { it.startsWith("薄肌来-备份-") }
            .sortedDescending()
        names.drop(keep).forEach { fm.removeItemAtPath(join(documentsDir, it), null) }
    }
}

/** iOS 不允许 App 自己安装，打开发布页面的安装包下载地址 */
private class IosUpdateInstaller : UpdateInstaller {
    override val assetSuffix = ".ipa"
    override fun canInstall(): Boolean = true
    override fun requestInstallPermission() = Unit
    override suspend fun install(info: UpdateInfo, onProgress: (Float) -> Unit): Boolean {
        val url = info.browserUrl.ifEmpty { "https://github.com/sekirankumo/BojiLai/releases/latest" }
        bridge.openUrl(url)
        return false
    }
}
