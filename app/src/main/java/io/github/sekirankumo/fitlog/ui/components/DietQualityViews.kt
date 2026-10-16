package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.material3.Icon
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.data.diet.DayQuality
import io.github.sekirankumo.fitlog.data.diet.FoodGroup
import io.github.sekirankumo.fitlog.data.diet.GroupScore
import io.github.sekirankumo.fitlog.data.diet.QualityZone
import io.github.sekirankumo.fitlog.data.diet.formatScore
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.fmt

/** 膳食分区间的颜色：良好用品牌绿，一般用提醒橙，需改进用警示红 */
@Composable
fun QualityZone.color(): Color = when (this) {
    QualityZone.Good -> Brand.colors.brand
    QualityZone.Fair -> Brand.colors.warn
    QualityZone.Poor -> Brand.colors.danger
}

/** 今日页的一行：「膳食质量」+ 刻度 + 分数和区间 */
@Composable
fun QualityRow(quality: DayQuality?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Brand.colors
    val ready = quality != null && quality.analyzed > 0
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("膳食质量", style = Brand.type.label, color = c.muted)
        QualityRail(if (ready) quality!!.total else null, Modifier.weight(1f))
        when {
            quality == null -> Text("还没有记录", style = Brand.type.label, color = c.muted)
            !ready -> Text("分析中", style = Brand.type.label, color = c.muted)
            else -> Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AnimatedNumber(formatScore(quality!!.total), Brand.type.n2, c.text)
                Text(quality.zone.label, style = Brand.type.labelBold, color = quality.zone.color(), modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}

/** 标签上显示的一类：几类相近的食物合在一起（水果、蔬菜、奶），植物油不单独显示 */
private class DisplayGroup(val name: String, val groups: List<GroupScore>) {
    val points = groups.sumOf { it.points }
    val max = groups.sumOf { it.group.maxPoints }
    val grams = groups.sumOf { it.grams }
}

private class GroupedQuality(val good: List<DisplayGroup>, val less: List<DisplayGroup>, val more: List<DisplayGroup>)

private fun group(quality: DayQuality): GroupedQuality {
    val by = quality.groups.associateBy { it.group }
    fun g(name: String, vararg keys: FoodGroup) = DisplayGroup(name, keys.mapNotNull { by[it] })
    val healthy = listOf(
        g("豆类", FoodGroup.Legumes),
        g("全谷物", FoodGroup.WholeGrains),
        g("禽肉", FoodGroup.Poultry),
        g("鱼虾", FoodGroup.Fish),
        g("蛋", FoodGroup.Eggs),
        g("奶", FoodGroup.LowFatDairy, FoodGroup.HighFatDairy),
        g("深绿叶菜", FoodGroup.DarkLeafy),
        g("蔬菜", FoodGroup.Cruciferous, FoodGroup.OrangeVeg, FoodGroup.OtherVeg, FoodGroup.OrangeTuber),
        g("坚果", FoodGroup.Nuts),
        g("水果", FoodGroup.Citrus, FoodGroup.OrangeFruit, FoodGroup.OtherFruit),
    )
    // 吃超了才算问题：不健康的吃到第二档以上，红肉、全脂奶吃过量
    val less = buildList {
        quality.groups.filter { it.group.kind == FoodGroup.Kind.Unhealthy && it.band > 0 }.forEach {
            add(DisplayGroup(if (it.group == FoodGroup.RefinedGrains) "精制米面" else it.group.short, listOf(it)))
        }
        quality.groups.filter { it.group.kind == FoodGroup.Kind.Moderate && it.grams > 0 && it.band == it.group.points.size - 1 }.forEach {
            add(DisplayGroup(if (it.group == FoodGroup.HighFatDairy) "全脂奶" else it.group.short, listOf(it)))
        }
    }
    val good = healthy.filter { it.points > 0 }
    // 多吃一点就能加分的，按能加的分排，最多 4 个
    val more = healthy.filter { it.points == 0.0 }
        .mapNotNull { d -> d.groups.mapNotNull { it.nextStep }.filter { it.first <= 150 }.maxByOrNull { it.second }?.let { d to it.second } }
        .sortedByDescending { it.second }
        .take(4)
        .map { it.first }
    return GroupedQuality(good, less, more)
}

/**
 * 饮食页的膳食质量卡片：分数 + 区间、本周平均和比昨天、刻度，下面三组标签（没有的组不显示），
 * 点标签看这一类吃了多少、怎么加分。
 * @param yesterday 前一天的分数，用来显示「比昨天」
 * @param backfill 后台正在补算历史记录的进度
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietQualityCard(
    quality: DayQuality?,
    weekAverage: Double?,
    yesterday: Double?,
    backfill: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    val c = Brand.colors
    var detail by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val ready = quality != null && quality.analyzed > 0
    val grouped = remember(quality) { quality?.takeIf { ready }?.let(::group) }
    BCard(modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(tween(Motion.LONG, easing = Motion.Emphasized))) {
            CardHeader("膳食质量") { InfoButton { showInfo = true } }
            if (!ready) {
                Spacer(Modifier.height(8.dp))
                Text(if (quality == null) "这天还没有记录" else "正在分析这天的饮食…", style = Brand.type.label, color = c.muted)
            } else {
                val q = quality!!
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        AnimatedNumber(formatScore(q.total), Brand.type.n1, c.text)
                        Text(q.zone.label, style = Brand.type.labelBold, color = q.zone.color(), modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        weekAverage?.let { SmallStat("本周平均", formatScore(it), c.text) }
                        yesterday?.let { y ->
                            val d = q.total - y
                            SmallStat("比昨天", (if (d >= 0) "+" else "−") + formatScore(kotlin.math.abs(d)), if (d >= 0) c.brand else c.warn)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                // 刻度：23 分以上是「良好」区间，下面标出 23
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val x = maxWidth * (DayQuality.GOOD_FROM / DayQuality.MAX).toFloat()
                    Column {
                        QualityRail(q.total, height = 4.dp, pin = 14.dp, pinBorder = c.surface)
                        Text(
                            "23",
                            style = Brand.type.nSub2,
                            color = c.muted,
                            modifier = Modifier.offset(x = x - 6.dp).padding(top = 4.dp),
                        )
                    }
                }
                grouped?.let { g ->
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    Spacer(Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Triple("做得好", g.good, TagKind.Good), Triple("少吃点", g.less, TagKind.Less), Triple("可以多吃", g.more, TagKind.More))
                            .filter { it.second.isNotEmpty() }
                            .forEach { (label, items, kind) ->
                                Row {
                                    Text(label, style = Brand.type.label, color = c.muted, modifier = Modifier.width(60.dp).padding(top = 4.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items.forEach { d -> Tag(d.name, kind = kind, onClick = { detail = d.name }) }
                                    }
                                }
                            }
                    }
                }
            }
            val pendingText = when {
                backfill != null -> "正在分析历史记录 ${backfill.first}/${backfill.second}"
                quality != null && quality.pending > 0 && quality.analyzed > 0 -> "还有 ${quality.pending} 条记录正在分析，分数会再更新"
                else -> null
            }
            pendingText?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = Brand.type.note, color = c.muted)
            }
        }
    }
    if (showInfo) QualityInfoSheet { showInfo = false }
    val g = grouped
    val name = detail
    if (g != null && name != null) {
        val d = (g.good + g.less + g.more).firstOrNull { it.name == name }
        if (d != null) GroupDetailSheet(d) { detail = null }
    }
}

@Composable
private fun SmallStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, style = Brand.type.label, color = Brand.colors.muted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = Brand.type.nSub1.copy(fontWeight = FontWeight.SemiBold), color = color)
    }
}

/** 圆圈里一个 i：点开看说明 */
@Composable
fun InfoButton(onClick: () -> Unit) {
    val c = Brand.colors
    // 点击区域比圆圈大一圈；往右挪一点，让看得见的圆圈和下面内容的右边对齐
    Box(
        Modifier
            .offset(x = 7.dp)
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "说明" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Info, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
    }
}

/** 点了某个标签后：这一类吃了多少克、得了几分、怎么加分 */
@Composable
private fun GroupDetailSheet(d: DisplayGroup, onDismiss: () -> Unit) {
    val c = Brand.colors
    BSheet(
        onDismiss = onDismiss,
        title = d.name,
        subtitle = "已吃 ${d.grams.fmt(0)}g · ${formatScore(d.points)}/${formatScore(d.max)} 分",
        actions = { s -> BButton("知道了", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val shown = if (d.groups.size > 1) d.groups.filter { it.grams > 0 || it.nextStep != null } else d.groups
            shown.forEach { g ->
                Column {
                    if (d.groups.size > 1) {
                        Text("${g.group.label} · 已吃 ${g.grams.fmt(0)}g · ${formatScore(g.points)}/${formatScore(g.group.maxPoints)} 分", style = Brand.type.bodyMedium, color = c.text)
                        Spacer(Modifier.height(2.dp))
                    }
                    Text(tipOf(g), style = Brand.type.body, color = if (d.groups.size > 1) c.muted else c.text)
                    Text(g.group.examples.substringBefore("（"), style = Brand.type.note, color = c.muted, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Text("克数由 AI 根据记录里的食材估算，记录里可以修改。", style = Brand.type.note, color = c.muted)
        }
    }
}

private fun tipOf(g: GroupScore): String = when (g.group.kind) {
    FoodGroup.Kind.Unhealthy -> when (g.band) {
        0 -> "控制得很好"
        // 回到第一档要严格少于第一个边界，回到中间档只要不超过上边界
        1 -> "少于 ${g.group.cutoffs[0].fmt(0)}g 可得 ${formatScore(g.group.points[0])} 分"
        else -> "不超过 ${g.group.cutoffs[g.band - 1].fmt(0)}g 可得 ${formatScore(g.group.points[g.band - 1])} 分"
    }
    else -> g.nextStep?.let { (need, gain) -> "再吃 ${need.fmt(0)}g 可 +${formatScore(gain)} 分" }
        ?: if (g.group.kind == FoodGroup.Kind.Moderate && g.band == g.group.points.size - 1) "吃得有点多了，适量就好" else "已拿满分"
}

@Composable
fun QualityInfoSheet(onDismiss: () -> Unit) {
    val c = Brand.colors
    BSheet(
        onDismiss = onDismiss,
        title = "膳食分怎么算",
        actions = { s -> BButton("知道了", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() } },
    ) {
        Text(
            "采用国际通用的全球膳食质量评分（GDQS），按一天里 25 类食物各吃了多少克打分，满分 49：\n\n" +
                "· 蔬菜、水果、豆类、坚果、全谷物、鱼、禽、蛋、低脂奶等 16 类健康食物，吃到一定量就加分；\n" +
                "· 红肉、全脂奶适量加分，吃太多不给分；\n" +
                "· 加工肉、精制米面、甜食、含糖饮料、果汁、土豆类、外购油炸食品，吃得越少分越高。\n\n" +
                "23 分以上营养不足和慢性病风险都较低，15–23 分一般，15 分以下需要改进。",
            style = Brand.type.body,
            color = c.text,
        )
        Spacer(Modifier.height(10.dp))
        Text("食材和克数由 AI 根据你的记录估算，看趋势更有参考价值；记录里可以修改。", style = Brand.type.note, color = c.muted)
    }
}
