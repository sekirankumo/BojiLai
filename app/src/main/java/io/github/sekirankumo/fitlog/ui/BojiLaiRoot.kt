package io.github.sekirankumo.fitlog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import io.github.sekirankumo.fitlog.ui.theme.Brand
import java.time.LocalDate

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Today("today", "今日", Icons.Outlined.WbSunny, Icons.Filled.WbSunny),
    Diet("diet", "饮食", Icons.Outlined.Restaurant, Icons.Filled.Restaurant),
    Training("training", "训练", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    Body("body", "身体", Icons.Outlined.MonitorWeight, Icons.Filled.MonitorWeight),
}

// 路由名与 OpenPageTool 的 APP_PAGES 保持一致
object Routes {
    const val SETTINGS = "settings"
    const val AI_SETTINGS = "ai_settings"
    const val PROFILE = "profile"
    const val OPERATIONS = "operations"
    const val ASSISTANT = "assistant"
    const val ADD_FOOD = "diet_add/{day}"
    const val LIBRARY = "exercise_library"
    const val EXERCISE = "exercise/{id}"
    const val GENERATE = "generate_plan"

    fun addFood(day: LocalDate) = "diet_add/${day.toEpochDay()}"
    fun exercise(id: String) = "exercise/$id"
}

private val TITLES = mapOf(
    Routes.SETTINGS to "设置",
    Routes.AI_SETTINGS to "AI 服务",
    Routes.PROFILE to "个人信息",
    Routes.OPERATIONS to "调整记录",
    Routes.ASSISTANT to "AI 助手",
    Routes.ADD_FOOD to "记一餐",
    Routes.LIBRARY to "动作库",
    Routes.EXERCISE to "动作详情",
    Routes.GENERATE to "生成训练计划",
)

private fun NavController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/**
 * @param openRoute 启动后直接打开的页面（通知、截图测试用）
 * @param sideEffects 截图测试时关闭检查更新、同步等副作用
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BojiLaiRoot(container: AppContainer, openRoute: String? = null, sideEffects: Boolean = true) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }

    LaunchedEffect(Unit) {
        openRoute?.let { r -> if (Tab.entries.any { it.route == r }) nav.openTab(r) else nav.navigate(r) }
    }
    if (sideEffects) {
        // 启动时检查更新；在前台时同步 Health Connect
        LaunchedEffect(Unit) { container.updateManager.check() }
        LaunchedEffect(Unit) {
            runCatching { if (container.healthSync.canReadBody()) container.healthSync.sync() }
        }
        UpdateDialog(container.updateManager)
    }

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

    val colors = Brand.colors
    Scaffold(
        containerColor = colors.bg,
        topBar = {
            TopAppBar(
                title = {
                    if (currentTab == Tab.Today) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LogoMark()
                            Text("  薄肌来", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        }
                    } else {
                        Text(
                            TITLES[route] ?: currentTab?.label ?: "",
                            style = if (currentTab != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        )
                    }
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
                        IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置", tint = colors.muted)
                        }
                    }
                    if (route == Routes.ASSISTANT) {
                        IconButton(onClick = { container.assistant.clear() }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "清空对话", tint = colors.muted)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, scrolledContainerColor = colors.bg),
            )
        },
        bottomBar = {
            if (currentTab != null) {
                BottomBar(
                    current = currentTab,
                    onTab = { nav.openTab(it.route) },
                    onAi = { nav.navigate(Routes.ASSISTANT) },
                )
            }
        },
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
                        openTraining = { nav.openTab(Tab.Training.route) },
                        openBody = { nav.openTab(Tab.Body.route) },
                        openDiet = { nav.openTab(Tab.Diet.route) },
                        openSettings = { nav.navigate(Routes.SETTINGS) },
                        askAi = { askAi(it) },
                    ),
                )
            }
            composable(Tab.Diet.route) {
                DietScreen(container) { day -> nav.navigate(Routes.addFood(day)) }
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
            composable(Tab.Body.route) { BodyScreen(container) }
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
                arguments = listOf(navArgument("day") { type = NavType.LongType }),
            ) { entry ->
                AddFoodScreen(
                    container,
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

/** 底部导航：左右各两个页面，中间是凸起的「问 AI」按钮 */
@Composable
private fun BottomBar(current: Tab, onTab: (Tab) -> Unit, onAi: () -> Unit) {
    val colors = Brand.colors
    Surface(color = colors.surface) {
        Column {
            HorizontalDivider(color = colors.line)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(Tab.Today, current, onTab)
                TabItem(Tab.Diet, current, onTab)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Surface(
                        onClick = onAi,
                        shape = CircleShape,
                        color = colors.brand,
                        border = BorderStroke(4.dp, colors.surface),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(60.dp).offset(y = (-14).dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "问 AI", tint = colors.onBrand, modifier = Modifier.size(22.dp))
                            Text("问 AI", style = MaterialTheme.typography.labelSmall, color = colors.onBrand, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                TabItem(Tab.Training, current, onTab)
                TabItem(Tab.Body, current, onTab)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: Tab, current: Tab, onTab: (Tab) -> Unit) {
    val colors = Brand.colors
    val selected = tab == current
    Column(
        Modifier
            .weight(1f)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab) { onTab(tab) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) colors.brandSoft else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = tab.label,
                tint = if (selected) colors.brand else colors.muted,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.brand else colors.muted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** 小号品牌标志 */
@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp = 28.dp) {
    val colors = Brand.colors
    Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.28f)).background(colors.brand),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(size * 0.62f))
    }
}
