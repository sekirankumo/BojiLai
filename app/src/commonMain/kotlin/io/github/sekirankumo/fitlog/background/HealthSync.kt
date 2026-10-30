package io.github.sekirankumo.fitlog.background

import androidx.compose.runtime.Composable

/**
 * 从系统的健康数据读取体重和体脂：安卓是 Health Connect（健康连接），iOS 是 Apple「健康」。
 * 只有用户在设置里点「开启同步」才会申请授权，之后每次打开 App 自动同步。
 */
interface HealthSync {
    /** 系统里这个健康数据服务的名字 */
    val serviceName: String

    /** 设置页里的说明 */
    val description: String

    /** 不能用时的原因（这台手机不支持、需要更新等）；能用返回 null */
    fun unavailableReason(): String?

    fun isAvailable(): Boolean = unavailableReason() == null

    /** 已经开启同步（有读取权限） */
    suspend fun canReadBody(): Boolean

    suspend fun lastSync(): Long?

    /** 同步体重和体脂，返回新增的记录数 */
    suspend fun sync(): Int

    /** 停止同步，已经同步进来的记录保留 */
    suspend fun revoke()

    /** 停止同步前的确认文字 */
    val stopText: String

    /** 打开系统里的健康数据设置；没有对应页面时返回 false */
    fun openSettings(): Boolean

    /** 申请读取权限，返回启动函数；授权结束（无论结果）回调 onDone */
    @Composable
    fun rememberEnable(onDone: () -> Unit): () -> Unit
}
