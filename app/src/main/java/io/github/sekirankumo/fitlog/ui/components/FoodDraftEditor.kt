package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.diet.FoodComponent
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.data.diet.FoodGroup
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.fmt
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import io.github.sekirankumo.fitlog.util.TimeUtil

/** 编辑一条食物：名称、份量、克数（改克数时营养按比例缩放）、营养素、餐次、时间 */
@Composable
fun FoodDraftEditor(
    draft: FoodDraft,
    onChange: (FoodDraft) -> Unit,
    showFavorite: Boolean = true,
    /** 是否显示餐次和时间（编辑常吃时不需要） */
    showMealTime: Boolean = true,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it)) },
                label = { Text("食物") },
                singleLine = true,
                modifier = Modifier.weight(1.4f),
            )
            OutlinedTextField(
                value = draft.amount ?: "",
                onValueChange = { onChange(draft.copy(amount = it)) },
                label = { Text("份量") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(draft.grams, { onChange(draft.withGrams(it)) }, "克数", Modifier.weight(1f))
            NumberField(draft.kcal, { onChange(draft.withNutrients(kcal = it ?: 0.0)) }, "热量 kcal", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(draft.proteinG, { onChange(draft.withNutrients(proteinG = it ?: 0.0)) }, "蛋白 g", Modifier.weight(1f))
            NumberField(draft.fatG, { onChange(draft.withNutrients(fatG = it ?: 0.0)) }, "脂肪 g", Modifier.weight(1f))
            NumberField(draft.carbsG, { onChange(draft.withNutrients(carbsG = it ?: 0.0)) }, "碳水 g", Modifier.weight(1f))
        }
        if (showMealTime) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Meal.entries.forEach { m ->
                FilterChip(
                    selected = draft.meal == m,
                    onClick = { onChange(draft.copy(meal = m)) },
                    label = { Text(m.label) },
                )
            }
            OutlinedButton(onClick = { pickDateTime(context, draft.eatenAt) { onChange(draft.copy(eatenAt = it)) } }) {
                Text(TimeUtil.formatShort(draft.eatenAt))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showFavorite) {
                Checkbox(checked = draft.addToFavorites, onCheckedChange = { onChange(draft.copy(addToFavorites = it)) })
                Text("加入常吃", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
            }
            // AI 判断错了可以手动改；只做提醒，不影响膳食分
            Checkbox(checked = draft.highSalt, onCheckedChange = { onChange(draft.copy(highSalt = it)) })
            Text("高盐", style = MaterialTheme.typography.bodyMedium)
            if (showFavorite) {
                draft.confidence?.let {
                    Text(
                        "　AI 把握：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it == "低") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ComponentsEditor(draft.components) { onChange(draft.copy(components = it)) }
    }
}

/** 食材和膳食分类：默认收起，展开后可以改分类、克数或删掉某样 */
@Composable
private fun ComponentsEditor(components: List<FoodComponent>?, onChange: (List<FoodComponent>) -> Unit) {
    val c = Brand.colors
    if (components == null) {
        Text("食材分类：保存后自动分析", style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(start = 12.dp))
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.animateContentSize(tween(Motion.MEDIUM, easing = Motion.Emphasized))) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { expanded = !expanded }.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("食材分类", style = MaterialTheme.typography.bodyMedium)
            Text(
                "  " + components.joinToString("、") { "${it.name}${it.grams.fmt(0)}g" }.ifEmpty { "无计分食材" },
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "收起" else "展开", tint = c.muted)
        }
        if (expanded) {
            Column(Modifier.padding(start = 12.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                components.forEachIndexed { i, comp ->
                    key(i, comp.name) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(comp.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            GroupPicker(comp.group) { g -> onChange(components.toMutableList().also { it[i] = comp.copy(group = g) }) }
                            NumberField(comp.grams, { v -> onChange(components.toMutableList().also { it[i] = comp.copy(grams = v ?: 0.0) }) }, "克", Modifier.width(76.dp))
                            IconButton(onClick = { onChange(components.filterIndexed { j, _ -> j != i }) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "删掉${comp.name}", tint = c.muted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 选膳食分类的小按钮 */
@Composable
private fun GroupPicker(key: String, onPick: (String) -> Unit) {
    val c = Brand.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { open = true }, shape = RoundedCornerShape(8.dp), color = c.surface2) {
            Text(
                FoodGroup.of(key)?.short ?: "不计分",
                style = MaterialTheme.typography.labelMedium,
                color = c.text,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            FoodGroup.entries.forEach { g ->
                DropdownMenuItem(text = { Text(g.label) }, onClick = {
                    open = false
                    onPick(g.key)
                })
            }
            DropdownMenuItem(text = { Text("不计分（调料、酒、咖啡茶等）") }, onClick = {
                open = false
                onPick(FoodGroup.NONE)
            })
        }
    }
}
