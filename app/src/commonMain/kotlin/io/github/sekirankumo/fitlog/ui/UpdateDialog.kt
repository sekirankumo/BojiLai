package io.github.sekirankumo.fitlog.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.sekirankumo.fitlog.platform.isIos
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BSheet
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.FigureNumber
import io.github.sekirankumo.fitlog.ui.components.ProgressLine
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.update.UpdateManager
import io.github.sekirankumo.fitlog.update.UpdateState
import kotlinx.coroutines.launch

/** 有新版本 / 正在下载 / 正在安装时弹出的底部弹层；三个阶段在同一个弹层里切换，不会闪一下 */
@Composable
fun UpdateDialog(manager: UpdateManager) {
    val c = Brand.colors
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var needPermission by remember { mutableStateOf(false) }
    val s = state
    if (s !is UpdateState.Available && s !is UpdateState.Downloading && s != UpdateState.Installing) return

    BSheet(
        onDismiss = { manager.dismiss() },
        title = when (s) {
            is UpdateState.Available -> "发现新版本 ${s.info.versionName}"
            is UpdateState.Downloading -> "正在下载 ${s.info.versionName}"
            else -> "正在安装"
        },
        subtitle = if (s is UpdateState.Available) "当前版本 ${manager.currentVersionName}" else null,
        dismissible = s !is UpdateState.Downloading,
        // 下载中没有按钮
        actions = { sheet ->
            when (s) {
                is UpdateState.Available -> {
                    BButton("以后再说", Modifier.weight(1f), kind = ButtonKind.Soft) { sheet.close() }
                    BButton(if (isIos) "去下载" else "更新", Modifier.weight(1f)) {
                        if (manager.canInstall()) {
                            scope.launch { manager.downloadAndInstall(s.info) }
                        } else {
                            needPermission = true
                            manager.requestInstallPermission()
                        }
                    }
                }
                UpdateState.Installing -> BButton("好", Modifier.weight(1f)) { sheet.close() }
                else -> Unit
            }
        },
    ) {
        when (s) {
            is UpdateState.Available -> {
                if (s.info.notes.isNotBlank()) {
                    Text(s.info.notes, style = Brand.type.body, color = c.text)
                }
                if (isIos) {
                    Spacer(Modifier.height(10.dp))
                    Text("会在浏览器里下载新的安装包，下载后用安装旧版本的方法（比如 Sideloadly）覆盖安装，数据不会丢。", style = Brand.type.label, color = c.muted)
                }
                if (needPermission) {
                    Spacer(Modifier.height(10.dp))
                    Text("请在刚打开的设置里允许「安装未知应用」，返回后再点更新。", style = Brand.type.label, color = c.warn)
                }
            }
            is UpdateState.Downloading -> {
                FigureNumber("${(s.progress * 100).toInt()}", "%", Brand.type.n1)
                Spacer(Modifier.height(10.dp))
                ProgressLine(s.progress, Modifier.padding(bottom = 4.dp), height = 6.dp)
            }
            else -> Text("如果系统弹出安装确认，请点「安装」。安装完成后应用会自动重启。", style = Brand.type.body, color = c.text)
        }
    }
}
