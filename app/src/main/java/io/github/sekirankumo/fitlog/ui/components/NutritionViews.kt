package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.NutritionNotice
import io.github.sekirankumo.fitlog.ui.theme.Brand
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** 今日页上那条「AI 调整了营养」的提示：说明、今天变了多少、查看和撤销 */
@Composable
fun NutritionNoticeCard(notice: NutritionNotice, onView: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    BCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            AiOrb(28.dp, shadow = false)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("AI 调整了接下来 7 天的营养", style = Brand.type.title, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(notice.summary, style = Brand.type.label, color = c.muted)
                val diff = notice.kcalAfter - notice.kcalBefore
                if (diff != 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "今天 ${notice.kcalAfter} kcal（比原来${if (diff < 0) "少" else "多"} ${abs(diff)}）",
                        style = Brand.type.labelBold,
                        color = c.text,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BButton("查看", Modifier.weight(1f), kind = ButtonKind.Soft, height = 40.dp, onClick = onView)
            BButton("撤销", Modifier.weight(1f), kind = ButtonKind.Text, height = 40.dp, onClick = onUndo)
        }
    }
}

/** 接下来 7 天每天的营养目标和说明 */
@Composable
fun NutritionWeekSheet(container: AppContainer, onDismiss: () -> Unit) {
    val c = Brand.colors
    val today = remember { LocalDate.now() }
    val flow = remember { container.nutritionPlan.observeDays(today, 7) }
    val days by flow.collectAsState(initial = null)
    val auto by container.appPrefs.autoNutrition.collectAsState()
    BSheet(
        onDismiss = onDismiss,
        title = "接下来 7 天的营养",
        subtitle = if (auto) "每天第一次打开时，AI 会根据前几天的表现重新安排" else "没有单独安排的日子按默认目标",
        actions = { s -> BButton("知道了", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() } },
    ) {
        days?.forEachIndexed { i, d ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayLabel(d.date, today), style = Brand.type.bodyMedium, color = c.text)
                    Spacer(Modifier.height(3.dp))
                    Text(d.note ?: if (d.byAi) "AI 安排" else "默认目标", style = Brand.type.note, color = c.muted)
                }
                Column(horizontalAlignment = Alignment.End) {
                    FigureNumber("${d.macros.kcal}", "kcal", Brand.type.n3, unitStyle = Brand.type.nSub2, animate = false)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "蛋白 ${d.macros.proteinG} · 碳水 ${d.macros.carbsG} · 脂肪 ${d.macros.fatG}",
                        style = Brand.type.note,
                        color = c.muted,
                    )
                }
            }
        }
    }
}

private fun dayLabel(d: LocalDate, today: LocalDate): String {
    val name = when (d) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
    }
    return "$name · ${d.monthValue}/${d.dayOfMonth}"
}
