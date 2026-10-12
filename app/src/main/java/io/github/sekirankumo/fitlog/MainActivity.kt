package io.github.sekirankumo.fitlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.sekirankumo.fitlog.ui.FitLogRoot
import io.github.sekirankumo.fitlog.ui.theme.FitLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FitLogApp).container
        setContent {
            FitLogTheme {
                FitLogRoot(container)
            }
        }
    }
}
