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
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.util.TimeUtil

/** 编辑一条食物：名称、份量、克数（改克数时营养按比例缩放）、营养素、餐次、时间 */
@Composable
fun FoodDraftEditor(
    draft: FoodDraft,
    onChange: (FoodDraft) -> Unit,
    showFavorite: Boolean = true,
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
        Row(
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
        if (showFavorite) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.addToFavorites, onCheckedChange = { onChange(draft.copy(addToFavorites = it)) })
                Text("加入常吃", style = MaterialTheme.typography.bodyMedium)
                draft.confidence?.let {
                    Text(
                        "　AI 把握：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it == "低") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
