package io.github.sekirankumo.fitlog

import io.github.sekirankumo.fitlog.data.NutritionNotice
import io.github.sekirankumo.fitlog.data.NutritionBaseline
import io.github.sekirankumo.fitlog.data.db.NutritionDay
import io.github.sekirankumo.fitlog.assistant.ChatItem
import io.github.sekirankumo.fitlog.data.ai.AiProvider
import io.github.sekirankumo.fitlog.data.ai.ModelRef
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.FavoriteFood
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.diet.FoodComponent
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.data.diet.encodeComponents
import io.github.sekirankumo.fitlog.data.plan.PlanExercise
import io.github.sekirankumo.fitlog.data.plan.PlanMeta
import io.github.sekirankumo.fitlog.data.plan.PlanSet
import io.github.sekirankumo.fitlog.util.TimeUtil
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.sin

/** 截图用的示例数据：一位正在减脂的用户，计划进行到第三周 */
object SampleData {
    private val today: LocalDate get() = LocalDate.now()

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = TimeUtil.toEpoch(day.atTime(hour, minute))

    suspend fun seed(container: AppContainer) {
        val db = container.database
        // AI 服务：助手回答上显示的名字就是这里配的模型名（Key 是假的，截图不会联网）
        container.aiSettings.update {
            it.copy(
                providers = listOf(AiProvider("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4/", "test-key")),
                assistantModel = ModelRef("zhipu", "glm-4-flash"),
                visionModel = ModelRef("zhipu", "glm-4v-flash"),
            )
        }
        container.profile.update {
            UserProfile(
                sex = "male",
                birthYear = today.year - 27,
                heightCm = 175.0,
                activityLevel = 1,
                goal = "lose",
                trainingDaysPerWeek = 4,
                sessionMinutes = 60,
                equipment = "健身房",
                notes = "左肩以前拉伤过，推举类动作别太重",
            )
        }

        // 身体数据：30 天从 74.6 降到 72.4，带一点日常波动
        val body = (0..29).filter { it % 3 != 1 }.map { d ->
            val day = today.minusDays((29 - d).toLong())
            val trend = 74.6 - 2.2 * d / 29.0
            val noise = 0.35 * sin(d * 1.7)
            BodyRecord(
                measuredAt = at(day, 7, 40),
                weightKg = Math.round((trend + noise) * 10) / 10.0,
                bodyFatPct = Math.round((20.6 - 1.4 * d / 29.0 + 0.2 * sin(d * 1.3)) * 10) / 10.0,
                source = if (d % 2 == 0) "health_connect" else "manual",
            )
        }
        db.bodyRecordDao().insertAll(body)

        // 饮食：今天的，加上前几天的（日期条上显示每天的膳食分颜色）
        fun food(
            meal: Meal, h: Int, m: Int, name: String, amount: String, g: Double, kcal: Double, p: Double, f: Double, c: Double,
            parts: List<FoodComponent>, salty: Boolean = false, day: LocalDate = today,
        ) = FoodEntry(
            eatenAt = at(day, h, m), meal = meal.key, name = name, amount = amount, grams = g, kcal = kcal, proteinG = p, fatG = f, carbsG = c,
            source = "ai", components = encodeComponents(parts), highSalt = salty,
        )
        fun part(name: String, group: String, grams: Double) = FoodComponent(name, group, grams)
        val d1 = today.minusDays(1)
        val d2 = today.minusDays(2)
        val d3 = today.minusDays(3)
        db.foodDao().insertAll(
            listOf(
                food(Meal.Breakfast, 7, 50, "全麦面包", "2 片", 70.0, 172.0, 8.6, 2.4, 29.0, listOf(part("全麦面包", "whole_grains", 70.0))),
                food(Meal.Breakfast, 7, 50, "水煮蛋", "2 个", 100.0, 144.0, 12.6, 9.6, 1.2, listOf(part("鸡蛋", "eggs", 100.0))),
                food(Meal.Breakfast, 7, 50, "无糖豆浆", "1 杯", 300.0, 93.0, 8.7, 4.2, 4.5, listOf(part("豆浆", "legumes", 300.0))),
                food(
                    Meal.Lunch, 12, 20, "黄焖鸡", "一份", 250.0, 420.0, 32.0, 24.0, 18.0,
                    listOf(part("鸡肉", "poultry", 150.0), part("土豆", "white_tubers", 50.0), part("香菇", "other_veg", 30.0), part("青椒", "other_veg", 20.0), part("植物油", "liquid_oil", 10.0)),
                    salty = true,
                ),
                food(Meal.Lunch, 12, 20, "米饭", "半碗", 150.0, 174.0, 3.9, 0.5, 38.9, listOf(part("米饭", "refined_grains", 150.0))),
                food(Meal.Lunch, 12, 20, "凉拌黄瓜", "一小盘", 100.0, 48.0, 0.9, 3.2, 3.9, listOf(part("黄瓜", "other_veg", 95.0), part("香油", "liquid_oil", 5.0))),
                food(Meal.Snack, 16, 0, "希腊酸奶", "1 杯", 150.0, 146.0, 13.5, 6.0, 9.0, listOf(part("希腊酸奶", "high_fat_dairy", 150.0))),
                // 昨天：吃得很好
                food(Meal.Breakfast, 8, 0, "燕麦牛奶", "一碗", 300.0, 230.0, 12.0, 4.0, 36.0, listOf(part("燕麦", "whole_grains", 50.0), part("低脂牛奶", "low_fat_dairy", 250.0)), day = d1),
                food(Meal.Lunch, 12, 30, "清蒸鲈鱼", "半条", 150.0, 165.0, 28.0, 5.0, 1.0, listOf(part("鲈鱼", "fish", 150.0)), day = d1),
                food(Meal.Lunch, 12, 30, "蒜蓉菠菜", "一盘", 160.0, 90.0, 4.0, 6.0, 5.0, listOf(part("菠菜", "dark_leafy", 150.0), part("植物油", "liquid_oil", 8.0)), day = d1),
                food(Meal.Snack, 16, 0, "坚果和苹果", "一小把 + 1 个", 220.0, 230.0, 5.0, 12.0, 28.0, listOf(part("混合坚果", "nuts", 20.0), part("苹果", "other_fruit", 200.0)), day = d1),
                // 前天：泡面加可乐
                food(
                    Meal.Dinner, 22, 0, "红烧牛肉面（泡面）", "一桶", 120.0, 520.0, 10.0, 22.0, 70.0,
                    listOf(part("面饼", "refined_grains", 80.0), part("面饼", "fried", 80.0)), salty = true, day = d2,
                ),
                food(Meal.Dinner, 22, 0, "可乐", "一罐", 330.0, 140.0, 0.0, 0.0, 35.0, listOf(part("可乐", "ssb", 330.0)), day = d2),
                food(Meal.Dinner, 22, 0, "火腿肠", "1 根", 40.0, 110.0, 4.0, 8.0, 5.0, listOf(part("火腿肠", "processed_meat", 40.0)), salty = true, day = d2),
                // 大前天：一般
                food(Meal.Breakfast, 8, 0, "豆浆鸡蛋", "1 杯 + 1 个", 350.0, 170.0, 15.0, 9.0, 6.0, listOf(part("豆浆", "legumes", 300.0), part("鸡蛋", "eggs", 50.0)), day = d3),
                food(
                    Meal.Lunch, 12, 0, "红烧肉盖饭", "一份", 450.0, 820.0, 22.0, 40.0, 90.0,
                    listOf(part("红烧肉", "red_meat", 100.0), part("米饭", "refined_grains", 300.0), part("植物油", "liquid_oil", 10.0)), salty = true, day = d3,
                ),
            )
        )
        listOf(
            FavoriteFood(
                name = "鸡胸肉沙拉", amount = "一份", grams = 300.0, kcal = 320.0, proteinG = 35.0, fatG = 12.0, carbsG = 15.0, useCount = 8,
                components = encodeComponents(listOf(FoodComponent("鸡胸肉", "poultry", 150.0), FoodComponent("生菜", "other_veg", 100.0), FoodComponent("橄榄油", "liquid_oil", 10.0))),
                highSalt = false,
            ),
            FavoriteFood(
                name = "燕麦牛奶", amount = "一碗", grams = 300.0, kcal = 280.0, proteinG = 12.0, fatG = 8.0, carbsG = 38.0, useCount = 6,
                components = encodeComponents(listOf(FoodComponent("燕麦", "whole_grains", 50.0), FoodComponent("牛奶", "high_fat_dairy", 250.0))),
                highSalt = false,
            ),
            FavoriteFood(
                name = "螺蛳粉", amount = "一碗", grams = 450.0, kcal = 560.0, proteinG = 15.0, fatG = 20.0, carbsG = 80.0, useCount = 3,
                components = encodeComponents(listOf(FoodComponent("米粉", "refined_grains", 250.0), FoodComponent("炸腐竹", "fried", 20.0), FoodComponent("酸笋", "other_veg", 30.0))),
                highSalt = true,
            ),
            FavoriteFood(name = "蛋白粉", amount = "1 勺", grams = 30.0, kcal = 120.0, proteinG = 24.0, fatG = 1.5, carbsG = 3.0, useCount = 5, components = "[]", highSalt = false),
        ).forEach { db.foodDao().insertFavorite(it) }

        // 训练计划：每周一三五六，4 周
        val plans = container.plans
        val start = today.minusWeeks(2).with(DayOfWeek.MONDAY)
        plans.setMeta(
            PlanMeta(
                name = "减脂增肌 · 4 周计划",
                goal = "减脂",
                summary = "每周 4 练，上下肢分化。复合动作打底，组间休息短一点，配合每天 400 kcal 左右的热量缺口。",
                progression = "每周在能完成全部次数的动作上加 2.5kg",
                startDate = start.toEpochDay(),
                endDate = start.plusWeeks(4).minusDays(1).toEpochDay(),
            )
        )
        val days = mutableListOf<PlanDay>()
        var d = start
        while (d.isBefore(start.plusWeeks(4))) {
            val template = when (d.dayOfWeek) {
                DayOfWeek.MONDAY -> "胸 + 三头" to chest()
                DayOfWeek.WEDNESDAY -> "腿 + 臀" to legs()
                DayOfWeek.FRIDAY -> "背 + 二头" to back()
                DayOfWeek.SATURDAY -> "肩 + 核心" to shoulders()
                else -> null
            }
            if (template != null && d != today) {
                val past = d.isBefore(today)
                val missed = past && d == start.plusDays(9) // 第二周周三漏练了
                val ex = if (past && !missed) template.second.map { e -> e.copy(sets = e.sets.map { it.copy(done = true, doneReps = it.reps, doneWeightKg = it.weightKg, doneDurationSec = it.durationSec) }) } else template.second
                days += PlanDay(
                    date = d.toEpochDay(),
                    title = template.first,
                    exercisesJson = plans.encode(ex),
                    status = when {
                        missed -> DayStatus.MISSED
                        past -> DayStatus.DONE
                        else -> DayStatus.PLANNED
                    },
                )
            }
            d = d.plusDays(1)
        }
        // 更早的训练历史（上一份计划），让热力图看起来像真实使用了几个月
        var h = start.minusWeeks(20)
        var n = 0
        while (h.isBefore(start)) {
            val t = when (h.dayOfWeek) {
                DayOfWeek.MONDAY -> "胸 + 三头" to chest()
                DayOfWeek.WEDNESDAY -> "腿 + 臀" to legs()
                DayOfWeek.FRIDAY -> "背 + 二头" to back()
                DayOfWeek.SATURDAY -> "肩 + 核心" to shoulders()
                else -> null
            }
            n++
            // 偶尔漏练、偶尔少做几个动作，看起来更真实
            val week = n / 7
            if (t != null && (week * 3 + h.dayOfWeek.value) % 10 != 0 && (week + h.dayOfWeek.value) % 13 != 0) {
                val keep = if (n % 4 == 0) t.second.dropLast(2) else t.second
                val ex = keep.map { e -> e.copy(sets = e.sets.map { it.copy(done = true, doneReps = it.reps, doneWeightKg = it.weightKg, doneDurationSec = it.durationSec) }) }
                days += PlanDay(
                    date = h.toEpochDay(),
                    title = t.first,
                    exercisesJson = plans.encode(ex),
                    status = DayStatus.DONE,
                    completedAt = at(h, 19, 30),
                )
            }
            h = h.plusDays(1)
        }

        // 今天：胸 + 三头，卧推已经做完，上斜哑铃做了一组
        val todayEx = chest().mapIndexed { i, e ->
            when (i) {
                0 -> e.copy(sets = e.sets.mapIndexed { si, s -> s.copy(done = true, doneReps = if (si == 3) 6 else s.reps, doneWeightKg = s.weightKg) })
                1 -> e.copy(sets = e.sets.mapIndexed { si, s -> if (si == 0) s.copy(done = true, doneReps = s.reps, doneWeightKg = s.weightKg) else s })
                else -> e
            }
        }
        days += PlanDay(date = today.toEpochDay(), title = "胸 + 三头", exercisesJson = plans.encode(todayEx), status = DayStatus.PLANNED)
        plans.upsertAll(days)
    }

