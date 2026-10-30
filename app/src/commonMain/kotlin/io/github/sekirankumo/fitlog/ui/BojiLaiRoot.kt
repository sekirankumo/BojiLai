package io.github.sekirankumo.fitlog.ui

import kotlinx.datetime.*
import io.github.sekirankumo.fitlog.util.*
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import io.github.sekirankumo.fitlog.ui.icons.automirrored.outlined.MenuBook
import io.github.sekirankumo.fitlog.ui.icons.outlined.DeleteSweep
import io.github.sekirankumo.fitlog.ui.icons.outlined.FitnessCenter
import io.github.sekirankumo.fitlog.ui.icons.outlined.MonitorWeight
import io.github.sekirankumo.fitlog.ui.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import io.github.sekirankumo.fitlog.ui.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.sekirankumo.fitlog.AppContainer
import io.github.sekirankumo.fitlog.ui.components.AiOrb
import io.github.sekirankumo.fitlog.ui.components.AppIcons
import io.github.sekirankumo.fitlog.ui.components.LocalHaze
import io.github.sekirankumo.fitlog.ui.components.LocalReduceMotion
import io.github.sekirankumo.fitlog.ui.components.LocalScreenInsets
import io.github.sekirankumo.fitlog.ui.components.LocalSpeechEngine
import io.github.sekirankumo.fitlog.ui.components.LocalToast
import io.github.sekirankumo.fitlog.ui.components.LocalTopBar
import io.github.sekirankumo.fitlog.ui.components.Motion
import io.github.sekirankumo.fitlog.ui.components.ScreenInsets
import io.github.sekirankumo.fitlog.ui.components.ToastHost
import io.github.sekirankumo.fitlog.ui.components.ToastState
import io.github.sekirankumo.fitlog.ui.components.TopBarState
import io.github.sekirankumo.fitlog.ui.components.glass
import io.github.sekirankumo.fitlog.ui.components.pressScale
import io.github.sekirankumo.fitlog.ui.screens.AddFoodScreen
import io.github.sekirankumo.fitlog.ui.screens.AiSettingsScreen
import io.github.sekirankumo.fitlog.ui.screens.AssistantScreen
import io.github.sekirankumo.fitlog.ui.screens.BodyScreen
import io.github.sekirankumo.fitlog.ui.screens.DietScreen
import io.github.sekirankumo.fitlog.ui.screens.ExerciseDetailScreen
import io.github.sekirankumo.fitlog.ui.screens.ExerciseLibraryScreen
import io.github.sekirankumo.fitlog.ui.screens.FavoritesScreen
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
import kotlinx.coroutines.flow.first

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Today("today", "今日", Icons.Outlined.WbSunny),
    Diet("diet", "饮食", Icons.Outlined.Restaurant),
    Training("training", "训练", Icons.Outlined.FitnessCenter),
    Body("body", "身体", Icons.Outlined.MonitorWeight),
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
    Routes.PROFILE to "个人信息与目标",
    Routes.OPERATIONS to "调整记录",
    Routes.ASSISTANT to "AI 助手",
    Routes.ADD_FOOD to "记一餐",
    Routes.LIBRARY to "动作库",
    Routes.EXERCISE to "动作详情",
    Routes.GENERATE to "生成训练计划",
    Routes.FAVORITES to "常吃食物",
)

private val TAB_ROUTES = Tab.entries.map { it.route }.toSet()

/** 顶栏的高度（不含状态栏）：主页面的大标题、二级页面的返回和标题都在这一行里 */
private val BAR_HEIGHT = 58.dp

/** 底部导航的高度和离屏幕底边的距离 */
private val NAV_HEIGHT = 64.dp
private val NAV_GAP = 14.dp

private fun AnimatedContentTransitionScope<NavBackStackEntry>.betweenTabs() =
    initialState.destination.route in TAB_ROUTES && targetState.destination.route in TAB_ROUTES

