package io.github.sekirankumo.fitlog.background

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.sekirankumo.fitlog.data.db.BodyRecord
import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import io.github.sekirankumo.fitlog.data.db.KeyValue
import io.github.sekirankumo.fitlog.data.db.KeyValueDao
import io.github.sekirankumo.fitlog.util.TimeUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.math.abs

/**
 * 从安卓的 Health Connect（健康连接）读取体重和体脂。小米运动健康、三星健康、体脂秤 App 等
 * 只要把数据写进 Health Connect，这里就能同步过来。
 * 只有用户在设置里点「开启同步」才会申请授权；Health Connect 只允许 App 在前台时读取，所以在打开 App 时同步。
 */
class AndroidHealthSync(
    private val context: Context,
    private val bodyDao: BodyRecordDao,
    private val kv: KeyValueDao,
) : HealthSync {
    override val serviceName = "Health Connect"

    override val description =
        "从安卓系统的 Health Connect（健康连接）读取体重和体脂。小米运动健康、三星健康、体脂秤 App 等，只要在它们的设置里打开 Health Connect 数据共享，数据就能同步过来。开启后每次打开薄肌来会自动同步。"

    override val stopText = "之后不再从 Health Connect 读取体重和体脂。已经同步进来的记录会保留，想删可以在身体页里删。"

    override fun unavailableReason(): String? = when (sdkStatus()) {
        HealthConnectClient.SDK_AVAILABLE -> null
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "需要先更新 Health Connect"
        else -> "这台手机不支持 Health Connect"
    }

    override fun openSettings(): Boolean = runCatching {
        context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    @Composable
    override fun rememberEnable(onDone: () -> Unit): () -> Unit {
        val callback = rememberUpdatedState(onDone)
        val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
            callback.value()
        }
        return { launcher.launch(permissions) }
    }

    private val mutex = Mutex()

    val permissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
    )

    fun sdkStatus(): Int =
        runCatching { HealthConnectClient.getSdkStatus(context) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    override fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun grantedPermissions(): Set<String> =
        if (isAvailable()) runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet()) else emptySet()

    override suspend fun canReadBody(): Boolean =
        grantedPermissions().contains(HealthPermission.getReadPermission(WeightRecord::class))

    override suspend fun lastSync(): Long? = kv.get(KEY_LAST)?.toLongOrNull()

    /** 同步体重和体脂，返回新增的记录数 */
    override suspend fun sync(): Int = mutex.withLock {
        if (!isAvailable()) throw IllegalStateException("这台手机没有可用的 Health Connect")
        val granted = client.permissionController.getGrantedPermissions()
        val canWeight = HealthPermission.getReadPermission(WeightRecord::class) in granted
        val canFat = HealthPermission.getReadPermission(BodyFatRecord::class) in granted
        if (!canWeight && !canFat) throw IllegalStateException("还没有授权读取体重和体脂")

        val now = System.currentTimeMillis()
        val since = (lastSync() ?: TimeUtil.daysAgo(90)) - 6 * 3600_000L
        val filter = TimeRangeFilter.after(Instant.ofEpochMilli(since))
        val weights = if (canWeight) client.readRecords(ReadRecordsRequest(WeightRecord::class, filter)).records else emptyList()
        val fats = if (canFat) client.readRecords(ReadRecordsRequest(BodyFatRecord::class, filter)).records else emptyList()

        var added = 0
        val usedFat = HashSet<String>()
        for (w in weights) {
            if (bodyDao.byExternalId(w.metadata.id) != null) continue
            val t = w.time.toEpochMilli()
            // 同一次称重的体脂时间基本一致，10 分钟内视为同一次
            val fat = fats.filter { abs(it.time.toEpochMilli() - t) < 10 * 60_000 }
                .minByOrNull { abs(it.time.toEpochMilli() - t) }
            fat?.let { usedFat += it.metadata.id }
            bodyDao.insert(
                BodyRecord(
                    measuredAt = t,
                    weightKg = w.weight.inKilograms,
                    bodyFatPct = fat?.percentage?.value,
                    source = "health_connect",
                    externalId = w.metadata.id,
                )
            )
            added++
        }
        for (f in fats) {
            if (f.metadata.id in usedFat) continue
            val ext = "fat:" + f.metadata.id
            if (bodyDao.byExternalId(ext) != null) continue
            bodyDao.insert(
                BodyRecord(
                    measuredAt = f.time.toEpochMilli(),
                    weightKg = null,
                    bodyFatPct = f.percentage.value,
                    source = "health_connect",
                    externalId = ext,
                )
            )
            added++
        }
        kv.put(KeyValue(KEY_LAST, now.toString()))
        added
    }

    /** 停止同步：撤销本 App 在 Health Connect 的全部授权，已经同步进来的记录保留 */
    override suspend fun revoke() {
        if (isAvailable()) client.permissionController.revokeAllPermissions()
    }

    private companion object {
        const val KEY_LAST = "hc_last_sync"
    }
}
