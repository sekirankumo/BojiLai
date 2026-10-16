package io.github.sekirankumo.fitlog.data

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 外观：跟随系统 / 浅色 / 深色 */
enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

/**
 * App 自己的小设置（目前只有外观）。用 SharedPreferences 同步读取，
 * 打开 App 的第一帧就是用户选的颜色，不会先闪一下另一套。
 */
class AppPrefs(context: Context) {
    private val app = context.applicationContext
    private val sp = app.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        sp.getString(KEY_THEME, null)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        sp.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
        applySystemNightMode(mode)
    }

    /**
     * 安卓 12 起把选择也告诉系统：启动画面、系统的选日期 / 时间对话框也跟着用同一套颜色。
     * 系统会自己记住，下次打开不用再设。
     */
    private fun applySystemNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val ui = app.getSystemService(UiModeManager::class.java) ?: return
        runCatching {
            ui.setApplicationNightMode(
                when (mode) {
                    ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