    private fun sets(n: Int, reps: Int?, kg: Double?, sec: Int? = null) = List(n) { PlanSet(reps = reps, weightKg = kg, durationSec = sec) }

    private fun chest() = listOf(
        PlanExercise("Barbell_Bench_Press_-_Medium_Grip", "杠铃卧推", sets(4, 8, 60.0), 120, "肩胛骨收紧，下放到乳头附近"),
        PlanExercise("Incline_Dumbbell_Press", "上斜哑铃卧推", sets(3, 10, 22.0), 90),
        PlanExercise("Cable_Crossover", "绳索夹胸", sets(3, 12, 15.0), 60),
        PlanExercise("Triceps_Pushdown_-_Rope_Attachment", "绳索下压（绳索把手）", sets(3, 12, 25.0), 60),
        PlanExercise("Plank", "平板支撑", sets(3, null, null, 60), 45),
    )

    private fun legs() = listOf(
        PlanExercise("Barbell_Squat", "杠铃深蹲", sets(4, 8, 70.0), 120),
        PlanExercise("Romanian_Deadlift", "罗马尼亚硬拉", sets(3, 10, 50.0), 90),
        PlanExercise("Leg_Press", "腿举", sets(3, 12, 100.0), 90),
        PlanExercise("Barbell_Hip_Thrust", "杠铃臀推", sets(3, 12, 60.0), 90),
        PlanExercise("Standing_Calf_Raises", "站姿提踵", sets(3, 15, 40.0), 45),
    )

