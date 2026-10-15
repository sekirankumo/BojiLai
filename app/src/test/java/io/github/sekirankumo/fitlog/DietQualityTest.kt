package io.github.sekirankumo.fitlog

import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.diet.DayQuality
import io.github.sekirankumo.fitlog.data.diet.FoodComponent
import io.github.sekirankumo.fitlog.data.diet.FoodGroup
import io.github.sekirankumo.fitlog.data.diet.QualityZone
import io.github.sekirankumo.fitlog.data.diet.encodeComponents
import io.github.sekirankumo.fitlog.data.diet.formatScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DietQualityTest {
    private fun entry(vararg parts: Pair<FoodGroup, Double>, analyzed: Boolean = true) = FoodEntry(
        eatenAt = 0,
        meal = "lunch",
        name = "测试",
        kcal = 0.0,
        proteinG = 0.0,
        fatG = 0.0,
        carbsG = 0.0,
        components = if (analyzed) encodeComponents(parts.map { FoodComponent(it.first.label, it.first.key, it.second) }) else null,
    )

    @Test
    fun maxIs49() {
        assertEquals(49.0, FoodGroup.entries.sumOf { it.maxPoints }, 1e-9)
    }

    @Test
    fun boundariesBelongToMiddleBand() {
        val citrus = FoodGroup.Citrus
        assertEquals(0.0, citrus.pointsFor(23.9), 0.0)
        assertEquals(1.0, citrus.pointsFor(24.0), 0.0)
        assertEquals(1.0, citrus.pointsFor(69.0), 0.0)
        assertEquals(2.0, citrus.pointsFor(69.1), 0.0)
        // 全脂奶：适量得分，太多不给分
        assertEquals(2.0, FoodGroup.HighFatDairy.pointsFor(734.0), 0.0)
        assertEquals(0.0, FoodGroup.HighFatDairy.pointsFor(735.0), 0.0)
        assertEquals(1.0, FoodGroup.RedMeat.pointsFor(46.0), 0.0)
        assertEquals(0.0, FoodGroup.RedMeat.pointsFor(47.0), 0.0)
        // 不健康食物：吃得越少分越高
        assertEquals(2.0, FoodGroup.ProcessedMeat.pointsFor(8.9), 0.0)
        assertEquals(1.0, FoodGroup.ProcessedMeat.pointsFor(30.0), 0.0)
        assertEquals(0.0, FoodGroup.ProcessedMeat.pointsFor(31.0), 0.0)
    }

    @Test
    fun noRecordsHasNoScore() {
        assertNull(DayQuality.of(emptyList()))
    }

    @Test
    fun sampleDay() {
        val q = DayQuality.of(
            listOf(
                entry(FoodGroup.WholeGrains to 70.0),
                entry(FoodGroup.Eggs to 100.0),
                entry(FoodGroup.Legumes to 300.0),
                entry(FoodGroup.Poultry to 150.0, FoodGroup.WhiteTubers to 50.0, FoodGroup.OtherVeg to 50.0, FoodGroup.LiquidOil to 10.0),
                entry(FoodGroup.RefinedGrains to 150.0),
                entry(FoodGroup.OtherVeg to 95.0, FoodGroup.LiquidOil to 5.0),
                entry(FoodGroup.HighFatDairy to 150.0),
                // 还没分析的记录不计分
                entry(FoodGroup.Sweets to 500.0, analyzed = false),
            )
        )!!
        // 健康 12.5 + 全脂奶 2 + 不健康 11（精制谷物 0，白色根茎 1，其余满分）
        assertEquals(25.5, q.total, 1e-9)
        assertEquals(QualityZone.Good, q.zone)
        assertEquals(1, q.pending)
        assertEquals("25.5", formatScore(q.total))
    }

    @Test
    fun instantNoodleNight() {
        val q = DayQuality.of(
            listOf(
                entry(FoodGroup.RefinedGrains to 80.0, FoodGroup.Fried to 80.0),
                entry(FoodGroup.SugaryDrinks to 330.0),
                entry(FoodGroup.ProcessedMeat to 40.0),
            )
        )!!
        // 甜食、果汁、白色根茎没吃各得 2 分，其他都是 0
        assertEquals(6.0, q.total, 1e-9)
        assertEquals(QualityZone.Poor, q.zone)
    }

    @Test
    fun suggestionPicksMostPointsForLeastFood() {
        val q = DayQuality.of(listOf(entry()))!!
        val (group, step) = q.suggestion!!
        // 坚果、豆类、深绿叶菜都是 +2，坚果只要 7 克
        assertEquals(FoodGroup.Nuts, group.group)
        assertEquals(7.0, step.first, 1e-9)
        assertEquals(2.0, step.second, 1e-9)
    }
}
