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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
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
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.RowItem
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.update.UpdateState
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.background.WorkScheduler
import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Switch
import androidx.core.content.ContextCompat
import io.github.sekirankumo.fitlog.voice.VoiceModelState
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsNav(
    val openAiSettings: () -> Unit,
    val openProfile: () -> Unit,
    val openOperations: () -> Unit,
    val openFavorites: () -> Unit,
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
    var confirmStopSync by remember { mutableStateOf(false) }
    var dataMessage by remember { mutableStateOf<String?>(null) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showLicenses by remember { mutableStateOf(false) }
    val speech = container.speech
    val voiceState by speech.state.collectAsState()
    var confirmDeleteVoice by remember { mutableStateOf(false) }
    val profileFlow = remember { container.profile.profile }
    val reminder = profileFlow.collectAsState(initial = null).value?.reminderMinuteOfDay

    // 训练提醒：打开时才申请通知权限，再选提醒时间
    fun setReminder(minute: Int?) {
        container.appScope.launch {
            container.profile.update { it.copy(reminderMinuteOfDay = minute) }
            WorkScheduler.scheduleReminder(context.applicationContext, minute)
        }
    }
    fun pickReminderTime() {
        val m = reminder ?: (19 * 60)
        TimePickerDialog(context, { _, h, min -> setReminder(h * 60 + min) }, m / 60, m % 60, true).show()
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pickReminderTime()
        } else {
            Toast.makeText(context, "需要通知权限才能提醒，可以在 系统设置 → 应用 → 薄肌来 → 通知 里打开", Toast.LENGTH_LONG).show()
        }
    }
    fun enableReminder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            pickReminderTime()
        }
    }

    LaunchedEffect(refresh) {
        healthStatus = when (health.sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthGranted = health.canReadBody()
                if (healthGranted) {
                    "已开启 · 上次同步：${health.lastSync()?.let { TimeUtil.format(it) } ?: "还没有"}"
                } else {
                    "未开启，点击授权读取体重和体脂"
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "需要先更新 Health Connect"
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

    val chevron: @Composable () -> Unit = {
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Group("个人")
        BCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem("个人信息与目标", subtitle = "身高、目标、营养目标、AI 记住的信息", icon = Icons.Filled.Person, onClick = nav.openProfile, trailing = chevron)
            Divider()
            RowItem("常吃食物", subtitle = "修改或删除收藏的常吃食物", icon = Icons.Filled.Star, onClick = nav.openFavorites, trailing = chevron)
            Divider()
            RowItem(
                "训练提醒",
                subtitle = reminder?.let { "每天 %02d:%02d 提醒，点击修改时间".format(it / 60, it % 60) } ?: "关闭",
                icon = Icons.Filled.Notifications,
                onClick = { if (reminder != null) pickReminderTime() else enableReminder() },
                trailing = {
                    Switch(
                        checked = reminder != null,
                        onCheckedChange = { on -> if (on) enableReminder() else setReminder(null) },
                    )
                },
            )
        }

        Group("AI 与语音")
        BCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem("AI 服务配置", subtitle = "API Key、模型选择、测试连接", icon = Icons.Filled.AutoAwesome, onClick = nav.openAiSettings, trailing = chevron)
            Divider()
            RowItem(
                "离线语音识别",
                subtitle = when (val s = voiceState) {
                    VoiceModelState.Ready -> "语音模型已下载（${speech.modelSizeMb}MB），点击可删除"
                    VoiceModelState.Missing -> "第一次用语音时下载模型（约 ${speech.modelSizeMb}MB），也可以点这里提前下载"
                    is VoiceModelState.Downloading -> "正在下载 ${(s.progress * 100).toInt()}%，点击取消"
                    is VoiceModelState.Failed -> "${s.message}。点击重试"
                    VoiceModelState.Unsupported -> "这台手机不支持，语音输入会使用系统语音识别"
                },
                icon = Icons.Filled.Mic,
                onClick = {
                    when (voiceState) {
                        VoiceModelState.Ready -> confirmDeleteVoice = true
                        is VoiceModelState.Downloading -> speech.cancelDownload()
                        VoiceModelState.Missing, is VoiceModelState.Failed -> speech.startDownload()
                        VoiceModelState.Unsupported -> Unit
                    }
                },
            )
        }

        Group("健康数据同步")
        BCard(padding = PaddingValues(vertical = 4.dp)) {
            RowItem(
                if (healthGranted) "立即同步" else "开启同步",
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
            if (health.isAvailable()) {
                Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) } }) {
                        Text("打开 Health Connect 设置")
                    }
                    Spacer(Modifier.weight(1f))
                    // 已经在同步时可以停止
                    if (healthGranted) {
                        TextButton(onClick = { confirmStopSync = true }) { Text("停止同步", color = c.danger) }
                    }
                }
            }
        }
        Text(
            "从安卓系统的 Health Connect（健康连接）读取体重和体脂。小米运动健康、三星健康、体脂秤 App 等，只要在它们的设置里打开 Health Connect 数据共享，数据就能同步过来。开启后每次打开薄肌来会自动同步。",
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
                "开源许可",
                subtitle = "动作示意图、人体肌肉图、语音识别",
                icon = Icons.Filled.Description,
                onClick = { showLicenses = true },
                trailing = chevron,
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

    if (showLicenses) {
        val mit = remember {
            runCatching { context.assets.open("musclemap/LICENSE.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            containerColor = c.surface,
            title = { Text("开源许可") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("动作示意图和动作数据", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "free-exercise-db（github.com/yuhonas/free-exercise-db），公共领域（The Unlicense）。中文说明为薄肌来自行编写。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("离线语音识别", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "sherpa-onnx（github.com/k2-fsa/sherpa-onnx）和 Zipformer 中文语音模型，Apache License 2.0。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("人体肌肉图", style = MaterialTheme.typography.titleSmall)
                    Text("MuscleMap（github.com/melihcolpan/MuscleMap），MIT License：", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(mit, style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("好") } },
        )
    }

    if (confirmStopSync) {
        AlertDialog(
            onDismissRequest = { confirmStopSync = false },
            containerColor = c.surface,
            title = { Text("停止同步？") },
            text = { Text("之后不再从 Health Connect 读取体重和体脂。已经同步进来的记录会保留，想删可以在身体页里删。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStopSync = false
                    scope.launch {
                        runCatching { health.revoke() }
                        refresh++
                    }
                }) { Text("停止同步", color = c.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmStopSync = false }) { Text("取消") } },
        )
    }

    if (confirmDeleteVoice) {
        AlertDialog(
            onDismissRequest = { confirmDeleteVoice = false },
            containerColor = c.surface,
            title = { Text("删除语音模型？") },
            text = { Text("删除后可以腾出约 ${speech.modelSizeMb}MB 空间，下次用语音输入时会重新下载。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteVoice = false
                    scope.launch { speech.deleteModel() }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteVoice = false }) { Text("取消") } },
        )
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

