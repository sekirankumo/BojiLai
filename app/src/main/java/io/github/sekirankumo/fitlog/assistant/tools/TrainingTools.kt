package io.github.sekirankumo.fitlog.assistant.tools

import io.github.sekirankumo.fitlog.assistant.AiTool
import io.github.sekirankumo.fitlog.assistant.ToolException
import io.github.sekirankumo.fitlog.assistant.ToolOutcome
import io.github.sekirankumo.fitlog.assistant.arrayProp
import io.github.sekirankumo.fitlog.assistant.bool
import io.github.sekirankumo.fitlog.assistant.booleanProp
import io.github.sekirankumo.fitlog.assistant.integerProp
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.num
import io.github.sekirankumo.fitlog.assistant.numberProp
import io.github.sekirankumo.fitlog.assistant.objectSchema
import io.github.sekirankumo.fitlog.assistant.objects
import io.github.sekirankumo.fitlog.assistant.requireStr
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.assistant.stringArrayProp
import io.github.sekirankumo.fitlog.assistant.stringProp
import io.github.sekirankumo.fitlog.assistant.strings
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.exercise.ExerciseRepository
import io.github.sekirankumo.fitlog.data.exercise.MUSCLE_GROUPS
import io.github.sekirankumo.fitlog.data.plan.PlanAi
import io.github.sekirankumo.fitlog.data.plan.PlanMeta
import io.github.sekirankumo.fitlog.data.plan.PlanRepository
import io.github.sekirankumo.fitlog.data.plan.PlanRequest
import io.github.sekirankumo.fitlog.data.plan.PlanSnapshot
import io.github.sekirankumo.fitlog.data.plan.cn
import io.github.sekirankumo.fitlog.data.plan.markDone
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

private val EXERCISE_ITEM_SCHEMA = objectSchema(
    listOf("exercise_id", "sets"),
    "exercise_id" to stringProp("动作库里的 id（先用 search_exercises 查）"),
    "name" to stringProp("中文名"),
    "sets" to integerProp("组数"),
    "reps" to integerProp("每组次数；计时动作不填"),
    "weight_kg" to numberProp("重量，不确定就不填"),
    "duration_sec" to integerProp("计时动作每组秒数"),
    "rest_sec" to integerProp("组间休息秒数"),
    "note" to stringProp("动作要点"),
)

private val DAY_SCHEMA = objectSchema(
    listOf("date", "title", "exercises"),
    "date" to stringProp("日期 yyyy-MM-dd"),
    "title" to stringProp("训练主题，如 胸+三头"),
    "note" to stringProp("备注"),
    "exercises" to arrayProp("动作列表", EXERCISE_ITEM_SCHEMA),
)

/** 修改计划的工具：执行前拍快照，撤销时还原 */
abstract class PlanWriteTool(protected val plans: PlanRepository) : AiTool {
    override val isWrite = true

    protected fun undoData(snapshot: PlanSnapshot): JsonObject = buildJsonObject {
        put("snapshot", plans.encodeSnapshot(snapshot))
    }

    override suspend fun undo(undoData: JsonObject) {
        undoData.str("snapshot")?.let { plans.restore(plans.decodeSnapshot(it)) }
        if (undoData.containsKey("meta")) {
            val meta = undoData.str("meta")?.let { plans.json.decodeFromString(PlanMeta.serializer(), it) }
            plans.setMeta(meta)
        }
    }
}

class GetPlanTool(private val plans: PlanRepository) : AiTool {
    override val name = "get_training_plan"
    override val description = "查看训练计划概况：计划信息和一段时间内每天的安排与完成状态"
    override val parameters = objectSchema(
        emptyList(),
        "from" to stringProp("开始日期 yyyy-MM-dd，默认 7 天前"),
        "to" to stringProp("结束日期 yyyy-MM-dd，默认 14 天后"),
    )
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val from = args.str("from")?.let { parseDate(it) } ?: LocalDate.now().minusDays(7)
        val to = args.str("to")?.let { parseDate(it) } ?: LocalDate.now().plusDays(14)
        val meta = plans.meta()
        val days = plans.range(from, to)
        if (meta == null && days.isEmpty()) return ToolOutcome("还没有训练计划，可以用 generate_training_plan 生成")
        return ToolOutcome(buildString {
            meta?.let {
                appendLine("计划：${it.name}（${LocalDate.ofEpochDay(it.startDate)} 至 ${LocalDate.ofEpochDay(it.endDate)}）")
                if (it.summary.isNotBlank()) appendLine("思路：${it.summary}")
                if (it.progression.isNotBlank()) appendLine("加量规则：${it.progression}")
            }
            appendLine("$from 到 $to（未列出的日期为休息日）：")
            days.forEach { appendLine(plans.describeDay(it, detailed = false)) }
        }.trim())
    }
}

