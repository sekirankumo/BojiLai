package io.github.sekirankumo.fitlog

import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.exercise.Exercise
import io.github.sekirankumo.fitlog.data.plan.PlanExercise
import io.github.sekirankumo.fitlog.data.plan.PlanSet
import io.github.sekirankumo.fitlog.data.stats.RecoveryFactors
import io.github.sekirankumo.fitlog.data.stats.SessionLoad
import io.github.sekirankumo.fitlog.data.stats.TrainingStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TrainingStatsTest {
    private val hour = 3_600_000L
    private val normal = RecoveryFactors()

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
        val d = day(LocalDate.of(2026, 10, 1), PlanExercise("bench", "卧推", List(4) { PlanSet(reps = 8, done = true, doneReps = 8) }))
        val load = TrainingStats.sessionLoads(listOf(d.first), { d.second }, { bench }, now = Long.MAX_VALUE).single()
        assertEquals(4.0, load.bySlug["chest"]!!, 1e-9)
        assertEquals(2.0, load.bySlug["deltoids"]!!, 1e-9)
        assertEquals(2.0, load.bySlug["triceps"]!!, 1e-9)
        assertTrue(load.nearFailure.isEmpty())
    }

    @Test
    fun missedRepsMarkNearFailure() {
        val sets = listOf(PlanSet(reps = 8, done = true, doneReps = 8), PlanSet(reps = 8, done = true, doneReps = 6))
        val d = day(LocalDate.of(2026, 10, 1), PlanExercise("bench", "卧推", sets))
        val load = TrainingStats.sessionLoads(listOf(d.first), { d.second }, { bench }, now = Long.MAX_VALUE).single()
        assertEquals(setOf("chest"), load.nearFailure)
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
    fun largerMusclesMoreVolumeAndFailureTakeLonger() {
        val chest = TrainingStats.recoveryHours("chest", 4.0, false, normal)
        val biceps = TrainingStats.recoveryHours("biceps", 4.0, false, normal)
        assertTrue(chest > biceps)
        assertTrue(TrainingStats.recoveryHours("chest", 8.0, false, normal) > chest)
        assertTrue(TrainingStats.recoveryHours("chest", 2.0, false, normal) < chest)
        assertTrue(TrainingStats.recoveryHours("chest", 4.0, true, normal) > chest)
    }

    @Test
    fun bodyFactorsSlowRecovery() {
        val base = TrainingStats.recoveryHours("chest", 4.0, false, normal)
        val older = TrainingStats.recoveryHours("chest", 4.0, false, RecoveryFactors(age = 52))
        val underfed = TrainingStats.recoveryHours("chest", 4.0, false, RecoveryFactors(calorieRatio = 0.6, proteinPerKg = 0.9))
        assertTrue(older > base)
        assertTrue(underfed > base)
        assertEquals(1.4, RecoveryFactors(age = 60, calorieRatio = 0.5, proteinPerKg = 0.5).multiplier(), 1e-9)
    }

    @Test
    fun fadesFromFullToZeroAtRecoveryTime() {
        assertEquals(1.0, TrainingStats.remaining(0.0, 72.0), 1e-9)
        assertEquals(0.5, TrainingStats.remaining(36.0, 72.0), 1e-9)
        assertEquals(0.0, TrainingStats.remaining(72.0, 72.0), 1e-9)
        // 前半段褪得慢
        assertTrue(TrainingStats.remaining(12.0, 72.0) > 1 - 12.0 / 72.0)
    }

    @Test
    fun recoveredMusclesDisappearAndRecentOnesStack() {
        val total = TrainingStats.recoveryHours("chest", 6.0, false, normal)
        val one = listOf(SessionLoad(0, mapOf("chest" to 6.0)))
        val fresh = TrainingStats.muscleStates(one, now = 0)["chest"]!!
        assertEquals(1f, fresh.intensity, 1e-6f)
        assertEquals(total, fresh.hoursLeft, 1e-6)
        assertFalse(TrainingStats.muscleStates(one, now = (total * hour).toLong() + hour).containsKey("chest"))

        val two = listOf(SessionLoad(0, mapOf("chest" to 3.0)), SessionLoad(24 * hour, mapOf("chest" to 3.0)))
        val stacked = TrainingStats.muscleStates(two, now = 24 * hour)["chest"]!!
        val single = TrainingStats.muscleStates(two.takeLast(1), now = 24 * hour)["chest"]!!
        assertTrue(stacked.intensity > single.intensity)
    }

    @Test
    fun formatsTimeLeft() {
        assertEquals("约 20 小时", TrainingStats.formatLeft(20.4))
        assertEquals("约 1.5 天", TrainingStats.formatLeft(30.0))
        assertEquals("约 3 天", TrainingStats.formatLeft(70.0))
    }
}
