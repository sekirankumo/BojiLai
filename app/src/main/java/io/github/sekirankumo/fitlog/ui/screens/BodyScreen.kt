package io.github.sekirankumo.fitlog.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import io.github.sekirankumo.fitlog.ui.components.ChartPoint
import io.github.sekirankumo.fitlog.ui.components.TrendChart
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
fun BodyScreen(dao: BodyRecordDao) {
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCard(records, inRange) }

            item {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric.entries.forEach { m ->
                                FilterChip(
                                    selected = metric == m,
                                    onClick = { metric = m },
                                    label = { Text("${m.label}（${m.unit}）") },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        TrendChart(points)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Range.entries.forEach { r ->
                                FilterChip(
                                    selected = range == r,
                                    onClick = { range = r },
                                    label = { Text(r.label) },
                                )
                            }
                        }
                        Text(
                            "粗线：7 天移动平均　细线：每次测量",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("全部记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { adding = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("记录")
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    Text(
                        "还没有记录。点「记录」手动添加，或者对 AI 说「今天体重 70 公斤」。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(records, key = { it.id }) { r ->
                Column {
                    ListItem(
                        modifier = Modifier.clickable { editing = r },
                        headlineContent = {
                            Text(
                                listOfNotNull(
                                    r.weightKg?.let { "${it.fmt()} kg" },
                                    r.bodyFatPct?.let { "体脂 ${it.fmt()}%" },
                                ).joinToString("　")
                            )
                        },
                        supportingContent = {
                            Text(listOfNotNull(TimeUtil.format(r.measuredAt), r.note).joinToString("　"))
                        },
                        trailingContent = {
                            Text(
                                SOURCE_LABELS[r.source] ?: r.source,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    HorizontalDivider()
                }
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
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

@Composable
private fun SummaryCard(all: List<BodyRecord>, inRange: List<BodyRecord>) {
    val latestWeight = all.firstOrNull { it.weightKg != null }
    val latestFat = all.firstOrNull { it.bodyFatPct != null }
    val firstWeightInRange = inRange.lastOrNull { it.weightKg != null }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("最新体重", style = MaterialTheme.typography.labelMedium)
                Text(
                    latestWeight?.weightKg?.let { "${it.fmt()} kg" } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                )
                val delta = latestWeight?.weightKg?.let { w -> firstWeightInRange?.weightKg?.let { w - it } }
                if (delta != null && firstWeightInRange !== latestWeight) {
                    Text(
                        "区间内 ${if (delta >= 0) "+" else ""}${delta.fmt()} kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text("最新体脂率", style = MaterialTheme.typography.labelMedium)
                Text(
                    latestFat?.bodyFatPct?.let { "${it.fmt()}%" } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                )
                latestWeight?.let {
                    Text(
                        TimeUtil.formatShort(it.measuredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
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
