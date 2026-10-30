package io.github.sekirankumo.fitlog.platform

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.sekirankumo.fitlog.BuildConfig
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** 安卓的 Application Context，App 启动时设置 */
@SuppressLint("StaticFieldLeak")
object AndroidPlatform {
    lateinit var context: Context
}

actual val isIos: Boolean = false

actual val supportsBlur: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

actual val appVersionCode: Int = BuildConfig.VERSION_CODE
actual val appVersionName: String = BuildConfig.VERSION_NAME

private const val ASSET_PREFIX = "file:///android_asset/"

actual fun readResourceFile(path: String): ByteArray? = runCatching {
    val uri = resourceUri(path)
    AndroidPlatform.context.assets.open(uri.removePrefix(ASSET_PREFIX)).use { it.readBytes() }
}.getOrNull()

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

actual fun classifyNetworkError(e: Throwable): NetworkError? = when (e) {
    is UnknownHostException -> NetworkError.UnknownHost
    is SocketTimeoutException, is HttpRequestTimeoutException, is ConnectTimeoutException -> NetworkError.Timeout
    is SSLException -> NetworkError.Ssl
    is IOException -> NetworkError.Other
    else -> null
}
