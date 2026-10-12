package io.github.sekirankumo.fitlog.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 训练日状态 */
object DayStatus {
    const val PLANNED = "planned"
    const val DONE = "done"
    const val MISSED = "missed"
    const val SKIPPED = "skipped"

    fun label(status: String) = when (status) {
        PLANNED -> "待训练"
        DONE -> "已完成"
        MISSED -> "漏打卡"
        SKIPPED -> "已跳过"
        else -> status
    }
}

/**
 * 训练计划中的一天。同一时间只有一份计划，所以直接用日期做主键；
 * 没有记录的日期就是休息日。动作和每组的数据以 JSON 保存（见 PlanExercise）。
 */
@kotlinx.serialization.Serializable
@Entity(tableName = "plan_day")
data class PlanDay(
    /** LocalDate.toEpochDay() */
    @PrimaryKey val date: Long,
    val title: String,
    val exercisesJson: String,
    val status: String = DayStatus.PLANNED,
    val note: String? = null,
    val completedAt: Long? = null,
)

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_day WHERE date >= :from AND date <= :to ORDER BY date")
    fun observeRange(from: Long, to: Long): Flow<List<PlanDay>>

    @Query("SELECT * FROM plan_day WHERE date >= :from AND date <= :to ORDER BY date")
    suspend fun range(from: Long, to: Long): List<PlanDay>

    @Query("SELECT * FROM plan_day WHERE date = :date")
    suspend fun get(date: Long): PlanDay?

    @Query("SELECT * FROM plan_day WHERE date = :date")
    fun observe(date: Long): Flow<PlanDay?>

    @Query("SELECT * FROM plan_day ORDER BY date")
    suspend fun all(): List<PlanDay>

    @Query("SELECT * FROM plan_day WHERE status = :status AND date >= :from AND date <= :to ORDER BY date")
    suspend fun withStatus(status: String, from: Long, to: Long): List<PlanDay>

    @Query("SELECT MAX(date) FROM plan_day")
    suspend fun lastDate(): Long?

    @Query("SELECT COUNT(*) FROM plan_day")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(day: PlanDay)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<PlanDay>)

    @Query("DELETE FROM plan_day WHERE date IN (:dates)")
    suspend fun deleteDates(dates: List<Long>)

    @Query("DELETE FROM plan_day WHERE date >= :from AND date <= :to")
    suspend fun deleteRange(from: Long, to: Long)

    @Query("DELETE FROM plan_day")
    suspend fun clear()
}

/** 通用键值表：个人资料、计划信息、同步进度等，随备份一起导出 */
@kotlinx.serialization.Serializable
@Entity(tableName = "kv")
data class KeyValue(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface KeyValueDao {
    @Query("SELECT value FROM kv WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM kv WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(kv: KeyValue)

    @Query("DELETE FROM kv WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM kv")
    suspend fun all(): List<KeyValue>

    @Query("DELETE FROM kv")
    suspend fun clear()
}
