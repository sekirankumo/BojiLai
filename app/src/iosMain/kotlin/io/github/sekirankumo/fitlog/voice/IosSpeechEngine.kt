package io.github.sekirankumo.fitlog.voice

import io.github.sekirankumo.fitlog.ios.IosPlatform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS 用系统自带的语音识别（优先在设备上识别），不用下载模型。
 * 需要麦克风和语音识别权限，由界面在开始前申请。
 */
class IosSpeechEngine : SpeechEngine {
    private val bridge get() = IosPlatform.bridge

    override val supported = true
    override val builtIn = true
    override val modelSizeMb = 0
    override val state: StateFlow<VoiceModelState> = MutableStateFlow(VoiceModelState.Ready)

    override fun startDownload() = Unit
    override fun cancelDownload() = Unit
    override suspend fun deleteModel() = Unit

    override suspend fun listen(
        finish: StopFlag,
        onText: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onReady: () -> Unit,
    ): String {
        val failed = CompletableDeferred<String>()
        var latest = ""
        bridge.speechStart(
            onText = { latest = it; onText(it) },
            onLevel = { onLevel(it) },
            onReady = { onReady() },
            onError = { failed.complete(it) },
        )
        try {
            // 等用户点「说完了」（或出错）
            while (!finish.get() && !failed.isCompleted) delay(50)
        } catch (e: CancellationException) {
            bridge.speechCancel()
            throw e
        }
        if (failed.isCompleted) throw IllegalStateException(failed.await())
        val final = CompletableDeferred<String>()
        bridge.speechStop { final.complete(it) }
        return final.await().ifEmpty { latest }
    }
}
