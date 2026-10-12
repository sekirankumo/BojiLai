package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.plan.PlanExercise
import io.github.sekirankumo.fitlog.data.plan.PlanSet
import io.github.sekirankumo.fitlog.data.plan.fmtKg
import io.github.sekirankumo.fitlog.data.plan.formatDuration
import io.github.sekirankumo.fitlog.data.plan.markDone
import io.github.sekirankumo.fitlog.ui.components.MonthCalendar
import io.github.sekirankumo.fitlog.ui.components.NumberField
import io.github.sekirankumo.fitlog.ui.components.rememberSpeechInput
import io.github.sekirankumo.fitlog.ui.components.statusColor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class TrainingNav(
    val openExercise: (String) -> Unit,
    val openLibrary: () -> Unit,
    val openGenerate: () -> Unit,
    val openOperations: () -> Unit,
    val askAi: (String) -> Unit,
)

@Composable
fun TrainingScreen(container: AppContainer, nav: TrainingNav) {
    val plans = container.plans
    var selectedEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    var monthEpoch by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay()) }
    val month = YearMonth.from(LocalDate.ofEpochDay(monthEpoch))

    val rangeFlow = remember(monthEpoch) { plans.observeRange(month.atDay(1).minusDays(7), month.atEndOfMonth().plusDays(7)) }
    val days by rangeFlow.collectAsState(initial = emptyList())
    val hasPlanFlow = remember { plans.observeHasPlan() }
    val hasPlan by hasPlanFlow.collectAsState(initial = true)
    val metaFlow = remember { plans.observeMeta() }
    val meta by metaFlow.collectAsState(initial = null)
    val dayFlow = remember(selectedEpoch) { plans.observeDay(selected) }
    val day by dayFlow.collectAsState(initial = null)
    val lastWeights by produceState(emptyMap<String, Double>(), days) { value = plans.lastWeights() }

    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hasPlan) {
            item { EmptyPlan(nav) }
            return@LazyColumn
        }
        item {
            Column {
                meta?.let {
                    Text(it.name, style = MaterialTheme.typography.titleMedium)
                    if (it.summary.isNotBlank()) {
                        Text(it.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = nav.openLibrary) {
                        Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" 动作库")
                    }
                    TextButton(onClick = nav.openOperations) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" 调整记录")
                    }
                    TextButton(onClick = nav.openGenerate) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" 新计划")
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(8.dp)) {
                    MonthCalendar(
                        month = month,
                        days = days.associateBy { it.date },
                        selected = selected,
                        onSelect = { selectedEpoch = it.toEpochDay() },
                        onMonthChange = { monthEpoch = it.atDay(1).toEpochDay() },
                    )
                }
            }
        }
        item { DayDetail(container, selected, day, lastWeights, nav) }
    }
}

