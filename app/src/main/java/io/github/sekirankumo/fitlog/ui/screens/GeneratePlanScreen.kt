package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.background.PlanGenerateWorker
import io.github.sekirankumo.fitlog.ui.components.AiOrb
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.ChoiceChips
import io.github.sekirankumo.fitlog.ui.components.Field
import io.github.sekirankumo.fitlog.ui.components.GroupLabel
import io.github.sekirankumo.fitlog.ui.components.NumberChoice
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.pickDate
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val GOAL_OPTIONS = listOf("减脂", "增肌", "力量", "塑形", "保持健康")
private val EQUIPMENT_OPTIONS = listOf("健身房", "家里有哑铃", "徒手", "哑铃 + 弹力带")
private val START_FORMAT = DateTimeFormatter.ofPattern("M 月 d 日")

/** 生成训练计划：填写目标、频率、器材，交给 AI */
@Composable
fun GeneratePlanScreen(container: AppContainer, onDone: () -> Unit) {
    val c = Brand.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var goal by rememberSaveable { mutableStateOf("减脂") }
    var daysPerWeek by rememberSaveable { mutableStateOf(3) }
    var weeks by rememberSaveable { mutableStateOf(4) }
    var minutes by rememberSaveable { mutableStateOf(60) }
    var equipment by rememberSaveable { mutableStateOf("健身房") }
    var notes by rememberSaveable { mutableStateOf("") }
    var startEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    TrackTopBar(scroll)

    // 生成在后台任务里进行：离开页面或切出 App 也会继续，完成后发通知
    val wm = remember { WorkManager.getInstance(context) }
    var workId by rememberSaveable { mutableStateOf<String?>(null) }
    val workFlow = remember { wm.getWorkInfosForUniqueWorkFlow(PlanGenerateWorker.UNIQUE) }
    val infos by workFlow.collectAsState(initial = emptyList())
    val current = infos.firstOrNull()
    val busy = current?.state == WorkInfo.State.RUNNING || current?.state == WorkInfo.State.ENQUEUED
    val progressChars by container.planAi.progress.collectAsState()

    LaunchedEffect(current?.id, current?.state) {
        val info = current ?: return@LaunchedEffect
        // 只处理这个页面发起的那次生成
        if (info.id.toString() != workId) return@LaunchedEffect
        when (info.state) {
            WorkInfo.State.SUCCEEDED -> {
                workId = null
                onDone()
            }
            WorkInfo.State.FAILED -> {
                workId = null
                error = "生成失败：${info.outputData.getString(PlanGenerateWorker.KEY_ERROR) ?: "未知错误"}"
            }
            WorkInfo.State.CANCELLED -> workId = null
            else -> Unit
        }
    }

    // 用个人信息里的偏好预填
    LaunchedEffect(Unit) {
        if (!loaded) {
            val p = container.profile.current()
            goal = when (p.goal) { "gain" -> "增肌"; "maintain" -> "保持健康"; else -> "减脂" }
            daysPerWeek = p.trainingDaysPerWeek
            minutes = p.sessionMinutes
            equipment = p.equipment
            notes = p.notes
            loaded = true
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(screenPadding(extraBottom = 32.dp))) {
        GroupLabel("训练目标", first = true)
        ChoiceChips(GOAL_OPTIONS.map { it to it }, goal, { goal = it })

        GroupLabel("每周训练天数")
        NumberChoice((2..6).toList(), "天", daysPerWeek, { daysPerWeek = it })

        GroupLabel("每次训练时长")
        NumberChoice(listOf(30, 45, 60, 90), "分钟", minutes, { minutes = it })

        GroupLabel("计划周期")
        NumberChoice(listOf(4, 6, 8, 12), "周", weeks, { weeks = it })

        GroupLabel("器材")
        ChoiceChips(EQUIPMENT_OPTIONS.map { it to it }, equipment.takeIf { it in EQUIPMENT_OPTIONS }, { equipment = it })
        Spacer(Modifier.height(10.dp))
        Field(equipment, { equipment = it }, placeholder = "也可以自己描述器材")

        GroupLabel("伤病、偏好（可选）")
        Field(notes, { notes = it }, placeholder = "例如：左膝不好，避免跳跃；想多练臀腿", singleLine = false, minLines = 2)

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surface)
                .clickable { pickDate(context, LocalDate.ofEpochDay(startEpoch)) { startEpoch = it.toEpochDay() } }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("开始日期", style = Brand.type.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
            val start = LocalDate.ofEpochDay(startEpoch)
            Text(
                if (start == LocalDate.now()) "今天" else start.format(START_FORMAT),
                style = Brand.type.label,
                color = c.muted,
            )
            Icon(AppIcons.Chevron, contentDescription = null, tint = c.faint, modifier = Modifier.padding(start = 4.dp).size(18.dp))
        }
        Text(
            "生成后会替换开始日期之后的原计划，已完成的训练会保留。生成之后可以在「调整记录」里撤销。",
            style = Brand.type.note,
            color = c.muted,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Brand.type.label, color = c.danger, modifier = Modifier.padding(start = 4.dp))
        }

        AnimatedVisibility(busy) {
            BCard(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AiOrb(40.dp, busy = true, shadow = false)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "AI 正在排计划" + (progressChars?.takeIf { it > 0 }?.let { " · 已生成 $it 字" } ?: "…"),
                            style = Brand.type.bodyMedium,
                            color = c.text,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "一般需要半分钟到两分钟。可以先去做别的，切出 App 也会继续生成，好了会发通知。",
                            style = Brand.type.note,
                            color = c.muted,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        BButton(
            if (busy) "生成中…" else "生成计划",
            Modifier.fillMaxWidth(),
            height = 50.dp,
            enabled = !busy,
        ) {
            error = null
            val args = buildJsonObject {
                put("goal", goal)
                put("days_per_week", daysPerWeek)
                put("weeks", weeks)
                put("session_minutes", minutes)
                put("equipment", equipment)
                put("notes", notes)
                put("start_date", LocalDate.ofEpochDay(startEpoch).toString())
            }
            // 记住这次的偏好
            scope.launch {
                container.profile.update {
                    it.copy(trainingDaysPerWeek = daysPerWeek, sessionMinutes = minutes, equipment = equipment)
                }
            }
            workId = PlanGenerateWorker.enqueue(context.applicationContext, args.toString()).toString()
        }
    }
}