class GetPlanDayTool(private val plans: PlanRepository) : AiTool {
    override val name = "get_training_day"
    override val description = "查看某天训练的详细内容（含动作和组的编号，打卡和修改时要用）"
    override val parameters = objectSchema(emptyList(), "date" to stringProp("日期 yyyy-MM-dd，默认今天"))
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val date = parseDate(args.str("date"))
        val day = plans.get(date) ?: return ToolOutcome("${date.cn()} 是休息日，没有安排训练")
        return ToolOutcome(plans.describeDay(day, detailed = true))
    }
}

class SearchExercisesTool(private val exercises: ExerciseRepository) : AiTool {
    override val name = "search_exercises"
    override val description = "在动作库里搜索动作，返回 id|名称|器械|主要肌群。安排或替换动作前先查这里"
    override val parameters = objectSchema(
        emptyList(),
        "query" to stringProp("关键词，如 深蹲、划船"),
        "muscle_group" to stringProp("肌群", MUSCLE_GROUPS.keys.toList() + listOf("有氧", "拉伸")),
        "equipment" to stringProp("器械，如 哑铃、徒手、杠铃、器械、弹力带"),
    )
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val list = exercises.search(args.str("query"), args.str("muscle_group"), args.str("equipment"))
        if (list.isEmpty()) return ToolOutcome("没有找到，换个关键词或去掉筛选条件再试")
        return ToolOutcome(list.take(40).joinToString("\n") { it.brief() })
    }
}

class SetPlanDaysTool(plans: PlanRepository) : PlanWriteTool(plans) {
    override val name = "set_training_days"
    override val description = "新建或覆盖某几天的训练安排（整天替换）。要把某天改成休息请用 delete_training_days"
    override val parameters = objectSchema(listOf("days"), "days" to arrayProp("训练日列表", DAY_SCHEMA))

    private fun build(args: JsonObject): List<PlanDay> {
        val days = args.objects("days").map { plans.parseAiDay(it) }
        if (days.isEmpty()) throw ToolException("days 不能为空")
        return days
    }

    override suspend fun preview(args: JsonObject) =
        "设置训练安排：\n" + build(args).joinToString("\n") { plans.describeDay(it, detailed = false) }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val days = build(args)
        val snap = plans.snapshot(days.map { it.date })
        plans.upsertAll(days)
        return ToolOutcome(
            "已设置 ${days.size} 天",
            "修改训练计划：" + days.joinToString("、") { "${LocalDate.ofEpochDay(it.date).cn()} ${it.title}" },
            undoData(snap),
        )
    }
}

class DeletePlanDaysTool(plans: PlanRepository) : PlanWriteTool(plans) {
    override val name = "delete_training_days"
    override val description = "删除某几天的训练（变成休息日）"
    override val parameters = objectSchema(listOf("dates"), "dates" to stringArrayProp("日期列表 yyyy-MM-dd"))

    private fun dates(args: JsonObject) = args.strings("dates").map { parseDate(it) }.ifEmpty { throw ToolException("dates 不能为空") }

    override suspend fun preview(args: JsonObject) = "把这些日期改为休息日：" + dates(args).joinToString("、") { it.cn() }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val dates = dates(args).map { it.toEpochDay() }
        val snap = plans.snapshot(dates)
        plans.deleteDates(dates)
        return ToolOutcome("已删除", "删除训练日：" + dates.joinToString("、") { LocalDate.ofEpochDay(it).cn() }, undoData(snap))
    }
}

class ShiftPlanTool(plans: PlanRepository) : PlanWriteTool(plans) {
    override val name = "shift_training_plan"
    override val description = "把某天及之后所有未完成的训练整体顺延或提前若干天（比如出差、生病时）"
    override val parameters = objectSchema(
        listOf("from_date", "days"),
        "from_date" to stringProp("从哪天开始 yyyy-MM-dd"),
        "days" to integerProp("移动天数，正数顺延，负数提前"),
    )

