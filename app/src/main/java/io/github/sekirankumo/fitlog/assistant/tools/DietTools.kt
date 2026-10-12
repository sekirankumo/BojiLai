package io.github.sekirankumo.fitlog.assistant.tools

import io.github.sekirankumo.fitlog.assistant.AiTool
import io.github.sekirankumo.fitlog.assistant.ToolException
import io.github.sekirankumo.fitlog.assistant.ToolOutcome
import io.github.sekirankumo.fitlog.assistant.arrayProp
import io.github.sekirankumo.fitlog.assistant.has
import io.github.sekirankumo.fitlog.assistant.integerProp
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.num
import io.github.sekirankumo.fitlog.assistant.numberProp
import io.github.sekirankumo.fitlog.assistant.objectSchema
import io.github.sekirankumo.fitlog.assistant.objects
import io.github.sekirankumo.fitlog.assistant.requireLong
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.assistant.stringProp
import io.github.sekirankumo.fitlog.data.ProfileRepository
import io.github.sekirankumo.fitlog.data.db.FoodDao
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.data.diet.DietRepository
import io.github.sekirankumo.fitlog.data.diet.describe
import io.github.sekirankumo.fitlog.data.diet.round1
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate

private val MEAL_KEYS = Meal.entries.map { it.key }

private val FOOD_ITEM_SCHEMA = objectSchema(
    listOf("name", "kcal", "protein_g", "fat_g", "carbs_g"),
    "name" to stringProp("食物名"),
    "amount" to stringProp("份量描述，如一碗、半份"),
    "grams" to numberProp("估算克数"),
    "kcal" to numberProp("热量 kcal"),
    "protein_g" to numberProp("蛋白质 g"),
    "fat_g" to numberProp("脂肪 g"),
    "carbs_g" to numberProp("碳水 g"),
    "meal" to stringProp("餐次", MEAL_KEYS),
    "time" to stringProp("进食时间 yyyy-MM-dd HH:mm，不填为现在"),
)

/** 把 yyyy-MM-dd 解析为日期，默认今天 */
internal fun parseDate(text: String?): LocalDate =
    text?.let { runCatching { LocalDate.parse(it.trim().take(10)) }.getOrNull() ?: throw ToolException("日期格式应为 yyyy-MM-dd") }
        ?: LocalDate.now()

private fun FoodEntry.toJson() = buildJsonObject {
    put("id", id)
    put("eatenAt", eatenAt)
    put("meal", meal)
    put("name", name)
    amount?.let { put("amount", it) }
    grams?.let { put("grams", it) }
    put("kcal", kcal)
    put("proteinG", proteinG)
    put("fatG", fatG)
    put("carbsG", carbsG)
    put("source", source)
    note?.let { put("note", it) }
}

private fun JsonObject.toFoodEntry() = FoodEntry(
    id = requireLong("id"),
    eatenAt = requireLong("eatenAt"),
    meal = str("meal") ?: Meal.Snack.key,
    name = str("name") ?: "",
    amount = str("amount"),
    grams = num("grams"),
    kcal = num("kcal") ?: 0.0,
    proteinG = num("proteinG") ?: 0.0,
    fatG = num("fatG") ?: 0.0,
    carbsG = num("carbsG") ?: 0.0,
    source = str("source") ?: "manual",
    note = str("note"),
)

class QueryFoodTool(private val dao: FoodDao, private val profile: ProfileRepository) : AiTool {
    override val name = "query_food_entries"
    override val description = "查询某天或某段时间的饮食记录和营养合计（含每日目标）"
    override val parameters = objectSchema(
        emptyList(),
        "date" to stringProp("日期 yyyy-MM-dd，默认今天"),
        "days" to integerProp("从 date 往前共查询多少天，默认 1"),
    )
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val end = parseDate(args.str("date"))
        val days = (args.long("days") ?: 1).coerceIn(1, 31)
        val targets = profile.currentTargets()
        val text = buildString {
            appendLine("每日目标：${targets.kcal}kcal 蛋白${targets.proteinG}g 脂肪${targets.fatG}g 碳水${targets.carbsG}g")
            for (i in days - 1 downTo 0) {
                val day = end.minusDays(i)
                val entries = dao.range(DietRepository.dayStart(day), DietRepository.dayStart(day.plusDays(1)))
                val t = DayTotals.of(entries)
                appendLine("== $day 合计 ${t.kcal.fmt(0)}kcal 蛋白${t.proteinG.fmt()}g 脂肪${t.fatG.fmt()}g 碳水${t.carbsG.fmt()}g")
                if (days <= 3) entries.forEach { appendLine(it.describe()) }
                if (entries.isEmpty()) appendLine("（没有记录）")
            }
        }
        return ToolOutcome(text.trim())
    }
}

class AddFoodTool(private val dao: FoodDao) : AiTool {
    override val name = "add_food_entries"
    override val description = "添加饮食记录。根据用户描述自行估算每种食物的份量和营养，每种食物一条；中式菜品要算上烹调用油"
    override val parameters = objectSchema(listOf("items"), "items" to arrayProp("食物列表", FOOD_ITEM_SCHEMA))
    override val isWrite = true

