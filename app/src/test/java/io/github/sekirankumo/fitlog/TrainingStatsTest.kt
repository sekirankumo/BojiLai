package io.github.sekirankumo.fitlog

import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.exercise.Exercise
import io.github.sekirankumo.fitlog.data.plan.PlanExercise
import io.github.sekirankumo.fitlog.data.plan.PlanSet
import io.github.sekirankumo.fitlog.data.stats.SessionLoad
import io.github.sekirankumo.fitlog.data.stats.TrainingStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingStatsTest {
    private val hour = 3_600_000L

    private val bench = Exercise(
        id = "bench", name = "卧推", nameEn = "Bench", category = "力量", equipment = "杠铃",
        primary = listOf("胸"), secondary = listOf("肩", "肱三头肌"), steps = emptyList(),
    )
    private val run = Exercise(
        id = "run", name = "跑步", nameEn = "Run", category = "有氧", equipment = "跑步机",
        primary = listOf("大腿前侧"), steps = emptyList(),
    )

    private fun day(date: LocalDate, vararg ex: PlanExercise) = PlanDay(
        date = date.toEpochDay(), title = "t", exercisesJson = "", status = DayStatus.DONE,
    ) to ex.toList()

    @Test
    fun primaryCountsFullSecondaryHalf() {
        val d = day(LocalDate.of(2026, 10, 1), PlanExercise("bench", "卧推", List(4) { PlanSet(done = true) }))
        val loads = TrainingStats.sessionLoads(listOf(d.first), { d.second }, { bench }, now = Long.MAX_VALUE)
        assertEquals(4.0, loads.single().bySlug["chest"]!!, 1e-9)
        assertEquals(2.0, loads.single().bySlug["deltoids"]!!, 1e-9)
        assertEquals(2.0, loads.single().bySlug["triceps"]!!, 1e-9)
    }

    @Test
    fun undoneSetsAndCardioAreIgnored() {
        val d = day(
            LocalDate.of(2026, 10, 1),
            PlanExercise("bench", "卧推", List(3) { PlanSet(done = false) }),
            PlanExercise("run", "跑步", listOf(PlanSet(done = true))),
        )
        val loads = TrainingStats.sessionLoads(listOf(d.first), { d.second }, { if (it.exerciseId == "run") run else bench }, now = Long.MAX_VALUE)
        assertTrue(loads.isEmpty())
    }

    @Test
    fun loadHalvesAfterHalfLifeAndFadesOut() {
        val s = listOf(SessionLoad(0, mapOf("chest" to 6.0)))
        assertEquals(6.0, TrainingStats.residualLoad(s, now = 0)["chest"]!!, 1e-9)
        assertEquals(3.0, TrainingStats.residualLoad(s, now = (TrainingStats.HALF_LIFE_HOURS * hour).toLong())["chest"]!!, 1e-6)
        assertFalse(TrainingStats.residualLoad(s, now = 14 * 24 * hour).containsKey("chest"))
    }

    @Test
    fun recentSessionsStack() {
        val s = listOf(SessionLoad(0, mapOf("chest" to 4.0)), SessionLoad(24 * hour, mapOf("chest" to 4.0)))
        val r = TrainingStats.residualLoad(s, now = 24 * hour)["chest"]!!
        assertTrue(r > 4.0 && r < 8.0)
        assertEquals(1f, TrainingStats.intensities(mapOf("chest" to r))["chest"]!!, 1e-6f)
    }

    @Test
    fun weekStreakCountsBackFromThisWeek() {
        val today = LocalDate.of(2026, 10, 14) // 周三
        val daily = mapOf(
            LocalDate.of(2026, 10, 13) to 12, // 本周
            LocalDate.of(2026, 10, 8) to 10, // 上周
            LocalDate.of(2026, 9, 30) to 8, // 上上周
            LocalDate.of(2026, 9, 16) to 8, // 中间断了一周
        )
        val s = TrainingStats.summary(daily, today)
        assertEquals(4, s.thisYear)
        assertEquals(1, s.thisWeek)
        assertEquals(3, s.weekStreak)
    }
}
