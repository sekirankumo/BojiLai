package io.github.sekirankumo.fitlog.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.update.UpdateState
import io.github.sekirankumo.fitlog.util.TimeUtil
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsNav(
    val openAiSettings: () -> Unit,
    val openProfile: () -> Unit,
    val openOperations: () -> Unit,
)

@Composable
fun SettingsScreen(container: AppContainer, nav: SettingsNav) {
    val context = LocalContext.current
    val updater = container.updateManager
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val health = container.healthSync
    var healthStatus by remember { mutableStateOf("检查中…") }
    var healthGranted by remember { mutableStateOf(false) }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(refresh) {
        healthStatus = when (health.sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthGranted = health.canReadBody()
                if (healthGranted) {
                    "已授权 · 上次同步：${health.lastSync()?.let { TimeUtil.format(it) } ?: "还没有"}"
                } else {
                    "未授权，点击授权读取体重、体脂和步数"
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "需要更新 Health Connect"
            else -> "这台手机不支持 Health Connect"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        refresh++
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            container.appScope.launch {
                dataMessage = runCatching { container.backup.exportTo(uri) }
                    .fold({ "已导出" }, { "导出失败：${it.message}" })
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        SectionTitle("个人")
        ListItem(
            modifier = Modifier.clickable(onClick = nav.openProfile),
            headlineContent = { Text("个人信息与目标") },
            supportingContent = { Text("身高、年龄、目标、营养目标、训练提醒、AI 记住的信息") },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = nav.openAiSettings),
            headlineContent = { Text("AI 服务配置") },
            supportingContent = { Text("添加 API Key、选择模型、测试连接") },
        )
        HorizontalDivider()

        SectionTitle("小米运动健康同步（Health Connect）")
        ListItem(
            modifier = Modifier.clickable {
                if (!health.isAvailable()) return@clickable
                if (!healthGranted) {
                    permissionLauncher.launch(health.permissions)
                } else {
                    scope.launch {
                        healthStatus = runCatching { health.sync() }.fold({ "同步完成，新增 $it 条记录" }, { "同步失败：${it.message}" })
                    }
                }
            },
            headlineContent = { Text(if (healthGranted) "立即同步" else "授权读取") },
            supportingContent = { Text(healthStatus) },
        )
        Text(
            "在「小米运动健康」里打开 Health Connect 数据同步（一般在 我的 → 设置 → 第三方数据共享 / Health Connect），体重秤和手环的数据就会自动同步到这里。打开 App 时会自动同步。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (health.isAvailable()) {
            TextButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                    }
                },
                modifier = Modifier.padding(horizontal = 8.dp),
            ) { Text("打开 Health Connect 设置") }
        }
        HorizontalDivider()

        SectionTitle("数据")
        ListItem(
            modifier = Modifier.clickable(onClick = nav.openOperations),
            headlineContent = { Text("操作记录") },
            supportingContent = { Text("AI 和自动调整做过的修改，可以撤销") },
        )
        ListItem(
            modifier = Modifier.clickable { exportLauncher.launch("薄肌来-${LocalDate.now()}.json") },
            headlineContent = { Text("导出备份") },
            supportingContent = { Text("全部记录导出为一个文件（不含 API Key）。每天也会自动备份到「下载/薄肌来」") },
        )
        ListItem(
            modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "*/*")) },
            headlineContent = { Text("从备份恢复") },
            supportingContent = { Text("会用备份文件替换现在的全部数据") },
        )
        dataMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
        }
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

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("从备份恢复？") },
            text = { Text("现在的全部数据会被备份文件里的内容替换。建议先导出一份当前数据。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    container.appScope.launch {
                        dataMessage = runCatching { container.backup.importFrom(uri) }.fold(
                            { "已恢复：${it.bodyRecords.size} 条身体记录、${it.foodEntries.size} 条饮食、${it.planDays.size} 个训练日" },
                            { "恢复失败：${it.message}" },
                        )
                    }
                }) { Text("恢复") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("取消") } },
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
