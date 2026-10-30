package io.github.sekirankumo.fitlog.background

import kotlinx.datetime.*
import io.github.sekirankumo.fitlog.util.*
import io.github.sekirankumo.fitlog.data.db.NutritionDay
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.sekirankumo.fitlog.platform.BackupStore
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.FavoriteFood
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.db.OpLog
import io.github.sekirankumo.fitlog.data.db.PlanDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
    /** 0.1.23 起：每天的营养目标（旧备份没有这一项） */
    val nutritionDays: List<NutritionDay> = emptyList(),
)

/**
 * 备份：导出 / 导入全部数据（不含 API Key）。
 * 自动备份写到「下载/薄肌来」文件夹（iOS 是「文件」App 里的薄肌来文件夹），卸载 App 也不会丢，保留最近 7 份。
 */
class BackupService(private val store: BackupStore, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportJson(): String {
        val file = BackupFile(
            exportedAt = nowMillis(),
            bodyRecords = db.bodyRecordDao().all(),
            foodEntries = db.foodDao().all(),
            favorites = db.foodDao().favorites(),
            planDays = db.planDao().all(),
            kv = db.kvDao().all(),
            opLogs = db.opLogDao().all(),
            nutritionDays = db.nutritionDao().all(),
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    /** 用备份文件的内容替换现有全部数据 */
    suspend fun importJson(text: String): BackupFile = withContext(Dispatchers.IO) {
        val file = json.decodeFromString(BackupFile.serializer(), text)
        db.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                db.bodyRecordDao().clear()
                db.foodDao().clear()
                db.foodDao().clearFavorites()
                db.planDao().clear()
                db.kvDao().clear()
                db.opLogDao().clear()
                db.nutritionDao().clear()
                db.bodyRecordDao().insertAll(file.bodyRecords)
                db.foodDao().insertAll(file.foodEntries)
                file.favorites.forEach { db.foodDao().insertFavorite(it) }
                db.planDao().upsertAll(file.planDays)
                file.kv.forEach { db.kvDao().put(it) }
                db.opLogDao().insertAll(file.opLogs)
                db.nutritionDao().upsertAll(file.nutritionDays)
            }
        }
        file
    }

    /** 每天最多自动备份一次；force 为 true 时（比如安装更新前）总是备份 */
    suspend fun autoBackup(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toString()
        if (!force && db.kvDao().get(KEY_LAST) == today) return@withContext false
        val name = "薄肌来-备份-${today.replace("-", "")}.json"
        if (!store.write(name, exportJson())) return@withContext false
        db.kvDao().put(KeyValue(KEY_LAST, today))
        runCatching { store.prune(KEEP) }
        true
    }

    private companion object {
        const val KEY_LAST = "last_auto_backup"
        const val KEEP = 7
    }
}
