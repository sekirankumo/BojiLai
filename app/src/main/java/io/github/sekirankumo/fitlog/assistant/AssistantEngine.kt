package io.github.sekirankumo.fitlog.assistant

import io.github.sekirankumo.fitlog.assistant.tools.describe
import io.github.sekirankumo.fitlog.data.ProfileRepository
import io.github.sekirankumo.fitlog.data.ai.AiSettings
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.ModelRef
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

    /** images：图片的本地地址（Uri），只用于界面显示 */
    data class User(override val id: Long, val text: String, val images: List<String> = emptyList()) : ChatItem
    data class Assistant(override val id: Long, val text: String) : ChatItem

    /** 已执行的修改，可撤销 */
    data class Action(override val id: Long, val opId: Long?, val summary: String, val undone: Boolean = false) : ChatItem

    /** 等待用户确认的修改 */
    data class Pending(override val id: Long, val previews: List<String>) : ChatItem
    data class Error(override val id: Long, val message: String) : ChatItem
}

/** 用户发给助手的一张图片 */
data class ChatImage(
    /** 本地地址（Uri），用于显示 */
    val uri: String,
    /** data:image/jpeg;base64,...，发给模型 */
    val dataUrl: String,
)

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

    /** 正在做什么（如「正在看图…」），为空时显示「正在思考…」 */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private fun add(item: ChatItem) = _items.update { it + item }
    private fun id() = nextId.getAndIncrement()

    fun send(text: String, images: List<ChatImage> = emptyList()) {
        val content = text.trim()
        if ((content.isEmpty() && images.isEmpty()) || _busy.value) return
        add(ChatItem.User(id(), content, images.map { it.uri }))
        _busy.value = true
        job = scope.launch { runLoop(content, images) }
    }

    /** 截图测试用：直接显示一段对话 */
    internal fun showPreview(list: List<ChatItem>) {
        _items.value = list
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

    private suspend fun runLoop(text: String, images: List<ChatImage>) {
        _busy.value = true
        // 直接发给助手模型的带图消息，这一轮结束后换成文字，后面的对话不再重复上传图片
        var imageMessage: JsonObject? = null
        try {
            val config = settings.current()
            val ref = config.assistantModel?.takeIf { it.model.isNotBlank() }
                ?: throw IllegalStateException("还没有配置助手模型，请到 设置 → AI 服务 里添加")
            val provider = config.provider(ref.providerId)
                ?: throw IllegalStateException("助手模型对应的服务已被删除，请重新选择")

            if (images.isEmpty()) {
                history += Msg.user(text)
            } else {
                val vision = config.visionModel?.takeIf { it.model.isNotBlank() }
                if (vision == null || (vision.providerId == ref.providerId && vision.model == ref.model)) {
                    // 没有单独的看图模型，或者助手模型本身就是看图模型：直接发图
                    val msg = Msg.userWithImages(text.ifEmpty { "（发了 ${images.size} 张图片，没有附加文字）" }, images.map { it.dataUrl })
                    imageMessage = msg
                    history += msg
                } else {
                    // 助手模型不一定能看图：先让看图模型把图片描述成文字
                    _status.value = "正在看图…"
                    val description = describeImages(config, vision, text, images)
                    history += Msg.user(
                        text.ifEmpty { "（发了 ${images.size} 张图片，没有附加文字）" } +
                            "\n\n【图片内容（由图片识别模型描述）】\n" + description,
                    )
                    _status.value = null
                }
            }

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
            val hint = if (imageMessage != null) "\n如果是助手模型不支持看图，可以在 设置 → AI 服务 里另外选一个「图片识别」模型。" else ""
            add(ChatItem.Error(id(), (e.message ?: e.toString()) + hint))
        } finally {
            imageMessage?.let { msg ->
                val i = history.indexOf(msg)
                if (i >= 0) {
                    history[i] = Msg.user(text.ifEmpty { "（发了图片）" } + "\n（附了 ${images.size} 张图片，图片内容见你接下来的回答）")
                }
            }
            _status.value = null
            _busy.value = false
        }
    }

    /** 用看图模型把图片内容写成文字，交给（可能不能看图的）助手模型 */
    private suspend fun describeImages(config: AiSettings, ref: ModelRef, text: String, images: List<ChatImage>): String {
        val provider = config.provider(ref.providerId)
            ?: throw IllegalStateException("图片识别模型对应的服务已被删除，请重新选择")
        val prompt = """
            用户在健身饮食 App 里给 AI 助手发了 ${images.size} 张图片，${if (text.isNotEmpty()) "并说：「$text」" else "没有附加文字"}。
            助手看不到图片，请把图片内容详细、准确地写成文字交给它：
            - 食物：逐一列出每种食物，估计份量（克数）和做法（油炸、清蒸等）；
            - 营养成分表、食品包装：抄下品名和所有数值、单位；
            - 体重秤、体脂秤、运动手环或 App 截图：读出所有数字、单位和日期时间；
            - 训练动作、器械、身材照片：描述动作姿势、器械名称和看得到的身体状态；
            - 其他内容如实描述，看不清的地方直接说明。
            有多张图片时按「图1」「图2」分开写。只描述，不要给建议。
        """.trimIndent()
        val result = client.chat(
            provider,
            ref.model,
            listOf(Msg.userWithImages(prompt, images.map { it.dataUrl })),
            temperature = 0.2,
            stream = true,
        )
        return result.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("图片识别模型没有返回内容")
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
            - 膳食质量用 get_diet_quality 查（GDQS 膳食分，满分 49，≥23 良好）。分数和食材分类由 App 自动计算，记录饮食时不用自己填；评价饮食结构、做周报时引用它的扣分项和提分建议，标着「高盐」的食物要提醒用户注意。
            - 常吃食物只在记录饮食时添加：用户要收藏时，在 add_food_entries 里给那条食物设 add_to_favorites。修改、删除常吃先用 list_favorite_foods 查 id，再用 update_favorite_food / delete_favorite_food。
            - 用户提到伤病、身体限制、饮食习惯、训练偏好等长期信息时，用 remember_user_info 记下来。
            - 修改类操作会先给用户确认（用户也可能设置了直接执行），执行结果会通过工具返回告诉你；用户取消就不要再重复尝试。
            - 用户想撤销之前的修改（包括漏打卡后的自动调整）时，先用 list_recent_operations 找到对应操作，再调用 undo_operation。
            - 用户发的图片会直接给你，或者先由图片识别模型写成文字（标着【图片内容】）。用户发食物照片想记录时，根据图片估算后调用 add_food_entries；发体重秤截图想记录时调用 add_body_record。

            回答格式：
            - 界面是手机屏幕，回答要简短，段落短一些。
            - 可以用 Markdown：**加粗**、列表、标题；对比多项数据时可以用表格，但不要超过 4 列。
            - 不要用 LaTeX 公式和 HTML 标签，算式直接写，例如 10×70 + 6.25×175 − 5×30 + 5 = 1649。

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
