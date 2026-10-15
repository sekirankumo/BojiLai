package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

private val ROW = 50.dp
private val HEAD = 40.dp
private val LEGEND = 30.dp

/**
 * 日历的状态：选中的日期、正在看的月份，以及展开程度 t（0 只露出选中那一周，1 是整月）。
 */
@Stable
class CalendarState(selected: LocalDate, expanded: Boolean) {
    var selected by mutableStateOf(selected)
    var month by mutableStateOf(YearMonth.from(selected))
    var t by mutableFloatStateOf(if (expanded) 1f else 0f)

    /** 从一周展开到整月要拉的距离（像素） */
    internal var span = 1f

    /** 这次拖动是在展开还是收起，松手时据此决定停在哪 */
    internal var opening = true

    /** 松手后用弹簧收到 0 或 1；收起时回到选中日期所在的月 */
    suspend fun settle(target: Float) {
        if (target == 0f) month = YearMonth.from(selected)
        animate(t, target, animationSpec = Motion.settle()) { v, _ -> t = v }
    }

    companion object {
        val Saver = Saver<CalendarState, List<Long>>(
            save = { listOf(it.selected.toEpochDay(), if (it.t > 0.5f) 1L else 0L) },
            restore = { CalendarState(LocalDate.ofEpochDay(it[0]), it[1] == 1L) },
        )
    }
}

@Composable
fun rememberCalendarState(): CalendarState = rememberSaveable(saver = CalendarState.Saver) { CalendarState(LocalDate.now(), false) }

/**
 * 页面级的「二楼」下拉：页面在最顶上时继续往下拉，先有一段阻力，再跟手展开成整月；
 * 拉过三成松手就展开。展开后手指往上推先收起月历。页面不在顶部时正常滚动，不受影响。
 * @param atTop 列表是否在最顶上
 */
fun CalendarState.pullConnection(atTop: () -> Boolean): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y < 0f && t > 0f && atTop()) {
            opening = false
            val newT = (t + available.y / span).coerceAtLeast(0f)
            val used = (t - newT) * span
            t = newT
            return Offset(0f, -used)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y > 0f && t < 1f) {
            opening = true
            // 前一小段拉得很费劲，过了之后才跟手
            val factor = if (t < 0.06f) 0.3f else 0.75f
            t = (t + available.y * factor / span).coerceAtMost(1f)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (t <= 0f || t >= 1f) return Velocity.Zero
        val target = when {
            available.y > 1200f -> 1f
            available.y < -1200f -> 0f
            opening -> if (t > 0.3f) 1f else 0f
            else -> if (t < 0.75f) 0f else 1f
        }
        settle(target)
        return available
    }
}

/** 日期下面的标记：一个色点（饮食）或一小段文字（训练） */
sealed interface DayMark {
    data class Dot(val color: Color) : DayMark
    data class Label(val text: String, val color: Color, val bold: Boolean = false) : DayMark
}

/**
 * 平时显示一周，展开后是整月（其他几周从选中那一周的上下两边露出来）。
 * 左右滑动换周（收起时）或换月（展开时）。
 * @param mark 某一天下面显示什么；没有就返回 null
 * @param description 读屏用的这一天的说明
 */
@Composable
fun ExpandableCalendar(
    state: CalendarState,
    onSelect: (LocalDate) -> Unit,
    mark: (LocalDate) -> DayMark?,
    modifier: Modifier = Modifier,
    description: (LocalDate) -> String = { "" },
    legend: @Composable () -> Unit = {},
) {
    val c = Brand.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val t = state.t
    val month = state.month
    val first = month.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val rows = ((first.dayOfWeek.value - 1) + month.lengthOfMonth() + 6) / 7
    val selRow = ((state.selected.toEpochDay() - start.toEpochDay()) / 7).toInt().takeIf { state.selected >= start && it < rows } ?: 0
    state.span = with(density) { (ROW * (rows - 1) + HEAD + LEGEND).toPx() }

    fun select(d: LocalDate) {
        state.selected = d
        if (state.t < 0.5f) state.month = YearMonth.from(d)
        onSelect(d)
    }
    fun shift(step: Int) {
        if (state.t > 0.5f) state.month = state.month.plusMonths(step.toLong()) else select(state.selected.plusWeeks(step.toLong()))
    }

    Column(modifier.fillMaxWidth()) {
        // 展开后才出现的月份标题
        Box(Modifier.fillMaxWidth().height(HEAD * t).clipToBounds().graphicsLayer { alpha = t }) {
            Row(Modifier.fillMaxWidth().height(HEAD), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.ChevronLeft, contentDescription = "上个月", tint = c.muted,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { shift(-1) }.padding(8.dp),
                )
                Text("${month.year} 年 ${month.monthValue} 月", style = Brand.type.title, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Icon(
                    AppIcons.Chevron, contentDescription = "下个月", tint = c.muted,
                    modifier = Modifier.size(36.dp).clip(CircleShape).clickable { shift(1) }.padding(8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(20.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, style = Brand.type.note, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(ROW + ROW * (rows - 1) * t)
                .clipToBounds()
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (abs(total) > 40.dp.toPx()) shift(if (total < 0) 1 else -1) },
                    ) { _, dx -> total += dx }
                },
        ) {
            Column(Modifier.offset { IntOffset(0, (-selRow * ROW.toPx() * (1 - t)).roundToInt()) }) {
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth().height(ROW), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (col in 0 until 7) {
                            val d = start.plusDays((r * 7 + col).toLong())
                            DayCell(
                                date = d,
                                mark = mark(d),
                                selected = d == state.selected,
                                isToday = d == today,
                                future = d.isAfter(today),
                                outside = d.monthValue != month.monthValue && t > 0.5f,
                                description = description(d),
                                modifier = Modifier.weight(1f),
                            ) { select(d) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(LEGEND * t).clipToBounds().graphicsLayer { alpha = t }, contentAlignment = Alignment.Center) {
            legend()
        }
        // 读屏和键盘用户：没有把手，提供一个看不见的展开 / 收起按钮
        Box(
            Modifier
                .size(1.dp)
                .semantics { contentDescription = if (t > 0.5f) "收起月历" else "展开月历" }
                .clickable { scope.launch { state.settle(if (state.t > 0.5f) 0f else 1f) } },
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    mark: DayMark?,
    selected: Boolean,
    isToday: Boolean,
    future: Boolean,
    outside: Boolean,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = Brand.colors
    val bg by animateColorAsState(if (selected) c.surface else Color.Transparent, tween(Motion.SHORT), label = "cell")
    Column(
        modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .height(46.dp)
            .then(if (selected) Modifier.cardMaterial(RoundedCornerShape(14.dp)) else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClickLabel = description.ifEmpty { null }, onClick = onClick)
            .graphicsLayer { alpha = if (outside) 0.3f else 1f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}",
            style = Brand.type.n3,
            color = when {
                isToday && !selected -> c.brand
                future -> c.muted
                else -> c.text
            },
        )
        Spacer(Modifier.height(3.dp))
        when (mark) {
            is DayMark.Dot -> Box(Modifier.size(5.dp).clip(CircleShape).background(mark.color))
            is DayMark.Label -> Text(
                mark.text,
                style = if (mark.bold) Brand.type.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else Brand.type.tiny,
                color = mark.color,
                maxLines = 1,
            )
            null -> Spacer(Modifier.height(5.dp))
        }
    }
}

/** 图例里的一项：色点 + 文字 */
@Composable
fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(" $text", style = Brand.type.note, color = Brand.colors.muted)
    }
}
