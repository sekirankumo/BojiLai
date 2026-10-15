package io.github.sekirankumo.fitlog.data.diet

import io.github.sekirankumo.fitlog.data.db.FoodEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 全球膳食质量评分（GDQS，Bromage 等 2021）的 25 类食物。
 * 每类按「一天吃了多少克」分档给分：cutoffs 是分档边界，points 是每档的分数（比 cutoffs 多一个）。
 * 边界值算进中间那一档：「<24 / 24–69 / >69」里 24 和 69 都是第二档。
 * 来源：Development and Validation of a Novel Food-Based Global Diet Quality Score, J Nutr 2021, Table 3。
 */
enum class FoodGroup(
    val key: String,
    val label: String,
    /** 拼图格子上的简称 */
    val short: String,
    val kind: Kind,
    val cutoffs: List<Double>,
    val points: List<Double>,
    /** 给 AI 归类用的中国常见食物 */
    val examples: String,
) {
    Citrus("citrus", "柑橘类水果", "柑橘", Kind.Healthy, listOf(24.0, 69.0), listOf(0.0, 1.0, 2.0), "橙子、橘子、柚子、柠檬"),
    OrangeFruit("orange_fruit", "深橙色水果", "橙黄果", Kind.Healthy, listOf(25.0, 123.0), listOf(0.0, 1.0, 2.0), "芒果、木瓜、杏、柿子、黄桃、哈密瓜"),
    OtherFruit("other_fruit", "其他水果", "其他果", Kind.Healthy, listOf(27.0, 107.0), listOf(0.0, 1.0, 2.0), "苹果、香蕉、葡萄、西瓜、梨、草莓、猕猴桃"),
    DarkLeafy("dark_leafy", "深绿色叶菜", "深绿叶菜", Kind.Healthy, listOf(13.0, 37.0), listOf(0.0, 2.0, 4.0), "菠菜、空心菜、油麦菜、苋菜、小白菜、油菜、茼蒿"),
    Cruciferous("cruciferous", "十字花科蔬菜", "十字花科", Kind.Healthy, listOf(13.0, 36.0), listOf(0.0, 0.25, 0.5), "西兰花、花菜、卷心菜、大白菜、萝卜"),
    OrangeVeg("orange_veg", "深橙色蔬菜", "橙黄菜", Kind.Healthy, listOf(9.0, 45.0), listOf(0.0, 0.25, 0.5), "胡萝卜、南瓜、红彩椒"),
    OtherVeg("other_veg", "其他蔬菜", "其他蔬菜", Kind.Healthy, listOf(23.0, 114.0), listOf(0.0, 0.25, 0.5), "番茄、黄瓜、茄子、青椒、洋葱、蘑菇、豆芽、冬瓜、生菜"),
    Legumes("legumes", "豆类和豆制品", "豆类", Kind.Healthy, listOf(9.0, 42.0), listOf(0.0, 2.0, 4.0), "黄豆、毛豆、豆腐、豆浆、豆干、腐竹、红豆、绿豆（不含豆芽、花生）"),
    OrangeTuber("orange_tuber", "深橙色根茎", "红薯类", Kind.Healthy, listOf(12.0, 63.0), listOf(0.0, 0.25, 0.5), "红心（橙心）红薯"),
    Nuts("nuts", "坚果和种子", "坚果", Kind.Healthy, listOf(7.0, 13.0), listOf(0.0, 2.0, 4.0), "花生、核桃、杏仁、腰果、芝麻、瓜子、花生酱"),
    WholeGrains("whole_grains", "全谷物", "全谷物", Kind.Healthy, listOf(8.0, 13.0), listOf(0.0, 1.0, 2.0), "糙米、燕麦、玉米、小米、荞麦、全麦面包、杂粮"),
    LiquidOil("liquid_oil", "植物油", "植物油", Kind.Healthy, listOf(2.0, 7.5), listOf(0.0, 1.0, 2.0), "炒菜、凉拌用的花生油、菜籽油、橄榄油等液体油（外购油炸食品里的油不算）"),
    Fish("fish", "鱼虾贝类", "鱼虾", Kind.Healthy, listOf(14.0, 71.0), listOf(0.0, 1.0, 2.0), "各种鱼、虾、蟹、贝类、鱿鱼"),
    Poultry("poultry", "禽肉", "禽肉", Kind.Healthy, listOf(16.0, 44.0), listOf(0.0, 1.0, 2.0), "鸡肉、鸭肉、鹅肉（未经腌制、熏制）"),
    LowFatDairy("low_fat_dairy", "低脂奶", "低脂奶", Kind.Healthy, listOf(33.0, 132.0), listOf(0.0, 1.0, 2.0), "脱脂、低脂牛奶，低脂酸奶（脂肪 ≤2%）"),
    Eggs("eggs", "蛋类", "蛋", Kind.Healthy, listOf(6.0, 32.0), listOf(0.0, 1.0, 2.0), "鸡蛋、鸭蛋、鹌鹑蛋（不含蛋黄酱）"),
    HighFatDairy("high_fat_dairy", "全脂奶", "全脂奶", Kind.Moderate, listOf(35.0, 142.0, 734.0), listOf(0.0, 1.0, 2.0, 0.0), "全脂牛奶、普通酸奶、奶茶里的奶；奶酪按 1 克折 6 克奶算"),
    RedMeat("red_meat", "红肉", "红肉", Kind.Moderate, listOf(9.0, 46.0), listOf(0.0, 1.0, 0.0), "猪肉、牛肉、羊肉及内脏（未经腌制、熏制）"),
    ProcessedMeat("processed_meat", "加工肉", "加工肉", Kind.Unhealthy, listOf(9.0, 30.0), listOf(2.0, 1.0, 0.0), "香肠、火腿、培根、腊肉、咸肉、午餐肉、肉松、肉干"),
    RefinedGrains("refined_grains", "精制谷物和烘焙", "精制谷物", Kind.Unhealthy, listOf(7.0, 33.0), listOf(2.0, 1.0, 0.0), "白米饭、白面条、馒头、包子饺子皮、白面包、米粉、方便面面饼"),
    Sweets("sweets", "甜食和冰淇淋", "甜食", Kind.Unhealthy, listOf(13.0, 37.0), listOf(2.0, 1.0, 0.0), "蛋糕、甜点、糖果、巧克力、冰淇淋、蜂蜜、奶茶和咖啡里加的糖"),
    SugaryDrinks("ssb", "含糖饮料", "含糖饮料", Kind.Unhealthy, listOf(57.0, 180.0), listOf(2.0, 1.0, 0.0), "可乐、雪碧等汽水（含无糖汽水）、功能饮料、运动饮料（奶茶、甜咖啡不算这一类）"),
    Juice("juice", "果汁", "果汁", Kind.Unhealthy, listOf(36.0, 144.0), listOf(2.0, 1.0, 0.0), "各种果汁、果蔬汁、水果奶昔"),
    WhiteTubers("white_tubers", "白色根茎", "土豆类", Kind.Unhealthy, listOf(27.0, 107.0), listOf(2.0, 1.0, 0.0), "土豆、山药、芋头、木薯、紫薯、白薯、薯条薯片里的土豆"),
    Fried("fried", "外购油炸食品", "油炸", Kind.Unhealthy, listOf(9.0, 45.0), listOf(2.0, 1.0, 0.0), "外面买的炸鸡、薯条、油条、炸串、薯片、方便面（油炸面饼）");

    enum class Kind { Healthy, Moderate, Unhealthy }

    val maxPoints: Double = points.max()

    /** 吃了 grams 克落在第几档 */
    fun band(grams: Double): Int {
        if (grams < cutoffs[0]) return 0
        var i = 1
        while (i < cutoffs.size && grams > cutoffs[i]) i++
        return i
    }

    fun pointsFor(grams: Double): Double = points[band(grams)]

    companion object {
        /** 不计分：黄油猪油等固体脂肪、椰子、酒、咖啡和茶本身、调味料 */
        const val NONE = "none"

        fun of(key: String?): FoodGroup? = entries.firstOrNull { it.key == key }
    }
}

