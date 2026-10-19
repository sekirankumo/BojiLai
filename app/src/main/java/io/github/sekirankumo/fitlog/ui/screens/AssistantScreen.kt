package io.github.sekirankumo.fitlog.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import io.github.sekirankumo.fitlog.assistant.AssistantEngine
import io.github.sekirankumo.fitlog.assistant.ChatImage
import io.github.sekirankumo.fitlog.assistant.ChatItem
import io.github.sekirankumo.fitlog.ui.components.AiOrb
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.LocalReduceMotion
import io.github.sekirankumo.fitlog.ui.components.LocalScreenInsets
import io.github.sekirankumo.fitlog.ui.components.MarkdownBlocks
import io.github.sekirankumo.fitlog.ui.components.Motion
import io.github.sekirankumo.fitlog.ui.components.PhotoViewer
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.TypingDots
import io.github.sekirankumo.fitlog.ui.components.VoiceWave
import io.github.sekirankumo.fitlog.ui.components.cardMaterial
import io.github.sekirankumo.fitlog.ui.components.glass
import io.github.sekirankumo.fitlog.ui.components.itemMotion
import io.github.sekirankumo.fitlog.ui.components.parseMarkdown
import io.github.sekirankumo.fitlog.ui.components.pressScale
import io.github.sekirankumo.fitlog.ui.components.rememberDictation
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.util.ImageUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val SUGGESTIONS = listOf(
    "中午吃了一碗牛肉面，加了个卤蛋",
    "今天早上体重 72.5，体脂 18.2",
    "今天的卧推做不了，肩膀有点疼，换个动作",
    "深蹲 4 组都做完了，60 公斤",
    "下周出差没有器材，把计划改成徒手训练",
    "我这周蛋白质吃够了吗？",
    "撤销刚才的自动调整",
)

/**
 * AI 助手：AI 的回答直接排在背景上（名字是正在用的模型名），用户的话是浅灰气泡；
 * 修改先给确认卡片，执行后的修改可以撤销。输入框是浮在底部的毛玻璃，可以发图片、语音听写。
 * @param openAiSettings 点欢迎页上的模型名（或「还没有配置」）去 AI 服务配置
 */
