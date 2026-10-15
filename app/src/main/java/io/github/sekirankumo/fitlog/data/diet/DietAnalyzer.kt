package io.github.sekirankumo.fitlog.data.diet

import io.github.sekirankumo.fitlog.assistant.bool
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.Msg
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.ai.extractJsonObject
import io.github.sekirankumo.fitlog.data.db.FoodDao
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 在后台给还没分析过的饮食记录和常吃食物补上食材分类和高盐标记：
 * 新版本第一次打开时补旧记录，平时补手动添加、AI 没拆好的记录。用户不用操作。
 * 只发文字（名称、份量、营养），每 10 条合成一次调用。
 */
class DietAnalyzer(
    private val settings: AiSettingsRepository,
    private val client: OpenAiClient,
    private val dao: FoodDao,
) {
    /** 正在补算时的进度（已完成, 总数），没在跑时为 null */
    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    private val mutex = Mutex()
    private var lastFailedAt = 0L

    /**
     * @param force 打开 App 时为 true；平时有新记录触发时，上次失败后 10 分钟内不再重试，免得一直白白调用
     */
    suspend fun run(force: Boolean = false) {
        if (mutex.isLocked) return
        mutex.withLock {
            if (!force && System.currentTimeMillis() - lastFailedAt < 10 * 60_000) return
            val config = settings.current()
            val ref = (config.assistantModel ?: config.visionModel)?.takeIf { it.model.isNotBlank() } ?: return
            val provider = config.provider(ref.providerId) ?: return
            try {
                analyzeEntries(provider, ref.model)
                analyzeFavorites(provider, ref.model)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastFailedAt = System.currentTimeMillis()
            } finally {
                _progress.value = null
            }
        }
    }

    private suspend fun analyzeEntries(provider: io.github.sekirankumo.fitlog.data.ai.AiProvider, model: String) {
        val tried = HashSet<Long>()
        var done = 0
        val total = dao.observePendingCount().first()
        while (true) {
            val batch = dao.pendingEntries(BATCH + tried.size).filter { it.id !in tried }.take(BATCH)
            if (batch.isEmpty()) return
            _progress.value = done to total
            val items = batch.map { e ->
                Item(e.id, e.name, e.amount, e.grams, e.kcal, e.proteinG, e.fatG, e.carbsG)
            }
            val results = ask(provider, model, items)
            for (e in batch) {
                tried += e.id
                val r = results[e.id] ?: continue
                dao.setAnalysis(e.id, encodeComponents(r.first)!!, r.second || e.highSalt == true)
                done++
            }
            _progress.value = done to total
        }
    }

    private suspend fun analyzeFavorites(provider: io.github.sekirankumo.fitlog.data.ai.AiProvider, model: String) {
        val tried = HashSet<Long>()
        while (true) {
            val batch = dao.pendingFavorites(BATCH + tried.size).filter { it.id !in tried }.take(BATCH)
            if (batch.isEmpty()) return
            val items = batch.map { f -> Item(f.id, f.name, f.amount, f.grams, f.kcal, f.proteinG, f.fatG, f.carbsG) }
            val results = ask(provider, model, items)
            for (f in batch) {
                tried += f.id
                val r = results[f.id] ?: continue
                dao.setFavoriteAnalysis(f.id, encodeComponents(r.first)!!, r.second || f.highSalt == true)
            }
        }
    }

    private class Item(
        val id: Long,
        val name: String,
        val amount: String?,
        val grams: Double?,
        val kcal: Double,
        val protein: Double,
        val fat: Double,
        val carbs: Double,
    )

    /** 返回 id → (食材, 是否高盐) */
    private suspend fun ask(
        provider: io.github.sekirankumo.fitlog.data.ai.AiProvider,
        model: String,
        items: List<Item>,
    ): Map<Long, Pair<List<FoodComponent>, Boolean>> {
        val list = items.joinToString("\n") { i ->
            "#${i.id} ${i.name}" + (i.amount?.let { "（$it）" } ?: "") + (i.grams?.let { " ${it.fmt(0)}g" } ?: "") +
                " ${i.kcal.fmt(0)}kcal 蛋白${i.protein.fmt()}g 脂肪${i.fat.fmt()}g 碳水${i.carbs.fmt()}g"
        }
        val prompt = buildString {
            appendLine("下面是用户记录过的食物（#编号 名称 份量 克数 营养）。请按规则把每一条拆成食材并判断是否高盐，克数要和记录的总量、营养大致对得上。")
            appendLine(list)
            appendLine()
            appendLine("""输出 JSON：{"results":[{"id":编号,${DietPrompt.FIELDS}}]}，每条记录都要有。""")
            appendLine()
            append(DietPrompt.RULES)
        }
        val result = client.chat(
            provider,
            model,
            listOf(Msg.system("你是专业的营养师，熟悉中国家常菜、外卖和各类包装食品。只输出 JSON。"), Msg.user(prompt)),
            jsonMode = true,
            temperature = 0.2,
            stream = true,
        )
        val root = extractJsonObject(result.content ?: return emptyMap())
        val out = HashMap<Long, Pair<List<FoodComponent>, Boolean>>()
        (root["results"] as? JsonArray).orEmpty().forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val id = (o["id"] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.trim('#').toLongOrNull() } ?: return@forEach
            val components = parseComponents(o["components"] as? JsonArray) ?: return@forEach
            out[id] = components to (o.bool("high_salt") == true)
        }
        return out
    }

    private companion object {
        const val BATCH = 10
    }
}