/** 一样食材：名称、属于哪一类（FoodGroup.key 或 none）、多少克 */
@Serializable
data class FoodComponent(val name: String, val group: String, val grams: Double)

private val componentJson = Json { ignoreUnknownKeys = true }
private val componentListSerializer = ListSerializer(FoodComponent.serializer())

fun encodeComponents(list: List<FoodComponent>?): String? = list?.let { componentJson.encodeToString(componentListSerializer, it) }

fun decodeComponents(text: String?): List<FoodComponent>? =
    text?.let { runCatching { componentJson.decodeFromString(componentListSerializer, it) }.getOrNull() }

/** 这条记录的食材；还没分析过时为 null */
val FoodEntry.componentList: List<FoodComponent>? get() = decodeComponents(components)

/** 一类食物当天的得分 */
data class GroupScore(val group: FoodGroup, val grams: Double) {
    val points: Double = group.pointsFor(grams)
    val band: Int = group.band(grams)

    /** 健康食物（和适量食物）再吃多少克能升一档、加几分；已经到头或者再多吃反而扣分时为 null */
    val nextStep: Pair<Double, Double>?
        get() {
            if (group.kind == FoodGroup.Kind.Unhealthy || band + 1 >= group.points.size) return null
            val gain = group.points[band + 1] - points
            if (gain <= 0) return null
            val target = if (band == 0) group.cutoffs[0] else group.cutoffs[band] + 1
            return (target - grams).coerceAtLeast(1.0) to gain
        }
}

