package io.github.sekirankumo.fitlog.update

import io.github.sekirankumo.fitlog.platform.appVersionCode
import io.github.sekirankumo.fitlog.platform.appVersionName
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 更新信息：GitHub API 的最新发布（github.com 的直接下载地址在部分网络下会超时） */
const val UPDATE_URL = "https://api.github.com/repos/sekirankumo/BojiLai/releases/latest"

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    /** GitHub API 的资源地址，需带 Accept: application/octet-stream 下载 */
    val apkUrl: String,
    val notes: String = "",
    /** 安装包在网页上的下载地址（iOS 用浏览器下载） */
    val browserUrl: String = "",
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
    @SerialName("browser_download_url") val browserUrl: String = "",
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
 * 安装更新的方式：安卓下载 APK 后自己安装；iOS 不允许 App 自己安装，打开下载页面。
 */
interface UpdateInstaller {
    /** 发布里的安装包后缀：.apk / .ipa */
    val assetSuffix: String

    /** 能直接安装（安卓需要「安装未知应用」权限） */
    fun canInstall(): Boolean

    /** 打开系统设置让用户允许安装 */
    fun requestInstallPermission()

    /**
     * 下载并安装，onProgress 回调 0–1 的下载进度；
     * 返回 true 表示已经交给系统安装（进入「正在安装」状态）。
     */
    suspend fun install(info: UpdateInfo, onProgress: (Float) -> Unit): Boolean
}

/**
 * 应用内更新：读取 GitHub Releases 的最新发布，
 * 有新版本时下载安装（安卓）或打开下载页面（iOS）。
 */
class UpdateManager(
    private val http: HttpClient,
    private val installer: UpdateInstaller,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersionCode: Int = appVersionCode
    val currentVersionName: String = appVersionName

    suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = try {
            val resp = http.get(UPDATE_URL) { header("Accept", "application/vnd.github+json") }
            val info = when {
                resp.status.value == 404 -> null // 还没有任何发布
                resp.status.value !in 200..299 -> error("HTTP ${resp.status.value}")
                else -> json.decodeFromString<GithubRelease>(resp.bodyAsText()).toUpdateInfo()
            }
            if (info != null && info.versionCode > currentVersionCode) {
                UpdateState.Available(info)
            } else {
                UpdateState.UpToDate
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateState.Error("检查更新失败：${e.message}")
        }
    }

    /** 标签格式为 v0.1.<versionCode>，版本号取最后一段 */
    private fun GithubRelease.toUpdateInfo(): UpdateInfo? {
        val code = tagName.substringAfterLast('.').toIntOrNull() ?: return null
        val pkg = assets.firstOrNull { it.name.endsWith(installer.assetSuffix) } ?: return null
        return UpdateInfo(
            versionCode = code,
            versionName = tagName.removePrefix("v"),
            apkUrl = pkg.url,
            notes = body.orEmpty().substringBefore("\n\nCo-Authored-By").trim(),
            browserUrl = pkg.browserUrl,
        )
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    /** 是否已允许本应用安装 APK（「安装未知应用」权限） */
    fun canInstall(): Boolean = installer.canInstall()

    fun requestInstallPermission() = installer.requestInstallPermission()

    /** 安装更新前执行（自动备份数据） */
    var beforeInstall: (suspend () -> Unit)? = null

    suspend fun downloadAndInstall(info: UpdateInfo) {
        try {
            runCatching { beforeInstall?.invoke() }
            _state.value = UpdateState.Downloading(info, 0f)
            val handedOff = installer.install(info) { p -> _state.value = UpdateState.Downloading(info, p) }
            _state.value = if (handedOff) UpdateState.Installing else UpdateState.Idle
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Error("更新失败：${e.message}")
        }
    }
}
