package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.delay

/** 整页内容的模糊来源，页面外面的浮层（底部导航）用它做毛玻璃；截图测试里为空 */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/**
 * 毛玻璃：模糊背后的内容再叠一层半透明底色，描一圈很淡的边。
 * 安卓 12 以下（或截图测试里）没有实时模糊，只用半透明底色。
 * @param source 模糊谁。浮层自己在 source 里面时什么都画不出来（变成全透明），
 * 所以页面里的浮层（输入框、保存栏）要传页面自己的列表做来源，不能用整页的 LocalHaze。
 */
@Composable
fun Modifier.glass(shape: Shape, source: HazeState? = LocalHaze.current): Modifier {
    val c = Brand.colors
    val haze = source
    val base = this.clip(shape)
    val filled = if (haze != null && !LocalReduceMotion.current) {
        base.hazeEffect(
            haze,
            HazeStyle(
                backgroundColor = c.bg,
                tint = HazeTint(c.glass),
                blurRadius = 24.dp,
                noiseFactor = 0f,
                fallbackTint = HazeTint(c.surface.copy(alpha = 0.94f)),
            ),
        )
    } else {
        base.background(c.surface.copy(alpha = 0.94f))
    }
    return filled.border(1.dp, c.glassLine, shape)
}

/**
 * 顶栏的状态，由各页面根据自己的滚动位置更新：
 * edge 是顶部渐隐边缘的强度（内容滑到顶栏下面时出现），compact 表示大标题收成小标题。
 */
@Stable
class TopBarState {
    var edge by mutableFloatStateOf(0f)
    var compact by mutableStateOf(false)

    /** 今日页：平时显示的问候语 */
    var mainText by mutableStateOf<String?>(null)

    /** 今日页：滑过主数字后显示的摘要 */
    var compactText by mutableStateOf<AnnotatedString?>(null)
}

val LocalTopBar = staticCompositionLocalOf { TopBarState() }

/** 页面四周要留出的空间：上面是状态栏 + 顶栏，下面是底部导航（或系统导航条） */
data class ScreenInsets(val top: Dp, val bottom: Dp)

val LocalScreenInsets = staticCompositionLocalOf { ScreenInsets(64.dp, 24.dp) }

/** 页面列表的内边距：左右 16，上下让开顶栏和底栏 */
@Composable
fun screenPadding(extraTop: Dp = 4.dp, extraBottom: Dp = 24.dp): PaddingValues {
    val i = LocalScreenInsets.current
    return PaddingValues(start = 16.dp, end = 16.dp, top = i.top + extraTop, bottom = i.bottom + extraBottom)
}

/** 根据列表滚动位置更新顶栏：滑动 24dp 内渐隐边缘完全出现，超过 compactAfter 收成小标题 */
@Composable
fun TrackTopBar(list: LazyListState, compactAfter: Dp = 28.dp) {
    val bar = LocalTopBar.current
    val density = LocalDensity.current
    LaunchedEffect(list, bar) {
        val edgePx = with(density) { 24.dp.toPx() }
        val compactPx = with(density) { compactAfter.toPx() }
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (index, offset) ->
            bar.edge = if (index > 0) 1f else (offset / edgePx).coerceIn(0f, 1f)
            bar.compact = index > 0 || offset > compactPx
        }
    }
}

@Composable
fun TrackTopBar(scroll: ScrollState, compactAfter: Dp = 28.dp) {
    val bar = LocalTopBar.current
    val density = LocalDensity.current
    LaunchedEffect(scroll, bar) {
        val edgePx = with(density) { 24.dp.toPx() }
        val compactPx = with(density) { compactAfter.toPx() }
        snapshotFlow { scroll.value }.collect { v ->
            bar.edge = (v / edgePx).coerceIn(0f, 1f)
            bar.compact = v > compactPx
        }
    }
}

/** 底部提示条：一句话 + 可选的按钮（撤销），几秒后自动消失 */
@Stable
class ToastState {
    internal var current by mutableStateOf<ToastData?>(null)
    private var counter = 0L

    fun show(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
        current = ToastData(++counter, message, action, onAction)
    }

    fun dismiss() {
        current = null
    }
}

internal data class ToastData(val id: Long, val message: String, val action: String?, val onAction: (() -> Unit)?)

val LocalToast = staticCompositionLocalOf { ToastState() }

/** 提示条：深色底（深色模式下是浅色底）的胶囊，从底部升起 */
@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val data = state.current
    // 淡出的过程中还要显示原来的内容
    val last = androidx.compose.runtime.remember { arrayOfNulls<ToastData>(1) }
    if (data != null) last[0] = data
    LaunchedEffect(data?.id) {
        if (data != null) {
            delay(if (data.action != null) 5000 else 2600)
            if (state.current?.id == data.id) state.current = null
        }
    }
    AnimatedVisibility(
        visible = data != null,
        modifier = modifier,
        enter = fadeIn(tween(Motion.SHORT)) + slideInVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { it / 2 },
        exit = fadeOut(tween(Motion.SHORT)) + slideOutVertically(tween(Motion.SHORT)) { it / 3 },
    ) {
        val shown = last[0] ?: return@AnimatedVisibility
        Row(
            Modifier
                .widthIn(max = 420.dp)
                .clip(CircleShape)
                .background(c.text)
                .padding(start = 18.dp, end = if (shown.action != null) 8.dp else 18.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(shown.message, style = Brand.type.label, color = c.bg, modifier = Modifier.weight(1f, fill = false))
            shown.action?.let { label ->
                Text(
                    label,
                    style = Brand.type.labelBold,
                    color = c.toastAction,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            state.current = null
                            shown.onAction?.invoke()
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** 浮层的阴影高度：让玻璃浮起来 */
val GlassShadow: Dp = 12.dp

/** 让一块区域可以点击、但不显示水波纹（玻璃浮层上用） */
@Composable
fun Modifier.quietClick(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)

/** 只是为了让 Box 默认对齐方式更好写 */
@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Box(modifier, contentAlignment = Alignment.Center) { content() }

/** 页面里自己的模糊来源（给页面内的浮层用）；截图测试里不启用 */
@Composable
fun rememberLocalHaze(): HazeState? {
    val state = androidx.compose.runtime.remember { HazeState() }
    return if (LocalReduceMotion.current) null else state
}

/** 把这块内容作为模糊来源；source 为空时不做任何事 */
fun Modifier.blurSource(source: HazeState?): Modifier = if (source == null) this else this.hazeSource(source)
