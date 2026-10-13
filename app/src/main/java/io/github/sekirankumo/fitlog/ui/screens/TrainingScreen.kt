package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.plan.PlanExercise
import io.github.sekirankumo.fitlog.data.plan.PlanSet
import io.github.sekirankumo.fitlog.data.plan.fmtKg
import io.github.sekirankumo.fitlog.data.plan.formatDuration
import io.github.sekirankumo.fitlog.data.plan.markDone
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.BarProgress
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.IconBadge
import io.github.sekirankumo.fitlog.ui.components.MonthCalendar
import io.github.sekirankumo.fitlog.ui.components.NumberField
import io.github.sekirankumo.fitlog.ui.components.Pill
import io.github.sekirankumo.fitlog.ui.components.rememberSpeechInput
import io.github.sekirankumo.fitlog.ui.components.statusColor
import io.github.sekirankumo.fitlog.ui.theme.Brand
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!hasPlan) {
            item { EmptyPlan(nav) }
            return@LazyColumn
        }
        item {
            val c = Brand.colors
            BCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.EventAvailable)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(meta?.name ?: "我的训练计划", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        meta?.let {
                            Text(
                                "${LocalDate.ofEpochDay(it.startDate).format(SHORT)} – ${LocalDate.ofEpochDay(it.endDate).format(SHORT)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = c.muted,
                            )
                        }
                    }
                }
                meta?.summary?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftAction(Icons.AutoMirrored.Filled.MenuBook, "动作库", Modifier.weight(1f), nav.openLibrary)
                    SoftAction(Icons.Filled.History, "调整记录", Modifier.weight(1f), nav.openOperations)
                    SoftAction(Icons.Filled.AutoAwesome, "新计划", Modifier.weight(1f), nav.openGenerate)
                }
            }
        }
        item {
            BCard(padding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                MonthCalendar(
                    month = month,
                    days = days.associateBy { it.date },
                    selected = selected,
                    onSelect = { selectedEpoch = it.toEpochDay() },
                    onMonthChange = { monthEpoch = it.atDay(1).toEpochDay() },
                )
            }
        }
        item { DayDetail(container, selected, day, lastWeights, nav) }
    }
}

private val SHORT = DateTimeFormatter.ofPattern("M月d日")

@Composable
private fun SoftAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Brand.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = c.surface2, modifier = modifier) {
        Row(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = c.brand, modifier = Modifier.size(18.dp))
            Text(" $label", style = MaterialTheme.typography.labelLarge, color = c.text)
        }
    }
}

@Composable
private fun EmptyPlan(nav: TrainingNav) {
    BCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        EmptyState(
            Icons.Filled.EventAvailable,
            "还没有训练计划",
            "告诉 AI 你的目标、每周能练几天、有什么器材，它会从动作库里挑动作，排好每天练什么、几组几次。",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = nav.openGenerate, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  AI 生成训练计划")
                }
                OutlinedButton(onClick = nav.openLibrary, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("先看看动作库")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayDetail(
    container: AppContainer,
    date: LocalDate,
    day: PlanDay?,
    lastWeights: Map<String, Double>,
    nav: TrainingNav,
) {
    val c = Brand.colors
    val plans = container.plans
    val scope = rememberCoroutineScope()
    val dateText = date.format(DateTimeFormatter.ofPattern("M月d日 ")) +
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (day == null) {
            BCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.SelfImprovement)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("$dateText · 休息日", style = MaterialTheme.typography.titleMedium)
                        Text("这天没有安排训练", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { nav.askAi("我想在 $date 加一次训练，") }, shape = RoundedCornerShape(14.dp)) {
                    Text("让 AI 在这天安排训练")
                }
            }
            return@Column
        }

        val exercises = plans.exercisesOf(day)
        val doneSets = exercises.sumOf { ex -> ex.sets.count { it.done } }
        val totalSets = exercises.sumOf { it.sets.size }
        BCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dateText, style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Text(day.title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val sc = statusColor(day.status)
                Pill(DayStatus.label(day.status), color = sc, background = sc.copy(alpha = 0.14f))
            }
            day.note?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BarProgress(if (totalSets == 0) 0f else doneSets.toFloat() / totalSets, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text("$doneSets / $totalSets 组", style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!date.isAfter(LocalDate.now())) {
                    Surface(
                        onClick = speech,
                        enabled = !voiceBusy,
                        shape = RoundedCornerShape(14.dp),
                        color = c.brand,
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            if (voiceBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.onBrand)
                            else Icon(Icons.Filled.Mic, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(18.dp))
                            Text(" 语音打卡", style = MaterialTheme.typography.labelLarge, color = c.onBrand)
                        }
                    }
                    if (day.status != DayStatus.DONE) {
                        SoftAction(Icons.Filled.CheckCircle, "全部完成", Modifier.weight(1f)) {
                            container.appScope.launch { plans.completeDay(date) }
                        }
                    }
                }
                SoftAction(Icons.Filled.AutoAwesome, "问 AI", Modifier.weight(1f)) {
                    nav.askAi("$date 的训练（${day.title}）我想调整：")
                }
            }
            voiceError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall)
            }
        }

        Text(
            "点一组打卡，长按修改实际次数和重量",
            style = MaterialTheme.typography.labelMedium,
            color = c.muted,
            modifier = Modifier.padding(start = 4.dp),
        )

        exercises.forEachIndexed { ei, ex ->
            val info = container.exercises[ex.exerciseId]
            val allDone = ex.sets.isNotEmpty() && ex.sets.all { it.done }
            BCard(padding = PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseThumb(info, 60)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ex.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (allDone) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Filled.CheckCircle, contentDescription = "已完成", tint = c.brand, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(
                            listOfNotNull(
                                info?.primary?.joinToString("、"),
                                ex.restSec?.let { "休息 ${formatDuration(it)}" },
                                lastWeights[plans.exerciseKey(ex)]?.let { "上次 ${it.fmtKg()}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (info != null) {
                        Surface(onClick = { nav.openExercise(info.id) }, shape = RoundedCornerShape(10.dp), color = c.surface2) {
                            Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("要领", style = MaterialTheme.typography.labelMedium, color = c.brand)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.brand, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                ex.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(top = 8.dp))
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 10.dp),
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
            containerColor = c.surface,
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
    val c = Brand.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (set.done) c.brand else c.surface2,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (set.done) Icon(Icons.Filled.Check, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(12.dp))
                Text(
                    "第${index + 1}组",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (set.done) c.onBrand.copy(alpha = 0.85f) else c.muted,
                )
            }
            Text(
                set.doneText() ?: set.targetText(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (set.done) c.onBrand else c.text,
            )
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
        containerColor = Brand.colors.surface,
        title = { Text(exercise?.name ?: "修改这一组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目标：${set.targetText()}", style = MaterialTheme.typography.bodySmall, color = Brand.colors.muted)
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
                onSave(set.copy(done = true, doneReps = reps?.toInt(), doneWeightKg = weight, doneDurationSec = duration?.toInt()))
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
