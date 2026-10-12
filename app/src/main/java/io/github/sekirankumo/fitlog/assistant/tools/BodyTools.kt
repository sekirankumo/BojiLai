package io.github.sekirankumo.fitlog.assistant.tools

import io.github.sekirankumo.fitlog.assistant.AiTool
import io.github.sekirankumo.fitlog.assistant.ToolException
import io.github.sekirankumo.fitlog.assistant.ToolOutcome
import io.github.sekirankumo.fitlog.assistant.has
import io.github.sekirankumo.fitlog.assistant.integerProp
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.num
import io.github.sekirankumo.fitlog.assistant.numberProp
import io.github.sekirankumo.fitlog.assistant.objectSchema
import io.github.sekirankumo.fitlog.assistant.requireLong
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.assistant.stringProp
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 身体记录的一行文字描述 */
fun BodyRecord.describe(): String = buildString {
    append("#$id ${TimeUtil.format(measuredAt)}")
    weightKg?.let { append(" 体重${it.fmt()}kg") }
    bodyFatPct?.let { append(" 体脂${it.fmt()}%") }
    note?.let { append(" 备注：$it") }
}

private fun BodyRecord.toUndoJson() = buildJsonObject {
    put("id", id)
    put("measuredAt", measuredAt)
    weightKg?.let { put("weightKg", it) }
    bodyFatPct?.let { put("bodyFatPct", it) }
    put("source", source)
    note?.let { put("note", it) }
}

private fun JsonObject.toBodyRecord() = BodyRecord(
    id = requireLong("id"),
    measuredAt = requireLong("measuredAt"),
    weightKg = num("weightKg"),
    bodyFatPct = num("bodyFatPct"),
    source = str("source") ?: "manual",
    note = str("note"),
)

private fun parseTime(args: JsonObject): Long? =
    args.str("time")?.let { TimeUtil.parse(it) ?: throw ToolException("时间格式应为 yyyy-MM-dd HH:mm") }

private fun validate(weight: Double?, fat: Double?) {
    if (weight != null && weight !in 20.0..300.0) throw ToolException("体重 $weight kg 不合理")
    if (fat != null && fat !in 2.0..70.0) throw ToolException("体脂率 $fat% 不合理")
}

class QueryBodyRecordsTool(private val dao: BodyRecordDao) : AiTool {
    override val name = "query_body_records"
    override val description = "查询体重、体脂记录，按时间倒序返回"
    override val parameters = objectSchema(
        emptyList(),
        "days" to integerProp("查询最近多少天，默认 30"),
    )
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val days = args.long("days") ?: 30
        val list = dao.since(TimeUtil.daysAgo(days))
        val text = if (list.isEmpty()) "最近 $days 天没有记录" else list.joinToString("\n") { it.describe() }
        return ToolOutcome(text)
    }
}

class AddBodyRecordTool(private val dao: BodyRecordDao) : AiTool {
    override val name = "add_body_record"
    override val description = "新增一条身体数据记录（体重和体脂至少填一个）"
    override val parameters = objectSchema(
        emptyList(),
        "weight_kg" to numberProp("体重，公斤"),
        "body_fat_pct" to numberProp("体脂率，百分比数值，如 18.5"),
        "time" to stringProp("测量时间 yyyy-MM-dd HH:mm，不填则为现在"),
        "note" to stringProp("备注"),
    )
    override val isWrite = true

    private fun build(args: JsonObject): BodyRecord {
        val weight = args.num("weight_kg")
        val fat = args.num("body_fat_pct")
        if (weight == null && fat == null) throw ToolException("体重和体脂至少需要一个")
        validate(weight, fat)
        return BodyRecord(
            measuredAt = parseTime(args) ?: System.currentTimeMillis(),
            weightKg = weight,
            bodyFatPct = fat,
            source = "ai",
            note = args.str("note"),
        )
    }

    override suspend fun preview(args: JsonObject) = "添加身体记录：" + build(args).describe().substringAfter(' ')

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val record = build(args)
        val saved = record.copy(id = dao.insert(record))
        return ToolOutcome(
            result = "已添加 ${saved.describe()}",
            summary = "添加身体记录：" + saved.describe().substringAfter(' '),
            undoData = buildJsonObject { put("id", saved.id) },
        )
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.deleteById(undoData.requireLong("id"))
    }
}

class UpdateBodyRecordTool(private val dao: BodyRecordDao) : AiTool {
    override val name = "update_body_record"
    override val description = "修改一条身体数据记录，只传需要修改的字段"
    override val parameters = objectSchema(
        listOf("id"),
        "id" to integerProp("记录 id"),
        "weight_kg" to numberProp("新的体重，公斤"),
        "body_fat_pct" to numberProp("新的体脂率"),
        "time" to stringProp("新的测量时间 yyyy-MM-dd HH:mm"),
        "note" to stringProp("新的备注"),
    )
    override val isWrite = true

    private suspend fun load(args: JsonObject): Pair<BodyRecord, BodyRecord> {
        val old = dao.get(args.requireLong("id")) ?: throw ToolException("找不到记录 #${args.long("id")}")
        val new = old.copy(
            weightKg = if (args.has("weight_kg")) args.num("weight_kg") else old.weightKg,
            bodyFatPct = if (args.has("body_fat_pct")) args.num("body_fat_pct") else old.bodyFatPct,
            measuredAt = parseTime(args) ?: old.measuredAt,
            note = if (args.has("note")) args.str("note") else old.note,
        )
        validate(new.weightKg, new.bodyFatPct)
        return old to new
    }

    override suspend fun preview(args: JsonObject): String {
        val (old, new) = load(args)
        return "修改身体记录：\n原：${old.describe()}\n新：${new.describe()}"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val (old, new) = load(args)
        dao.update(new)
        return ToolOutcome(
            result = "已修改为 ${new.describe()}",
            summary = "修改身体记录 ${new.describe()}",
            undoData = old.toUndoJson(),
        )
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.insert(undoData.toBodyRecord())
    }
}

class DeleteBodyRecordTool(private val dao: BodyRecordDao) : AiTool {
    override val name = "delete_body_record"
    override val description = "删除一条身体数据记录"
    override val parameters = objectSchema(listOf("id"), "id" to integerProp("记录 id"))
    override val isWrite = true

    private suspend fun load(args: JsonObject) =
        dao.get(args.requireLong("id")) ?: throw ToolException("找不到记录 #${args.long("id")}")

    override suspend fun preview(args: JsonObject) = "删除身体记录：${load(args).describe()}"

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val old = load(args)
        dao.deleteById(old.id)
        return ToolOutcome(
            result = "已删除 ${old.describe()}",
            summary = "删除身体记录 ${old.describe()}",
            undoData = old.toUndoJson(),
        )
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.insert(undoData.toBodyRecord())
    }
}
