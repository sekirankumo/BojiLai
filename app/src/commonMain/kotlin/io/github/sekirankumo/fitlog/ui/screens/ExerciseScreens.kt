package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sekirankumo.fitlog.data.exercise.Exercise
import io.github.sekirankumo.fitlog.data.exercise.ExerciseRepository
import io.github.sekirankumo.fitlog.data.exercise.MUSCLE_GROUPS
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.AssetImage
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.Field
import io.github.sekirankumo.fitlog.ui.components.ListDivider
import io.github.sekirankumo.fitlog.ui.components.LocalReduceMotion
import io.github.sekirankumo.fitlog.ui.components.Motion
import io.github.sekirankumo.fitlog.ui.components.Tag
import io.github.sekirankumo.fitlog.ui.components.TagKind
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.Zoomable
import io.github.sekirankumo.fitlog.ui.components.itemMotion
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.delay

/** 动作库：搜索 + 按肌群筛选 */
@Composable
fun ExerciseLibraryScreen(repo: ExerciseRepository, onOpen: (String) -> Unit) {
    val c = Brand.colors
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = listOf<String?>(null) + MUSCLE_GROUPS.keys.toList() + listOf("有氧", "拉伸")
    val list = repo.search(query.ifBlank { null }, group)
    val state = rememberLazyListState()
    TrackTopBar(state)

    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = screenPadding()) {
        item(key = "search") {
            Field(query, { query = it }, placeholder = "搜索动作，例如：深蹲、卧推", leading = Icons.Outlined.Search)
        }
        item(key = "groups") {
            FilterRow(groups.map { it to (it ?: "全部") }, group, { group = it }, Modifier.padding(top = 12.dp))
        }
        item(key = "count") {
            Text(
                "共 ${list.size} 个动作",
                style = Brand.type.note,
                color = c.muted,
                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
            // 一张长卡片里的一行：第一行圆上角、最后一行圆下角，中间用细线隔开
            val top = if (i == 0) 18.dp else 0.dp
            val bottom = if (i == list.lastIndex) 18.dp else 0.dp
            Column(
                itemMotion()
                    .clip(RoundedCornerShape(top, top, bottom, bottom))
                    .background(c.surface),
            ) {
                if (i > 0) ListDivider(start = 88.dp)
                ExerciseRow(e) { onOpen(e.id) }
            }
        }
    }
}

@Composable
private fun ExerciseRow(e: Exercise, onClick: () -> Unit) {
    val c = Brand.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssetImage(e.images.firstOrNull(), Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = Brand.type.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                "${e.equipment} · ${e.primary.joinToString("、")}",
                style = Brand.type.note,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(AppIcons.Chevron, contentDescription = null, tint = c.faint, modifier = Modifier.size(18.dp))
    }
}

/** 一行可以横向滑动的筛选标签，选中是浅绿底 */
@Composable
fun <T> FilterRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Brand.colors
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val bg by animateColorAsState(if (on) c.brandSoft else Color.Transparent, tween(Motion.SHORT), label = "filter")
            Text(
                label,
                style = if (on) Brand.type.labelBold else Brand.type.label,
                color = if (on) c.brand else c.text,
                maxLines = 1,
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

/** 小缩略图（训练页用） */
@Composable
fun ExerciseThumb(e: Exercise?, size: Int = 56) {
    AssetImage(e?.images?.firstOrNull(), Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)))
}

/** 动作详情：起始 / 结束姿势自动交替演示，点开可全屏缩放；下面是中文要领 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(exercise: Exercise?) {
    val c = Brand.colors
    val scroll = rememberScrollState()
    TrackTopBar(scroll)
    if (exercise == null) {
        Text("找不到这个动作", Modifier.padding(screenPadding()), style = Brand.type.body, color = c.muted)
        return
    }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(exercise.id) {
        if (exercise.images.size < 2 || reduceMotion) return@LaunchedEffect
        while (true) {
            delay(1400)
            frame = (frame + 1) % exercise.images.size
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(screenPadding(extraBottom = 32.dp))) {
        // 动作演示
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface2)
                .clickable { viewerIndex = frame },
        ) {
            Crossfade(targetState = frame, animationSpec = tween(Motion.LONG, easing = Motion.Emphasized), label = "demo") { i ->
                AssetImage(exercise.images.getOrNull(i), Modifier.fillMaxSize())
            }
            if (exercise.images.size > 1) {
                OverlayLabel(if (frame == 0) "起始姿势" else "结束姿势", Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            Box(
                Modifier.align(Alignment.BottomEnd).padding(12.dp).size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ZoomIn, contentDescription = "放大查看", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
        if (exercise.images.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                exercise.images.forEachIndexed { i, path ->
                    val on = i == frame
                    val border by animateColorAsState(if (on) c.brand else Color.Transparent, tween(Motion.MEDIUM), label = "frame")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { viewerIndex = i }) {
                        AssetImage(
                            path,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.35f)
                                .clip(RoundedCornerShape(14.dp))
                                .border(2.dp, border, RoundedCornerShape(14.dp)),
                        )
                        Text(
                            if (i == 0) "起始 · 点击放大" else "结束 · 点击放大",
                            style = Brand.type.note,
                            color = if (on) c.brand else c.muted,
                            modifier = Modifier.padding(start = 2.dp, top = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(exercise.name, style = Brand.type.h1, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(exercise.nameEn, style = Brand.type.label, color = c.muted)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(exercise.equipment)
            exercise.level?.let { Tag(it) }
            exercise.primary.forEach { Tag(it, kind = TagKind.Good) }
            exercise.secondary.forEach { Tag(it, kind = TagKind.More) }
        }

        Spacer(Modifier.height(18.dp))
        BCard {
            Text("动作要领", style = Brand.type.title, color = c.text)
            Spacer(Modifier.height(8.dp))
            exercise.steps.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 7.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(c.brandSoft), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = Brand.type.nSub2, color = c.brand)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(s, style = Brand.type.body, color = c.text, modifier = Modifier.weight(1f).padding(top = 2.dp))
                }
            }
        }
        Text(
            "示意图来源：free-exercise-db（公共领域）",
            style = Brand.type.note,
            color = c.faint,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp),
        )
    }

    viewerIndex?.let { start ->
        ImageViewer(exercise.images, start) { viewerIndex = null }
    }
}

/** 压在图片上的小标签：半透明深底白字 */
@Composable
private fun OverlayLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Brand.type.noteBold,
        color = Color.White,
        modifier = modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 全屏看图：左右滑动切换，双指缩放，双击放大 / 还原 */
@Composable
fun ImageViewer(images: List<String>, start: Int, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pager = rememberPagerState(initialPage = start.coerceIn(0, (images.size - 1).coerceAtLeast(0))) { images.size }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableAsset(images[page])
            }
            Row(
                Modifier.align(Alignment.TopCenter).safeDrawingPadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pager.currentPage + 1} / ${images.size} · ${if (pager.currentPage == 0) "起始姿势" else "结束姿势"}",
                    color = Color.White,
                    style = Brand.type.labelBold,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Close, contentDescription = "关闭", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                "双指缩放 · 双击放大 · 左右滑动切换",
                color = Color.White.copy(alpha = 0.65f),
                style = Brand.type.note,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
            )
        }
    }
}

@Composable
private fun ZoomableAsset(path: String) {
    Zoomable { zoom ->
        AssetImage(path, Modifier.fillMaxWidth().then(zoom), contentScale = ContentScale.Fit)
    }
}
