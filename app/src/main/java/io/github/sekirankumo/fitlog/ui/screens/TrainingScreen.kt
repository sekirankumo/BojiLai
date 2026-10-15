package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.plan.PlanExercise
import io.github.sekirankumo.fitlog.data.plan.PlanMeta
import io.github.sekirankumo.fitlog.data.plan.PlanSet
import io.github.sekirankumo.fitlog.data.plan.fmtKg
import io.github.sekirankumo.fitlog.data.plan.formatDuration
import io.github.sekirankumo.fitlog.data.plan.markDone
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.AssetImage
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.BSheet
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.CardHeader
import io.github.sekirankumo.fitlog.ui.components.DayMark
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.ExpandableCalendar
import io.github.sekirankumo.fitlog.ui.components.FigureNumber
import io.github.sekirankumo.fitlog.ui.components.LocalReduceMotion
import io.github.sekirankumo.fitlog.ui.components.Motion
import io.github.sekirankumo.fitlog.ui.components.NumberField
import io.github.sekirankumo.fitlog.ui.components.ProgressLine
import io.github.sekirankumo.fitlog.ui.components.Tag
import io.github.sekirankumo.fitlog.ui.components.TagKind
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.pressScale
import io.github.sekirankumo.fitlog.ui.components.pullConnection
import io.github.sekirankumo.fitlog.ui.components.rememberCalendarState
import io.github.sekirankumo.fitlog.ui.components.rememberSpeechInput
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.hypot

class TrainingNav(
    val openExercise: (String) -> Unit,
    val openLibrary: () -> Unit,
    val openGenerate: () -> Unit,
    val openOperations: () -> Unit,
    val askAi: (String) -> Unit,
)

private val SHORT = DateTimeFormatter.ofPattern("M月d日")

/**
 * 训练：日期条（往下拉展开成月历，日期下写当天练的部位，颜色表示完成情况）、
 * 这天的训练（日期、状态、进度、语音打卡 / 全部完成 / 问 AI）、一张卡片一个动作、最后是计划信息。
 */
@Composable
fun TrainingScreen(container: AppContainer, nav: TrainingNav) {
    val plans = container.plans
    val cal = rememberCalendarState()
    val selected = cal.selected
    val month = cal.month

    val rangeFlow = remember(month) { plans.observeRange(month.atDay(1).minusDays(7), month.atEndOfMonth().plusDays(7)) }
    val days by rangeFlow.collectAsState(initial = emptyList())
    val byDate = remember(days) { days.associateBy { it.date } }
    val hasPlanFlow = remember { plans.observeHasPlan() }
    val hasPlan by hasPlanFlow.collectAsState(initial = true)
    val metaFlow = remember { plans.observeMeta() }
    val meta by metaFlow.collectAsState(initial = null)
    val dayFlow = remember(selected) { plans.observeDay(selected) }
    val day by dayFlow.collectAsState(initial = null)
    val lastWeights by produceState(emptyMap<String, Double>(), days) { value = plans.lastWeights() }
    val today = LocalDate.now()

    val list = rememberLazyListState()
    TrackTopBar(list)
    val pull = remember(cal, list) { cal.pullConnection { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 } }

    val c = Brand.colors
    LazyColumn(state = list, modifier = Modifier.nestedScroll(pull), contentPadding = screenPadding()) {
        if (!hasPlan) {
            item { EmptyPlan(nav) }
            return@LazyColumn
        }
        item(key = "calendar") {
            ExpandableCalendar(
                state = cal,
                onSelect = {},
                mark = { d ->
                    byDate[d.toEpochDay()]?.let { p ->
                        val color = when {
                            p.status == DayStatus.DONE -> c.brand
                            p.status == DayStatus.MISSED -> c.danger
                            d == today -> c.text
                            p.status == DayStatus.SKIPPED -> c.faint
                            else -> c.muted
                        }
                        DayMark.Label(shortTitle(p.title), color, bold = d == today)
                    }
                },
                description = { d -> byDate[d.toEpochDay()]?.let { "${it.title} ${DayStatus.label(it.status)}" } ?: "休息" },
                legend = {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("已完成", style = Brand.type.note, color = c.brand)
                        Text("漏打卡", style = Brand.type.note, color = c.danger)
                        Text("待训练", style = Brand.type.note, color = c.muted)
                    }
                },
            )
        }
        item(key = "day") {
            Box(Modifier.padding(top = 16.dp)) { DayCard(container, selected, day, nav) }
        }
        val d = day
        if (d != null) {
            val exercises = plans.exercisesOf(d)
            val past = !selected.isAfter(today)
            if (past) {
                item(key = "hint") {
                    Text("点一组打卡，长按修改实际次数和重量", style = Brand.type.note, color = c.muted, modifier = Modifier.padding(start = 4.dp, top = 14.dp))
                }
            }
            // 今天要做的下一组用描边标出来
            val next = if (selected == today) {
                exercises.withIndex().firstNotNullOfOrNull { (ei, ex) -> ex.sets.indexOfFirst { !it.done }.takeIf { it >= 0 }?.let { ei to it } }
            } else {
                null
            }
            itemsIndexed(exercises, key = { i, ex -> "ex_${i}_${ex.name}" }) { ei, ex ->
                ExerciseCard(container, selected, ei, ex, past, next, lastWeights, nav, Modifier.padding(top = 12.dp))
            }
        }
        meta?.let { m ->
            item(key = "plan") { PlanCard(m, today, nav, Modifier.padding(top = 12.dp)) }
        }
    }
}

