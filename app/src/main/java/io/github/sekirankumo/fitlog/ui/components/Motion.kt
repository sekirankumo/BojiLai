package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 全 App 统一的动效参数：时长短、先快后慢，宁快勿拖。
 * 系统设置里关掉动画（或调了动画倍速）时 Compose 会自动跟随。
 */
object Motion {
    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 350

    /** Material 3 的强调曲线：起步快、收尾柔和 */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 数值变化（进度条、圆环）用 */
    fun <T> value(): FiniteAnimationSpec<T> = tween(700, easing = EmphasizedDecelerate)

    /** 按压回弹用：有一点点弹性 */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
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
 * 变化时上下滚动的数字/文字：变大从下往上滚，变小从上往下滚。
 */
@Composable
fun AnimatedNumber(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            val up = (targetState.toDoubleOrNull() ?: 0.0) >= (initialState.toDoubleOrNull() ?: 0.0)
            val dir = if (up) 1 else -1
            (slideInVertically(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { it / 2 * dir } + fadeIn(tween(Motion.MEDIUM)))
                .togetherWith(slideOutVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedAccelerate)) { -it / 2 * dir } + fadeOut(tween(Motion.SHORT)))
                .using(SizeTransform(clip = false))
        },
        label = "number",
    ) { value ->
        Text(value, style = style, color = color, fontWeight = fontWeight)
    }
}

/** AI 思考中：三个依次跳动的小点 */
@Composable
fun TypingDots(color: Color, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                label = "dot$i",
            )
            // 每个点错开 1/3 周期，在前半段跳起、后半段落下
            val phase = ((offset + 1f - i / 3f) % 1f)
            val lift = if (reduce) 0f else if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        translationY = -lift * 5.dp.toPx()
                        alpha = 0.4f + 0.6f * lift
                    }
                    .background(color, CircleShape),
            )
        }
    }
}
