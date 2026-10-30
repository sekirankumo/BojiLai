package io.github.sekirankumo.fitlog.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.github.sekirankumo.fitlog.resources.Res
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorDomain
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy

actual val isIos: Boolean = true

actual val supportsBlur: Boolean = true

actual val appVersionCode: Int =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 1

actual val appVersionName: String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.1.1"

fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

actual fun resourceUri(path: String): String = Res.getUri("files/$path")

actual fun readResourceFile(path: String): ByteArray? = runCatching {
    val uri = resourceUri(path)
    val data = if (uri.startsWith("file:")) {
        NSURL.URLWithString(uri)?.let { NSData.dataWithContentsOfURL(it) }
    } else {
        NSData.dataWithContentsOfFile(uri)
    }
    data?.toByteArray()
}.getOrNull()

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

actual fun classifyNetworkError(e: Throwable): NetworkError? = when (e) {
    is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> NetworkError.Timeout
    is DarwinHttpRequestException -> {
        val err = e.origin
        when {
            err.domain != NSURLErrorDomain -> NetworkError.Other
            err.code == NSURLErrorTimedOut -> NetworkError.Timeout
            err.code in listOf(NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed, NSURLErrorNotConnectedToInternet, NSURLErrorCannotConnectToHost) ->
                NetworkError.UnknownHost
            err.code in -1206L..-1200L -> NetworkError.Ssl
            else -> NetworkError.Other
        }
    }
    is kotlinx.io.IOException -> NetworkError.Other
    else -> null
}
