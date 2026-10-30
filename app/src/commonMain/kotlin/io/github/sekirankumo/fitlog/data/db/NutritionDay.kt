package io.github.sekirankumo.fitlog.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 某一天的营养目标。没有这一行的日子用默认目标（按身体数据算的那套）。
 * 过去的日子会把当天实际用的目标记下来（source = default），以后改目标不会影响历史记录的对比。
 */
@kotlinx.serialization.Serializable
@Entity(tableName = "nutrition_day")
data class NutritionDay(
    /** epochDay */
    @PrimaryKey val date: Long,
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    /** 给用户看的一句说明，例如「练腿日，碳水多一点」 */
    val note: String? = null,
    /** ai：AI 安排的；default：按当时的默认目标记下来的 */
    val source: String,
    val updatedAt: Long,
) {
    companion object {
        const val SOURCE_AI = "ai"
        const val SOURCE_DEFAULT = "default"
    }
}

@Dao
interface NutritionDao {
    @Query("SELECT * FROM nutrition_day WHERE date = :date")
    fun observe(date: Long): Flow<NutritionDay?>

    @Query("SELECT * FROM nutrition_day WHERE date = :date")
    suspend fun get(date: Long): NutritionDay?

    @Query("SELECT * FROM nutrition_day WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun range(from: Long, to: Long): List<NutritionDay>

    @Query("SELECT * FROM nutrition_day WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: Long, to: Long): Flow<List<NutritionDay>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(days: List<NutritionDay>)

    @Query("DELETE FROM nutrition_day WHERE date IN (:dates)")
    suspend fun deleteDates(dates: List<Long>)

    @Query("SELECT * FROM nutrition_day ORDER BY date")
    suspend fun all(): List<NutritionDay>

    @Query("DELETE FROM nutrition_day")
    suspend fun clear()
}