    private fun back() = listOf(
        PlanExercise("Wide-Grip_Lat_Pulldown", "宽握高位下拉", sets(4, 10, 50.0), 90),
        PlanExercise("Bent_Over_Barbell_Row", "杠铃俯身划船", sets(4, 8, 50.0), 90),
        PlanExercise("Seated_Cable_Rows", "坐姿绳索划船", sets(3, 12, 45.0), 60),
        PlanExercise("Hammer_Curls", "锤式弯举", sets(3, 12, 12.0), 60),
    )

    private fun shoulders() = listOf(
        PlanExercise("Seated_Dumbbell_Press", "坐姿哑铃推举", sets(4, 10, 16.0), 90),
        PlanExercise("Side_Lateral_Raise", "哑铃侧平举", sets(4, 15, 7.5), 60),
        PlanExercise("Face_Pull", "绳索面拉", sets(3, 15, 20.0), 60),
        PlanExercise("Hanging_Leg_Raise", "悬垂举腿", sets(3, 12, null), 60),
    )

    /** 「记一餐」页 AI 识别后的结果 */
    fun drafts(): List<FoodDraft> {
        val t = at(today, 18, 40)
        return listOf(
            FoodDraft(
                FoodDraft.nextKey(), "红烧牛腩", "一份", 280.0, 392.0, 30.8, 22.4, 16.8, Meal.Dinner, t, confidence = "中",
                components = listOf(FoodComponent("牛腩", "red_meat", 120.0), FoodComponent("土豆", "white_tubers", 80.0), FoodComponent("胡萝卜", "orange_veg", 40.0), FoodComponent("植物油", "liquid_oil", 10.0)),
                highSalt = true,
            ),
            FoodDraft(
                FoodDraft.nextKey(), "清炒西兰花", "一盘", 200.0, 118.0, 6.0, 7.6, 9.4, Meal.Dinner, t, confidence = "高",
                components = listOf(FoodComponent("西兰花", "cruciferous", 190.0), FoodComponent("植物油", "liquid_oil", 10.0)),
            ),
            FoodDraft(
                FoodDraft.nextKey(), "杂粮饭", "一小碗", 120.0, 151.0, 4.1, 1.0, 31.6, Meal.Dinner, t, confidence = "中", addToFavorites = true,
                components = listOf(FoodComponent("杂粮饭", "whole_grains", 120.0)),
            ),
        )
    }

