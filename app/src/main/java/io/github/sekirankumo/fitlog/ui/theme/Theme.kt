package io.github.sekirankumo.fitlog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sekirankumo.fitlog.R
import io.github.sekirankumo.fitlog.data.ThemeMode

/**
 * 「安静而有力量」的配色：只用一个品牌绿，红和橙只在提醒时出现；关键数字直接放在背景上，
 * 只有能操作的模块才用卡片；毛玻璃只给浮在内容上方的东西（底部导航、输入框、保存栏）。
 * 和介绍页（docs/index.html）、设计原型用的是同一套色值。不使用系统的动态取色。
 */
@Immutable
data class BrandColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    /** 正文、主要数字 */
    val text: Color,
    val muted: Color,
    /** 最浅的灰，只用于装饰（分隔、未到的日期等），不用于需要读的文字 */
    val faint: Color,
    val line: Color,
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    /** 提醒色：超出目标、膳食分「一般」 */
    val warn: Color,
    val danger: Color,
    val dangerSoft: Color,
    /** 毛玻璃浮层的底色（半透明） */
    val glass: Color,
    val glassLine: Color,
    /** 底部弹层的底色 */
    val sheet: Color,
    /** 弹层里输入框、次要按钮的底色 */
    val fill: Color,
    /** 膳食分刻度上「良好」区间的淡绿 */
    val band: Color,
    /** AI 光球的四个颜色：亮度相同，从品牌绿往冷色走，避开表示提醒的黄橙红 */
    val orb: List<Color>,
    /** 今日页环境光：状态好时偏绿，超出时偏橙 */
    val glowGood: Color,
    val glowWarn: Color,
    val glow2Good: Color,
    val glow2Warn: Color,
    /** 肌肉图：没练过的肌肉、非肌肉部分 */
    val muscle: Color,
    val bodyOther: Color,
    /** 底部提示条（深色底）里按钮的颜色 */
    val toastAction: Color,
)

val LightBrand = BrandColors(
    dark = false,
    bg = Color(0xFFF4F6F4),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFECF0ED),
    text = Color(0xFF101A15),
    muted = Color(0xFF66756D),
    faint = Color(0xFF9AA69F),
    line = Color(0xFFE2E8E4),
    brand = Color(0xFF1E6B52),
    onBrand = Color(0xFFFFFFFF),
    brandSoft = Color(0xFFDCEFE5),
    warn = Color(0xFFD9822B),
    danger = Color(0xFFC0392B),
    dangerSoft = Color(0xFFF8E1DE),
    glass = Color(0xADFFFFFF),
    glassLine = Color(0x14101A15),
    sheet = Color(0xFFFAFCFA),
    fill = Color(0x11101A15),
    band = Color(0xFFCBDBD4),
    orb = listOf(Color(0xFF33A377), Color(0xFF299F9C), Color(0xFF3699B8), Color(0xFF638BD5)),
    glowGood = Color(0x382E966C),
    glowWarn = Color(0x3DE89646),
    glow2Good = Color(0x2946A8BE),
    glow2Warn = Color(0x29E2705C),
    muscle = Color(0xFFE2E9E4),
    bodyOther = Color(0xFFEEF2EF),
    toastAction = Color(0xFF7FD8B0),
)

val DarkBrand = BrandColors(
    dark = true,
    bg = Color(0xFF0B110E),
    surface = Color(0xFF131B17),
    surface2 = Color(0xFF1A2420),
    text = Color(0xFFE7EFEA),
    muted = Color(0xFF93A49B),
    faint = Color(0xFF5E6E66),
    line = Color(0xFF1F2B25),
    brand = Color(0xFF7FD8B0),
    onBrand = Color(0xFF0B110E),
    brandSoft = Color(0xFF1C3429),
    warn = Color(0xFFF0A35E),
    danger = Color(0xFFFF8A7A),
    dangerSoft = Color(0xFF3A1F1B),
    glass = Color(0x9E131B17),
    glassLine = Color(0x14E7EFEA),
    sheet = Color(0xFF161F1B),
    fill = Color(0x12E7EFEA),
    band = Color(0xFF345346),
    orb = listOf(Color(0xFF51BC8F), Color(0xFF4AB8B5), Color(0xFF53B3D1), Color(0xFF7BA4F0)),
    glowGood = Color(0x297FD8B0),
    glowWarn = Color(0x2BF0A35E),
    glow2Good = Color(0x1C6EC8DC),
    glow2Warn = Color(0x1CFF826E),
    muscle = Color(0xFF223029),
    bodyOther = Color(0xFF19231E),
    toastAction = Color(0xFF1E6B52),
)

/** 数字统一用 Barlow Condensed（开源 OFL 许可，打包在 res/font） */
val NumFont = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
)

/** 去掉字体上下的额外留白、行内居中：数字和小标签才能和旁边的东西准确对齐 */
private val Tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
private val NoPadding = PlatformTextStyle(includeFontPadding = false)