    override suspend fun preview(args: JsonObject): String {
        val n = args.long("days") ?: 0
        return "${parseDate(args.str("from_date")).cn()} 及之后的训练${if (n >= 0) "顺延" else "提前"} ${kotlin.math.abs(n)} 天"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val from = parseDate(args.str("from_date"))
        val n = args.long("days") ?: throw ToolException("缺少 days")
        if (n == 0L) throw ToolException("days 不能为 0")
        val end = plans.lastDate() ?: return ToolOutcome("没有需要移动的训练")
        val moving = plans.range(from, end).filter { it.status != DayStatus.DONE }
        if (moving.isEmpty()) return ToolOutcome("没有需要移动的训练")
        val moved = moving.map { it.copy(date = it.date + n, status = DayStatus.PLANNED) }
        val conflicts = plans.range(LocalDate.ofEpochDay(moved.minOf { it.date }), LocalDate.ofEpochDay(moved.maxOf { it.date }))
            .filter { it.status == DayStatus.DONE && moved.any { m -> m.date == it.date } }
        if (conflicts.isNotEmpty()) throw ToolException("移动后会和已完成的训练（${conflicts.joinToString { LocalDate.ofEpochDay(it.date).toString() }}）重叠")
        val snap = plans.snapshot(moving.map { it.date } + moved.map { it.date })
        plans.deleteDates(moving.map { it.date })
        plans.upsertAll(moved)
        return ToolOutcome(
            "已移动 ${moving.size} 天",
            "${from.cn()} 起的 ${moving.size} 次训练${if (n > 0) "顺延" else "提前"} ${kotlin.math.abs(n)} 天",
            undoData(snap),
        )
    }
}

class ReplaceExerciseTool(plans: PlanRepository, private val exercises: ExerciseRepository) : PlanWriteTool(plans) {
    override val name = "replace_exercise"
    override val description = "把计划里的某个动作换成另一个动作（做不了、没器械、受伤时）。先用 search_exercises 找合适的替代动作"
    override val parameters = objectSchema(
        listOf("old_exercise", "new_exercise_id"),
        "old_exercise" to stringProp("要替换的动作 id 或中文名"),
        "new_exercise_id" to stringProp("新动作的 id"),
        "from_date" to stringProp("从哪天开始替换，默认今天"),
        "to_date" to stringProp("到哪天为止，默认整个计划"),
        "reps" to integerProp("新动作每组次数，不填沿用原来的"),
    )

    private suspend fun affected(args: JsonObject): Triple<List<PlanDay>, String, io.github.sekirankumo.fitlog.data.exercise.Exercise> {
        val old = args.requireStr("old_exercise")
        val new = exercises[args.str("new_exercise_id")] ?: throw ToolException("动作库里没有 ${args.str("new_exercise_id")}，请先用 search_exercises 查找")
        val from = parseDate(args.str("from_date"))
        val to = args.str("to_date")?.let { parseDate(it) } ?: plans.lastDate() ?: from
        val days = plans.range(from, to).filter { day ->
            day.status != DayStatus.DONE && plans.exercisesOf(day).any { it.exerciseId == old || it.name == old }
        }
        if (days.isEmpty()) throw ToolException("在 $from 到 $to 的未完成训练里没有找到动作「$old」")
        return Triple(days, old, new)
    }

    override suspend fun preview(args: JsonObject): String {
        val (days, old, new) = affected(args)
        val oldName = exercises[old]?.name ?: old
        return "把「$oldName」换成「${new.name}」，涉及 ${days.size} 天：" + days.joinToString("、") { LocalDate.ofEpochDay(it.date).toString() }
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val (days, old, new) = affected(args)
        val reps = args.long("reps")?.toInt()
        val snap = plans.snapshot(days.map { it.date })
        val updated = days.map { day ->
            val list = plans.exercisesOf(day).map { ex ->
                if (ex.exerciseId == old || ex.name == old) {
                    ex.copy(
                        exerciseId = new.id,
                        name = new.name,
                        // 换了动作，原来的重量不再适用
                        sets = ex.sets.map { s -> s.copy(weightKg = null, reps = reps ?: s.reps, done = false) },
                    )
                } else ex
            }
            day.copy(exercisesJson = plans.encode(list))
        }
        plans.upsertAll(updated)
        val oldName = exercises[old]?.name ?: old
        return ToolOutcome("已替换 ${days.size} 天", "动作替换：$oldName → ${new.name}（${days.size} 天）", undoData(snap))
    }
}

