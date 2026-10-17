package io.github.sekirankumo.fitlog

import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Gemini 思考型模型的工具调用：每次调用带一个思考签名（extra_content.google.thought_signature），
 * 下一轮必须原样带回去，否则报 400。而且它的流式返回里工具调用没有 index。
 */
class ToolCallStreamTest {
    private val client = OpenAiClient(OkHttpClient())

    private fun stream(vararg chunks: String) = Buffer().apply {
        chunks.forEach { writeUtf8("data: $it\n\n") }
        writeUtf8("data: [DONE]\n\n")
    }

    @Test fun geminiThoughtSignatureIsKeptAndSentBack() {
        val result = client.readStream(
            stream(
                """{"choices":[{"delta":{"role":"assistant","tool_calls":[{"extra_content":{"google":{"thought_signature":"SIG_A"}},"function":{"arguments":"{\"date\":\"2026-10-16\"}","name":"query_food_entries"},"id":"call_a","type":"function"}]}}]}""",
                """{"choices":[{"delta":{"role":"assistant","tool_calls":[{"function":{"arguments":"{}","name":"get_user_profile"},"id":"call_b","type":"function"}]}}]}""",
            ),
            null,
        )
        // 两次调用没有 index，也不能拼成一个
        assertEquals(listOf("query_food_entries", "get_user_profile"), result.toolCalls.map { it.name })
        assertEquals("{\"date\":\"2026-10-16\"}", result.toolCalls[0].arguments)

        val calls = result.toMessage()["tool_calls"] as JsonArray
        val first = calls[0] as JsonObject
        val signature = ((first["extra_content"] as JsonObject)["google"] as JsonObject)["thought_signature"] as JsonPrimitive
        assertEquals("SIG_A", signature.content)
        // 没有签名的调用原样不带
        assertNull((calls[1] as JsonObject)["extra_content"])
    }

    @Test fun normalStreamingWithIndexStillJoinsArguments() {
        val result = client.readStream(
            stream(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"add_food_entries","arguments":"{\"items\":"}}]}}]}""",
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"[]}"}}]}}]}""",
            ),
            null,
        )
        assertEquals(1, result.toolCalls.size)
        assertEquals("{\"items\":[]}", result.toolCalls[0].arguments)
        assertNull((result.toMessage()["tool_calls"] as JsonArray)[0].let { (it as JsonObject)["extra_content"] })
    }
}
