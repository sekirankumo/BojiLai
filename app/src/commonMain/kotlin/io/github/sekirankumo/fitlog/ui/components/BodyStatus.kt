package io.github.sekirankumo.fitlog.ui.components

import kotlinx.datetime.*
import io.github.sekirankumo.fitlog.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.data.diet.DietRepository
import io.github.sekirankumo.fitlog.data.stats.MuscleState
import io.github.sekirankumo.fitlog.data.stats.RecoveryFactors
import io.github.sekirankumo.fitlog.data.stats.TrainingStats
import io.github.sekirankumo.fitlog.ui.theme.Brand

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
    val targetFlow = remember { container.nutritionPlan.targetFor(today.minusDays(1)) }
    val targets by targetFlow.collectAsState(initial = null)
    val weightFlow = remember { container.database.bodyRecordDao().observeLatest() }
    val latest by weightFlow.collectAsState(initial = null)

    return remember(days, food, profile, targets, latest) {
        val now = nowMillis()
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

/** 正面、背面两个肌肉图并排 */
@Composable
fun MuscleFigures(muscles: Map<String, MuscleState>, female: Boolean, height: Dp, modifier: Modifier = Modifier, gap: Dp = 4.dp) {
    val intensities = remember(muscles) { muscles.mapValues { it.value.intensity } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        MuscleFigure(BodyView.front(female), intensities, Modifier.weight(1f).height(height))
        MuscleFigure(BodyView.back(female), intensities, Modifier.weight(1f).height(height))
    }
}

/** 「正在恢复」和还在恢复的部位；同一个中文名的部位按剩余时间最长的算 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecoveringChips(muscles: Map<String, MuscleState>, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val recovering = remember(muscles) {
        muscles.entries
            .filter { it.value.intensity >= 0.05f }
            .mapNotNull { e -> TrainingStats.SLUG_LABELS[e.key]?.let { it to e.value.hoursLeft } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
            .entries.sortedByDescending { it.value }
            .take(6)
            .map { it.key }
    }
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (recovering.isEmpty()) "全身都已恢复" else "正在恢复",
            style = Brand.type.label,
            color = c.muted,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        recovering.forEach { Tag(it) }
    }
}

/** 体重小折线：最近几次记录，最后一点加粗 */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val c = Brand.colors
    if (values.size < 2) return
    Canvas(modifier) {
        val lo = values.min()
        val hi = values.max().coerceAtLeast(lo + 0.1)
        val pad = 3.dp.toPx()
        fun x(i: Int) = pad + (size.width - pad * 2) * i / (values.size - 1)
        fun y(v: Double) = pad + (size.height - pad * 2) * (1 - ((v - lo) / (hi - lo)).toFloat())
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, c.brand, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(c.brand, 3.dp.toPx(), Offset(x(values.size - 1), y(values.last())))
    }
}
