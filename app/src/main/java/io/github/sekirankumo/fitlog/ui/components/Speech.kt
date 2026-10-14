package io.github.sekirankumo.fitlog.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.voice.SpeechEngine
import io.github.sekirankumo.fitlog.voice.VoiceModelState
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** 内置离线语音识别，在 App 根部提供；为空时（如截图测试）只用系统语音识别 */
val LocalSpeechEngine = staticCompositionLocalOf<SpeechEngine?> { null }

private enum class SpeechPhase { Idle, AskDownload, Listening }

/**
 * 语音输入，返回一个启动函数，识别结果通过 onResult 回调。
 * 优先用内置的离线识别（第一次用时下载模型）；手机不支持时改用系统语音识别，
 * 都没有就提示用输入法的语音输入。
 */
@Composable
fun rememberSpeechInput(prompt: String = "请说话", onResult: (String) -> Unit): () -> Unit {
    val engine = LocalSpeechEngine.current
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    var phase by remember { mutableStateOf(SpeechPhase.Idle) }

    val systemLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { callback.value(it) }
        }
    }
    fun launchSystem() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            systemLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toast(context, "手机没有可用的语音识别，请在输入框里用输入法的语音输入")
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            phase = SpeechPhase.Listening
        } else {
            toast(context, "需要麦克风权限才能语音输入，可以在 系统设置 → 应用 → 薄肌来 → 权限 里打开")
        }
    }
    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            phase = SpeechPhase.Listening
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun start() {
        when {
            engine == null || !engine.supported -> launchSystem()
            engine.state.value == VoiceModelState.Ready -> startListening()
            else -> phase = SpeechPhase.AskDownload
        }
    }

    when (phase) {
        SpeechPhase.AskDownload -> if (engine != null) {
            VoiceDownloadDialog(
                engine,
                onReady = {
                    phase = SpeechPhase.Idle
                    startListening()
                },
                onDismiss = { phase = SpeechPhase.Idle },
            )
        }
        SpeechPhase.Listening -> if (engine != null) {
            VoiceListenSheet(
                engine,
                prompt,
                onResult = { text ->
                    phase = SpeechPhase.Idle
                    if (text.isBlank()) toast(context, "没有听清，再试一次吧") else callback.value(text)
                },
                onError = { e ->
                    phase = SpeechPhase.Idle
                    // 识别库加载不了（极少数机型），退回系统语音识别
                    if (e is LinkageError) launchSystem() else toast(context, "语音识别出错：${e.message ?: e}")
                },
                onCancel = { phase = SpeechPhase.Idle },
            )
        }
        SpeechPhase.Idle -> Unit
    }

    return { start() }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

/** 第一次使用：说明并下载语音模型，下载完自动开始听 */
@Composable
private fun VoiceDownloadDialog(engine: SpeechEngine, onReady: () -> Unit, onDismiss: () -> Unit) {
    val c = Brand.colors
    val state by engine.state.collectAsState()
    LaunchedEffect(state) {
        if (state == VoiceModelState.Ready) onReady()
    }
    val downloading = state as? VoiceModelState.Downloading
    AlertDialog(
        onDismissRequest = { if (downloading == null) onDismiss() },
        containerColor = c.surface,
        icon = { Icon(Icons.Filled.Mic, contentDescription = null, tint = c.brand) },
        title = { Text("开启语音输入") },
        text = {
            Column {
                Text(
                    "语音识别在手机上离线完成，录音不会上传，也不用 API Key。" +
                        "第一次使用需要下载语音模型（约 ${engine.modelSizeMb}MB），以后就不用再下载了，建议连上 Wi-Fi。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (downloading != null) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { downloading.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = c.brand,
                        trackColor = c.surface2,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("正在下载 ${(downloading.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                (state as? VoiceModelState.Failed)?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it.message, style = MaterialTheme.typography.bodySmall, color = c.danger)
                }
            }
        },
        confirmButton = {
            if (downloading == null) {
                TextButton(onClick = { engine.startDownload() }) {
                    Text(if (state is VoiceModelState.Failed) "重试" else "下载")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                engine.cancelDownload()
                onDismiss()
            }) { Text("取消") }
        },
    )
}

/** 录音面板：实时显示识别出的文字，停顿一下自动结束，也可以点「说完了」 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceListenSheet(
    engine: SpeechEngine,
    prompt: String,
    onResult: (String) -> Unit,
    onError: (Throwable) -> Unit,
    onCancel: () -> Unit,
) {
    val finish = remember { AtomicBoolean(false) }
    var partial by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var hearing by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val text = engine.listen(
                finish,
                onPartial = { partial = it },
                onLevel = {
                    level = it
                    hearing = true
                },
            )
            onResult(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onError(e)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Brand.colors.surface,
    ) {
        VoicePanel(
            prompt = prompt,
            partial = partial,
            level = level,
            hearing = hearing,
            stopping = stopping,
            onCancel = onCancel,
            onFinish = {
                stopping = true
                finish.set(true)
            },
        )
    }
}

/** 录音面板的内容（单独拿出来方便截图预览） */
@Composable
fun VoicePanel(
    prompt: String,
    partial: String,
    level: Float,
    hearing: Boolean,
    stopping: Boolean,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = Brand.colors
    val pulse by animateFloatAsState(1f + level * 0.45f, tween(120), label = "pulse")
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(
            partial.ifEmpty { if (hearing) "请说话…" else "正在准备…" },
            style = MaterialTheme.typography.titleLarge,
            color = if (partial.isEmpty()) c.muted else c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        )
        Spacer(Modifier.height(20.dp))
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(76.dp).scale(pulse).background(c.brandSoft, CircleShape))
            Box(Modifier.size(64.dp).background(c.brand, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = c.onBrand, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (stopping) "正在识别…" else "停顿一下会自动结束",
            style = MaterialTheme.typography.labelMedium,
            color = c.muted,
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(onClick = onCancel, shape = RoundedCornerShape(14.dp), color = c.surface2, modifier = Modifier.weight(1f)) {
                Text("取消", style = MaterialTheme.typography.labelLarge, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp))
            }
            Surface(onClick = onFinish, enabled = !stopping, shape = RoundedCornerShape(14.dp), color = c.brand, modifier = Modifier.weight(1f)) {
                Text("说完了", style = MaterialTheme.typography.labelLarge, color = c.onBrand, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp))
            }
        }
    }
}
