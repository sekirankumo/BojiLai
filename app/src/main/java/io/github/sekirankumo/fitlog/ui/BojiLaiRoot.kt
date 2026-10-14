package io.github.sekirankumo.fitlog.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.navigation.NavBackStackEntry
import io.github.sekirankumo.fitlog.ui.components.Motion
import io.github.sekirankumo.fitlog.ui.components.pressScale
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import io.github.sekirankumo.fitlog.ui.components.LocalReduceMotion
import kotlinx.coroutines.delay
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
import io.github.sekirankumo.fitlog.ui.screens.FavoritesScreen
import io.github.sekirankumo.fitlog.ui.components.LocalSpeechEngine
import io.github.sekirankumo.fitlog.ui.screens.TodayScreen
import io.github.sekirankumo.fitlog.ui.screens.TrainingNav
import io.github.sekirankumo.fitlog.ui.screens.TrainingScreen
import io.github.sekirankumo.fitlog.ui.theme.Brand
import kotlinx.coroutines.flow.first
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
    const val FAVORITES = "favorites"

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
    Routes.FAVORITES to "常吃食物",
)

private val TAB_ROUTES = Tab.entries.map { it.route }.toSet()

/** 从二级页面返回主页签时，底栏等多久再出现（和页面退出动画差不多长） */
private const val BAR_DELAY_MS = 250L
private const val BAR_FADE_MS = 200

