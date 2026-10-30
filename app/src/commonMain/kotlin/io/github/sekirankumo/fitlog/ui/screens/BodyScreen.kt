package io.github.sekirankumo.fitlog.ui.screens

import io.github.sekirankumo.fitlog.util.*
import io.github.sekirankumo.fitlog.platform.rememberPickers
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import io.github.sekirankumo.fitlog.ui.icons.outlined.MonitorWeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.BSheet
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.CardHeader
import io.github.sekirankumo.fitlog.ui.components.ChartPoint
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.Field
import io.github.sekirankumo.fitlog.ui.components.FigureNumber
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.MuscleFigures
import io.github.sekirankumo.fitlog.ui.components.Motion
import io.github.sekirankumo.fitlog.ui.components.RecoveringChips
import io.github.sekirankumo.fitlog.ui.components.Segmented
import io.github.sekirankumo.fitlog.ui.components.Tag
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.TrendChart
import io.github.sekirankumo.fitlog.ui.components.rememberTrainingData
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class Metric(val label: String, val unit: String) {
    Weight("体重", "kg"),
    BodyFat("体脂", "%"),
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
    "health_connect" to "健康数据同步",
    "apple_health" to "健康数据同步",
    "scale" to "体重秤",
)

/**
 * 身体：体重是最大的数字（和所选时间范围比的变化），旁边是体脂率；
 * 趋势图（细线是每次测量，粗线是 7 天平均）、肌肉恢复图、全部记录（点一条修改，删除可撤销）。
 */
