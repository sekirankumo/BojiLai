package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.sekirankumo.fitlog.data.exercise.Exercise
import io.github.sekirankumo.fitlog.data.exercise.ExerciseRepository
import io.github.sekirankumo.fitlog.data.exercise.MUSCLE_GROUPS
import io.github.sekirankumo.fitlog.ui.components.AssetImage
import io.github.sekirankumo.fitlog.ui.components.Zoomable
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.Pill
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.delay

/** 动作库：搜索 + 按肌群筛选 */
@Composable
fun ExerciseLibraryScreen(repo: ExerciseRepository, onOpen: (String) -> Unit) {
    val c = Brand.colors
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = MUSCLE_GROUPS.keys.toList() + listOf("有氧", "拉伸")
    val list = repo.search(query.ifBlank { null }, group)

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索动作，例如：深蹲、卧推") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = c.surface,
                    focusedContainerColor = c.surface,
                    unfocusedBorderColor = c.line,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = group == null, onClick = { group = null }, label = { Text("全部") }, colors = chipColors())
                groups.forEach { g ->
                    FilterChip(selected = group == g, onClick = { group = if (group == g) null else g }, label = { Text(g) }, colors = chipColors())
                }
            }
        }
        item {
            Text("共 ${list.size} 个动作", style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        items(list, key = { it.id }) { e ->
            BCard(onClick = { onOpen(e.id) }, padding = PaddingValues(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AssetImage(e.images.firstOrNull(), Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${e.equipment} · ${e.primary.joinToString("、")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
                }
            }
        }
    }
}

@Composable
fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Brand.colors.surface,
    selectedContainerColor = Brand.colors.brand,
    selectedLabelColor = Brand.colors.onBrand,
    labelColor = Brand.colors.text,
)

/** 小缩略图（训练页用） */
@Composable
fun ExerciseThumb(e: Exercise?, size: Int = 56) {
    AssetImage(e?.images?.firstOrNull(), Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)))
}

/** 动作详情：自动播放的起始 / 结束姿势演示，点开可全屏缩放；下面是中文要领 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(exercise: Exercise?) {
    val c = Brand.colors
    if (exercise == null) {
        Text("找不到这个动作", Modifier.padding(16.dp))
        return
    }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val reduceMotion = io.github.sekirankumo.fitlog.ui.components.LocalReduceMotion.current
    LaunchedEffect(exercise.id) {
        if (exercise.images.size < 2 || reduceMotion) return@LaunchedEffect
        while (true) {
            delay(1100)
            frame = (frame + 1) % exercise.images.size
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 动作演示
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(24.dp))
                .background(c.surface2)
                .clickable { viewerIndex = frame },
        ) {
            Crossfade(targetState = frame, animationSpec = tween(350), label = "demo") { i ->
                AssetImage(exercise.images.getOrNull(i), Modifier.fillMaxSize())
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill(if (frame == 0) "起始姿势" else "结束姿势", color = Color.White, background = Color.Black.copy(alpha = 0.55f))
            }
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(34.dp),
            ) {
                Icon(Icons.Filled.ZoomIn, contentDescription = "放大查看", tint = Color.White, modifier = Modifier.padding(7.dp))
            }
        }
        if (exercise.images.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                exercise.images.forEachIndexed { i, path ->
                    Column(Modifier.weight(1f).clickable { viewerIndex = i }) {
                        AssetImage(path, Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(14.dp)))
                        Text(
                            if (i == 0) "起始 · 点击放大" else "结束 · 点击放大",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        Column {
            Text(exercise.name, style = MaterialTheme.typography.headlineMedium)
            Text(exercise.nameEn, style = MaterialTheme.typography.bodySmall, color = c.muted)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(exercise.equipment)
            exercise.level?.let { Pill(it, color = c.onAmber, background = c.amber) }
            exercise.primary.forEach { Pill(it, color = c.onInfo, background = c.info) }
            exercise.secondary.forEach { Pill(it, color = c.muted, background = c.surface2) }
        }

        BCard {
            Text("动作要领", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            exercise.steps.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(c.brand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", color = c.onBrand, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            "示意图来源：free-exercise-db（公共领域）",
            style = MaterialTheme.typography.labelSmall,
            color = c.muted,
        )
    }

    viewerIndex?.let { start ->
        ImageViewer(exercise.images, start) { viewerIndex = null }
    }
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
                Modifier.align(Alignment.TopCenter).safeDrawingPadding().fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pager.currentPage + 1} / ${images.size} · ${if (pager.currentPage == 0) "起始姿势" else "结束姿势"}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
            Text(
                "双指缩放 · 双击放大 · 左右滑动切换",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
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