@Composable
private fun EmptyPlan(nav: TrainingNav) {
    EmptyState(
        Icons.Outlined.EventAvailable,
        "还没有训练计划",
        "告诉 AI 你的目标、每周能练几天、有什么器材，它会从动作库里挑动作，排好每天练什么、几组几次。",
        modifier = Modifier.padding(top = 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BButton("AI 生成训练计划", Modifier.fillMaxWidth(), icon = AppIcons.Sparkle, height = 50.dp, onClick = nav.openGenerate)
            BButton("先看看动作库", Modifier.fillMaxWidth(), kind = ButtonKind.Soft, onClick = nav.openLibrary)
        }
    }
}

private fun dateText(date: LocalDate, today: LocalDate) =
    date.format(DateTimeFormatter.ofPattern("M月d日 ")) + date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA) +
        if (date == today) " · 今天" else ""

/** 这天的训练：日期、标题、状态、进度和操作；休息日只有一句话和「让 AI 安排」 */
@Composable
private fun DayCard(container: AppContainer, date: LocalDate, day: PlanDay?, nav: TrainingNav) {
    val c = Brand.colors
    val plans = container.plans
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
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

    if (day == null) {
        BCard {
            Text(dateText(date, today), style = Brand.type.label, color = c.muted)
            Spacer(Modifier.height(2.dp))
            Text("休息日", style = Brand.type.h2, color = c.text)
            Spacer(Modifier.height(4.dp))
            Text("这天没有安排训练", style = Brand.type.label, color = c.muted)
            Spacer(Modifier.height(14.dp))
            BButton("让 AI 在这天安排训练", Modifier.fillMaxWidth(), kind = ButtonKind.Soft, icon = AppIcons.Sparkle) {
                nav.askAi("我想在 $date 加一次训练，")
            }
        }
        return
    }

    val exercises = plans.exercisesOf(day)
    val doneSets = exercises.sumOf { ex -> ex.sets.count { it.done } }
    val totalSets = exercises.sumOf { it.sets.size }
    // 一天全部做完的那一刻，卡片边缘亮一下淡绿光（只亮一次）
    val flash = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    val previous = remember(day.date) { arrayOf(day.status) }
    LaunchedEffect(day.status) {
        if (!reduce && previous[0] != DayStatus.DONE && day.status == DayStatus.DONE) {
            flash.animateTo(1f, tween(220))
            flash.animateTo(0f, tween(700))
        }
        previous[0] = day.status
    }
    BCard(flash = flash.value) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(dateText(date, today), style = Brand.type.label, color = c.muted)
                Spacer(Modifier.height(2.dp))
                Text(day.title, style = Brand.type.h2, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusTag(day.status)
        }
        day.note?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = Brand.type.label, color = c.muted)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgressLine(if (totalSets == 0) 0f else doneSets.toFloat() / totalSets, Modifier.weight(1f))
            FigureNumber("$doneSets", "/ $totalSets 组", Brand.type.n2, unitStyle = Brand.type.label)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!date.isAfter(today)) {
                BButton("语音打卡", Modifier.weight(1f), icon = Icons.Outlined.Mic, loading = voiceBusy, onClick = speech)
                if (day.status != DayStatus.DONE) {
                    BButton("全部完成", Modifier.weight(1f), kind = ButtonKind.Soft, icon = Icons.Outlined.TaskAlt) {
                        container.appScope.launch { plans.completeDay(date) }
                    }
                }
            }
            BButton("问 AI", Modifier.weight(1f), kind = ButtonKind.Soft, icon = AppIcons.Sparkle) {
                nav.askAi("$date 的训练（${day.title}）我想调整：")
            }
        }
        voiceError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = Brand.type.note, color = c.danger)
        }
    }

    pendingCheckin?.let { (args, text) ->
        BSheet(
            onDismiss = { pendingCheckin = null },
            title = "确认打卡",
            actions = { s ->
                BButton("取消", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() }
                BButton("确认", Modifier.weight(1f)) {
                    s.close {
                        val tool = container.tools["checkin_training"]!!
                        container.appScope.launch { runCatching { container.operations.runWrite(tool, args) } }
                    }
                }
            },
        ) {
            Text(text, style = Brand.type.body, color = c.text)
        }
    }
}

