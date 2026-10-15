package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.NutritionTargets
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.ChoiceChips
import io.github.sekirankumo.fitlog.ui.components.Field
import io.github.sekirankumo.fitlog.ui.components.FigureNumber
import io.github.sekirankumo.fitlog.ui.components.GroupLabel
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.NumberField
import io.github.sekirankumo.fitlog.ui.components.Segmented
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.launch

/** 个人信息：用于计算营养目标、生成训练计划；AI 记住的信息也在这里修改。改资料时营养目标实时重算 */
@Composable
fun ProfileScreen(container: AppContainer, onDone: () -> Unit) {
    val c = Brand.colors
    val toast = LocalToast.current
    var p by remember { mutableStateOf<UserProfile?>(null) }
    val latestFlow = remember { container.database.bodyRecordDao().observeLatest() }
    val records by latestFlow.collectAsState(initial = null)
    LaunchedEffect(Unit) { p = container.profile.current() }
    val scroll = rememberScrollState()
    TrackTopBar(scroll)
    val profile = p ?: return
    val weight = records?.weightKg
    val preview = NutritionTargets.from(profile, weight)

    fun set(transform: (UserProfile) -> UserProfile) {
        p = transform(profile)
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(screenPadding(extraBottom = 32.dp))) {
        GroupLabel("基本信息", first = true)
        BCard {
            Segmented(listOf("male" to "男", "female" to "女"), profile.sex, { v -> set { it.copy(sex = v) } }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(profile.birthYear?.toDouble(), { v -> set { it.copy(birthYear = v?.toInt()) } }, "出生年份", Modifier.weight(1f), integer = true)
                NumberField(profile.heightCm, { v -> set { it.copy(heightCm = v) } }, "身高 cm", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "体重取最新的身体记录：${weight?.let { "%.1f kg".format(it) } ?: "还没有记录"}",
                style = Brand.type.note,
                color = c.muted,
            )
        }

        GroupLabel("日常活动量（不含训练）")
        ChoiceChips(UserProfile.ACTIVITY_LABELS.mapIndexed { i, l -> i to l }, profile.activityLevel, { v -> set { it.copy(activityLevel = v) } })

        GroupLabel("目标")
        Segmented(UserProfile.GOALS.entries.map { it.key to it.value }, profile.goal, { v -> set { it.copy(goal = v) } }, Modifier.fillMaxWidth())

        GroupLabel("每日营养目标")
        BCard {
            Text("热量", style = Brand.type.label, color = c.muted)
            Spacer(Modifier.height(4.dp))
            FigureNumber("${preview.kcal}", "kcal", Brand.type.n1)
            if (!preview.computed && !preview.manual) {
                Spacer(Modifier.height(4.dp))
                Text("还缺${preview.missing.joinToString("、")}，暂用默认值", style = Brand.type.note, color = c.warn)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("蛋白质" to preview.proteinG, "脂肪" to preview.fatG, "碳水" to preview.carbsG).forEach { (label, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = Brand.type.label, color = c.muted)
                        FigureNumber("$v", "g", Brand.type.n3, unitStyle = Brand.type.nSub2)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    profile.kcalTargetOverride?.toDouble(),
                    { v -> set { it.copy(kcalTargetOverride = v?.toInt()?.takeIf { x -> x > 0 }) } },
                    "自定热量",
                    Modifier.weight(1f),
                    integer = true,
                    placeholder = "自动",
                )
                NumberField(
                    profile.proteinTargetOverride?.toDouble(),
                    { v -> set { it.copy(proteinTargetOverride = v?.toInt()?.takeIf { x -> x > 0 }) } },
                    "自定蛋白 g",
                    Modifier.weight(1f),
                    integer = true,
                    placeholder = "自动",
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("留空则按身体数据自动计算", style = Brand.type.note, color = c.muted)
        }

        GroupLabel("训练偏好")
        BCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(profile.trainingDaysPerWeek.toDouble(), { v -> set { it.copy(trainingDaysPerWeek = v?.toInt() ?: it.trainingDaysPerWeek) } }, "每周天数", Modifier.weight(1f), integer = true)
                NumberField(profile.sessionMinutes.toDouble(), { v -> set { it.copy(sessionMinutes = v?.toInt() ?: it.sessionMinutes) } }, "每次分钟", Modifier.weight(1f), integer = true)
            }
            Spacer(Modifier.height(10.dp))
            Field(profile.equipment, { v -> set { it.copy(equipment = v) } }, label = "可用器材")
        }

        GroupLabel("AI 记住的信息")
        Field(
            profile.notes,
            { v -> set { it.copy(notes = v) } },
            placeholder = "伤病、身体限制、饮食习惯、训练偏好……",
            singleLine = false,
            minLines = 3,
        )
        Text(
            "伤病、身体限制、饮食习惯、训练偏好……AI 生成计划和建议时会参考。对 AI 说「记住我…」也会写到这里。",
            style = Brand.type.note,
            color = c.muted,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )

        Spacer(Modifier.height(22.dp))
        BButton("保存", Modifier.fillMaxWidth(), height = 50.dp) {
            val toSave = profile
            container.appScope.launch {
                // 训练提醒在设置页里单独管理，这里不覆盖
                container.profile.update { current -> toSave.copy(reminderMinuteOfDay = current.reminderMinuteOfDay) }
            }
            toast.show("已保存，每日目标 ${preview.kcal} kcal")
            onDone()
        }
    }
}
