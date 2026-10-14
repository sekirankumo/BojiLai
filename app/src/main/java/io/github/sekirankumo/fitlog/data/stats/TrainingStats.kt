package io.github.sekirankumo.fitlog.data.stats

import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.exercise.Exercise
import io.github.sekirankumo.fitlog.data.plan.PlanExercise
import io.github.sekirankumo.fitlog.util.TimeUtil
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.cos

/**
 * 一次训练对各个部位的刺激。
 * @param bySlug 部位 → 加权组数（主要肌群 1 组算 1，辅助肌群算 0.5）
 * @param nearFailure 这次训练里有没做满目标次数的组的部位（接近力竭，恢复更慢）
 */
data class SessionLoad(val time: Long, val bySlug: Map<String, Double>, val nearFailure: Set<String> = emptySet())

/** 某个部位当前的恢复状态 */
data class MuscleState(
    /** 0..1，颜色深浅 */
    val intensity: Float,
    /** 距离完全恢复还要多少小时 */
    val hoursLeft: Double,
)

/**
 * 影响恢复速度的身体状况。
 * @param age 年龄，未知为 null
 * @param calorieRatio 最近两天平均摄入 / 目标热量，没有饮食记录为 null
 * @param proteinPerKg 最近两天平均每公斤体重的蛋白质克数，未知为 null
 */
data class RecoveryFactors(
    val age: Int? = null,
    val calorieRatio: Double? = null,
    val proteinPerKg: Double? = null,
) {
    /** 恢复时间的整体倍数（1.0 = 正常） */
    fun multiplier(): Double {
        var m = 1.0
        when {
            age == null -> Unit
            age >= 50 -> m += 0.2
            age >= 35 -> m += 0.1
        }
        // 热量缺口超过目标的 25%（目标本身已含减脂缺口）、蛋白质不足，都会拖慢修复
        if (calorieRatio != null && calorieRatio < 0.75) m += 0.1
        if (proteinPerKg != null && proteinPerKg < 1.2) m += 0.1
        return m.coerceIn(1.0, 1.4)
    }
}

/**
 * 训练统计：都是纯函数，只依赖传进来的数据，方便测试。
 *
 * 肌肉恢复模型（参照运动科学的常见结论）：
 * - 同一肌群建议间隔约 48 小时再练；训练后肌肉蛋白合成会升高 24–48 小时；
 * - 大肌群、训练量大、做到接近力竭恢复更慢；年龄偏大、热量缺口大、蛋白质不足也会更慢。
 * 所以每次训练、每个部位单独算「恢复所需时间」= 部位基准 × 训练量 × 强度 × 身体状况，
 * 颜色在这段时间内从深到浅褪回底色（前半段褪得慢，后半段快），多次训练叠加。
 */
object TrainingStats {
    /** 一次训练的加权组数达到这个值时，刚练完颜色最深 */
    const val FULL_SETS = 6.0

    /** 各部位的基准恢复时间（小时），对应一次常规训练量（约 4 组） */
    val BASE_RECOVERY_HOURS: Map<String, Double> = mapOf(
        "quadriceps" to 72.0, "hamstring" to 72.0, "gluteal" to 72.0,
        "upperBack" to 72.0, "lowerBack" to 72.0, "chest" to 72.0,
        "deltoids" to 60.0, "trapezius" to 60.0, "adductors" to 60.0,
        "biceps" to 48.0, "triceps" to 48.0, "calves" to 48.0,
        "abs" to 36.0, "obliques" to 36.0, "forearm" to 36.0, "neck" to 36.0,
    )

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
        val failed = HashSet<String>()
        for (ex in exercisesOf(day)) {
            val done = ex.sets.count { it.done }
            if (done == 0) continue
            val info = lookup(ex) ?: continue
            if (info.category in SKIP_CATEGORIES) continue
            val primary = info.primary.flatMap { MUSCLE_TO_SLUGS[it].orEmpty() }.distinct()
            val secondary = info.secondary.flatMap { MUSCLE_TO_SLUGS[it].orEmpty() }.distinct() - primary.toSet()
            primary.forEach { bySlug.merge(it, done.toDouble(), Double::plus) }
            secondary.forEach { bySlug.merge(it, done * 0.5, Double::plus) }
            val nearFailure = ex.sets.any { it.done && it.reps != null && it.doneReps != null && it.doneReps < it.reps }
            if (nearFailure) failed += primary
        }
        if (bySlug.isEmpty()) return@mapNotNull null
        val evening = TimeUtil.toEpoch(LocalDate.ofEpochDay(day.date).atTime(19, 0))
        SessionLoad(day.completedAt ?: minOf(evening, now), bySlug, failed)
    }

    /**
     * 一次训练后，这个部位需要多少小时恢复。
     * 训练量按组数调整：约 4 组为基准，2 组约 0.85 倍，8 组约 1.3 倍，最多 1.45 倍。
     */
    fun recoveryHours(slug: String, sets: Double, nearFailure: Boolean, factors: RecoveryFactors): Double {
        val base = BASE_RECOVERY_HOURS[slug] ?: 48.0
        val volume = (0.7 + 0.075 * sets).coerceIn(0.75, 1.45)
        val effort = if (nearFailure) 1.15 else 1.0
        return base * volume * effort * factors.multiplier()
    }

    /** 恢复过程中残留的比例：刚练完 1，到恢复时间为 0；前半段降得慢、后半段快 */
    fun remaining(elapsedHours: Double, totalHours: Double): Double {
        if (elapsedHours < 0) return 1.0
        if (elapsedHours >= totalHours) return 0.0
        return (1 + cos(PI * elapsedHours / totalHours)) / 2
    }

    /** 当前各部位的恢复状态（已完全恢复的部位不在结果里） */
    fun muscleStates(sessions: List<SessionLoad>, now: Long, factors: RecoveryFactors = RecoveryFactors()): Map<String, MuscleState> {
        val load = HashMap<String, Double>()
        val left = HashMap<String, Double>()
        for (s in sessions) {
            val elapsed = (now - s.time) / 3_600_000.0
            if (elapsed < 0) continue
            for ((slug, sets) in s.bySlug) {
                val total = recoveryHours(slug, sets, slug in s.nearFailure, factors)
                val r = remaining(elapsed, total)
                if (r <= 0.0) continue
                load.merge(slug, sets / FULL_SETS * r, Double::plus)
                left.merge(slug, total - elapsed, ::maxOf)
            }
        }
        return load.mapValues { (slug, l) -> MuscleState(l.coerceIn(0.0, 1.0).toFloat(), left[slug] ?: 0.0) }
    }

    /** 每天完成的组数（热力图用） */
    fun dailySets(days: List<PlanDay>, exercisesOf: (PlanDay) -> List<PlanExercise>): Map<LocalDate, Int> =
        days.associate { day ->
            LocalDate.ofEpochDay(day.date) to exercisesOf(day).sumOf { ex -> ex.sets.count { it.done } }
        }.filterValues { it > 0 }

    /** 剩余恢复时间的说法：「约 20 小时」「约 2 天」 */
    fun formatLeft(hours: Double): String {
        if (hours < 1) return "快恢复了"
        if (hours < 24) return "约 ${hours.toInt()} 小时"
        // 以半天为单位：30 小时 → 约 1.5 天
        val days = Math.ceil(hours / 12) / 2
        return if (days % 1.0 == 0.0) "约 ${days.toInt()} 天" else "约 $days 天"
    }
}