/** 两个底部页签之间切换：平级页面，只做快速淡入（带一点点放大），不左右滑 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

private fun tabEnter() =
    fadeIn(tween(Motion.MEDIUM, delayMillis = 60, easing = Motion.EmphasizedDecelerate)) +
        scaleIn(tween(Motion.MEDIUM, delayMillis = 60, easing = Motion.EmphasizedDecelerate), initialScale = 0.98f)

private fun tabExit() = fadeOut(tween(90))

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
    // 底栏滑出时还要显示离开前的页签
    val lastTab = remember { arrayOf(Tab.Today) }
    SideEffect { if (currentTab != null) lastTab[0] = currentTab }

    // 底栏：进二级页面时立刻收起；回到主页签时等页面切换动画走完再浮上来，
    // 免得底栏一出现就把正在退出的页面（比如 AI 助手的输入框）往上挤
    val reduceMotion = LocalReduceMotion.current
    var barShown by remember { mutableStateOf(false) }
    val barEverShown = remember { booleanArrayOf(false) }
    LaunchedEffect(currentTab != null) {
        if (currentTab != null) {
            if (barEverShown[0] && !reduceMotion) delay(BAR_DELAY_MS)
            barShown = true
            barEverShown[0] = true
        } else {
            barShown = false
        }
    }

    LaunchedEffect(Unit) {
        if (openRoute == null) return@LaunchedEffect
        // 等导航图建好（第一个页面出现）后再跳转
        nav.currentBackStackEntryFlow.first()
        if (Tab.entries.any { it.route == openRoute }) nav.openTab(openRoute) else nav.navigate(openRoute)
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
    // 语音输入用的内置离线识别
    CompositionLocalProvider(LocalSpeechEngine provides container.speech) {
        Scaffold(
            containerColor = colors.bg,
            topBar = {
                // 整条顶栏作为一个整体原地淡入淡出：返回箭头、标题、右侧按钮一起换，
                // 不会出现标题被箭头挤走、从左往右擦出来的情况
                Crossfade(targetState = route, animationSpec = tween(Motion.MEDIUM), label = "topBar") { r ->
                    val tab = Tab.entries.firstOrNull { it.route == r }
                    TopAppBar(
                        title = {
                            if (tab == Tab.Today) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LogoMark()
                                    Text("  薄肌来", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                }
                            } else {
                                Text(
                                    TITLES[r] ?: tab?.label ?: "",
                                    style = if (tab != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                                )
                            }
                        },
                        navigationIcon = {
                            if (r != null && tab == null) {
                                IconButton(onClick = { nav.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        },
                        actions = {
                            if (tab != null) {
                                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                                    Icon(Icons.Filled.Settings, contentDescription = "设置", tint = colors.muted)
                                }
                            }
                            if (r == Routes.ASSISTANT) {
                                IconButton(onClick = { container.assistant.clear() }) {
                                    Icon(Icons.Filled.DeleteSweep, contentDescription = "清空对话", tint = colors.muted)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg, scrolledContainerColor = colors.bg),
                    )
                }
            },
            bottomBar = {
                if (barShown) {
                    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
                    LaunchedEffect(Unit) { appear.animateTo(1f, tween(BAR_FADE_MS, easing = Motion.EmphasizedDecelerate)) }
                    BottomBar(
                        current = currentTab ?: lastTab[0],
                        onTab = { nav.openTab(it.route) },
                        onAi = { nav.navigate(Routes.ASSISTANT) },
                        modifier = Modifier.graphicsLayer {
                            alpha = appear.value
                            translationY = (1f - appear.value) * 24.dp.toPx()
                            // 半透明时默认会先画到和底栏一样大的离屏画布上，凸出底栏的「问 AI」按钮会被裁掉；
                            // 改成逐个绘制时乘透明度，不经过离屏画布
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    )
                }
            },
        ) { padding ->
            // 营养目标缺资料时的「去填写」：只差体重记录就去身体页，否则去个人信息
            val completeProfile: (List<String>) -> Unit = { missing ->
                if (missing == listOf("体重记录")) nav.openTab(Tab.Body.route) else nav.navigate(Routes.PROFILE)
            }
            NavHost(
                navController = nav,
                startDestination = Tab.Today.route,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                enterTransition = {
                    when {
                        betweenTabs() -> tabEnter()
                        targetState.destination.route == Routes.ASSISTANT ->
                            slideInVertically(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { it / 6 } + fadeIn(tween(Motion.MEDIUM))
                        else -> slideInHorizontally(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { it / 4 } + fadeIn(tween(Motion.MEDIUM))
                    }
                },
                exitTransition = {
                    when {
                        betweenTabs() -> tabExit()
                        targetState.destination.route == Routes.ASSISTANT ->
                            scaleOut(tween(Motion.LONG, easing = Motion.Emphasized), targetScale = 0.96f) + fadeOut(tween(Motion.LONG))
                        else -> slideOutHorizontally(tween(Motion.LONG, easing = Motion.Emphasized)) { -it / 10 } + fadeOut(tween(Motion.MEDIUM))
                    }
                },
                popEnterTransition = {
                    when {
                        betweenTabs() -> tabEnter()
                        initialState.destination.route == Routes.ASSISTANT ->
                            scaleIn(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate), initialScale = 0.96f) + fadeIn(tween(Motion.MEDIUM))
                        else -> slideInHorizontally(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { -it / 10 } + fadeIn(tween(Motion.MEDIUM))
                    }
                },
                popExitTransition = {
                    when {
                        betweenTabs() -> tabExit()
                        initialState.destination.route == Routes.ASSISTANT ->
                            slideOutVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedAccelerate)) { it / 6 } + fadeOut(tween(Motion.MEDIUM))
                        else -> slideOutHorizontally(tween(Motion.MEDIUM, easing = Motion.EmphasizedAccelerate)) { it / 4 } + fadeOut(tween(Motion.MEDIUM))
                    }
                },
            ) {
                composable(Tab.Today.route) {
                    TodayScreen(
                        container,
                        TodayNav(
                            openTraining = { nav.openTab(Tab.Training.route) },
                            openBody = { nav.openTab(Tab.Body.route) },
                            openDiet = { nav.openTab(Tab.Diet.route) },
                            completeProfile = completeProfile,
                        ),
                    )
                }
                composable(Tab.Diet.route) {
                    DietScreen(container, completeProfile) { day -> nav.navigate(Routes.addFood(day)) }
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
                            openFavorites = { nav.navigate(Routes.FAVORITES) },
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
                        openFavorites = { nav.navigate(Routes.FAVORITES) },
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
                composable(Routes.FAVORITES) { FavoritesScreen(container) }
            }
        }
    }
}

/** 底部导航：左右各两个页面，中间是凸起的「问 AI」按钮 */
@Composable
private fun BottomBar(current: Tab, onTab: (Tab) -> Unit, onAi: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Brand.colors
    // 用不裁剪的 Box 叠放：底栏在下，「问 AI」按钮浮在上面，凸出的部分不会被切掉
    Box(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().background(colors.surface)) {
            HorizontalDivider(color = colors.line)
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabItem(Tab.Today, current, onTab)
                TabItem(Tab.Diet, current, onTab)
                Box(Modifier.weight(1f))
                TabItem(Tab.Training, current, onTab)
                TabItem(Tab.Body, current, onTab)
            }
        }
        val aiInteraction = remember { MutableInteractionSource() }
        Surface(
            onClick = onAi,
            shape = CircleShape,
            color = colors.brand,
            border = BorderStroke(4.dp, colors.surface),
            shadowElevation = 6.dp,
            interactionSource = aiInteraction,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-20).dp).size(64.dp).pressScale(aiInteraction, 0.92f),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "问 AI", tint = colors.onBrand, modifier = Modifier.size(22.dp))
                Text("问 AI", style = MaterialTheme.typography.labelSmall, color = colors.onBrand, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: Tab, current: Tab, onTab: (Tab) -> Unit) {
    val colors = Brand.colors
    val selected = tab == current
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        tween(Motion.MEDIUM, easing = Motion.Emphasized),
        label = "tab",
    )
    val tint = lerp(colors.muted, colors.brand, progress)
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
                .drawBehind {
                    val p = progress.coerceIn(0f, 1f)
                    if (p > 0.01f) {
                        val w = size.width * (0.4f + 0.6f * p)
                        drawRoundRect(
                            colors.brandSoft.copy(alpha = p),
                            topLeft = Offset((size.width - w) / 2, 0f),
                            size = Size(w, size.height),
                            cornerRadius = CornerRadius(size.height / 2),
                        )
                    }
                }
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
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
