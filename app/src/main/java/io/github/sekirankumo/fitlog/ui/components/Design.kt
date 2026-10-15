package io.github.sekirankumo.fitlog.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.data.diet.DayQuality
import io.github.sekirankumo.fitlog.ui.theme.Brand

/** 为 true 时关闭自动播放类动画和毛玻璃（截图测试用） */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** 在底部弹层里：输入框、次要按钮换成一层淡墨色，浅色玻璃上才看得清边界 */
val LocalOnSheet = staticCompositionLocalOf { false }

private val CardShape = RoundedCornerShape(18.dp)

/** 卡片的「实体」质感：浅色是极轻的投影，深色是一道很淡的顶边高光 */
@Composable
fun Modifier.cardMaterial(shape: androidx.compose.ui.graphics.Shape = CardShape): Modifier {
    val c = Brand.colors
    return if (c.dark) {
        this.border(
            BorderStroke(1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f), 0.18f to Color.Transparent)),
            shape,
        )
    } else {
        this.shadow(6.dp, shape, ambientColor = Color(0x14101A15), spotColor = Color(0x1F101A15))
    }
}

/**
 * 实色卡片，只用在能操作的模块上。可点的卡片按下时轻微缩小、松手回弹。
 * @param flash 0..1，完成时边缘亮一下淡绿光
 */
@Composable
fun BCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Brand.colors.surface,
    padding: PaddingValues = PaddingValues(16.dp),
    flash: Float = 0f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Brand.colors
    val flashMod = if (flash > 0.01f) Modifier.border(1.5.dp, c.brand.copy(alpha = 0.45f * flash), CardShape) else Modifier
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.pressScale(interaction, 0.98f).cardMaterial().then(flashMod),
            shape = CardShape,
            color = color,
            interactionSource = interaction,
        ) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier.cardMaterial().then(flashMod), shape = CardShape, color = color) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

/** 卡片标题行：标题 + 右侧可选的内容 */
@Composable
fun CardHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = Brand.type.title, color = Brand.colors.text, modifier = Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** 区块标题（兼容旧调用） */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) =
    CardHeader(title, modifier, action)

/** 分组名：写在卡片外面的小字 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, first: Boolean = false) {
    Text(
        text,
        style = Brand.type.label,
        color = Brand.colors.muted,
        modifier = modifier.padding(start = 4.dp, top = if (first) 4.dp else 22.dp, bottom = 8.dp),
    )
}

enum class TagKind { Neutral, Good, Less, More }

/** 小标签：中性 / 做得好（浅绿）/ 少吃点、高盐（浅红）/ 可以多吃（灰字） */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    kind: TagKind = TagKind.Neutral,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val c = Brand.colors
    val (bg, fg) = when (kind) {
        TagKind.Neutral -> c.surface2 to c.text
        TagKind.Good -> c.brandSoft to c.brand
        TagKind.Less -> c.dangerSoft to c.danger
        TagKind.More -> c.surface2 to c.muted
    }
    val style = (if (small) Brand.type.tiny else Brand.type.noteBold).let {
        if (kind == TagKind.Good || kind == TagKind.Less) it.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else it
    }
    Text(
        text,
        color = fg,
        style = style,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (small) 7.dp else 10.dp, vertical = if (small) 2.dp else 4.dp),
    )
}

/** 胶囊标签（自定颜色） */
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
        style = Brand.type.noteBold,
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 高盐提醒：只是个红色小标签，不弹窗、不拦截 */
@Composable
fun HighSaltTag() = Tag("高盐", kind = TagKind.Less, small = true)

/** 分组列表：一张卡片里一行一项，行之间是缩进的细线 */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().cardMaterial(), shape = CardShape, color = Brand.colors.surface) {
        Column(content = content)
    }
}

@Composable
fun ListDivider(start: Dp = 16.dp) {
    Box(Modifier.padding(start = start).fillMaxWidth().height(1.dp).background(Brand.colors.line))
}

