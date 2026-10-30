package io.github.sekirankumo.fitlog.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 操作日志：记录 AI（以及自动任务）对数据做的每次修改，
 * undoJson 保存撤销所需的数据，由对应工具的 undo() 解释。
 */
@kotlinx.serialization.Serializable
@Entity(tableName = "op_log")
data class OpLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val toolName: String,
    val summary: String,
    val undoJson: String?,
    val undone: Boolean = false,
)

@Dao
interface OpLogDao {
    @Insert
    suspend fun insert(log: OpLog): Long

    @Query("SELECT * FROM op_log WHERE id = :id")
    suspend fun get(id: Long): OpLog?

    @Query("SELECT * FROM op_log ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<OpLog>

    @Query("SELECT * FROM op_log ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<OpLog>>

    @Query("UPDATE op_log SET undone = 1 WHERE id = :id")
    suspend fun markUndone(id: Long)

    @Query("SELECT * FROM op_log ORDER BY createdAt")
    suspend fun all(): List<OpLog>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<OpLog>)

    @Query("DELETE FROM op_log")
    suspend fun clear()
}
