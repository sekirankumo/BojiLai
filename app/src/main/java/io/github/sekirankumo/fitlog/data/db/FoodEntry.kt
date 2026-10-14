package io.github.sekirankumo.fitlog.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 餐次 */
enum class Meal(val key: String, val label: String) {
    Breakfast("breakfast", "早餐"),
    Lunch("lunch", "午餐"),
    Dinner("dinner", "晚餐"),
    Snack("snack", "加餐");

    companion object {
        fun of(key: String?): Meal = entries.firstOrNull { it.key == key || it.label == key } ?: Snack

        /** 根据进食时间推断餐次 */
        fun byHour(hour: Int): Meal = when (hour) {
            in 5..9 -> Breakfast
            in 10..14 -> Lunch
            in 17..20 -> Dinner
            else -> Snack
        }
    }
}

/** 一条饮食记录（一种食物） */
@kotlinx.serialization.Serializable
@Entity(tableName = "food_entry")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 进食时间，epoch 毫秒 */
    val eatenAt: Long,
    /** Meal.key */
    val meal: String,
    val name: String,
    /** 份量描述，例如「一碗」「半份」 */
    val amount: String? = null,
    val grams: Double? = null,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    /** ai / manual / favorite */
    val source: String = "manual",
    val note: String? = null,
)

/** 常吃的食物，一键记录 */
@kotlinx.serialization.Serializable
@Entity(tableName = "favorite_food")
data class FavoriteFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: String? = null,
    val grams: Double? = null,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val useCount: Int = 0,
    val lastUsedAt: Long = 0,
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entry WHERE eatenAt >= :from AND eatenAt < :to ORDER BY eatenAt")
    fun observeRange(from: Long, to: Long): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entry WHERE eatenAt >= :from AND eatenAt < :to ORDER BY eatenAt")
    suspend fun range(from: Long, to: Long): List<FoodEntry>

    @Query("SELECT * FROM food_entry WHERE id = :id")
    suspend fun get(id: Long): FoodEntry?

    @Query("SELECT * FROM food_entry ORDER BY eatenAt")
    suspend fun all(): List<FoodEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FoodEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FoodEntry>): List<Long>

    @Update
    suspend fun update(entry: FoodEntry)

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM food_entry WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM food_entry")
    suspend fun clear()

    // ---- 常吃 ----

    @Query("SELECT * FROM favorite_food ORDER BY useCount DESC, lastUsedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteFood>>

    @Query("SELECT * FROM favorite_food ORDER BY useCount DESC, lastUsedAt DESC")
    suspend fun favorites(): List<FavoriteFood>

    @Query("SELECT * FROM favorite_food WHERE name = :name LIMIT 1")
    suspend fun favoriteByName(name: String): FavoriteFood?

    @Query("SELECT * FROM favorite_food WHERE id = :id")
    suspend fun favorite(id: Long): FavoriteFood?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(food: FavoriteFood): Long

    @Query("UPDATE favorite_food SET useCount = useCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun touchFavorite(id: Long, now: Long)

    @Query("DELETE FROM favorite_food WHERE id = :id")
    suspend fun deleteFavorite(id: Long)

    @Query("DELETE FROM favorite_food")
    suspend fun clearFavorites()
}
