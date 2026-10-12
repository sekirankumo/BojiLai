package io.github.sekirankumo.fitlog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.update.UpdateManager
import io.github.sekirankumo.fitlog.update.UpdateState
import kotlinx.coroutines.launch

/** 有新版本 / 正在下载 / 正在安装时弹出的对话框 */
@Composable
fun UpdateDialog(manager: UpdateManager) {
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var needPermission by remember { mutableStateOf(false) }

    when (val s = state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = { manager.dismiss() },
            title = { Text("发现新版本 ${s.info.versionName}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("当前版本：${manager.currentVersionName}")
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.info.notes)
                    }
                    if (needPermission) {
                        Spacer(Modifier.height(8.dp))
                        Text("请在刚打开的设置里允许「安装未知应用」，返回后再点更新。")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (manager.canInstall()) {
                        scope.launch { manager.downloadAndInstall(s.info) }
                    } else {
                        needPermission = true
                        context.startActivity(manager.installPermissionIntent())
                    }
                }) { Text("更新") }
            },
            dismissButton = {
                TextButton(onClick = { manager.dismiss() }) { Text("以后再说") }
            },
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("正在下载 ${s.info.versionName}") },
            text = {
                LinearProgressIndicator(
                    progress = { s.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {},
        )

        UpdateState.Installing -> AlertDialog(
            onDismissRequest = { manager.dismiss() },
            title = { Text("正在安装") },
            text = { Text("如果系统弹出安装确认，请点「安装」。安装完成后应用会自动重启。") },
            confirmButton = { TextButton(onClick = { manager.dismiss() }) { Text("好") } },
        )

        else -> Unit
    }
}
