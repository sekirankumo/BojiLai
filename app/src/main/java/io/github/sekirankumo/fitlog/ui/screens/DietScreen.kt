package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import io.github.sekirankumo.fitlog.ui.components.Motion
import io.github.sekirankumo.fitlog.ui.components.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.EmptyState
import io.github.sekirankumo.fitlog.ui.components.FoodDraftEditor
import io.github.sekirankumo.fitlog.ui.components.IconBadge
import io.github.sekirankumo.fitlog.ui.components.NutritionSummary
import io.github.sekirankumo.fitlog.ui.components.pickDate
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun Meal.icon(): ImageVector = when (this) {
    Meal.Breakfast -> Icons.Filled.BakeryDining
    Meal.Lunch -> Icons.Filled.LunchDining
    Meal.Dinner -> Icons.Filled.DinnerDining
    Meal.Snack -> Icons.Filled.Cookie
}

@Composable
fun DietScreen(container: AppContainer, completeProfile: (List<String>) -> Unit, openAdd: (LocalDate) -> Unit) {
    val c = Brand.colors
    var dayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch)
    val entriesFlow = remember(dayEpoch) { container.diet.observeDay(day) }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val targets by container.profile.targets.collectAsState(initial = null)
    var editing by remember { mutableStateOf<FoodEntry?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { WeekStrip(day) { dayEpoch = it.toEpochDay() } }
        item {
            NutritionSummary(
                DayTotals.of(entries),
                targets,
                title = if (day == LocalDate.now()) "今日摄入" else day.format(DateTimeFormatter.ofPattern("M月d日摄入")),
                onCompleteProfile = completeProfile,
            )
        }
        item {
            val addInteraction = remember { MutableInteractionSource() }
            Surface(
                onClick = { openAdd(day) },
                shape = RoundedCornerShape(20.dp),
                color = c.brand,
                interactionSource = addInteraction,
                modifier = Modifier.fillMaxWidth().pressScale(addInteraction),
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.CameraAlt, tint = c.brand, background = c.onBrand, size = 44.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("记一餐", style = MaterialTheme.typography.titleLarge, color = c.onBrand)
                        Text("拍照、语音或打字，AI 帮你算热量", style = MaterialTheme.typography.bodySmall, color = c.onBrand.copy(alpha = 0.85f))
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.onBrand)
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.Restaurant,
                    "这天还没有记录",
                    "拍一张照片或者说一句「中午吃了一碗牛肉面」，AI 会帮你拆分并估算营养。",
                )
            }
        }
        Meal.entries.forEach { meal ->
            val items = entries.filter { Meal.of(it.meal) == meal }
            if (items.isNotEmpty()) {
                item(key = "meal_${meal.key}") {
                    BCard(
                        Modifier.animateItem().animateContentSize(tween(Motion.LONG, easing = Motion.Emphasized)),
                        padding = PaddingValues(vertical = 8.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconBadge(meal.icon(), size = 32.dp, corner = 10.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(meal.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${items.sumOf { it.kcal }.fmt(0)} kcal", style = MaterialTheme.typography.titleSmall, color = c.muted)
                        }
                        items.forEachIndexed { i, e ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 58.dp, end = 16.dp), color = c.line)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { editing = e }
                                    .padding(start = 58.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOfNotNull(
                                            e.amount,
                                            e.grams?.let { "${it.fmt(0)}g" },
                                            "蛋白 ${e.proteinG.fmt(0)} · 脂 ${e.fatG.fmt(0)} · 碳 ${e.carbsG.fmt(0)}",
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(e.kcal.fmt(0), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        FoodEntryDialog(container, entry, onDismiss = { editing = null })
    }
}

/** 一周的日期条：点日期切换，左右箭头换周，点月份选任意日期 */
@Composable
fun WeekStrip(selected: LocalDate, onSelect: (LocalDate) -> Unit) {
    val c = Brand.colors
    val context = LocalContext.current
    val monday = selected.minusDays((selected.dayOfWeek.value - 1).toLong())
    val today = LocalDate.now()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pickDate(context, selected, onSelect) }) {
                Text(selected.format(DateTimeFormatter.ofPattern("yyyy 年 M 月")), style = MaterialTheme.typography.titleMedium, color = c.text)
            }
            Spacer(Modifier.weight(1f))
            if (selected != today) {
                TextButton(onClick = { onSelect(today) }) { Text("回到今天") }
            }
            IconButton(onClick = { onSelect(selected.minusWeeks(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
            }
            IconButton(onClick = { onSelect(selected.plusWeeks(1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0..6) {
                val d = monday.plusDays(i.toLong())
                val isSel = d == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSel) c.brand else c.surface)
                        .clickable { onSelect(d) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA).takeLast(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) c.onBrand.copy(alpha = 0.8f) else c.muted,
                    )
                    Text(
                        "${d.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSel) c.onBrand else if (d == today) c.brand else c.text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(
                        Modifier.padding(top = 2.dp).size(4.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (d == today && !isSel) c.brand else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
    }
}

/** 编辑一条已保存的饮食记录 */
@Composable
private fun FoodEntryDialog(container: AppContainer, entry: FoodEntry, onDismiss: () -> Unit) {
    var draft by remember(entry.id) {
        mutableStateOf(
            FoodDraft(
                key = entry.id,
                name = entry.name,
                amount = entry.amount,
                grams = entry.grams,
                kcal = entry.kcal,
                proteinG = entry.proteinG,
                fatG = entry.fatG,
                carbsG = entry.carbsG,
                meal = Meal.of(entry.meal),
                eatenAt = entry.eatenAt,
                source = entry.source,
            )
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Brand.colors.surface,
        title = { Text("编辑饮食") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FoodDraftEditor(draft, { draft = it })
            }
        },
        confirmButton = {
            TextButton(enabled = draft.name.isNotBlank(), onClick = {
                container.appScope.launch {
                    val updated = draft.toEntry().copy(id = entry.id, source = entry.source, note = entry.note)
                    container.database.foodDao().update(updated)
                    if (draft.addToFavorites) container.diet.addFavorite(updated)
                }
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    container.appScope.launch { container.database.foodDao().deleteById(entry.id) }
                    onDismiss()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

