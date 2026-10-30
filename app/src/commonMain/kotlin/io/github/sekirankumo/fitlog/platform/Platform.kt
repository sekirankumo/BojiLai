package io.github.sekirankumo.fitlog.platform

import androidx.compose.ui.graphics.ImageBitmap
import io.github.sekirankumo.fitlog.resources.Res
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.ExperimentalResourceApi

/*
 * 安卓和 iOS 不一样的地方都在这个包里：这里是共用的接口，
 * 实现分别在 androidMain / iosMain 的同名包里。
 */

/** 是否 iOS（界面上少数文字要按系统说法写，比如「Apple 健康」「文件」App） */
expect val isIos: Boolean

/** 这台设备能不能做模糊效果（安卓 12 起才支持；iOS 都支持） */
expect val supportsBlur: Boolean

/** 当前版本号（安卓 versionCode / iOS CFBundleVersion）和版本名 */
expect val appVersionCode: Int
expect val appVersionName: String

/** 同步读取打包在 App 里的资源文件（composeResources/files 下的相对路径），读不到返回 null */
expect fun readResourceFile(path: String): ByteArray?

/** 把 JPEG / PNG 解码成图片 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** 资源文件给 Coil 用的地址 */
@OptIn(ExperimentalResourceApi::class)
fun resourceUri(path: String): String = Res.getUri("files/$path")

/** 发通知；open 是点开后跳转的页面路由 */
interface Notifier {
    fun show(id: Int, channel: String, title: String, text: String, open: String)

    companion object {
        const val CHANNEL_PLAN = "plan"
        const val CHANNEL_REMINDER = "reminder"
    }
}

/** 训练提醒：minuteOfDay 为 null 时取消 */
interface ReminderScheduler {
    fun schedule(minuteOfDay: Int?)
}

enum class JobStatus { Running, Succeeded, Failed, Cancelled }

data class PlanJob(val id: String, val status: JobStatus, val error: String? = null)

/** 生成训练计划的后台任务：离开页面也会继续，完成后发通知 */
interface PlanJobs {
    fun enqueue(args: String): String
    val current: Flow<PlanJob?>
}

/** 每天的自动备份写到哪里（安卓：下载/薄肌来；iOS：「文件」App 里的薄肌来文件夹） */
interface BackupStore {
    /** 写入（同名覆盖）一份备份 */
    suspend fun write(name: String, text: String): Boolean

    /** 只保留最新的 keep 份 */
    suspend fun prune(keep: Int)
}

/** 同步读写的小设置（打开 App 第一帧就要用的外观设置） */
interface SyncPrefs {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

enum class NetworkError { UnknownHost, Timeout, Ssl, Other }

/** 网络错误分类（用来给出中文提示）；不是网络错误返回 null */
expect fun classifyNetworkError(e: Throwable): NetworkError?
