package io.github.sekirankumo.fitlog.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun chat(
        provider: AiProvider,
        model: String,
        messages: List<JsonObject>,
        tools: List<JsonObject> = emptyList(),
        jsonMode: Boolean = false,
        temperature: Double? = null,
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
        }
        val req = Request.Builder()
            .url(url(provider, "chat/completions"))
            .header("Authorization", "Bearer ${provider.apiKey}")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw AiException("HTTP ${resp.code}：${errorMessage(text)}")
            parseChat(text)
        }
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
        val content = (message["content"] as? JsonPrimitive)?.contentOrNull
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
fun extractJsonObject(text: String): JsonObject {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) throw AiException("AI 没有返回 JSON：${text.take(200)}")
    return Json.parseToJsonElement(text.substring(start, end + 1)).jsonObject
}
