package io.github.sekirankumo.fitlog.background

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.room.withTransaction
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.FavoriteFood
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.db.OpLog
import io.github.sekirankumo.fitlog.data.db.PlanDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class BackupFile(
    val version: Int = 1,
    val exportedAt: Long,
    val bodyRecords: List<BodyRecord> = emptyList(),
    val foodEntries: List<FoodEntry> = emptyList(),
    val favorites: List<FavoriteFood> = emptyList(),
    val planDays: List<PlanDay> = emptyList(),
    val kv: List<KeyValue> = emptyList(),
    val opLogs: List<OpLog> = emptyList(),
)

/**
 * 备份：导出 / 导入全部数据（不含 API Key）。
 * 自动备份写到「下载/薄肌来」文件夹，卸载 App 也不会丢，保留最近 7 份。
 */
class BackupService(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportJson(): String {
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            bodyRecords = db.bodyRecordDao().all(),
            foodEntries = db.foodDao().all(),
            favorites = db.foodDao().favorites(),
            planDays = db.planDao().all(),
            kv = db.kvDao().all(),
            opLogs = db.opLogDao().all(),
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = exportJson()
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("无法写入文件")
    }

    /** 用备份文件替换现有全部数据 */
    suspend fun importFrom(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("无法读取文件")
        val file = json.decodeFromString(BackupFile.serializer(), text)
        db.withTransaction {
            db.bodyRecordDao().clear()
            db.foodDao().clear()
            db.foodDao().clearFavorites()
            db.planDao().clear()
            db.kvDao().clear()
            db.opLogDao().clear()
            db.bodyRecordDao().insertAll(file.bodyRecords)
            db.foodDao().insertAll(file.foodEntries)
            file.favorites.forEach { db.foodDao().insertFavorite(it) }
            db.planDao().upsertAll(file.planDays)
            file.kv.forEach { db.kvDao().put(it) }
            db.opLogDao().insertAll(file.opLogs)
        }
        file
    }

    /** 每天最多自动备份一次；force 为 true 时（比如安装更新前）总是备份 */
    suspend fun autoBackup(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        if (!force && db.kvDao().get(KEY_LAST) == today) return@withContext false
        val name = "薄肌来-备份-${today.replace("-", "")}.json"
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
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
        }) ?: return@withContext false
        resolver.openOutputStream(uri, "wt")?.use { it.write(exportJson().toByteArray()) }
        db.kvDao().put(KeyValue(KEY_LAST, today))
        prune()
        true
    }

    private fun prune() {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val ids = mutableListOf<Long>()
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE '薄肌来-备份-%'",
            arrayOf(RELATIVE_PATH),
            "${MediaStore.MediaColumns.DISPLAY_NAME} DESC",
        )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
        ids.drop(KEEP).forEach { id ->
            runCatching { resolver.delete(ContentUris.withAppendedId(collection, id), null, null) }
        }
    }

    private companion object {
        const val KEY_LAST = "last_auto_backup"
        const val KEEP = 7
        val RELATIVE_PATH = Environment.DIRECTORY_DOWNLOADS + "/薄肌来/"
    }
}
