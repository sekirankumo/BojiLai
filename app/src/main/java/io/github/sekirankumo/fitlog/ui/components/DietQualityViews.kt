package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * 分段刻度条：底轨按 0–15、15–23、23–49 分成三段（段间的小缺口就是及格线和良好线），
 * 从左往右填到当前分数，填充色跟着所在区间变。和营养素进度条是同一种读法。
 * @param score 为 null 时只画底轨（没有记录或还在分析）
 */
@Composable
fun QualityBar(score: Double?, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val c = Brand.colors
    val target = score?.coerceIn(0.0, DayQuality.MAX) ?: 0.0
    val animated = animatedValue(target.toFloat(), "quality")
    val zoneColor = score?.let { DayQuality.zoneOf(it).color() } ?: c.surface2
    val fill by animateColorAsState(zoneColor, tween(Motion.MEDIUM), label = "qualityColor")
    val bounds = listOf(0.0, DayQuality.FAIR_FROM, DayQuality.GOOD_FROM, DayQuality.MAX)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val gap = 3.dp.toPx()
        val usable = size.width - gap * 2
        val r = CornerRadius(size.height / 2)
        var x = 0f
        for (i in 0 until 3) {
            val from = bounds[i]
            val to = bounds[i + 1]
            val w = (usable * ((to - from) / DayQuality.MAX)).toFloat()
            drawRoundRect(c.surface2, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = r)
            val part = ((animated - from) / (to - from)).coerceIn(0.0, 1.0).toFloat()
            if (score != null && part > 0f) {
                drawRoundRect(fill, topLeft = Offset(x, 0f), size = Size(w * part, size.height), cornerRadius = r)
            }
            x += w + gap
        }
    }
}

