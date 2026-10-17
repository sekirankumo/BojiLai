package io.github.sekirankumo.fitlog.ui.screens

import io.github.sekirankumo.fitlog.ui.components.rememberNextWeek
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import io.github.sekirankumo.fitlog.ui.components.itemMotion
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.NutritionWeekSheet
import io.github.sekirankumo.fitlog.ui.components.NutritionNoticeCard
import io.github.sekirankumo.fitlog.data.NutritionNotice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.NutritionTargets
import io.github.sekirankumo.fitlog.data.UserProfile
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.Meal
import io.github.sekirankumo.fitlog.data.db.PlanDay
import io.github.sekirankumo.fitlog.data.diet.DayQuality
import io.github.sekirankumo.fitlog.data.diet.DayTotals
import io.github.sekirankumo.fitlog.ui.components.ActivityHeatmap
import io.github.sekirankumo.fitlog.ui.components.AmbientGlow
import io.github.sekirankumo.fitlog.ui.components.AnimatedNumber
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.CardHeader
import io.github.sekirankumo.fitlog.ui.components.FigureNumber
import io.github.sekirankumo.fitlog.ui.components.LocalScreenInsets
import io.github.sekirankumo.fitlog.ui.components.LocalTopBar
import io.github.sekirankumo.fitlog.ui.components.MuscleFigures
import io.github.sekirankumo.fitlog.ui.components.ProgressLine
import io.github.sekirankumo.fitlog.ui.components.QualityRow
import io.github.sekirankumo.fitlog.ui.components.RecoveringChips
import io.github.sekirankumo.fitlog.ui.components.Sparkline
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.TrainingData
import io.github.sekirankumo.fitlog.ui.components.rememberTrainingData
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.ui.theme.NumFont
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

class TodayNav(
    val openTraining: () -> Unit,
    val openBody: () -> Unit,
    val openDiet: () -> Unit,
    /** 补全个人信息；只差体重记录时去身体页 */
    val completeProfile: (missing: List<String>) -> Unit,
)

private val CN_NUM = listOf("零", "一", "两", "三", "四", "五")

/**
 * 今日：最大的数字是「今天还能吃多少」，下面三大营养素和膳食质量直接放在背景上；
 * 训练（热力图 + 今天练什么）和身体（肌肉图 + 体重体脂）是两张卡片。
 * 顶部有缓慢漂移的环境光，颜色跟今天的状态走。
 */
