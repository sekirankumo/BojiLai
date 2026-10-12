package io.github.sekirankumo.fitlog.assistant

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add

/**
 * App 的一项功能，暴露给 AI 助手调用。
 * 界面按钮和 AI 调用的是同一套底层逻辑；新功能完成后在 ToolRegistry 里注册即可。
 */
interface AiTool {
    val name: String
    val description: String
    /** JSON Schema，描述参数 */
    val parameters: JsonObject
    /** 写操作：执行前需要用户确认，并写入操作日志以便撤销 */
    val isWrite: Boolean

    /** 写操作在确认卡片上显示的说明 */
    suspend fun preview(args: JsonObject): String = name

    suspend fun execute(args: JsonObject): ToolOutcome

    /** 根据 execute 返回的 undoData 撤销 */
    suspend fun undo(undoData: JsonObject) {}
}

data class ToolOutcome(
    /** 返回给 AI 的结果 */
    val result: String,
    /** 写操作完成后显示给用户的一句话 */
    val summary: String? = null,
    /** 撤销所需的数据；为 null 表示不可撤销 */
    val undoData: JsonObject? = null,
)

/** 工具执行中可预期的错误，消息会原样返回给 AI */
class ToolException(message: String) : Exception(message)

// ---------- 参数 Schema 构造 ----------

fun objectSchema(required: List<String> = emptyList(), vararg props: Pair<String, JsonObject>): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
        putJsonArray("required") { required.forEach { add(it) } }
    }

fun stringProp(description: String, enum: List<String>? = null) = buildJsonObject {
    put("type", "string")
    put("description", description)
    if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
}

fun numberProp(description: String) = buildJsonObject {
    put("type", "number")
    put("description", description)
}

fun integerProp(description: String) = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

fun booleanProp(description: String) = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

/** 对象数组参数 */
fun arrayProp(description: String, item: JsonObject) = buildJsonObject {
    put("type", "array")
    put("description", description)
    put("items", item)
}

fun stringArrayProp(description: String) = buildJsonObject {
    put("type", "array")
    put("description", description)
    putJsonObject("items") { put("type", "string") }
}

// ---------- 参数读取 ----------

private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it.contentOrNull != null }

fun JsonObject.str(key: String): String? = prim(key)?.contentOrNull?.takeIf { it.isNotBlank() }
fun JsonObject.num(key: String): Double? = prim(key)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
fun JsonObject.long(key: String): Long? = prim(key)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }
fun JsonObject.has(key: String): Boolean = this[key] != null && prim(key) != null

fun JsonObject.requireLong(key: String): Long = long(key) ?: throw ToolException("缺少参数 $key")

fun JsonObject.requireStr(key: String): String = str(key) ?: throw ToolException("缺少参数 $key")

fun JsonObject.bool(key: String): Boolean? = prim(key)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }

fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { it as? JsonObject }

fun JsonObject.strings(key: String): List<String> =
    (this[key] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
