package io.github.sekirankumo.fitlog.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AiException(message: String) : Exception(message)

data class ToolCall(
    val id: String,
    val name: String,
    /** JSON 字符串 */
    val arguments: String,
    /**
     * 服务商附加在这次调用上的额外信息，下一轮要原样带回去。
     * Gemini 的思考型模型把「思考签名」（extra_content.google.thought_signature）放在这里，
     * 带回去时少了它会直接报 400。别的服务一般没有这个字段。
     */
    val extra: JsonObject? = null,
)

data class ChatResult(
    val content: String?,
    val toolCalls: List<ToolCall>,
) {
    /** 规范化后的 assistant 消息，用于放回对话历史 */
    fun toMessage(): JsonObject = buildJsonObject {
        put("role", "assistant")
        if (content != null) put("content", content)
        if (toolCalls.isNotEmpty()) {
            putJsonArray("tool_calls") {
                toolCalls.forEach { call ->
                    add(buildJsonObject {
                        put("id", call.id)
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", call.name)
                            put("arguments", call.arguments)
                        }
                        call.extra?.let { put("extra_content", it) }
                    })
                }
            }
        }
    }
}

/** 构造对话消息的小工具 */
object Msg {
    fun system(text: String) = buildJsonObject { put("role", "system"); put("content", text) }
    fun user(text: String) = buildJsonObject { put("role", "user"); put("content", text) }
    fun assistant(text: String) = buildJsonObject { put("role", "assistant"); put("content", text) }
    fun tool(callId: String, result: String) = buildJsonObject {
        put("role", "tool")
        put("tool_call_id", callId)
        put("content", result)
    }

    /** 带图片的用户消息，images 为 data:image/...;base64,... 地址 */
    fun userWithImages(text: String, images: List<String>) = buildJsonObject {
        put("role", "user")
        putJsonArray("content") {
            images.forEach { url ->
                add(buildJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") { put("url", url) }
                })
            }
            add(buildJsonObject { put("type", "text"); put("text", text) })
        }
    }
}