/** 切换底部标签：内容原地淡入、上移 8dp，不左右滑 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnter() =
    fadeIn(tween(Motion.MEDIUM, delayMillis = 60, easing = Motion.EmphasizedDecelerate)) +
        slideInVertically(tween(Motion.MEDIUM, delayMillis = 60, easing = Motion.EmphasizedDecelerate)) { it / 90 }

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
@Composable
fun BojiLaiRoot(container: AppContainer, openRoute: String? = null, sideEffects: Boolean = true) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTab = Tab.entries.firstOrNull { it.route == route }
    // 底栏淡出时还要显示离开前的页签
    val lastTab = remember { arrayOf(Tab.Today) }
    SideEffect { if (currentTab != null) lastTab[0] = currentTab }

    val reduceMotion = LocalReduceMotion.current
    val haze = remember { HazeState() }
    val toast = remember { ToastState() }
    // 每个页面一份顶栏状态（普通 Map：取用时不写入快照状态，状态本身的字段才是可观察的）
    val bars = remember { HashMap<String, TopBarState>() }
    fun barOf(r: String) = bars.getOrPut(r) { TopBarState() }

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

    val c = Brand.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabInsets = ScreenInsets(top = statusTop + BAR_HEIGHT, bottom = navBottom + NAV_HEIGHT + NAV_GAP)
    val subInsets = ScreenInsets(top = statusTop + BAR_HEIGHT, bottom = navBottom)
    val busy by container.assistant.busy.collectAsState()

    CompositionLocalProvider(
        LocalSpeechEngine provides container.speech,
        LocalHaze provides if (reduceMotion) null else haze,
        LocalToast provides toast,
    ) {
        Box(Modifier.fillMaxSize().background(c.bg)) {
            // 营养目标缺资料时的「去填写」：只差体重记录就去身体页，否则去个人信息
            val completeProfile: (List<String>) -> Unit = { missing ->
                if (missing == listOf("体重记录")) nav.openTab(Tab.Body.route) else nav.navigate(Routes.PROFILE)
            }
            NavHost(
                navController = nav,
                startDestination = Tab.Today.route,
                modifier = Modifier.fillMaxSize().then(if (reduceMotion) Modifier else Modifier.hazeSource(haze)),
                enterTransition = {
                    when {
                        betweenTabs() -> tabEnter()
                        targetState.destination.route == Routes.ASSISTANT ->
                            slideInVertically(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate)) { it / 8 } + fadeIn(tween(Motion.MEDIUM))
                        else -> slideInHorizontally(tween(340, easing = Motion.EmphasizedDecelerate)) { it / 14 } + fadeIn(tween(Motion.MEDIUM))
                    }
                },
                exitTransition = {
                    when {
                        betweenTabs() -> tabExit()
                        targetState.destination.route == Routes.ASSISTANT ->
                            scaleOut(tween(Motion.LONG, easing = Motion.Emphasized), targetScale = 0.97f) + fadeOut(tween(Motion.MEDIUM))
                        else -> slideOutHorizontally(tween(340, easing = Motion.Emphasized)) { -it / 28 } + fadeOut(tween(Motion.MEDIUM))
                    }
                },
                popEnterTransition = {
                    when {
                        betweenTabs() -> tabEnter()
                        initialState.destination.route == Routes.ASSISTANT ->
                            scaleIn(tween(Motion.LONG, easing = Motion.EmphasizedDecelerate), initialScale = 0.97f) + fadeIn(tween(Motion.MEDIUM))
                        else -> slideInHorizontally(tween(340, easing = Motion.EmphasizedDecelerate)) { -it / 28 } + fadeIn(tween(Motion.MEDIUM))
                    }
                },
                popExitTransition = {
                    when {
                        betweenTabs() -> tabExit()
                        initialState.destination.route == Routes.ASSISTANT ->
                            slideOutVertically(tween(Motion.MEDIUM, easing = Motion.EmphasizedAccelerate)) { it / 8 } + fadeOut(tween(Motion.MEDIUM))
                        else -> slideOutHorizontally(tween(Motion.MEDIUM, easing = Motion.EmphasizedAccelerate)) { it / 14 } + fadeOut(tween(Motion.SHORT))
                    }
                },
            ) {
                composable(Tab.Today.route) {
                    Page(barOf(Tab.Today.route), tabInsets) {
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
                }
                composable(Tab.Diet.route) {
                    Page(barOf(Tab.Diet.route), tabInsets) {
                        DietScreen(container, completeProfile) { day -> nav.navigate(Routes.addFood(day)) }
                    }
                }
                composable(Tab.Training.route) {
                    Page(barOf(Tab.Training.route), tabInsets) {
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
                }
                composable(Tab.Body.route) { Page(barOf(Tab.Body.route), tabInsets) { BodyScreen(container) } }
                composable(Routes.SETTINGS) {
                    Page(barOf(Routes.SETTINGS), subInsets) {
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
                }
                composable(Routes.AI_SETTINGS) { Page(barOf(Routes.AI_SETTINGS), subInsets) { AiSettingsScreen(container) } }
                composable(Routes.PROFILE) { Page(barOf(Routes.PROFILE), subInsets) { ProfileScreen(container) { nav.popBackStack() } } }
                composable(Routes.OPERATIONS) { Page(barOf(Routes.OPERATIONS), subInsets) { OperationsScreen(container) } }
                composable(Routes.ASSISTANT) {
                    Page(barOf(Routes.ASSISTANT), subInsets) {
                        AssistantScreen(container.assistant, openAiSettings = { nav.navigate(Routes.AI_SETTINGS) })
                    }
                }
                composable(
                    Routes.ADD_FOOD,
                    arguments = listOf(navArgument("day") { type = NavType.LongType }),
                ) { entry ->
                    Page(barOf(Routes.ADD_FOOD), subInsets) {
                        AddFoodScreen(
                            container,
                            day = LocalDate.ofEpochDay(entry.savedStateHandle.get<Long>("day") ?: LocalDate.now().toEpochDay()),
                            onDone = { nav.popBackStack() },
                            openFavorites = { nav.navigate(Routes.FAVORITES) },
                        )
                    }
                }
                composable(Routes.LIBRARY) {
                    Page(barOf(Routes.LIBRARY), subInsets) {
                        ExerciseLibraryScreen(container.exercises) { nav.navigate(Routes.exercise(it)) }
                    }
                }
                composable(
                    Routes.EXERCISE,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    Page(barOf(Routes.EXERCISE), subInsets) { ExerciseDetailScreen(container.exercises[entry.savedStateHandle.get<String>("id")]) }
                }
                composable(Routes.GENERATE) { Page(barOf(Routes.GENERATE), subInsets) { GeneratePlanScreen(container) { nav.popBackStack() } } }
                composable(Routes.FAVORITES) { Page(barOf(Routes.FAVORITES), subInsets) { FavoritesScreen(container) } }
            }

            TopBar(
                route = route,
                stateOf = { r -> r?.let(::barOf) },
                onBack = { nav.popBackStack() },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onLibrary = { nav.navigate(Routes.LIBRARY) },
                onClearChat = { container.assistant.clear() },
            )

            // 底栏浮在页面上方、不占页面的位置：进出只做淡入淡出和轻微位移
            FloatingBottomBar(
                current = currentTab,
                fallback = lastTab[0],
                reduceMotion = reduceMotion,
                busy = busy,
                onTab = { nav.openTab(it.route) },
                onAi = { nav.navigate(Routes.ASSISTANT) },
            )

            // 提示条：主页面在底栏上方，二级页面贴着底部（AI 助手和记一餐留出输入框 / 保存栏）
            val toastBottom = when {
                currentTab != null -> navBottom + NAV_HEIGHT + NAV_GAP + 12.dp
                route == Routes.ASSISTANT || route == Routes.ADD_FOOD -> navBottom + 92.dp
                else -> navBottom + 24.dp
            }
            ToastHost(
                toast,
                Modifier.align(Alignment.BottomCenter).padding(bottom = toastBottom, start = 16.dp, end = 16.dp),
            )
        }
    }
}

/**
 * 顶栏：本身没有底色。内容滑到顶部时逐渐淡出（渐隐边缘），没有内容经过时看不见，
 * 所以不会出现「突然变成一块玻璃」。主页面左边是大标题（今日是问候语），滑动后收成小标题 / 摘要；
 * 二级页面是返回箭头 + 标题。整条顶栏随页面切换原地淡入淡出。
 */
