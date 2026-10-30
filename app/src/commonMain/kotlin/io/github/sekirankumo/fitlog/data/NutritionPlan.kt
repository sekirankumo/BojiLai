package io.github.sekirankumo.fitlog.data

import kotlinx.datetime.*
import io.github.sekirankumo.fitlog.util.*
import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.db.KeyValueDao
import io.github.sekirankumo.fitlog.data.db.NutritionDao
import io.github.sekirankumo.fitlog.data.db.NutritionDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/** 一天的热量和三大营养素 */
data class DayMacros(val kcal: Int, val proteinG: Int, val fatG: Int, val carbsG: Int)

/**
 * 营养安排的硬性规则。AI 给出的数超出范围时在这里拉回来，规则写在代码里，不靠 AI 自觉。
 */
object NutritionRules {
    /** 每天热量最少 1200 kcal，并且不低于基础代谢 */
    const val MIN_KCAL = 1200

    /** 自动调整时，每天相对基础热量最多 ±15% */
    const val AUTO_DAY_RANGE = 0.15

    /** 基础热量每次最多变 150 kcal，两次之间至少隔 7 天，相对默认目标不超过 ±20% */
    const val BASELINE_STEP = 150
    const val BASELINE_INTERVAL_DAYS = 7
    const val BASELINE_RANGE = 0.20

    /** 蛋白质每公斤体重 1.6–2.4 g，脂肪不低于每公斤 0.7 g、不超过总热量的 40% */
    const val PROTEIN_MIN_PER_KG = 1.6
    const val PROTEIN_MAX_PER_KG = 2.4
    const val FAT_MIN_PER_KG = 0.7
    const val FAT_MAX_SHARE = 0.4

    /** 体重未知时按 65 kg 算每公斤的量 */
    private const val FALLBACK_WEIGHT = 65.0

    /** Mifflin-St Jeor 基础代谢；资料不全时为 null */
    fun bmr(profile: UserProfile, weightKg: Double?): Int? {
        val age = profile.age() ?: return null
        val height = profile.heightCm ?: return null
        val weight = weightKg ?: return null
        val sex = profile.sex ?: return null
        return (10 * weight + 6.25 * height - 5 * age + if (sex == "male") 5 else -161).roundToInt()
    }

    /** 每天热量的下限 */
    fun floorKcal(profile: UserProfile, weightKg: Double?): Int = maxOf(MIN_KCAL, bmr(profile, weightKg) ?: MIN_KCAL)

    /**
     * 把一天的数拉回安全范围，并让三大营养素和热量对得上（碳水用剩下的热量算）。
     * @param kcalRange 自动调整时传「基础热量 ±15%」；用户在对话里明确要求的安排不限幅度，只守下限
     */
    fun normalize(kcal: Int, proteinG: Int, fatG: Int, weightKg: Double?, floor: Int, kcalRange: IntRange? = null): DayMacros {
        val w = weightKg ?: FALLBACK_WEIGHT
        var k = kcal
        if (kcalRange != null) k = k.coerceIn(kcalRange)
        k = k.coerceIn(floor, maxOf(floor, 6000))
        val pMin = (w * PROTEIN_MIN_PER_KG).roundToInt()
        val pMax = (w * PROTEIN_MAX_PER_KG).roundToInt()
        var p = proteinG.coerceIn(pMin, pMax)
        val fMin = (w * FAT_MIN_PER_KG).roundToInt()
        var f = fatG.coerceIn(fMin, maxOf(fMin, (k * FAT_MAX_SHARE / 9).roundToInt()))
        fun carbs() = ((k - p * 4 - f * 9) / 4.0).roundToInt()
        // 热量不够分：脂肪先降到下限，再不够蛋白质降到下限，还不够就把热量提上来（不低于两者的下限）
        if (carbs() < 0) f = fMin
        if (carbs() < 0) p = pMin
        if (carbs() < 0) k = p * 4 + f * 9
        return DayMacros(k, p, f, carbs().coerceAtLeast(0))
    }

    /** 自动调整时每天热量的允许范围 */
    fun autoRange(baseline: Int): IntRange =
        (baseline * (1 - AUTO_DAY_RANGE)).roundToInt()..(baseline * (1 + AUTO_DAY_RANGE)).roundToInt()

