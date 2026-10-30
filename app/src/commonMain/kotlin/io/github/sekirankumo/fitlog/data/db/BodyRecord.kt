package io.github.sekirankumo.fitlog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 一次身体数据测量 */
@kotlinx.serialization.Serializable
@Entity(tableName = "body_record")
data class BodyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 测量时间，epoch 毫秒 */
    val measuredAt: Long,
    val weightKg: Double?,
    val bodyFatPct: Double?,
    /** 数据来源：manual / ai / health_connect / apple_health / scale */
    val source: String = "manual",
    val note: String? = null,
    /** 外部数据的 id（Health Connect 记录 id），用于去重 */
    val externalId: String? = null,
)

@Dao
interface BodyRecordDao {
    @Query("SELECT * FROM body_record ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<BodyRecord>>

    @Query("SELECT * FROM body_record ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatest(): Flow<BodyRecord?>

    @Query("SELECT * FROM body_record ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): BodyRecord?

    @Query("SELECT * FROM body_record WHERE weightKg IS NOT NULL ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latestWeight(): BodyRecord?

    @Query("SELECT * FROM body_record WHERE measuredAt >= :from ORDER BY measuredAt DESC")
    suspend fun since(from: Long): List<BodyRecord>

    @Query("SELECT * FROM body_record WHERE measuredAt >= :from AND measuredAt <= :to ORDER BY measuredAt")
    suspend fun between(from: Long, to: Long): List<BodyRecord>

    @Query("SELECT * FROM body_record WHERE id = :id")
    suspend fun get(id: Long): BodyRecord?

    @Query("SELECT * FROM body_record WHERE externalId = :externalId LIMIT 1")
    suspend fun byExternalId(externalId: String): BodyRecord?

    @Query("SELECT * FROM body_record ORDER BY measuredAt")
    suspend fun all(): List<BodyRecord>

    /** REPLACE：撤销删除时按原 id 重新插入 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BodyRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<BodyRecord>)

    @Update
    suspend fun update(record: BodyRecord)

    @Delete
    suspend fun delete(record: BodyRecord)

    @Query("DELETE FROM body_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM body_record")
    suspend fun clear()
}
