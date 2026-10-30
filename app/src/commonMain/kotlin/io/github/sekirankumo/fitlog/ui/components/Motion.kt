package io.github.sekirankumo.fitlog.ui.components

import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 全 App 统一的动效：一套曲线、三档时长（150 / 300 / 450 毫秒）。
 * 手势类动效跟手，松手后用弹簧收尾，只稍微回弹一点、不晃。
 * 系统设置里关掉动画（或调了动画倍速）时 Compose 会自动跟随。
 */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 300
    const val LONG = 450

    /** 强调曲线：起步快、收尾柔和 */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 数值变化（进度线、刻度点）用 */
    fun <T> value(): FiniteAnimationSpec<T> = tween(600, easing = EmphasizedDecelerate)

    /** 按压回弹：只回弹一点点 */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)

    /** 跟手的东西松手后收尾（弹层、月历） */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

/** 数值变化时的动画；截图测试（LocalReduceMotion）里直接跳到目标值 */
@Composable
fun animatedValue(target: Float, label: String): Float {
    val reduce = LocalReduceMotion.current
    val v by animateFloatAsState(target, if (reduce) snap() else Motion.value(), label = label)
    return v
}

/**
 * 列表项出现、消失、换位置时的动画（淡入淡出 + 位移）。
 * 截图测试（LocalReduceMotion）里关掉，保证截到的是最终画面。
 */
@Composable
fun LazyItemScope.itemMotion(): Modifier = if (LocalReduceMotion.current) Modifier else Modifier.animateItem()

/** 按下时轻微缩小、松手弹回，配合可点击控件的 interactionSource 使用 */
@Composable
fun Modifier.pressScale(interaction: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, Motion.press(), label = "press")
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 逐位滚动的数字（像里程表）：每一位单独滚，变大往上滚、变小往下滚，从左到右错开 30 毫秒。
 * 不是数字的字符（小数点、负号）也跟着位置走。页面刚出现的一秒内不滚（那时只是数据加载出来，数值并没有变）。
 */
@Composable
fun AnimatedNumber(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    if (LocalReduceMotion.current) {
        Text(text, style = style, color = color, fontWeight = fontWeight, modifier = modifier)
        return
    }
    val previous = remember { arrayOf(text) }
    val up = (text.toDoubleOrNull() ?: 0.0) >= (previous[0].toDoubleOrNull() ?: 0.0)
    previous[0] = text
    // 切换页面时数字本身没变，只是数据刚加载出来（比如从 0 变成实际值），这时不滚动，免得看起来像数值在变；
    // 停留一会儿之后数值真的变了（删掉一条记录、打卡一组）才滚动
    var live by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        live = true
    }
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            // 从右往左对齐位数：位数变化时个位、十位还是同一个滚轮
            key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        if (!live) {
                            (EnterTransition.None togetherWith ExitTransition.None).using(null)
                        } else {
                            val wait = i * 30
                            val dir = if (up) 1 else -1
                            (slideInVertically(tween(Motion.LONG, wait, Motion.EmphasizedDecelerate)) { it * dir } + fadeIn(tween(Motion.MEDIUM, wait)))
                                .togetherWith(slideOutVertically(tween(Motion.LONG, wait, Motion.Emphasized)) { -it * dir } + fadeOut(tween(Motion.SHORT, wait)))
                                .using(SizeTransform(clip = true))
                        }
                    },
                    label = "digit",
                ) { c ->
                    Text(c.toString(), style = style, color = color, fontWeight = fontWeight)
                }
            }
        }
    }
}

/** AI 思考中：三个依次亮起的小点 */
@Composable
fun TypingDots(color: Color, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "typing")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { i ->
            val phase = ((t - i * 0.17f) % 1f + 1f) % 1f
            val on = if (reduce) 1f else (1f - kotlin.math.abs(phase - 0.4f) / 0.4f).coerceIn(0f, 1f)
            Box(Modifier.size(6.dp).graphicsLayer { alpha = 0.25f + 0.75f * on }.background(color, CircleShape))
        }
    }
}

/**
 * AI 光球：品牌绿到蓝紫的扫掠渐变缓慢旋转，左上角一点高光。平时 9 秒一圈；
 * AI 思考时平滑加速到 2.5 秒一圈并轻轻呼吸，答完再慢下来。App 里只有它是多色的。
 */
