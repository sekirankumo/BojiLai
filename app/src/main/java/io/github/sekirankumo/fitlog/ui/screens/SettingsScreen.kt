package io.github.sekirankumo.fitlog.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.background.WorkScheduler
import io.github.sekirankumo.fitlog.ui.components.BButton
import io.github.sekirankumo.fitlog.ui.components.BCard
import io.github.sekirankumo.fitlog.ui.components.BSheet
import io.github.sekirankumo.fitlog.ui.components.ButtonKind
import io.github.sekirankumo.fitlog.ui.components.ConfirmSheet
import io.github.sekirankumo.fitlog.ui.components.GroupLabel
import io.github.sekirankumo.fitlog.ui.components.ListCard
import io.github.sekirankumo.fitlog.ui.components.ListDivider
import io.github.sekirankumo.fitlog.ui.components.ListRow
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.LogoMark
import io.github.sekirankumo.fitlog.ui.components.ToggleSwitch
import io.github.sekirankumo.fitlog.ui.components.TrackTopBar
import io.github.sekirankumo.fitlog.ui.components.TypingDots
import io.github.sekirankumo.fitlog.ui.components.screenPadding
import io.github.sekirankumo.fitlog.ui.theme.Brand
import io.github.sekirankumo.fitlog.update.UpdateState
import io.github.sekirankumo.fitlog.util.TimeUtil
import io.github.sekirankumo.fitlog.voice.VoiceModelState
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsNav(
    val openAiSettings: () -> Unit,
    val openProfile: () -> Unit,
    val openOperations: () -> Unit,
    val openFavorites: () -> Unit,
)

