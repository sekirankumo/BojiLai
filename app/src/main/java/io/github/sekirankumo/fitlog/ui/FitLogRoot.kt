package io.github.sekirankumo.fitlog.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.ui.screens.AiSettingsScreen
import io.github.sekirankumo.fitlog.ui.screens.AssistantScreen
import io.github.sekirankumo.fitlog.ui.screens.BodyScreen
import io.github.sekirankumo.fitlog.ui.screens.DietScreen
import io.github.sekirankumo.fitlog.ui.screens.SettingsScreen
import io.github.sekirankumo.fitlog.ui.screens.TodayScreen
import io.github.sekirankumo.fitlog.ui.screens.TrainingScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "今日", Icons.Filled.Today),
    Diet("diet", "饮食", Icons.Filled.Restaurant),
    Training("training", "训练", Icons.Filled.FitnessCenter),
    Body("body", "身体", Icons.Filled.MonitorWeight),
}

// 路由名与 OpenPageTool 的 APP_PAGES 保持一致
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_AI_SETTINGS = "ai_settings"
private const val ROUTE_ASSISTANT = "assistant"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FitLogRoot(container: AppContainer) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }

    // 每次启动检查一次更新
    LaunchedEffect(Unit) { container.updateManager.check() }
    UpdateDialog(container.updateManager)

    // AI 助手请求的页面跳转
    LaunchedEffect(Unit) {
        container.navEvents.collect { target ->
            val tab = Tab.entries.firstOrNull { it.route == target }
            if (tab != null) {
                nav.navigate(tab.route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            } else {
                nav.navigate(target) { launchSingleTop = true }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        when (route) {
                            ROUTE_SETTINGS -> "设置"
                            ROUTE_AI_SETTINGS -> "AI 服务"
                            ROUTE_ASSISTANT -> "AI 助手"
                            else -> currentTab?.label ?: ""
                        }
                    )
                },
                navigationIcon = {
                    if (route != null && currentTab == null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (currentTab != null) {
                        IconButton(onClick = { nav.navigate(ROUTE_SETTINGS) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                    if (route == ROUTE_ASSISTANT) {
                        IconButton(onClick = { container.assistant.clear() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空对话")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (currentTab != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == currentTab,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab != null) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(ROUTE_ASSISTANT) },
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    text = { Text("问 AI") },
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Today.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Tab.Today.route) { TodayScreen() }
            composable(Tab.Diet.route) { DietScreen() }
            composable(Tab.Training.route) { TrainingScreen() }
            composable(Tab.Body.route) { BodyScreen(container.database.bodyRecordDao()) }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(container, onOpenAiSettings = { nav.navigate(ROUTE_AI_SETTINGS) })
            }
            composable(ROUTE_AI_SETTINGS) { AiSettingsScreen(container) }
            composable(ROUTE_ASSISTANT) { AssistantScreen(container.assistant) }
        }
    }
}
