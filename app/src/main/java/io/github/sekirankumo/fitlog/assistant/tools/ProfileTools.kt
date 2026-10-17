package io.github.sekirankumo.fitlog.assistant.tools

import io.github.sekirankumo.fitlog.assistant.AiTool
import io.github.sekirankumo.fitlog.assistant.ToolException
import io.github.sekirankumo.fitlog.assistant.ToolOutcome
import io.github.sekirankumo.fitlog.assistant.integerProp
import io.github.sekirankumo.fitlog.assistant.long
import io.github.sekirankumo.fitlog.assistant.num
import io.github.sekirankumo.fitlog.assistant.numberProp
import io.github.sekirankumo.fitlog.assistant.objectSchema
import io.github.sekirankumo.fitlog.assistant.requireStr
import io.github.sekirankumo.fitlog.assistant.str
import io.github.sekirankumo.fitlog.assistant.stringProp
import io.github.sekirankumo.fitlog.data.ProfileRepository
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val profileJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun UserProfile.toUndo() = buildJsonObject { put("profile", profileJson.encodeToString(UserProfile.serializer(), this@toUndo)) }

private suspend fun ProfileRepository.restore(undoData: JsonObject) {
    val old = undoData.str("profile")?.let { profileJson.decodeFromString(UserProfile.serializer(), it) } ?: return
    update { old }
}

class GetProfileTool(private val profile: ProfileRepository, private val bodyDao: BodyRecordDao) : AiTool {
    override val name = "get_user_profile"
    override val description = "查看用户的个人信息、每日营养目标和 AI 记住的注意事项"
    override val parameters = objectSchema()
    override val isWrite = false

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val p = profile.current()
        val t = profile.currentTargets()
        return ToolOutcome(
            p.describe(bodyDao.latestWeight()?.weightKg) +
                "\n默认每日目标：${t.kcal}kcal 蛋白${t.proteinG}g 脂肪${t.fatG}g 碳水${t.carbsG}g（某天单独的安排用 get_nutrition_plan 查）" +
                if (t.computed) "" else "（还缺${t.missing.joinToString("、")}，部分使用默认值）"
        )
    }
}

class UpdateProfileTool(private val profile: ProfileRepository) : AiTool {
    override val name = "update_user_profile"
    override val description = "修改用户的个人信息或营养目标，只传需要修改的字段"
    override val parameters = objectSchema(
        emptyList(),
        "sex" to stringProp("性别", listOf("male", "female")),
        "birth_year" to integerProp("出生年份"),
        "height_cm" to numberProp("身高 cm"),
        "activity_level" to integerProp("日常活动量 0 久坐 / 1 轻度 / 2 中度 / 3 高"),
        "goal" to stringProp("目标", UserProfile.GOALS.keys.toList()),
        "kcal_target" to integerProp("手动指定每日热量目标，传 0 恢复自动计算"),
        "protein_target" to integerProp("手动指定每日蛋白质目标 g，传 0 恢复自动计算"),
        "training_days_per_week" to integerProp("每周训练天数"),
        "session_minutes" to integerProp("每次训练分钟数"),
        "equipment" to stringProp("可用器材"),
    )
    override val isWrite = true

    private fun merged(p: UserProfile, a: JsonObject) = p.copy(
        sex = a.str("sex") ?: p.sex,
        birthYear = a.long("birth_year")?.toInt() ?: p.birthYear,
        heightCm = a.num("height_cm") ?: p.heightCm,
        activityLevel = a.long("activity_level")?.toInt()?.coerceIn(0, 3) ?: p.activityLevel,
        goal = a.str("goal")?.takeIf { it in UserProfile.GOALS } ?: p.goal,
        kcalTargetOverride = a.long("kcal_target")?.toInt()?.let { if (it <= 0) null else it } ?: p.kcalTargetOverride,
        proteinTargetOverride = a.long("protein_target")?.toInt()?.let { if (it <= 0) null else it } ?: p.proteinTargetOverride,
        trainingDaysPerWeek = a.long("training_days_per_week")?.toInt() ?: p.trainingDaysPerWeek,
        sessionMinutes = a.long("session_minutes")?.toInt() ?: p.sessionMinutes,
        equipment = a.str("equipment") ?: p.equipment,
    )

    override suspend fun preview(args: JsonObject): String {
        val changes = args.keys.joinToString("、") { "$it=${args[it]}" }
        if (changes.isBlank()) throw ToolException("没有要修改的字段")
        return "修改个人信息：$changes"
    }

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val old = profile.current()
        // kcal_target 传 0 表示恢复自动计算，单独处理
        var new = merged(old, args)
        if (args.long("kcal_target") == 0L) new = new.copy(kcalTargetOverride = null)
        if (args.long("protein_target") == 0L) new = new.copy(proteinTargetOverride = null)
        profile.update { new }
        return ToolOutcome("已更新", "修改个人信息", old.toUndo())
    }

    override suspend fun undo(undoData: JsonObject) = profile.restore(undoData)
}

class RememberTool(private val profile: ProfileRepository) : AiTool {
    override val name = "remember_user_info"
    override val description = "记住关于用户的长期信息（伤病、身体限制、饮食习惯、训练偏好等），以后生成计划和建议时会参考"
    override val parameters = objectSchema(
        listOf("text"),
        "text" to stringProp("要记住的内容，简洁的一句话"),
        "mode" to stringProp("append 追加（默认）或 replace 替换全部", listOf("append", "replace")),
    )
    override val isWrite = true

    override suspend fun preview(args: JsonObject) =
        (if (args.str("mode") == "replace") "把 AI 记住的信息替换为：" else "记住：") + args.requireStr("text")

    override suspend fun execute(args: JsonObject): ToolOutcome {
        val old = profile.current()
        val text = args.requireStr("text")
        val notes = if (args.str("mode") == "replace" || old.notes.isBlank()) text else old.notes + "\n" + text
        profile.update { it.copy(notes = notes) }
        return ToolOutcome("已记住", "记住：$text", old.toUndo())
    }

    override suspend fun undo(undoData: JsonObject) = profile.restore(undoData)
}