@Composable
private fun EmptyPlan(nav: TrainingNav) {
    Card(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("还没有训练计划", style = MaterialTheme.typography.titleLarge)
            Text(
                "告诉 AI 你的目标、每周能练几天、有什么器材，它会从动作库里挑动作，帮你排好每天练什么、几组几次。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = nav.openGenerate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Text("  AI 生成训练计划")
            }
            OutlinedButton(onClick = nav.openLibrary, modifier = Modifier.fillMaxWidth()) { Text("先看看动作库") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun DayDetail(
    container: AppContainer,
    date: LocalDate,
    day: PlanDay?,
    lastWeights: Map<String, Double>,
    nav: TrainingNav,
) {
    val plans = container.plans
    val scope = rememberCoroutineScope()
    val dateText = date.format(DateTimeFormatter.ofPattern("M月d日")) + " " +
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    var editing by remember { mutableStateOf<Triple<Int, Int, PlanSet>?>(null) }
    var voiceBusy by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var pendingCheckin by remember { mutableStateOf<Pair<JsonObject, String>?>(null) }

    val speech = rememberSpeechInput("说说练了什么，例如：卧推四组都做完了，最后一组只做了 6 个") { said ->
        val d = day ?: return@rememberSpeechInput
        voiceBusy = true
        voiceError = null
        scope.launch {
            try {
                val parsed = container.planAi.parseCheckin(d, said)
                val args = buildJsonObject {
                    put("date", date.toString())
                    parsed["updates"]?.let { put("updates", it) }
                    parsed["mark_day_done"]?.let { put("mark_day_done", it) }
                }
                val tool = container.tools["checkin_training"]!!
                pendingCheckin = args to "你说：$said\n\n" + tool.preview(args)
            } catch (e: Exception) {
                voiceError = "语音打卡失败：${e.message}"
            } finally {
                voiceBusy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (day == null) {
            Text("$dateText · 休息日", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { nav.askAi("我想在 $date 加一次训练，") }) {
                Text("在这天安排训练（问 AI）")
            }
            return@Column
        }
        val exercises = plans.exercisesOf(day)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$dateText · ${day.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = statusColor(day.status).copy(alpha = 0.15f)) {
                Text(
                    DayStatus.label(day.status),
                    color = statusColor(day.status),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        day.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        val doneSets = exercises.sumOf { ex -> ex.sets.count { it.done } }
        val totalSets = exercises.sumOf { it.sets.size }
        Text(
            "已完成 $doneSets / $totalSets 组 · 点一组打卡，长按修改实际次数和重量",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!date.isAfter(LocalDate.now())) {
                FilledTonalButton(onClick = speech, enabled = !voiceBusy) {
                    if (voiceBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" 语音打卡")
                }
                if (day.status != DayStatus.DONE) {
                    FilledTonalButton(onClick = { container.appScope.launch { plans.completeDay(date) } }) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" 全部完成")
                    }
                }
            }
            OutlinedButton(onClick = { nav.askAi("${date} 的训练（${day.title}）我想调整：") }) { Text("问 AI") }
        }
        voiceError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

        exercises.forEachIndexed { ei, ex ->
            val info = container.exercises[ex.exerciseId]
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = info != null) { info?.let { nav.openExercise(it.id) } },
                    ) {
                        ExerciseThumb(info, 52)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(ex.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    info?.primary?.joinToString("、"),
                                    ex.restSec?.let { "休息 ${formatDuration(it)}" },
                                    lastWeights[plans.exerciseKey(ex)]?.let { "上次 ${it.fmtKg()}" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (info != null) Text("要领 ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    ex.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        ex.sets.forEachIndexed { si, s ->
                            SetChip(
                                index = si,
                                set = s,
                                onClick = {
                                    container.appScope.launch {
                                        plans.updateSet(date, ei, si) {
                                            if (it.done) it.copy(done = false, doneReps = null, doneWeightKg = null, doneDurationSec = null)
                                            else it.markDone(lastWeights[plans.exerciseKey(ex)])
                                        }
                                    }
                                },
                                onLongClick = { editing = Triple(ei, si, s) },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { (ei, si, s) ->
        SetEditDialog(
            set = s,
            exercise = day?.let { plans.exercisesOf(it).getOrNull(ei) },
            onDismiss = { editing = null },
            onSave = { updated ->
                container.appScope.launch { plans.updateSet(date, ei, si) { updated } }
                editing = null
            },
        )
    }

    pendingCheckin?.let { (args, text) ->
        AlertDialog(
            onDismissRequest = { pendingCheckin = null },
            title = { Text("确认打卡") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    val tool = container.tools["checkin_training"]!!
                    container.appScope.launch { runCatching { container.operations.runWrite(tool, args) } }
                    pendingCheckin = null
                }) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { pendingCheckin = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetChip(index: Int, set: PlanSet, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (set.done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "第${index + 1}组" + if (set.done) " ✓" else "",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(set.doneText() ?: set.targetText(), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SetEditDialog(set: PlanSet, exercise: PlanExercise?, onDismiss: () -> Unit, onSave: (PlanSet) -> Unit) {
    val timed = set.durationSec != null
    var reps by remember { mutableStateOf((set.doneReps ?: set.reps)?.toDouble()) }
    var weight by remember { mutableStateOf(set.doneWeightKg ?: set.weightKg) }
    var duration by remember { mutableStateOf((set.doneDurationSec ?: set.durationSec)?.toDouble()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise?.name ?: "修改这一组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标：${set.targetText()}", style = MaterialTheme.typography.bodySmall)
                if (timed) {
                    NumberField(duration, { duration = it }, "实际秒数", integer = true)
                } else {
                    NumberField(reps, { reps = it }, "实际次数", integer = true)
                    NumberField(weight, { weight = it }, "重量 kg")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    set.copy(
                        done = true,
                        doneReps = reps?.toInt(),
                        doneWeightKg = weight,
                        doneDurationSec = duration?.toInt(),
                    )
                )
            }) { Text("完成这一组") }
        },
        dismissButton = {
            Row {
                if (set.done) {
                    TextButton(onClick = {
                        onSave(set.copy(done = false, doneReps = null, doneWeightKg = null, doneDurationSec = null))
                    }) { Text("取消打卡") }
                }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

