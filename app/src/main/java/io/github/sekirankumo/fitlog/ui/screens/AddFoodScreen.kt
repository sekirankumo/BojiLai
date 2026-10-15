package io.github.sekirankumo.fitlog.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.diet.FoodDraft
import io.github.sekirankumo.fitlog.data.diet.FoodPhoto
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.Field
import io.github.sekirankumo.fitlog.ui.components.FigureNumber
import io.github.sekirankumo.fitlog.ui.components.FoodDraftEditor
import io.github.sekirankumo.fitlog.ui.components.HighSaltTag
import io.github.sekirankumo.fitlog.ui.components.LocalScreenInsets
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.Motion
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.cardMaterial
import io.github.sekirankumo.fitlog.ui.components.glass
import io.github.sekirankumo.fitlog.ui.components.itemMotion
import io.github.sekirankumo.fitlog.ui.components.pressScale
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
 * 也可以直接从「常吃」里选（横滑到最后是管理入口），或手动添加。保存栏是浮在底部的毛玻璃。
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
    val toast = LocalToast.current
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

    val insets = LocalScreenInsets.current
    val list = rememberLazyListState()
    TrackTopBar(list)
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = insets.top + 4.dp,
                bottom = insets.bottom + if (drafts.isNotEmpty()) 110.dp else 32.dp,
            ),
        ) {
            if (!isToday) {
                item(key = "backfill") {
                    Text(
                        "补记 ${day.monthValue}月${day.dayOfMonth}日 的饮食",
                        style = Brand.type.label.copy(fontWeight = FontWeight.Medium),
                        color = c.text,
                        modifier = Modifier.padding(bottom = 12.dp).clip(CircleShape).background(c.surface2).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // 输入区：拍照 / 相册 / 语音 + 文字描述
            item(key = "input") {
                BCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InputTile(Icons.Outlined.PhotoCamera, "拍照", Modifier.weight(1f)) { takePhoto() }
                        InputTile(Icons.Outlined.PhotoLibrary, "相册", Modifier.weight(1f)) {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        InputTile(Icons.Outlined.Mic, "语音", Modifier.weight(1f), onClick = speech)
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
                                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(c.surface2),
                                    )
                                    if (p.photo == null) {
                                        CircularProgressIndicator(Modifier.size(22.dp).align(Alignment.Center), strokeWidth = 2.dp, color = c.brand)
                                    }
                                    Box(
                                        Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color(0x80000000)).clickable { photos.remove(p) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(AppIcons.Close, contentDescription = "移除", tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Field(
                        text,
                        { text = it },
                        placeholder = "也可以直接描述，例如：中午吃了黄焖鸡米饭，米饭只吃了一半",
                        singleLine = false,
                        minLines = 2,
                    )
                    Spacer(Modifier.height(12.dp))
                    BButton(
                        if (busy) "AI 正在识别…" else "AI 识别热量",
                        Modifier.fillMaxWidth(),
                        icon = AppIcons.Sparkle,
                        height = 50.dp,
                        loading = busy,
                    ) { recognize() }
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = Brand.type.note, color = if (it.startsWith("识别失败")) c.danger else c.warn)
                    }
                }
            }

            if (favorites.isNotEmpty()) {
                item(key = "favorites") {
                    Column(Modifier.padding(top = 22.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("常吃", style = Brand.type.label, color = c.muted)
                            Text(" · 点一下直接加入，不用再识别", style = Brand.type.note, color = c.muted)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier
                                .layout { m, cons ->
                                    // 横滑条铺到屏幕两边，里面左右各留 16dp
                                    val extra = 32.dp.roundToPx()
                                    val w = cons.maxWidth + extra
                                    val p = m.measure(cons.copy(minWidth = w, maxWidth = w))
                                    layout(cons.maxWidth, p.height) { p.place(-16.dp.roundToPx(), 0) }
                                }
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            favorites.take(30).forEach { f ->
                                FavoriteChip(f.name, f.kcal.fmt(0)) { drafts += FoodDraft.fromFavorite(f, defaultTime) }
                            }
                            // 滑到最后是管理入口：修改、删除常吃
                            Row(
                                Modifier.height(38.dp).clip(CircleShape).background(c.surface2).clickable(onClick = openFavorites).padding(start = 14.dp, end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("管理", style = Brand.type.label.copy(fontWeight = FontWeight.Medium), color = c.muted)
                                Icon(AppIcons.Chevron, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            if (drafts.isNotEmpty()) {
                item(key = "draftsHead") {
                    Text(
                        "确认一下（${drafts.size} 项）",
                        style = Brand.type.label,
                        color = c.muted,
                        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
            }
            items(drafts, key = { it.key }) { d ->
                BCard(itemMotion().padding(bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                d.name.ifBlank { "新食物" },
                                style = Brand.type.title,
                                color = c.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (d.highSalt) {
                                Spacer(Modifier.width(6.dp))
                                HighSaltTag()
                            }
                        }
                        FigureNumber(d.kcal.fmt(0), "kcal", Brand.type.n3, unitStyle = Brand.type.nSub2, color = c.brand)
                        Box(
                            Modifier.padding(start = 4.dp).size(36.dp).clip(CircleShape).clickable { drafts.remove(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除这一项", tint = c.muted, modifier = Modifier.size(19.dp))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
                    Spacer(Modifier.height(14.dp))
                    FoodDraftEditor(d, { updated ->
                        val i = drafts.indexOfFirst { it.key == d.key }
                        if (i >= 0) drafts[i] = updated
                    })
                }
            }

            item(key = "manual") {
                Row(
                    Modifier
                        .padding(top = if (drafts.isEmpty()) 14.dp else 2.dp)
                        .clip(CircleShape)
                        .clickable { drafts += FoodDraft.blank(defaultTime) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.Plus, contentDescription = null, tint = c.brand, modifier = Modifier.size(18.dp))
                    Text(" 手动添加一项", style = Brand.type.label.copy(fontWeight = FontWeight.Medium), color = c.brand)
                }
            }
        }

        // 浮在底部的毛玻璃保存栏：有待保存的食物时才出现
        AnimatedVisibility(
            visible = drafts.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(Motion.MEDIUM)) + slideInVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedDecelerate)) { it / 2 },
            exit = fadeOut(tween(Motion.SHORT)) + slideOutVertically(tween(Motion.SHORT)) { it / 3 },
        ) {
            Row(
                Modifier
                    .navigationBarsPadding()
                    .padding(12.dp)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(26.dp))
                    .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("共 ${drafts.size} 项", style = Brand.type.note, color = c.muted)
                    FigureNumber(drafts.sumOf { it.kcal }.fmt(0), "kcal", Brand.type.n2, unitStyle = Brand.type.nSub2)
                }
                BButton("保存", Modifier.width(110.dp), enabled = drafts.all { it.name.isNotBlank() }, height = 46.dp) {
                    val toSave = drafts.toList()
                    container.appScope.launch { container.diet.saveDrafts(toSave) }
                    toast.show("已保存 ${toSave.size} 项")
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun InputTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Brand.colors
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressScale(interaction, 0.96f)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface2)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = c.brand, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = Brand.type.label.copy(fontWeight = FontWeight.Medium), color = c.text)
    }
}

/** 常吃：名称 + 热量，点一下加入 */
@Composable
private fun FavoriteChip(name: String, kcal: String, onClick: () -> Unit) {
    val c = Brand.colors
    Row(
        Modifier
            .height(38.dp)
            .cardMaterial(CircleShape)
            .clip(CircleShape)
            .background(c.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.StarOutline, contentDescription = null, tint = c.muted, modifier = Modifier.size(14.dp))
        Text(" $name ", style = Brand.type.label.copy(fontWeight = FontWeight.Medium), color = c.text, maxLines = 1)
        Text(kcal, style = Brand.type.nSub2.copy(fontWeight = FontWeight.SemiBold), color = c.muted)
    }
}
