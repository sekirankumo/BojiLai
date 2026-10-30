package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import io.github.sekirankumo.fitlog.platform.rememberPickers
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.diet.FoodComponent
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.data.diet.FoodGroup
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt

/**
 * 编辑一项食物：名称、份量、克数（改克数时营养按比例换算）、营养、餐次和时间、
 * 是否加入常吃、是否高盐，以及可展开的食材分类（影响膳食分）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodDraftEditor(
    draft: FoodDraft,
    onChange: (FoodDraft) -> Unit,
    showFavorite: Boolean = true,
    /** 是否显示餐次和时间（编辑常吃时不需要） */
    showMealTime: Boolean = true,
) {
    val c = Brand.colors
    val pickers = rememberPickers()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(draft.name, { onChange(draft.copy(name = it)) }, Modifier.weight(1.4f), label = "食物", placeholder = "新食物")
            Field(draft.amount ?: "", { onChange(draft.copy(amount = it)) }, Modifier.weight(1f), label = "份量")
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
        if (showMealTime) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Meal.entries.forEach { m ->
                    val on = draft.meal == m
                    val bg by animateColorAsState(if (on) c.brandSoft else Color.Transparent, tween(Motion.SHORT), label = "meal")
                    Text(
                        m.label,
                        style = if (on) Brand.type.labelBold else Brand.type.label,
                        color = if (on) c.brand else c.text,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(bg)
                            .then(if (on) Modifier else Modifier.border(1.dp, c.line, CircleShape))
                            .clickable(role = Role.RadioButton) { onChange(draft.copy(meal = m)) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                Text(
                    TimeUtil.formatShort(draft.eatenAt),
                    style = Brand.type.nSub1,
                    color = c.text,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(softFill())
                        .clickable { pickers.pickDateTime(draft.eatenAt) { onChange(draft.copy(eatenAt = it)) } }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (showFavorite) CheckItem(draft.addToFavorites, { onChange(draft.copy(addToFavorites = it)) }, "加入常吃")
            // AI 判断错了可以手动改；只做提醒，不影响膳食分
            CheckItem(draft.highSalt, { onChange(draft.copy(highSalt = it)) }, "高盐")
            Spacer(Modifier.weight(1f))
            if (showFavorite) {
                draft.confidence?.let {
                    Text("AI 把握：$it", style = Brand.type.note, color = if (it == "低") c.danger else c.muted)
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
        Text("食材分类：保存后自动分析", style = Brand.type.note, color = c.muted, modifier = Modifier.padding(start = 4.dp))
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(Motion.MEDIUM), label = "chevron")
    Column(Modifier.animateContentSize(tween(Motion.MEDIUM, easing = Motion.Emphasized))) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(softFill())
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("食材分类", style = Brand.type.label, color = c.text)
            Text(
                "  " + components.joinToString("、") { "${it.name}${it.grams.fmt(0)}g" }.ifEmpty { "无计分食材" },
                style = Brand.type.label,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                AppIcons.ChevronDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = c.muted,
                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = turn },
            )
        }
        if (expanded) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                components.forEachIndexed { i, comp ->
                    key(i, comp.name) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(comp.name, style = Brand.type.label, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                            GroupPicker(comp.group) { g -> onChange(components.toMutableList().also { it[i] = comp.copy(group = g) }) }
                            NumberField(comp.grams, { v -> onChange(components.toMutableList().also { it[i] = comp.copy(grams = v ?: 0.0) }) }, "", Modifier.width(76.dp))
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).clickable { onChange(components.filterIndexed { j, _ -> j != i }) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(AppIcons.Close, contentDescription = "删掉${comp.name}", tint = c.muted, modifier = Modifier.size(16.dp))
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
        Text(
            FoodGroup.of(key)?.short ?: "不计分",
            style = Brand.type.noteBold,
            color = c.text,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(softFill())
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 9.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = c.surface) {
            FoodGroup.entries.forEach { g ->
                DropdownMenuItem(text = { Text(g.label, style = Brand.type.label) }, onClick = {
                    open = false
                    onPick(g.key)
                })
            }
            DropdownMenuItem(text = { Text("不计分（调料、酒、咖啡茶等）", style = Brand.type.label) }, onClick = {
                open = false
                onPick(FoodGroup.NONE)
            })
        }
    }
}
