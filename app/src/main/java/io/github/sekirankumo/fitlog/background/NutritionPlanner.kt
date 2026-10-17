package io.github.sekirankumo.fitlog.background

import io.github.sekirankumo.fitlog.assistant.OperationService
import io.github.sekirankumo.fitlog.assistant.ToolRegistry
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.objects
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.data.AppPrefs
import io.github.sekirankumo.fitlog.data.DayMacros
import io.github.sekirankumo.fitlog.data.NutritionContext
import io.github.sekirankumo.fitlog.data.NutritionNotice
import io.github.sekirankumo.fitlog.data.NutritionPlanRepository
import io.github.sekirankumo.fitlog.data.NutritionRules
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.Msg
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.ai.extractJsonObject
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.diet.DayQuality
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.data.diet.DietRepository
import io.github.sekirankumo.fitlog.data.diet.formatScore
import io.github.sekirankumo.fitlog.data.macros
import io.github.sekirankumo.fitlog.data.plan.PlanRepository
import io.github.sekirankumo.fitlog.data.plan.cn
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 每天第一次打开 App 时（凌晨 4 点以后），让 AI 像营养师一样看前几天的表现，
 * 重排「今天起 7 天」每天的营养目标，直接生效并记入调整记录（可撤销），今日页提示一次。
 * 没配 AI、关掉了自动调整、最近一周没有饮食记录时不调；网络失败下次打开再试。
 */
