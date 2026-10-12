package io.github.sekirankumo.fitlog.data

import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.db.KeyValueDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.math.roundToInt

@Serializable
data class UserProfile(
    /** male / female */
    val sex: String? = null,
    val birthYear: Int? = null,
    val heightCm: Double? = null,
    /** 日常活动量 0 久坐 / 1 轻度 / 2 中度 / 3 高 */
    val activityLevel: Int = 1,
    /** lose 减脂 / maintain 保持 / gain 增肌 */
    val goal: String = "lose",
    /** 手动指定的每日热量目标，为空则自动计算 */
    val kcalTargetOverride: Int? = null,
    val proteinTargetOverride: Int? = null,
    val trainingDaysPerWeek: Int = 3,
    val sessionMinutes: Int = 60,
    /** 可用器材，例如「健身房」「家里有哑铃」 */
    val equipment: String = "健身房",
    /** AI 记住的信息：伤病、偏好、饮食习惯等 */
    val notes: String = "",
    /** 训练提醒时间（分钟数，从 0 点算起），为空则不提醒 */
    val reminderMinuteOfDay: Int? = null,
) {
    val goalLabel: String get() = GOALS[goal] ?: goal

    fun age(): Int? = birthYear?.let { LocalDate.now().year - it }

    /** 给 AI 的个人信息描述 */
    fun describe(latestWeightKg: Double?): String = buildString {
        append("性别：${when (sex) { "male" -> "男"; "female" -> "女"; else -> "未填" }}；")
        append("年龄：${age() ?: "未填"}；")
        append("身高：${heightCm?.let { "${it.roundToInt()}cm" } ?: "未填"}；")
        append("最新体重：${latestWeightKg?.let { "%.1fkg".format(it) } ?: "未知"}；")
        append("目标：$goalLabel；日常活动量：${ACTIVITY_LABELS.getOrElse(activityLevel) { "轻度" }}；")
        append("每周训练 $trainingDaysPerWeek 天，每次约 $sessionMinutes 分钟；器材：$equipment")
        if (notes.isNotBlank()) append("\n需要注意：$notes")
    }

    companion object {
        val GOALS = linkedMapOf("lose" to "减脂", "maintain" to "保持", "gain" to "增肌")
        val ACTIVITY_LABELS = listOf("久坐", "轻度活动", "中度活动", "高强度活动")
        private val ACTIVITY_FACTORS = listOf(1.2, 1.375, 1.55, 1.725)

        fun activityFactor(level: Int) = ACTIVITY_FACTORS.getOrElse(level) { 1.375 }
    }
}

/** 每日营养目标 */
data class NutritionTargets(
    val kcal: Int,
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
    /** 是否根据身体数据自动算出（false 表示资料不全，用的是默认值） */
    val computed: Boolean,
) {
    companion object {
        /** Mifflin-St Jeor 估算基础代谢，乘活动系数，再按目标增减 */
        fun from(profile: UserProfile, weightKg: Double?): NutritionTargets {
            val age = profile.age()
            val height = profile.heightCm
            val computed = weightKg != null && age != null && height != null && profile.sex != null
            val tdee = if (computed) {
                val bmr = 10 * weightKg!! + 6.25 * height!! - 5 * age!! + if (profile.sex == "male") 5 else -161
                bmr * UserProfile.activityFactor(profile.activityLevel)
            } else {
                2000.0
            }
            val kcal = profile.kcalTargetOverride ?: when (profile.goal) {
                "lose" -> tdee - 400
                "gain" -> tdee + 250
                else -> tdee
            }.roundToInt().coerceAtLeast(1200)
            val protein = profile.proteinTargetOverride
                ?: ((weightKg ?: 65.0) * if (profile.goal == "maintain") 1.4 else 1.8).roundToInt()
            val fat = (kcal * 0.27 / 9).roundToInt()
            val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
            return NutritionTargets(kcal, protein, fat, carbs, computed)
        }
    }
}

class ProfileRepository(
    private val kv: KeyValueDao,
    private val bodyDao: BodyRecordDao,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(text: String?): UserProfile =
        text?.let { runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull() } ?: UserProfile()

    val profile: Flow<UserProfile> = kv.observe(KEY).map(::decode)

    val targets: Flow<NutritionTargets> = combine(profile, bodyDao.observeAll()) { p, records ->
        NutritionTargets.from(p, records.firstOrNull { it.weightKg != null }?.weightKg)
    }

    suspend fun current(): UserProfile = decode(kv.get(KEY))

    suspend fun currentTargets(): NutritionTargets =
        NutritionTargets.from(current(), bodyDao.latestWeight()?.weightKg)

    suspend fun update(transform: (UserProfile) -> UserProfile) {
        kv.put(KeyValue(KEY, json.encodeToString(UserProfile.serializer(), transform(current()))))
    }

    private companion object {
        const val KEY = "profile"
    }
}