    /** AI 排好的接下来 7 天营养，以及今日页的调整提示 */
    suspend fun nutritionPlan(container: AppContainer) {
        val notes = listOf("训练日，碳水多一点", "休息日，碳水少一点", "练腿日，碳水最多", "休息日", "上肢日", "休息日", "练腿日")
        val kcals = listOf(1950, 1750, 2050, 1750, 1950, 1750, 2050)
        container.database.nutritionDao().upsertAll(
            kcals.mapIndexed { i, k ->
                NutritionDay(today.plusDays(i.toLong()).toEpochDay(), k, 135, 60, (k - 135 * 4 - 60 * 9) / 4, notes[i], NutritionDay.SOURCE_AI, 0)
            },
        )
        container.nutritionPlan.setBaseline(NutritionBaseline(1880, today.minusDays(3).toEpochDay()))
        container.nutritionPlan.setNotice(
            NutritionNotice(1, "昨天多吃了约 300 kcal，分 3 天补回；练腿日碳水多一点", today.toEpochDay(), 2050, 1950),
        )
    }

    /** 助手页的一段对话 */
    fun chat(): List<ChatItem> = listOf(
        ChatItem.User(1, "中午吃了一碗牛肉面，加了个卤蛋"),
        ChatItem.Action(2, 101, "添加饮食：牛肉面、卤蛋（共 625 kcal）"),
        ChatItem.Assistant(3, "记好了。今天还能吃约 980 kcal，蛋白质还差 46 g，晚饭可以来份鸡胸或者鱼。", model = "glm-4-flash"),
        ChatItem.User(4, "今天卧推肩膀有点疼，换个动作"),
        ChatItem.Assistant(5, "肩膀不舒服就别硬推了。可以换成「上斜哑铃卧推」或「器械推胸」，轨迹更可控、对肩膀压力小。换成哪个？", model = "glm-4-flash"),
        ChatItem.User(6, "器械推胸"),
        ChatItem.Pending(7, listOf("把「杠铃卧推」换成「器械推胸」，涉及 3 天：今天、下周一、下下周一")),
    )

    /** AI 用表格和列表回答 */
    fun chatTable(): List<ChatItem> = listOf(
        ChatItem.User(1, "我这周蛋白质吃够了吗？"),
        ChatItem.Assistant(
            2,
            """
            这周 5 天里有 **3 天** 没吃够，平均每天差 **18 g**：

            | 日期 | 蛋白质 | 目标 | 差多少 |
            |:--|--:|--:|--:|
            | 周一 | 132 g | 130 g | ✓ |
            | 周二 | 104 g | 130 g | −26 g |
            | 周三 | 98 g | 130 g | −32 g |
            | 周四 | 135 g | 130 g | ✓ |
            | 周五 | 117 g | 130 g | −13 g |

            ### 怎么补
            1. 早餐加 **2 个鸡蛋**（约 13 g）
            2. 训练后来一杯乳清蛋白（约 24 g）
            3. 晚饭的肉换成鸡胸或鱼，份量多一点

            > 练完 2 小时内吃够蛋白质，恢复会更快。
            """.trimIndent(),
            model = "glm-4-flash",
        ),
    )
}
