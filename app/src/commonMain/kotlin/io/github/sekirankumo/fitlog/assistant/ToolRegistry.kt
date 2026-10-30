package io.github.sekirankumo.fitlog.assistant

import io.github.sekirankumo.fitlog.util.*
import io.github.sekirankumo.fitlog.data.db.OpLog
import io.github.sekirankumo.fitlog.data.db.OpLogDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class ToolRegistry(tools: List<AiTool>) {
    private val byName = tools.associateBy { it.name }

    operator fun get(name: String): AiTool? = byName[name]

    /** OpenAI 格式的 tools 定义 */
    val schemas: List<JsonObject> = tools.map { tool ->
        buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.parameters)
            }
        }
    }
}

/** 执行写操作并记录到操作日志；按日志撤销 */
class OperationService(private val dao: OpLogDao) {
    lateinit var registry: ToolRegistry

    /** 执行写操作，返回结果和日志 id */
    suspend fun runWrite(tool: AiTool, args: JsonObject): Pair<ToolOutcome, Long> {
        val outcome = tool.execute(args)
        val id = dao.insert(
            OpLog(
                createdAt = nowMillis(),
                toolName = tool.name,
                summary = outcome.summary ?: tool.name,
                undoJson = outcome.undoData?.toString(),
            )
        )
        return outcome to id
    }

    suspend fun undo(opId: Long): String {
        val log = dao.get(opId) ?: throw ToolException("找不到操作 #$opId")
        if (log.undone) throw ToolException("操作 #$opId 已经撤销过了")
        val data = log.undoJson ?: throw ToolException("操作 #$opId 不支持撤销")
        val tool = registry[log.toolName] ?: throw ToolException("找不到工具 ${log.toolName}")
        tool.undo(Json.parseToJsonElement(data).jsonObject)
        dao.markUndone(opId)
        return "已撤销：${log.summary}"
    }
}