class CheckinTool(plans: PlanRepository) : PlanWriteTool(plans) {
    override val name = "checkin_training"
    override val description = "训练打卡：记录某天各组的完成情况。编号来自 get_training_day"
    override val parameters = objectSchema(
        emptyList(),
        "date" to stringProp("日期，默认今天"),
        "updates" to arrayProp(
            "每组的完成记录",
            objectSchema(
                listOf("exercise_index"),
                "exercise_index" to integerProp("动作编号"),
                "set_index" to integerProp("组编号；不填表示这个动作的所有组"),
                "reps" to integerProp("实际次数，不填按目标"),
                "weight_kg" to numberProp("实际重量，不填按目标"),
                "duration_sec" to integerProp("实际秒数"),
                "done" to booleanProp("是否完成，默认 true；传 false 可以取消打卡"),
            ),
        ),
        "mark_day_done" to booleanProp("是否把当天全部标为完成"),
    )

    private suspend fun compute(args: JsonObject): Pair<PlanDay, PlanDay> {
        val date = parseDate(args.str("date"))
        val day = plans.get(date) ?: throw ToolException("${date.cn()} 没有安排训练")
        val weights = plans.lastWeights()
        val list = plans.exercisesOf(day).toMutableList()
        for (u in args.objects("updates")) {
            val ei = u.long("exercise_index")?.toInt() ?: continue
            val ex = list.getOrNull(ei) ?: throw ToolException("没有编号为 $ei 的动作")
            val target = u.long("set_index")?.toInt()
            val done = u.bool("done") ?: true
            val sets = ex.sets.mapIndexed { si, s ->
                if (target != null && target != si) return@mapIndexed s
                if (!done) return@mapIndexed s.copy(done = false, doneReps = null, doneWeightKg = null, doneDurationSec = null)
                s.copy(
                    doneReps = u.long("reps")?.toInt() ?: s.doneReps,
                    doneWeightKg = u.num("weight_kg") ?: s.doneWeightKg,
                    doneDurationSec = u.long("duration_sec")?.toInt() ?: s.doneDurationSec,
                ).markDone(weights[plans.exerciseKey(ex)])
            }
            list[ei] = ex.copy(sets = sets)
        }
        var newList = list.toList()
        if (args.bool("mark_day_done") == true) {
            newList = newList.map { ex -> ex.copy(sets = ex.sets.map { if (it.done) it else it.markDone(weights[plans.exerciseKey(ex)]) }) }
        }
        val updated = plans.withAutoStatus(day.copy(exercisesJson = plans.encode(newList)), newList)
        return day to updated
    }

    override suspend fun preview(args: JsonObject): String {
        val (_, new) = compute(args)
        return "训练打卡：\n" + plans.describeDay(new, detailed = true)
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val (old, new) = compute(args)
        val snap = PlanSnapshot(listOf(old.date), listOf(old))
        plans.upsert(new)
        val doneSets = plans.exercisesOf(new).sumOf { ex -> ex.sets.count { it.done } }
        val total = plans.exercisesOf(new).sumOf { it.sets.size }
        return ToolOutcome(
            "已打卡，当天完成 $doneSets/$total 组，状态：${DayStatus.label(new.status)}",
            "训练打卡 ${LocalDate.ofEpochDay(new.date).cn()}：完成 $doneSets/$total 组",
            undoData(snap),
        )
    }
}

class GeneratePlanTool(plans: PlanRepository, private val ai: PlanAi) : PlanWriteTool(plans) {
    override val name = "generate_training_plan"
    override val description = "让 AI 生成一份新的训练计划，会替换开始日期之后的原计划"
    override val parameters = objectSchema(
        listOf("goal", "days_per_week", "weeks"),
        "goal" to stringProp("训练目标，如 增肌、减脂、力量、塑形"),
        "days_per_week" to integerProp("每周训练几天"),
        "weeks" to integerProp("持续几周"),
        "session_minutes" to integerProp("每次多少分钟，默认 60"),
        "equipment" to stringProp("可用器材，默认健身房"),
        "notes" to stringProp("伤病、偏好等其他要求"),
        "start_date" to stringProp("开始日期，默认今天"),
    )

    private fun request(args: JsonObject) = PlanRequest(
        goal = args.requireStr("goal"),
        daysPerWeek = (args.long("days_per_week") ?: 3).toInt().coerceIn(1, 7),
        weeks = (args.long("weeks") ?: 4).toInt().coerceIn(1, 16),
        sessionMinutes = (args.long("session_minutes") ?: 60).toInt(),
        equipment = args.str("equipment") ?: "健身房",
        notes = args.str("notes") ?: "",
        startDate = parseDate(args.str("start_date")),
    )

