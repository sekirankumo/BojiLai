package io.github.sekirankumo.fitlog.assistant.tools

import io.github.sekirankumo.fitlog.assistant.requireStr
import io.github.sekirankumo.fitlog.data.NutritionPlanRepository
import io.github.sekirankumo.fitlog.assistant.AiTool
import io.github.sekirankumo.fitlog.assistant.ToolException
import io.github.sekirankumo.fitlog.assistant.ToolOutcome
import io.github.sekirankumo.fitlog.assistant.arrayProp
import io.github.sekirankumo.fitlog.assistant.bool
import io.github.sekirankumo.fitlog.assistant.booleanProp
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
import io.github.sekirankumo.fitlog.data.db.FavoriteFood
import io.github.sekirankumo.fitlog.data.db.FoodDao
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.diet.DayQuality
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.data.diet.FoodGroup
import io.github.sekirankumo.fitlog.data.diet.componentList
import io.github.sekirankumo.fitlog.data.diet.encodeComponents
import io.github.sekirankumo.fitlog.data.diet.formatScore
import io.github.sekirankumo.fitlog.data.diet.DietRepository
import io.github.sekirankumo.fitlog.data.diet.describe
import io.github.sekirankumo.fitlog.data.diet.round1
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonObject
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
    "add_to_favorites" to booleanProp("同时加入常吃（用户说要收藏、加入常吃时才设为 true）"),
)

private val favoriteJson = Json { ignoreUnknownKeys = true }

private fun FavoriteFood.toJson(): JsonObject = favoriteJson.encodeToJsonElement(FavoriteFood.serializer(), this).jsonObject

private fun JsonObject.toFavorite(): FavoriteFood = favoriteJson.decodeFromJsonElement(FavoriteFood.serializer(), this)

private fun FavoriteFood.describe() =
    "#$id $name${amount?.let { "（$it）" } ?: ""} ${grams?.let { "${it.fmt(0)}g " } ?: ""}" +
        "${kcal.fmt(0)}kcal 蛋白${proteinG.fmt()}g 脂肪${fatG.fmt()}g 碳水${carbsG.fmt()}g"

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

class QueryFoodTool(private val dao: FoodDao, private val nutrition: NutritionPlanRepository) : AiTool {
    override val name = "query_food_entries"
    override val description = "查询某天或某段时间的饮食记录和营养合计（含当天的营养目标，每天的目标可能不同）"
    override val parameters = objectSchema(
        emptyList(),
        "date" to stringProp("日期 yyyy-MM-dd，默认今天"),
        "days" to integerProp("从 date 往前共查询多少天，默认 1"),
    )
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val end = parseDate(args.str("date"))
        val days = (args.long("days") ?: 1).coerceIn(1, 31)
        val text = buildString {
            for (i in days - 1 downTo 0) {
                val day = end.minusDays(i)
                val entries = dao.range(DietRepository.dayStart(day), DietRepository.dayStart(day.plusDays(1)))
                val t = DayTotals.of(entries)
                val target = nutrition.targetAt(day)
                appendLine("== $day 合计 ${t.kcal.fmt(0)}kcal 蛋白${t.proteinG.fmt()}g 脂肪${t.fatG.fmt()}g 碳水${t.carbsG.fmt()}g" + qualityText(entries))
                appendLine("当天目标 ${target.kcal}kcal 蛋白${target.proteinG}g 脂肪${target.fatG}g 碳水${target.carbsG}g")
                if (days <= 3) entries.forEach { appendLine(it.describe()) }
                if (entries.isEmpty()) appendLine("（没有记录）")
            }
        }
        return ToolOutcome(text.trim())
    }
}

/** 一天的膳食分，接在合计后面 */
private fun qualityText(entries: List<FoodEntry>): String {
    val q = DayQuality.of(entries) ?: return ""
    if (q.analyzed == 0) return " 膳食分：分析中"
    return " 膳食分 ${formatScore(q.total)}/49（${q.zone.label}）" + if (q.pending > 0) "，${q.pending} 条待分析" else ""
}

