package io.github.sekirankumo.fitlog.data.plan

import io.github.sekirankumo.fitlog.assistant.ToolException
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.num
import io.github.sekirankumo.fitlog.assistant.objects
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.db.KeyValueDao
import io.github.sekirankumo.fitlog.data.db.PlanDao
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.exercise.ExerciseRepository
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** 计划中的一组 */
@Serializable
data class PlanSet(
    val reps: Int? = null,
    val weightKg: Double? = null,
    /** 计时类动作（平板支撑、有氧）的秒数 */
    val durationSec: Int? = null,
    val done: Boolean = false,
    val doneReps: Int? = null,
    val doneWeightKg: Double? = null,
    val doneDurationSec: Int? = null,
) {
    fun targetText(): String = when {
        durationSec != null -> formatDuration(durationSec)
        reps != null && weightKg != null -> "${reps}×${weightKg.fmtKg()}"
        reps != null -> "${reps} 次"
        else -> "1 组"
    }

    fun doneText(): String? {
        if (!done) return null
        return when {
            doneDurationSec != null -> formatDuration(doneDurationSec)
            doneReps != null && doneWeightKg != null -> "${doneReps}×${doneWeightKg.fmtKg()}"
            doneReps != null -> "${doneReps} 次"
            else -> "完成"
        }
    }
}

/** 计划中的一个动作 */
@Serializable
data class PlanExercise(
    val exerciseId: String? = null,
    val name: String,
    val sets: List<PlanSet>,
    val restSec: Int? = null,
    val note: String? = null,
)

/** 当前计划的整体信息 */
@Serializable
data class PlanMeta(
    val name: String,
    val goal: String = "",
    val summary: String = "",
    val progression: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val startDate: Long,
    val endDate: Long,
)

/** 撤销计划修改用的快照：这些日期原来的样子（不在 days 里的日期原来是休息日） */
@Serializable
data class PlanSnapshot(val dates: List<Long>, val days: List<PlanDay>)

fun Double.fmtKg(): String = if (this % 1.0 == 0.0) "${toInt()}kg" else "${fmt()}kg"

fun formatDuration(sec: Int): String = when {
    sec >= 60 && sec % 60 == 0 -> "${sec / 60} 分钟"
    sec >= 60 -> "${sec / 60} 分 ${sec % 60} 秒"
    else -> "$sec 秒"
}

fun LocalDate.cn(): String =
    "$this ${dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)}"

