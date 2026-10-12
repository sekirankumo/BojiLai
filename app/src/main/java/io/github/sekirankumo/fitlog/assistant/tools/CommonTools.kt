package io.github.sekirankumo.fitlog.assistant.tools

import io.github.sekirankumo.fitlog.assistant.AiTool
import io.github.sekirankumo.fitlog.assistant.OperationService
import io.github.sekirankumo.fitlog.assistant.ToolException
import io.github.sekirankumo.fitlog.assistant.ToolOutcome
import io.github.sekirankumo.fitlog.assistant.integerProp
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.objectSchema
import io.github.sekirankumo.fitlog.assistant.requireLong
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.assistant.stringProp
import io.github.sekirankumo.fitlog.data.db.OpLogDao
import io.github.sekirankumo.fitlog.util.TimeUtil
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject

/** 可以跳转的页面：路由 to 中文名 */
val APP_PAGES = mapOf(
    "today" to "今日",
    "diet" to "饮食",
    "training" to "训练",
    "body" to "身体数据",
    "settings" to "设置",
    "ai_settings" to "AI 服务设置",
)

class OpenPageTool(private val navEvents: MutableSharedFlow<String>) : AiTool {
    override val name = "open_page"
    override val description = "打开 App 的某个页面"
    override val parameters = objectSchema(
        listOf("page"),
        "page" to stringProp(
            APP_PAGES.entries.joinToString("，") { "${it.key}=${it.value}" },
            APP_PAGES.keys.toList(),
        ),
    )
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val page = args.str("page")?.takeIf { it in APP_PAGES } ?: throw ToolException("未知页面")
        navEvents.emit(page)
        return ToolOutcome("已打开${APP_PAGES[page]}页面")
    }
}

class ListOperationsTool(private val dao: OpLogDao) : AiTool {
    override val name = "list_recent_operations"
    override val description = "列出最近对数据做过的修改操作（包括 AI 和自动任务做的），可用于撤销"
    override val parameters = objectSchema(emptyList(), "limit" to integerProp("条数，默认 10"))
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val list = dao.recent((args.long("limit") ?: 10).toInt().coerceIn(1, 50))
        if (list.isEmpty()) return ToolOutcome("没有操作记录")
        return ToolOutcome(list.joinToString("\n") { op ->
            val flag = when {
                op.undone -> "（已撤销）"
                op.undoJson == null -> "（不可撤销）"
                else -> ""
            }
            "操作#${op.id} ${TimeUtil.format(op.createdAt)} ${op.summary}$flag"
        })
    }
}

class UndoOperationTool(
    private val dao: OpLogDao,
    private val operations: OperationService,
) : AiTool {
    override val name = "undo_operation"
    override val description = "撤销之前的某次修改操作，id 来自 list_recent_operations"
    override val parameters = objectSchema(listOf("operation_id"), "operation_id" to integerProp("操作 id"))
    override val isWrite = true

    override suspend fun preview(args: JsonObject): String {
        val op = dao.get(args.requireLong("operation_id")) ?: throw ToolException("找不到该操作")
        return "撤销操作：${op.summary}"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val msg = operations.undo(args.requireLong("operation_id"))
        return ToolOutcome(result = msg, summary = msg)
    }
}