    override suspend fun preview(args: JsonObject): String {
        val r = request(args)
        return "生成新的训练计划：${r.goal}，每周 ${r.daysPerWeek} 练，共 ${r.weeks} 周，从 ${r.startDate} 开始（会替换之后的原计划）"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val plan = ai.generate(request(args))
        val start = plan.meta.startDate
        val end = maxOf(plan.meta.endDate, plans.lastDate()?.toEpochDay() ?: plan.meta.endDate)
        // 已完成的训练保留，新计划在这些日期上的安排让位
        val done = plans.range(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end)).filter { it.status == DayStatus.DONE }
        val oldMeta = plans.meta()?.let { plans.json.encodeToString(PlanMeta.serializer(), it) } ?: ""
        val snap = plans.replaceRange(start, end, plan.days.filter { d -> done.none { it.date == d.date } } + done)
        plans.setMeta(plan.meta)
        val undo = buildJsonObject {
            put("snapshot", plans.encodeSnapshot(snap))
            put("meta", oldMeta)
        }
        return ToolOutcome(
            "已生成：${plan.meta.name}，${plan.days.size} 个训练日。${plan.meta.summary}",
            "生成训练计划「${plan.meta.name}」（${plan.days.size} 个训练日）",
            undo,
        )
    }
}

class ReplanTool(plans: PlanRepository, private val ai: PlanAi) : PlanWriteTool(plans) {
    override val name = "replan_training"
    override val description = "让 AI 根据最近的完成情况、体重变化和用户说明，重新安排接下来一段时间的训练"
    override val parameters = objectSchema(
        listOf("reason"),
        "reason" to stringProp("为什么要调整，例如：最近太累、体重没变化、下周出差"),
        "days_ahead" to integerProp("重新安排未来多少天，默认 14"),
    )

    override suspend fun preview(args: JsonObject): String {
        val n = args.long("days_ahead") ?: 14
        return "让 AI 重新安排接下来 $n 天的训练（原因：${args.str("reason")}）"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val today = LocalDate.now()
        val to = today.plusDays((args.long("days_ahead") ?: 14).coerceIn(3, 42) - 1)
        val reason = args.requireStr("reason")
        val (days, summary) = try {
            ai.replan(reason, today, to)
        } catch (e: Exception) {
            if (args.bool("allow_fallback") != true) throw e
            return localFallback(today, reason, e.message)
        }
        // 已完成（包括今天已经打了部分卡）的训练保留，AI 在这些日期上的安排让位
        val done = plans.range(today, to).filter { day ->
            day.status == DayStatus.DONE || plans.exercisesOf(day).any { ex -> ex.sets.any { it.done } }
        }
        val snap = plans.replaceRange(today.toEpochDay(), to.toEpochDay(), days.filter { d -> done.none { it.date == d.date } } + done)
        return ToolOutcome(summary, "${args.str("label") ?: "AI 调整计划"}：$summary", undoData(snap))
    }

    /** AI 不可用时的兜底：把漏掉的训练挪到最近的空闲日，没有空闲日就整体顺延一天 */
    private suspend fun localFallback(today: LocalDate, reason: String, error: String?): ToolOutcome {
        val missed = plans.range(today.minusDays(7), today.minusDays(1)).filter { it.status == DayStatus.MISSED }.takeLast(1)
        if (missed.isEmpty()) throw ToolException("AI 调整失败：$error")
        val workout = missed.last()
        val window = plans.range(today, today.plusDays(3))
        val free = (0L..3L).map { today.plusDays(it) }.firstOrNull { d -> window.none { it.date == d.toEpochDay() } }
        val (snap, text) = if (free != null) {
            val snap = plans.snapshot(listOf(free.toEpochDay()))
            plans.upsert(workout.copy(date = free.toEpochDay(), status = DayStatus.PLANNED, completedAt = null))
            snap to "把漏掉的「${workout.title}」挪到 ${free.cn()}"
        } else {
            val end = plans.lastDate() ?: today
            val moving = plans.range(today, end).filter { it.status != DayStatus.DONE }
            val moved = moving.map { it.copy(date = it.date + 1) } +
                workout.copy(date = today.toEpochDay(), status = DayStatus.PLANNED, completedAt = null)
            val snap = plans.snapshot(moving.map { it.date } + moved.map { it.date })
            plans.deleteDates(moving.map { it.date })
            plans.upsertAll(moved)
            snap to "今天补练漏掉的「${workout.title}」，之后的训练顺延一天"
        }
        return ToolOutcome(text, "漏打卡自动调整（AI 不可用，按规则处理）：$text", undoData(snap))
    }
}
