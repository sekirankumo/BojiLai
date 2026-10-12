package io.github.sekirankumo.fitlog

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.sekirankumo.fitlog.background.Notifications
import io.github.sekirankumo.fitlog.ui.FitLogRoot
import io.github.sekirankumo.fitlog.ui.theme.FitLogTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FitLogApp).container
        val openRoute = intent?.getStringExtra(Notifications.EXTRA_OPEN)
        setContent {
            FitLogTheme {
                FitLogRoot(container, openRoute)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Health Connect 授权页里点「隐私政策」时显示 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitLogTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp)) {
                        Text("FitLog 如何使用健康数据", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "FitLog 只读取体重、体脂率和步数，用来显示趋势、计算营养目标和调整训练计划。" +
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
