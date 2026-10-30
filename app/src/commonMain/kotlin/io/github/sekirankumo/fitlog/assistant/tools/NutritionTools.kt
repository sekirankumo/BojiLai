package io.github.sekirankumo.fitlog.assistant.tools

import kotlinx.datetime.*
import io.github.sekirankumo.fitlog.util.*
import io.github.sekirankumo.fitlog.assistant.AiTool
import io.github.sekirankumo.fitlog.assistant.ToolException
import io.github.sekirankumo.fitlog.assistant.ToolOutcome
import io.github.sekirankumo.fitlog.assistant.arrayProp
import io.github.sekirankumo.fitlog.assistant.bool
import io.github.sekirankumo.fitlog.assistant.booleanProp
import io.github.sekirankumo.fitlog.assistant.integerProp
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.num
import io.github.sekirankumo.fitlog.assistant.objectSchema
import io.github.sekirankumo.fitlog.assistant.objects
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.assistant.stringProp
import io.github.sekirankumo.fitlog.data.AppPrefs
import io.github.sekirankumo.fitlog.data.DayMacros
import io.github.sekirankumo.fitlog.data.NutritionBaseline
import io.github.sekirankumo.fitlog.data.NutritionPlanRepository
import io.github.sekirankumo.fitlog.data.NutritionRules
import io.github.sekirankumo.fitlog.data.db.NutritionDay
import io.github.sekirankumo.fitlog.data.plan.cn
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

private fun DayMacros.text() = "${kcal}kcal 蛋白${proteinG}g 脂肪${fatG}g 碳水${carbsG}g"

private fun parseDay(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

class GetNutritionPlanTool(private val repo: NutritionPlanRepository, private val prefs: AppPrefs) : AiTool {
    override val name = "get_nutrition_plan"
    override val description = "查看每天的营养目标安排（热量和三大营养素）。没有单独安排的日子用默认目标。"
    override val parameters = objectSchema(
        emptyList(),
        "from" to stringProp("开始日期 yyyy-MM-dd，默认今天"),
        "days" to integerProp("查看多少天，默认 7，最多 31"),
    )
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val from = parseDay(args.str("from")) ?: LocalDate.now()
        val count = (args.long("days") ?: 7).toInt().coerceIn(1, 31)
        val ctx = repo.context()
        val byDate = repo.range(from, from.plusDays(count - 1L)).associateBy { it.date }
        val text = buildString {
            appendLine("默认目标：${ctx.defaults.kcal}kcal 蛋白${ctx.defaults.proteinG}g 脂肪${ctx.defaults.fatG}g 碳水${ctx.defaults.carbsG}g")
            appendLine("基础热量：${ctx.baseline}kcal" + (ctx.baselineSince?.let { "（$it 起）" } ?: "（没有调整过）") + "；每天热量下限 ${ctx.floor}kcal")
            appendLine("每天自动调整：${if (prefs.autoNutrition.value) "开" else "关"}")
            for (i in 0 until count) {
                val d = from.plusDays(i.toLong())
                val day = byDate[d.toEpochDay()]
                val m = if (day != null) DayMacros(day.kcal, day.proteinG, day.fatG, day.carbsG) else
                    DayMacros(ctx.defaults.kcal, ctx.defaults.proteinG, ctx.defaults.fatG, ctx.defaults.carbsG)
                val source = when (day?.source) {
                    NutritionDay.SOURCE_AI -> "AI 安排" + (day.note?.let { "：$it" } ?: "")
                    NutritionDay.SOURCE_DEFAULT -> "当天的默认目标"
                    else -> "默认目标"
                }
                appendLine("${d.cn()} ${m.text()}（$source）")
            }
        }
        return ToolOutcome(text.trim())
    }
}

/**
 * 安排某几天的营养目标（只能是今天及以后）。数值会按安全规则校正：热量不低于下限，
 * 蛋白质、脂肪不低于按体重算的下限，碳水用剩下的热量算。
 * 每天自动调整也用这个工具（auto = true 时每天热量限制在基础热量 ±15%）。
 */
class SetNutritionPlanTool(private val repo: NutritionPlanRepository) : AiTool {
    override val name = "set_nutrition_plan"
    override val description = "安排或修改某几天的营养目标（今天及以后）。每天给热量、蛋白质、脂肪；碳水会用剩下的热量自动算。" +
        "数值会按安全规则校正（热量不低于基础代谢，蛋白质每公斤≥1.6g，脂肪每公斤≥0.7g）。只想改默认目标时用 update_user_profile。"
    override val parameters = objectSchema(
        listOf("days"),
        "days" to arrayProp(
            "每天的安排",
            objectSchema(
                listOf("date", "kcal", "protein_g", "fat_g"),
                "date" to stringProp("日期 yyyy-MM-dd，不能早于今天"),
                "kcal" to integerProp("热量 kcal"),
                "protein_g" to integerProp("蛋白质 g"),
                "fat_g" to integerProp("脂肪 g"),
                "note" to stringProp("给用户看的一句说明，12 字以内，例如「练腿日，碳水多一点」"),
            ),
        ),
        "baseline_kcal" to integerProp("可选：同时调整基础热量（每天热量围绕它上下浮动）"),
        "label" to stringProp("这次安排的一句话说明，会显示在调整记录里"),
    )
    override val isWrite = true

