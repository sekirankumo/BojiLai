package io.github.sekirankumo.fitlog.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.github.sekirankumo.fitlog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    /** GitHub API 的资源地址，需带 Accept: application/octet-stream 下载 */
    val apkUrl: String,
    val notes: String = "",
)

/** GitHub API /releases/latest 的返回内容（只取用到的字段） */
@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
private data class GithubAsset(
    val name: String,
    val url: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * 应用内更新：读取 GitHub Releases 上的 update.json，
 * 有新版本时下载 APK 并通过 PackageInstaller 安装。
 */
class UpdateManager(
    private val context: Context,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionCode: Int = BuildConfig.VERSION_CODE
    val currentVersionName: String = BuildConfig.VERSION_NAME

    suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = try {
            val info = withContext(Dispatchers.IO) {
                val req = Request.Builder()
                    .url(BuildConfig.UPDATE_URL)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 404) return@withContext null // 还没有任何发布
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    json.decodeFromString<GithubRelease>(resp.body!!.string()).toUpdateInfo()
                }
            }
            if (info != null && info.versionCode > currentVersionCode) {
                UpdateState.Available(info)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: Exception) {
            UpdateState.Error("检查更新失败：${e.message}")
        }
    }

    /** 标签格式为 v0.1.<versionCode>，版本号取最后一段 */
    private fun GithubRelease.toUpdateInfo(): UpdateInfo? {
        val code = tagName.substringAfterLast('.').toIntOrNull() ?: return null
        val apk = assets.firstOrNull { it.name.endsWith(".apk") } ?: return null
        return UpdateInfo(
            versionCode = code,
            versionName = tagName.removePrefix("v"),
            apkUrl = apk.url,
            notes = body.orEmpty().substringBefore("\n\nCo-Authored-By").trim(),
        )
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    /** 是否已允许本应用安装 APK（「安装未知应用」权限） */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun downloadAndInstall(info: UpdateInfo) {
        try {
            val apk = download(info)
            _state.value = UpdateState.Installing
            withContext(Dispatchers.IO) { install(apk) }
        } catch (e: Exception) {
            _state.value = UpdateState.Error("更新失败：${e.message}")
        }
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "update.apk")
        _state.value = UpdateState.Downloading(info, 0f)
        val req = Request.Builder()
            .url(info.apkUrl)
            .header("Accept", "application/octet-stream")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("下载失败 HTTP ${resp.code}")
            val body = resp.body!!
            val total = body.contentLength()
            var done = 0L
            var lastReported = 0f
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val p = done.toFloat() / total
                            if (p - lastReported >= 0.01f) {
                                lastReported = p
                                _state.value = UpdateState.Downloading(info, p)
                            }
                        }
                    }
                }
            }
        }
        target
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}