/** 今日页饮食卡片里的一行：和蛋白质、脂肪、碳水同样的样式 */
@Composable
fun QualityRow(quality: DayQuality?, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val ready = quality != null && quality.analyzed > 0
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (ready) quality!!.zone.color() else c.line))
            Spacer(Modifier.width(6.dp))
            Text("膳食质量", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.weight(1f))
            when {
                quality == null -> Text("还没有记录", style = MaterialTheme.typography.labelMedium, color = c.muted)
                !ready -> Text("分析中", style = MaterialTheme.typography.labelMedium, color = c.muted)
                else -> {
                    AnimatedNumber(formatScore(quality!!.total), style = MaterialTheme.typography.titleSmall, color = c.text)
                    Text(" / 49 · ", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    Text(quality.zone.label, style = MaterialTheme.typography.labelMedium, color = quality.zone.color(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        QualityBar(if (ready) quality!!.total else null)
    }
}

/**
 * 饮食页的膳食质量卡片：分数 + 分段刻度条 + 25 类食物拼图 + 提分建议。
 * @param weekAverage 本周有记录的日子的平均分
 * @param backfill 后台正在补算历史记录的进度
 */
@Composable
fun DietQualityCard(
    quality: DayQuality?,
    weekAverage: Double?,
    backfill: Pair<Int, Int>?,
    modifier: Modifier = Modifier,
) {
    val c = Brand.colors
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val ready = quality != null && quality.analyzed > 0
    BCard(modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize(tween(Motion.LONG, easing = Motion.Emphasized))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("膳食质量", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                weekAverage?.let {
                    Text("本周平均 ${formatScore(it)}", style = MaterialTheme.typography.labelMedium, color = c.muted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (ready) {
                    AnimatedNumber(
                        formatScore(quality!!.total),
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.text,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(" / 49", style = MaterialTheme.typography.bodyMedium, color = c.muted, modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        quality.zone.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = quality.zone.color(),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                } else {
                    Text(
                        if (quality == null) "这天还没有记录" else "正在分析这天的饮食…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.muted,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            QualityBar(if (ready) quality!!.total else null, height = 8.dp)

            if (ready) {
                Spacer(Modifier.height(16.dp))
                FoodMosaic(quality!!.groups, selected) { key -> selected = if (selected == key) null else key }
                Spacer(Modifier.height(12.dp))
                val focus = quality.groups.firstOrNull { it.group.key == selected }
                if (focus != null) {
                    GroupDetail(focus)
                } else {
                    quality.suggestion?.let { (g, step) ->
                        Text(
                            "💡 再吃 ${step.first.fmt(0)}g ${g.group.label} → +${formatScore(step.second)} 分",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.text,
                        )
                    } ?: Text("点格子可以看每类食物吃了多少、得了几分", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }

            val pendingText = when {
                backfill != null -> "正在分析历史记录 ${backfill.first}/${backfill.second}"
                quality != null && quality.pending > 0 && quality.analyzed > 0 -> "还有 ${quality.pending} 条记录正在分析，分数会再更新"
                else -> null
            }
            pendingText?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = c.muted)
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "基于全球膳食质量评分（GDQS）· ≥23 分为良好",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.muted,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = "膳食分说明",
                    tint = c.muted,
                    modifier = Modifier.size(28.dp).clip(CircleShape).clickable { showInfo = true }.padding(5.dp),
                )
            }
        }
    }
    if (showInfo) QualityInfoDialog { showInfo = false }
}

/** 25 类食物拼成 5×5 的格子：拿到分的健康食物亮绿色，吃超了的不健康食物变红 */
@Composable
private fun FoodMosaic(groups: List<GroupScore>, selected: String?, onSelect: (String) -> Unit) {
    val c = Brand.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { g ->
                    val (bg, fg) = tileColors(g)
                    val bgAnim by animateColorAsState(bg, tween(Motion.MEDIUM), label = "tile")
                    val isSel = selected == g.group.key
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgAnim)
                            .then(if (isSel) Modifier.border(1.5.dp, c.text, RoundedCornerShape(10.dp)) else Modifier)
                            .clickable { onSelect(g.group.key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            g.group.short,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            color = fg,
                            fontWeight = if (bg == c.surface2) FontWeight.Normal else FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 格子颜色：背景色和文字色 */
@Composable
private fun tileColors(g: GroupScore): Pair<Color, Color> {
    val c = Brand.colors
    val light = lerp(c.surface2, c.brand, 0.32f)
    return when (g.group.kind) {
        FoodGroup.Kind.Healthy -> when {
            g.points >= g.group.maxPoints -> c.brand to c.onBrand
            g.points > 0 -> light to c.text
            else -> c.surface2 to c.muted
        }
        FoodGroup.Kind.Moderate -> when {
            g.grams <= 0 -> c.surface2 to c.muted
            g.points >= g.group.maxPoints -> c.brand to c.onBrand
            g.points > 0 -> light to c.text
            // 吃超了才算问题，吃得少只是没拿到分
            g.band == g.group.points.size - 1 -> c.dangerSoft to c.danger
            else -> c.surface2 to c.muted
        }
        FoodGroup.Kind.Unhealthy -> when (g.band) {
            0 -> c.surface2 to c.muted
            1 -> lerp(c.surface, c.warn, 0.22f) to c.warn
            else -> c.dangerSoft to c.danger
        }
    }
}

/** 点了某一格后显示的明细 */
@Composable
private fun GroupDetail(g: GroupScore) {
    val c = Brand.colors
    val head = "${g.group.label} · 已吃 ${g.grams.fmt(0)}g · ${formatScore(g.points)}/${formatScore(g.group.maxPoints)} 分"
    val tip = when (g.group.kind) {
        FoodGroup.Kind.Unhealthy -> when (g.band) {
            0 -> "控制得很好"
            // 回到第一档要严格少于第一个边界，回到中间档只要不超过上边界
            1 -> "少于 ${g.group.cutoffs[0].fmt(0)}g 可得 ${formatScore(g.group.points[0])} 分"
            else -> "不超过 ${g.group.cutoffs[g.band - 1].fmt(0)}g 可得 ${formatScore(g.group.points[g.band - 1])} 分"
        }
        else -> g.nextStep?.let { (need, gain) -> "再吃 ${need.fmt(0)}g 可 +${formatScore(gain)} 分" }
            ?: if (g.group.kind == FoodGroup.Kind.Moderate && g.band == g.group.points.size - 1) "吃得有点多了，适量就好" else "已拿满分"
    }
    Column {
        Text(head, style = MaterialTheme.typography.bodyMedium, color = c.text)
        Text("${g.group.examples.substringBefore("（")} · $tip", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

@Composable
private fun QualityInfoDialog(onDismiss: () -> Unit) {
    val c = Brand.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("膳食分怎么算") },
        text = {
            Text(
                "采用国际通用的全球膳食质量评分（GDQS），按一天里 25 类食物各吃了多少克打分，满分 49：\n\n" +
                    "· 蔬菜、水果、豆类、坚果、全谷物、鱼、禽、蛋、低脂奶等 16 类健康食物，吃到一定量就加分；\n" +
                    "· 红肉、全脂奶适量加分，吃太多不给分；\n" +
                    "· 加工肉、精制米面、甜食、含糖饮料、果汁、土豆类、外购油炸食品，吃得越少分越高。\n\n" +
                    "23 分以上营养不足和慢性病风险都较低，15–23 分一般，15 分以下需要改进。\n\n" +
                    "食材和克数由 AI 根据你的记录估算，看趋势更有参考价值；记录里可以修改。",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
    )
}

/** 高盐提醒：只是个红色小标签，不弹窗、不拦截 */
@Composable
fun HighSaltTag() {
    Pill("高盐", color = Brand.colors.danger, background = Brand.colors.dangerSoft)
}