/** 列表行：左图标、标题、说明、右侧内容（默认是箭头） */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    titleColor: Color = Brand.colors.text,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Brand.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Brand.type.bodyMedium, color = titleColor)
            subtitle?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = Brand.type.note, color = c.muted)
            }
        }
        trailing?.invoke()
        if (trailing == null && chevron) {
            Icon(AppIcons.Chevron, contentDescription = null, tint = c.faint, modifier = Modifier.size(18.dp))
        }
    }
}

/** 兼容旧调用 */
@Composable
fun RowItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) = ListRow(title, modifier, subtitle, icon, onClick = onClick, chevron = false, trailing = trailing)

/** 开关 */
@Composable
fun ToggleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, description: String? = null) {
    val c = Brand.colors
    val track by animateColorAsState(if (checked) c.brand else c.surface2, tween(Motion.SHORT), label = "track")
    val knob by animateDpAsState(if (checked) 18.dp else 0.dp, Motion.press(), label = "knob")
    Box(
        modifier
            .size(46.dp, 28.dp)
            .clip(CircleShape)
            .background(track)
            .then(if (checked) Modifier else Modifier.border(1.dp, c.line, CircleShape))
            .clickable(role = Role.Switch, onClickLabel = description) { onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** 次要按钮的底色：普通页面是 surface2，弹层里是淡墨色 */
@Composable
fun softFill(): Color = if (LocalOnSheet.current) Brand.colors.fill else Brand.colors.surface2

enum class ButtonKind { Primary, Soft, Text, Danger }

/** 按钮：主按钮（品牌绿）、次要（浅底）、文字、危险（红字） */
@Composable
fun BButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 44.dp,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Brand.colors
    val (bg, fg) = when (kind) {
        ButtonKind.Primary -> c.brand to c.onBrand
        ButtonKind.Soft -> softFill() to c.text
        ButtonKind.Text -> Color.Transparent to c.brand
        ButtonKind.Danger -> Color.Transparent to c.danger
    }
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = height).pressScale(interaction, 0.97f).graphicsAlpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                TypingDots(fg, Modifier.padding(end = 8.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (kind == ButtonKind.Soft) c.brand else fg, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                style = if (kind == ButtonKind.Primary) Brand.type.labelBold else Brand.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = fg,
                maxLines = 1,
            )
        }
    }
}

private fun Modifier.graphicsAlpha(a: Float) = if (a >= 1f) this else this.graphicsLayer { alpha = a }

/**
 * 输入框：标签写在上面，底色是浅灰（弹层里是淡墨色），聚焦时描一圈品牌绿。
 * @param numeric 数字键盘；数字用 Barlow Condensed 显示
 */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    numeric: Boolean = false,
    integer: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle? = null,
    leading: ImageVector? = null,
) {
    val c = Brand.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val fill = softFill()
    val bg by animateColorAsState(if (focused) c.surface else fill, tween(Motion.SHORT), label = "fieldBg")
    val border by animateColorAsState(if (focused) c.brand else Color.Transparent, tween(Motion.SHORT), label = "fieldBorder")
    val style = textStyle ?: if (numeric) Brand.type.n3.copy(fontSize = Brand.type.n3.fontSize * 0.95f) else Brand.type.bodyMedium
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        label?.takeIf { it.isNotEmpty() }?.let { Text(it, style = Brand.type.note, color = c.muted, modifier = Modifier.padding(start = 2.dp)) }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else 8,
            textStyle = style.copy(color = c.text),
            cursorBrush = SolidColor(c.brand),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    integer -> KeyboardType.Number
                    numeric -> KeyboardType.Decimal
                    else -> KeyboardType.Text
                },
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(1.5.dp, border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) {
                        Icon(leading, contentDescription = null, tint = c.muted, modifier = Modifier.padding(end = 8.dp).size(20.dp))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = Brand.type.body, color = c.muted.copy(alpha = 0.75f))
                        }
                        inner()
                    }
                }
            },
        )
    }
}

