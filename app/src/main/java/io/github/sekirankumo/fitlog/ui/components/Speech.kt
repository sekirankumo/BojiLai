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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.voice.SpeechEngine
import io.github.sekirankumo.fitlog.voice.VoiceModelState
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** 内置离线语音识别，在 App 根部提供；为空时（如截图测试）只用系统语音识别 */
val LocalSpeechEngine = staticCompositionLocalOf<SpeechEngine?> { null }

/** 声波显示的竖条数（最近约 2 秒的音量） */
private const val WAVE_BARS = 40

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

/**
 * 开始语音前的准备：手机不支持内置识别时改用系统语音识别（结果走 onSystemResult），
 * 第一次使用先下载模型，再申请麦克风权限，都好了调用 onGranted。返回启动函数。
 */
@Composable
private fun rememberVoiceGate(
    prompt: String,
    onSystemResult: (String) -> Unit,
    onGranted: () -> Unit,
): () -> Unit {
    val engine = LocalSpeechEngine.current
    val context = LocalContext.current
    val systemCallback = rememberUpdatedState(onSystemResult)
    val grantedCallback = rememberUpdatedState(onGranted)
    var askDownload by remember { mutableStateOf(false) }

    val systemLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { systemCallback.value(it) }
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
            grantedCallback.value()
        } else {
            toast(context, "需要麦克风权限才能语音输入，可以在 系统设置 → 应用 → 薄肌来 → 权限 里打开")
        }
    }
    fun requestMic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            grantedCallback.value()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    if (askDownload && engine != null) {
        VoiceDownloadDialog(
            engine,
            onReady = {
                askDownload = false
                requestMic()
            },
            onDismiss = { askDownload = false },
        )
    }

    fun start() {
        when {
            engine == null || !engine.supported -> launchSystem()
            engine.state.value == VoiceModelState.Ready -> requestMic()
            else -> askDownload = true
        }
    }
    return { start() }
}

/**
 * 弹出面板式的语音输入（记一餐、训练打卡用），返回启动函数。
 * 一直听到点「说完了」为止，停顿处自动加逗号；结果通过 onResult 回调。
 */
@Composable
fun rememberSpeechInput(prompt: String = "请说话", onResult: (String) -> Unit): () -> Unit {
    val engine = LocalSpeechEngine.current
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    var listening by remember { mutableStateOf(false) }
    val start = rememberVoiceGate(prompt, onSystemResult = { callback.value(it) }, onGranted = { listening = true })

    if (listening && engine != null) {
        VoiceListenSheet(
            engine,
            prompt,
            onResult = { text ->
                listening = false
                if (text.isBlank()) toast(context, "没有听到说话，再试一次吧") else callback.value(text)
            },
            onError = { e ->
                listening = false
                toast(context, "语音识别出错：${e.message ?: e}")
            },
            onCancel = { listening = false },
        )
    }
    return start
}

/** 第一次使用：说明并下载语音模型，下载完自动继续 */
@Composable
private fun VoiceDownloadDialog(engine: SpeechEngine, onReady: () -> Unit, onDismiss: () -> Unit) {
    val c = Brand.colors
    val state by engine.state.collectAsState()
    LaunchedEffect(state) {
        if (state == VoiceModelState.Ready) onReady()
    }
    val downloading = state as? VoiceModelState.Downloading
    BSheet(
        onDismiss = {
            engine.cancelDownload()
            onDismiss()
        },
        title = "开启语音输入",
        actions = { s ->
            BButton("取消", Modifier.weight(1f), kind = ButtonKind.Soft) {
                engine.cancelDownload()
                s.close()
            }
            if (downloading == null) {
                BButton(if (state is VoiceModelState.Failed) "重试" else "下载", Modifier.weight(1f)) { engine.startDownload() }
            }
        },
    ) {
        Text(
            "语音识别在手机上离线完成，录音不会上传，也不用 API Key。" +
                "第一次使用需要下载语音模型（约 ${engine.modelSizeMb}MB），以后就不用再下载了，建议连上 Wi-Fi。",
            style = Brand.type.body,
            color = c.text,
        )
        if (downloading != null) {
            Spacer(Modifier.height(16.dp))
            ProgressLine(downloading.progress, height = 4.dp)
            Spacer(Modifier.height(6.dp))
            Text("正在下载 ${(downloading.progress * 100).toInt()}%", style = Brand.type.note, color = c.muted)
        }
        (state as? VoiceModelState.Failed)?.let {
            Spacer(Modifier.height(12.dp))
            Text(it.message, style = Brand.type.note, color = c.danger)
        }
    }
}

