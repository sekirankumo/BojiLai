package io.github.sekirankumo.fitlog.data

import io.github.sekirankumo.fitlog.platform.SyncPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 外观：跟随系统 / 浅色 / 深色 */
enum class ThemeMode(val label: String) {
    System("跟随系统"),
    Light("浅色"),
    Dark("深色"),
}

/**
 * App 自己的小设置（外观、是否每天自动调整营养）。同步读取（安卓 SharedPreferences / iOS UserDefaults），
 * 打开 App 的第一帧就是用户选的颜色，不会先闪一下另一套。
 * @param onThemeChanged 安卓 12 起把选择也告诉系统：启动画面、系统的选日期 / 时间对话框也跟着用同一套颜色
 */
class AppPrefs(
    private val sp: SyncPrefs,
    private val onThemeChanged: (ThemeMode) -> Unit = {},
) {
    private val _themeMode = MutableStateFlow(
        sp.getString(KEY_THEME)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _autoNutrition = MutableStateFlow(sp.getBoolean(KEY_AUTO_NUTRITION, true))

    /** 每天第一次打开时让 AI 根据前几天的表现重排接下来 7 天的营养目标 */
    val autoNutrition: StateFlow<Boolean> = _autoNutrition

    fun setAutoNutrition(on: Boolean) {
        sp.putBoolean(KEY_AUTO_NUTRITION, on)
        _autoNutrition.value = on
    }

    fun setThemeMode(mode: ThemeMode) {
        sp.putString(KEY_THEME, mode.name)
        _themeMode.value = mode
        onThemeChanged(mode)
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_AUTO_NUTRITION = "auto_nutrition"
    }
}
