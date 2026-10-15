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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「薄肌来」品牌配色，和介绍页（docs/index.html）的配色一致。
 * 不使用系统的动态取色，保证在任何手机上看起来都一样。
 */
@Immutable
data class BrandColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val brand: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val accent: Color,
    val line: Color,
    val danger: Color,
    val dangerSoft: Color,
    val amber: Color,
    val onAmber: Color,
    val info: Color,
    val onInfo: Color,
    /** 计划中（待训练）的颜色 */
    val planned: Color,
    val protein: Color,
    val fat: Color,
    val carbs: Color,
    /** 提醒色（膳食分「一般」区间），和脂肪的金黄色区分开 */
    val warn: Color,
)

val LightBrand = BrandColors(
    bg = Color(0xFFF5F8F5),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEEF3EF),
    text = Color(0xFF13201A),
    muted = Color(0xFF5B6B63),
    brand = Color(0xFF1E6B52),
    onBrand = Color(0xFFFFFFFF),
    brandSoft = Color(0xFFD7EFE3),
    accent = Color(0xFFB8E36A),
    line = Color(0xFFDBE5DE),
    danger = Color(0xFFC0392B),
    dangerSoft = Color(0xFFFBE3E0),
    amber = Color(0xFFFFF3D6),
    onAmber = Color(0xFF4A3A12),
    info = Color(0xFFE6EEFB),
    onInfo = Color(0xFF1C3557),
    planned = Color(0xFF3E6374),
    protein = Color(0xFF2E8B6A),
    fat = Color(0xFFE0A53A),
    carbs = Color(0xFF4F7DC9),
    warn = Color(0xFFD9822B),
)

val DarkBrand = BrandColors(
    bg = Color(0xFF0D1411),
    surface = Color(0xFF151F1A),
    surface2 = Color(0xFF1B2822),
    text = Color(0xFFE4EDE8),
    muted = Color(0xFF9DB0A6),
    brand = Color(0xFF7FD8B0),
    onBrand = Color(0xFF0D1411),
    brandSoft = Color(0xFF1F3A2F),
    accent = Color(0xFFC9EF86),
    line = Color(0xFF26352E),
    danger = Color(0xFFFF8A7A),
    dangerSoft = Color(0xFF3A1F1B),
    amber = Color(0xFF3A3218),
    onAmber = Color(0xFFF3E4B5),
    info = Color(0xFF1C2A3D),
    onInfo = Color(0xFFC9DCF5),
    planned = Color(0xFF8CB8CC),
    protein = Color(0xFF6FD3A9),
    fat = Color(0xFFF2C46B),
    carbs = Color(0xFF8DB2F0),
    warn = Color(0xFFF0A35E),
)

val LocalBrand = staticCompositionLocalOf { LightBrand }

/** 在任意 Composable 里用 Brand.colors 取品牌色 */
object Brand {
    val colors: BrandColors
        @Composable get() = LocalBrand.current
}

private fun scheme(c: BrandColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = c.brand, onPrimary = c.onBrand,
        primaryContainer = c.brandSoft, onPrimaryContainer = c.brand,
        secondary = c.muted, onSecondary = c.bg,
        secondaryContainer = c.brandSoft, onSecondaryContainer = c.text,
        tertiary = c.planned, onTertiary = c.bg,
        tertiaryContainer = c.amber, onTertiaryContainer = c.onAmber,
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
        tertiary = c.planned, onTertiary = c.surface,
        tertiaryContainer = c.amber, onTertiaryContainer = c.onAmber,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        surfaceContainerLowest = c.surface, surfaceContainerLow = c.surface,
        surfaceContainer = c.surface, surfaceContainerHigh = c.surface, surfaceContainerHighest = c.surface2,
        outline = c.line, outlineVariant = c.line,
        error = c.danger, onError = c.surface, errorContainer = c.dangerSoft, onErrorContainer = c.danger,
    )
}

private fun typography(font: FontFamily?): Typography {
    val f = font ?: FontFamily.Default
    fun s(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) = TextStyle(
        fontFamily = f, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacing.sp,
    )
    return Typography(
        displaySmall = s(34, FontWeight.ExtraBold, 40),
        headlineLarge = s(30, FontWeight.ExtraBold, 36),
        headlineMedium = s(26, FontWeight.Bold, 32),
        headlineSmall = s(22, FontWeight.Bold, 28),
        titleLarge = s(20, FontWeight.Bold, 26),
        titleMedium = s(16, FontWeight.Bold, 22),
        titleSmall = s(14, FontWeight.SemiBold, 20),
        bodyLarge = s(16, FontWeight.Normal, 24),
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
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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
    CompositionLocalProvider(LocalBrand provides colors) {
        MaterialTheme(
            colorScheme = scheme(colors, darkTheme),
            typography = typography(fontFamily),
            shapes = AppShapes,
            content = content,
        )
    }
}