class PlanRepository(
    private val dao: PlanDao,
    private val kv: KeyValueDao,
    private val exercises: ExerciseRepository,
) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    fun exercisesOf(day: PlanDay): List<PlanExercise> =
        runCatching { json.decodeFromString<List<PlanExercise>>(day.exercisesJson) }.getOrDefault(emptyList())

    fun encode(list: List<PlanExercise>): String = json.encodeToString(list)

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<PlanDay>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay())

    fun observeDay(date: LocalDate): Flow<PlanDay?> = dao.observe(date.toEpochDay())

    fun observeHasPlan(): Flow<Boolean> = dao.observeCount().map { it > 0 }

    suspend fun get(date: LocalDate): PlanDay? = dao.get(date.toEpochDay())

    suspend fun range(from: LocalDate, to: LocalDate): List<PlanDay> = dao.range(from.toEpochDay(), to.toEpochDay())

    suspend fun lastDate(): LocalDate? = dao.lastDate()?.let { LocalDate.ofEpochDay(it) }

    // ---------- 计划信息 ----------

    fun observeMeta(): Flow<PlanMeta?> = kv.observe(META_KEY).map { it?.let(::decodeMeta) }

    suspend fun meta(): PlanMeta? = kv.get(META_KEY)?.let(::decodeMeta)

    suspend fun setMeta(meta: PlanMeta?) {
        if (meta == null) kv.delete(META_KEY) else kv.put(KeyValue(META_KEY, json.encodeToString(meta)))
    }

    private fun decodeMeta(text: String) = runCatching { json.decodeFromString<PlanMeta>(text) }.getOrNull()

    // ---------- 修改与快照 ----------

    suspend fun snapshot(dates: Collection<Long>): PlanSnapshot {
        val sorted = dates.distinct().sorted()
        val days = if (sorted.isEmpty()) emptyList() else dao.range(sorted.first(), sorted.last()).filter { it.date in sorted }
        return PlanSnapshot(sorted, days)
    }

    suspend fun restore(snapshot: PlanSnapshot) {
        dao.deleteDates(snapshot.dates)
        dao.upsertAll(snapshot.days)
    }

    fun encodeSnapshot(s: PlanSnapshot): String = json.encodeToString(s)
    fun decodeSnapshot(text: String): PlanSnapshot = json.decodeFromString(text)

    /** 用 newDays 替换 [from, to] 范围内的全部计划，返回原来的快照 */
    suspend fun replaceRange(from: Long, to: Long, newDays: List<PlanDay>): PlanSnapshot {
        val old = dao.range(from, to)
        val snap = PlanSnapshot((old.map { it.date } + newDays.map { it.date }).distinct().sorted(), old)
        dao.deleteRange(from, to)
        dao.upsertAll(newDays)
        return snap
    }

    suspend fun upsert(day: PlanDay) = dao.upsert(day)
    suspend fun upsertAll(days: List<PlanDay>) = dao.upsertAll(days)
    suspend fun deleteDates(dates: List<Long>) = dao.deleteDates(dates)

    /** 更新某天的某一组，并在全部完成时把这天标为已完成 */
    suspend fun updateSet(date: LocalDate, exerciseIndex: Int, setIndex: Int, transform: (PlanSet) -> PlanSet) {
        val day = get(date) ?: return
        val list = exercisesOf(day).toMutableList()
        val ex = list.getOrNull(exerciseIndex) ?: return
        val sets = ex.sets.toMutableList()
        if (setIndex !in sets.indices) return
        sets[setIndex] = transform(sets[setIndex])
        list[exerciseIndex] = ex.copy(sets = sets)
        dao.upsert(withAutoStatus(day.copy(exercisesJson = encode(list)), list))
    }

    /** 全部组标记完成（未填实际数值的按目标填入） */
    suspend fun completeDay(date: LocalDate) {
        val day = get(date) ?: return
        val weights = lastWeights()
        val list = exercisesOf(day).map { ex ->
            ex.copy(sets = ex.sets.map { if (it.done) it else it.markDone(weights[exerciseKey(ex)]) })
        }
        dao.upsert(day.copy(exercisesJson = encode(list), status = DayStatus.DONE, completedAt = System.currentTimeMillis()))
    }

    fun withAutoStatus(day: PlanDay, list: List<PlanExercise>): PlanDay {
        val allDone = list.isNotEmpty() && list.all { ex -> ex.sets.all { it.done } }
        return when {
            allDone -> day.copy(status = DayStatus.DONE, completedAt = day.completedAt ?: System.currentTimeMillis())
            day.status == DayStatus.DONE -> day.copy(status = DayStatus.PLANNED, completedAt = null)
            else -> day
        }
    }

    /** 每个动作最近一次实际使用的重量，用来提示和预填 */
    suspend fun lastWeights(): Map<String, Double> {
        val result = HashMap<String, Double>()
        dao.all().filter { it.date <= LocalDate.now().toEpochDay() }.forEach { day ->
            exercisesOf(day).forEach { ex ->
                ex.sets.lastOrNull { it.done && it.doneWeightKg != null }?.doneWeightKg?.let { result[exerciseKey(ex)] = it }
            }
        }
        return result
    }

    fun exerciseKey(ex: PlanExercise) = ex.exerciseId ?: ex.name

    // ---------- 给 AI 的描述 ----------

    fun describeDay(day: PlanDay, detailed: Boolean): String = buildString {
        val date = LocalDate.ofEpochDay(day.date)
        append("${date.cn()} ${day.title} [${DayStatus.label(day.status)}]")
        val list = exercisesOf(day)
        if (!detailed) {
            append("：")
            append(list.joinToString("；") { ex ->
                val first = ex.sets.firstOrNull()
                "${ex.name} ${ex.sets.size}组×${first?.targetText() ?: ""}"
            })
            return@buildString
        }
        day.note?.let { append(" 备注：$it") }
        list.forEachIndexed { i, ex ->
            append("\n  动作$i ${ex.name}（${ex.exerciseId ?: "自定义"}）")
            ex.note?.let { append(" 说明：$it") }
            ex.sets.forEachIndexed { j, s ->
                append("\n    组$j 目标 ${s.targetText()}")
                s.doneText()?.let { append(" ✓ 实际 $it") }
            }
        }
    }

    // ---------- 解析 AI 给出的训练日 ----------

    /**
     * AI 输出格式：
     * {"date":"yyyy-MM-dd","title":"…","note":"…","exercises":[{"exercise_id":"…","name":"…","sets":3,"reps":10,
     *  "weight_kg":40,"duration_sec":null,"rest_sec":90,"note":"…"}]}
     */
    fun parseAiExercises(items: List<JsonObject>): List<PlanExercise> = items.map { o ->
        val ex = exercises.resolve(o.str("exercise_id"), o.str("name"))
        val count = (o.long("sets") ?: 3).toInt().coerceIn(1, 12)
        val reps = o.long("reps")?.toInt()
        val duration = o.long("duration_sec")?.toInt()
        val weight = o.num("weight_kg")
        PlanExercise(
            exerciseId = ex?.id,
            name = ex?.name ?: o.str("name") ?: throw ToolException("动作缺少名称"),
            sets = List(count) { PlanSet(reps = if (duration != null) null else reps ?: 10, weightKg = weight, durationSec = duration) },
            restSec = o.long("rest_sec")?.toInt(),
            note = o.str("note"),
        )
    }

    fun parseAiDay(o: JsonObject, date: LocalDate? = null): PlanDay {
        val d = date ?: o.str("date")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
            ?: throw ToolException("训练日缺少日期")
        val list = parseAiExercises(o.objects("exercises"))
        if (list.isEmpty()) throw ToolException("${d} 没有动作；休息日请不要列出")
        return PlanDay(
            date = d.toEpochDay(),
            title = o.str("title") ?: "训练",
            exercisesJson = encode(list),
            status = DayStatus.PLANNED,
            note = o.str("note"),
        )
    }

    companion object {
        const val META_KEY = "plan_meta"

        /** 给 AI 的训练日输出格式说明 */
        const val DAY_FORMAT = """{"date":"yyyy-MM-dd","title":"训练主题，如 胸+三头","note":"可选","exercises":[{"exercise_id":"动作库里的 id","name":"中文名","sets":组数,"reps":每组次数,"weight_kg":建议重量或 null,"duration_sec":计时动作的秒数否则 null,"rest_sec":组间休息秒数,"note":"可选要点"}]}"""
    }
}

fun PlanSet.markDone(lastWeight: Double?): PlanSet = copy(
    done = true,
    doneReps = doneReps ?: reps,
    doneWeightKg = doneWeightKg ?: weightKg ?: lastWeight,
    doneDurationSec = doneDurationSec ?: durationSec,
)
