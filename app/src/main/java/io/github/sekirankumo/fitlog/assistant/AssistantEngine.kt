package io.github.sekirankumo.fitlog.assistant

import io.github.sekirankumo.fitlog.assistant.tools.describe
import io.github.sekirankumo.fitlog.data.ProfileRepository
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.Msg
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.ai.ToolCall
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.util.TimeUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.atomic.AtomicLong

/** 对话界面上显示的一项 */
sealed interface ChatItem {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatItem
    data class Assistant(override val id: Long, val text: String) : ChatItem

    /** 已执行的修改，可撤销 */
    data class Action(override val id: Long, val opId: Long?, val summary: String, val undone: Boolean = false) : ChatItem

    /** 等待用户确认的修改 */
    data class Pending(override val id: Long, val previews: List<String>) : ChatItem
    data class Error(override val id: Long, val message: String) : ChatItem
}

/**
 * AI 助手：把用户的话和工具定义发给模型，执行模型要求的工具调用，循环直到模型给出最终回答。
 * 读操作直接执行；写操作先弹确认（可在设置里关闭），执行后写入操作日志以便撤销。
 */
class AssistantEngine(
    private val settings: AiSettingsRepository,
    private val client: OpenAiClient,
    private val registry: ToolRegistry,
    private val operations: OperationService,
    private val db: AppDatabase,
    private val profile: ProfileRepository,
) {
    /** 其他页面跳转到助手时预填的输入内容 */
    val prefill = MutableStateFlow<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val nextId = AtomicLong(1)
    private val history = mutableListOf<JsonObject>()
    private var job: Job? = null
    private var pendingDecision: CompletableDeferred<Boolean>? = null

    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private fun add(item: ChatItem) = _items.update { it + item }
    private fun id() = nextId.getAndIncrement()

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty() || _busy.value) return
        add(ChatItem.User(id(), content))
        history += Msg.user(content)
        job = scope.launch { runLoop() }
    }

    /** 用户对确认卡片的选择 */
    fun decide(approve: Boolean) {
        pendingDecision?.complete(approve)
    }

    fun undo(item: ChatItem.Action) {
        val opId = item.opId ?: return
        scope.launch {
            try {
                operations.undo(opId)
                _items.update { list -> list.map { if (it.id == item.id) item.copy(undone = true) else it } }
                // 让 AI 知道这次撤销，避免后续对话基于过时的数据。
                // AI 正在处理时不能插入（会打断工具调用和结果的对应关系），它下次查询时会看到最新数据
                if (!_busy.value) {
                    history += Msg.user("（我刚在界面上撤销了：${item.summary}）")
                    history += Msg.assistant("好的，已知晓该操作被撤销。")
                }
            } catch (e: Exception) {
                add(ChatItem.Error(id(), e.message ?: "撤销失败"))
            }
        }
    }

    fun clear() {
        pendingDecision?.complete(false)
        job?.cancel()
        history.clear()
        _items.value = emptyList()
        _busy.value = false
    }

    private suspend fun runLoop() {
        _busy.value = true
        try {
            val config = settings.current()
            val ref = config.assistantModel?.takeIf { it.model.isNotBlank() }
                ?: throw IllegalStateException("还没有配置助手模型，请到 设置 → AI 服务 里添加")
            val provider = config.provider(ref.providerId)
                ?: throw IllegalStateException("助手模型对应的服务已被删除，请重新选择")

            repeat(MAX_STEPS) {
                val messages = listOf(Msg.system(systemPrompt())) + trimmedHistory()
                val result = client.chat(provider, ref.model, messages, registry.schemas)
                history += result.toMessage()
                result.content?.let { add(ChatItem.Assistant(id(), it)) }
                if (result.toolCalls.isEmpty()) return

                val writes = mutableListOf<Pair<ToolCall, JsonObject>>()
                for (call in result.toolCalls) {
                    val tool = registry[call.name]
                    val args = runCatching { Json.parseToJsonElement(call.arguments).jsonObject }.getOrNull()
                    when {
                        tool == null -> history += Msg.tool(call.id, "错误：没有名为 ${call.name} 的工具")
                        args == null -> history += Msg.tool(call.id, "错误：参数不是合法的 JSON")
                        !tool.isWrite -> history += Msg.tool(call.id, safeExecute { tool.execute(args).result })
                        else -> writes += call to args
                    }
                }
                if (writes.isNotEmpty()) handleWrites(writes, config.autoExecuteWrites)
            }
            add(ChatItem.Error(id(), "步骤过多，已停止。可以换个说法再试。"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            add(ChatItem.Error(id(), e.message ?: e.toString()))
        } finally {
            _busy.value = false
        }
    }

    private suspend fun handleWrites(writes: List<Pair<ToolCall, JsonObject>>, autoExecute: Boolean) {
        // 先生成预览；预览失败（比如找不到记录）的直接把错误返回给 AI
        val valid = mutableListOf<Triple<ToolCall, JsonObject, String>>()
        for ((call, args) in writes) {
            val tool = registry[call.name]!!
            try {
                valid += Triple(call, args, tool.preview(args))
            } catch (e: Exception) {
                history += Msg.tool(call.id, "错误：${e.message}")
            }
        }
        if (valid.isEmpty()) return

        val approved = autoExecute || awaitDecision(valid.map { it.third })
        for ((call, args, _) in valid) {
            if (!approved) {
                history += Msg.tool(call.id, "用户取消了这个操作，没有执行")
                continue
            }
            val tool = registry[call.name]!!
            try {
                val (outcome, opId) = operations.runWrite(tool, args)
                add(ChatItem.Action(id(), opId.takeIf { outcome.undoData != null }, outcome.summary ?: tool.name))
                history += Msg.tool(call.id, outcome.result)
            } catch (e: Exception) {
                history += Msg.tool(call.id, "错误：${e.message}")
            }
        }
    }

    private suspend fun awaitDecision(previews: List<String>): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingDecision = deferred
        val card = ChatItem.Pending(id(), previews)
        add(card)
        return try {
            deferred.await()
        } finally {
            pendingDecision = null
            _items.update { list -> list.filterNot { it.id == card.id } }
        }
    }

    private suspend fun safeExecute(block: suspend () -> String): String = try {
        block()
    } catch (e: Exception) {
        "错误：${e.message}"
    }

    /** 保留最近的对话，并保证从用户消息开始（不能把工具结果和它的调用拆开） */
    private fun trimmedHistory(): List<JsonObject> {
        if (history.size <= MAX_HISTORY) return history.toList()
        var start = history.size - MAX_HISTORY
        while (start < history.size && history[start]["role"]?.toString()?.trim('"') != "user") start++
        return history.subList(start, history.size).toList()
    }

    private suspend fun systemPrompt(): String {
        val latestWeight = db.bodyRecordDao().latestWeight()
        val p = profile.current()
        val targets = profile.currentTargets()
        val today = java.time.LocalDate.now()
        val food = db.foodDao().range(
            io.github.sekirankumo.fitlog.data.diet.DietRepository.dayStart(today),
            io.github.sekirankumo.fitlog.data.diet.DietRepository.dayStart(today.plusDays(1)),
        )
        val todayPlan = db.planDao().get(today.toEpochDay())
        return """
            你是「薄肌来」App 内置的 AI 助手兼私人教练和营养师，帮助用户记录饮食、安排训练、跟踪身体数据。用简洁的中文回答。
            当前时间：${TimeUtil.nowForPrompt()}

            你可以调用工具读取和修改 App 里的所有数据，规则：
            - 需要数据时先用工具查询，不要编造数据。
            - 用户说的相对时间（如"今天早上""昨晚"）要换算成 yyyy-MM-dd HH:mm 再传给工具；"早上"没说具体时间就用 08:00。
            - 用户说吃了什么时，你自己估算份量和营养后调用 add_food_entries，每种食物一条；中式菜品要把烹调用油算进去。收藏的常吃食物优先用 list_favorite_foods 里的数值。
            - 训练相关：安排或替换动作前先用 search_exercises 查动作库，exercise_id 必须来自动作库；打卡前先用 get_training_day 看编号。
            - 用户说某个动作做不了时，找 2–3 个锻炼相同肌群、符合用户器材的替代动作让用户选，再用 replace_exercise。
            - 用户提到伤病、身体限制、饮食习惯、训练偏好等长期信息时，用 remember_user_info 记下来。
            - 修改类操作会先给用户确认（用户也可能设置了直接执行），执行结果会通过工具返回告诉你；用户取消就不要再重复尝试。
            - 用户想撤销之前的修改（包括漏打卡后的自动调整）时，先用 list_recent_operations 找到对应操作，再调用 undo_operation。

            用户信息：${p.describe(latestWeight?.weightKg)}
            每日营养目标：${targets.kcal}kcal 蛋白${targets.proteinG}g 脂肪${targets.fatG}g 碳水${targets.carbsG}g
            今天已摄入：${food.sumOf { it.kcal }.toInt()}kcal（${food.size} 条记录）
            今天的训练：${todayPlan?.let { "${it.title}（${io.github.sekirankumo.fitlog.data.db.DayStatus.label(it.status)}）" } ?: "休息日或没有计划"}
            最新体重记录：${latestWeight?.describe() ?: "暂无"}
        """.trimIndent()
    }

    private companion object {
        const val MAX_STEPS = 8
        const val MAX_HISTORY = 40
    }
}