@Composable
private fun StatusTag(status: String) {
    val kind = when (status) {
        DayStatus.DONE -> TagKind.Good
        DayStatus.MISSED -> TagKind.Less
        else -> TagKind.More
    }
    Tag(DayStatus.label(status), kind = kind)
}

/** 一个动作一张卡片：照片、名称（全部做完时出现圆圈对勾）、部位休息上次重量、每一组；点上面这一行看动作要领 */
@Composable
private fun ExerciseCard(
    container: AppContainer,
    date: LocalDate,
    ei: Int,
    ex: PlanExercise,
    past: Boolean,
    next: Pair<Int, Int>?,
    lastWeights: Map<String, Double>,
    nav: TrainingNav,
    modifier: Modifier,
) {
    val c = Brand.colors
    val plans = container.plans
    val info = container.exercises[ex.exerciseId]
    val allDone = ex.sets.isNotEmpty() && ex.sets.all { it.done }
    var editing by remember { mutableStateOf<Int?>(null) }
    BCard(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = info != null, onClickLabel = "查看动作要领") { info?.let { nav.openExercise(it.id) } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssetImage(info?.images?.firstOrNull(), Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ex.name, style = Brand.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    AnimatedVisibility(
                        visible = allDone,
                        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.4f) + fadeIn(tween(Motion.SHORT)),
                        exit = scaleOut(tween(Motion.SHORT), targetScale = 0.6f) + fadeOut(tween(Motion.SHORT)),
                    ) {
                        DoneMark(Modifier.padding(start = 6.dp))
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(
                        info?.primary?.joinToString("、"),
                        ex.restSec?.let { "休息 ${formatDuration(it)}" },
                        lastWeights[plans.exerciseKey(ex)]?.let { "上次 ${it.fmtKg()}" },
                    ).joinToString(" · "),
                    style = Brand.type.note,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 只留一个箭头，表示这一行能点进去看要领
            if (info != null) {
                Icon(AppIcons.Chevron, contentDescription = null, tint = c.faint, modifier = Modifier.size(20.dp))
            }
        }
        ex.note?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Brand.type.label, color = c.muted)
        }
        Spacer(Modifier.height(12.dp))
        // 一行四组，对齐整齐
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ex.sets.chunked(4).forEachIndexed { row, chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunk.forEachIndexed { k, s ->
                        val si = row * 4 + k
                        SetChip(
                            index = si,
                            set = s,
                            isNext = next == (ei to si),
                            enabled = past,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                container.appScope.launch {
                                    plans.updateSet(date, ei, si) {
                                        if (it.done) it.copy(done = false, doneReps = null, doneWeightKg = null, doneDurationSec = null)
                                        else it.markDone(lastWeights[plans.exerciseKey(ex)])
                                    }
                                }
                            },
                            onLongClick = { editing = si },
                        )
                    }
                    repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    editing?.let { si ->
        ex.sets.getOrNull(si)?.let { s ->
            SetEditSheet(s, ex, si, onDismiss = { editing = null }) { updated ->
                container.appScope.launch { plans.updateSet(date, ei, si) { updated } }
            }
        }
    }
}

/** 名字旁边的圆圈对勾 */
@Composable
private fun DoneMark(modifier: Modifier = Modifier) {
    val c = Brand.colors
    Box(modifier.size(18.dp).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
        Icon(AppIcons.Check, contentDescription = "已完成", tint = c.onBrand, modifier = Modifier.size(12.dp))
    }
}