class DietQualityTool(private val dao: FoodDao) : AiTool {
    override val name = "get_diet_quality"
    override val description = "查询膳食质量分（GDQS，满分 49，≥23 良好，15–23 一般，<15 需改进）：每天的总分、各类食物吃了多少克和得分、扣分项和提分建议，以及高盐食物"
    override val parameters = objectSchema(
        emptyList(),
        "date" to stringProp("日期 yyyy-MM-dd，默认今天"),
        "days" to integerProp("从 date 往前共查询多少天，默认 1，最多 14"),
    )
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val end = parseDate(args.str("date"))
        val days = (args.long("days") ?: 1).coerceIn(1, 14)
        val text = buildString {
            for (i in days - 1 downTo 0) {
                val day = end.minusDays(i)
                val entries = dao.range(DietRepository.dayStart(day), DietRepository.dayStart(day.plusDays(1)))
                val q = DayQuality.of(entries)
                when {
                    q == null -> appendLine("== $day 没有记录")
                    q.analyzed == 0 -> appendLine("== $day 记录还在分析，暂时没有分数")
                    else -> {
                        appendLine("== $day 膳食分 ${formatScore(q.total)}/49（${q.zone.label}）" + if (q.pending > 0) "，${q.pending} 条待分析" else "")
                        if (days <= 3) {
                            val got = q.groups.filter { it.group.kind != FoodGroup.Kind.Unhealthy && it.points > 0 }
                            val lost = q.groups.filter { it.group.kind == FoodGroup.Kind.Unhealthy && it.points < it.group.maxPoints } +
                                q.groups.filter { it.group.kind == FoodGroup.Kind.Moderate && it.band == it.group.points.size - 1 && it.grams > 0 }
                            appendLine("加分：" + got.joinToString("、") { "${it.group.label}${it.grams.fmt(0)}g +${formatScore(it.points)}" }.ifEmpty { "无" })
                            appendLine("扣分：" + lost.joinToString("、") { "${it.group.label}${it.grams.fmt(0)}g（${formatScore(it.points)}/${formatScore(it.group.maxPoints)}）" }.ifEmpty { "无" })
                            q.suggestion?.let { (g, step) -> appendLine("提分建议：再吃 ${step.first.fmt(0)}g ${g.group.label} 可 +${formatScore(step.second)} 分") }
                        }
                        val salty = entries.filter { it.highSalt == true }
                        if (salty.isNotEmpty()) appendLine("高盐：" + salty.joinToString("、") { it.name })
                    }
                }
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

    /** 每条食物和它是否要加入常吃 */
    private fun items(args: JsonObject): List<Pair<FoodEntry, Boolean>> {
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
            ) to (o.bool("add_to_favorites") == true)
        }
    }

    override suspend fun preview(args: JsonObject) =
        "添加饮食记录：\n" + items(args).joinToString("\n") { (e, fav) ->
            e.describe().substringAfter(' ') + if (fav) "（加入常吃）" else ""
        }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val parsed = items(args)
        val entries = parsed.map { it.first }
        val ids = dao.insertAll(entries)
        val total = entries.sumOf { it.kcal }
        // 加入常吃：同名的覆盖，撤销时恢复原来的那条
        val favorites = buildJsonArray {
            parsed.filter { it.second }.forEach { (e, _) ->
                val existing = dao.favoriteByName(e.name)
                dao.insertFavorite(
                    FavoriteFood(
                        id = existing?.id ?: 0,
                        name = e.name,
                        amount = e.amount,
                        grams = e.grams,
                        kcal = e.kcal,
                        proteinG = e.proteinG,
                        fatG = e.fatG,
                        carbsG = e.carbsG,
                        useCount = (existing?.useCount ?: 0) + 1,
                        lastUsedAt = System.currentTimeMillis(),
                    ),
                )
                add(buildJsonObject {
                    put("name", e.name)
                    existing?.let { put("old", it.toJson()) }
                })
            }
        }
        val favCount = parsed.count { it.second }
        return ToolOutcome(
            result = "已添加 ${entries.size} 条，id：${ids.joinToString()}" + if (favCount > 0) "；$favCount 种已加入常吃" else "",
            summary = "添加饮食：${entries.joinToString("、") { it.name }}（共 ${total.fmt(0)} kcal）" +
                if (favCount > 0) "，并加入常吃" else "",
            undoData = buildJsonObject {
                putJsonArray("ids") { ids.forEach { add(it) } }
                put("favorites", favorites)
            },
        )
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.deleteByIds(
            (undoData["ids"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() }
        )
        (undoData["favorites"] as? kotlinx.serialization.json.JsonArray).orEmpty().forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val old = o["old"] as? JsonObject
            if (old != null) {
                dao.insertFavorite(old.toFavorite())
            } else {
                o.str("name")?.let { name -> dao.favoriteByName(name)?.let { dao.deleteFavorite(it.id) } }
            }
        }
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
        ).let { e ->
            val oldGrams = old.grams
            val newGrams = e.grams
            when {
                e.name != old.name -> e.copy(components = null, highSalt = null)
                oldGrams != null && oldGrams > 0 && newGrams != null && newGrams > 0 && newGrams != oldGrams ->
                    e.copy(components = encodeComponents(old.componentList?.map { it.copy(grams = (it.grams * newGrams / oldGrams).round1()) }))
                else -> e
            }
        }
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
        return ToolOutcome(list.joinToString("\n") { it.describe() })
    }
}

class AddFavoriteTool(private val dao: FoodDao) : AiTool {
    override val name = "add_favorite_food"
    override val description = "新增一个常吃食物（只收藏，不记录饮食），以后记一餐时点一下就能加入。营养数值按给出的份量填写；食材和膳食分类 App 会在后台自动补上"
    override val parameters = objectSchema(
        listOf("name", "kcal", "protein_g", "fat_g", "carbs_g"),
        "name" to stringProp("名称"),
        "amount" to stringProp("份量描述，例如「一碗」「1 杯」"),
        "grams" to numberProp("克数"),
        "kcal" to numberProp("热量"),
        "protein_g" to numberProp("蛋白质 g"),
        "fat_g" to numberProp("脂肪 g"),
        "carbs_g" to numberProp("碳水 g"),
        "high_salt" to booleanProp("是否高盐（腌腊、卤味、重口味等）"),
    )
    override val isWrite = true

