package io.github.sekirankumo.fitlog.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.ui.theme.LightBrand

/** App 自己画的几个图标；其余用 Material 图标 */
object AppIcons {
    /** AI 的星星 */
    val Sparkle: ImageVector by lazy {
        ImageVector.Builder("sparkle", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                PathParser().parsePathString(
                    "M12 2.5l1.9 5.3 5.3 1.9-5.3 1.9L12 16.9l-1.9-5.3-5.3-1.9 5.3-1.9zM19 14l.9 2.4 2.4.9-2.4.9L19 20.6l-.9-2.4-2.4-.9 2.4-.9z",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** 哑铃：和 App 图标里的是同一个 */
    val Dumbbell: ImageVector by lazy {
        ImageVector.Builder("dumbbell", 24.dp, 24.dp, 54f, 54f).apply {
            path(fill = SolidColor(Color.Black)) { dumbbell(-27f, -27f) }
        }.build()
    }

    /** 说明：圆圈里一个 i。用线条画，保证 i 在圆圈正中（文字的 i 会偏） */
    val Info: ImageVector by lazy {
        ImageVector.Builder("info", 24.dp, 24.dp, 24f, 24f).apply {
            fun line(d: String, width: Float) = addPath(
                PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
            )
            line("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", 1.6f)
            line("M12 11v5.5", 1.8f)
            line("M12 7.6h0.01", 2.4f)
        }.build()
    }

    val Chevron: ImageVector by lazy { stroke("chevron", "M9 6l6 6-6 6") }
    val ChevronLeft: ImageVector by lazy { stroke("chevronLeft", "M15 6l-6 6 6 6") }
    val ChevronDown: ImageVector by lazy { stroke("chevronDown", "M6 9l6 6 6-6") }
    val Check: ImageVector by lazy { stroke("check", "M5 12.5l4.5 4.5L19 7", 2.6f) }
    val Back: ImageVector by lazy { stroke("back", "M19 12H5M11 6l-6 6 6 6") }
    val Close: ImageVector by lazy { stroke("close", "M6 6l12 12M18 6L6 18") }
    val Plus: ImageVector by lazy { stroke("plus", "M12 5v14M5 12h14", 2.2f) }
    val Send: ImageVector by lazy { stroke("send", "M12 19V5M6 11l6-6 6 6", 2.2f) }

    private fun stroke(name: String, d: String, width: Float = 2f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}

/** 圆角矩形 */
private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    horizontalLineTo(x + w - r)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    verticalLineTo(y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    horizontalLineTo(x + r)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    verticalLineTo(y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}

/**
 * 哑铃，坐标是 App 图标 108 画布里的位置（和 res/drawable/ic_launcher_foreground.xml 一致），
 * dx、dy 把它平移到需要的画布里。
 */
private fun PathBuilder.dumbbell(dx: Float, dy: Float) {
    roundRect(41f + dx, 51.6f + dy, 26f, 4.8f, 2.4f)
    roundRect(35f + dx, 40f + dy, 7f, 28f, 3f)
    roundRect(66f + dx, 40f + dy, 7f, 28f, 3f)
    roundRect(29.5f + dx, 45f + dy, 5.5f, 18f, 2.5f)
    roundRect(73f + dx, 45f + dy, 5.5f, 18f, 2.5f)
}

/**
 * App 图标：和 AI 键同一颗光球，中间是哑铃。设置「关于」里用。
 * 颜色固定用浅色那套（图标本身不跟深浅色变）。
 */
@Composable
fun LogoMark(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val orb = LightBrand.orb
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val corner = CornerRadius(this.size.width * 0.27f)
            val clip = androidx.compose.ui.graphics.Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, this@Canvas.size.width, this@Canvas.size.height, corner))
            }
            clipPath(clip) {
                // 渐变从时钟 6 点多的方向开始（和原型的 conic-gradient from 200deg 一致）
                rotate(110f) {
                    drawCircle(
                        Brush.sweepGradient(0f to orb[0], 0.22f to orb[1], 0.42f to orb[2], 0.6f to orb[3], 0.82f to orb[1], 1f to orb[0], center = center),
                        radius = this@Canvas.size.width,
                    )
                }
                val hl = Offset(this@Canvas.size.width * 0.3f, this@Canvas.size.height * 0.24f)
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0f)), center = hl, radius = this@Canvas.size.width * 0.57f),
                    radius = this@Canvas.size.width * 0.57f,
                    center = hl,
                )
            }
        }
        Icon(AppIcons.Dumbbell, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.7f))
    }
}
