package io.github.sekirankumo.fitlog.platform

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import io.github.sekirankumo.fitlog.data.diet.FoodPhoto
import io.github.sekirankumo.fitlog.util.ImageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
actual fun rememberShowMessage(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text -> Toast.makeText(context, text, Toast.LENGTH_LONG).show() } }
}

private fun Permission.manifestName(): String? = when (this) {
    Permission.Microphone -> Manifest.permission.RECORD_AUDIO
    // 安卓 13 以下不需要申请通知权限
    Permission.Notifications ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
}

@Composable
actual fun rememberPermissionRequest(permission: Permission, onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callback.value(granted)
    }
    return {
        val name = permission.manifestName()
        if (name == null || ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED) {
            callback.value(true)
        } else {
            launcher.launch(name)
        }
    }
}

/** 安卓用系统的选日期 / 时间对话框 */
private class AndroidPickers(private val context: Context) : Pickers {
    private val zone get() = ZoneId.systemDefault()

    override fun pickDateTime(initial: Long, onPicked: (Long) -> Unit) {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(initial), zone)
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                val picked = local.withYear(y).withMonth(m + 1).withDayOfMonth(d).withHour(h).withMinute(min)
                onPicked(picked.atZone(zone).toInstant().toEpochMilli())
            }, local.hour, local.minute, true).show()
        }, local.year, local.monthValue - 1, local.dayOfMonth).show()
    }

    override fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(context, { _, y, m, d ->
            onPicked(LocalDate(y, m + 1, d))
        }, initial.year, initial.monthNumber - 1, initial.dayOfMonth).show()
    }

    override fun pickTime(minuteOfDay: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, minuteOfDay / 60, minuteOfDay % 60, true).show()
    }
}

@Composable
actual fun rememberPickers(): Pickers {
    val context = LocalContext.current
    return remember(context) { AndroidPickers(context) }
}

@Composable
actual fun rememberExportFile(onResult: (error: String?) -> Unit): (fileName: String, content: suspend () -> String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<(suspend () -> String)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pending
        pending = null
        if (uri != null && content != null) {
            scope.launch {
                val result = runCatching {
                    val text = content()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                            ?: error("无法写入文件")
                    }
                }
                callback.value(result.exceptionOrNull()?.let { it.message ?: it.toString() })
            }
        }
    }
    return { name, content ->
        pending = content
        launcher.launch(name)
    }
}

@Composable
actual fun rememberImportFile(onResult: (Result<String>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: error("无法读取文件")
                    }
                }
                callback.value(result)
            }
        }
    }
    return { launcher.launch(arrayOf("application/json", "*/*")) }
}

@Composable
actual fun rememberPhotoPicker(maxItems: Int, onPicked: (List<PhotoRef>) -> Unit): PhotoPicker {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onPicked)
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) callback.value(listOf(PhotoRef(uri)))
        pendingCameraUri = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems)) { uris ->
        if (uris.isNotEmpty()) callback.value(uris.map { PhotoRef(it.toString()) })
    }
    return remember(context) {
        object : PhotoPicker {
            override fun takePhoto() {
                val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                pendingCameraUri = uri.toString()
                cameraLauncher.launch(uri)
            }

            override fun pickFromGallery() {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
}

actual suspend fun loadFoodPhoto(ref: PhotoRef, maxSide: Int): FoodPhoto =
    ImageUtil.loadFoodPhoto(AndroidPlatform.context, Uri.parse(ref.uri), maxSide)

@Composable
actual fun rememberSystemSpeech(prompt: String, onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { callback.value(it) }
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "手机没有可用的语音识别，请在输入框里用输入法的语音输入", Toast.LENGTH_LONG).show()
        }
    }
}