enum class QualityZone(val label: String) { Poor("需改进"), Fair("一般"), Good("良好") }

/**
 * 一天的膳食质量。
 * @param analyzed 已经拆好食材的记录数；pending 是还在后台分析的
 */
class DayQuality(
    val groups: List<GroupScore>,
    val analyzed: Int,
    val pending: Int,
) {
    val total: Double = groups.sumOf { it.points }
    val zone: QualityZone = zoneOf(total)

    /** 多吃一点就能加最多分的那一类：先比加的分，再比要吃的克数 */
    val suggestion: Pair<GroupScore, Pair<Double, Double>>?
        get() = groups.mapNotNull { g -> g.nextStep?.let { g to it } }
            .filter { it.second.first <= 150 }
            .sortedWith(compareByDescending<Pair<GroupScore, Pair<Double, Double>>> { it.second.second }.thenBy { it.second.first })
            .firstOrNull()

    companion object {
        const val MAX = 49.0
        const val FAIR_FROM = 15.0
        const val GOOD_FROM = 23.0

        fun zoneOf(score: Double) = when {
            score >= GOOD_FROM -> QualityZone.Good
            score >= FAIR_FROM -> QualityZone.Fair
            else -> QualityZone.Poor
        }

        /** 一天的记录算膳食分；没有任何记录时为 null（什么都没吃按规则会得 14 分，没有意义） */
        fun of(entries: List<FoodEntry>): DayQuality? {
            if (entries.isEmpty()) return null
            val grams = HashMap<FoodGroup, Double>()
            var analyzed = 0
            var pending = 0
            for (e in entries) {
                val list = e.componentList
                if (list == null) {
                    pending++
                    continue
                }
                analyzed++
                for (c in list) {
                    val g = FoodGroup.of(c.group) ?: continue
                    grams[g] = (grams[g] ?: 0.0) + c.grams.coerceAtLeast(0.0)
                }
            }
            return DayQuality(FoodGroup.entries.map { GroupScore(it, grams[it] ?: 0.0) }, analyzed, pending)
        }
    }
}

/** 分数显示：整数不带小数，否则保留一位 */
fun formatScore(score: Double): String {
    val r = Math.round(score * 10) / 10.0
    return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString()
}

/** 让 AI 拆食材、判断高盐时用的说明，识别食物、后台补算、AI 助手共用 */
object DietPrompt {
    val GROUPS: String = FoodGroup.entries.joinToString("\n") { "- ${it.key}：${it.label}，如${it.examples}" } +
        "\n- ${FoodGroup.NONE}：不计分，如黄油猪油等固体脂肪、椰子、酒、咖啡和茶本身"

    val RULES: String = """
        食材拆分（components）：
        1. 每种食物拆成主要食材，估算每样实际吃下去的克数（熟重），group 只能从下面的分类 key 里选一个。
        2. 炒菜、凉拌用的油单独列一项，归 liquid_oil，一般每份菜 5–15 克。自家油炸用的油也算 liquid_oil。
        3. 外面买的油炸食品要算两次：先按原料归类（油条→refined_grains，薯条→white_tubers，炸鸡→poultry），再用同样的克数在 fried 里列一次。方便面的面饼算 refined_grains 加 fried。
        4. 奶茶拆成奶（high_fat_dairy 或 low_fat_dairy）和加的糖（sweets，按糖的克数）；甜咖啡、甜饮品里加的糖同样算 sweets。做菜放的少量调味糖不用列。
        5. 以叶子为主的深绿色十字花科菜（小白菜、油菜、芥蓝等）算 dark_leafy。
        6. 盐、酱油、醋、葱姜蒜、香料等调料不用列。
        高盐（high_salt）：一份含盐超过约 1.8 克，或属于腌制咸菜、酱卤、加工肉、方便面、连汤喝的汤面、火锅、麻辣烫、重口味外卖等典型高盐食物时为 true，否则为 false。

        分类：
    """.trimIndent() + "\n" + GROUPS

    /** 单条食物的 JSON 字段说明 */
    const val FIELDS = "\"components\":[{\"name\":\"食材名\",\"group\":\"分类 key\",\"grams\":克数}],\"high_salt\":true或false"
}

/** 解析 AI 返回的食材列表，分类不认识的归为不计分，克数不合法的丢掉 */
fun parseComponents(array: kotlinx.serialization.json.JsonArray?): List<FoodComponent>? {
    if (array == null) return null
    return array.mapNotNull { el ->
        val o = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
        val name = (o["name"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val grams = (o["grams"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        val key = (o["group"] as? kotlinx.serialization.json.JsonPrimitive)?.content
        FoodComponent(name, FoodGroup.of(key)?.key ?: FoodGroup.NONE, Math.round(grams * 10) / 10.0)
    }
}