@Composable
fun AssistantScreen(engine: AssistantEngine, openAiSettings: () -> Unit = {}) {
    val c = Brand.colors
    val items by engine.items.collectAsState()
    val busy by engine.busy.collectAsState()
    val model by engine.modelName.collectAsState(initial = null)
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val status by engine.status.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val attachments = remember { mutableStateListOf<Attachment>() }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var attachMenu by remember { mutableStateOf(false) }
    // 打开页面时已经在的消息不再播入场动画，只有新来的回答按段落淡入
    val seen = remember { mutableSetOf<Long>().apply { addAll(items.map { it.id }) } }
    TrackTopBar(listState)

    fun send() {
        engine.send(input, attachments.mapNotNull { it.image })
        input = ""
        attachments.clear()
    }

    // 语音听写：说的话实时接在输入框原有文字后面；手动改了文字就不再覆盖
    var dictationBase by remember { mutableStateOf<String?>(null) }
    var sendAfterDictation by remember { mutableStateOf(false) }
    val dictation = rememberDictation(
        onStart = { dictationBase = input.trimEnd() },
        onText = { said -> dictationBase?.let { base -> input = appendDictation(base, said) } },
        onFinished = {
            dictationBase = null
            if (sendAfterDictation) {
                sendAfterDictation = false
                if (!engine.busy.value && (input.isNotBlank() || attachments.isNotEmpty())) send()
            }
        },
    )
    // 听写时点输入框：停止听写，方便直接修改
    val fieldInteraction = remember { MutableInteractionSource() }
    LaunchedEffect(fieldInteraction) {
        fieldInteraction.interactions.collect {
            if (it is PressInteraction.Press && dictation.active) dictation.stop()
        }
    }

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

    // 键盘弹出时列表的可见区域变矮：看列表实际变矮了多少，就把内容往上推多少，
    // 原来看得到的最后几行（通常是最新的消息）还停在输入框上面，不会被键盘挡住
    val ime = WindowInsets.ime
    LaunchedEffect(listState) {
        var last = 0
        snapshotFlow { listState.layoutInfo.viewportSize.height }.collect { h ->
            val shrink = last - h
            if (last > 0 && shrink > 0) listState.scrollBy(shrink.toFloat())
            last = h
        }
    }

    val insets = LocalScreenInsets.current
    // 键盘弹出后系统导航条被键盘盖住，不用再给它留位置
    val imeBottom = ime.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = insets.top + 8.dp, bottom = (insets.bottom - imeBottom).coerceAtLeast(0.dp) + 110.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (items.isEmpty()) {
                item { Welcome(model, openAiSettings) { input = it } }
            }
            items(items, key = { it.id }) { item ->
                val fresh = remember(item.id) { item.id !in seen }
                seen += item.id
                Box(itemMotion()) {
                    when (item) {
                        is ChatItem.User -> UserMessage(item) { index -> viewer = item.images to index }
                        is ChatItem.Assistant -> AssistantMessage(item, fresh)
                        is ChatItem.Action -> ActionCard(item, onUndo = { engine.undo(item) })
                        is ChatItem.Pending -> PendingCard(item, onDecide = engine::decide)
                        is ChatItem.Error -> ErrorCard(item.message)
                    }
                }
            }
            if (busy && items.none { it is ChatItem.Pending }) {
                item(key = "thinking") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = itemMotion()) {
                        AiOrb(18.dp, busy = true, sparkle = false, shadow = false)
                        Text("  ${status ?: "正在思考"} ", style = Brand.type.label, color = c.muted)
                        TypingDots(c.muted)
                    }
                }
            }
        }

        // 浮在底部的毛玻璃输入框
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .glass(RoundedCornerShape(26.dp), source = null)
                .padding(6.dp),
        ) {
            if (attachments.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEach { a ->
                        Box {
                            AsyncImage(
                                model = a.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2),
                            )
                            if (a.image == null) {
                                CircularProgressIndicator(Modifier.size(18.dp).align(Alignment.Center), strokeWidth = 2.dp, color = c.brand)
                            }
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).clip(CircleShape).background(Color(0x80000000)).clickable { attachments.remove(a) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(AppIcons.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Box {
                    ComposerButton(AppIcons.Plus, "发图片", c.muted, enabled = !busy && attachments.size < MAX_IMAGES) { attachMenu = true }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }, containerColor = c.surface) {
                        DropdownMenuItem(
                            text = { Text("拍照", style = Brand.type.body) },
                            leadingIcon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = c.brand) },
                            onClick = {
                                attachMenu = false
                                takePhoto()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("从相册选", style = Brand.type.body) },
                            leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = c.brand) },
                            onClick = {
                                attachMenu = false
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                    }
                }
                if (dictation.active) {
                    Box(Modifier.width(44.dp).height(42.dp).padding(start = 4.dp), contentAlignment = Alignment.Center) {
                        if (dictation.ready) VoiceWave(dictation.levels.takeLast(8), c.brand, Modifier.fillMaxWidth().height(22.dp)) else TypingDots(c.brand)
                    }
                }
                Box(Modifier.weight(1f).heightIn(min = 42.dp).padding(horizontal = 4.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (input.isEmpty()) {
                        Text(
                            when {
                                dictation.active -> "请说话…"
                                attachments.isEmpty() -> "说点什么"
                                else -> "说说这张图"
                            },
                            style = Brand.type.body,
                            color = c.muted,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = {
                            // 听写时手动改文字：停止听写，以手动修改为准
                            if (dictationBase != null) {
                                dictationBase = null
                                dictation.stop()
                            }
                            input = it
                        },
                        textStyle = Brand.type.body.copy(color = c.text),
                        cursorBrush = SolidColor(c.brand),
                        maxLines = 5,
                        interactionSource = fieldInteraction,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "消息" },
                    )
                }
                if (dictation.active) {
                    ComposerButton(Icons.Rounded.Stop, "结束语音输入", c.onBrand, background = c.brand) { dictation.stop() }
                } else {
                    ComposerButton(Icons.Outlined.Mic, "语音输入", c.muted, enabled = !busy) { dictation.start() }
                }
                val imagesReady = attachments.all { it.image != null }
                val canSend = !busy && imagesReady && (input.isNotBlank() || attachments.isNotEmpty())
                val sendBg by animateColorAsState(if (canSend) c.brand else c.surface2, tween(Motion.MEDIUM), label = "send")
                ComposerButton(AppIcons.Send, "发送", if (canSend) c.onBrand else c.muted, background = sendBg, enabled = canSend) {
                    // 听写中直接点发送：等最后一句识别完再发
                    if (dictation.active) {
                        sendAfterDictation = true
                        dictation.stop()
                    } else {
                        send()
                    }
                }
            }
        }
    }

    viewer?.let { (images, index) ->
        PhotoViewer(images.map(Uri::parse), index) { viewer = null }
    }
}

