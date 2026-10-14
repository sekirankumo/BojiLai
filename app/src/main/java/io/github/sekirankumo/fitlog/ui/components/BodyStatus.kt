package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.stats.TrainingStats
import io.github.sekirankumo.fitlog.ui.theme.Brand
import java.time.LocalDate

/** 由训练记录算出的统计：肌肉图深浅、每天完成组数 */
class TrainingData(
    val intensities: Map<String, Float>,
    val daily: Map<LocalDate, Int>,
    val summary: TrainingStats.Summary,
)

/** 读取最近一年的训练记录并计算统计，记录变化时自动更新 */
@Composable
fun rememberTrainingData(container: AppContainer): TrainingData {
    val today = LocalDate.now()
    val flow = remember { container.plans.observeRange(today.minusWeeks(53), today) }
    val days by flow.collectAsState(initial = emptyList())
    return remember(days) {
        val now = System.currentTimeMillis()
        val plans = container.plans
        val loads = TrainingStats.sessionLoads(
            days,
            exercisesOf = { plans.exercisesOf(it) },
            lookup = { container.exercises.resolve(it.exerciseId, it.name) },
            now = now,
        )
        val daily = TrainingStats.dailySets(days) { plans.exercisesOf(it) }
        TrainingData(
            intensities = TrainingStats.intensities(TrainingStats.residualLoad(loads, now)),
            daily = daily,
            summary = TrainingStats.summary(daily, today),
        )
    }
}

/**
 * 身体状态：左边是正面、背面肌肉图（颜色越深 = 练得越多、越近），右边是体重和体脂。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyStatusCard(
    intensities: Map<String, Float>,
    female: Boolean,
    weight: String,
    weightSub: String?,
    weightSubGood: Boolean,
    fat: String,
    fatSub: String?,
    fatSubGood: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Brand.colors
    BCard(modifier.fillMaxWidth(), onClick = onClick) {
        SectionHeader("身体状态") {
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1.25f)) {
                MuscleFigure(BodyView.front(female), intensities, Modifier.weight(1f).height(196.dp))
                MuscleFigure(BodyView.back(female), intensities, Modifier.weight(1f).height(196.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric("体重", weight, "kg", weightSub, weightSubGood)
                HorizontalDivider(color = c.line)
                Metric("体脂率", fat, "%", fatSub, fatSubGood)
            }
        }
        Spacer(Modifier.height(10.dp))
        val recent = intensities.filterValues { it >= 0.12f }.entries.sortedByDescending { it.value }
            .mapNotNull { TrainingStats.SLUG_LABELS[it.key] }.distinct().take(5)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (recent.isEmpty()) "最近几天没有训练记录" else "正在恢复",
                style = MaterialTheme.typography.labelMedium,
                color = c.muted,
            )
            Spacer(Modifier.weight(1f))
            Legend()
        }
        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recent.forEach { Pill(it) }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, sub: String?, good: Boolean) {
    val c = Brand.colors
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(" $unit", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        sub?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = if (good) c.brand else c.muted, maxLines = 1)
        }
    }
}

/** 深浅图例：较久 ▢▢▢▢ 刚练过 */
@Composable
private fun Legend() {
    val c = Brand.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("较久 ", style = MaterialTheme.typography.labelSmall, color = c.muted)
        muscleLegendColors().forEach { color -> Swatch(color) }
        Text(" 刚练过", style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
}

/** 训练热力图下方的说明行 + 图例 */
@Composable
fun HeatmapFooter(summary: TrainingStats.Summary) {
    val c = Brand.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "今年 ${summary.thisYear} 次 · 本周 ${summary.thisWeek} 次" +
                if (summary.weekStreak >= 2) " · 连续 ${summary.weekStreak} 周" else "",
            style = MaterialTheme.typography.labelMedium,
            color = c.muted,
            modifier = Modifier.weight(1f),
        )
        Text("少 ", style = MaterialTheme.typography.labelSmall, color = c.muted)
        heatColors().forEach { Swatch(it) }
        Text(" 多", style = MaterialTheme.typography.labelSmall, color = c.muted)
    }
}
