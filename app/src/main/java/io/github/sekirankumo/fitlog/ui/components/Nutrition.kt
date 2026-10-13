package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.data.NutritionTargets
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlin.math.abs
import kotlin.math.roundToInt

/** 一天的热量环 + 三大营养素进度 */
@Composable
fun NutritionSummary(
    totals: DayTotals,
    targets: NutritionTargets?,
    modifier: Modifier = Modifier,
    title: String = "今日摄入",
    onClick: (() -> Unit)? = null,
) {
    val c = Brand.colors
    val target = targets?.kcal
    val left = target?.let { it - totals.kcal }
    val over = left != null && left < 0
    BCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = target?.let { (totals.kcal / it).toFloat() } ?: 0f,
                size = 116.dp,
                stroke = 12.dp,
                color = if (over) c.danger else c.brand,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (over) "超出" else "还能吃",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.muted,
                    )
                    Text(
                        left?.let { abs(it).roundToInt().toString() } ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (over) c.danger else c.text,
                    )
                    Text("kcal", style = MaterialTheme.typography.labelSmall, color = c.muted)
                }
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(title, style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${totals.kcal.roundToInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                        Text(
                            " / ${target ?: "—"} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                            modifier = Modifier.padding(bottom = 5.dp),
                        )
                    }
                }
                MacroProgress("蛋白质", totals.proteinG, targets?.proteinG, c.protein)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MacroProgress("脂肪", totals.fatG, targets?.fatG, c.fat, Modifier.weight(1f))
            MacroProgress("碳水", totals.carbsG, targets?.carbsG, c.carbs, Modifier.weight(1f))
        }
        if (targets != null && !targets.computed) {
            Spacer(Modifier.height(10.dp))
            Text(
                "在 设置 → 个人信息 填写身高、年龄等，目标会更准确",
                style = MaterialTheme.typography.bodySmall,
                color = c.muted,
            )
        }
    }
}

