package io.github.sekirankumo.fitlog

import io.github.sekirankumo.fitlog.assistant.ChatItem
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.FavoriteFood
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
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

        // 今天的饮食
        fun food(meal: Meal, h: Int, m: Int, name: String, amount: String, g: Double, kcal: Double, p: Double, f: Double, c: Double) =
            FoodEntry(eatenAt = at(today, h, m), meal = meal.key, name = name, amount = amount, grams = g, kcal = kcal, proteinG = p, fatG = f, carbsG = c, source = "ai")
        db.foodDao().insertAll(
            listOf(
                food(Meal.Breakfast, 7, 50, "全麦面包", "2 片", 70.0, 172.0, 8.6, 2.4, 29.0),
                food(Meal.Breakfast, 7, 50, "水煮蛋", "2 个", 100.0, 144.0, 12.6, 9.6, 1.2),
                food(Meal.Breakfast, 7, 50, "无糖豆浆", "1 杯", 300.0, 93.0, 8.7, 4.2, 4.5),
                food(Meal.Lunch, 12, 20, "黄焖鸡", "一份", 250.0, 420.0, 32.0, 24.0, 18.0),
                food(Meal.Lunch, 12, 20, "米饭", "半碗", 150.0, 174.0, 3.9, 0.5, 38.9),
                food(Meal.Lunch, 12, 20, "凉拌黄瓜", "一小盘", 100.0, 48.0, 0.9, 3.2, 3.9),
                food(Meal.Snack, 16, 0, "希腊酸奶", "1 杯", 150.0, 146.0, 13.5, 6.0, 9.0),
            )
        )
        listOf(
            FavoriteFood(name = "鸡胸肉沙拉", amount = "一份", grams = 300.0, kcal = 320.0, proteinG = 35.0, fatG = 12.0, carbsG = 15.0, useCount = 8),
            FavoriteFood(name = "燕麦牛奶", amount = "一碗", grams = 300.0, kcal = 280.0, proteinG = 12.0, fatG = 8.0, carbsG = 38.0, useCount = 6),
            FavoriteFood(name = "蛋白粉", amount = "1 勺", grams = 30.0, kcal = 120.0, proteinG = 24.0, fatG = 1.5, carbsG = 3.0, useCount = 5),
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
            FoodDraft(FoodDraft.nextKey(), "番茄牛腩", "一份", 280.0, 392.0, 30.8, 22.4, 16.8, Meal.Dinner, t, confidence = "中"),
            FoodDraft(FoodDraft.nextKey(), "清炒西兰花", "一盘", 200.0, 118.0, 6.0, 7.6, 9.4, Meal.Dinner, t, confidence = "高"),
            FoodDraft(FoodDraft.nextKey(), "杂粮饭", "一小碗", 120.0, 151.0, 4.1, 1.0, 31.6, Meal.Dinner, t, confidence = "中", addToFavorites = true),
        )
    }

    /** 助手页的一段对话 */
    fun chat(): List<ChatItem> = listOf(
        ChatItem.User(1, "中午吃了一碗牛肉面，加了个卤蛋"),
        ChatItem.Action(2, 101, "添加饮食：牛肉面、卤蛋（共 625 kcal）"),
        ChatItem.Assistant(3, "记好了。今天还能吃约 980 kcal，蛋白质还差 46 g，晚饭可以来份鸡胸或者鱼。"),
        ChatItem.User(4, "今天卧推肩膀有点疼，换个动作"),
        ChatItem.Assistant(5, "肩膀不舒服就别硬推了。可以换成「上斜哑铃卧推」或「器械推胸」，轨迹更可控、对肩膀压力小。换成哪个？"),
        ChatItem.User(6, "器械推胸"),
        ChatItem.Pending(7, listOf("把「杠铃卧推」换成「器械推胸」，涉及 3 天：今天、下周一、下下周一")),
    )
}
