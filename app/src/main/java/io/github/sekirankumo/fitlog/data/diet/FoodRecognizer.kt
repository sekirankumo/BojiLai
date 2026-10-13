package io.github.sekirankumo.fitlog.data.diet

import io.github.sekirankumo.fitlog.assistant.num
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.data.ai.AiException
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.Msg
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.ai.extractJsonObject
import io.github.sekirankumo.fitlog.data.db.FavoriteFood
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.util.TimeUtil
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

/** AI 识别出、等待用户确认的一条食物 */
data class FoodDraft(
    val key: Long,
    val name: String,
    val amount: String?,
    val grams: Double?,
    val kcal: Double,
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    val meal: Meal,
    val eatenAt: Long,
    /** 高 / 中 / 低 */
    val confidence: String? = null,
    val addToFavorites: Boolean = false,
    val source: String = "ai",
    /** 当前营养数值对应的克数；输入框清空再输入时仍按它缩放 */
    val refGrams: Double? = grams,
) {
    /** 修改克数时按比例缩放营养素 */
    fun withGrams(newGrams: Double?): FoodDraft {
        val ref = refGrams
        if (newGrams == null || newGrams <= 0 || ref == null || ref <= 0) {
            return copy(grams = newGrams, refGrams = ref ?: newGrams?.takeIf { it > 0 })
        }
        val r = newGrams / ref
        return copy(
            grams = newGrams,
            refGrams = newGrams,
            kcal = (kcal * r).round1(),
            proteinG = (proteinG * r).round1(),
            fatG = (fatG * r).round1(),
            carbsG = (carbsG * r).round1(),
        )
    }

    /** 手动改营养数值后，以当前克数作为新的缩放基准 */
    fun withNutrients(
        kcal: Double = this.kcal,
        proteinG: Double = this.proteinG,
        fatG: Double = this.fatG,
        carbsG: Double = this.carbsG,
    ) = copy(
        kcal = kcal,
        proteinG = proteinG,
        fatG = fatG,
        carbsG = carbsG,
        refGrams = grams?.takeIf { it > 0 } ?: refGrams,
    )

    fun toEntry() = FoodEntry(
        eatenAt = eatenAt,
        meal = meal.key,
        name = name.trim(),
        amount = amount?.trim()?.ifEmpty { null },
        grams = grams,
        kcal = kcal,
        proteinG = proteinG,
        fatG = fatG,
        carbsG = carbsG,
        source = source,
    )

    companion object {
        private var counter = System.currentTimeMillis()
        fun nextKey(): Long = ++counter

        fun fromFavorite(f: FavoriteFood, eatenAt: Long) = FoodDraft(
            key = nextKey(),
            name = f.name,
            amount = f.amount,
            grams = f.grams,
            kcal = f.kcal,
            proteinG = f.proteinG,
            fatG = f.fatG,
            carbsG = f.carbsG,
            meal = Meal.byHour(TimeUtil.toLocal(eatenAt).hour),
            eatenAt = eatenAt,
            source = "favorite",
        )

        fun blank(eatenAt: Long) = FoodDraft(
            key = nextKey(),
            name = "",
            amount = null,
            grams = null,
            kcal = 0.0,
            proteinG = 0.0,
            fatG = 0.0,
            carbsG = 0.0,
            meal = Meal.byHour(TimeUtil.toLocal(eatenAt).hour),
            eatenAt = eatenAt,
            source = "manual",
        )
    }
}

fun Double.round1(): Double = (this * 10).roundToInt() / 10.0

/** 一张待识别的照片 */
data class FoodPhoto(
    /** data:image/jpeg;base64,... */
    val dataUrl: String,
    /** 拍摄时间（EXIF），未知为 null */
    val takenAt: Long?,
)

