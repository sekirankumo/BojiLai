package io.github.sekirankumo.fitlog.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import io.github.sekirankumo.fitlog.assistant.ChatImage
import io.github.sekirankumo.fitlog.ui.components.MarkdownBlocks
import io.github.sekirankumo.fitlog.ui.components.MdBlock
import io.github.sekirankumo.fitlog.ui.components.PhotoViewer
import io.github.sekirankumo.fitlog.ui.components.parseMarkdown
import io.github.sekirankumo.fitlog.util.ImageUtil
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.assistant.AssistantEngine
import io.github.sekirankumo.fitlog.assistant.ChatItem
import io.github.sekirankumo.fitlog.ui.components.IconBadge
import io.github.sekirankumo.fitlog.ui.components.rememberSpeechInput
import io.github.sekirankumo.fitlog.ui.theme.Brand

private val SUGGESTIONS = listOf(
    "中午吃了一碗牛肉面，加了个卤蛋",
    "今天早上体重 72.5，体脂 18.2",
    "今天的卧推做不了，肩膀有点疼，换个动作",
    "深蹲 4 组都做完了，60 公斤",
    "下周出差没有器材，把计划改成徒手训练",
    "我这周蛋白质吃够了吗？",
    "撤销刚才的自动调整",
)

@Composable
fun AssistantScreen(engine: AssistantEngine) {
    val c = Brand.colors
    val items by engine.items.collectAsState()
    val busy by engine.busy.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val speech = rememberSpeechInput("请说") { said -> input = if (input.isBlank()) said else "$input，$said" }
    val status by engine.status.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachments = remember { mutableStateListOf<Attachment>() }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var attachMenu by remember { mutableStateOf(false) }

    fun attach(uri: Uri) {
        if (attachments.size >= MAX_IMAGES) return
        attachments += Attachment(uri, null)
        scope.launch {
            val photo = runCatching { ImageUtil.loadFoodPhoto(context, uri, maxSide = 1280) }.getOrNull()
            val i = attachments.indexOfFirst { it.uri == uri && it.image == null }
            if (i < 0) return@launch
            if (photo == null) {
                attachments.removeAt(i)
                Toast.makeText(context, "有一张图片无法读取", Toast.LENGTH_SHORT).show()
            } else {
                attachments[i] = Attachment(uri, ChatImage(uri.toString(), photo.dataUrl))
            }
        }
    }

    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri?.let(Uri::parse)
        if (ok && uri != null) attach(uri)
        pendingCameraUri = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
        val room = MAX_IMAGES - attachments.size
        uris.take(room).forEach { attach(it) }
        if (uris.size > room) Toast.makeText(context, "一次最多发 $MAX_IMAGES 张图片", Toast.LENGTH_SHORT).show()
    }
    fun takePhoto() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "chat_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri.toString()
        cameraLauncher.launch(uri)
    }

    // 从其他页面带过来的预填内容
    val prefill by engine.prefill.collectAsState()
    LaunchedEffect(prefill) {
        prefill?.let {
            input = it
            engine.prefill.value = null
        }
    }

    LaunchedEffect(items.size, busy) {
        val count = items.size + if (busy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item { Welcome { input = it } }
            }
            items(items, key = { it.id }) { item ->
                when (item) {
                    is ChatItem.User -> UserMessage(item) { index -> viewer = item.images to index }
                    is ChatItem.Assistant -> AssistantBubble(item.text)
                    is ChatItem.Action -> ActionCard(item, onUndo = { engine.undo(item) })
                    is ChatItem.Pending -> PendingCard(item, onDecide = engine::decide)
                    is ChatItem.Error -> ErrorCard(item.message)
                }
            }
            if (busy && items.none { it is ChatItem.Pending }) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  ${status ?: "正在思考…"}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
        }

        Surface(color = c.surface) {
            Column {
                HorizontalDivider(color = c.line)
                if (attachments.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        attachments.forEach { a ->
                            Box {
                                AsyncImage(
                                    model = a.uri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2),
                                )
                                if (a.image == null) {
                                    CircularProgressIndicator(Modifier.size(20.dp).align(Alignment.Center), strokeWidth = 2.dp)
                                }
                                Surface(
                                    onClick = { attachments.remove(a) },
                                    shape = CircleShape,
                                    color = c.text.copy(alpha = 0.6f),
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "移除", tint = c.surface, modifier = Modifier.padding(3.dp))
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = RoundedCornerShape(24.dp), color = c.surface2, modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(onClick = { attachMenu = true }, enabled = !busy && attachments.size < MAX_IMAGES) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "发图片", tint = c.brand)
                                }
                                DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }, containerColor = c.surface) {
                                    DropdownMenuItem(
                                        text = { Text("拍照") },
                                        leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                                        onClick = {
                                            attachMenu = false
                                            takePhoto()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("从相册选") },
                                        leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                        onClick = {
                                            attachMenu = false
                                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                    )
                                }
                            }
                            IconButton(onClick = speech, enabled = !busy) {
                                Icon(Icons.Filled.Mic, contentDescription = "语音输入", tint = c.brand)
                            }
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (attachments.isEmpty()) "说点什么" else "说说这张图") },
                                maxLines = 4,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    val imagesReady = attachments.all { it.image != null }
                    val canSend = !busy && imagesReady && (input.isNotBlank() || attachments.isNotEmpty())
                    Surface(
                        onClick = {
                            engine.send(input, attachments.mapNotNull { it.image })
                            input = ""
                            attachments.clear()
                        },
                        enabled = canSend,
                        shape = CircleShape,
                        color = if (canSend) c.brand else c.surface2,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (canSend) c.onBrand else c.muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    viewer?.let { (images, index) ->
        PhotoViewer(images.map(Uri::parse), index) { viewer = null }
    }
}