@Composable
fun TodayScreen(container: AppContainer, nav: TodayNav) {
    val c = Brand.colors
    val today = LocalDate.now()
    val foodFlow = remember { container.diet.observeDay(today) }
    val food by foodFlow.collectAsState(initial = emptyList())
    // 今天的营养目标：有 AI 安排用安排，没有用默认
    val targetFlow = remember(today) { container.nutritionPlan.targetFor(today) }
    val targets by targetFlow.collectAsState(initial = null)
    val dayFlow = remember { container.plans.observeDay(today) }
    val plan by dayFlow.collectAsState(initial = null)
    val bodyFlow = remember { container.database.bodyRecordDao().observeAll() }
    val records by bodyFlow.collectAsState(initial = emptyList())
    val profile by container.profile.profile.collectAsState(initial = UserProfile())
    val training = rememberTrainingData(container)

    val totals = DayTotals.of(food)
    val quality = remember(food) { DayQuality.of(food) }
    val target = targets?.kcal
    val left = target?.let { it - totals.kcal }
    val over = left != null && left < 0
    val leftText = left?.let { abs(it).roundToInt().toString() } ?: "—"

    // 顶栏：平时是问候语，滑过主数字后换成「还能吃多少」
    val bar = LocalTopBar.current
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        else -> "晚上好"
    }
    val meals = food.map { Meal.of(it.meal) }.distinct().size
    val mainText = greeting + when {
        food.isEmpty() -> "，今天还没有记饮食"
        over -> "，今天有点超了"
        else -> "，今天已经记了${CN_NUM.getOrElse(meals) { "$meals" }}餐"
    }
    val numStyle = SpanStyle(fontFamily = NumFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
    SideEffect {
        bar.mainText = mainText
        bar.compactText = buildAnnotatedString {
            append(if (over) "超出 " else "还能吃 ")
            withStyle(numStyle) { append(leftText) }
            append(" kcal")
        }
    }
    val list = rememberLazyListState()
    TrackTopBar(list, compactAfter = 150.dp)
    val topInset = LocalScreenInsets.current.top

    // AI 调整营养后的提示：只在今日页出现一次。一显示就标记为看过，这次停留期间留着，下次再来就没有了
    val toast = LocalToast.current
    val storedNotice by container.nutritionPlan.notice.collectAsState(initial = null)
    var notice by remember { mutableStateOf<NutritionNotice?>(null) }
    var showWeek by remember { mutableStateOf(false) }
    val nextWeek = rememberNextWeek(container)
    val autoNutrition by container.appPrefs.autoNutrition.collectAsState()
    LaunchedEffect(storedNotice) {
        val n = storedNotice ?: return@LaunchedEffect
        if (n.date == today.toEpochDay()) notice = n
        container.nutritionPlan.dismissNotice(n.opId)
    }

    LazyColumn(state = list, contentPadding = screenPadding(extraTop = 6.dp, extraBottom = 28.dp)) {
        item(key = "hero") {
            Box {
                // 环境光：铺满屏幕宽度，从屏幕最上面开始，不占列表的位置
                AmbientGlow(
                    warn = over,
                    modifier = Modifier.layout { measurable, constraints ->
                        val w = constraints.maxWidth + 32.dp.roundToPx()
                        val h = 520.dp.roundToPx()
                        val p = measurable.measure(Constraints.fixed(w, h))
                        layout(0, 0) { p.place(-16.dp.roundToPx(), -(topInset + 66.dp).roundToPx()) }
                    },
                )
                Hero(totals, targets, left, over, leftText)
            }
        }
        item(key = "macros") {
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Macro("蛋白质", totals.proteinG, targets?.proteinG, Modifier.weight(1f))
                Macro("脂肪", totals.fatG, targets?.fatG, Modifier.weight(1f))
                Macro("碳水", totals.carbsG, targets?.carbsG, Modifier.weight(1f))
            }
        }
        notice?.let { n ->
            item(key = "nutrition_notice") {
                NutritionNoticeCard(
                    n,
                    onView = { showWeek = true },
                    onUndo = {
                        notice = null
                        container.appScope.launch {
                            val ok = runCatching { container.operations.undo(n.opId) }.isSuccess
                            toast.show(if (ok) "已撤销这次营养调整" else "撤销失败，可以在调整记录里再试")
                        }
                    },
                    modifier = itemMotion().padding(top = 24.dp),
                )
            }
        }
        item(key = "quality") {
            QualityRow(quality, Modifier.padding(top = 24.dp), onClick = nav.openDiet)
        }
        targets?.takeIf { it.needsProfile }?.let { t ->
            item(key = "profile") { ProfileHint(t, nav.completeProfile, Modifier.padding(top = 16.dp)) }
        }
        item(key = "training") {
            Box(Modifier.padding(top = 24.dp)) { TrainingCard(container, plan, training, nav.openTraining) }
        }
        item(key = "body") {
            Box(Modifier.padding(top = 12.dp)) { BodyCard(records, training, profile.sex == "female", nav.openBody) }
        }
    }

    if (showWeek) NutritionWeekSheet(nextWeek, autoNutrition) { showWeek = false }
}

@Composable
private fun Hero(totals: DayTotals, targets: NutritionTargets?, left: Double?, over: Boolean, leftText: String) {
    val c = Brand.colors
    Column(Modifier.padding(top = 8.dp)) {
        Text(if (over) "今天超出" else "今天还能吃", style = Brand.type.label, color = c.muted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedNumber(leftText, Brand.type.hero, if (over) c.warn else c.text)
            Text("kcal", style = Brand.type.nSub1, color = c.muted, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        }
        Spacer(Modifier.height(14.dp))
        ProgressLine(targets?.kcal?.let { (totals.kcal / it).toFloat() } ?: 0f, warnWhenFull = true)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("已吃 ", style = Brand.type.label, color = c.muted)
            Text("${totals.kcal.roundToInt()}", style = Brand.type.nSub1.copy(fontWeight = FontWeight.SemiBold), color = c.text)
            Spacer(Modifier.weight(1f))
            Text("目标 ", style = Brand.type.label, color = c.muted)
            Text("${targets?.kcal ?: "—"}", style = Brand.type.nSub1.copy(fontWeight = FontWeight.SemiBold), color = c.text)
        }
    }
}