class NutritionPlanner(
    private val settings: AiSettingsRepository,
    private val client: OpenAiClient,
    private val repo: NutritionPlanRepository,
    private val db: AppDatabase,
    private val plans: PlanRepository,
    private val prefs: AppPrefs,
    private val operations: OperationService,
    private val registry: ToolRegistry,
    /** 先处理漏打卡（训练可能被重排），再安排营养 */
    private val beforeCheck: suspend () -> Unit,
) {
    private val mutex = Mutex()
    private var lastAttempt = 0L

    suspend fun dailyCheck(now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        // 凌晨 4 点前打开不算新的一天：前一天可能还没吃完
        if (now.hour < 4) return@withLock
        val today = now.toLocalDate()
        val kv = db.kvDao()
        val checked = kv.get(KEY_CHECKED)?.toLongOrNull()?.let { LocalDate.ofEpochDay(it) }
        if (checked != null && !checked.isBefore(today)) return@withLock
        // 失败后别每次切回 App 都重试
        if (System.currentTimeMillis() - lastAttempt < RETRY_MS) return@withLock
        lastAttempt = System.currentTimeMillis()

        // 过去的日子先固定下来：以后改目标、重新安排都不再影响历史记录的对比
        val freezeFrom = maxOf(checked?.plusDays(1) ?: today.minusDays(7), today.minusDays(60))
        repo.freeze(freezeFrom, today.minusDays(1))

        suspend fun done() = kv.put(KeyValue(KEY_CHECKED, today.toEpochDay().toString()))
        if (!prefs.autoNutrition.value) return@withLock done()
        val config = settings.current()
        val ref = config.assistantModel?.takeIf { it.model.isNotBlank() }
        val provider = ref?.let { config.provider(it.providerId) }
        if (ref == null || provider == null) return@withLock done()

        val foodDao = db.foodDao()
        val recent = foodDao.range(DietRepository.dayStart(today.minusDays(7)), DietRepository.dayStart(today))
        // 最近一周都没记饮食：没法按表现调整
        if (recent.isEmpty()) return@withLock done()

        runCatching { beforeCheck() }
        val ctx = repo.context()
        val horizon = (0 until DAYS).map { today.plusDays(it.toLong()) }
        val before = horizon.map { repo.targetAt(it).macros() }

        val result = client.chat(
            provider,
            ref.model,
            listOf(Msg.system(SYSTEM), Msg.user(describe(today, ctx, horizon, before))),
            jsonMode = true,
            temperature = 0.3,
        )
        val answer = extractJsonObject(result.content ?: return@withLock)
        val byDate = answer.objects("days").associateBy { it.str("date")?.take(10) }

        // 先按同样的规则算出最终的数，和现在的比一比，没变化就不打扰
        val proposedBase = answer.long("baseline_kcal")?.toInt()
        val daysSince = ctx.baselineSince?.let { (today.toEpochDay() - it.toEpochDay()).toInt() }
        val newBase = NutritionRules.nextBaseline(ctx.baseline, proposedBase, ctx.defaults.kcal, ctx.floor, daysSince)
        val range = NutritionRules.autoRange(newBase)
        val after = horizon.mapIndexed { i, d ->
            val o = byDate[d.toString()]
            NutritionRules.normalize(
                kcal = o?.long("kcal")?.toInt() ?: before[i].kcal,
                proteinG = o?.long("protein_g")?.toInt() ?: before[i].proteinG,
                fatG = o?.long("fat_g")?.toInt() ?: before[i].fatG,
                weightKg = ctx.weightKg,
                floor = ctx.floor,
                kcalRange = range,
            )
        }
        if (after == before && newBase == ctx.baseline) return@withLock done()

        val summary = answer.str("summary")?.take(60) ?: "根据前几天的表现调整了接下来 7 天"
        val tool = registry["set_nutrition_plan"] ?: return@withLock
        val args = buildJsonObject {
            put("auto", true)
            put("label", "AI 调整营养计划：$summary")
            if (newBase != ctx.baseline) put("baseline_kcal", newBase)
            putJsonArray("days") {
                horizon.forEachIndexed { i, d ->
                    addJsonObject {
                        put("date", d.toString())
                        put("kcal", after[i].kcal)
                        put("protein_g", after[i].proteinG)
                        put("fat_g", after[i].fatG)
                        byDate[d.toString()]?.str("note")?.let { put("note", it) }
                    }
                }
            }
        }
        val opId = operations.runWrite(tool, args).second
        repo.setNotice(NutritionNotice(opId, summary, today.toEpochDay(), before[0].kcal, repo.targetAt(today).kcal))
        done()
    }

    /** 给 AI 的情况说明 */
    private suspend fun describe(today: LocalDate, ctx: NutritionContext, horizon: List<LocalDate>, current: List<DayMacros>): String = buildString {
        val foodDao = db.foodDao()
        appendLine("今天是 ${today.cn()}，当前时间 ${TimeUtil.nowForPrompt()}")
        appendLine("用户信息：${ctx.profile.describe(ctx.weightKg)}")
        appendLine("默认目标（按身体数据算的）：${ctx.defaults.macros().text()}")
        appendLine("每天热量下限：${ctx.floor}kcal（自动调整时每天热量只能在基础热量 ±15% 内）")
        val since = ctx.baselineSince
        val canChange = since == null || today.toEpochDay() - since.toEpochDay() >= NutritionRules.BASELINE_INTERVAL_DAYS
        appendLine(
            "基础热量：${ctx.baseline}kcal" +
                (since?.let { "（$it 起用）" } ?: "（还没调整过，等于默认目标）") +
                if (canChange) "；这次可以调整（每次最多 ±150）" else "；离上次调整不满 7 天，这次不能调整，baseline_kcal 填 null",
        )

        appendLine()
        appendLine("最近 7 天（目标 / 实际 / 训练）：")
        for (i in 7 downTo 1) {
            val d = today.minusDays(i.toLong())
            val target = repo.targetAt(d).macros()
            val entries = foodDao.range(DietRepository.dayStart(d), DietRepository.dayStart(d.plusDays(1)))
            val training = plans.get(d)?.let { "${it.title}（${DayStatus.label(it.status)}）" } ?: "休息"
            val actual = if (entries.isEmpty()) {
                "没有记录"
            } else {
                val t = DayTotals.of(entries)
                val q = DayQuality.of(entries)?.takeIf { it.analyzed > 0 }?.let { "，膳食分 ${formatScore(it.total)}/49" } ?: ""
                "${t.kcal.fmt(0)}kcal 蛋白${t.proteinG.fmt(0)}g 脂肪${t.fatG.fmt(0)}g 碳水${t.carbsG.fmt(0)}g$q"
            }
            appendLine("${d.cn()} 目标 ${target.text()} / 实际 $actual / 训练 $training")
        }
        val todayFood = foodDao.range(DietRepository.dayStart(today), DietRepository.dayStart(today.plusDays(1)))
        if (todayFood.isNotEmpty()) appendLine("今天到现在已吃：${DayTotals.of(todayFood).kcal.fmt(0)}kcal")

        appendLine()
        appendLine(weightTrend())

        appendLine()
        appendLine("接下来 7 天（训练 / 现在的营养安排）：")
        horizon.forEachIndexed { i, d ->
            val training = plans.get(d)?.let { day ->
                val muscles = plans.exercisesOf(day).take(4).joinToString("、") { it.name }
                "${day.title}（$muscles）"
            } ?: "休息"
            appendLine("${d.cn()} 训练 $training / 营养 ${current[i].text()}")
        }
    }

    /** 体重：最近 7 天平均和再往前 7 天平均 */
    private suspend fun weightTrend(): String {
        val records = db.bodyRecordDao().since(TimeUtil.daysAgo(14)).filter { it.weightKg != null }
        if (records.isEmpty()) return "体重：最近两周没有记录"
        val cut = TimeUtil.daysAgo(7)
        val recent = records.filter { it.measuredAt >= cut }.mapNotNull { it.weightKg }
        val earlier = records.filter { it.measuredAt < cut }.mapNotNull { it.weightKg }
        return buildString {
            append("体重：")
            if (recent.isNotEmpty()) append("最近 7 天平均 ${recent.average().fmt()}kg（${recent.size} 次）")
            if (earlier.isNotEmpty()) append("，再往前 7 天平均 ${earlier.average().fmt()}kg（${earlier.size} 次）")
            if (recent.isNotEmpty() && earlier.isNotEmpty()) {
                val pct = (recent.average() - earlier.average()) / earlier.average() * 100
                append("，一周变化 ${if (pct >= 0) "+" else ""}${"%.2f".format(pct)}%")
            }
        }
    }

    private fun DayMacros.text() = "${kcal}kcal 蛋白${proteinG}g 脂肪${fatG}g 碳水${carbsG}g"

    private companion object {
        const val KEY_CHECKED = "nutrition_planned_on"
        const val DAYS = 7
        const val RETRY_MS = 20 * 60 * 1000L

        val SYSTEM = """
            你是专业的运动营养师。每天早上根据用户前几天的实际表现，重新安排「今天起接下来 7 天」每天的营养目标。只输出 JSON。

            调整原则：
            1. 每天的小修正，只看已经过完的日子：
               - 实际热量和目标相差不到 10% 算正常波动，不用补偿。
               - 吃超了：超出部分只补回一半，分摊到接下来 2–3 天，每天最多少 200 kcal，不要让用户饿着硬补。
               - 吃少了：不要让后面加量补回来，只保证接下来按目标吃够，蛋白质优先。
               - 没有记录的日子当作不知道，不补偿。
            2. 基础热量（每天热量围绕它浮动）每周最多调一次，输入里会说明这次能不能调。看 7 天平均体重：
               - 减脂：每周减体重的 0.5%–1% 算正常；明显偏慢可以下调 100–150 kcal，偏快就上调。
               - 增肌：每周增 0.25%–0.5% 算正常，偏离时同样小幅调整。
               - 保持：体重稳定就不动。
               - 体重记录太少就不调。不需要调整时 baseline_kcal 填 null。
            3. 按训练分配：
               - 蛋白质每天基本稳定，每公斤体重 1.6–2.2 g。
               - 脂肪不低于每公斤体重 0.7 g。
               - 剩下的热量给碳水：训练日多，练腿或全身的日子最多，休息日少。
            4. 7 天的平均热量接近基础热量（再加上第 1 条的修正）；每天热量不低于给出的下限。
            5. 考虑用户「需要注意」里写的饮食习惯和身体情况，不给极端方案。
            6. 前几天都正常、训练安排也没变时，按原安排输出，summary 写「按原计划」。

            输出格式（只输出这个 JSON）：
            {"summary":"给用户看的一句话：这次主要怎么调、为什么，40 字以内","baseline_kcal":整数或null,"days":[{"date":"yyyy-MM-dd","kcal":整数,"protein_g":整数,"fat_g":整数,"carbs_g":整数,"note":"这一天的一句说明，12 字以内"}]}
            days 要正好覆盖输入里「接下来 7 天」的每一天。
        """.trimIndent()
    }
}
