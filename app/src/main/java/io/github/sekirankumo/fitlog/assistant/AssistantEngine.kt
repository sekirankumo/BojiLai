package io.github.sekirankumo.fitlog.assistant

import io.github.sekirankumo.fitlog.assistant.tools.describe
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
) {
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
                // 让 AI 知道这次撤销，避免后续对话基于过时的数据
                history += Msg.user("（我刚在界面上撤销了：${item.summary}）")
                history += Msg.assistant("好的，已知晓该操作被撤销。")
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
        val latest = db.bodyRecordDao().latest()
        return """
            你是 FitLog 应用内置的 AI 助手，帮助用户记录饮食、训练和身体数据。用简洁的中文回答。
            当前时间：${TimeUtil.nowForPrompt()}

            你可以调用工具读取和修改 App 里的数据，规则：
            - 需要数据时先用工具查询，不要编造数据。
            - 用户说的相对时间（如"今天早上""昨晚"）要换算成 yyyy-MM-dd HH:mm 再传给工具；"早上"没说具体时间就用 08:00。
            - 修改类操作会先给用户确认，执行结果会通过工具返回告诉你；用户取消就不要再重复尝试。
            - 用户想撤销之前的修改时，先用 list_recent_operations 找到对应操作，再调用 undo_operation。
            - 目前已上线：身体数据（体重、体脂）、页面跳转、操作记录与撤销。饮食记录和训练计划还在开发中，用户问到时说明尚未上线。

            用户最新的身体数据：${latest?.describe() ?: "暂无"}
        """.trimIndent()
    }

    private companion object {
        const val MAX_STEPS = 8
        const val MAX_HISTORY = 40
    }
}