@Composable
fun BodyScreen(container: AppContainer) {
    val c = Brand.colors
    val dao = container.database.bodyRecordDao()
    val recordsFlow = remember(dao) { dao.observeAll() }
    val records by recordsFlow.collectAsState(initial = emptyList())
    val profile by container.profile.profile.collectAsState(initial = UserProfile())
    val training = rememberTrainingData(container)
    var metric by remember { mutableStateOf(Metric.Weight) }
    var range by remember { mutableStateOf(Range.Month) }
    var editing by remember { mutableStateOf<BodyRecord?>(null) }
    var adding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current

    val inRange = range.days?.let { d -> records.filter { it.measuredAt >= TimeUtil.daysAgo(d) } } ?: records
    val points = inRange.mapNotNull { r -> r.value(metric)?.let { ChartPoint(r.measuredAt, it) } }
    val list = rememberLazyListState()
    TrackTopBar(list)

    LazyColumn(state = list, contentPadding = screenPadding()) {
        item(key = "hero") { Summary(records, inRange, range, Modifier.padding(top = 8.dp)) }
        item(key = "trend") {
            BCard(Modifier.padding(top = 24.dp)) {
                CardHeader("趋势") {
                    Segmented(Metric.entries.map { it to it.label }, metric, { metric = it }, Modifier.width(132.dp))
                }
                Spacer(Modifier.height(12.dp))
                TrendChart(points)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Range.entries.forEach { r ->
                        val on = r == range
                        Text(
                            r.label,
                            style = if (on) Brand.type.labelBold else Brand.type.label,
                            color = if (on) c.text else c.muted,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (on) c.surface2 else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { range = r }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("粗线是 7 天平均，比每天的数字更能看出真实走向", style = Brand.type.note, color = c.muted)
            }
        }
        item(key = "recovery") {
            BCard(Modifier.padding(top = 12.dp)) {
                CardHeader("肌肉恢复")
                Spacer(Modifier.height(6.dp))
                MuscleFigures(training.muscles, profile.sex == "female", 240.dp, Modifier.padding(horizontal = 28.dp), gap = 16.dp)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                Spacer(Modifier.height(12.dp))
                RecoveringChips(training.muscles)
            }
        }
        item(key = "records") {
            BCard(Modifier.padding(top = 12.dp).animateContentSize(tween(Motion.LONG, easing = Motion.Emphasized))) {
                CardHeader("记录") {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c.surface2).clickable { adding = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Plus, contentDescription = "添加记录", tint = c.text, modifier = Modifier.size(16.dp))
                    }
                }
                if (records.isEmpty()) {
                    EmptyState(
                        Icons.Outlined.MonitorWeight,
                        "还没有身体数据",
                        "点右上角的 + 手动添加，对 AI 说「今天体重 70 公斤」，或者在设置里开启健康数据同步。",
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    records.forEachIndexed { i, r ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                        RecordRow(r) { editing = r }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        BodyRecordSheet(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { record ->
                scope.launch { if (record.id == 0L) dao.insert(record) else dao.update(record) }
            },
            onDelete = { record ->
                scope.launch { dao.delete(record) }
                toast.show("已删除记录", "撤销") { scope.launch { dao.insert(record) } }
            },
        )
    }
}

@Composable
private fun RecordRow(r: BodyRecord, onClick: () -> Unit) {
    val c = Brand.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.weightKg?.fmt() ?: "—", style = Brand.type.n3, color = c.text)
                Text(
                    listOfNotNull(r.weightKg?.let { "kg" }, r.bodyFatPct?.let { "体脂 ${it.fmt()}%" }).joinToString(" · "),
                    style = Brand.type.note,
                    color = c.muted,
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(listOfNotNull(TimeUtil.format(r.measuredAt), r.note).joinToString(" · "), style = Brand.type.note, color = c.muted)
        }
        Tag(SOURCE_LABELS[r.source] ?: r.source, kind = io.github.sekirankumo.fitlog.ui.components.TagKind.More)
    }
}

/** 顶部：体重大数字和所选时间范围内的变化，旁边体脂率 */
@Composable
private fun Summary(all: List<BodyRecord>, inRange: List<BodyRecord>, range: Range, modifier: Modifier) {
    val c = Brand.colors
    val latestWeight = all.firstOrNull { it.weightKg != null }
    val latestFat = all.firstOrNull { it.bodyFatPct != null }
    val firstWeightInRange = inRange.lastOrNull { it.weightKg != null }
    val firstFatInRange = inRange.lastOrNull { it.bodyFatPct != null }
    val wDelta = latestWeight?.weightKg?.let { w -> firstWeightInRange?.takeIf { it !== latestWeight }?.weightKg?.let { w - it } }
    val fDelta = latestFat?.bodyFatPct?.let { f -> firstFatInRange?.takeIf { it !== latestFat }?.bodyFatPct?.let { f - it } }
    fun signed(d: Double) = (if (d >= 0) "+" else "−") + abs(d).fmt()
    Column(modifier) {
        Text("体重", style = Brand.type.label, color = c.muted)
        Spacer(Modifier.height(4.dp))
        FigureNumber(latestWeight?.weightKg?.fmt() ?: "—", "kg", Brand.type.display)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(
                wDelta?.let { "${range.label}内 ${signed(it)}" } ?: latestWeight?.let { TimeUtil.formatShort(it.measuredAt) } ?: "还没有记录",
                style = Brand.type.noteBold,
                color = if (wDelta != null && wDelta < 0) c.brand else c.muted,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("体脂率 ", style = Brand.type.label, color = c.muted, modifier = Modifier.padding(bottom = 2.dp))
                FigureNumber(latestFat?.bodyFatPct?.fmt() ?: "—", "%", Brand.type.n2, unitStyle = Brand.type.nSub2)
                fDelta?.let {
                    Text(" ${signed(it)}", style = Brand.type.note, color = if (it < 0) c.brand else c.muted, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun BodyRecordSheet(
    initial: BodyRecord?,
    onDismiss: () -> Unit,
    onSave: (BodyRecord) -> Unit,
    onDelete: (BodyRecord) -> Unit,
) {
    val c = Brand.colors
    val pickers = rememberPickers()
    var weight by remember { mutableStateOf(initial?.weightKg?.fmt() ?: "") }
    var fat by remember { mutableStateOf(initial?.bodyFatPct?.fmt() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var time by remember { mutableLongStateOf(initial?.measuredAt ?: nowMillis()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun pickDateTime() {
        pickers.pickDateTime(time) { time = it }
    }

    fun validate(): BodyRecord? {
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
        if (error != null) return null
        return (initial ?: BodyRecord(measuredAt = time, weightKg = null, bodyFatPct = null)).copy(
            measuredAt = time,
            weightKg = w,
            bodyFatPct = f,
            note = note.trim().ifEmpty { null },
        )
    }

    BSheet(
        onDismiss = onDismiss,
        title = if (initial == null) "添加记录" else "编辑记录",
        actions = { s ->
            if (initial != null) {
                BButton("删除", Modifier.weight(0.8f), kind = ButtonKind.Danger) { s.close { onDelete(initial) } }
            }
            BButton("取消", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() }
            BButton("保存", Modifier.weight(1f)) {
                validate()?.let { record -> s.close { onSave(record) } }
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(weight, { weight = it }, Modifier.weight(1f), label = "体重（kg）", numeric = true)
                Field(fat, { fat = it }, Modifier.weight(1f), label = "体脂率（%）", numeric = true)
            }
            Field(note, { note = it }, label = "备注（可选）")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("时间：", style = Brand.type.label, color = c.muted)
                Text(
                    TimeUtil.format(time),
                    style = Brand.type.label.copy(fontWeight = FontWeight.Medium),
                    color = c.brand,
                    modifier = Modifier.clip(CircleShape).clickable { pickDateTime() }.padding(horizontal = 4.dp, vertical = 4.dp),
                )
                initial?.let { Text(" · 来源：${SOURCE_LABELS[it.source] ?: it.source}", style = Brand.type.label, color = c.muted) }
            }
            error?.let { Text(it, style = Brand.type.note, color = c.danger) }
        }
    }
}
