package io.github.sekirankumo.fitlog.ui.components

import kotlinx.datetime.*
import io.github.sekirankumo.fitlog.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekirankumo.fitlog.ui.theme.Brand

/** 每天完成组数 → 颜色档位 */
fun heatLevel(sets: Int): Int = when {
    sets <= 0 -> 0
    sets < 9 -> 1
    sets < 16 -> 2
    sets < 25 -> 3
    else -> 4
}

@Composable
fun heatColors(): List<Color> {
    val c = Brand.colors
    return listOf(c.surface2, lerp(c.surface2, c.brand, 0.35f), lerp(c.surface2, c.brand, 0.6f), lerp(c.surface2, c.brand, 0.82f), c.brand)
}

/**
 * 训练热力图：每列一周（周一到周日），每格一天，颜色越深当天完成的组数越多。
 * 宽度能放几周就显示几周，最右边是本周。
 */
@Composable
fun ActivityHeatmap(daily: Map<LocalDate, Int>, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val colors = heatColors()
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = c.muted)
    val today = LocalDate.now()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 3.dp
        // 先按约 13dp 的格子算能放几周，再把格子拉伸到正好铺满宽度
        val weeks = ((maxWidth + gap) / (13.dp + gap)).toInt().coerceIn(4, 53)
        val cell = (maxWidth - gap * (weeks - 1)) / weeks
        val firstMonday = today.with(DayOfWeek.MONDAY).minusWeeks((weeks - 1).toLong())
        Canvas(Modifier.fillMaxWidth().height(16.dp + cell * 7 + gap * 6)) {
            val cellPx = cell.toPx()
            val gapPx = gap.toPx()
            val left = 0f
            val top = 16.dp.toPx()
            var lastLabelX = -1000f
            for (w in 0 until weeks) {
                val monday = firstMonday.plusWeeks(w.toLong())
                val sunday = monday.plusDays(6)
                val x = left + w * (cellPx + gapPx)
                // 月份标签：标在包含某月 1 号的那一列，标签之间留出间距
                val month = when {
                    monday.dayOfMonth == 1 -> monday.monthValue
                    sunday.monthValue != monday.monthValue -> sunday.monthValue
                    else -> null
                }
                if (month != null && x - lastLabelX > 30.dp.toPx()) {
                    val m = measurer.measure("${month}月", labelStyle)
                    if (x + m.size.width <= size.width) {
                        drawText(m, topLeft = Offset(x, 0f))
                        lastLabelX = x
                    }
                }
                for (d in 0 until 7) {
                    val date = monday.plusDays(d.toLong())
                    if (date.isAfter(today)) continue
                    val y = top + d * (cellPx + gapPx)
                    drawRoundRect(
                        colors[heatLevel(daily[date] ?: 0)],
                        topLeft = Offset(x, y),
                        size = Size(cellPx, cellPx),
                        cornerRadius = CornerRadius(3.dp.toPx()),
                    )
                    if (date == today) {
                        drawRoundRect(
                            c.text.copy(alpha = 0.55f),
                            topLeft = Offset(x, y),
                            size = Size(cellPx, cellPx),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                            style = Stroke(1.5.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