@Composable
private fun BoxScope.TopBar(
    route: String?,
    stateOf: (String?) -> TopBarState?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onLibrary: () -> Unit,
    onClearChat: () -> Unit,
) {
    val c = Brand.colors
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val edge by animateFloatAsState(stateOf(route)?.edge ?: 0f, tween(Motion.SHORT), label = "edge")
    // 渐隐边缘比顶栏多往下延伸一点，内容是慢慢融进背景里的
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(statusTop + BAR_HEIGHT + 28.dp)
            .drawBehind {
                if (edge > 0.01f) {
                    // 状态栏和顶栏下面基本不透，顶栏以下的 28dp 再慢慢融进背景
                    val bar = (statusTop + BAR_HEIGHT).toPx() / size.height
                    drawRect(
                        Brush.verticalGradient(
                            0f to c.bg.copy(alpha = edge),
                            bar * 0.75f to c.bg.copy(alpha = edge),
                            bar to c.bg.copy(alpha = 0.9f * edge),
                            1f to c.bg.copy(alpha = 0f),
                        ),
                    )
                }
            },
    )
    Crossfade(
        targetState = route,
        animationSpec = tween(Motion.MEDIUM),
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = statusTop),
        label = "topBar",
    ) { r ->
        val tab = Tab.entries.firstOrNull { it.route == r }
        val bar = stateOf(r)
        val compact = bar?.compact == true
        Row(
            Modifier.fillMaxWidth().height(BAR_HEIGHT).padding(start = if (tab == null) 4.dp else 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (r != null && tab == null) {
                BarIcon(AppIcons.Back, "返回", c.text, onBack)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                when {
                    tab == Tab.Today -> SwapTitle(
                        compact = compact && bar?.compactText != null,
                        main = { Text(bar?.mainText ?: "", style = Brand.type.body, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        small = { Text(bar?.compactText ?: androidx.compose.ui.text.AnnotatedString(""), style = Brand.type.label, color = c.muted, maxLines = 1) },
                    )
                    tab != null -> SwapTitle(
                        compact = compact,
                        main = { Text(tab.label, style = Brand.type.h1, color = c.text) },
                        small = { Text(tab.label, style = Brand.type.title, color = c.text) },
                    )
                    else -> Text(TITLES[r] ?: "", style = Brand.type.title, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (tab == Tab.Training) BarIcon(Icons.AutoMirrored.Outlined.MenuBook, "动作库", c.muted, onLibrary)
            if (tab != null) BarIcon(Icons.Outlined.Settings, "设置", c.muted, onSettings)
            if (r == Routes.ASSISTANT) BarIcon(Icons.Outlined.DeleteSweep, "清空对话", c.muted, onClearChat)
        }
    }
}

/** 大标题 ↔ 小标题：交叉淡入，并上下错开 8dp */
@Composable
private fun SwapTitle(compact: Boolean, main: @Composable () -> Unit, small: @Composable () -> Unit) {
    val p by animateFloatAsState(if (compact) 1f else 0f, tween(Motion.MEDIUM, easing = Motion.Emphasized), label = "swap")
    Box(contentAlignment = Alignment.CenterStart) {
        Box(Modifier.graphicsLayer { alpha = 1f - p; translationY = -8.dp.toPx() * p }) { main() }
        Box(Modifier.graphicsLayer { alpha = p; translationY = 8.dp.toPx() * (1f - p) }) { small() }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).clickable(onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** 浮在页面最下方的玻璃底栏：在主页签时淡入，进二级页面时淡出并下沉一点 */
@Composable
private fun BoxScope.FloatingBottomBar(
    current: Tab?,
    fallback: Tab,
    reduceMotion: Boolean,
    busy: Boolean,
    onTab: (Tab) -> Unit,
    onAi: () -> Unit,
) {
    val visible = current != null
    val alpha = animateFloatAsState(
        if (visible) 1f else 0f,
        if (reduceMotion) snap() else tween(Motion.MEDIUM, easing = Motion.Emphasized),
        label = "bottomBar",
    )
    // 只在「完全隐藏 ↔ 不是完全隐藏」切换时重组，透明度变化只在绘制时读取
    val composed by remember { derivedStateOf { alpha.value > 0f } }
    if (!composed) return
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BoxWithConstraints(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(bottom = navBottom + NAV_GAP)
            .graphicsLayer {
                this.alpha = alpha.value
                translationY = (1f - alpha.value) * 28.dp.toPx()
                // 半透明时逐个绘制再乘透明度，不经过离屏画布，凸出底栏的光球不会被裁掉
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val width = minOf(maxWidth - 28.dp, 402.dp)
        BottomBar(current ?: fallback, busy, Modifier.width(width), onTab = { if (visible) onTab(it) }, onAi = { if (visible) onAi() })
    }
}

/** 底部导航：左右各两个页面，中间是 AI 光球 */
@Composable
private fun BottomBar(current: Tab, busy: Boolean, modifier: Modifier, onTab: (Tab) -> Unit, onAi: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(modifier.height(NAV_HEIGHT), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .shadow(14.dp, shape, ambientColor = Color(0x22101A15), spotColor = Color(0x30101A15))
                .glass(shape),
        )
        Row(Modifier.fillMaxWidth().height(NAV_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            TabItem(Tab.Today, current, onTab)
            TabItem(Tab.Diet, current, onTab)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .offset(y = (-2).dp)
                        .pressScale(interaction, 0.92f)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onAi)
                        .semantics { contentDescription = "问 AI" },
                ) {
                    AiOrb(52.dp, busy = busy)
                }
            }
            TabItem(Tab.Training, current, onTab)
            TabItem(Tab.Body, current, onTab)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(tab: Tab, current: Tab, onTab: (Tab) -> Unit) {
    val c = Brand.colors
    val selected = tab == current
    val iconTint by animateColorAsState(if (selected) c.brand else c.muted, tween(Motion.MEDIUM), label = "tabIcon")
    val textTint by animateColorAsState(if (selected) c.text else c.muted, tween(Motion.MEDIUM), label = "tabText")
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab) { onTab(tab) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = iconTint, modifier = Modifier.size(22.dp))
        Text(tab.label, style = Brand.type.tiny, color = textTint)
    }
}

/** 小号品牌标志（兼容旧调用） */
@Composable
fun LogoMark(size: androidx.compose.ui.unit.Dp = 28.dp) = io.github.sekirankumo.fitlog.ui.components.LogoMark(size)

/** 页面拿到自己的顶栏状态和四周留白 */
@Composable
private fun Page(bar: TopBarState, insets: ScreenInsets, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTopBar provides bar, LocalScreenInsets provides insets, content = content)
}