/** 录音面板：实时显示识别出的文字和声波，点「说完了」结束 */
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
    var text by remember { mutableStateOf("") }
    var levels by remember { mutableStateOf(List(WAVE_BARS) { 0f }) }
    var ready by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    // 切到后台或锁屏时按「说完了」处理，已经识别的文字不丢
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        stopping = true
        finish.set(true)
    }
    LaunchedEffect(Unit) {
        try {
            val result = engine.listen(
                finish,
                onText = { text = it },
                onLevel = { levels = levels.drop(1) + it },
                onReady = { ready = true },
            )
            onResult(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            onError(e)
        }
    }
    BSheet(onDismiss = onCancel, dismissible = !stopping) {
        VoicePanel(
            prompt = prompt,
            text = text,
            levels = levels,
            ready = ready,
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
    text: String,
    levels: List<Float>,
    ready: Boolean,
    stopping: Boolean,
    onCancel: () -> Unit,
    onFinish: () -> Unit,
) {
    val c = Brand.colors
    Column(Modifier.fillMaxWidth()) {
        Text(if (ready) "请说话…" else "正在准备…", style = Brand.type.h2, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(prompt, style = Brand.type.label, color = c.muted)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            if (ready) {
                VoiceWave(levels, c.brand, Modifier.fillMaxWidth().height(48.dp))
            } else {
                TypingDots(c.brand)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text.ifEmpty { " " },
            style = Brand.type.title.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, lineHeight = 26.sp),
            color = c.text,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (stopping) "正在识别最后一句…" else "说完点「说完了」，停顿会自动加逗号",
            style = Brand.type.note,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BButton("取消", Modifier.weight(1f), kind = ButtonKind.Soft, enabled = !stopping, onClick = onCancel)
            BButton("说完了", Modifier.weight(1f), enabled = !stopping, onClick = onFinish)
        }
    }
}

/** 声波：一排竖条，最右边是最新的音量 */
@Composable
fun VoiceWave(levels: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (levels.isEmpty()) return@Canvas
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (levels.size - 1)) / levels.size).coerceAtLeast(1f)
        val minHeight = 3.dp.toPx()
        levels.forEachIndexed { i, level ->
            // 开平方让小声也看得出起伏
            val h = (minHeight + (size.height - minHeight) * kotlin.math.sqrt(level.coerceIn(0f, 1f)))
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * (i + 1) / levels.size),
                topLeft = Offset(i * (barWidth + gap), (size.height - h) / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/**
 * 输入框里的连续听写（AI 助手用）：点一下开始，说的话实时写进输入框，
 * 不会自动停止，调用 [stop] 结束。
 */
@Stable
class Dictation internal constructor() {
    /** 正在听写 */
    var active by mutableStateOf(false)
        internal set

    /** 模型加载好、已经在识别 */
    var ready by mutableStateOf(false)
        internal set

    /** 最近的音量，用来画声波 */
    var levels by mutableStateOf(List(WAVE_BARS) { 0f })
        internal set

    internal var finish = AtomicBoolean(false)
    internal var starter: () -> Unit = {}

    fun start() {
        if (active) return
        finish = AtomicBoolean(false)
        starter()
    }

    /** 结束听写；最后一句识别完后回调 onFinished */
    fun stop() {
        finish.set(true)
    }
}

/**
 * @param onStart 开始听写时回调（记下输入框里原来的文字）
 * @param onText 到目前为止听写出的全部文字
 * @param onFinished 听写结束（最后一句也识别完了）
 */
@Composable
fun rememberDictation(
    onStart: () -> Unit,
    onText: (String) -> Unit,
    onFinished: () -> Unit = {},
): Dictation {
    val engine = LocalSpeechEngine.current
    val context = LocalContext.current
    val dictation = remember { Dictation() }
    val startCallback = rememberUpdatedState(onStart)
    val textCallback = rememberUpdatedState(onText)
    val finishedCallback = rememberUpdatedState(onFinished)
    var running by remember { mutableStateOf(false) }

    dictation.starter = rememberVoiceGate(
        "请说话",
        onSystemResult = {
            startCallback.value()
            textCallback.value(it)
            finishedCallback.value()
        },
        onGranted = { running = true },
    )

    if (running && engine != null) {
        // 离开页面、切到后台或锁屏时结束听写，已经识别的文字保留在输入框里
        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { dictation.stop() }
        LaunchedEffect(Unit) {
            dictation.active = true
            dictation.ready = false
            startCallback.value()
            try {
                val result = engine.listen(
                    dictation.finish,
                    onText = { textCallback.value(it) },
                    onLevel = { dictation.levels = dictation.levels.drop(1) + it },
                    onReady = { dictation.ready = true },
                )
                textCallback.value(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                toast(context, "语音识别出错：${e.message ?: e}")
            } finally {
                dictation.active = false
                dictation.ready = false
                dictation.levels = List(WAVE_BARS) { 0f }
                running = false
                finishedCallback.value()
            }
        }
    }
    return dictation
}