/**
 * 全 App 只用这几级字号。
 * 数字：主角 92 / 一级 34 / 二级 24 / 三级 20，次要数字和单位 16 / 14。
 * 文字：大标题 28 / 二级标题 22 / 标题 16 / 正文 15 / 标签 13 / 注释 12。
 */
@Immutable
class TypeScale(private val cn: FontFamily) {
    private fun num(size: Int, weight: FontWeight = FontWeight.SemiBold, line: TextUnit = (size * 1.1f).sp) = TextStyle(
        fontFamily = NumFont, fontSize = size.sp, fontWeight = weight, lineHeight = line,
        lineHeightStyle = Tight, platformStyle = NoPadding,
    )

    private fun text(size: Int, weight: FontWeight, line: Int) = TextStyle(
        fontFamily = cn, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp,
        lineHeightStyle = Tight, platformStyle = NoPadding,
    )

    val hero = num(92, line = 88.sp)

    /** 二级主数字：饮食页的「吃了多少」、身体页的体重 */
    val display = num(60, line = 60.sp)
    val n1 = num(34)
    val n2 = num(24)
    val n3 = num(20)
    val nSub1 = num(16, FontWeight.Medium)
    val nSub2 = num(14, FontWeight.Medium)

    val h1 = text(28, FontWeight.Bold, 36)
    val h2 = text(22, FontWeight.Bold, 30)
    val title = text(16, FontWeight.Bold, 22)
    val body = text(15, FontWeight.Normal, 24)
    val bodyMedium = text(15, FontWeight.Medium, 22)
    val label = text(13, FontWeight.Normal, 19)
    val labelBold = text(13, FontWeight.Bold, 19)
    val note = text(12, FontWeight.Normal, 17)
    val noteBold = text(12, FontWeight.Medium, 17)
    val tiny = text(11, FontWeight.Medium, 14)
}

val LocalBrand = staticCompositionLocalOf { LightBrand }
val LocalType = staticCompositionLocalOf { TypeScale(FontFamily.Default) }

/** 在任意 Composable 里用 Brand.colors 取颜色、Brand.type 取字号 */
object Brand {
    val colors: BrandColors
        @Composable get() = LocalBrand.current
    val type: TypeScale
        @Composable get() = LocalType.current
}

private fun scheme(c: BrandColors) = if (c.dark) {
    darkColorScheme(
        primary = c.brand, onPrimary = c.onBrand,
        primaryContainer = c.brandSoft, onPrimaryContainer = c.brand,
        secondary = c.muted, onSecondary = c.bg,
        secondaryContainer = c.brandSoft, onSecondaryContainer = c.text,
        tertiary = c.muted, onTertiary = c.bg,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface2, surfaceContainerHighest = c.surface2,
        outline = c.line, outlineVariant = c.line,
        error = c.danger, onError = c.bg, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
    )
} else {
    lightColorScheme(
        primary = c.brand, onPrimary = c.onBrand,
        primaryContainer = c.brandSoft, onPrimaryContainer = c.brand,
        secondary = c.muted, onSecondary = c.surface,
        secondaryContainer = c.brandSoft, onSecondaryContainer = c.text,
        tertiary = c.muted, onTertiary = c.surface,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface2,
        outline = c.line, outlineVariant = c.line,
        error = c.danger, onError = c.surface, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
    )
}

/** Material 组件（输入框、菜单等）用的字号，和上面的字号层级对齐 */
private fun typography(font: FontFamily): Typography {
    fun s(size: Int, weight: FontWeight, line: Int) = TextStyle(
        fontFamily = font, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp,
    )
    return Typography(
        displaySmall = s(34, FontWeight.Bold, 40),
        headlineLarge = s(28, FontWeight.Bold, 36),
        headlineMedium = s(26, FontWeight.Bold, 32),
        headlineSmall = s(22, FontWeight.Bold, 30),
        titleLarge = s(20, FontWeight.Bold, 26),
        titleMedium = s(16, FontWeight.Bold, 22),
        titleSmall = s(14, FontWeight.SemiBold, 20),
        bodyLarge = s(15, FontWeight.Normal, 24),
        bodyMedium = s(14, FontWeight.Normal, 21),
        bodySmall = s(12, FontWeight.Normal, 17),
        labelLarge = s(14, FontWeight.SemiBold, 20),
        labelMedium = s(12, FontWeight.Medium, 16),
        labelSmall = s(11, FontWeight.Medium, 14),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** 设置里选的外观对应深色还是浅色；跟随系统时看系统 */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * @param fontFamily 只在截图测试里用来指定中文字体，App 里用系统字体
 */
@Composable
fun BojiLaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontFamily: FontFamily? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkBrand else LightBrand
    val font = fontFamily ?: FontFamily.Default
    val type = remember(font) { TypeScale(font) }
    CompositionLocalProvider(LocalBrand provides colors, LocalType provides type) {
        MaterialTheme(
            colorScheme = scheme(colors),
            typography = typography(font),
            shapes = AppShapes,
            content = content,
        )
    }
}
