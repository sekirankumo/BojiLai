package io.github.sekirankumo.fitlog.data.diet

import io.github.sekirankumo.fitlog.data.db.FavoriteFood
import io.github.sekirankumo.fitlog.data.db.FoodDao
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import java.time.LocalDate

/** 一天的营养合计 */
data class DayTotals(val kcal: Double, val proteinG: Double, val fatG: Double, val carbsG: Double) {
    companion object {
        fun of(entries: List<FoodEntry>) = DayTotals(
            entries.sumOf { it.kcal },
            entries.sumOf { it.proteinG },
            entries.sumOf { it.fatG },
            entries.sumOf { it.carbsG },
        )
    }
}

fun FoodEntry.describe(): String = buildString {
    append("#$id ${TimeUtil.format(eatenAt)} ${io.github.sekirankumo.fitlog.data.db.Meal.of(meal).label} $name")
    amount?.let { append("（$it）") }
    grams?.let { append(" ${it.fmt(0)}g") }
    append(" ${kcal.fmt(0)}kcal 蛋白${proteinG.fmt()}g 脂肪${fatG.fmt()}g 碳水${carbsG.fmt()}g")
}

class DietRepository(private val dao: FoodDao) {
    fun observeDay(day: LocalDate) = dao.observeRange(dayStart(day), dayStart(day.plusDays(1)))

    suspend fun day(day: LocalDate) = dao.range(dayStart(day), dayStart(day.plusDays(1)))

    suspend fun saveDrafts(drafts: List<FoodDraft>): List<Long> {
        val ids = dao.insertAll(drafts.map { it.toEntry() })
        val now = System.currentTimeMillis()
        drafts.filter { it.addToFavorites }.forEach { addFavorite(it.toEntry()) }
        // 从常吃里选的食物，增加使用次数
        drafts.filter { it.source == "favorite" }.forEach { d ->
            dao.favoriteByName(d.name)?.let { dao.touchFavorite(it.id, now) }
        }
        return ids
    }

    suspend fun addFavorite(e: FoodEntry) {
        val existing = dao.favoriteByName(e.name)
        dao.insertFavorite(
            FavoriteFood(
                id = existing?.id ?: 0,
                name = e.name,
                amount = e.amount,
                grams = e.grams,
                kcal = e.kcal,
                proteinG = e.proteinG,
                fatG = e.fatG,
                carbsG = e.carbsG,
                useCount = (existing?.useCount ?: 0) + 1,
                lastUsedAt = System.currentTimeMillis(),
            )
        )
    }

    companion object {
        fun dayStart(day: LocalDate): Long = TimeUtil.toEpoch(day.atStartOfDay())
    }
}
