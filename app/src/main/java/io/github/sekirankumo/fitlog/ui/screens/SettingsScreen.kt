package io.github.sekirankumo.fitlog.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.background.HealthSync
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.RowItem
import io.github.sekirankumo.fitlog.ui.theme.Brand
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
    val c = Brand.colors
    val context = LocalContext.current
    val updater = container.updateManager
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val health = container.healthSync
    var healthStatus by remember { mutableStateOf("检查中…") }
    var healthGranted by remember { mutableStateOf(false) }
    var stepsInfo by remember { mutableStateOf<String?>(null) }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

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
        stepsInfo = when (val s = health.todaySteps()) {
            is HealthSync.Steps.Value -> "今日步数 ${s.count}，来源：${s.sources.joinToString("、").ifEmpty { "未知" }}"
            HealthSync.Steps.NotGranted -> "步数：没有授权读取步数，点上面的「授权读取」"
            HealthSync.Steps.NoData -> "步数：Health Connect 里今天没有步数数据。请在小米运动健康里确认已打开「步数」的同步，并在 Health Connect 的应用权限里允许它写入步数"
            HealthSync.Steps.Unavailable -> null
            is HealthSync.Steps.Failed -> "步数读取失败：${s.message}"
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

    val chevron: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Group("个人")
        BCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem("个人信息与目标", subtitle = "身高、目标、营养目标、训练提醒、AI 记住的信息", icon = Icons.Filled.Person, onClick = nav.openProfile, trailing = chevron)
            Divider()
            RowItem("AI 服务配置", subtitle = "API Key、模型选择、测试连接", icon = Icons.Filled.AutoAwesome, onClick = nav.openAiSettings, trailing = chevron)
        }

        Group("小米运动健康同步")
        BCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem(
                if (healthGranted) "立即同步" else "授权读取",
                subtitle = healthStatus,
                icon = Icons.Filled.Favorite,
                onClick = {
                    if (!health.isAvailable()) return@RowItem
                    if (!healthGranted) {
                        permissionLauncher.launch(health.permissions)
                    } else {
                        scope.launch {
                            healthStatus = runCatching { health.sync() }.fold({ "同步完成，新增 $it 条记录" }, { "同步失败：${it.message}" })
                            refresh++
                        }
                    }
                },
                trailing = chevron,
            )
            stepsInfo?.let {
                Divider()
                Text(it, style = MaterialTheme.typography.bodySmall, color = c.muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            if (health.isAvailable()) {
                Divider()
                TextButton(
                    onClick = { runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) } },
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) { Text("打开 Health Connect 设置") }
            }
        }
        Text(
            "在小米运动健康里打开 Health Connect 数据共享（一般在 我的 → 设置 → 第三方数据共享），体重秤和手环的数据就会同步过来。打开 App 时会自动同步。",
            style = MaterialTheme.typography.bodySmall,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Group("数据")
        BCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem("调整记录", subtitle = "AI 和自动调整做过的修改，可以撤销", icon = Icons.Filled.History, onClick = nav.openOperations, trailing = chevron)
            Divider()
            RowItem(
                "导出备份",
                subtitle = "全部记录导出为一个文件（不含 API Key）",
                icon = Icons.Filled.Upload,
                onClick = { exportLauncher.launch("薄肌来-${LocalDate.now()}.json") },
                trailing = chevron,
            )
            Divider()
            RowItem(
                "从备份恢复",
                subtitle = "用备份文件替换现在的全部数据",
                icon = Icons.Filled.Download,
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                trailing = chevron,
            )
        }
        Text(
            dataMessage ?: "每天会自动备份到手机的「下载/薄肌来」，卸载 App 也不会丢。",
            style = MaterialTheme.typography.bodySmall,
            color = if (dataMessage != null) c.brand else c.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Group("关于")
        BCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem(
                "薄肌来",
                subtitle = "版本 ${updater.currentVersionName}（${updater.currentVersionCode}）",
                icon = Icons.Filled.Info,
            )
            Divider()
            RowItem(
                "检查更新",
                subtitle = when (val s = state) {
                    UpdateState.Checking -> "正在检查…"
                    UpdateState.UpToDate -> "已是最新版本"
                    is UpdateState.Available -> "发现新版本 ${s.info.versionName}"
                    is UpdateState.Downloading -> "正在下载 ${(s.progress * 100).toInt()}%"
                    UpdateState.Installing -> "正在安装…"
                    is UpdateState.Error -> s.message
                    UpdateState.Idle -> "从 GitHub 获取最新版本"
                },
                icon = Icons.Filled.SystemUpdate,
                onClick = {
                    if (state !is UpdateState.Checking && state !is UpdateState.Downloading) scope.launch { updater.check() }
                },
                trailing = chevron,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            containerColor = c.surface,
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
private fun Group(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Brand.colors.muted,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 64.dp, end = 16.dp), color = Brand.colors.line)

