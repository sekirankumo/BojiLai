package io.github.sekirankumo.fitlog.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.sqrt

@Serializable
private data class ReleaseInfo(val assets: List<ReleaseAsset> = emptyList())

@Serializable
private data class ReleaseAsset(val name: String, val url: String)

/**
 * 内置离线语音识别：sherpa-onnx + 流式 Zipformer 中文模型，录音只在手机上处理。
 * 模型文件放在本项目 GitHub Release（asr-zh-v1），第一次使用时下载到 files/asr/。
 */
class SherpaSpeechEngine(private val context: Context, private val http: OkHttpClient) : SpeechEngine {
    private class ModelFile(val name: String, val size: Long, val sha256: String)

    private val dir = File(context.filesDir, "asr/$MODEL_TAG")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }

    /** 识别库只打包了 arm64-v8a，App 按其他架构安装时没有这个库 */
    override val supported: Boolean = File(context.applicationInfo.nativeLibraryDir ?: "", "libsherpa-onnx-jni.so").exists()

    /** 模型总大小（MB） */
    override val builtIn = false

    override val modelSizeMb: Int = (FILES.sumOf { it.size } / 1_000_000).toInt()

    private val _state = MutableStateFlow(
        when {
            !supported -> VoiceModelState.Unsupported
            FILES.all { File(dir, it.name).length() == it.size } -> VoiceModelState.Ready
            else -> VoiceModelState.Missing
        },
    )
    override val state: StateFlow<VoiceModelState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /** 在后台下载模型，关掉对话框也不会中断 */
    override fun startDownload() {
        if (!supported || downloadJob?.isActive == true || _state.value == VoiceModelState.Ready) return
        downloadJob = scope.launch { download() }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
    }

    private suspend fun download() {
        _state.value = VoiceModelState.Downloading(0f)
        try {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                val total = FILES.sumOf { it.size }.toFloat()
                var done = 0L
                val apiUrls = runCatching { releaseAssetUrls() }.getOrDefault(emptyMap())
                for (f in FILES) {
                    val target = File(dir, f.name)
                    if (target.length() == f.size && sha256(target) == f.sha256) {
                        done += f.size
                        continue
                    }
                    val tmp = File(dir, "${f.name}.part")
                    val base = done
                    // 先走 GitHub API（和 App 更新同一个通道），不行再用普通下载地址
                    val candidates = listOfNotNull(apiUrls[f.name], "$DIRECT_BASE/${f.name}")
                    var lastError: Exception? = null
                    for (url in candidates) {
                        try {
                            fetch(url, tmp) { n -> _state.value = VoiceModelState.Downloading(((base + n) / total).coerceAtMost(1f)) }
                            lastError = null
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            lastError = e
                        }
                    }
                    lastError?.let { throw it }
                    if (tmp.length() != f.size || sha256(tmp) != f.sha256) {
                        tmp.delete()
                        throw IOException("文件校验失败，可能是网络不稳定，请重试")
                    }
                    target.delete()
                    if (!tmp.renameTo(target)) throw IOException("保存模型失败")
                    done += f.size
                }
            }
            _state.value = VoiceModelState.Ready
        } catch (e: CancellationException) {
            _state.value = VoiceModelState.Missing
            throw e
        } catch (e: Exception) {
            _state.value = VoiceModelState.Failed(downloadMessage(e))
        }
    }

    private fun releaseAssetUrls(): Map<String, String> {
        val req = Request.Builder()
            .url("$API_BASE/releases/tags/$MODEL_TAG")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body!!.string()
        }
        return json.decodeFromString<ReleaseInfo>(body).assets.associate { it.name to it.url }
    }

    private suspend fun fetch(url: String, target: File, onBytes: (Long) -> Unit) {
        val req = Request.Builder().url(url).header("Accept", "application/octet-stream").build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            var done = 0L
            var lastReport = 0L
            resp.body!!.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 256 * 1024) {
                            lastReport = done
                            onBytes(done)
                        }
                    }
                }
            }
            onBytes(done)
        }
    }

    private fun downloadMessage(e: Exception): String = when (e) {
        is UnknownHostException -> "下载失败：网络连接不上，请检查网络后重试"
        is SocketTimeoutException -> "下载失败：网络超时，请重试"
        else -> "下载失败：${e.message ?: e.toString()}"
    }

    /** 删除已下载的模型，释放空间 */
    override suspend fun deleteModel() {
        cancelDownload()
        lock.withLock {
            recognizer?.release()
            recognizer = null
        }
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
        if (supported) _state.value = VoiceModelState.Missing
    }

    private val lock = Mutex()
    private var recognizer: OnlineRecognizer? = null
    private var releaseJob: Job? = null

    /**
     * 连续听写：一直录音识别，直到 [finish] 被置为 true 或协程被取消，不会因为停顿自动结束。
     * 说话中间停顿约 1 秒算一句，句与句之间自动加逗号。需要已有录音权限、模型已下载。
     * @param onText 到目前为止识别出的全部文字（随说话实时更新，前面的字可能被修正）
     * @param onLevel 音量 0–1，约每 50 毫秒一次，用来画声波
     * @param onReady 模型加载好、开始出字时回调（加载期间的声音会先存着，不会丢）
     * @return 最终识别结果，没听到说话时为空字符串
     */
    @SuppressLint("MissingPermission")
    override suspend fun listen(
        finish: StopFlag,
        onText: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onReady: () -> Unit,
    ): String = withContext(Dispatchers.Default) {
        lock.withLock {
            releaseJob?.cancel()
            // 先开麦克风再加载模型：加载的约 1 秒里说的话存在录音缓冲区里，加载完再识别
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val audio = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, SAMPLE_RATE * 2 * 4),
            )
            if (audio.state != AudioRecord.STATE_INITIALIZED) {
                audio.release()
                scheduleRelease()
                throw IOException("无法打开麦克风，可能被其他应用占用")
            }
            var stream: OnlineStream? = null
            val sentences = mutableListOf<String>()
            fun joined(partial: String) = (sentences + partial).filter { it.isNotEmpty() }.joinToString("，")
            var text = ""
            try {
                audio.startRecording()
                val rec = recognizer ?: createRecognizer().also { recognizer = it }
                val s = rec.createStream().also { stream = it }
                onReady()
                val buf = ShortArray(SAMPLE_RATE / 20)
                val started = SystemClock.elapsedRealtime()
                while (isActive && !finish.get()) {
                    val n = audio.read(buf, 0, buf.size)
                    if (n < 0) throw IOException("录音失败（$n）")
                    if (n == 0) continue
                    var sum = 0.0
                    val samples = FloatArray(n) { i ->
                        val v = buf[i] / 32768f
                        sum += v * v
                        v
                    }
                    onLevel(min(1f, sqrt(sum / n).toFloat() * 8f))
                    s.acceptWaveform(samples, SAMPLE_RATE)
                    while (rec.isReady(s)) rec.decode(s)
                    var partial = clean(rec.getResult(s).text)
                    // 一句说完（停顿了一下）：记下这句，从头识别下一句
                    if (rec.isEndpoint(s)) {
                        if (partial.isNotEmpty()) sentences += partial
                        partial = ""
                        rec.reset(s)
                    }
                    val now = joined(partial)
                    if (now != text) {
                        text = now
                        onText(text)
                    }
                    // 防止忘了关一直录：最长 10 分钟
                    if (SystemClock.elapsedRealtime() - started > MAX_LISTEN_MS) break
                }
                runCatching { audio.stop() }
                ensureActive()
                // 补一小段静音再结束，让最后一个字也能识别出来
                s.acceptWaveform(FloatArray(SAMPLE_RATE / 2), SAMPLE_RATE)
                s.inputFinished()
                while (rec.isReady(s)) rec.decode(s)
                text = joined(clean(rec.getResult(s).text))
            } finally {
                audio.release()
                stream?.release()
                scheduleRelease()
            }
            text
        }
    }

    /** 一段时间不用就释放模型占用的内存，下次用时再加载（约 1 秒） */
    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            lock.withLock {
                recognizer?.release()
                recognizer = null
            }
        }
    }

    private fun createRecognizer(): OnlineRecognizer {
        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                zipformer2Ctc = OnlineZipformer2CtcModelConfig(model = File(dir, "model.int8.onnx").absolutePath),
                tokens = File(dir, "tokens.txt").absolutePath,
                numThreads = 2,
                provider = "cpu",
            ),
            // 这里的「结束」只用来分句，不会停止录音。
            // 规则 1：一直没说话 3 秒，清空重来；规则 2：说完停顿 1 秒算一句；规则 3：一句最长 20 秒
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 3f, 0f),
                rule2 = EndpointRule(true, 1f, 0f),
                rule3 = EndpointRule(false, 0f, 20f),
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
        )
        return OnlineRecognizer(assetManager = null, config = config)
    }

    private companion object {
        const val MODEL_TAG = "asr-zh-v1"
        const val SAMPLE_RATE = 16000
        const val MAX_LISTEN_MS = 600_000L
        const val IDLE_RELEASE_MS = 120_000L

        /** https://api.github.com/repos/<owner>/<repo> */
        val API_BASE = io.github.sekirankumo.fitlog.update.UPDATE_URL.substringBefore("/releases")
        val DIRECT_BASE = "https://github.com/" + API_BASE.substringAfter("/repos/") + "/releases/download/$MODEL_TAG"

        /** sherpa-onnx-streaming-zipformer-small-ctc-zh-int8-2025-04-01（Apache-2.0） */
        val FILES = listOf(
            ModelFile("model.int8.onnx", 26_342_340, "68c9c943840f7d9cf3e8a4970ba50f404feb5277f611fa82b7e72267786fa84a"),
            ModelFile("tokens.txt", 13_366, "6fed8c6c248516f38e7faa19404b57413e8ce259f1cbc1fa4aebc86eac32fdfd"),
        )

        private val CJK_SPACE = Regex("""(?<=[㐀-鿿])\s+(?=[㐀-鿿])""")

        /** 中文字之间不要空格，英文单词保留空格 */
        fun clean(text: String): String = text.trim().replace(CJK_SPACE, "").lowercase()

        fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
