package io.github.sekirankumo.fitlog.ios

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.platform.iosPlatformDeps
import io.github.sekirankumo.fitlog.ui.BojiLaiRoot
import io.github.sekirankumo.fitlog.ui.theme.BojiLaiTheme
import io.github.sekirankumo.fitlog.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** Swift 调用的入口：启动、回到前台、点通知 */
object IosApp {
    lateinit var container: AppContainer
        private set

    /** 点通知要打开的页面，等界面准备好再跳 */
    private val pendingRoute = MutableStateFlow<String?>(null)

    fun start(bridge: NativeBridge) {
        if (::container.isInitialized) return
        IosPlatform.bridge = bridge
        lateinit var c: AppContainer
        c = AppContainer(iosPlatformDeps { c })
        container = c
        bridge.setInterfaceStyle(c.appPrefs.themeMode.value.ordinal)
        c.start()
    }

    /**
     * 每次回到 App：今天第一次打开时 AI 重排接下来的营养目标；
     * iOS 不保证后台定时任务会跑，所以漏打卡检查、自动备份、提醒也在这时做
     */
    fun onForeground() {
        if (!::container.isInitialized) return
        val c = container
        c.appScope.launch {
            runCatching { c.nutritionPlanner.dailyCheck() }
            runCatching { c.backup.autoBackup() }
            runCatching { c.reminders.schedule(c.profile.current().reminderMinuteOfDay) }
        }
    }

    fun openRoute(route: String) {
        pendingRoute.value = route
    }

    fun createViewController(): UIViewController = ComposeUIViewController {
        val c = container
        val mode by c.appPrefs.themeMode.collectAsState()
        BojiLaiTheme(darkTheme = mode.isDark()) {
            BojiLaiRoot(c)
        }
        // 点通知打开的页面：等界面开始接收跳转再发
        LaunchedEffect(Unit) {
            pendingRoute.filterNotNull().collect { route ->
                c.navEvents.subscriptionCount.first { it > 0 }
                c.navEvents.emit(route)
                pendingRoute.value = null
            }
        }
    }
}