    /** 基础热量能落在的范围：默认目标 ±20%，且不低于下限 */
    fun baselineRange(defaultKcal: Int, floor: Int): IntRange {
        val lo = maxOf(floor, (defaultKcal * (1 - BASELINE_RANGE)).roundToInt())
        val hi = maxOf(lo, (defaultKcal * (1 + BASELINE_RANGE)).roundToInt())
        return lo..hi
    }

    /**
     * 新的基础热量：离上次调整不满 7 天就不变；每次最多变 150；不超出默认目标 ±20%。
     * @param daysSinceChange 上次调整到今天的天数；从没调整过为 null
     */
    fun nextBaseline(current: Int, proposed: Int?, defaultKcal: Int, floor: Int, daysSinceChange: Int?): Int {
        val range = baselineRange(defaultKcal, floor)
        val base = current.coerceIn(range)
        if (proposed == null) return base
        if (daysSinceChange != null && daysSinceChange < BASELINE_INTERVAL_DAYS) return base
        return proposed.coerceIn(base - BASELINE_STEP, base + BASELINE_STEP).coerceIn(range)
    }
}

/** AI 调整后的基础热量（每天热量围绕它上下浮动）；since 是开始用它的日期 */
@Serializable
data class NutritionBaseline(val kcal: Int, val since: Long)

/** 今日页上那条「AI 调整了营养」的提示，看过一次就消失 */
@Serializable
data class NutritionNotice(
    val opId: Long,
    val summary: String,
    val date: Long,
    val kcalBefore: Int,
    val kcalAfter: Int,
)

/** 某一天用的目标和说明（「接下来 7 天」弹层用） */
data class PlannedDay(val date: LocalDate, val macros: DayMacros, val note: String?, val byAi: Boolean)

/** 安排营养时要用到的个人情况 */
data class NutritionContext(
    val profile: UserProfile,
    val defaults: NutritionTargets,
    val weightKg: Double?,
    val floor: Int,
    /** 当前在用的基础热量（没调整过就是默认目标） */
    val baseline: Int,
    /** 上次调整基础热量的日期 */
    val baselineSince: LocalDate?,
)

/** 有安排就用安排的数，资料是否齐全的提示沿用默认目标 */
fun NutritionTargets.with(day: NutritionDay?): NutritionTargets =
    if (day == null) this else copy(kcal = day.kcal, proteinG = day.proteinG, fatG = day.fatG, carbsG = day.carbsG)

fun NutritionTargets.macros() = DayMacros(kcal, proteinG, fatG, carbsG)

