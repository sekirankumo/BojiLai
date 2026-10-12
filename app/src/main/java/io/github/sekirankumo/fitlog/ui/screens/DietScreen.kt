package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.NutritionTargets
import io.github.sekirankumo.fitlog.data.db.FoodEntry
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.ui.components.FoodDraftEditor
import io.github.sekirankumo.fitlog.ui.components.pickDate
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 打开添加饮食页面：mode = camera / gallery / voice / text */
typealias OpenAddFood = (mode: String, day: LocalDate) -> Unit

@Composable
fun DietScreen(container: AppContainer, openAdd: OpenAddFood) {
    var dayEpoch by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    val day = LocalDate.ofEpochDay(dayEpoch)
    val entriesFlow = remember(dayEpoch) { container.diet.observeDay(day) }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val targets by container.profile.targets.collectAsState(initial = null)
    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    val context = LocalContext.current

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DayBar(
                day = day,
                onPrev = { dayEpoch-- },
                onNext = { dayEpoch++ },
                onPick = { pickDate(context, day) { dayEpoch = it.toEpochDay() } },
            )
        }
        item { NutritionCard(DayTotals.of(entries), targets) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AddButton(Icons.Filled.CameraAlt, "拍照", Modifier.weight(1f)) { openAdd("camera", day) }
                AddButton(Icons.Filled.PhotoLibrary, "相册", Modifier.weight(1f)) { openAdd("gallery", day) }
                AddButton(Icons.Filled.Mic, "语音", Modifier.weight(1f)) { openAdd("voice", day) }
                AddButton(Icons.Filled.Edit, "文字", Modifier.weight(1f)) { openAdd("text", day) }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "这天还没有记录。拍张照片、说一句话，或者打字描述吃了什么，AI 会帮你估算热量。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Meal.entries.forEach { meal ->
            val items = entries.filter { Meal.of(it.meal) == meal }
            if (items.isNotEmpty()) {
                item(key = "meal_${meal.key}") {
                    Card {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "${meal.label} · ${items.sumOf { it.kcal }.fmt(0)} kcal",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                            items.forEach { e ->
                                ListItem(
                                    modifier = Modifier.clickable { editing = e },
                                    headlineContent = { Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        Text(
                                            listOfNotNull(
                                                e.amount,
                                                e.grams?.let { "${it.fmt(0)}g" },
                                                "蛋白${e.proteinG.fmt(0)} 脂${e.fatG.fmt(0)} 碳${e.carbsG.fmt(0)}",
                                            ).joinToString(" · ")
                                        )
                                    },
                                    trailingContent = { Text("${e.kcal.fmt(0)} kcal") },
                                )
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

@Composable
fun DayBar(day: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    val today = LocalDate.now()
    val label = when (day) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.plusDays(1) -> "明天"
        else -> day.format(DateTimeFormatter.ofPattern("M月d日"))
    } + " " + day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "前一天") }
        TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "后一天") }
    }
}

@Composable
private fun AddButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun NutritionCard(totals: DayTotals, targets: NutritionTargets?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${totals.kcal.fmt(0)}", style = MaterialTheme.typography.headlineMedium)
                Text(
                    " / ${targets?.kcal ?: "—"} kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                targets?.let {
                    val left = it.kcal - totals.kcal
                    Text(
                        if (left >= 0) "还可以吃 ${left.fmt(0)}" else "超出 ${(-left).fmt(0)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { targets?.let { (totals.kcal / it.kcal).toFloat().coerceIn(0f, 1f) } ?: 0f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MacroBar("蛋白质", totals.proteinG, targets?.proteinG, Modifier.weight(1f))
                MacroBar("脂肪", totals.fatG, targets?.fatG, Modifier.weight(1f))
                MacroBar("碳水", totals.carbsG, targets?.carbsG, Modifier.weight(1f))
            }
            if (targets != null && !targets.computed) {
                Text(
                    "在 设置 → 个人信息 填写身高、年龄等，可以算出更准确的目标",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MacroBar(label: String, value: Double, target: Int?, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text("${value.fmt(0)} / ${target ?: "—"} g", style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(
            progress = { target?.let { (value / it).toFloat().coerceIn(0f, 1f) } ?: 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
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
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

