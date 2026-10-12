package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt

/** 图表上的一个点：时间（epoch 毫秒）和数值 */
data class ChartPoint(val time: Long, val value: Double)

/**
 * 趋势折线图：浅色细线为每次测量，粗线为 7 天移动平均。
 * points 不要求有序。
 */
@Composable
fun TrendChart(points: List<ChartPoint>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Box(modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(
                "至少需要两条记录才能显示趋势",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val sorted = points.sortedBy { it.time }
    val avg = movingAverage(sorted, windowMs = 7L * 24 * 3600 * 1000)
    val raw = MaterialTheme.colorScheme.outline
    val main = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = labelColor)

    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val left = 44.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()

        val minV = sorted.minOf { it.value }
        val maxV = sorted.maxOf { it.value }
        val pad = ((maxV - minV) * 0.15).coerceAtLeast(0.5)
        val lo = minV - pad
        val hi = maxV + pad
        val t0 = sorted.first().time
        val t1 = sorted.last().time.coerceAtLeast(t0 + 1)

        fun x(t: Long) = left + (right - left) * ((t - t0).toFloat() / (t1 - t0))
        fun y(v: Double) = bottom - (bottom - top) * ((v - lo) / (hi - lo)).toFloat()

        // 横向网格和数值标签
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            val yy = y(v)
            drawLine(grid, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
            val label = measurer.measure(v.fmt(), labelStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), yy - label.size.height / 2))
        }

        // 首尾日期
        val startLabel = measurer.measure(TimeUtil.formatShortDate(t0), labelStyle)
        val endLabel = measurer.measure(TimeUtil.formatShortDate(t1), labelStyle)
        drawText(startLabel, topLeft = Offset(left, bottom + 4.dp.toPx()))
        drawText(endLabel, topLeft = Offset(right - endLabel.size.width, bottom + 4.dp.toPx()))

        // 原始测量
        val rawPath = Path()
        sorted.forEachIndexed { i, p ->
            if (i == 0) rawPath.moveTo(x(p.time), y(p.value)) else rawPath.lineTo(x(p.time), y(p.value))
        }
        drawPath(rawPath, raw, style = Stroke(width = 1.5.dp.toPx()))
        sorted.forEach { p -> drawCircle(raw, radius = 2.5.dp.toPx(), center = Offset(x(p.time), y(p.value))) }

        // 移动平均
        val avgPath = Path()
        avg.forEachIndexed { i, p ->
            if (i == 0) avgPath.moveTo(x(p.time), y(p.value)) else avgPath.lineTo(x(p.time), y(p.value))
        }
        drawPath(
            avgPath,
            main,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 每个点取它之前 windowMs 内所有点的平均 */
private fun movingAverage(sorted: List<ChartPoint>, windowMs: Long): List<ChartPoint> {
    var start = 0
    var sum = 0.0
    return sorted.mapIndexed { i, p ->
        sum += p.value
        while (sorted[start].time < p.time - windowMs) {
            sum -= sorted[start].value
            start++
        }
        ChartPoint(p.time, sum / (i - start + 1))
    }
}