@Composable
private fun Macro(label: String, value: Double, target: Int?, modifier: Modifier) {
    val c = Brand.colors
    Column(modifier) {
        Text(label, style = Brand.type.label, color = c.muted)
        Spacer(Modifier.height(4.dp))
        FigureNumber("${value.roundToInt()}", target?.let { "/${it}g" } ?: "g", Brand.type.n1)
        Spacer(Modifier.height(8.dp))
        ProgressLine(target?.let { (value / it).toFloat() } ?: 0f, height = 3.dp, warnWhenFull = true)
    }
}

/** 资料不全时：还差什么、去填写 */
@Composable
fun ProfileHint(targets: NutritionTargets, onComplete: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .clickable { onComplete(targets.missing) }
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "还差${targets.missing.joinToString("、")}，补全后目标会更准确",
            style = Brand.type.label,
            color = c.muted,
            modifier = Modifier.weight(1f),
        )
        Text("去填写", style = Brand.type.labelBold, color = c.brand)
        Icon(AppIcons.Chevron, contentDescription = null, tint = c.brand, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun TrainingCard(container: AppContainer, plan: PlanDay?, training: TrainingData, open: () -> Unit) {
    val c = Brand.colors
    BCard(onClick = open) {
        CardHeader("训练")
        Spacer(Modifier.height(12.dp))
        ActivityHeatmap(training.daily)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (plan == null) {
                Column(Modifier.weight(1f)) {
                    Text("今天休息", style = Brand.type.title, color = c.text)
                    Text("好好恢复，肌肉是在休息时长出来的", style = Brand.type.label, color = c.muted)
                }
            } else {
                val exercises = container.plans.exercisesOf(plan)
                val done = exercises.sumOf { ex -> ex.sets.count { it.done } }
                val total = exercises.sumOf { it.sets.size }
                Column(Modifier.weight(1f)) {
                    Text("今天 · ${plan.title}", style = Brand.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(exercises.joinToString("、") { it.name }, style = Brand.type.label, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FigureNumber("$done", " / $total 组", Brand.type.n2, unitStyle = Brand.type.label)
            }
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.brand), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Chevron, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** 身体卡片：肌肉图 + 体重体脂（和一周前比）+ 正在恢复的部位 */
@Composable
private fun BodyCard(records: List<BodyRecord>, training: TrainingData, female: Boolean, open: () -> Unit) {
    val c = Brand.colors
    val latestW = records.firstOrNull { it.weightKg != null }
    val weekAgoW = records.firstOrNull { it.weightKg != null && it.measuredAt <= TimeUtil.daysAgo(7) }
    val wDelta = latestW?.weightKg?.let { w -> weekAgoW?.weightKg?.let { w - it } }
    val latestF = records.firstOrNull { it.bodyFatPct != null }
    val weekAgoF = records.firstOrNull { it.bodyFatPct != null && it.measuredAt <= TimeUtil.daysAgo(7) }
    val fDelta = latestF?.bodyFatPct?.let { f -> weekAgoF?.bodyFatPct?.let { f - it } }
    val spark = remember(records) { records.mapNotNull { it.weightKg }.take(10).reversed() }
    BCard(onClick = open) {
        CardHeader("身体")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MuscleFigures(training.muscles, female, 196.dp, Modifier.weight(1.25f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text("体重", style = Brand.type.label, color = c.muted)
                    FigureNumber(latestW?.weightKg?.fmt() ?: "—", "kg", Brand.type.n1)
                    Text(deltaText(wDelta, latestW == null), style = Brand.type.note, color = if (wDelta != null && wDelta < 0) c.brand else c.muted)
                    if (spark.size >= 2) Sparkline(spark, Modifier.fillMaxWidth().height(30.dp).padding(top = 6.dp))
                }
                Column {
                    Text("体脂率", style = Brand.type.label, color = c.muted)
                    FigureNumber(latestF?.bodyFatPct?.fmt() ?: "—", "%", Brand.type.n1)
                    if (fDelta != null) Text(deltaText(fDelta, false), style = Brand.type.note, color = if (fDelta < 0) c.brand else c.muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Spacer(Modifier.height(12.dp))
        RecoveringChips(training.muscles)
    }
}

/** 「本周 −0.6」：负号用真正的减号 */
private fun deltaText(d: Double?, empty: Boolean): String = when {
    d != null -> "本周 " + (if (d >= 0) "+" else "−") + abs(d).fmt()
    empty -> "还没有记录"
    else -> " "
}
