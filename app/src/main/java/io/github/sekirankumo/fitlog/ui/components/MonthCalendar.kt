package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.PlanDay
import java.time.LocalDate
import java.time.YearMonth

/** 训练日历：每格显示日期和训练主题，颜色表示状态 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    days: Map<Long, PlanDay>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    val today = LocalDate.now()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
        }
        Row {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val first = month.atDay(1)
        val offset = first.dayOfWeek.value - 1
        val total = offset + month.lengthOfMonth()
        val rows = (total + 6) / 7
        for (r in 0 until rows) {
            Row {
                for (c in 0 until 7) {
                    val index = r * 7 + c - offset + 1
                    if (index < 1 || index > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = month.atDay(index)
                        DayCell(
                            date = date,
                            day = days[date.toEpochDay()],
                            isToday = date == today,
                            isSelected = date == selected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(date) },
                        )
                    }
                }
            }
        }
        Legend()
    }
}

@Composable
fun statusColor(status: String?): Color = when (status) {
    DayStatus.DONE -> MaterialTheme.colorScheme.primary
    DayStatus.MISSED -> MaterialTheme.colorScheme.error
    DayStatus.SKIPPED -> MaterialTheme.colorScheme.outline
    DayStatus.PLANNED -> MaterialTheme.colorScheme.tertiary
    else -> Color.Transparent
}

@Composable
private fun DayCell(
    date: LocalDate,
    day: PlanDay?,
    isToday: Boolean,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val color = statusColor(day?.status)
    Box(
        modifier
            .aspectRatio(0.85f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.secondaryContainer
                    day?.status == DayStatus.DONE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    day?.status == DayStatus.MISSED -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            if (day != null) {
                Box(Modifier.padding(top = 2.dp).size(6.dp).clip(CircleShape).background(color))
                Text(
                    day.title,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(DayStatus.PLANNED, DayStatus.DONE, DayStatus.MISSED).forEach { s ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(s)))
            Text(
                " ${DayStatus.label(s)}　",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