@Composable
fun AiOrb(
    size: Dp,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    sparkle: Boolean = true,
    shadow: Boolean = true,
) {
    val c = Brand.colors
    val reduce = LocalReduceMotion.current
    var angle by remember { mutableFloatStateOf(200f) }
    var breathe by remember { mutableFloatStateOf(0f) }
    val busyState = rememberUpdatedState(busy)
    if (!reduce) {
        LaunchedEffect(Unit) {
            var last = 0L
            var speed = 40f
            var clock = 0f
            while (true) {
                withInfiniteAnimationFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                    last = now
                    val target = if (busyState.value) 144f else 40f
                    speed += (target - speed) * (dt * 2.5f).coerceAtMost(1f)
                    angle = (angle + speed * dt) % 360f
                    clock += dt
                    breathe = if (busyState.value) sin(clock * 2 * PI.toFloat() / 1.6f) else breathe * (1f - (dt * 4f).coerceAtMost(1f))
                }
            }
        }
    }
    val glow = c.orb[2]
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = 1f + 0.04f * breathe
                scaleX = s
                scaleY = s
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            if (shadow) {
                drawCircle(
                    Brush.radialGradient(listOf(glow.copy(alpha = 0.38f), Color.Transparent), center = center + Offset(0f, r * 0.25f), radius = r * 1.45f),
                    radius = r * 1.45f,
                    center = center + Offset(0f, r * 0.25f),
                )
            }
            rotate(angle) {
                drawCircle(
                    Brush.sweepGradient(
                        0f to c.orb[0], 0.22f to c.orb[1], 0.42f to c.orb[2], 0.6f to c.orb[3], 0.82f to c.orb[1], 1f to c.orb[0],
                        center = center,
                    ),
                    radius = r,
                )
            }
            val hl = center + Offset(-r * 0.3f * cos(PI / 4).toFloat(), -r * 0.4f)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0f)), center = hl, radius = r * 0.95f),
                radius = r * 0.94f,
                center = center,
            )
        }
        if (sparkle) {
            Icon(
                AppIcons.Sparkle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.42f).graphicsLayer { shadowElevation = 0f },
            )
        }
    }
}

/**
 * 今日页顶部缓慢漂移的环境光：三团柔光以 53 / 67 / 79 秒的周期无规律地游动，
 * 颜色跟当天状态走（吃得好偏绿，超出偏橙），换状态时 1.6 秒过渡。往下渐隐到背景里。
 */
@Composable
fun AmbientGlow(warn: Boolean, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val reduce = LocalReduceMotion.current
    val main by animateColorAsState(if (warn) c.glowWarn else c.glowGood, tween(1600), label = "glow")
    val second by animateColorAsState(if (warn) c.glow2Warn else c.glow2Good, tween(1600), label = "glow2")
    val transition = rememberInfiniteTransition(label = "drift")
    fun spec(ms: Int) = infiniteRepeatable<Float>(tween(ms, easing = Motion.Emphasized.let { CubicBezierEasing(0.45f, 0f, 0.55f, 1f) }), RepeatMode.Reverse)
    val p1 by transition.animateFloat(0f, 1f, spec(53_000), label = "p1")
    val p2 by transition.animateFloat(0f, 1f, spec(67_000), label = "p2")
    val p3 by transition.animateFloat(0f, 1f, spec(79_000), label = "p3")
    Canvas(
        modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val w = size.width
        val h = size.height
        fun path(p: Float, a: Float, b: Float) = if (reduce) 0f else sin(p * PI.toFloat() * a) * b
        val blobs = listOf(
            Triple(Offset(w * 0.08f + path(p1, 1.3f, w * 0.22f), h * 0.12f + path(p1, 2.1f, h * 0.12f)), w * 0.62f, main),
            Triple(Offset(w * 0.95f - path(p2, 1.7f, w * 0.24f), h * 0.1f + path(p2, 1.2f, h * 0.16f)), w * 0.52f, second),
            Triple(Offset(w * 0.5f + path(p3, 1.5f, w * 0.18f), h * 0.42f - path(p3, 1.9f, h * 0.1f)), w * 0.45f, main.copy(alpha = main.alpha * 0.7f)),
        )
        for ((center, radius, color) in blobs) {
            drawCircle(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius), radius = radius, center = center)
        }
        // 下半部分渐隐到背景
        drawRect(
            Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
}
