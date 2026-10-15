package io.github.sekirankumo.fitlog.ui.screens

import io.github.sekirankumo.fitlog.data.diet.DayQuality
import io.github.sekirankumo.fitlog.ui.components.BottomBarSpace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.DayStatus
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.ui.components.ActivityHeatmap
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.BarProgress
import io.github.sekirankumo.fitlog.ui.components.BodyStatusCard
import io.github.sekirankumo.fitlog.ui.components.IconBadge
import io.github.sekirankumo.fitlog.ui.components.NutritionSummary
import io.github.sekirankumo.fitlog.ui.components.Pill
import io.github.sekirankumo.fitlog.ui.components.SectionHeader
import io.github.sekirankumo.fitlog.ui.components.TrainingData
import io.github.sekirankumo.fitlog.ui.components.rememberTrainingData
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
    /** 补全个人信息；只差体重记录时去身体页 */
    val completeProfile: (missing: List<String>) -> Unit,
)

/** 今日：摄入、训练（热力图 + 今日安排）、身体状态（肌肉图 + 体重体脂） */
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
    val profile by container.profile.profile.collectAsState(initial = UserProfile())
    val training = rememberTrainingData(container)

    val greeting = when (LocalTime.now().hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp + BottomBarSpace),
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

        item {
            NutritionSummary(
                DayTotals.of(food),
                targets,
                onClick = nav.openDiet,
                onCompleteProfile = nav.completeProfile,
                showQuality = true,
                quality = remember(food) { DayQuality.of(food) },
            )
        }

        item { TrainingCard(container, plan, training, nav.openTraining) }

        item { TodayBodyStatus(records, training, profile.sex == "female", nav.openBody) }
    }
}

@Composable
private fun TrainingCard(container: AppContainer, plan: PlanDay?, training: TrainingData, open: () -> Unit) {
    val c = Brand.colors
    BCard(onClick = open) {
        SectionHeader("训练") {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
        Spacer(Modifier.height(10.dp))
        ActivityHeatmap(training.daily)
        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = c.line)

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
                Text("今日 · ${plan.title}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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



/** 身体状态卡片：今日页上体重变化和一周前比 */
@Composable
private fun TodayBodyStatus(records: List<BodyRecord>, training: TrainingData, female: Boolean, open: () -> Unit) {
    val latestW = records.firstOrNull { it.weightKg != null }
    val weekAgoW = records.firstOrNull { it.weightKg != null && it.measuredAt <= TimeUtil.daysAgo(7) }
    val wDelta = latestW?.weightKg?.let { w -> weekAgoW?.weightKg?.let { w - it } }
    val latestF = records.firstOrNull { it.bodyFatPct != null }
    val weekAgoF = records.firstOrNull { it.bodyFatPct != null && it.measuredAt <= TimeUtil.daysAgo(7) }
    val fDelta = latestF?.bodyFatPct?.let { f -> weekAgoF?.bodyFatPct?.let { f - it } }
    fun sign(d: Double) = if (d >= 0) "+" else ""
    BodyStatusCard(
        muscles = training.muscles,
        female = female,
        weight = latestW?.weightKg?.fmt() ?: "—",
        weightSub = wDelta?.let { "比一周前 ${sign(it)}${it.fmt()}" } ?: if (latestW == null) "还没有记录" else null,
        weightSubGood = wDelta != null && wDelta < 0,
        fat = latestF?.bodyFatPct?.fmt() ?: "—",
        fatSub = fDelta?.let { "比一周前 ${sign(it)}${it.fmt()}" },
        fatSubGood = fDelta != null && fDelta < 0,
        onClick = open,
    )
}
