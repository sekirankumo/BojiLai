package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.data.diet.DietRepository
import io.github.sekirankumo.fitlog.data.stats.MuscleState
import io.github.sekirankumo.fitlog.data.stats.RecoveryFactors
import io.github.sekirankumo.fitlog.data.stats.TrainingStats
import io.github.sekirankumo.fitlog.ui.theme.Brand
import java.time.LocalDate

/** 由训练记录算出的统计：各部位恢复状态、每天完成组数 */
class TrainingData(
    val muscles: Map<String, MuscleState>,
    val daily: Map<LocalDate, Int>,
) {
    val intensities: Map<String, Float> get() = muscles.mapValues { it.value.intensity }
}

/**
 * 读取最近一年的训练记录并计算统计，记录变化时自动更新。
 * 恢复速度会参考年龄和最近两天的饮食（热量、蛋白质）。
 */
@Composable
fun rememberTrainingData(container: AppContainer): TrainingData {
    val today = LocalDate.now()
    val planFlow = remember { container.plans.observeRange(today.minusWeeks(53), today) }
    val days by planFlow.collectAsState(initial = emptyList())
    // 饮食只看昨天一整天（今天还没吃完，不能算缺口）
    val foodFlow = remember {
        container.database.foodDao().observeRange(DietRepository.dayStart(today.minusDays(1)), DietRepository.dayStart(today))
    }
    val food by foodFlow.collectAsState(initial = emptyList())
    val profile by container.profile.profile.collectAsState(initial = UserProfile())
    val targets by container.profile.targets.collectAsState(initial = null)
    val weightFlow = remember { container.database.bodyRecordDao().observeLatest() }
    val latest by weightFlow.collectAsState(initial = null)

    return remember(days, food, profile, targets, latest) {
        val now = System.currentTimeMillis()
        val plans = container.plans
        // 昨天有饮食记录才参与计算
        val hasFood = food.isNotEmpty()
        val factors = RecoveryFactors(
            age = profile.age(),
            calorieRatio = targets?.kcal?.takeIf { hasFood }?.let { t -> food.sumOf { it.kcal } / t },
            proteinPerKg = latest?.weightKg?.takeIf { hasFood }?.let { w -> food.sumOf { it.proteinG } / w },
        )
        val loads = TrainingStats.sessionLoads(
            days,
            exercisesOf = { plans.exercisesOf(it) },
            lookup = { container.exercises.resolve(it.exerciseId, it.name) },
            now = now,
        )
        TrainingData(
            muscles = TrainingStats.muscleStates(loads, now, factors),
            daily = TrainingStats.dailySets(days) { plans.exercisesOf(it) },
        )
    }
}

/**
 * 身体状态：左边是正面、背面肌肉图（刚练过颜色深，随恢复慢慢变浅），右边是体重和体脂，
 * 下面列出还在恢复的部位和大概还要多久。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyStatusCard(
    muscles: Map<String, MuscleState>,
    female: Boolean,
    weight: String,
    weightSub: String?,
    weightSubGood: Boolean,
    fat: String,
    fatSub: String?,
    fatSubGood: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Brand.colors
    val intensities = remember(muscles) { muscles.mapValues { it.value.intensity } }
    BCard(modifier.fillMaxWidth(), onClick = onClick) {
        SectionHeader("身体状态") {
            if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1.25f)) {
                MuscleFigure(BodyView.front(female), intensities, Modifier.weight(1f).height(196.dp))
                MuscleFigure(BodyView.back(female), intensities, Modifier.weight(1f).height(196.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(0.85f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric("体重", weight, "kg", weightSub, weightSubGood)
                HorizontalDivider(color = c.line)
                Metric("体脂率", fat, "%", fatSub, fatSubGood)
            }
        }
        Spacer(Modifier.height(10.dp))
        // 同一个中文名的部位（如腹肌、腹斜肌各自独立）按剩余时间最长的显示
        val recovering = muscles.entries
            .filter { it.value.intensity >= 0.05f }
            .mapNotNull { e -> TrainingStats.SLUG_LABELS[e.key]?.let { it to e.value.hoursLeft } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
            .entries.sortedByDescending { it.value }
            .take(6)
        Text(
            if (recovering.isEmpty()) "全身都已恢复" else "正在恢复",
            style = MaterialTheme.typography.labelMedium,
            color = c.muted,
        )
        if (recovering.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recovering.forEach { (label, _) -> Pill(label) }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, sub: String?, good: Boolean) {
    val c = Brand.colors
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumber(value, style = MaterialTheme.typography.headlineSmall, color = c.text, fontWeight = FontWeight.Bold)
            Text(" $unit", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        sub?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = if (good) c.brand else c.muted, maxLines = 1)
        }
    }
}