/** 用 AI 识别照片 / 文字描述中的食物，估算营养 */
class FoodRecognizer(
    private val settings: AiSettingsRepository,
    private val client: OpenAiClient,
) {
    /** day：记录到哪一天（补记以前的饮食时不是今天） */
    suspend fun recognize(
        text: String,
        photos: List<FoodPhoto>,
        day: java.time.LocalDate = java.time.LocalDate.now(),
    ): Pair<List<FoodDraft>, String?> {
        val isToday = day == java.time.LocalDate.now()
        val config = settings.current()
        val ref = if (photos.isNotEmpty()) {
            config.visionModel ?: throw AiException("还没有配置图片识别模型，请到 设置 → AI 服务 里选择")
        } else {
            config.assistantModel ?: config.visionModel ?: throw AiException("还没有配置 AI 模型")
        }
        if (ref.model.isBlank()) throw AiException("模型名为空，请到 设置 → AI 服务 里填写")
        val provider = config.provider(ref.providerId) ?: throw AiException("模型对应的服务已被删除")

        val now = if (isToday) System.currentTimeMillis() else TimeUtil.toEpoch(day.atTime(12, 0))
        val photoInfo = photos.mapIndexed { i, p ->
            "第 ${i + 1} 张照片拍摄时间：${p.takenAt?.let { TimeUtil.format(it) } ?: "未知"}"
        }.joinToString("\n")
        val prompt = buildString {
            appendLine("当前时间：${TimeUtil.nowForPrompt()}")
            if (!isToday) appendLine("用户在补记 $day 的饮食，除非另有说明，时间都放在这一天。")
            if (photoInfo.isNotEmpty()) appendLine(photoInfo)
            appendLine("用户描述：${text.ifBlank { "（无，请根据照片判断）" }}")
            appendLine()
            append(INSTRUCTIONS)
        }
        val message = if (photos.isEmpty()) Msg.user(prompt) else Msg.userWithImages(prompt, photos.map { it.dataUrl })
        val result = client.chat(
            provider,
            ref.model,
            listOf(Msg.system(SYSTEM), message),
            jsonMode = true,
            temperature = 0.2,
            stream = true,
        )
        val root = extractJsonObject(result.content ?: throw AiException("AI 没有返回内容"))
        val items = ((root["items"] ?: root["foods"]) as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            val time = o.str("time")?.let { TimeUtil.parse(it) }
                ?: photos.firstNotNullOfOrNull { it.takenAt }
                ?: now
            FoodDraft(
                key = FoodDraft.nextKey(),
                name = name,
                amount = o.str("amount"),
                grams = o.num("grams"),
                kcal = (o.num("kcal") ?: 0.0).round1(),
                proteinG = (o.num("protein_g") ?: 0.0).round1(),
                fatG = (o.num("fat_g") ?: 0.0).round1(),
                carbsG = (o.num("carbs_g") ?: 0.0).round1(),
                meal = o.str("meal")?.let { Meal.of(it) } ?: Meal.byHour(TimeUtil.toLocal(time).hour),
                eatenAt = time,
                confidence = o.str("confidence"),
            )
        }
        if (items.isEmpty()) throw AiException(root.str("note") ?: "没有识别出食物，可以补充文字描述后再试")
        return items to root.str("note")
    }

    private companion object {
        const val SYSTEM = "你是专业的营养师，擅长估算中国家常菜、外卖、餐厅菜品和各类零食饮料的份量与营养。只输出 JSON。"

        val INSTRUCTIONS = """
            请识别用户吃了哪些食物，每种食物单独一条，估算份量和营养，输出 JSON：
            {"items":[{"name":"食物名","amount":"份量描述，如一碗/半份/2个","grams":克数,"kcal":热量,"protein_g":蛋白质克,"fat_g":脂肪克,"carbs_g":碳水克,"meal":"breakfast|lunch|dinner|snack","time":"yyyy-MM-dd HH:mm","confidence":"高|中|低"}],"note":"需要提醒用户的地方，没有就留空"}

            规则：
            1. 一道菜里有明显不同的部分（如盖饭的米饭和浇头、套餐的主食和配菜）要拆成多条。
            2. 份量按中国常见餐具和外卖规格估算：一碗米饭约 150–200g 熟重，一份外卖盖饭米饭约 250–300g，一个馒头约 100g，一碗面（含汤）面条熟重约 250–300g。
            3. 家常菜和外卖要把烹调用油算进去（炒菜每份约 10–20g 油），宁可略高估也不要低估。
            4. 用户说了品牌或连锁店（如麦当劳、星巴克）时，尽量按该品牌的官方营养数据。
            5. "半碗""一小盘""吃了几口"这类模糊描述，按常识换算成克数。
            6. 时间：用户说了时间（"中午""刚才""昨晚"）就换算成具体时间；否则用照片拍摄时间；都没有就用当前时间。meal 根据时间和食物判断。
            7. 照片看不清或不确定时 confidence 填「低」，并在 note 里说明。
            8. 数值都是数字，不要带单位。
        """.trimIndent()
    }
}
