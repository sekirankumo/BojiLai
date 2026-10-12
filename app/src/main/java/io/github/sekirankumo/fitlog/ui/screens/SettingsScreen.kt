package io.github.sekirankumo.fitlog.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, onOpenAiSettings: () -> Unit) {
    val updater = container.updateManager
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("AI 服务")
        ListItem(
            modifier = Modifier.clickable(onClick = onOpenAiSettings),
            headlineContent = { Text("AI 服务配置") },
            supportingContent = { Text("添加 API Key、选择模型、测试连接") },
        )
        HorizontalDivider()

        SectionTitle("关于")
        ListItem(
            headlineContent = { Text("当前版本") },
            supportingContent = { Text("${updater.currentVersionName}（${updater.currentVersionCode}）") },
        )
        ListItem(
            headlineContent = { Text("检查更新") },
            supportingContent = {
                Text(
                    when (val s = state) {
                        UpdateState.Checking -> "正在检查…"
                        UpdateState.UpToDate -> "已是最新版本"
                        is UpdateState.Available -> "发现新版本 ${s.info.versionName}"
                        is UpdateState.Downloading -> "正在下载 ${(s.progress * 100).toInt()}%"
                        UpdateState.Installing -> "正在安装…"
                        is UpdateState.Error -> s.message
                        UpdateState.Idle -> "从 GitHub 获取最新版本"
                    }
                )
            },
            trailingContent = {
                TextButton(
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                    onClick = { scope.launch { updater.check() } },
                ) { Text("检查") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
