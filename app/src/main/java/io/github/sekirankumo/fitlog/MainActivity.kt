package io.github.sekirankumo.fitlog

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.sekirankumo.fitlog.ui.theme.isDark
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.background.Notifications
import io.github.sekirankumo.fitlog.ui.BojiLaiRoot
import io.github.sekirankumo.fitlog.ui.theme.BojiLaiTheme

/** 首次打开不申请任何权限：通知权限在设置里开启训练提醒时才申请 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BojiLaiApp).container
        val openRoute = intent?.getStringExtra(Notifications.EXTRA_OPEN)
        setContent {
            val dark = rememberAppDark(container)
            BojiLaiTheme(darkTheme = dark) {
                BojiLaiRoot(container, openRoute)
            }
        }
    }
}

/**
 * 设置里选的外观（跟随系统 / 浅色 / 深色），换了之后状态栏、导航条的图标颜色也跟着换。
 */
@Composable
private fun ComponentActivity.rememberAppDark(container: AppContainer): Boolean {
    val mode by container.appPrefs.themeMode.collectAsState()
    val dark = mode.isDark()
    DisposableEffect(dark) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
        onDispose {}
    }
    return dark
}

// 和 enableEdgeToEdge 默认的导航条底色一样（只在三键导航时出现）
private val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

/** Health Connect 授权页里点「隐私政策」时显示 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BojiLaiApp).container
        setContent {
            BojiLaiTheme(darkTheme = rememberAppDark(container)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                        Text("薄肌来如何使用健康数据", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "薄肌来只读取体重和体脂率，用来显示趋势、计算营养目标和调整训练计划。" +
                                "数据只保存在这台手机上，不会上传到任何服务器。" +
                                "只有当你主动使用 AI 助手时，相关数据才会作为对话内容发送给你自己配置的 AI 服务。",
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}