private const val MAX_IMAGES = 4

/** 输入框上方待发送的图片；image 为空表示还在压缩 */
private data class Attachment(val uri: Uri, val image: ChatImage?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(onPick: (String) -> Unit) {
    val c = Brand.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(Icons.Filled.AutoAwesome, tint = c.onBrand, background = c.brand, size = 64.dp, corner = 22.dp)
        Spacer(Modifier.height(14.dp))
        Text("有什么可以帮你？", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "记饮食、打卡、换动作、调整计划、查数据，\n说一句就行。修改前我会先问你。",
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SUGGESTIONS.forEach { s ->
                Surface(onClick = { onPick(s) }, shape = RoundedCornerShape(16.dp), color = c.surface, border = androidx.compose.foundation.BorderStroke(1.dp, c.line)) {
                    Text(s, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

/** 用户发的消息：图片在上，文字气泡在下，都靠右 */
@Composable
private fun UserMessage(item: ChatItem.User, onOpenImage: (Int) -> Unit) {
    val c = Brand.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (item.images.isNotEmpty()) {
            val size = if (item.images.size == 1) 168.dp else 96.dp
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.images.forEachIndexed { i, uri ->
                    AsyncImage(
                        model = Uri.parse(uri),
                        contentDescription = "图片 ${i + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(size)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surface2)
                            .clickable { onOpenImage(i) },
                    )
                }
            }
        }
        if (item.text.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                color = c.brand,
                modifier = Modifier.widthIn(max = 300.dp),
            ) {
                SelectionContainer {
                    Text(
                        item.text,
                        color = c.onBrand,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** AI 的回复：按 Markdown 排版；有表格或代码时气泡占满宽度 */
@Composable
private fun AssistantBubble(text: String) {
    val c = Brand.colors
    val blocks = remember(text) { parseMarkdown(text) }
    val wide = blocks.any { it is MdBlock.Table || it is MdBlock.Code }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            color = c.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, c.line),
            modifier = if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(max = 320.dp),
        ) {
            SelectionContainer {
                MarkdownBlocks(blocks, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun ActionCard(item: ChatItem.Action, onUndo: () -> Unit) {
    val c = Brand.colors
    Surface(shape = RoundedCornerShape(16.dp), color = c.brandSoft) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = c.brand, modifier = Modifier.size(18.dp))
            Text(
                item.summary + if (item.undone) "（已撤销）" else "",
                Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                textDecoration = if (item.undone) TextDecoration.LineThrough else null,
            )
            if (item.opId != null && !item.undone) {
                Surface(onClick = onUndo, shape = RoundedCornerShape(10.dp), color = c.surface) {
                    Text("撤销", style = MaterialTheme.typography.labelLarge, color = c.brand, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun PendingCard(item: ChatItem.Pending, onDecide: (Boolean) -> Unit) {
    val c = Brand.colors
    Surface(shape = RoundedCornerShape(16.dp), color = c.amber) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("AI 想要执行以下修改：", style = MaterialTheme.typography.titleSmall, color = c.onAmber, fontWeight = FontWeight.Bold)
            item.previews.forEach {
                Text("· $it", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = c.onAmber)
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                Surface(
                    onClick = { onDecide(false) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.dp, c.onAmber.copy(alpha = 0.4f)),
                ) {
                    Text("取消", color = c.onAmber, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                }
                Spacer(Modifier.width(8.dp))
                Surface(onClick = { onDecide(true) }, shape = RoundedCornerShape(12.dp), color = c.brand) {
                    Text("确认", color = c.onBrand, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val c = Brand.colors
    Surface(shape = RoundedCornerShape(16.dp), color = c.dangerSoft) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
            Text(message, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = c.danger)
        }
    }
}