/**
 * 一组：做完的是浅绿底，从手指按下的位置圆形铺开，小对勾画出来；下一组用描边标出来。
 * 点一下打卡 / 取消，长按改实际次数和重量。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetChip(
    index: Int,
    set: PlanSet,
    isNext: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val c = Brand.colors
    val interaction = remember { MutableInteractionSource() }
    var pressAt by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Press) pressAt = it.pressPosition }
    }
    val reveal by animateFloatAsState(if (set.done) 1f else 0f, tween(320, easing = Motion.EmphasizedDecelerate), label = "reveal")
    val tick by animateFloatAsState(if (set.done) 1f else 0f, tween(250, delayMillis = if (set.done) 100 else 0), label = "tick")
    val fg by animateColorAsState(if (set.done) c.brand else c.text, tween(Motion.SHORT), label = "setFg")
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .pressScale(interaction, 0.94f)
            .clip(shape)
            .background(if (isNext) Color.Transparent else c.surface2)
            .drawBehind {
                if (reveal > 0f) {
                    val center = pressAt ?: Offset(size.width / 2, size.height / 2)
                    val far = maxOf(
                        hypot(center.x, center.y), hypot(size.width - center.x, center.y),
                        hypot(center.x, size.height - center.y), hypot(size.width - center.x, size.height - center.y),
                    )
                    drawCircle(c.brandSoft, far * reveal, center)
                }
            }
            .then(if (isNext && !set.done) Modifier.border(1.5.dp, c.brand, shape) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tick > 0f) {
                Canvas(Modifier.size(10.dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.52f)
                        lineTo(size.width * 0.4f, size.height * 0.72f)
                        lineTo(size.width * 0.8f, size.height * 0.28f)
                    }
                    val measure = PathMeasure().apply { setPath(path, false) }
                    val part = Path()
                    measure.getSegment(0f, measure.length * tick, part, true)
                    drawPath(part, fg, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Spacer(Modifier.width(2.dp))
            }
            Text("第${index + 1}组", style = Brand.type.tiny, color = if (set.done) c.brand else c.muted)
        }
        Spacer(Modifier.height(1.dp))
        Text(set.doneText() ?: set.targetText(), style = Brand.type.nSub1.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 1)
    }
}

@Composable
private fun SetEditSheet(set: PlanSet, exercise: PlanExercise, index: Int, onDismiss: () -> Unit, onSave: (PlanSet) -> Unit) {
    val timed = set.durationSec != null
    var reps by remember { mutableStateOf((set.doneReps ?: set.reps)?.toDouble()) }
    var weight by remember { mutableStateOf(set.doneWeightKg ?: set.weightKg) }
    var duration by remember { mutableStateOf((set.doneDurationSec ?: set.durationSec)?.toDouble()) }
    BSheet(
        onDismiss = onDismiss,
        title = "修改这一组",
        subtitle = "${exercise.name} · 第 ${index + 1} 组 · 目标 ${set.targetText()}",
        actions = { s ->
            if (set.done) {
                BButton("取消打卡", Modifier.weight(1f), kind = ButtonKind.Soft) {
                    s.close { onSave(set.copy(done = false, doneReps = null, doneWeightKg = null, doneDurationSec = null)) }
                }
            } else {
                BButton("关闭", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() }
            }
            BButton("完成这一组", Modifier.weight(1f)) {
                s.close { onSave(set.copy(done = true, doneReps = reps?.toInt(), doneWeightKg = weight, doneDurationSec = duration?.toInt())) }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (timed) {
                NumberField(duration, { duration = it }, "实际秒数", Modifier.weight(1f), integer = true)
            } else {
                NumberField(reps, { reps = it }, "实际次数", Modifier.weight(1f), integer = true)
                NumberField(weight, { weight = it }, "重量 kg", Modifier.weight(1f))
            }
        }
    }
}

/** 计划信息：名称、第几周、起止日期、说明，以及调整记录、新计划 */
@Composable
private fun PlanCard(meta: PlanMeta, today: LocalDate, nav: TrainingNav, modifier: Modifier) {
    val c = Brand.colors
    val start = LocalDate.ofEpochDay(meta.startDate)
    val end = LocalDate.ofEpochDay(meta.endDate)
    val totalWeeks = ((meta.endDate - meta.startDate + 1) / 7).toInt().coerceAtLeast(1)
    val week = ((today.toEpochDay() - meta.startDate) / 7 + 1).toInt()
    BCard(modifier) {
        CardHeader(meta.name) {
            if (!today.isBefore(start) && !today.isAfter(end)) {
                Text("第 $week / $totalWeeks 周", style = Brand.type.note, color = c.muted)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("${start.format(SHORT)} – ${end.format(SHORT)}", style = Brand.type.note, color = c.muted)
        meta.summary.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = Brand.type.label, color = c.muted)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BButton("调整记录", Modifier.weight(1f), kind = ButtonKind.Soft, icon = Icons.Outlined.History, onClick = nav.openOperations)
            BButton("新计划", Modifier.weight(1f), kind = ButtonKind.Soft, icon = AppIcons.Sparkle, onClick = nav.openGenerate)
        }
    }
}

/**
 * 日历格子里放得下的短名字：去掉空格和括号里的说明，太长时只留加号前面那段。
 * 例如「上肢拉（背/二头）+ 核心」→「上肢拉」，「胸 + 三头」→「胸+三头」。
 */
private fun shortTitle(title: String): String {
    val plain = title.replace(" ", "").substringBefore("（").substringBefore("(")
    return if (plain.length > 4) plain.substringBefore("+").substringBefore("＋").ifEmpty { plain } else plain
}