/** OpenAI 兼容接口客户端：/chat/completions 和 /models */
class OpenAiClient(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private fun url(provider: AiProvider, path: String) = provider.baseUrl.trimEnd('/') + "/" + path

    /**
     * @param stream 用流式输出：AI 边生成边传回，长时间生成时连接不会因为「没有数据」被网关或运营商切断。
     *               生成训练计划、识别食物这类耗时请求都应该打开。
     * @param onProgress 流式输出时回调已收到的字数（含思考过程）
     */
    suspend fun chat(
        provider: AiProvider,
        model: String,
        messages: List<JsonObject>,
        tools: List<JsonObject> = emptyList(),
        jsonMode: Boolean = false,
        temperature: Double? = null,
        stream: Boolean = false,
        onProgress: ((Int) -> Unit)? = null,
    ): ChatResult {
        // 依次尝试：原始参数 → 去掉 temperature → 去掉 response_format → 关掉流式。只有服务端明确拒绝参数（400/422）时才降级。
        // Claude 新模型、OpenAI 新模型只接受默认温度，传别的值会直接 400
        data class Attempt(val json: Boolean, val stream: Boolean, val temperature: Double?)
        val attempts = buildList {
            add(Attempt(jsonMode, stream, temperature))
            if (temperature != null) add(Attempt(jsonMode, stream, null))
            if (jsonMode) add(Attempt(false, stream, null))
            if (stream) add(Attempt(false, false, null))
        }.distinct()
        var lastError: AiException? = null
        for ((json, useStream, temp) in attempts) {
            try {
                return withNetworkRetry { chatOnce(provider, model, messages, tools, json, temp, useStream, onProgress) }
            } catch (e: AiException) {
                lastError = e
                val rejected = e.message?.contains("HTTP 400") == true || e.message?.contains("HTTP 422") == true
                if (!rejected) throw e
            }
        }
        throw lastError ?: AiException("请求失败")
    }

    /** 网络被中断（切到后台、网络切换、网关断开空闲连接等）时自动重试一次，并把错误翻译成中文 */
    private suspend fun <T> withNetworkRetry(block: suspend () -> T): T {
        repeat(2) { attempt ->
            try {
                return block()
            } catch (e: IOException) {
                if (attempt == 1 || e is UnknownHostException) throw AiException(networkMessage(e))
                delay(1500)
            }
        }
        error("unreachable")
    }

    private fun networkMessage(e: IOException): String = when (e) {
        is UnknownHostException -> "连不上 AI 服务，请检查网络或接口地址"
        is SocketTimeoutException -> "AI 响应超时，可能是模型太慢或网络不好，请稍后再试"
        is SSLException -> "和 AI 服务的加密连接失败：${e.message}"
        else -> when {
            e.message?.contains("abort", true) == true || e.message?.contains("reset", true) == true ->
                "网络连接被中断（切到后台、网络切换或代理断开都可能导致），请重试"
            else -> "网络错误：${e.message}"
        }
    }

    private suspend fun chatOnce(
        provider: AiProvider,
        model: String,
        messages: List<JsonObject>,
        tools: List<JsonObject>,
        jsonMode: Boolean,
        temperature: Double?,
        stream: Boolean,
        onProgress: ((Int) -> Unit)?,
    ): ChatResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("messages", JsonArray(messages))
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools))
                put("tool_choice", "auto")
            }
            if (jsonMode) putJsonObject("response_format") { put("type", "json_object") }
            temperature?.let { put("temperature", it) }
            if (stream) put("stream", true)
        }
        val req = Request.Builder()
            .url(url(provider, "chat/completions"))
            .header("Authorization", "Bearer ${provider.apiKey}")
            .apply { if (stream) header("Accept", "text/event-stream") }
            .post(body.toString().toRequestBody(jsonType))
            .build()
        val call = http.newCall(req)
        // 协程被取消时（比如离开页面）同时取消网络请求
        val job = coroutineContext[Job]
        val handle = job?.invokeOnCompletion { if (it != null) call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw AiException("HTTP ${resp.code}：${errorMessage(resp.body?.string().orEmpty())}")
                }
                val isSse = resp.header("Content-Type")?.contains("event-stream") == true
                if (stream && isSse) readStream(resp.body!!.source(), onProgress) else parseChat(resp.body?.string().orEmpty())
            }
        } finally {
            handle?.dispose()
        }
    }

    /** 读取 SSE 流：拼接 delta 里的文字和工具调用 */
    internal fun readStream(source: okio.BufferedSource, onProgress: ((Int) -> Unit)?): ChatResult {
        val content = StringBuilder()
        var thinking = 0
        // 工具调用按 index 拼接
        val calls = sortedMapOf<Int, Triple<StringBuilder, StringBuilder, StringBuilder>>()
        val extras = mutableMapOf<Int, JsonObject>()
        var lastReport = 0
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]") break
            if (data.isEmpty()) continue
            val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
            obj["error"]?.let { err ->
                val msg = ((err as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull ?: err.toString()
                throw AiException("AI 服务返回错误：$msg")
            }
            val delta = ((obj["choices"] as? JsonArray)?.firstOrNull() as? JsonObject)?.get("delta") as? JsonObject ?: continue
            (delta["content"] as? JsonPrimitive)?.contentOrNull?.let { content.append(it) }
            ((delta["reasoning_content"] ?: delta["reasoning"]) as? JsonPrimitive)?.contentOrNull?.let { thinking += it.length }
            (delta["tool_calls"] as? JsonArray)?.forEachIndexed { pos, el ->
                val tc = el as? JsonObject ?: return@forEachIndexed
                val fnName = ((tc["function"] as? JsonObject)?.get("name") as? JsonPrimitive)?.contentOrNull
                val index = (tc["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
                    // 没写 index：一个新的函数名说明是新的一次调用，接在后面，别和前一次拼在一起
                    ?: if (fnName != null && calls[pos]?.second?.isNotEmpty() == true) (calls.lastKey() + 1) else pos
                val entry = calls.getOrPut(index) { Triple(StringBuilder(), StringBuilder(), StringBuilder()) }
                (tc["extra_content"] as? JsonObject)?.let { extras[index] = it }
                (tc["id"] as? JsonPrimitive)?.contentOrNull?.let { if (entry.first.isEmpty()) entry.first.append(it) }
                val fn = tc["function"] as? JsonObject
                (fn?.get("name") as? JsonPrimitive)?.contentOrNull?.let { if (entry.second.isEmpty()) entry.second.append(it) }
                when (val a = fn?.get("arguments")) {
                    is JsonPrimitive -> a.contentOrNull?.let { entry.third.append(it) }
                    is JsonObject -> entry.third.append(a.toString())
                    else -> Unit
                }
            }
            val total = content.length + thinking + calls.values.sumOf { it.third.length }
            if (onProgress != null && total - lastReport >= 20) {
                lastReport = total
                onProgress(total)
            }
        }
        val toolCalls = calls.entries.filter { it.value.second.isNotEmpty() }.map { (i, v) ->
            ToolCall(
                id = v.first.toString().ifEmpty { "call_$i" },
                name = v.second.toString(),
                arguments = v.third.toString().ifBlank { "{}" },
                extra = extras[i],
            )
        }
        val text = stripThinking(content.toString())
        if (text.isBlank() && toolCalls.isEmpty()) throw AiException("AI 没有返回内容")
        return ChatResult(text.takeIf { it.isNotBlank() }, toolCalls)
    }

    suspend fun listModels(provider: AiProvider): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(url(provider, "models"))
            .header("Authorization", "Bearer ${provider.apiKey}")
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException("HTTP ${resp.code}：${errorMessage(text)}")
            val data = json.parseToJsonElement(text).jsonObject["data"] as? JsonArray
                ?: throw AiException("该服务不支持获取模型列表，请手动填写模型名")
            data.mapNotNull { (it.jsonObject["id"] as? JsonPrimitive)?.contentOrNull }
                .map { it.removePrefix("models/") }
                .sorted()
        }
    }

    private fun parseChat(text: String): ChatResult {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }
            .getOrElse { throw AiException("返回内容无法解析：${text.take(200)}") }
        val message = (root["choices"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("message") as? JsonObject
            ?: throw AiException("返回格式异常：${text.take(200)}")
        val content = (message["content"] as? JsonPrimitive)?.contentOrNull?.let(::stripThinking)
        val calls = (message["tool_calls"] as? JsonArray).orEmpty().mapIndexed { i, el ->
            val o = el.jsonObject
            val fn = o["function"]!!.jsonObject
            val args = when (val a = fn["arguments"]) {
                is JsonPrimitive -> a.contentOrNull ?: "{}"
                null -> "{}"
                else -> a.toString()
            }
            ToolCall(
                id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: "call_$i",
                name = (fn["name"] as JsonPrimitive).content,
                arguments = args.ifBlank { "{}" },
                extra = o["extra_content"] as? JsonObject,
            )
        }
        return ChatResult(content?.takeIf { it.isNotBlank() }, calls)
    }

    private fun errorMessage(body: String): String {
        val parsed = runCatching {
            val root = json.parseToJsonElement(body)
            val err = (root as? JsonObject)?.get("error")
                ?: (root as? JsonArray)?.firstOrNull()?.jsonObject?.get("error")
            when (err) {
                is JsonObject -> (err["message"] as? JsonPrimitive)?.contentOrNull
                is JsonPrimitive -> err.contentOrNull
                else -> null
            }
        }.getOrNull()
        return parsed ?: body.take(300).ifBlank { "无返回内容" }
    }
}

/** 从 AI 回复里取出 JSON（兼容 ```json 代码块包裹的情况） */
fun extractJsonObject(raw: String): JsonObject {
    val text = stripThinking(raw)
    val objStart = text.indexOf('{')
    val arrStart = text.indexOf('[')
    // 有的模型直接返回数组，包装成 {"items": [...]}
    if (arrStart >= 0 && (objStart < 0 || arrStart < objStart)) {
        val end = text.lastIndexOf(']')
        if (end > arrStart) {
            runCatching { Json.parseToJsonElement(text.substring(arrStart, end + 1)) as JsonArray }.getOrNull()?.let {
                return buildJsonObject { put("items", it) }
            }
        }
    }
    val end = text.lastIndexOf('}')
    if (objStart < 0 || end <= objStart) throw AiException("AI 没有返回 JSON：${text.take(200)}")
    return runCatching { Json.parseToJsonElement(text.substring(objStart, end + 1)).jsonObject }
        .getOrElse { throw AiException("AI 返回的 JSON 格式有误：${text.take(200)}") }
}

/** 去掉推理模型输出的 <think>…</think> 思考过程 */
fun stripThinking(text: String): String =
    text.replace(Regex("(?s)<think>.*?</think>"), "").trim()