/** 分段选择（性别、目标、体重 / 体脂） */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(softFill()).padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) c.surface else Color.Transparent, tween(Motion.SHORT), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .then(if (on && !c.dark) Modifier.shadow(1.dp, RoundedCornerShape(10.dp)) else Modifier)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = if (on) Brand.type.labelBold else Brand.type.label, color = if (on) c.text else c.muted)
            }
        }
    }
}

/** 单选标签（活动量、训练目标、器材）：选中是浅绿底 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) c.brandSoft else Color.Transparent, tween(Motion.SHORT), label = "chip")
            Text(
                label,
                style = if (on) Brand.type.labelBold else Brand.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                color = if (on) c.brand else c.text,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bg)
                    .then(if (on) Modifier else Modifier.border(1.dp, c.line, CircleShape))
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** 大号数字选择（每周天数、时长、周期）：数字用 Barlow，单位是小字 */
@Composable
fun NumberChoice(options: List<Int>, unit: String, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { v ->
            val on = v == selected
            val bg by animateColorAsState(if (on) c.brandSoft else c.surface2, tween(Motion.SHORT), label = "num")
            Row(
                Modifier
                    .weight(1f)
                    .height(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .clickable(role = Role.RadioButton) { onSelect(v) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$v", style = Brand.type.n2, color = if (on) c.brand else c.text)
                Text(unit, style = Brand.type.tiny, color = if (on) c.brand else c.muted, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
            }
        }
    }
}

/** 勾选框 + 文字 */
@Composable
fun CheckItem(checked: Boolean, onChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val c = Brand.colors
    val bg by animateColorAsState(if (checked) c.brand else Color.Transparent, tween(Motion.SHORT), label = "check")
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox) { onChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .then(if (checked) Modifier else Modifier.border(1.5.dp, c.muted, RoundedCornerShape(6.dp))),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(AppIcons.Check, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(14.dp))
        }
        Text(label, style = Brand.type.body, color = c.text)
    }
}

/**
 * 细进度线：第一次出现时从 0 长到当前值，数值变化时平滑过去。
 * 超过 100% 时变成提醒橙。
 */
@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    color: Color = Brand.colors.brand,
    warnWhenFull: Boolean = false,
) {
    val c = Brand.colors
    val p = animatedValue(progress.coerceIn(0f, 1f), "line")
    val fill by animateColorAsState(if (warnWhenFull && progress >= 1f) c.warn else color, tween(Motion.MEDIUM), label = "lineColor")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(c.surface2, cornerRadius = r)
        if (p > 0f) drawRoundRect(fill, size = Size(size.width * p, size.height), cornerRadius = r)
    }
}

/** 兼容旧调用 */
@Composable
fun BarProgress(progress: Float, modifier: Modifier = Modifier, color: Color = Brand.colors.brand, track: Color = Brand.colors.surface2, height: Dp = 4.dp) =
    ProgressLine(progress, modifier, height, color)

/**
 * 膳食分刻度：灰色底轨，23 分以上的「良好」区间是一段很淡的绿，彩色的点是当前分数。
 * 目标就是让点落在绿色那段里。今日页和饮食页用的是同一个。
 * @param pinBorder 点外面一圈的颜色，要和背后的底色一样
 */
@Composable
fun QualityRail(score: Double?, modifier: Modifier = Modifier, height: Dp = 3.dp, pin: Dp = 12.dp, pinBorder: Color = Brand.colors.bg) {
    val c = Brand.colors
    val target = (score ?: 0.0).coerceIn(0.0, DayQuality.MAX).toFloat()
    val pos = animatedValue(target / DayQuality.MAX.toFloat(), "railPin")
    val zoneColor = score?.let { qualityColor(it) } ?: c.faint
    val pinColor by animateColorAsState(zoneColor, tween(Motion.MEDIUM), label = "pinColor")
    val goodFrom = (DayQuality.GOOD_FROM / DayQuality.MAX).toFloat()
    Canvas(modifier.fillMaxWidth().height(pin)) {
        val h = height.toPx()
        val y = (size.height - h) / 2
        val r = CornerRadius(h / 2)
        drawRoundRect(c.surface2, topLeft = Offset(0f, y), size = Size(size.width, h), cornerRadius = r)
        drawRoundRect(c.band, topLeft = Offset(size.width * goodFrom, y), size = Size(size.width * (1 - goodFrom), h), cornerRadius = r)
        if (score != null) {
            val x = (size.width * pos).coerceIn(pin.toPx() / 2, size.width - pin.toPx() / 2)
            val radius = pin.toPx() / 2
            drawCircle(pinBorder, radius, Offset(x, size.height / 2))
            drawCircle(pinColor, radius - 2.5.dp.toPx(), Offset(x, size.height / 2))
        }
    }
}

