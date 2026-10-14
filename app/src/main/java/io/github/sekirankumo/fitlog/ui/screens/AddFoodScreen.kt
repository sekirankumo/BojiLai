package io.github.sekirankumo.fitlog.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.data.diet.FoodPhoto
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.FoodDraftEditor
import io.github.sekirankumo.fitlog.ui.components.IconBadge
import io.github.sekirankumo.fitlog.ui.components.Pill
import io.github.sekirankumo.fitlog.ui.components.SectionHeader
import io.github.sekirankumo.fitlog.ui.components.rememberSpeechInput
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.ImageUtil
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private data class PickedPhoto(val uri: Uri, val photo: FoodPhoto?)

/**
 * 记一餐：拍照 / 相册 / 语音 / 文字都在这一页 → AI 识别 → 逐条确认修改 → 保存。
 * 也可以直接从「常吃」里选，或手动添加。
 */
@Composable
fun AddFoodScreen(
    container: AppContainer,
    day: LocalDate,
    onDone: () -> Unit,
    openFavorites: () -> Unit = {},
) {
    val c = Brand.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateListOf<PickedPhoto>() }
    val drafts = remember {
        mutableStateListOf<FoodDraft>().apply {
            addAll(container.draftInbox.value)
            container.draftInbox.value = emptyList()
        }
    }
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val favoritesFlow = remember { container.database.foodDao().observeFavorites() }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    val isToday = day == LocalDate.now()
    val defaultTime = if (isToday) System.currentTimeMillis() else TimeUtil.toEpoch(day.atTime(12, 0))

    fun addPhoto(uri: Uri) {
        photos += PickedPhoto(uri, null)
        scope.launch {
            val loaded = runCatching { ImageUtil.loadFoodPhoto(context, uri) }.getOrNull()
            val i = photos.indexOfFirst { it.uri == uri }
            if (i >= 0) {
                if (loaded == null) {
                    photos.removeAt(i)
                    message = "有一张图片无法读取"
                } else {
                    photos[i] = PickedPhoto(uri, loaded)
                }
            }
        }
    }

    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri?.let(Uri::parse)
        if (ok && uri != null) addPhoto(uri)
        pendingCameraUri = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        uris.forEach { addPhoto(it) }
    }
    val speech = rememberSpeechInput("说说吃了什么，比如：中午吃了一碗牛肉面") { said ->
        text = if (text.isBlank()) said else "$text，$said"
    }

    fun takePhoto() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "food_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri.toString()
        cameraLauncher.launch(uri)
    }

    fun recognize() {
        val ready = photos.mapNotNull { it.photo }
        if (ready.size < photos.size) {
            message = "图片还在处理，请稍等"
            return
        }
        if (ready.isEmpty() && text.isBlank()) {
            message = "先拍张照片，或者说说吃了什么"
            return
        }
        busy = true
        message = null
        scope.launch {
            try {
                val (items, note) = container.foodRecognizer.recognize(text, ready, day)
                drafts += items
                photos.clear()
                text = ""
                message = note
            } catch (e: Exception) {
                message = "识别失败：${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isToday) {
                item { Pill("补记 $day 的饮食", color = c.onAmber, background = c.amber) }
            }

            // 输入区：拍照 / 相册 / 语音 + 文字描述
            item {
                BCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InputTile(Icons.Filled.CameraAlt, "拍照", Modifier.weight(1f)) { takePhoto() }
                        InputTile(Icons.Filled.PhotoLibrary, "相册", Modifier.weight(1f)) {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        InputTile(Icons.Filled.Mic, "语音", Modifier.weight(1f), onClick = speech)
                    }
                    if (photos.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            photos.forEach { p ->
                                Box {
                                    AsyncImage(
                                        model = p.uri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(12.dp)),
                                    )
                                    if (p.photo == null) {
                                        CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.Center), strokeWidth = 2.dp)
                                    }
                                    Surface(
                                        onClick = { photos.remove(p) },
                                        shape = CircleShape,
                                        color = c.text.copy(alpha = 0.6f),
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "移除", tint = c.surface, modifier = Modifier.padding(3.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("也可以直接描述，例如：中午吃了黄焖鸡米饭，米饭只吃了一半") },
                        minLines = 2,
                        maxLines = 5,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = c.surface2,
                            focusedContainerColor = c.surface2,
                            unfocusedBorderColor = c.surface2,
                        ),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { recognize() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.onBrand)
                            Text("  AI 正在识别…")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  AI 识别热量")
                        }
                    }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("识别失败")) c.danger else c.onAmber)
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader("常吃")
                        Text("点一下直接加入，不用再识别", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            favorites.take(30).forEach { f ->
                                AssistChip(
                                    onClick = { drafts += FoodDraft.fromFavorite(f, defaultTime) },
                                    label = { Text("${f.name} · ${f.kcal.fmt(0)}") },
                                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.fat) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = c.surface),
                                    border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = c.line),
                                )
                            }
                            // 滑到最后是管理入口：修改、删除常吃
                            AssistChip(
                                onClick = openFavorites,
                                label = { Text("管理") },
                                trailingIcon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = c.muted) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = c.surface2),
                                border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = c.surface2),
                            )
                        }
                    }
                }
            }

            if (drafts.isNotEmpty()) {
                item { SectionHeader("确认一下（${drafts.size} 项）") }
            }
            items(drafts, key = { it.key }) { d ->
                BCard(Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            d.name.ifBlank { "新食物" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${d.kcal.fmt(0)} kcal", style = MaterialTheme.typography.titleMedium, color = c.brand)
                        IconButton(onClick = { drafts.remove(d) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "删除这一项", tint = c.muted)
                        }
                    }
                    HorizontalDivider(color = c.line)
                    Spacer(Modifier.height(10.dp))
                    FoodDraftEditor(d, { updated ->
                        val i = drafts.indexOfFirst { it.key == d.key }
                        if (i >= 0) drafts[i] = updated
                    })
                }
            }

            item {
                TextButton(onClick = { drafts += FoodDraft.blank(defaultTime) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" 手动添加一项")
                }
            }
        }

        if (drafts.isNotEmpty()) {
            Surface(color = c.surface) {
                Column {
                    HorizontalDivider(color = c.line)
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("共 ${drafts.size} 项", style = MaterialTheme.typography.labelMedium, color = c.muted)
                            Text("${drafts.sumOf { it.kcal }.fmt(0)} kcal", style = MaterialTheme.typography.titleLarge)
                        }
                        Button(
                            enabled = drafts.all { it.name.isNotBlank() },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.brand),
                            onClick = {
                                val toSave = drafts.toList()
                                container.appScope.launch { container.diet.saveDrafts(toSave) }
                                onDone()
                            },
                        ) { Text("保存", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Brand.colors
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = c.brandSoft, modifier = modifier) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(icon, tint = c.onBrand, background = c.brand, size = 40.dp, corner = 20.dp)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = c.brand)
        }
    }
}

