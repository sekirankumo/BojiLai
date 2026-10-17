package io.github.sekirankumo.fitlog

import io.github.sekirankumo.fitlog.data.NutritionRules
import io.github.sekirankumo.fitlog.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 营养安排的硬性规则：AI 给出的数不管多离谱，最后都要落在安全范围里 */
class NutritionPlanTest {
    private val male = UserProfile(sex = "male", birthYear = LocalDate.now().year - 30, heightCm = 175.0)

    @Test fun floorIsBmrOrAtLeast1200() {
        // 70kg 30 岁 175cm 男：10×70 + 6.25×175 − 5×30 + 5 = 1648.75
        assertEquals(1649, NutritionRules.floorKcal(male, 70.0))
        // 资料不全时按 1200
        assertEquals(1200, NutritionRules.floorKcal(UserProfile(), null))
    }

    @Test fun normalizeKeepsMinimumsAndBalancesCarbs() {
        val m = NutritionRules.normalize(kcal = 900, proteinG = 50, fatG = 10, weightKg = 70.0, floor = 1649)
        assertEquals(1649, m.kcal)
        assertEquals(112, m.proteinG) // 70 × 1.6
        assertEquals(49, m.fatG) // 70 × 0.7
        // 碳水 = (1649 − 112×4 − 49×9) / 4
        assertEquals(((1649 - 112 * 4 - 49 * 9) / 4.0).let { Math.round(it).toInt() }, m.carbsG)
        assertTrue(m.carbsG >= 0)
    }

    @Test fun autoRangeLimitsDailySwing() {
        val range = NutritionRules.autoRange(2000)
        assertEquals(1700..2300, range)
        val m = NutritionRules.normalize(kcal = 3000, proteinG = 140, fatG = 60, weightKg = 70.0, floor = 1649, kcalRange = range)
        assertEquals(2300, m.kcal)
        val low = NutritionRules.normalize(kcal = 1000, proteinG = 140, fatG = 60, weightKg = 70.0, floor = 1649, kcalRange = range)
        assertEquals(1700, low.kcal)
    }

    @Test fun proteinAndFatHaveUpperBounds() {
        val m = NutritionRules.normalize(kcal = 2000, proteinG = 400, fatG = 200, weightKg = 70.0, floor = 1649)
        assertEquals(168, m.proteinG) // 70 × 2.4
        assertEquals(89, m.fatG) // 2000 × 40% / 9
    }

    @Test fun tooFewCaloriesForMinimumsRaisesKcal() {
        // 体重很大、下限很低时，蛋白和脂肪的下限加起来可能超过热量：热量要提上来，碳水不为负
        val m = NutritionRules.normalize(kcal = 1200, proteinG = 0, fatG = 0, weightKg = 200.0, floor = 1200)
        assertEquals(320, m.proteinG)
        assertEquals(140, m.fatG)
        assertEquals(320 * 4 + 140 * 9, m.kcal)
        assertEquals(0, m.carbsG)
    }

    @Test fun baselineChangesAtMostWeeklyAndBy150() {
        // 不满 7 天不调
        assertEquals(2000, NutritionRules.nextBaseline(2000, 1700, 2100, 1649, daysSinceChange = 3))
        // 满 7 天最多变 150
        assertEquals(1850, NutritionRules.nextBaseline(2000, 1700, 2100, 1649, daysSinceChange = 7))
        assertEquals(2150, NutritionRules.nextBaseline(2000, 2600, 2100, 1649, daysSinceChange = null))
        // 不超出默认目标 ±20%
        assertEquals(1680, NutritionRules.nextBaseline(1700, 1500, 2100, 1649, daysSinceChange = 10))
        // 不想调就保持
        assertEquals(2000, NutritionRules.nextBaseline(2000, null, 2100, 1649, daysSinceChange = 30))
    }
}