    private fun build(args: JsonObject): List<FoodEntry> {
        val items = args.objects("items")
        if (items.isEmpty()) throw ToolException("items 不能为空")
        return items.map { o ->
            val time = o.str("time")?.let { TimeUtil.parse(it) ?: throw ToolException("时间格式应为 yyyy-MM-dd HH:mm") }
                ?: System.currentTimeMillis()
            FoodEntry(
                eatenAt = time,
                meal = o.str("meal")?.let { Meal.of(it).key } ?: Meal.byHour(TimeUtil.toLocal(time).hour).key,
                name = o.str("name") ?: throw ToolException("食物名不能为空"),
                amount = o.str("amount"),
                grams = o.num("grams"),
                kcal = (o.num("kcal") ?: 0.0).round1(),
                proteinG = (o.num("protein_g") ?: 0.0).round1(),
                fatG = (o.num("fat_g") ?: 0.0).round1(),
                carbsG = (o.num("carbs_g") ?: 0.0).round1(),
                source = "ai",
            )
        }
    }

    override suspend fun preview(args: JsonObject) =
        "添加饮食记录：\n" + build(args).joinToString("\n") { it.describe().substringAfter(' ') }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val entries = build(args)
        val ids = dao.insertAll(entries)
        val total = entries.sumOf { it.kcal }
        return ToolOutcome(
            result = "已添加 ${entries.size} 条，id：${ids.joinToString()}",
            summary = "添加饮食：${entries.joinToString("、") { it.name }}（共 ${total.fmt(0)} kcal）",
            undoData = buildJsonObject { putJsonArray("ids") { ids.forEach { add(it) } } },
        )
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.deleteByIds(
            (undoData["ids"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() }
        )
    }
}

class UpdateFoodTool(private val dao: FoodDao) : AiTool {
    override val name = "update_food_entry"
    override val description = "修改一条饮食记录，只传需要修改的字段"
    override val parameters = objectSchema(
        listOf("id"),
        "id" to integerProp("记录 id"),
        "name" to stringProp("食物名"),
        "amount" to stringProp("份量描述"),
        "grams" to numberProp("克数"),
        "kcal" to numberProp("热量"),
        "protein_g" to numberProp("蛋白质 g"),
        "fat_g" to numberProp("脂肪 g"),
        "carbs_g" to numberProp("碳水 g"),
        "meal" to stringProp("餐次", MEAL_KEYS),
        "time" to stringProp("进食时间 yyyy-MM-dd HH:mm"),
    )
    override val isWrite = true

    private suspend fun load(args: JsonObject): Pair<FoodEntry, FoodEntry> {
        val old = dao.get(args.requireLong("id")) ?: throw ToolException("找不到饮食记录 #${args.long("id")}")
        val new = old.copy(
            name = args.str("name") ?: old.name,
            amount = if (args.has("amount")) args.str("amount") else old.amount,
            grams = if (args.has("grams")) args.num("grams") else old.grams,
            kcal = args.num("kcal") ?: old.kcal,
            proteinG = args.num("protein_g") ?: old.proteinG,
            fatG = args.num("fat_g") ?: old.fatG,
            carbsG = args.num("carbs_g") ?: old.carbsG,
            meal = args.str("meal")?.let { Meal.of(it).key } ?: old.meal,
            eatenAt = args.str("time")?.let { TimeUtil.parse(it) } ?: old.eatenAt,
        )
        return old to new
    }

    override suspend fun preview(args: JsonObject): String {
        val (old, new) = load(args)
        return "修改饮食记录：\n原：${old.describe()}\n新：${new.describe()}"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val (old, new) = load(args)
        dao.update(new)
        return ToolOutcome("已修改为 ${new.describe()}", "修改饮食：${new.name}", old.toJson())
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.insert(undoData.toFoodEntry())
    }
}

class DeleteFoodTool(private val dao: FoodDao) : AiTool {
    override val name = "delete_food_entry"
    override val description = "删除一条饮食记录"
    override val parameters = objectSchema(listOf("id"), "id" to integerProp("记录 id"))
    override val isWrite = true

    private suspend fun load(args: JsonObject) =
        dao.get(args.requireLong("id")) ?: throw ToolException("找不到饮食记录 #${args.long("id")}")

    override suspend fun preview(args: JsonObject) = "删除饮食记录：${load(args).describe()}"

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val old = load(args)
        dao.deleteById(old.id)
        return ToolOutcome("已删除 ${old.describe()}", "删除饮食：${old.name}", old.toJson())
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.insert(undoData.toFoodEntry())
    }
}

class ListFavoritesTool(private val dao: FoodDao) : AiTool {
    override val name = "list_favorite_foods"
    override val description = "列出用户收藏的常吃食物及其营养，记录这些食物时优先使用这里的数值"
    override val parameters = objectSchema()
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val list = dao.favorites()
        if (list.isEmpty()) return ToolOutcome("没有收藏的食物")
        return ToolOutcome(list.joinToString("\n") {
            "${it.name}${it.amount?.let { a -> "（$a）" } ?: ""} ${it.grams?.fmt(0)?.plus("g") ?: ""} " +
                "${it.kcal.fmt(0)}kcal 蛋白${it.proteinG.fmt()}g 脂肪${it.fatG.fmt()}g 碳水${it.carbsG.fmt()}g"
        })
    }
}
