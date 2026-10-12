package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class TodayNav(
    val addFood: () -> Unit,
    val openTraining: () -> Unit,
    val openBody: () -> Unit,
    val openDiet: () -> Unit,
)

/** 今日：摄入、训练、体重和步数一屏看完 */
@Composable
fun TodayScreen(container: AppContainer, nav: TodayNav) {
    val today = LocalDate.now()
    val foodFlow = remember { container.diet.observeDay(today) }
    val food by foodFlow.collectAsState(initial = emptyList())
    val targets by container.profile.targets.collectAsState(initial = null)
    val dayFlow = remember { container.plans.observeDay(today) }
    val plan by dayFlow.collectAsState(initial = null)
    val bodyFlow = remember { container.database.bodyRecordDao().observeAll() }
    val records by bodyFlow.collectAsState(initial = emptyList())
    val steps by produceState<Long?>(null) { value = container.healthSync.todaySteps() }

    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(greeting, style = MaterialTheme.typography.headlineSmall)
                Text(
                    today.format(DateTimeFormatter.ofPattern("M月d日 ")) + today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // 饮食
        item {
            Column(Modifier.clickable(onClick = nav.openDiet)) {
                NutritionCard(DayTotals.of(food), targets)
            }
        }

        // 训练
        item {
            Card(Modifier.fillMaxWidth().clickable(onClick = nav.openTraining)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("  今日训练", style = MaterialTheme.typography.titleSmall)
                    }
                    val p = plan
                    if (p == null) {
                        Text("今天休息，好好恢复。", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        val exercises = container.plans.exercisesOf(p)
                        val done = exercises.sumOf { ex -> ex.sets.count { it.done } }
                        val total = exercises.sumOf { it.sets.size }
                        Text("${p.title} · ${DayStatus.label(p.status)}", style = MaterialTheme.typography.titleMedium)
                        Text(
                            exercises.joinToString("、") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(
                            progress = { if (total == 0) 0f else done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("$done / $total 组", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // 身体
        item {
            val latest = records.firstOrNull { it.weightKg != null }
            val weekAgo = records.firstOrNull { it.weightKg != null && it.measuredAt <= TimeUtil.daysAgo(7) }
            Card(Modifier.fillMaxWidth().clickable(onClick = nav.openBody)) {
                Row(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("体重", style = MaterialTheme.typography.labelMedium)
                        Text(latest?.weightKg?.let { "${it.fmt()} kg" } ?: "—", style = MaterialTheme.typography.titleLarge)
                        val delta = latest?.weightKg?.let { w -> weekAgo?.weightKg?.let { w - it } }
                        Text(
                            delta?.let { "比一周前 ${if (it >= 0) "+" else ""}${it.fmt()} kg" } ?: (latest?.let { TimeUtil.formatShort(it.measuredAt) } ?: "还没有记录"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("体脂率", style = MaterialTheme.typography.labelMedium)
                        Text(
                            records.firstOrNull { it.bodyFatPct != null }?.bodyFatPct?.let { "${it.fmt()}%" } ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text("今日步数", style = MaterialTheme.typography.labelMedium)
                        Text(steps?.toString() ?: "—", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }

        // 快捷操作
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = nav.addFood, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" 拍照记饮食")
                }
                FilledTonalButton(onClick = nav.openBody, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.MonitorWeight, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" 记体重")
                }
            }
        }
    }
}
