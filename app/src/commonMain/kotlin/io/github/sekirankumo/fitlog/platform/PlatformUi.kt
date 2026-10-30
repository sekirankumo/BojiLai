package io.github.sekirankumo.fitlog.platform

import androidx.compose.runtime.Composable
import io.github.sekirankumo.fitlog.data.diet.FoodPhoto
import kotlinx.datetime.LocalDate

/** 弹出一句提示（安卓用系统 Toast，iOS 用 App 里的提示条） */
@Composable
expect fun rememberShowMessage(): (String) -> Unit

enum class Permission { Microphone, Notifications }

/**
 * 申请权限，返回启动函数：已经有权限时直接回调 true，没有就弹系统的授权框。
 * 首次打开 App 不申请任何权限，只在用户打开对应功能时调用。
 */
@Composable
expect fun rememberPermissionRequest(permission: Permission, onResult: (Boolean) -> Unit): () -> Unit

/** 选日期 / 时间 */
interface Pickers {
    /** 依次选日期和时间，结果是毫秒时间戳 */
    fun pickDateTime(initial: Long, onPicked: (Long) -> Unit)
    fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit)

    /** 只选时间，参数和结果都是一天里的第几分钟 */
    fun pickTime(minuteOfDay: Int, onPicked: (Int) -> Unit)
}

@Composable
expect fun rememberPickers(): Pickers

/** 导出文件：让用户选保存位置，写入 content()；onResult 收到错误信息，成功为 null */
@Composable
expect fun rememberExportFile(onResult: (error: String?) -> Unit): (fileName: String, content: suspend () -> String) -> Unit

/** 选一个文本文件读出内容；取消时不回调 */
@Composable
expect fun rememberImportFile(onResult: (Result<String>) -> Unit): () -> Unit

/**
 * 拍照或从相册选的一张图。
 * @param uri 图片地址（安卓 content://，iOS file://），也给 Coil 显示缩略图用
 * @param takenAt 选图时已经知道的拍摄时间（iOS 相册），不知道为 null
 */
data class PhotoRef(val uri: String, val takenAt: Long? = null)

interface PhotoPicker {
    fun takePhoto()
    fun pickFromGallery()
}

/** @param maxItems 相册一次最多选几张 */
@Composable
expect fun rememberPhotoPicker(maxItems: Int, onPicked: (List<PhotoRef>) -> Unit): PhotoPicker

/** 读取照片：缩小到长边 maxSide、转正、压成 JPEG，并取出拍摄时间 */
expect suspend fun loadFoodPhoto(ref: PhotoRef, maxSide: Int = 1024): FoodPhoto

/** 内置识别不能用时改用系统的语音识别界面（安卓）；返回启动函数 */
@Composable
expect fun rememberSystemSpeech(prompt: String, onResult: (String) -> Unit): () -> Unit
