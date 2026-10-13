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
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.ui.screens.AddFoodScreen
import io.github.sekirankumo.fitlog.ui.screens.AiSettingsScreen
import io.github.sekirankumo.fitlog.ui.screens.AssistantScreen
import io.github.sekirankumo.fitlog.ui.screens.BodyScreen
import io.github.sekirankumo.fitlog.ui.screens.DietScreen
import io.github.sekirankumo.fitlog.ui.screens.ExerciseDetailScreen
import io.github.sekirankumo.fitlog.ui.screens.ExerciseLibraryScreen
import io.github.sekirankumo.fitlog.ui.screens.GeneratePlanScreen
import io.github.sekirankumo.fitlog.ui.screens.OperationsScreen
import io.github.sekirankumo.fitlog.ui.screens.ProfileScreen
import io.github.sekirankumo.fitlog.ui.screens.SettingsNav
import io.github.sekirankumo.fitlog.ui.screens.SettingsScreen
import io.github.sekirankumo.fitlog.ui.screens.TodayNav
import io.github.sekirankumo.fitlog.ui.screens.TodayScreen
import io.github.sekirankumo.fitlog.ui.screens.TrainingNav
import io.github.sekirankumo.fitlog.ui.screens.TrainingScreen
import java.time.LocalDate

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "今日", Icons.Filled.Today),
    Diet("diet", "饮食", Icons.Filled.Restaurant),
    Training("training", "训练", Icons.Filled.FitnessCenter),
    Body("body", "身体", Icons.Filled.MonitorWeight),
}

// 路由名与 OpenPageTool 的 APP_PAGES 保持一致
private object Routes {
    const val SETTINGS = "settings"
    const val AI_SETTINGS = "ai_settings"
    const val PROFILE = "profile"
    const val OPERATIONS = "operations"
    const val ASSISTANT = "assistant"
    const val ADD_FOOD = "diet_add/{mode}/{day}"
    const val LIBRARY = "exercise_library"
    const val EXERCISE = "exercise/{id}"
    const val GENERATE = "generate_plan"

    fun addFood(mode: String, day: LocalDate) = "diet_add/$mode/${day.toEpochDay()}"
    fun exercise(id: String) = "exercise/$id"
}

private val TITLES = mapOf(
    Routes.SETTINGS to "设置",
    Routes.AI_SETTINGS to "AI 服务",
    Routes.PROFILE to "个人信息",
    Routes.OPERATIONS to "操作记录",
    Routes.ASSISTANT to "AI 助手",
    Routes.ADD_FOOD to "记录饮食",
    Routes.LIBRARY to "动作库",
    Routes.EXERCISE to "动作详情",
    Routes.GENERATE to "生成训练计划",
)

private fun NavController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BojiLaiRoot(container: AppContainer, openRoute: String? = null) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }

    // 每次启动检查一次更新；从通知进来时跳到对应页面；在前台时同步 Health Connect
    LaunchedEffect(Unit) {
        openRoute?.let { r -> Tab.entries.firstOrNull { it.route == r }?.let { nav.openTab(it.route) } }
        container.updateManager.check()
    }
    LaunchedEffect(Unit) {
        runCatching { if (container.healthSync.canReadBody()) container.healthSync.sync() }
    }
    UpdateDialog(container.updateManager)

    // AI 助手请求的页面跳转
    LaunchedEffect(Unit) {
        container.navEvents.collect { target ->
            if (Tab.entries.any { it.route == target }) nav.openTab(target) else nav.navigate(target) { launchSingleTop = true }
        }
    }

    fun askAi(text: String) {
        container.assistant.prefill.value = text
        nav.navigate(Routes.ASSISTANT) { launchSingleTop = true }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(TITLES[route] ?: currentTab?.label ?: "") },
                navigationIcon = {
                    if (route != null && currentTab == null) {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (currentTab != null) {
                        IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                    if (route == Routes.ASSISTANT) {
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
                            onClick = { nav.openTab(tab.route) },
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
                    onClick = { nav.navigate(Routes.ASSISTANT) },
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
            composable(Tab.Today.route) {
                TodayScreen(
                    container,
                    TodayNav(
                        addFood = { nav.navigate(Routes.addFood("camera", LocalDate.now())) },
                        openTraining = { nav.openTab(Tab.Training.route) },
                        openBody = { nav.openTab(Tab.Body.route) },
                        openDiet = { nav.openTab(Tab.Diet.route) },
                    ),
                )
            }
            composable(Tab.Diet.route) {
                DietScreen(container) { mode, day -> nav.navigate(Routes.addFood(mode, day)) }
            }
            composable(Tab.Training.route) {
                TrainingScreen(
                    container,
                    TrainingNav(
                        openExercise = { nav.navigate(Routes.exercise(it)) },
                        openLibrary = { nav.navigate(Routes.LIBRARY) },
                        openGenerate = { nav.navigate(Routes.GENERATE) },
                        openOperations = { nav.navigate(Routes.OPERATIONS) },
                        askAi = { askAi(it) },
                    ),
                )
            }
            composable(Tab.Body.route) { BodyScreen(container.database.bodyRecordDao()) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container,
                    SettingsNav(
                        openAiSettings = { nav.navigate(Routes.AI_SETTINGS) },
                        openProfile = { nav.navigate(Routes.PROFILE) },
                        openOperations = { nav.navigate(Routes.OPERATIONS) },
                    ),
                )
            }
            composable(Routes.AI_SETTINGS) { AiSettingsScreen(container) }
            composable(Routes.PROFILE) { ProfileScreen(container) { nav.popBackStack() } }
            composable(Routes.OPERATIONS) { OperationsScreen(container) }
            composable(Routes.ASSISTANT) { AssistantScreen(container.assistant) }
            composable(
                Routes.ADD_FOOD,
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType },
                    navArgument("day") { type = NavType.LongType },
                ),
            ) { entry ->
                AddFoodScreen(
                    container,
                    mode = entry.arguments?.getString("mode") ?: "text",
                    day = LocalDate.ofEpochDay(entry.arguments?.getLong("day") ?: LocalDate.now().toEpochDay()),
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.LIBRARY) {
                ExerciseLibraryScreen(container.exercises) { nav.navigate(Routes.exercise(it)) }
            }
            composable(
                Routes.EXERCISE,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                ExerciseDetailScreen(container.exercises[entry.arguments?.getString("id")])
            }
            composable(Routes.GENERATE) { GeneratePlanScreen(container) { nav.popBackStack() } }
        }
    }
}
