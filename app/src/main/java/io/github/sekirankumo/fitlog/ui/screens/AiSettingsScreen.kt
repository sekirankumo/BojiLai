package io.github.sekirankumo.fitlog.ui.screens

import io.github.sekirankumo.fitlog.ui.components.TagKind
import io.github.sekirankumo.fitlog.ui.components.Tag
import io.github.sekirankumo.fitlog.ui.components.AiOrb
import io.github.sekirankumo.fitlog.data.ai.presetFor
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import android.net.Uri
import android.content.Intent
import android.content.Context
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.data.ai.AiProvider
import io.github.sekirankumo.fitlog.data.ai.AiSettings
import io.github.sekirankumo.fitlog.data.ai.ModelRef
import io.github.sekirankumo.fitlog.data.ai.Msg
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.ai.PROVIDER_PRESETS
import io.github.sekirankumo.fitlog.data.ai.ProviderPreset
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.BSheet
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.Field
import io.github.sekirankumo.fitlog.ui.components.GroupLabel
import io.github.sekirankumo.fitlog.ui.components.ListCard
import io.github.sekirankumo.fitlog.ui.components.ListDivider
import io.github.sekirankumo.fitlog.ui.components.ListRow
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.ToggleSwitch
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.TypingDots
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.components.softFill
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/** AI 服务配置：服务列表、两个模型槽位（助手 / 图片识别）、修改前是否确认 */
@Composable
fun AiSettingsScreen(container: AppContainer) {
    val c = Brand.colors
    val repo = container.aiSettings
    val client = container.aiClient
    val loaded by repo.settings.collectAsState(initial = null)
    val settings = loaded ?: AiSettings()
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    var editing by remember { mutableStateOf<AiProvider?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingPreset by remember { mutableStateOf<ProviderPreset?>(null) }
    val scroll = rememberScrollState()
    TrackTopBar(scroll)

    // 还一个服务都没有时，默认选好智谱（有免费模型、国内能直接用）
    fun startAdd() {
        addingPreset = PROVIDER_PRESETS.first().takeIf { settings.providers.isEmpty() }
        adding = true
    }

    /** 新加的服务：自动把推荐模型配给空着的槽位，再测一次连接，结果用提示条告诉用户 */
    suspend fun autoSetup(p: AiProvider, preset: ProviderPreset) {
        if (preset.chatModels.isEmpty() && preset.visionModels.isEmpty()) return
        toast.show("正在连接 ${p.name}…")
        // 推荐模型和这家实际提供的模型对一下（厂商常改名）；拿不到列表就直接用推荐的
        val available = runCatching { client.listModels(p) }.getOrNull()?.takeIf { it.isNotEmpty() }
        fun pick(candidates: List<String>): String? =
            if (available == null) {
                candidates.firstOrNull()
            } else {
                candidates.firstNotNullOfOrNull { m -> available.firstOrNull { it.equals(m, ignoreCase = true) } }
            }
        val chat = pick(preset.chatModels)
        val vision = pick(preset.visionModels)
        var assigned = false
        repo.update { s ->
            val needChat = s.assistantModel == null || s.provider(s.assistantModel.providerId) == null
            val needVision = s.visionModel == null || s.provider(s.visionModel.providerId) == null
            assigned = needChat && chat != null
            s.copy(
                assistantModel = if (needChat && chat != null) ModelRef(p.id, chat) else s.assistantModel,
                visionModel = if (needVision && vision != null) ModelRef(p.id, vision) else s.visionModel,
            )
        }
        if (chat == null) {
            toast.show("已保存。没找到推荐的模型，请在「模型分配」里点列表按钮选一个")
            return
        }
        val result = runCatching { testToolCalling(client, p, chat) }.getOrElse { "✗ ${it.message}" }
        toast.show(
            when {
                result.startsWith("✓") -> if (assigned) "${p.name} 已连好，AI 助手会用 $chat" else "${p.name} 已连好"
                result.startsWith("△") -> "已连上，但 $chat 可能不支持工具调用，建议在下面换个模型"
                else -> "连接失败：${result.removePrefix("✗ ").take(60)}。检查一下 Key 是否复制完整"
            },
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(screenPadding(extraBottom = 32.dp))) {
        Text(
            "支持任何 OpenAI 兼容接口。Key 只保存在本机，不会上传或进入系统备份。",
            style = Brand.type.label,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        if (loaded != null && settings.providers.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            SetupCard { startAdd() }
        }
        GroupLabel("AI 服务")
        ListCard {
            settings.providers.forEach { p ->
                ListRow(p.name, subtitle = p.baseUrl, onClick = { editing = p })
                ListDivider()
            }
            Row(
                Modifier.fillMaxWidth().clickable { startAdd() }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Plus, contentDescription = null, tint = c.brand, modifier = Modifier.size(20.dp))
                Text("  添加服务", style = Brand.type.bodyMedium, color = c.brand)
            }
        }

        GroupLabel("模型分配")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ModelSlot(
                title = "AI 助手",
                hint = "对话和操控 App，需要支持工具调用（function calling）。助手回答时显示的名字就是这里的模型名",
                ref = settings.assistantModel,
                providers = settings.providers,
                client = client,
                onChange = { ref -> scope.launch { repo.update { it.copy(assistantModel = ref) } } },
                test = { p, m -> testToolCalling(client, p, m) },
            )
            ModelSlot(
                title = "图片识别",
                hint = "识别食物照片，需要支持看图",
                ref = settings.visionModel,
                providers = settings.providers,
                client = client,
                onChange = { ref -> scope.launch { repo.update { it.copy(visionModel = ref) } } },
                test = { p, m -> testVision(client, p, m) },
            )
        }

        GroupLabel("行为")
        ListCard {
            ListRow(
                "AI 修改数据前先让我确认",
                subtitle = "关闭后 AI 会直接执行修改，仍可在对话里撤销",
                trailing = {
                    ToggleSwitch(!settings.autoExecuteWrites, { confirm -> scope.launch { repo.update { it.copy(autoExecuteWrites = !confirm) } } })
                },
            )
        }
    }

    if (adding || editing != null) {
        ProviderSheet(
            initial = editing,
            preset = addingPreset.takeIf { editing == null },
            onDismiss = { adding = false; editing = null },
            onSave = { p, isNew, preset ->
                scope.launch {
                    repo.update { s ->
                        val list = if (s.providers.any { it.id == p.id }) s.providers.map { if (it.id == p.id) p else it } else s.providers + p
                        s.copy(providers = list)
                    }
                    if (isNew && preset != null) autoSetup(p, preset)
                }
            },
            onDelete = { p ->
                scope.launch {
                    repo.update { s ->
                        s.copy(
                            providers = s.providers.filterNot { it.id == p.id },
                            assistantModel = s.assistantModel?.takeIf { it.providerId != p.id },
                            visionModel = s.visionModel?.takeIf { it.providerId != p.id },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun ModelSlot(
    title: String,
    hint: String,
    ref: ModelRef?,
    providers: List<AiProvider>,
    client: OpenAiClient,
    onChange: (ModelRef?) -> Unit,
    test: suspend (AiProvider, String) -> String,
) {
    val c = Brand.colors
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    var providerMenu by remember { mutableStateOf(false) }
    var modelText by remember(ref) { mutableStateOf(ref?.model ?: "") }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelMenu by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val provider = providers.firstOrNull { it.id == ref?.providerId }

    fun commit(model: String = modelText) {
        val p = provider ?: return
        onChange(ModelRef(p.id, model.trim()).takeIf { it.model.isNotEmpty() })
    }

    BCard(Modifier.fillMaxWidth()) {
        Text(title, style = Brand.type.title, color = c.text)
        Spacer(Modifier.height(2.dp))
        Text(hint, style = Brand.type.note, color = c.muted)
        Spacer(Modifier.height(12.dp))
        Box {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(softFill())
                    .clickable(enabled = providers.isNotEmpty()) { providerMenu = true }
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    provider?.name ?: if (providers.isEmpty()) "请先添加服务" else "选择服务",
                    style = Brand.type.label.copy(fontWeight = FontWeight.Medium),
                    color = if (provider != null) c.text else c.muted,
                )
                Icon(AppIcons.ChevronDown, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }, containerColor = c.surface) {
                providers.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p.name, style = Brand.type.body) },
                        onClick = {
                            providerMenu = false
                            models = emptyList()
                            // 换服务时保留已填的模型名，用户可再改
                            onChange(ModelRef(p.id, modelText.trim()))
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(modelText, { modelText = it }, Modifier.weight(1f), label = "模型名", placeholder = "例如 glm-4-flash")
            Box {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(softFill())
                        .clickable(enabled = provider != null && !working) {
                            val p = provider ?: return@clickable
                            scope.launch {
                                working = true
                                status = null
                                try {
                                    models = client.listModels(p)
                                    modelMenu = models.isNotEmpty()
                                    if (models.isEmpty()) status = "没有获取到模型"
                                } catch (e: Exception) {
                                    status = e.message
                                } finally {
                                    working = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "获取模型列表", tint = c.muted, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }, modifier = Modifier.heightIn(max = 360.dp), containerColor = c.surface) {
                    models.forEach { m ->
                        DropdownMenuItem(text = { Text(m, style = Brand.type.nSub1) }, onClick = {
                            modelMenu = false
                            modelText = m
                            commit(m)
                            toast.show("已选择 $m")
                        })
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BButton(
                "保存模型名",
                kind = ButtonKind.Text,
                height = 38.dp,
                enabled = provider != null && modelText.isNotBlank() && modelText.trim() != ref?.model,
            ) {
                commit()
                toast.show("已保存模型名")
            }
            BButton("测试", kind = ButtonKind.Soft, height = 38.dp, enabled = provider != null && modelText.isNotBlank() && !working) {
                val p = provider ?: return@BButton
                commit()
                scope.launch {
                    working = true
                    status = "测试中…"
                    status = try {
                        test(p, modelText.trim())
                    } catch (e: Exception) {
                        "✗ ${e.message}"
                    }
                    working = false
                }
            }
            if (working) TypingDots(c.muted)
        }
        status?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = Brand.type.note, color = if (it.startsWith("✓")) c.brand else if (it.startsWith("✗")) c.danger else c.muted)
        }
    }
}

/** 还没有任何 AI 服务时，页面最上面的引导 */
@Composable
private fun SetupCard(onStart: () -> Unit) {
    val c = Brand.colors
    BCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiOrb(40.dp, shadow = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("还没有配置 AI 服务", style = Brand.type.title, color = c.text)
                Spacer(Modifier.height(3.dp))
                Text("AI 助手、拍照识别热量都要用到。跟着引导注册、复制一个 Key 就行，大约一分钟。", style = Brand.type.note, color = c.muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        BButton("开始配置", Modifier.fillMaxWidth(), height = 46.dp, onClick = onStart)
    }
}

/**
 * 添加 / 编辑 AI 服务。添加时先选服务，下面出现这家的引导：费用、能否直连、能否看图，
 * 「去获取 Key」直接打开创建 Key 的页面；用户复制了 Key 回来，自动从剪贴板填进去。
 * @param preset 打开时已经选好的服务（添加时）
 */
@Composable
private fun ProviderSheet(
    initial: AiProvider?,
    preset: ProviderPreset?,
    onDismiss: () -> Unit,
    onSave: (provider: AiProvider, isNew: Boolean, preset: ProviderPreset?) -> Unit,
    onDelete: (AiProvider) -> Unit,
) {
    val c = Brand.colors
    val context = LocalContext.current
    val adding = initial == null
    var picked by remember { mutableStateOf(if (adding) preset else initial?.let { presetFor(it.baseUrl) }) }
    var name by remember { mutableStateOf(initial?.name ?: preset?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: preset?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }
    // 只有用户点了「去获取 Key」再回来时才看剪贴板，平时不碰
    var wentForKey by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf(false) }

    fun applyPreset(p: ProviderPreset) {
        picked = p
        name = if (p.baseUrl.isNotEmpty()) p.name else ""
        baseUrl = p.baseUrl
    }

    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (!focused || !wentForKey) return@LaunchedEffect
        wentForKey = false
        val text = readClipboard(context) ?: return@LaunchedEffect
        if (looksLikeKey(text) && text != apiKey) {
            apiKey = text
            pasted = true
        }
    }

    val guide = picked?.takeIf { it.keyUrl != null }
    BSheet(
        onDismiss = onDismiss,
        title = if (adding) "添加 AI 服务" else "编辑 AI 服务",
        actions = { s ->
            if (initial != null) {
                BButton("删除", Modifier.weight(0.8f), kind = ButtonKind.Danger) { s.close { onDelete(initial) } }
            }
            BButton("取消", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() }
            BButton(
                if (adding && guide != null) "保存并连接" else "保存",
                Modifier.weight(1.2f),
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            ) {
                val p = AiProvider(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    name = name.trim(),
                    baseUrl = baseUrl.trim(),
                    apiKey = apiKey.trim(),
                )
                s.close { onSave(p, adding, picked) }
            }
        },
    ) {
        if (adding) {
            Text("选择服务", style = Brand.type.note, color = c.muted)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PROVIDER_PRESETS.forEach { p ->
                    val on = picked == p
                    Text(
                        p.name,
                        style = if (on) Brand.type.labelBold else Brand.type.label,
                        color = if (on) c.brand else c.text,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (on) c.brandSoft else softFill())
                            .clickable { applyPreset(p) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            picked?.let { p ->
                Spacer(Modifier.height(12.dp))
                if (guide != null) {
                    KeyGuide(guide) {
                        wentForKey = true
                        openUrl(context, guide.keyUrl!!)
                    }
                } else {
                    Text(p.intro, style = Brand.type.note, color = c.muted)
                }
            }
            Spacer(Modifier.height(16.dp))
        } else if (guide != null) {
            // 编辑时：Key 过期或填错了，可以直接去重新拿
            BButton("去${guide.name}重新获取 Key", kind = ButtonKind.Text, height = 36.dp) {
                wentForKey = true
                openUrl(context, guide.keyUrl!!)
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyField(apiKey, { apiKey = it; pasted = false }, showKey, Modifier.weight(1f))
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(softFill()).clickable { showKey = !showKey },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showKey) "隐藏" else "显示",
                    tint = c.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (pasted) {
            Text("已从剪贴板填入 Key", style = Brand.type.note, color = c.brand, modifier = Modifier.padding(start = 2.dp, top = 6.dp))
        }

        Spacer(Modifier.height(16.dp))
        if (adding && guide != null) {
            Text("接口信息（已按所选服务填好，一般不用改）", style = Brand.type.note, color = c.muted)
            Spacer(Modifier.height(8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(name, { name = it }, label = "名称")
            Field(baseUrl, { baseUrl = it }, label = "接口地址（Base URL）")
        }
    }
}

/** 某个服务的获取 Key 引导：几个标签、一句介绍、三步、去获取的按钮 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyGuide(p: ProviderPreset, onGetKey: () -> Unit) {
    val c = Brand.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(softFill()).padding(14.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(p.cost, kind = if (p.free) TagKind.Good else TagKind.Neutral)
            Tag(if (p.china) "国内可直连" else "需要能访问海外网站", kind = if (p.china) TagKind.Neutral else TagKind.Less)
            Tag(if (p.visionModels.isNotEmpty()) "能识别照片" else "不能识别照片", kind = if (p.visionModels.isNotEmpty()) TagKind.Neutral else TagKind.More)
        }
        Spacer(Modifier.height(10.dp))
        Text(p.intro, style = Brand.type.label, color = c.text)
        Spacer(Modifier.height(12.dp))
        listOf(
            "注册并登录${p.name}",
            "创建一个 API Key，点复制",
            "回到薄肌来，Key 会自动填好",
        ).forEachIndexed { i, step ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(c.brandSoft), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = Brand.type.nSub2, color = c.brand)
                }
                Spacer(Modifier.width(10.dp))
                Text(step, style = Brand.type.label, color = c.text)
            }
        }
        p.note?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = Brand.type.note, color = c.muted)
        }
        Spacer(Modifier.height(12.dp))
        BButton("去${p.name}获取 Key", Modifier.fillMaxWidth(), height = 46.dp, onClick = onGetKey)
    }
}

/** 用浏览器打开网页 */
private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** 读剪贴板里的第一段文字 */
private fun readClipboard(context: Context): String? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    if (clip == null || clip.itemCount == 0) null else clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
}.getOrNull()

/** 看起来像 API Key：一整段没有空格的字母数字（可带 . _ -），长度 20～200，字母和数字都有 */
private fun looksLikeKey(text: String): Boolean =
    text.length in 20..200 &&
        text.all { (it.isLetterOrDigit() && it.code < 128) || it in "._-" } &&
        text.any { it.isDigit() } &&
        text.any { it.isLetter() }

/** API Key 输入框：默认显示成圆点 */
@Composable
private fun KeyField(value: String, onValueChange: (String) -> Unit, show: Boolean, modifier: Modifier) {
    val c = Brand.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("API Key", style = Brand.type.note, color = c.muted, modifier = Modifier.padding(start = 2.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Brand.type.bodyMedium.copy(color = c.text),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.brand),
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 46.dp).clip(RoundedCornerShape(12.dp)).background(softFill()).padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { inner() }
            },
        )
    }
}

/** 让模型调用一个测试工具，检查是否支持工具调用 */
private suspend fun testToolCalling(client: OpenAiClient, provider: AiProvider, model: String): String {
    val tool = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", "record_weight")
            put("description", "记录体重")
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("kg") { put("type", "number") }
                }
            }
        }
    }
    val result = client.chat(provider, model, listOf(Msg.user("帮我记录体重 70 公斤")), listOf(tool))
    return if (result.toolCalls.isNotEmpty()) {
        "✓ 连接正常，支持工具调用"
    } else {
        "△ 连接正常，但模型没有调用工具，可能不支持工具调用。回复：${result.content?.take(80)}"
    }
}

/** 发一张纯红色图片，检查模型能否看图 */
private suspend fun testVision(client: OpenAiClient, provider: AiProvider, model: String): String {
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
    val png = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
    val dataUrl = "data:image/png;base64," + Base64.encodeToString(png, Base64.NO_WRAP)
    val result = client.chat(
        provider,
        model,
        listOf(Msg.userWithImages("这张图片是什么颜色？只回答颜色。", listOf(dataUrl))),
    )
    val answer = result.content.orEmpty()
    return if ("红" in answer || "red" in answer.lowercase()) {
        "✓ 连接正常，能识别图片（回答：${answer.take(20)}）"
    } else {
        "△ 有回复但可能看不到图片：${answer.take(80)}"
    }
}