/** 每天的营养安排：读写、过去日子的固定、撤销用的快照、基础热量和今日页提示 */
class NutritionPlanRepository(
    private val dao: NutritionDao,
    private val kv: KeyValueDao,
    private val profile: ProfileRepository,
    private val bodyDao: BodyRecordDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val daysSerializer = ListSerializer(NutritionDay.serializer())

    /** 某天的营养目标：有安排用安排，没有用默认 */
    fun targetFor(date: LocalDate): Flow<NutritionTargets> =
        combine(profile.targets, dao.observe(date.toEpochDay())) { defaults, day -> defaults.with(day) }

    suspend fun targetAt(date: LocalDate): NutritionTargets = profile.currentTargets().with(dao.get(date.toEpochDay()))

    suspend fun get(date: LocalDate): NutritionDay? = dao.get(date.toEpochDay())

    suspend fun range(from: LocalDate, to: LocalDate): List<NutritionDay> = dao.range(from.toEpochDay(), to.toEpochDay())

    /** 从 from 起连续几天实际用的目标 */
    fun observeDays(from: LocalDate, count: Int): Flow<List<PlannedDay>> =
        combine(profile.targets, dao.observeRange(from.toEpochDay(), from.plusDays(count - 1L).toEpochDay())) { defaults, list ->
            val byDate = list.associateBy { it.date }
            (0 until count).map { i ->
                val d = from.plusDays(i.toLong())
                val day = byDate[d.toEpochDay()]
                PlannedDay(d, defaults.with(day).macros(), day?.note, day?.source == NutritionDay.SOURCE_AI)
            }
        }

    /** 今天及以后有没有 AI 的安排 */
    fun observeHasPlan(today: LocalDate): Flow<Boolean> =
        dao.observeRange(today.toEpochDay(), today.plusYears(5).toEpochDay()).map { list -> list.any { it.source == NutritionDay.SOURCE_AI } }

    suspend fun upsert(days: List<NutritionDay>) = dao.upsertAll(days)

    suspend fun delete(dates: List<Long>) = dao.deleteDates(dates)

    suspend fun context(): NutritionContext {
        val p = profile.current()
        val weight = bodyDao.latestWeight()?.weightKg
        val defaults = NutritionTargets.from(p, weight)
        val floor = NutritionRules.floorKcal(p, weight)
        val b = baseline()
        val base = NutritionRules.nextBaseline(b?.kcal ?: defaults.kcal, null, defaults.kcal, floor, null)
        return NutritionContext(p, defaults, weight, floor, base, b?.let { LocalDate.ofEpochDay(it.since) })
    }

    /**
     * 过去的日子：没有安排的按现在的默认目标记下来，以后改目标、重新安排都不会再影响这些日子。
     */
    suspend fun freeze(from: LocalDate, to: LocalDate) {
        if (to.isBefore(from)) return
        val existing = dao.range(from.toEpochDay(), to.toEpochDay()).map { it.date }.toSet()
        val defaults = profile.currentTargets()
        val now = nowMillis()
        val missing = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
            .filter { it.toEpochDay() !in existing }
            .map { NutritionDay(it.toEpochDay(), defaults.kcal, defaults.proteinG, defaults.fatG, defaults.carbsG, null, NutritionDay.SOURCE_DEFAULT, now) }
            .toList()
        if (missing.isNotEmpty()) dao.upsertAll(missing)
    }

    // ---------- 基础热量 ----------

    suspend fun baseline(): NutritionBaseline? =
        kv.get(KEY_BASELINE)?.let { runCatching { json.decodeFromString(NutritionBaseline.serializer(), it) }.getOrNull() }

    fun observeBaseline(): Flow<NutritionBaseline?> =
        kv.observe(KEY_BASELINE).map { text -> text?.let { runCatching { json.decodeFromString(NutritionBaseline.serializer(), it) }.getOrNull() } }

    suspend fun setBaseline(value: NutritionBaseline?) {
        if (value == null) kv.delete(KEY_BASELINE) else kv.put(KeyValue(KEY_BASELINE, json.encodeToString(NutritionBaseline.serializer(), value)))
    }

    // ---------- 今日页提示 ----------

    val notice: Flow<NutritionNotice?> =
        kv.observe(KEY_NOTICE).map { text -> text?.let { runCatching { json.decodeFromString(NutritionNotice.serializer(), it) }.getOrNull() } }

    suspend fun setNotice(value: NutritionNotice) = kv.put(KeyValue(KEY_NOTICE, json.encodeToString(NutritionNotice.serializer(), value)))

    /** 只清掉指定的那条（期间如果又有新的调整，新提示保留） */
    suspend fun dismissNotice(opId: Long) {
        val current = kv.get(KEY_NOTICE)?.let { runCatching { json.decodeFromString(NutritionNotice.serializer(), it) }.getOrNull() }
        if (current == null || current.opId == opId) kv.delete(KEY_NOTICE)
    }

    // ---------- 撤销 ----------

    /** 记下这些日子现在的安排和基础热量，撤销时原样放回 */
    suspend fun snapshot(dates: List<Long>): JsonObject {
        val old = dates.mapNotNull { dao.get(it) }
        val base = kv.get(KEY_BASELINE)
        return buildJsonObject {
            put("dates", dates.joinToString(","))
            put("days", json.encodeToString(daysSerializer, old))
            put("baseline", base)
        }
    }

    suspend fun restore(snapshot: JsonObject) {
        val dates = (snapshot["dates"] as? JsonPrimitive)?.contentOrNull.orEmpty().split(",").mapNotNull { it.trim().toLongOrNull() }
        val old = (snapshot["days"] as? JsonPrimitive)?.contentOrNull?.let { json.decodeFromString(daysSerializer, it) }.orEmpty()
        if (dates.isNotEmpty()) dao.deleteDates(dates)
        if (old.isNotEmpty()) dao.upsertAll(old)
        val base = (snapshot["baseline"] as? JsonPrimitive)?.contentOrNull
        if (base == null) kv.delete(KEY_BASELINE) else kv.put(KeyValue(KEY_BASELINE, base))
    }

    private companion object {
        const val KEY_BASELINE = "nutrition_baseline"
        const val KEY_NOTICE = "nutrition_notice"
    }
}
