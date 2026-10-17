package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.NutritionTargets
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.diet.DayQuality
import io.github.sekirankumo.fitlog.data.diet.QualityZone
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.data.diet.DietRepository
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.ui.components.AnimatedNumber
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.BSheet
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.DayMark
import io.github.sekirankumo.fitlog.ui.components.DietQualityCard
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.ExpandableCalendar
import io.github.sekirankumo.fitlog.ui.components.FigureNumber
import io.github.sekirankumo.fitlog.ui.components.FoodDraftEditor
import io.github.sekirankumo.fitlog.ui.components.HighSaltTag
import io.github.sekirankumo.fitlog.ui.components.LegendDot
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.ProgressLine
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.color
import io.github.sekirankumo.fitlog.ui.components.itemMotion
import io.github.sekirankumo.fitlog.ui.components.pressScale
import io.github.sekirankumo.fitlog.ui.components.pullConnection
import io.github.sekirankumo.fitlog.ui.components.rememberCalendarState
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 饮食：日期条（往下拉展开成月历）、这天吃了多少和三大营养素、记一餐、膳食质量、各餐记录。
 * 点日期切到那一天；点一条记录在弹层里修改或删除（删除可撤销）。
 */
@Composable
fun DietScreen(container: AppContainer, completeProfile: (List<String>) -> Unit, openAdd: (LocalDate) -> Unit) {
    val c = Brand.colors
    val cal = rememberCalendarState()
    val day = cal.selected
    val today = LocalDate.now()
    val entriesFlow = remember(day) { container.diet.observeDay(day) }
    // 还在从数据库读取时为 null：这时不显示「还没有记录」，免得每次打开都闪一下
    val loadedEntries by entriesFlow.collectAsState(initial = null)
    val entries = loadedEntries.orEmpty()
    val targetFlow = remember(day) { container.nutritionPlan.targetFor(day) }
    val targets by targetFlow.collectAsState(initial = null)
    var editing by remember { mutableStateOf<FoodEntry?>(null) }

    // 日历上显示的这个月（前后多带一周）每天的膳食分区间
    val month = cal.month
    val rangeFlow = remember(month) {
        container.database.foodDao().observeRange(
            DietRepository.dayStart(month.atDay(1).minusDays(7)),
            DietRepository.dayStart(month.atEndOfMonth().plusDays(8)),
        )
    }
    val rangeEntries by rangeFlow.collectAsState(initial = emptyList())
    val dayQuality = remember(rangeEntries) {
        rangeEntries.groupBy { TimeUtil.toLocal(it.eatenAt).toLocalDate() }.mapValues { DayQuality.of(it.value) }
    }
    // 本周平均：这天所在的周一到这天
    val monday = day.minusDays((day.dayOfWeek.value - 1).toLong())
    val weekFlow = remember(monday) {
        container.database.foodDao().observeRange(DietRepository.dayStart(monday.minusDays(1)), DietRepository.dayStart(monday.plusDays(7)))
    }
    val weekEntries by weekFlow.collectAsState(initial = emptyList())
    val weekQuality = remember(weekEntries) {
        weekEntries.groupBy { TimeUtil.toLocal(it.eatenAt).toLocalDate() }.mapValues { DayQuality.of(it.value) }
    }
    val weekAverage = weekQuality.filterKeys { it >= monday && it <= day }.values.filterNotNull().filter { it.analyzed > 0 }
        .map { it.total }.takeIf { it.isNotEmpty() }?.average()
    val yesterday = weekQuality[day.minusDays(1)]?.takeIf { it.analyzed > 0 }?.total
    val quality = remember(entries) { DayQuality.of(entries) }
    val backfill by container.dietAnalyzer.progress.collectAsState()

    val list = rememberLazyListState()
    TrackTopBar(list)
    val pull = remember(cal, list) { cal.pullConnection { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 } }

    LazyColumn(
        state = list,
        modifier = Modifier.nestedScroll(pull),
        contentPadding = screenPadding(),
    ) {
        item(key = "calendar") {
            ExpandableCalendar(
                state = cal,
                onSelect = {},
                mark = { d ->
                    dayQuality[d]?.let { q ->
                        if (q.analyzed == 0) {
                            DayMark.Dot(c.faint)
                        } else {
                            DayMark.Dot(
                                when (q.zone) {
                                    QualityZone.Good -> c.brand
                                    QualityZone.Fair -> c.warn
                                    QualityZone.Poor -> c.danger
                                },
                            )
                        }
                    }
                },
                description = { d -> dayQuality[d]?.takeIf { it.analyzed > 0 }?.let { "膳食${it.zone.label}" } ?: "" },
                legend = {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(c.brand, "良好")
                        LegendDot(c.warn, "一般")
                        LegendDot(c.danger, "需改进")
                    }
                },
            )
        }
        if (loadedEntries != null && entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    Icons.Outlined.RamenDining,
                    if (day.isAfter(today)) "这天还没到" else "这天还没有记录",
                    "拍一张照片或者说一句「中午吃了一碗牛肉面」，AI 会帮你拆分并估算营养。",
                    modifier = itemMotion(),
                )
            }
        } else {
            item(key = "intake") {
                Intake(day, today, DayTotals.of(entries), targets)
            }
        }
        targets?.takeIf { it.needsProfile }?.let { t ->
            item(key = "profile") { ProfileHint(t, completeProfile, Modifier.padding(top = 14.dp)) }
        }
        item(key = "add") {
            AddButton(isPast = day.isBefore(today), Modifier.padding(top = 22.dp)) { openAdd(day) }
        }
        if (entries.isNotEmpty()) {
            item(key = "quality") {
                DietQualityCard(quality, weekAverage, yesterday, backfill, Modifier.padding(top = 24.dp))
            }
        }
        Meal.entries.forEach { meal ->
            val items = entries.filter { Meal.of(it.meal) == meal }
            if (items.isNotEmpty()) {
                item(key = "meal_${meal.key}") {
                    MealCard(meal, items, itemMotion().padding(top = 12.dp)) { editing = it }
                }
            }
        }
    }

    editing?.let { entry ->
        FoodEntrySheet(container, entry, onDismiss = { editing = null })
    }
}

