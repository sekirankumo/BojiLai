package io.github.sekirankumo.fitlog.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.db.KeyValueDao
import io.github.sekirankumo.fitlog.ios.IosPlatform
import io.github.sekirankumo.fitlog.ios.NativeBodySample
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.util.nowMillis
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

/**
 * 从 Apple「健康」读取体重和体脂。体脂秤、小米运动健康等 App 只要写进了「健康」，这里就能同步过来。
 * iOS 不告诉 App 用户到底允许了哪些数据，所以「已开启」以用户在设置里点过「开启同步」为准；
 * 没有授权时读出来是空的。
 */
class AppleHealthSync(
    private val bodyDao: BodyRecordDao,
    private val kv: KeyValueDao,
) : HealthSync {
    private val mutex = Mutex()
    private val bridge get() = IosPlatform.bridge

    override val serviceName = "健康"

    override val description =
        "从 iPhone 的「健康」App 读取体重和体脂。体脂秤、小米运动健康等 App，只要在它们的设置里打开写入「健康」，数据就能同步过来。开启后每次打开薄肌来会自动同步。"

    override val stopText = "之后不再从「健康」读取体重和体脂。已经同步进来的记录会保留，想删可以在身体页里删。如果想彻底收回权限，可以在「健康」App → 共享 → App 里关掉薄肌来。"

    override fun unavailableReason(): String? =
        if (bridge.healthAvailable()) null else "这台设备没有「健康」App"

    override suspend fun canReadBody(): Boolean = isAvailable() && kv.get(KEY_ENABLED) == "1"

    override suspend fun lastSync(): Long? = kv.get(KEY_LAST)?.toLongOrNull()

    override suspend fun sync(): Int = mutex.withLock {
        if (!isAvailable()) throw IllegalStateException("这台设备没有「健康」App")
        if (kv.get(KEY_ENABLED) != "1") throw IllegalStateException("还没有开启同步")

        val now = nowMillis()
        val since = (lastSync() ?: TimeUtil.daysAgo(90)) - 6 * 3600_000L
        val samples = read(since)
        val weights = samples.filter { it.kind == "weight" }
        val fats = samples.filter { it.kind == "fat" }

        var added = 0
        val usedFat = HashSet<String>()
        for (w in weights) {
            if (bodyDao.byExternalId(w.id) != null) continue
            val t = w.timeMs.toLong()
            // 同一次称重的体脂时间基本一致，10 分钟内视为同一次
            val fat = fats.filter { abs(it.timeMs.toLong() - t) < 10 * 60_000 }
                .minByOrNull { abs(it.timeMs.toLong() - t) }
            fat?.let { usedFat += it.id }
            bodyDao.insert(
                BodyRecord(
                    measuredAt = t,
                    weightKg = w.value,
                    bodyFatPct = fat?.value,
                    source = SOURCE,
                    externalId = w.id,
                )
            )
            added++
        }
        for (f in fats) {
            if (f.id in usedFat) continue
            val ext = "fat:" + f.id
            if (bodyDao.byExternalId(ext) != null) continue
            bodyDao.insert(
                BodyRecord(
                    measuredAt = f.timeMs.toLong(),
                    weightKg = null,
                    bodyFatPct = f.value,
                    source = SOURCE,
                    externalId = ext,
                )
            )
            added++
        }
        kv.put(KeyValue(KEY_LAST, now.toString()))
        added
    }

    private suspend fun read(since: Long): List<NativeBodySample> = suspendCancellableCoroutine { cont ->
        bridge.healthRead(since.toDouble()) { samples, error ->
            if (samples != null) cont.resume(samples) else cont.resumeWithException(IllegalStateException(error ?: "读取失败"))
        }
    }

    /** 停止同步：之后不再读取，已经同步进来的记录保留 */
    override suspend fun revoke() {
        kv.put(KeyValue(KEY_ENABLED, "0"))
    }

    override fun openSettings(): Boolean = bridge.openHealthApp()

    @Composable
    override fun rememberEnable(onDone: () -> Unit): () -> Unit {
        val callback = rememberUpdatedState(onDone)
        return remember {
            {
                bridge.healthRequest { ok ->
                    platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                        if (ok) {
                            // 标记为开启并同步一次后回调，设置页刷新状态
                            MainScope().launch {
                                kv.put(KeyValue(KEY_ENABLED, "1"))
                                runCatching { sync() }
                                callback.value()
                            }
                        } else {
                            callback.value()
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val KEY_LAST = "hk_last_sync"
        const val KEY_ENABLED = "hk_enabled"
        const val SOURCE = "apple_health"
    }
}