    override suspend fun preview(args: JsonObject): String {
        val items = args.objects("days")
        if (items.isEmpty()) throw ToolException("没有要安排的日子")
        val first = items.first()
        return "安排 ${items.size} 天的营养目标" + (args.str("label")?.let { "：$it" } ?: "") +
            "（例如 ${first.str("date")} ${first.long("kcal")}kcal）"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val today = LocalDate.now()
        val ctx = repo.context()
        val auto = args.bool("auto") == true
        val items = args.objects("days")
        if (items.isEmpty()) throw ToolException("没有要安排的日子")

        // 基础热量：自动调整受「每周最多一次、每次最多 150」限制；对话里用户要求的只守范围
        val proposedBase = args.long("baseline_kcal")?.toInt()
        val daysSince = ctx.baselineSince?.let { (today.toEpochDay() - it.toEpochDay()).toInt() }
        val newBase = when {
            proposedBase == null -> ctx.baseline
            auto -> NutritionRules.nextBaseline(ctx.baseline, proposedBase, ctx.defaults.kcal, ctx.floor, daysSince)
            else -> proposedBase.coerceIn(NutritionRules.baselineRange(ctx.defaults.kcal, ctx.floor))
        }
        val range = if (auto) NutritionRules.autoRange(newBase) else null

        val now = nowMillis()
        val days = items.map { o ->
            val date = parseDay(o.str("date")) ?: throw ToolException("日期格式不对：${o.str("date")}")
            if (date.isBefore(today)) throw ToolException("只能安排今天及以后的日子（$date 已经过去了）")
            val m = NutritionRules.normalize(
                kcal = o.num("kcal")?.roundToInt() ?: ctx.baseline,
                proteinG = o.num("protein_g")?.roundToInt() ?: ctx.defaults.proteinG,
                fatG = o.num("fat_g")?.roundToInt() ?: ctx.defaults.fatG,
                weightKg = ctx.weightKg,
                floor = ctx.floor,
                kcalRange = range,
            )
            NutritionDay(date.toEpochDay(), m.kcal, m.proteinG, m.fatG, m.carbsG, o.str("note")?.take(20), NutritionDay.SOURCE_AI, now)
        }.distinctBy { it.date }

        val undo = repo.snapshot(days.map { it.date })
        repo.upsert(days)
        if (newBase != ctx.baseline) repo.setBaseline(NutritionBaseline(newBase, today.toEpochDay()))

        val result = buildString {
            if (newBase != ctx.baseline) appendLine("基础热量调整为 ${newBase}kcal")
            days.forEach { d ->
                appendLine("${LocalDate.ofEpochDay(d.date)} ${DayMacros(d.kcal, d.proteinG, d.fatG, d.carbsG).text()}" + (d.note?.let { "（$it）" } ?: ""))
            }
        }
        val label = args.str("label") ?: "安排 ${days.size} 天的营养目标"
        return ToolOutcome("已保存（按安全规则校正后的实际数值）：\n" + result.trim(), label, undo)
    }

    override suspend fun undo(undoData: JsonObject) = repo.restore(undoData)
}

class ClearNutritionPlanTool(private val repo: NutritionPlanRepository) : AiTool {
    override val name = "clear_nutrition_plan"
    override val description = "清除今天及以后的营养安排，恢复按默认目标，同时把基础热量恢复成默认。"
    override val parameters = objectSchema(
        emptyList(),
        "from" to stringProp("从哪天开始清除 yyyy-MM-dd，默认今天，不能早于今天"),
        "keep_baseline" to booleanProp("为 true 时保留 AI 调整过的基础热量，默认 false"),
    )
    override val isWrite = true

    override suspend fun preview(args: JsonObject) = "清除${args.str("from") ?: "今天"}起的营养安排，恢复默认目标"

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val today = LocalDate.now()
        val from = (parseDay(args.str("from")) ?: today).let { if (it.isBefore(today)) today else it }
        val dates = repo.range(from, from.plusYears(5)).map { it.date }
        val undo = repo.snapshot(dates)
        repo.delete(dates)
        if (args.bool("keep_baseline") != true) repo.setBaseline(null)
        return ToolOutcome("已清除 ${dates.size} 天的安排，之后按默认目标", "恢复默认营养目标", undo)
    }

    override suspend fun undo(undoData: JsonObject) = repo.restore(undoData)
}
