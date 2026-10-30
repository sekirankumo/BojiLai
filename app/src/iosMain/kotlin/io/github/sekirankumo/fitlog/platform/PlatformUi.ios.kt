package io.github.sekirankumo.fitlog.platform

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.sekirankumo.fitlog.data.diet.FoodPhoto
import io.github.sekirankumo.fitlog.ios.IosPlatform
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.util.TimeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.resume
import kotlin.time.Instant

private val bridge get() = IosPlatform.bridge

/** 回调可能不在主线程，切回主线程再改界面状态 */
private fun onMain(block: () -> Unit) {
    platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) { block() }
}

@Composable
actual fun rememberShowMessage(): (String) -> Unit {
    val toast = LocalToast.current
    return remember(toast) { { text -> toast.show(text) } }
}

@Composable
actual fun rememberPermissionRequest(permission: Permission, onResult: (Boolean) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember(permission) {
        {
            val done: (Boolean) -> Unit = { granted -> onMain { callback.value(granted) } }
            when (permission) {
                Permission.Microphone -> bridge.requestSpeechPermission(done)
                Permission.Notifications -> bridge.requestNotificationPermission(done)
            }
        }
    }
}

private sealed interface PickRequest {
    class Date(val initial: LocalDate, val onPicked: (LocalDate) -> Unit) : PickRequest

    /** 选日期时间：先选日期，再选时间 */
    class DateTime(val initial: Long, val onPicked: (Long) -> Unit, val date: LocalDate? = null) : PickRequest
    class Time(val minuteOfDay: Int, val onPicked: (Int) -> Unit) : PickRequest
}

private class ComposePickers : Pickers {
    var request by mutableStateOf<PickRequest?>(null)

    override fun pickDateTime(initial: Long, onPicked: (Long) -> Unit) {
        request = PickRequest.DateTime(initial, onPicked)
    }

    override fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        request = PickRequest.Date(initial, onPicked)
    }

    override fun pickTime(minuteOfDay: Int, onPicked: (Int) -> Unit) {
        request = PickRequest.Time(minuteOfDay, onPicked)
    }
}

/** iOS 没有安卓那样的系统选日期对话框，用 Material 的日期 / 时间选择器 */
@Composable
actual fun rememberPickers(): Pickers {
    val pickers = remember { ComposePickers() }
    when (val r = pickers.request) {
        is PickRequest.Date -> DateDialog(r.initial, onDismiss = { pickers.request = null }) {
            pickers.request = null
            r.onPicked(it)
        }
        is PickRequest.DateTime -> {
            val local = TimeUtil.toLocal(r.initial)
            if (r.date == null) {
                DateDialog(local.date, onDismiss = { pickers.request = null }) {
                    pickers.request = PickRequest.DateTime(r.initial, r.onPicked, it)
                }
            } else {
                TimeDialog(local.hour * 60 + local.minute, onDismiss = { pickers.request = null }) { m ->
                    pickers.request = null
                    r.onPicked(TimeUtil.toEpoch(r.date.atTime(m / 60, m % 60)))
                }
            }
        }
        is PickRequest.Time -> TimeDialog(r.minuteOfDay, onDismiss = { pickers.request = null }) {
            pickers.request = null
            r.onPicked(it)
        }
        null -> Unit
    }
    return pickers
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    // 日期选择器按 UTC 零点表示日期
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({
                val ms = state.selectedDateMillis
                if (ms != null) onPicked(Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date) else onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
    ) {
        DatePicker(state, showModeToggle = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minuteOfDay: Int, onDismiss: () -> Unit, onPicked: (Int) -> Unit) {
    val state = rememberTimePickerState(minuteOfDay / 60, minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onPicked(state.hour * 60 + state.minute) }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        text = { TimePicker(state) },
    )
}

@Composable
actual fun rememberExportFile(onResult: (error: String?) -> Unit): (fileName: String, content: suspend () -> String) -> Unit {
    val callback = rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    return remember {
        { name, content ->
            scope.launch {
                val text = try {
                    content()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    callback.value(e.message ?: e.toString())
                    return@launch
                }
                bridge.exportFile(name, text) { result ->
                    // 取消时不提示
                    if (result != "") onMain { callback.value(result) }
                }
            }
        }
    }
}

@Composable
actual fun rememberImportFile(onResult: (Result<String>) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    return remember {
        {
            bridge.importFile { text, error ->
                onMain {
                    when {
                        text != null -> callback.value(Result.success(text))
                        error != null -> callback.value(Result.failure(Exception(error)))
                    }
                }
            }
        }
    }
}

@Composable
actual fun rememberPhotoPicker(maxItems: Int, onPicked: (List<PhotoRef>) -> Unit): PhotoPicker {
    val callback = rememberUpdatedState(onPicked)
    return remember(maxItems) {
        object : PhotoPicker {
            private fun deliver(list: List<io.github.sekirankumo.fitlog.ios.NativePhoto>) {
                if (list.isEmpty()) return
                val refs = list.map { p ->
                    PhotoRef("file://" + p.path, p.takenAtMs.takeIf { it >= 0 }?.toLong())
                }
                onMain { callback.value(refs) }
            }

            override fun takePhoto() = bridge.takePhoto { deliver(it) }
            override fun pickFromGallery() = bridge.pickPhotos(maxItems) { deliver(it) }
        }
    }
}

actual suspend fun loadFoodPhoto(ref: PhotoRef, maxSide: Int): FoodPhoto = withContext(Dispatchers.Default) {
    val base64 = suspendCancellableCoroutine { cont ->
        bridge.loadPhoto(ref.uri.removePrefix("file://"), maxSide) { cont.resume(it) }
    } ?: error("无法读取图片")
    FoodPhoto("data:image/jpeg;base64,$base64", ref.takenAt)
}

/** iOS 的内置识别一直可用，不会走到这里 */
@Composable
actual fun rememberSystemSpeech(prompt: String, onResult: (String) -> Unit): () -> Unit {
    val show = rememberShowMessage()
    return { show("这台设备不支持语音识别，请在输入框里用键盘的听写") }
}
