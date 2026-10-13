package io.github.sekirankumo.fitlog.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.ui.theme.Brand

/** 为 true 时关闭自动播放类动画（截图测试用） */
val LocalReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

/** 扁平卡片：白底 + 细描边 + 20dp 圆角，可点击 */
@Composable
fun BCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Brand.colors.surface,
    border: Boolean = true,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val stroke = if (border) BorderStroke(1.dp, Brand.colors.line) else null
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = stroke) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = stroke) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/** 圆角方块里的图标 */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color = Brand.colors.brand,
    background: Color = Brand.colors.brandSoft,
    size: Dp = 40.dp,
    corner: Dp = 12.dp,
) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** 区块标题，右侧可放操作 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** 胶囊标签 */
@Composable
fun Pill(
    text: String,
    color: Color = Brand.colors.brand,
    background: Color = Brand.colors.brandSoft,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 环形进度 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    stroke: Dp = 12.dp,
    color: Color = Brand.colors.brand,
    track: Color = Brand.colors.surface2,
    content: @Composable () -> Unit = {},
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            val p = progress.coerceIn(0f, 1f)
            if (p > 0f) {
                drawArc(color, -90f, 360f * p, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** 细长进度条 */
@Composable
fun BarProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Brand.colors.brand,
    track: Color = Brand.colors.surface2,
    height: Dp = 8.dp,
) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) {
            Box(Modifier.fillMaxWidth(p).height(height).clip(CircleShape).background(color))
        }
    }
}

/** 营养素进度：名称 + 数值 + 细进度条 */
@Composable
fun MacroProgress(label: String, value: Double, target: Int?, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.padding(bottom = 4.dp).size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.colors.muted)
        }
        Text(
            "${value.toInt()}" + (target?.let { " / $it g" } ?: " g"),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        BarProgress(target?.let { (value / it).toFloat() } ?: 0f, color = color, height = 6.dp)
    }
}

/** 数据小方块：标签、大数字、单位、说明 */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String = "",
    sub: String? = null,
    subColor: Color = Brand.colors.muted,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    BCard(modifier, onClick = onClick, padding = PaddingValues(14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Brand.colors.muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
            if (unit.isNotEmpty()) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = Brand.colors.muted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        sub?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 空状态：大图标 + 标题 + 说明 + 可选操作 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconBadge(icon, size = 64.dp, corner = 20.dp)
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Brand.colors.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        action?.let {
            Spacer(Modifier.height(4.dp))
            it()
        }
    }
}

/** 列表行：左图标、标题、说明、右侧内容 */
@Composable
fun RowItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, size = 36.dp, corner = 10.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.colors.muted) }
        }
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            it()
        }
    }
}

/** 读取 assets 里的图片（动作库示意图很小，直接同步解码并缓存） */
@Composable
fun rememberAssetImage(path: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(path) {
        path?.let { p ->
            AssetImageCache.get(p) ?: runCatching {
                context.assets.open(p).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()?.also { AssetImageCache.put(p, it) }
        }
    }
}

private object AssetImageCache {
    private val cache = object : android.util.LruCache<String, ImageBitmap>(60) {}
    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }
}

/** 显示 assets 图片，加载不到时显示占位底色 */
@Composable
fun AssetImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val bitmap = rememberAssetImage(path)
    if (bitmap != null) {
        Image(bitmap, contentDescription, modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(Brand.colors.surface2))
    }
}