    private suspend fun build(args: JsonObject): FavoriteFood {
        val name = args.requireStr("name").trim()
        if (dao.favoriteByName(name) != null) throw ToolException("已经有叫「$name」的常吃了，要改的话用 update_favorite_food")
        return FavoriteFood(
            name = name,
            amount = args.str("amount"),
            grams = args.num("grams"),
            kcal = (args.num("kcal") ?: 0.0).round1(),
            proteinG = (args.num("protein_g") ?: 0.0).round1(),
            fatG = (args.num("fat_g") ?: 0.0).round1(),
            carbsG = (args.num("carbs_g") ?: 0.0).round1(),
            highSalt = args.bool("high_salt"),
        )
    }

    override suspend fun preview(args: JsonObject) = "添加常吃：" + build(args).describe().substringAfter(' ')

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val food = build(args)
        val id = dao.insertFavorite(food)
        return ToolOutcome("已添加常吃 #$id ${food.name}", "添加常吃：${food.name}", buildJsonObject { put("id", id) })
    }

    override suspend fun undo(undoData: JsonObject) {
        undoData.long("id")?.let { dao.deleteFavorite(it) }
    }
}

class UpdateFavoriteTool(private val dao: FoodDao) : AiTool {
    override val name = "update_favorite_food"
    override val description = "修改一个常吃食物，只传需要修改的字段；id 来自 list_favorite_foods。只改克数、没给营养数值时，营养按克数等比例换算"
    override val parameters = objectSchema(
        listOf("id"),
        "id" to integerProp("常吃食物 id"),
        "name" to stringProp("名称"),
        "amount" to stringProp("份量描述"),
        "grams" to numberProp("克数"),
        "kcal" to numberProp("热量"),
        "protein_g" to numberProp("蛋白质 g"),
        "fat_g" to numberProp("脂肪 g"),
        "carbs_g" to numberProp("碳水 g"),
    )
    override val isWrite = true

    private suspend fun load(args: JsonObject): Pair<FavoriteFood, FavoriteFood> {
        val old = dao.favorite(args.requireLong("id")) ?: throw ToolException("找不到常吃食物 #${args.long("id")}")
        val newName = args.str("name")?.trim() ?: old.name
        if (newName != old.name && dao.favoriteByName(newName) != null) throw ToolException("已经有叫「$newName」的常吃了")
        val grams = if (args.has("grams")) args.num("grams") else old.grams
        // 只改了克数：营养按比例换算
        val nutrientsGiven = listOf("kcal", "protein_g", "fat_g", "carbs_g").any { args.has(it) }
        val ratio = if (!nutrientsGiven && grams != null && grams > 0 && old.grams != null && old.grams > 0) grams / old.grams else 1.0
        val new = old.copy(
            name = newName,
            amount = if (args.has("amount")) args.str("amount") else old.amount,
            grams = grams,
            kcal = (args.num("kcal") ?: old.kcal * ratio).round1(),
            proteinG = (args.num("protein_g") ?: old.proteinG * ratio).round1(),
            fatG = (args.num("fat_g") ?: old.fatG * ratio).round1(),
            carbsG = (args.num("carbs_g") ?: old.carbsG * ratio).round1(),
        )
        return old to new
    }

    override suspend fun preview(args: JsonObject): String {
        val (old, new) = load(args)
        return "修改常吃：\n原：${old.describe().substringAfter(' ')}\n新：${new.describe().substringAfter(' ')}"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val (old, new) = load(args)
        dao.insertFavorite(new)
        return ToolOutcome("已修改为 ${new.describe()}", "修改常吃：${new.name}", old.toJson())
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.insertFavorite(undoData.toFavorite())
    }
}

class DeleteFavoriteTool(private val dao: FoodDao) : AiTool {
    override val name = "delete_favorite_food"
    override val description = "从常吃里删除一个食物（不影响已有的饮食记录）；id 来自 list_favorite_foods"
    override val parameters = objectSchema(listOf("id"), "id" to integerProp("常吃食物 id"))
    override val isWrite = true

    private suspend fun load(args: JsonObject) =
        dao.favorite(args.requireLong("id")) ?: throw ToolException("找不到常吃食物 #${args.long("id")}")

    override suspend fun preview(args: JsonObject) = "从常吃里删除：${load(args).describe().substringAfter(' ')}"

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val old = load(args)
        dao.deleteFavorite(old.id)
        return ToolOutcome("已删除常吃 ${old.describe()}", "删除常吃：${old.name}", old.toJson())
    }

    override suspend fun undo(undoData: JsonObject) {
        dao.insertFavorite(undoData.toFavorite())
    }
}
