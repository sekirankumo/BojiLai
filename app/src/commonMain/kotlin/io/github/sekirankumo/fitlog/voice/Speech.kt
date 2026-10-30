package io.github.sekirankumo.fitlog.voice

import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/** 离线语音模型的状态 */
sealed interface VoiceModelState {
    /** 不是 64 位 ARM 手机，没有内置识别库 */
    data object Unsupported : VoiceModelState
    data object Missing : VoiceModelState
    data class Downloading(val progress: Float) : VoiceModelState
    data object Ready : VoiceModelState
    data class Failed(val message: String) : VoiceModelState
}

/** 结束听写的开关：界面上点「说完了」时置为 true */
class StopFlag {
    @Volatile
    private var stopped = false

    fun get(): Boolean = stopped
    fun set(value: Boolean) {
        stopped = value
    }
}

/**
 * 离线语音识别：安卓用 sherpa-onnx（第一次使用下载模型），iOS 用系统自带的语音识别。
 */
interface SpeechEngine {
    /** 能用内置识别；为 false 时安卓改用系统语音识别 */
    val supported: Boolean

    /** 系统自带的识别（iOS），不需要下载模型 */
    val builtIn: Boolean

    /** 模型总大小（MB） */
    val modelSizeMb: Int

    val state: StateFlow<VoiceModelState>

    fun startDownload()
    fun cancelDownload()
    suspend fun deleteModel()

    /**
     * 连续听写：一直录音识别，直到 [finish] 被置为 true 或协程被取消，不会因为停顿自动结束。
     * 停顿处自动加逗号，返回全部文字。
     */
    suspend fun listen(
        finish: StopFlag,
        onText: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onReady: () -> Unit,
    ): String
}
