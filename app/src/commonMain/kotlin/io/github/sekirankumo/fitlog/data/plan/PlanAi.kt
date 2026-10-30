package io.github.sekirankumo.fitlog.data.plan

import kotlinx.datetime.*
import io.github.sekirankumo.fitlog.util.*
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.objects
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.data.ProfileRepository
import io.github.sekirankumo.fitlog.data.ai.AiException
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.Msg
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.ai.extractJsonObject
import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.exercise.ExerciseRepository
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.serialization.json.JsonObject

/** 生成计划的参数 */
data class PlanRequest(
    val goal: String,
    val daysPerWeek: Int,
    val weeks: Int,
    val sessionMinutes: Int,
    val equipment: String,
    val notes: String,
    val startDate: LocalDate,
)

data class GeneratedPlan(val meta: PlanMeta, val days: List<PlanDay>)

/** 训练计划相关的 AI 调用：生成新计划、根据近况重新安排 */
class PlanAi(
    private val settings: AiSettingsRepository,
    private val client: OpenAiClient,
    private val plans: PlanRepository,
    private val exercises: ExerciseRepository,
    private val profile: ProfileRepository,
    private val bodyDao: BodyRecordDao,
) {
    /** 正在进行的 AI 请求已收到的字数，界面用来显示进度；空闲时为 null */
    val progress = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    private suspend fun ask(system: String, user: String): JsonObject = try {
        progress.value = 0
        askOnce(system, user)
    } finally {
        progress.value = null
    }

    private suspend fun askOnce(system: String, user: String): JsonObject {
        val config = settings.current()
        val ref = config.assistantModel?.takeIf { it.model.isNotBlank() }
            ?: throw AiException("还没有配置 AI 助手模型")
        val provider = config.provider(ref.providerId) ?: throw AiException("模型对应的服务已被删除")
        val result = client.chat(
            provider,
            ref.model,
            listOf(Msg.system(system), Msg.user(user)),
            jsonMode = true,
            temperature = 0.3,
            stream = true,
            onProgress = { progress.value = it },
        )
        return extractJsonObject(result.content ?: throw AiException("AI 没有返回内容"))
    }

    private suspend fun userContext(): String {
        val p = profile.current()
        val weights = bodyDao.since(TimeUtil.daysAgo(30)).filter { it.weightKg != null }
        val trend = if (weights.size >= 2) {
            val newest = weights.first().weightKg!!
            val oldest = weights.last().weightKg!!
            "最近 30 天体重从 ${oldest.fmt()}kg 变为 ${newest.fmt()}kg（${weights.size} 次记录）"
        } else {
            "最近 30 天体重记录不足"
        }
        return "用户信息：${p.describe(bodyDao.latestWeight()?.weightKg)}\n$trend"
    }

    /** weekday 一般是 1–7，也兼容「周一」「星期三」这类写法 */
    private fun weekdayOf(o: JsonObject): Int? {
        o.long("weekday")?.let { return it.toInt() }
        val text = o.str("weekday") ?: return null
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        return names.indexOfFirst { text.endsWith(it) || (it == "日" && text.endsWith("天")) }.takeIf { it >= 0 }?.plus(1)
    }

    suspend fun generate(req: PlanRequest): GeneratedPlan {
        val user = """
            ${userContext()}

            请设计一份训练计划：
            - 目标：${req.goal}
            - 每周训练 ${req.daysPerWeek} 天，每次约 ${req.sessionMinutes} 分钟
            - 可用器材：${req.equipment}
            - 其他要求：${req.notes.ifBlank { "无" }}

            只能从下面的动作库里选动作（格式 id|名称|器械|主要肌群），exercise_id 必须原样使用：
            ${exercises.catalog()}

            输出一周的模板（weekday 1=周一 … 7=周日，休息日不要列出），JSON 格式：
            {"name":"计划名称","summary":"一两句话说明思路","progression":"之后几周如何加量的规则","week":[{"weekday":1,"title":"训练主题","exercises":[{"exercise_id":"…","name":"…","sets":4,"reps":8,"weight_kg":null,"duration_sec":null,"rest_sec":90,"note":"要点"}]}]}

            要求：
            1. 训练日之间合理安排休息，同一肌群间隔至少 48 小时。
            2. 每次训练 4–7 个动作，先复合动作后孤立动作，时长符合要求。
            3. 不知道用户的力量水平，weight_kg 一律填 null，由用户打卡时填写。
            4. 计时动作（平板支撑、有氧、拉伸）用 duration_sec，reps 填 null。
            5. 有伤病或限制时避开相关动作。
        """.trimIndent()
        val root = ask("你是经验丰富的私人教练，熟悉增肌、减脂和体能训练。只输出 JSON。", user)

        val week = root.objects("week")
        if (week.isEmpty()) throw AiException("AI 没有给出训练安排")
        val days = ArrayList<PlanDay>()
        val start = req.startDate
        val end = start.plusWeeks(req.weeks.toLong()).minusDays(1)
        var d = start
        while (!d.isAfter(end)) {
            val template = week.firstOrNull { weekdayOf(it) == d.dayOfWeek.isoDayNumber }
            // 个别训练日格式有问题时跳过，不影响整个计划
            if (template != null) runCatching { plans.parseAiDay(template, d) }.onSuccess { days += it }
            d = d.plusDays(1)
        }
        if (days.isEmpty()) throw AiException("AI 给出的计划没有训练日")
        val meta = PlanMeta(
            name = root.str("name") ?: "${req.goal}训练计划",
            goal = req.goal,
            summary = root.str("summary") ?: "",
            progression = root.str("progression") ?: "",
            startDate = start.toEpochDay(),
            endDate = end.toEpochDay(),
        )
        return GeneratedPlan(meta, days)
    }

    /**
     * 根据近况重新安排 [from, to] 内的计划。返回新的训练日（范围内没列出的日期为休息）和改动说明。
     */
    suspend fun replan(reason: String, from: LocalDate, to: LocalDate): Pair<List<PlanDay>, String> {
        val history = plans.range(from.minusDays(14), from.minusDays(1))
        val upcoming = plans.range(from, to)
        val meta = plans.meta()
        val user = """
            ${userContext()}
            当前时间：${TimeUtil.nowForPrompt()}
            当前计划：${meta?.name ?: "无名称"}；${meta?.summary ?: ""}；加量规则：${meta?.progression ?: "无"}

            调整原因：$reason

            最近 14 天的训练记录：
            ${history.joinToString("\n") { plans.describeDay(it, detailed = true) }.ifBlank { "（无）" }}

            ${from} 到 ${to} 原来的安排：
            ${upcoming.joinToString("\n") { plans.describeDay(it, detailed = true) }.ifBlank { "（无）" }}

            可用动作库（id|名称|器械|主要肌群）：
            ${exercises.catalog()}

            请重新安排 ${from} 到 ${to} 的训练，输出 JSON：
            {"summary":"用一两句话告诉用户改了什么、为什么","days":[训练日…]}
            训练日格式：${PlanRepository.DAY_FORMAT}

            要求：
            1. 漏掉的训练如果重要（比如大肌群），要合理补回来，但不要把两次大训练挤在相邻两天。
            2. 保持原计划的整体结构和每周训练天数，不要无故删减。
            3. 结合体重变化和完成情况适当调整训练量：完成率低就适当减量，进展顺利可以小幅加量。
            4. 已经填过的重量尽量沿用；date 必须在 ${from} 到 ${to} 之间；休息日不要列出。
        """.trimIndent()
        val root = ask("你是经验丰富的私人教练，负责根据学员的实际情况调整训练计划。只输出 JSON。", user)
        val days = root.objects("days").mapNotNull { runCatching { plans.parseAiDay(it) }.getOrNull() }
            .filter { it.date in from.toEpochDay()..to.toEpochDay() }
            .distinctBy { it.date }
        if (days.isEmpty() && upcoming.isNotEmpty()) throw AiException("AI 返回的计划是空的")
        return days to (root.str("summary") ?: "已重新安排 ${from} 到 ${to} 的训练")
    }

    /** 把语音打卡的话解析成对当天各组的更新 */
    suspend fun parseCheckin(day: PlanDay, said: String): JsonObject {
        val user = """
            今天的训练安排（动作和组的编号从 0 开始）：
            ${plans.describeDay(day, detailed = true)}

            用户说：$said

            请把用户的话转换成打卡记录，输出 JSON：
            {"updates":[{"exercise_index":0,"set_index":0,"reps":8,"weight_kg":60,"duration_sec":null,"done":true}],"mark_day_done":false}
            规则：
            - set_index 为 null 表示这个动作的所有组都按同样的数据记录。
            - 用户只说"做完了"没说数据时，reps/weight_kg 留 null（按目标记录）。
            - 用户说"今天的全部做完了"时 mark_day_done 为 true。
            - 动作名称对不上时按最接近的动作处理。
        """.trimIndent()
        return ask("你负责把健身打卡的口语记录转换成结构化数据。只输出 JSON。", user)
    }
}