/** 设置：分组列表，组名写在卡片外面；健康数据同步单独一张卡片，停止同步也在里面 */
@Composable
fun SettingsScreen(container: AppContainer, nav: SettingsNav) {
    val c = Brand.colors
    val context = LocalContext.current
    val toast = LocalToast.current
    val updater = container.updateManager
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    val health = container.healthSync
    var healthStatus by remember { mutableStateOf("检查中…") }
    var healthGranted by remember { mutableStateOf(false) }
    var healthAvailable by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var confirmStopSync by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showLicenses by remember { mutableStateOf(false) }
    val speech = container.speech
    val voiceState by speech.state.collectAsState()
    var confirmDeleteVoice by remember { mutableStateOf(false) }
    val profileFlow = remember { container.profile.profile }
    val reminder = profileFlow.collectAsState(initial = null).value?.reminderMinuteOfDay
    val scroll = rememberScrollState()
    TrackTopBar(scroll)

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
        healthAvailable = health.isAvailable()
        healthStatus = when (health.sdkStatus()) {
            HealthConnectClient.SDK_AVAILABLE -> {
                healthGranted = health.canReadBody()
                if (healthGranted) {
                    "已开启 · 上次同步：${health.lastSync()?.let { TimeUtil.format(it) } ?: "还没有"}"
                } else {
                    "未开启"
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
                val msg = runCatching { container.backup.exportTo(uri) }.fold({ "已导出" }, { "导出失败：${it.message}" })
                toast.show(msg)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri
    }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(screenPadding())) {
        GroupLabel("个人", first = true)
        ListCard {
            ListRow("个人信息与目标", subtitle = "身高、目标、营养目标、AI 记住的信息", icon = Icons.Outlined.PersonOutline, onClick = nav.openProfile)
            ListDivider(52.dp)
            ListRow("常吃食物", subtitle = "修改或删除收藏的常吃食物", icon = Icons.Outlined.StarOutline, onClick = nav.openFavorites)
            ListDivider(52.dp)
            ListRow(
                "训练提醒",
                subtitle = reminder?.let { "每天 %02d:%02d 提醒，点击修改时间".format(it / 60, it % 60) } ?: "关闭",
                icon = Icons.Outlined.NotificationsNone,
                onClick = { if (reminder != null) pickReminderTime() else enableReminder() },
                trailing = {
                    ToggleSwitch(reminder != null, { on -> if (on) enableReminder() else setReminder(null) }, description = "训练提醒")
                },
            )
        }

        GroupLabel("AI 与语音")
        ListCard {
            ListRow("AI 服务配置", subtitle = "API Key、模型选择、测试连接", icon = Icons.Outlined.AutoAwesome, onClick = nav.openAiSettings)
            ListDivider(52.dp)
            ListRow(
                "离线语音识别",
                subtitle = when (val s = voiceState) {
                    VoiceModelState.Ready -> "语音模型已下载（${speech.modelSizeMb}MB），点击可删除"
                    VoiceModelState.Missing -> "第一次用语音时下载模型（约 ${speech.modelSizeMb}MB），也可以点这里提前下载"
                    is VoiceModelState.Downloading -> "正在下载 ${(s.progress * 100).toInt()}%，点击取消"
                    is VoiceModelState.Failed -> "${s.message}。点击重试"
                    VoiceModelState.Unsupported -> "这台手机不支持，语音输入会使用系统语音识别"
                },
                icon = Icons.Outlined.GraphicEq,
                chevron = false,
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

        GroupLabel("健康数据同步")
        BCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (healthGranted) c.brand else c.faint))
                Text("  $healthStatus", style = Brand.type.bodyMedium, color = c.text)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "从安卓系统的 Health Connect（健康连接）读取体重和体脂。小米运动健康、三星健康、体脂秤 App 等，只要在它们的设置里打开 Health Connect 数据共享，数据就能同步过来。开启后每次打开薄肌来会自动同步。",
                style = Brand.type.label,
                color = c.muted,
            )
            if (healthAvailable) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (healthGranted) {
                        BButton("立即同步", Modifier.weight(1f), loading = syncing) {
                            syncing = true
                            scope.launch {
                                val msg = runCatching { health.sync() }.fold({ "同步完成，新增 $it 条记录" }, { "同步失败：${it.message}" })
                                syncing = false
                                toast.show(msg)
                                refresh++
                            }
                        }
                        BButton("打开 Health Connect 设置", Modifier.weight(1.3f), kind = ButtonKind.Soft) {
                            runCatching { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                        }
                    } else {
                        BButton("开启同步", Modifier.weight(1f)) { permissionLauncher.launch(health.permissions) }
                    }
                }
                // 已经在同步时可以停止
                if (healthGranted) {
                    Spacer(Modifier.height(4.dp))
                    BButton("停止同步", kind = ButtonKind.Danger, height = 36.dp) { confirmStopSync = true }
                }
            }
        }

        GroupLabel("数据")
        ListCard {
            ListRow("调整记录", subtitle = "AI 和自动调整做过的修改，可以撤销", icon = Icons.Outlined.History, onClick = nav.openOperations)
            ListDivider(52.dp)
            ListRow(
                "导出备份",
                subtitle = "全部记录导出为一个文件（不含 API Key）",
                icon = Icons.Outlined.FileUpload,
                chevron = false,
                onClick = { exportLauncher.launch("薄肌来-${LocalDate.now()}.json") },
            )
            ListDivider(52.dp)
            ListRow(
                "从备份恢复",
                subtitle = "用备份文件替换现在的全部数据",
                icon = Icons.Outlined.FileDownload,
                chevron = false,
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
            )
        }
        Text(
            "每天会自动备份到手机的「下载/薄肌来」，卸载 App 也不会丢。",
            style = Brand.type.note,
            color = c.muted,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )

        GroupLabel("关于")
        ListCard {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                LogoMark(40.dp)
                Column(Modifier.padding(start = 14.dp)) {
                    Text("薄肌来", style = Brand.type.bodyMedium, color = c.text)
                    Text("版本 ${updater.currentVersionName}（${updater.currentVersionCode}）", style = Brand.type.note, color = c.muted)
                }
            }
            ListDivider(52.dp)
            ListRow("开源许可", subtitle = "动作示意图、人体肌肉图、语音识别、字体、毛玻璃效果", icon = Icons.Outlined.Description, onClick = { showLicenses = true })
            ListDivider(52.dp)
            ListRow(
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
                icon = Icons.Outlined.SystemUpdate,
                onClick = {
                    if (state !is UpdateState.Checking && state !is UpdateState.Downloading) scope.launch { updater.check() }
                },
                trailing = if (state is UpdateState.Checking) ({ TypingDots(c.muted) }) else null,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showLicenses) {
        val mit = remember {
            runCatching { context.assets.open("musclemap/LICENSE.txt").bufferedReader().use { it.readText() } }.getOrDefault("")
        }
        BSheet(
            onDismiss = { showLicenses = false },
            title = "开源许可",
            actions = { s -> BButton("好", Modifier.weight(1f), kind = ButtonKind.Soft) { s.close() } },
        ) {
            License("动作示意图和动作数据", "free-exercise-db（github.com/yuhonas/free-exercise-db），公共领域（The Unlicense）。中文说明为薄肌来自行编写。")
            License("离线语音识别", "sherpa-onnx（github.com/k2-fsa/sherpa-onnx）和 Zipformer 中文语音模型，Apache License 2.0。")
            License("数字字体", "Barlow Condensed（github.com/jpt/barlow），SIL Open Font License 1.1。")
            License("毛玻璃效果", "Haze（github.com/chrisbanes/haze），Apache License 2.0。")
            License("人体肌肉图", "MuscleMap（github.com/melihcolpan/MuscleMap），MIT License：")
            Text(mit, style = Brand.type.note, color = c.muted)
        }
    }

    if (confirmStopSync) {
        ConfirmSheet(
            title = "停止同步？",
            text = "之后不再从 Health Connect 读取体重和体脂。已经同步进来的记录会保留，想删可以在身体页里删。",
            confirm = "停止同步",
            danger = true,
            onDismiss = { confirmStopSync = false },
        ) {
            scope.launch {
                runCatching { health.revoke() }
                refresh++
            }
        }
    }

    if (confirmDeleteVoice) {
        ConfirmSheet(
            title = "删除语音模型？",
            text = "删除后可以腾出约 ${speech.modelSizeMb}MB 空间，下次用语音输入时会重新下载。",
            confirm = "删除",
            danger = true,
            onDismiss = { confirmDeleteVoice = false },
        ) { scope.launch { speech.deleteModel() } }
    }

    confirmImport?.let { uri ->
        ConfirmSheet(
            title = "从备份恢复？",
            text = "现在的全部数据会被备份文件里的内容替换。建议先导出一份当前数据。",
            confirm = "恢复",
            onDismiss = { confirmImport = null },
        ) {
            container.appScope.launch {
                val msg = runCatching { container.backup.importFrom(uri) }.fold(
                    { "已恢复：${it.bodyRecords.size} 条身体记录、${it.foodEntries.size} 条饮食、${it.planDays.size} 个训练日" },
                    { "恢复失败：${it.message}" },
                )
                toast.show(msg)
            }
        }
    }
}

@Composable
private fun License(title: String, text: String) {
    Text(title, style = Brand.type.labelBold, color = Brand.colors.text)
    Spacer(Modifier.height(2.dp))
    Text(text, style = Brand.type.label, color = Brand.colors.muted)
    Spacer(Modifier.height(12.dp))
}
