package io.github.sekirankumo.fitlog.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.background.WorkScheduler
import io.github.sekirankumo.fitlog.data.NutritionTargets
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.ui.components.NumberField
import kotlinx.coroutines.launch

/** 个人信息：用于计算营养目标、生成训练计划；AI 记住的信息也在这里修改 */
@Composable
fun ProfileScreen(container: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    var p by remember { mutableStateOf<UserProfile?>(null) }
    val latestFlow = remember { container.database.bodyRecordDao().observeLatest() }
    val records by latestFlow.collectAsState(initial = null)
    LaunchedEffect(Unit) { p = container.profile.current() }
    val profile = p ?: return
    val weight = records?.weightKg
    val preview = NutritionTargets.from(profile, weight)

    fun set(transform: (UserProfile) -> UserProfile) {
        p = transform(profile)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("基本信息", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(profile.sex == "male", { set { it.copy(sex = "male") } }, label = { Text("男") }, colors = chipColors())
            FilterChip(profile.sex == "female", { set { it.copy(sex = "female") } }, label = { Text("女") }, colors = chipColors())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(profile.birthYear?.toDouble(), { v -> set { it.copy(birthYear = v?.toInt()) } }, "出生年份", Modifier.weight(1f), integer = true)
            NumberField(profile.heightCm, { v -> set { it.copy(heightCm = v) } }, "身高 cm", Modifier.weight(1f))
        }
        Text(
            "体重取最新的身体记录：${weight?.let { "%.1f kg".format(it) } ?: "还没有记录"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("日常活动量（不含训练）", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UserProfile.ACTIVITY_LABELS.forEachIndexed { i, label ->
                FilterChip(profile.activityLevel == i, { set { it.copy(activityLevel = i) } }, label = { Text(label) }, colors = chipColors())
            }
        }
        Text("目标", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UserProfile.GOALS.forEach { (key, label) ->
                FilterChip(profile.goal == key, { set { it.copy(goal = key) } }, label = { Text(label) }, colors = chipColors())
            }
        }

        io.github.sekirankumo.fitlog.ui.components.BCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("每日营养目标", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${preview.kcal} kcal · 蛋白 ${preview.proteinG}g · 脂肪 ${preview.fatG}g · 碳水 ${preview.carbsG}g" +
                        if (preview.computed) "" else "\n（资料不全，暂用默认值）",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(profile.kcalTargetOverride?.toDouble(), { v -> set { it.copy(kcalTargetOverride = v?.toInt()?.takeIf { x -> x > 0 }) } }, "自定热量", Modifier.weight(1f), integer = true)
                    NumberField(profile.proteinTargetOverride?.toDouble(), { v -> set { it.copy(proteinTargetOverride = v?.toInt()?.takeIf { x -> x > 0 }) } }, "自定蛋白 g", Modifier.weight(1f), integer = true)
                }
                Text("留空则自动计算", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text("训练偏好", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(profile.trainingDaysPerWeek.toDouble(), { v -> set { it.copy(trainingDaysPerWeek = v?.toInt() ?: it.trainingDaysPerWeek) } }, "每周天数", Modifier.weight(1f), integer = true)
            NumberField(profile.sessionMinutes.toDouble(), { v -> set { it.copy(sessionMinutes = v?.toInt() ?: it.sessionMinutes) } }, "每次分钟", Modifier.weight(1f), integer = true)
        }
        OutlinedTextField(
            value = profile.equipment,
            onValueChange = { v -> set { it.copy(equipment = v) } },
            label = { Text("可用器材") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            OutlinedButton(onClick = {
                val m = profile.reminderMinuteOfDay ?: (19 * 60)
                TimePickerDialog(context, { _, h, min -> set { it.copy(reminderMinuteOfDay = h * 60 + min) } }, m / 60, m % 60, true).show()
            }) {
                Text(profile.reminderMinuteOfDay?.let { "训练提醒：%02d:%02d".format(it / 60, it % 60) } ?: "设置训练提醒")
            }
            if (profile.reminderMinuteOfDay != null) {
                TextButton(onClick = { set { it.copy(reminderMinuteOfDay = null) } }) { Text("关闭提醒") }
            }
        }

        Text("AI 记住的信息", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = profile.notes,
            onValueChange = { v -> set { it.copy(notes = v) } },
            placeholder = { Text("伤病、身体限制、饮食习惯、训练偏好……AI 生成计划和建议时会参考。对 AI 说「记住我…」也会写到这里。") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )

        Button(
            onClick = {
                val toSave = profile
                container.appScope.launch {
                    container.profile.update { toSave }
                    WorkScheduler.scheduleReminder(context.applicationContext, toSave.reminderMinuteOfDay)
                }
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存") }
    }
}
