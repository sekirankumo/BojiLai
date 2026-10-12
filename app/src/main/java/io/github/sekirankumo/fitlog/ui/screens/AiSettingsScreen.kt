package io.github.sekirankumo.fitlog.ui.screens

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.util.UUID

@Composable
fun AiSettingsScreen(container: AppContainer) {
    val repo = container.aiSettings
    val client = container.aiClient
    val settings by repo.settings.collectAsState(initial = AiSettings())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<AiProvider?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        SectionHeader("AI 服务")
        Text(
            "支持任何 OpenAI 兼容接口。Key 只保存在本机，不会上传或进入系统备份。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        settings.providers.forEach { p ->
            ListItem(
                modifier = Modifier.clickable { editing = p },
                headlineContent = { Text(p.name) },
                supportingContent = { Text(p.baseUrl) },
            )
        }
        TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("添加服务")
        }
        HorizontalDivider()

        SectionHeader("模型分配")
        ModelSlot(
            title = "AI 助手",
            hint = "对话和操控 App，需要支持工具调用（function calling）",
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
        HorizontalDivider(Modifier.padding(top = 8.dp))

        SectionHeader("行为")
        ListItem(
            headlineContent = { Text("AI 修改数据前先让我确认") },
            supportingContent = { Text("关闭后 AI 会直接执行修改，仍可在对话里撤销") },
            trailingContent = {
                Switch(
                    checked = !settings.autoExecuteWrites,
                    onCheckedChange = { confirm -> scope.launch { repo.update { it.copy(autoExecuteWrites = !confirm) } } },
                )
            },
        )
    }

    if (adding || editing != null) {
        ProviderDialog(
            initial = editing,
            onDismiss = { adding = false; editing = null },
            onSave = { p ->
                scope.launch {
                    repo.update { s ->
                        val list = if (s.providers.any { it.id == p.id }) {
                            s.providers.map { if (it.id == p.id) p else it }
                        } else {
                            s.providers + p
                        }
                        s.copy(providers = list)
                    }
                }
                adding = false
                editing = null
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
                editing = null
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
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

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Box {
                OutlinedButton(onClick = { providerMenu = true }, enabled = providers.isNotEmpty()) {
                    Text(provider?.name ?: if (providers.isEmpty()) "请先添加服务" else "选择服务")
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                    providers.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
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

            Box {
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("模型名") },
                    singleLine = true,
                    enabled = provider != null,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(enabled = provider != null && !working, onClick = {
                            val p = provider ?: return@IconButton
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
                        }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "获取模型列表")
                        }
                    },
                )
                DropdownMenu(
                    expanded = modelMenu,
                    onDismissRequest = { modelMenu = false },
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    models.forEach { m ->
                        DropdownMenuItem(text = { Text(m) }, onClick = {
                            modelMenu = false
                            modelText = m
                            commit(m)
                        })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = provider != null && modelText.isNotBlank() && modelText.trim() != ref?.model,
                    onClick = { commit() },
                ) { Text("保存模型名") }
                TextButton(
                    enabled = provider != null && modelText.isNotBlank() && !working,
                    onClick = {
                        val p = provider ?: return@TextButton
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
                    },
                ) { Text("测试") }
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ProviderDialog(
    initial: AiProvider?,
    onDismiss: () -> Unit,
    onSave: (AiProvider) -> Unit,
    onDelete: (AiProvider) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var presetMenu by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    fun applyPreset(p: ProviderPreset) {
        if (p.baseUrl.isNotEmpty()) {
            name = p.name
            baseUrl = p.baseUrl
        }
        hint = p.hint
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加 AI 服务" else "编辑 AI 服务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { presetMenu = true }) {
                        Text("从常用服务选择")
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = presetMenu, onDismissRequest = { presetMenu = false }) {
                        PROVIDER_PRESETS.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = { presetMenu = false; applyPreset(p) },
                            )
                        }
                    }
                }
                hint?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("接口地址（Base URL）") }, singleLine = true)
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "隐藏" else "显示",
                            )
                        }
                    },
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && apiKey.isNotBlank(),
                onClick = {
                    onSave(
                        AiProvider(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey.trim(),
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onDelete(initial) }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
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
    val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
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
