package io.github.sekirankumo.fitlog.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.ChartPoint
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.Pill
import io.github.sekirankumo.fitlog.ui.components.SectionHeader
import io.github.sekirankumo.fitlog.ui.components.StatTile
import io.github.sekirankumo.fitlog.ui.components.TrendChart
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch

private enum class Metric(val label: String, val unit: String) {
    Weight("体重", "kg"),
    BodyFat("体脂率", "%"),
}

private enum class Range(val label: String, val days: Long?) {
    Month("1 个月", 30),
    Quarter("3 个月", 90),
    Year("1 年", 365),
    All("全部", null),
}

private fun BodyRecord.value(metric: Metric) = when (metric) {
    Metric.Weight -> weightKg
    Metric.BodyFat -> bodyFatPct
}

private val SOURCE_LABELS = mapOf(
    "manual" to "手动",
    "ai" to "AI",
    "health_connect" to "小米同步",
    "scale" to "体重秤",
)


@Composable
fun BodyScreen(container: AppContainer) {
    val c = Brand.colors
    val dao = container.database.bodyRecordDao()
    val recordsFlow = remember(dao) { dao.observeAll() }
    val records by recordsFlow.collectAsState(initial = emptyList())
    var metric by remember { mutableStateOf(Metric.Weight) }
    var range by remember { mutableStateOf(Range.Month) }
    var editing by remember { mutableStateOf<BodyRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val inRange = range.days?.let { d -> records.filter { it.measuredAt >= TimeUtil.daysAgo(d) } } ?: records
    val points = inRange.mapNotNull { r -> r.value(metric)?.let { ChartPoint(r.measuredAt, it) } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Summary(container, records, inRange, range) }

            item {
                BCard {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Metric.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = metric == m,
                                onClick = { metric = m },
                                shape = SegmentedButtonDefaults.itemShape(i, Metric.entries.size),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = c.brand,
                                    activeContentColor = c.onBrand,
                                    inactiveContainerColor = c.surface,
                                    activeBorderColor = c.brand,
                                    inactiveBorderColor = c.line,
                                ),
                                icon = {},
                            ) { Text("${m.label}（${m.unit}）") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    TrendChart(points)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Range.entries.forEach { r ->
                            FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) }, colors = chipColors())
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "粗线是 7 天移动平均，比每天的数字更能反映真实趋势",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.muted,
                    )
                }
            }

            item {
                SectionHeader("全部记录") {
                    Surface(onClick = { adding = true }, shape = RoundedCornerShape(12.dp), color = c.brand) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = c.onBrand, modifier = Modifier.padding(end = 2.dp))
                            Text("记录", style = MaterialTheme.typography.labelLarge, color = c.onBrand)
                        }
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.MonitorWeight,
                        "还没有身体数据",
                        "点「记录」手动添加，对 AI 说「今天体重 70 公斤」，或者在设置里打开小米运动健康同步。",
                    )
                }
            } else {
                item {
                    BCard(padding = PaddingValues(vertical = 4.dp)) {
                        records.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = c.line)
                            Row(
                                Modifier.fillMaxWidth().clickable { editing = r }.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(r.weightKg?.let { "${it.fmt()} kg" } ?: "—", style = MaterialTheme.typography.titleMedium)
                                        r.bodyFatPct?.let {
                                            Text("  体脂 ${it.fmt()}%", style = MaterialTheme.typography.bodyMedium, color = c.muted)
                                        }
                                    }
                                    Text(
                                        listOfNotNull(TimeUtil.format(r.measuredAt), r.note).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.muted,
                                    )
                                }
                                Pill(SOURCE_LABELS[r.source] ?: r.source, color = c.muted, background = c.surface2)
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    if (adding || editing != null) {
        BodyRecordDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { record ->
                scope.launch { if (record.id == 0L) dao.insert(record) else dao.update(record) }
                adding = false
                editing = null
            },
            onDelete = { record ->
                editing = null
                scope.launch {
                    dao.delete(record)
                    val result = snackbar.showSnackbar("已删除记录", actionLabel = "撤销", withDismissAction = true)
                    if (result == SnackbarResult.ActionPerformed) dao.insert(record)
                }
            },
        )
    }
}