/** 这天吃了多少（大数字）、还能吃多少，下面一条进度线和三大营养素 */
@Composable
private fun Intake(day: LocalDate, today: LocalDate, totals: DayTotals, targets: NutritionTargets?) {
    val c = Brand.colors
    val target = targets?.kcal
    val left = target?.let { it - totals.kcal }
    val over = left != null && left < 0
    Column(Modifier.padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(if (day == today) "今天吃了" else "${day.monthValue}月${day.dayOfMonth}日吃了", style = Brand.type.label, color = c.muted)
                Spacer(Modifier.height(4.dp))
                FigureNumber("${totals.kcal.roundToInt()}", "/ ${target ?: "—"} kcal", Brand.type.display)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (over) "超出" else "还能吃", style = Brand.type.label, color = c.muted)
                AnimatedNumber(left?.let { "${abs(it).roundToInt()}" } ?: "—", Brand.type.n2, if (over) c.warn else c.brand)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressLine(target?.let { (totals.kcal / it).toFloat() } ?: 0f, warnWhenFull = true)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            listOf(Triple("蛋白质", totals.proteinG, targets?.proteinG), Triple("脂肪", totals.fatG, targets?.fatG), Triple("碳水", totals.carbsG, targets?.carbsG))
                .forEach { (label, v, t) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = Brand.type.label, color = c.muted)
                        Spacer(Modifier.height(2.dp))
                        FigureNumber("${v.roundToInt()}", t?.let { "/${it}g" } ?: "g", Brand.type.n2, unitStyle = Brand.type.nSub2)
                        Spacer(Modifier.height(6.dp))
                        ProgressLine(t?.let { (v / it).toFloat() } ?: 0f, height = 3.dp, warnWhenFull = true)
                    }
                }
        }
    }
}

/** 记一餐：整页唯一的实心按钮 */
@Composable
private fun AddButton(isPast: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Brand.colors
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = c.brand,
        interactionSource = interaction,
        modifier = modifier.fillMaxWidth().pressScale(interaction),
    ) {
        Row(
            Modifier.padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (isPast) "补记这天" else "记一餐", style = Brand.type.title, color = c.onBrand)
            Spacer(Modifier.width(10.dp))
            Text("拍照 · 语音 · 打字", style = Brand.type.note, color = c.onBrand.copy(alpha = 0.8f))
        }
    }
}

/** 一餐一张卡片，点一条记录修改 */
@Composable
private fun MealCard(meal: Meal, items: List<FoodEntry>, modifier: Modifier, onEdit: (FoodEntry) -> Unit) {
    val c = Brand.colors
    BCard(modifier.animateContentSize(tween(io.github.sekirankumo.fitlog.ui.components.Motion.LONG))) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(meal.label, style = Brand.type.title, color = c.text, modifier = Modifier.weight(1f))
            Text("${items.sumOf { it.kcal }.fmt(0)} kcal", style = Brand.type.nSub1.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
        }
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { i, e ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(
                Modifier.fillMaxWidth().clickable { onEdit(e) }.padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.name, style = Brand.type.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (e.highSalt == true) {
                            Spacer(Modifier.width(6.dp))
                            HighSaltTag()
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            listOfNotNull(e.amount, e.grams?.let { "${it.fmt(0)}g" }).joinToString(" ").ifEmpty { null },
                            "蛋白 ${e.proteinG.fmt(0)} · 脂 ${e.fatG.fmt(0)} · 碳 ${e.carbsG.fmt(0)}",
                        ).joinToString(" · "),
                        style = Brand.type.note,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(e.kcal.fmt(0), style = Brand.type.n3, color = c.text, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** 修改一条已保存的饮食记录；删除后底部提示可以撤销 */
@Composable
private fun FoodEntrySheet(container: AppContainer, entry: FoodEntry, onDismiss: () -> Unit) {
    val toast = LocalToast.current
    val original = remember(entry.id) { FoodDraft.fromEntry(entry) }
    var draft by remember(entry.id) { mutableStateOf(original) }
    BSheet(
        onDismiss = onDismiss,
        title = "编辑饮食",
        actions = { s ->
            BButton("删除", Modifier.weight(0.8f), kind = ButtonKind.Danger) {
                s.close {
                    container.appScope.launch { container.database.foodDao().deleteById(entry.id) }
                    toast.show("已删除「${entry.name}」", "撤销") {
                        container.appScope.launch { container.database.foodDao().insertAll(listOf(entry)) }
                    }
                }
            }
            BButton("取消", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() }
            BButton("保存", Modifier.weight(1f), enabled = draft.name.isNotBlank()) {
                s.close {
                    container.appScope.launch {
                        // 改了食物名称而食材没动过：原来的拆分不再对应，清空后让后台重新分析
                        val stale = draft.name.trim() != entry.name && draft.components == original.components
                        val updated = draft.toEntry().copy(id = entry.id, source = entry.source, note = entry.note).let {
                            if (stale) it.copy(components = null, highSalt = null) else it
                        }
                        container.database.foodDao().update(updated)
                        if (draft.addToFavorites) container.diet.addFavorite(updated)
                    }
                }
            }
        },
    ) {
        FoodDraftEditor(draft, { draft = it })
    }
}
