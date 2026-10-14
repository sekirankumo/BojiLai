package io.github.sekirankumo.fitlog.data.stats

import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.exercise.Exercise
import io.github.sekirankumo.fitlog.data.plan.PlanExercise
import io.github.sekirankumo.fitlog.util.TimeUtil
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.pow

/** 一次训练对各个部位的刺激：部位 → 加权组数 */
data class SessionLoad(val time: Long, val bySlug: Map<String, Double>)

/**
 * 训练统计：都是纯函数，只依赖传进来的数据，方便测试。
 *
 * 肌肉图的颜色 = 各部位的「残留刺激」：每次训练按组数给对应部位加分（主要肌群 1，辅助肌群 0.5），
 * 之后按半衰期指数衰减。所以刚练完颜色最深，之后几天慢慢变浅；练得越多，起点越深、褪得也越久。
 */
object TrainingStats {
    /** 衰减半衰期（小时）：36 小时后剩一半，约 5 天后基本褪完 */
    const val HALF_LIFE_HOURS = 36.0

    /** 残留刺激达到这个值时颜色最深（约等于刚做完 6 组主要肌群） */
    const val FULL_LOAD = 6.0

    /** 动作库的肌群名 → 肌肉图上的部位 */
    val MUSCLE_TO_SLUGS: Map<String, List<String>> = mapOf(
        "胸" to listOf("chest"),
        "肩" to listOf("deltoids"),
        "肱二头肌" to listOf("biceps"),
        "肱三头肌" to listOf("triceps"),
        "前臂" to listOf("forearm"),
        "腹肌" to listOf("abs", "obliques"),
        "背阔肌" to listOf("upperBack"),
        "中背" to listOf("upperBack"),
        "下背" to listOf("lowerBack"),
        "斜方肌" to listOf("trapezius"),
        "颈部" to listOf("neck"),
        "臀" to listOf("gluteal"),
        "臀中肌" to listOf("gluteal"),
        "大腿前侧" to listOf("quadriceps"),
        "大腿后侧" to listOf("hamstring"),
        "大腿内侧" to listOf("adductors"),
        "小腿" to listOf("calves"),
    )

    /** 肌肉图部位的中文名 */
    val SLUG_LABELS: Map<String, String> = linkedMapOf(
        "chest" to "胸",
        "deltoids" to "肩",
        "upperBack" to "背",
        "lowerBack" to "下背",
        "trapezius" to "斜方肌",
        "biceps" to "肱二头",
        "triceps" to "肱三头",
        "forearm" to "前臂",
        "abs" to "腹肌",
        "obliques" to "腹斜肌",
        "gluteal" to "臀",
        "quadriceps" to "大腿前侧",
        "hamstring" to "大腿后侧",
        "adductors" to "大腿内侧",
        "calves" to "小腿",
        "neck" to "颈部",
    )

    /** 不计入肌肉刺激的动作类别 */
    private val SKIP_CATEGORIES = setOf("有氧", "拉伸")

    /**
     * 把训练日转成每次训练的刺激。
     * 时间取完成时间；没有完成时间的取当天 19:00（但不晚于 now，今天练到一半的算作刚练）。
     */
    fun sessionLoads(
        days: List<PlanDay>,
        exercisesOf: (PlanDay) -> List<PlanExercise>,
        lookup: (PlanExercise) -> Exercise?,
        now: Long,
    ): List<SessionLoad> = days.mapNotNull { day ->
        val bySlug = HashMap<String, Double>()
        for (ex in exercisesOf(day)) {
            val done = ex.sets.count { it.done }
            if (done == 0) continue
            val info = lookup(ex) ?: continue
            if (info.category in SKIP_CATEGORIES) continue
            info.primary.flatMap { MUSCLE_TO_SLUGS[it].orEmpty() }.distinct()
                .forEach { bySlug.merge(it, done.toDouble(), Double::plus) }
            (info.secondary.flatMap { MUSCLE_TO_SLUGS[it].orEmpty() }.distinct() -
                info.primary.flatMap { MUSCLE_TO_SLUGS[it].orEmpty() }.toSet())
                .forEach { bySlug.merge(it, done * 0.5, Double::plus) }
        }
        if (bySlug.isEmpty()) return@mapNotNull null
        val evening = TimeUtil.toEpoch(LocalDate.ofEpochDay(day.date).atTime(19, 0))
        SessionLoad(day.completedAt ?: minOf(evening, now), bySlug)
    }

    /** 当前各部位的残留刺激（加权组数，已按时间衰减） */
    fun residualLoad(sessions: List<SessionLoad>, now: Long, halfLifeHours: Double = HALF_LIFE_HOURS): Map<String, Double> {
        val result = HashMap<String, Double>()
        for (s in sessions) {
            val hours = (now - s.time) / 3_600_000.0
            if (hours < 0) continue
            val factor = 0.5.pow(hours / halfLifeHours)
            if (factor < 0.01) continue
            s.bySlug.forEach { (slug, load) -> result.merge(slug, load * factor, Double::plus) }
        }
        return result
    }

    /** 残留刺激 → 0..1 的颜色深浅 */
    fun intensities(residual: Map<String, Double>): Map<String, Float> =
        residual.mapValues { (it.value / FULL_LOAD).coerceIn(0.0, 1.0).toFloat() }

    /** 每天完成的组数（热力图用） */
    fun dailySets(days: List<PlanDay>, exercisesOf: (PlanDay) -> List<PlanExercise>): Map<LocalDate, Int> =
        days.associate { day ->
            LocalDate.ofEpochDay(day.date) to exercisesOf(day).sumOf { ex -> ex.sets.count { it.done } }
        }.filterValues { it > 0 }

    /** 热力图下方的统计 */
    data class Summary(val thisYear: Int, val thisWeek: Int, val weekStreak: Int)

    fun summary(daily: Map<LocalDate, Int>, today: LocalDate): Summary {
        val monday = today.with(DayOfWeek.MONDAY)
        val from = today.withDayOfYear(1)
        val thisYear = daily.keys.count { !it.isBefore(from) && !it.isAfter(today) }
        val thisWeek = daily.keys.count { !it.isBefore(monday) && !it.isAfter(today) }
        // 连续有训练的周数：从本周（本周还没练就从上周）往前数
        var streak = 0
        var w = if (thisWeek > 0) monday else monday.minusWeeks(1)
        while (daily.keys.any { !it.isBefore(w) && it.isBefore(w.plusWeeks(1)) }) {
            streak++
            w = w.minusWeeks(1)
        }
        return Summary(thisYear, thisWeek, streak)
    }
}
