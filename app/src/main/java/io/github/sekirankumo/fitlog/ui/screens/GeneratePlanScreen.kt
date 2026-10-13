package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.ui.components.pickDate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

private val GOAL_OPTIONS = listOf("减脂", "增肌", "力量", "塑形", "保持健康")
private val EQUIPMENT_OPTIONS = listOf("健身房", "家里有哑铃", "徒手", "哑铃 + 弹力带")

/** 生成训练计划：填写目标、频率、器材，交给 AI */
@Composable
fun GeneratePlanScreen(container: AppContainer, onDone: () -> Unit) {
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
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("训练目标")
        ChipRow(GOAL_OPTIONS, goal) { goal = it }
        Label("每周训练天数")
        ChipRow((2..6).map { "$it 天" }, "$daysPerWeek 天") { daysPerWeek = it.removeSuffix(" 天").toInt() }
        Label("每次训练时长")
        ChipRow(listOf(30, 45, 60, 90).map { "$it 分钟" }, "$minutes 分钟") { minutes = it.removeSuffix(" 分钟").toInt() }
        Label("计划周期")
        ChipRow(listOf(4, 6, 8, 12).map { "$it 周" }, "$weeks 周") { weeks = it.removeSuffix(" 周").toInt() }
        Label("器材")
        ChipRow(EQUIPMENT_OPTIONS, equipment) { equipment = it }
        OutlinedTextField(
            value = equipment,
            onValueChange = { equipment = it },
            label = { Text("也可以自己描述器材") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("伤病、偏好等（可选）") },
            placeholder = { Text("例如：左膝不好，避免跳跃；想多练臀腿") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        OutlinedButton(onClick = { pickDate(context, LocalDate.ofEpochDay(startEpoch)) { startEpoch = it.toEpochDay() } }) {
            Text("开始日期：${LocalDate.ofEpochDay(startEpoch)}")
        }
        Text(
            "生成后会替换开始日期之后的原计划，已完成的训练会保留。生成之后可以在「调整记录」里撤销。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val args = buildJsonObject {
                        put("goal", goal)
                        put("days_per_week", daysPerWeek)
                        put("weeks", weeks)
                        put("session_minutes", minutes)
                        put("equipment", equipment)
                        put("notes", notes)
                        put("start_date", LocalDate.ofEpochDay(startEpoch).toString())
                    }
                    try {
                        container.operations.runWrite(container.tools["generate_training_plan"]!!, args)
                        // 记住这次的偏好
                        container.profile.update {
                            it.copy(trainingDaysPerWeek = daysPerWeek, sessionMinutes = minutes, equipment = equipment)
                        }
                        onDone()
                    } catch (e: Exception) {
                        error = "生成失败：${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("  AI 正在排计划，大约需要半分钟…")
            } else {
                Text("生成计划")
            }
        }
    }
}

@Composable
private fun Label(text: String) = Text(text, style = MaterialTheme.typography.titleSmall)

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) }, colors = chipColors()) }
    }
}
