package io.github.sekirankumo.fitlog.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.data.diet.FoodPhoto
import io.github.sekirankumo.fitlog.ui.components.FoodDraftEditor
import io.github.sekirankumo.fitlog.ui.components.rememberSpeechInput
import io.github.sekirankumo.fitlog.util.ImageUtil
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.fmt
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private data class PickedPhoto(val uri: Uri, val photo: FoodPhoto?)

/**
 * 添加饮食：拍照 / 相册 / 语音 / 文字输入 → AI 识别 → 逐条确认修改 → 保存。
 * 也可以直接从「常吃」里选，或手动添加。
 */
@Composable
fun AddFoodScreen(container: AppContainer, mode: String, day: LocalDate, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateListOf<PickedPhoto>() }
    val drafts = remember { mutableStateListOf<FoodDraft>() }
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var started by rememberSaveable { mutableStateOf(false) }
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

    fun pickGallery() = galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // 从饮食页的按钮进来时，直接打开对应的输入方式
    LaunchedEffect(Unit) {
        if (!started) {
            started = true
            when (mode) {
                "camera" -> takePhoto()
                "gallery" -> pickGallery()
                "voice" -> speech()
            }
        }
    }

    fun recognize() {
        val ready = photos.mapNotNull { it.photo }
        if (ready.size < photos.size) {
            message = "图片还在处理，请稍等"
            return
        }
        if (ready.isEmpty() && text.isBlank()) {
            message = "请先拍照或描述吃了什么"
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isToday) {
                item { Text("补记 ${day} 的饮食", style = MaterialTheme.typography.titleSmall) }
            }
            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (photos.isNotEmpty()) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                photos.forEach { p ->
                                    Box {
                                        AsyncImage(
                                            model = p.uri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(88.dp).clip(RoundedCornerShape(8.dp)),
                                        )
                                        if (p.photo == null) {
                                            CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center), strokeWidth = 2.dp)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                                        ) {
                                            IconButton(onClick = { photos.remove(p) }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Filled.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("描述吃了什么，可以补充份量和时间，例如：中午公司楼下吃了黄焖鸡米饭，米饭只吃了一半") },
                            minLines = 2,
                            maxLines = 5,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { takePhoto() }) { Icon(Icons.Filled.CameraAlt, contentDescription = "拍照") }
                            IconButton(onClick = { pickGallery() }) { Icon(Icons.Filled.PhotoLibrary, contentDescription = "相册") }
                            IconButton(onClick = speech) { Icon(Icons.Filled.Mic, contentDescription = "语音") }
                            Box(Modifier.weight(1f))
                            Button(onClick = { recognize() }, enabled = !busy) {
                                if (busy) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Text("  AI 识别")
                            }
                        }
                        message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item {
                    Column {
                        Text("常吃（点一下直接加入）", style = MaterialTheme.typography.titleSmall)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            favorites.take(30).forEach { f ->
                                AssistChip(
                                    onClick = { drafts += FoodDraft.fromFavorite(f, defaultTime) },
                                    label = { Text("${f.name} ${f.kcal.fmt(0)}") },
                                )
                            }
                        }
                    }
                }
            }

            items(drafts, key = { it.key }) { d ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                d.name.ifBlank { "新食物" },
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { drafts.remove(d) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除这一项")
                            }
                        }
                        FoodDraftEditor(d, { updated ->
                            val i = drafts.indexOfFirst { it.key == d.key }
                            if (i >= 0) drafts[i] = updated
                        })
                    }
                }
            }

            item {
                OutlinedButton(onClick = { drafts += FoodDraft.blank(defaultTime) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(" 手动添加一项")
                }
            }
        }

        if (drafts.isNotEmpty()) {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "共 ${drafts.size} 项 · ${drafts.sumOf { it.kcal }.fmt(0)} kcal",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Button(
                        enabled = drafts.all { it.name.isNotBlank() },
                        onClick = {
                            val toSave = drafts.toList()
                            container.appScope.launch { container.diet.saveDrafts(toSave) }
                            onDone()
                        },
                    ) { Text("保存") }
                }
            }
        }
    }
}
