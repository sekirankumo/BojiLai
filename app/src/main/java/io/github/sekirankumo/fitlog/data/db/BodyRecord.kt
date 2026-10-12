package io.github.sekirankumo.fitlog.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 一次身体数据测量 */
@Entity(tableName = "body_record")
data class BodyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 测量时间，epoch 毫秒 */
    val measuredAt: Long,
    val weightKg: Double?,
    val bodyFatPct: Double?,
    /** 数据来源：manual / health_connect / scale */
    val source: String = "manual",
    val note: String? = null,
)

@Dao
interface BodyRecordDao {
    @Query("SELECT * FROM body_record ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<BodyRecord>>

    @Query("SELECT * FROM body_record ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatest(): Flow<BodyRecord?>

    @Insert
    suspend fun insert(record: BodyRecord): Long

    @Update
    suspend fun update(record: BodyRecord)

    @Delete
    suspend fun delete(record: BodyRecord)
}