@Composable
private fun ComposerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(42.dp)
            .pressScale(interaction, 0.9f)
            .clip(CircleShape)
            .background(background)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
    }
}

private const val MAX_IMAGES = 4

/** 把听写的文字接在原有文字后面，中间没有标点时补一个逗号 */
private fun appendDictation(base: String, said: String): String = when {
    said.isEmpty() -> base
    base.isEmpty() -> said
    base.last() in "，。！？、；：,.!?;:" -> base + said
    else -> "$base，$said"
}

/** 输入框上方待发送的图片；image 为空表示还在压缩 */
private data class Attachment(val uri: Uri, val image: ChatImage?)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(model: String?, openAiSettings: () -> Unit, onPick: (String) -> Unit) {
    val c = Brand.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AiOrb(76.dp, sparkle = false)
        Spacer(Modifier.height(20.dp))
        Text("有什么可以帮你？", style = Brand.type.h2, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(
            "记饮食、打卡、换动作、调整计划、查数据，\n说一句就行。修改前我会先问你。",
            style = Brand.type.label,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        // 正在用哪个模型；点一下去换
        Text(
            model ?: "还没有配置 AI 服务，点这里去设置",
            style = if (model != null) Brand.type.nSub2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else Brand.type.note,
            color = if (model != null) c.muted else c.warn,
            modifier = Modifier
                .clip(CircleShape)
                .background(c.surface2)
                .clickable(onClick = openAiSettings)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SUGGESTIONS.forEach { s ->
                Text(
                    s,
                    style = Brand.type.label,
                    color = c.text,
                    modifier = Modifier
                        .cardMaterial(RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surface)
                        .clickable { onPick(s) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** 用户发的消息：图片在上，文字是浅灰气泡，都靠右 */
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
            SelectionContainer {
                Text(
                    item.text,
                    color = c.text,
                    style = Brand.type.body,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                        .background(c.surface2)
                        .padding(horizontal = 15.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** AI 的回答：上面是模型名，正文直接排在背景上，新来的按段落依次淡入 */
@Composable
private fun AssistantMessage(item: ChatItem.Assistant, fresh: Boolean) {
    val c = Brand.colors
    val blocks = remember(item.text) { parseMarkdown(item.text) }
    val animate = fresh && !LocalReduceMotion.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiOrb(16.dp, sparkle = false, shadow = false)
            Text("  ${item.model ?: "AI 助手"}", style = Brand.type.nSub2, color = c.muted)
        }
        Spacer(Modifier.height(6.dp))
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                blocks.forEachIndexed { i, block ->
                    val a = remember { Animatable(if (animate) 0f else 1f) }
                    LaunchedEffect(Unit) {
                        if (animate) {
                            delay(i * 90L)
                            a.animateTo(1f, tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate))
                        }
                    }
                    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 8.dp.toPx() }) {
                        MarkdownBlocks(listOf(block))
                    }
                }
            }
        }
    }
}

/** 已执行的修改：可以撤销；撤销后划掉 */
@Composable
private fun ActionCard(item: ChatItem.Action, onUndo: () -> Unit) {
    val c = Brand.colors
    Row(
        Modifier
            .fillMaxWidth()
            .cardMaterial(RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(if (item.undone) c.faint else c.brand), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Check, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(12.dp))
        }
        Text(
            item.summary + if (item.undone) "（已撤销）" else "",
            Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 4.dp),
            style = Brand.type.label,
            color = if (item.undone) c.muted else c.text,
            textDecoration = if (item.undone) TextDecoration.LineThrough else null,
        )
        if (item.opId != null && !item.undone) {
            BButton("撤销", kind = ButtonKind.Soft, height = 34.dp, onClick = onUndo)
        }
    }
}

/** 等确认的修改 */
@Composable
private fun PendingCard(item: ChatItem.Pending, onDecide: (Boolean) -> Unit) {
    val c = Brand.colors
    Column(
        Modifier
            .fillMaxWidth()
            .cardMaterial(RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text("AI 想要执行以下修改", style = Brand.type.note, color = c.muted)
        item.previews.forEach {
            Row(Modifier.padding(top = 8.dp)) {
                Text("·  ", style = Brand.type.body, color = c.brand)
                Text(it, style = Brand.type.body, color = c.text, modifier = Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            BButton("取消", kind = ButtonKind.Soft, height = 38.dp) { onDecide(false) }
            BButton("确认", height = 38.dp) { onDecide(true) }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    val c = Brand.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.dangerSoft).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(18.dp))
        Text(message, Modifier.padding(start = 8.dp), style = Brand.type.label, color = c.danger)
    }
}
