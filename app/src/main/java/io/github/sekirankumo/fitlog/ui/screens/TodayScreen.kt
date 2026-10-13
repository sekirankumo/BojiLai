package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.background.HealthSync
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.BarProgress
import io.github.sekirankumo.fitlog.ui.components.IconBadge
import io.github.sekirankumo.fitlog.ui.components.NutritionSummary
import io.github.sekirankumo.fitlog.ui.components.Pill
import io.github.sekirankumo.fitlog.ui.components.SectionHeader
import io.github.sekirankumo.fitlog.ui.components.StatTile
import io.github.sekirankumo.fitlog.ui.components.statusColor
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class TodayNav(
    val openTraining: () -> Unit,
    val openBody: () -> Unit,
    val openDiet: () -> Unit,
    val openSettings: () -> Unit,
    val askAi: (String) -> Unit,
)

private val QUICK_QUESTIONS = listOf(
    "今天还能吃点什么？",
    "这周练得怎么样？",
    "最近体重有变化吗？",
)

/** 今日：摄入、训练、身体数据一屏看完 */
@Composable
fun TodayScreen(container: AppContainer, nav: TodayNav) {
    val c = Brand.colors
    val today = LocalDate.now()
    val foodFlow = remember { container.diet.observeDay(today) }
    val food by foodFlow.collectAsState(initial = emptyList())
    val targets by container.profile.targets.collectAsState(initial = null)
    val dayFlow = remember { container.plans.observeDay(today) }
    val plan by dayFlow.collectAsState(initial = null)
    val bodyFlow = remember { container.database.bodyRecordDao().observeAll() }
    val records by bodyFlow.collectAsState(initial = emptyList())

    // 每次回到前台刷新步数
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val steps by produceState<HealthSync.Steps?>(null, refresh) { value = container.healthSync.todaySteps() }

    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text(greeting, style = MaterialTheme.typography.headlineLarge)
                Text(
                    today.format(DateTimeFormatter.ofPattern("M 月 d 日 · ")) + today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.muted,
                )
            }
        }

        item { NutritionSummary(DayTotals.of(food), targets, onClick = nav.openDiet) }

        item { TrainingCard(container, plan, nav.openTraining) }

        item {
            val latest = records.firstOrNull { it.weightKg != null }
            val weekAgo = records.firstOrNull { it.weightKg != null && it.measuredAt <= TimeUtil.daysAgo(7) }
            val delta = latest?.weightKg?.let { w -> weekAgo?.weightKg?.let { w - it } }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "体重",
                    value = latest?.weightKg?.fmt() ?: "—",
                    unit = "kg",
                    sub = delta?.let { "比一周前 ${if (it >= 0) "+" else ""}${it.fmt()}" } ?: "还没有记录",
                    subColor = if (delta != null && delta < 0) c.brand else c.muted,
                    modifier = Modifier.weight(1f),
                    onClick = nav.openBody,
                )
                StatTile(
                    label = "体脂率",
                    value = records.firstOrNull { it.bodyFatPct != null }?.bodyFatPct?.fmt() ?: "—",
                    unit = "%",
                    sub = latest?.let { TimeUtil.formatShortDate(it.measuredAt) } ?: " ",
                    modifier = Modifier.weight(1f),
                    onClick = nav.openBody,
                )
                val s = steps
                StatTile(
                    label = "今日步数",
                    value = (s as? HealthSync.Steps.Value)?.count?.toString() ?: "—",
                    sub = when (s) {
                        is HealthSync.Steps.Value -> s.sources.firstOrNull() ?: "Health Connect"
                        HealthSync.Steps.NotGranted -> "点此授权"
                        HealthSync.Steps.NoData -> "暂无数据"
                        HealthSync.Steps.Unavailable -> "手机不支持"
                        is HealthSync.Steps.Failed -> "读取失败"
                        null -> "读取中"
                    },
                    subColor = if (s is HealthSync.Steps.Value || s == null) c.muted else c.danger,
                    modifier = Modifier.weight(1f),
                    onClick = if (s is HealthSync.Steps.Value) nav.openBody else nav.openSettings,
                )
            }
        }

        item {
            BCard(onClick = { nav.askAi("") }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.AutoAwesome, tint = c.onBrand, background = c.brand)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("问问 AI", style = MaterialTheme.typography.titleMedium)
                        Text("记饮食、打卡、改计划，说一句就行", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_QUESTIONS.forEach { q ->
                        AssistChip(
                            onClick = { nav.askAi(q) },
                            label = { Text(q) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = c.surface2, labelColor = c.text),
                            border = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingCard(container: AppContainer, plan: io.github.sekirankumo.fitlog.data.db.PlanDay?, open: () -> Unit) {
    val c = Brand.colors
    BCard(onClick = open) {
        SectionHeader("今日训练") {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
        Spacer(Modifier.height(10.dp))
        if (plan == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.SelfImprovement)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("今天休息", style = MaterialTheme.typography.titleMedium)
                    Text("好好恢复，肌肉是在休息时长出来的", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            return@BCard
        }
        val exercises = container.plans.exercisesOf(plan)
        val done = exercises.sumOf { ex -> ex.sets.count { it.done } }
        val total = exercises.sumOf { it.sets.size }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.FitnessCenter)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plan.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    exercises.joinToString("、") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            val sc = statusColor(plan.status)
            Pill(DayStatus.label(plan.status), color = sc, background = sc.copy(alpha = 0.14f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BarProgress(if (total == 0) 0f else done.toFloat() / total, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text("$done / $total 组", style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
    }
}