/** 膳食分所在区间的颜色：良好品牌绿、一般提醒橙、需改进警示红 */
@Composable
fun qualityColor(score: Double): Color = when {
    score >= DayQuality.GOOD_FROM -> Brand.colors.brand
    score >= DayQuality.FAIR_FROM -> Brand.colors.warn
    else -> Brand.colors.danger
}

/** 空状态：图标 + 标题 + 说明 + 可选操作 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val c = Brand.colors
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(c.surface2), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = Brand.type.title, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(text, style = Brand.type.label, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        action?.let {
            Spacer(Modifier.height(16.dp))
            it()
        }
    }
}


/** 数字 + 单位：数字是主角，单位小一号、灰色 */
@Composable
fun FigureNumber(
    value: String,
    unit: String?,
    numberStyle: TextStyle,
    modifier: Modifier = Modifier,
    unitStyle: TextStyle = Brand.type.nSub1,
    color: Color = Brand.colors.text,
    animate: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        if (animate) AnimatedNumber(value, numberStyle, color) else Text(value, style = numberStyle, color = color)
        unit?.let {
            Text(it, style = unitStyle, color = Brand.colors.muted, modifier = Modifier.padding(start = 3.dp, bottom = 2.dp))
        }
    }
}

/** 标签 + 大数字 + 单位 */
@Composable
fun LabeledFigure(label: String, value: String, unit: String?, numberStyle: TextStyle, modifier: Modifier = Modifier, unitStyle: TextStyle = Brand.type.nSub1) {
    Column(modifier) {
        Text(label, style = Brand.type.label, color = Brand.colors.muted)
        Spacer(Modifier.height(4.dp))
        FigureNumber(value, unit, numberStyle, unitStyle = unitStyle)
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

/** 显示 assets 图片，加载不到时显示占位底色；深色模式下稍微压暗，不刺眼 */
@Composable
fun AssetImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val c = Brand.colors
    val bitmap = rememberAssetImage(path)
    if (bitmap != null) {
        Image(
            bitmap,
            contentDescription,
            modifier,
            contentScale = contentScale,
            colorFilter = if (c.dark) androidx.compose.ui.graphics.ColorFilter.tint(Color(0x26000000), androidx.compose.ui.graphics.BlendMode.SrcAtop) else null,
        )
    } else {
        Box(modifier.background(c.surface2))
    }
}

/** 删除线文字（已撤销的操作） */
val StrikeThrough = TextDecoration.LineThrough

/** 带内容的提供者：让一块区域里的输入框、按钮用弹层的配色 */
@Composable
fun OnSheet(content: @Composable () -> Unit) = CompositionLocalProvider(LocalOnSheet provides true, content = content)

/** 让文字在一行里两端对齐的小工具：左边文字、右边内容 */
@Composable
fun SpaceBetween(modifier: Modifier = Modifier, left: @Composable RowScope.() -> Unit, right: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        left()
        Spacer(Modifier.weight(1f))
        right()
    }
}

/** 文字超长时省略 */
val Ellipsis = TextOverflow.Ellipsis

/** 宽度不够时的保护：BoxWithConstraints 的简写 */
@Composable
fun WidthAware(content: @Composable (Dp) -> Unit) = BoxWithConstraints { content(maxWidth) }