/** 顶部的身体状态卡片（和今日页同一个组件），体重体脂的变化按所选时间范围计算 */
@Composable
private fun Summary(container: AppContainer, all: List<BodyRecord>, inRange: List<BodyRecord>, range: Range) {
    val training = io.github.sekirankumo.fitlog.ui.components.rememberTrainingData(container)
    val profile by container.profile.profile.collectAsState(initial = io.github.sekirankumo.fitlog.data.UserProfile())
    val latestWeight = all.firstOrNull { it.weightKg != null }
    val latestFat = all.firstOrNull { it.bodyFatPct != null }
    val firstWeightInRange = inRange.lastOrNull { it.weightKg != null }
    val firstFatInRange = inRange.lastOrNull { it.bodyFatPct != null }
    val wDelta = latestWeight?.weightKg?.let { w -> firstWeightInRange?.takeIf { it !== latestWeight }?.weightKg?.let { w - it } }
    val fDelta = latestFat?.bodyFatPct?.let { f -> firstFatInRange?.takeIf { it !== latestFat }?.bodyFatPct?.let { f - it } }
    fun deltaText(d: Double?) = d?.let { "${range.label}内 ${if (it >= 0) "+" else ""}${it.fmt()}" }
    io.github.sekirankumo.fitlog.ui.components.BodyStatusCard(
        intensities = training.intensities,
        female = profile.sex == "female",
        weight = latestWeight?.weightKg?.fmt() ?: "—",
        weightSub = deltaText(wDelta) ?: latestWeight?.let { TimeUtil.formatShort(it.measuredAt) } ?: "还没有记录",
        weightSubGood = wDelta != null && wDelta < 0,
        fat = latestFat?.bodyFatPct?.fmt() ?: "—",
        fatSub = deltaText(fDelta) ?: latestFat?.let { TimeUtil.formatShort(it.measuredAt) },
        fatSubGood = fDelta != null && fDelta < 0,
    )
}

@Composable
private fun BodyRecordDialog(
    initial: BodyRecord?,
    onDismiss: () -> Unit,
    onSave: (BodyRecord) -> Unit,
    onDelete: (BodyRecord) -> Unit,
) {
    val context = LocalContext.current
    var weight by remember { mutableStateOf(initial?.weightKg?.fmt() ?: "") }
    var fat by remember { mutableStateOf(initial?.bodyFatPct?.fmt() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var time by remember { mutableLongStateOf(initial?.measuredAt ?: System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickDateTime() {
        val local = TimeUtil.toLocal(time)
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                time = TimeUtil.toEpoch(local.withYear(y).withMonth(m + 1).withDayOfMonth(d).withHour(h).withMinute(min))
            }, local.hour, local.minute, true).show()
        }, local.year, local.monthValue - 1, local.dayOfMonth).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加记录" else "编辑记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("体重（kg）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it },
                    label = { Text("体脂率（%）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    singleLine = true,
                )
                OutlinedButton(onClick = { pickDateTime() }) { Text("时间：${TimeUtil.format(time)}") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = weight.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                val f = fat.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                error = when {
                    weight.isNotBlank() && w == null -> "体重格式不对"
                    fat.isNotBlank() && f == null -> "体脂率格式不对"
                    w == null && f == null -> "体重和体脂率至少填一个"
                    w != null && w !in 20.0..300.0 -> "体重不在合理范围"
                    f != null && f !in 2.0..70.0 -> "体脂率不在合理范围"
                    else -> null
                }
                if (error == null) {
                    onSave(
                        (initial ?: BodyRecord(measuredAt = time, weightKg = null, bodyFatPct = null)).copy(
                            measuredAt = time,
                            weightKg = w,
                            bodyFatPct = f,
                            note = note.trim().ifEmpty { null },
                        )
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
