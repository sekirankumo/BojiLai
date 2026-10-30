package io.github.sekirankumo.fitlog.ios

/**
 * iOS 系统功能由 Swift 实现（iosApp/BojiLai/NativeBridgeImpl.swift），启动时传进来。
 * 用 Swift 写相机、相册、Apple 健康、语音识别、通知这些系统接口更直接、更稳。
 * 回调都可能在任意线程调用。
 */
interface NativeBridge {
    /** 外观：0 跟随系统，1 浅色，2 深色（状态栏、键盘、系统弹窗跟着变） */
    fun setInterfaceStyle(style: Int)

    fun openUrl(url: String)

    /** 打开系统设置里本 App 的页面 */
    fun openAppSettings()

    /** 麦克风 + 语音识别权限 */
    fun requestSpeechPermission(onResult: (Boolean) -> Unit)

    fun requestNotificationPermission(onResult: (Boolean) -> Unit)

    /** 马上发一条通知；route 是点开后跳转的页面 */
    fun showNotification(id: String, title: String, body: String, route: String)

    /** 在指定时间发通知（毫秒时间戳） */
    fun scheduleNotification(id: String, title: String, body: String, route: String, atEpochMs: Double)

    /** 取消 id 以 prefix 开头的、还没发出的通知，完成后回调 */
    fun cancelNotifications(prefix: String, onDone: () -> Unit)

    /** 切到后台后再多跑一会儿（生成训练计划用），返回任务号 */
    fun beginBackgroundTask(): Long
    fun endBackgroundTask(id: Long)

    /** 拍照，结果是转正后的 JPEG 文件；取消时回调空列表 */
    fun takePhoto(onResult: (List<NativePhoto>) -> Unit)

    /** 从相册选最多 max 张 */
    fun pickPhotos(max: Int, onResult: (List<NativePhoto>) -> Unit)

    /** 读取照片：缩小到长边 maxSide 后压成 JPEG，回调 base64；失败为 null */
    fun loadPhoto(path: String, maxSide: Int, onResult: (String?) -> Unit)

    /** 让用户选位置保存文件。回调：null 成功，"" 取消，其他为错误信息 */
    fun exportFile(name: String, text: String, onResult: (String?) -> Unit)

    /** 选一个文件读出文字。回调 (内容, 错误)，两个都为 null 表示取消 */
    fun importFile(onResult: (String?, String?) -> Unit)

    /** 这台设备有没有 Apple 健康 */
    fun healthAvailable(): Boolean

    /** 申请读取体重和体脂（iOS 不告诉 App 用户到底允许了没有，只回调弹窗是否正常结束） */
    fun healthRequest(onResult: (Boolean) -> Unit)

    /** 读取某个时间之后的体重（kind=weight，kg）和体脂（kind=fat，百分数）。回调 (样本, 错误) */
    fun healthRead(sinceEpochMs: Double, onResult: (List<NativeBodySample>?, String?) -> Unit)

    /** 打开「健康」App */
    fun openHealthApp(): Boolean

    /**
     * 开始听写（系统语音识别，优先在设备上识别）。
     * onText：到目前为止的全部文字；onLevel：音量 0–1；onReady：开始识别；onError：出错（之后不会再回调）
     */
    fun speechStart(
        onText: (String) -> Unit,
        onLevel: (Float) -> Unit,
        onReady: () -> Unit,
        onError: (String) -> Unit,
    )

    /** 结束听写，等最后一句识别完回调全部文字 */
    fun speechStop(onFinal: (String) -> Unit)

    /** 直接取消听写 */
    fun speechCancel()
}

/** 拍照或相册里的一张图：文件路径和拍摄时间（毫秒，不知道为 -1） */
data class NativePhoto(val path: String, val takenAtMs: Double)

/** Apple 健康里的一条记录 */
data class NativeBodySample(val id: String, val timeMs: Double, val kind: String, val value: Double)

/** Swift 启动时设置 */
object IosPlatform {
    lateinit var bridge: NativeBridge
}
