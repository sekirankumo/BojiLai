package io.github.sekirankumo.fitlog

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import androidx.compose.ui.test.onNodeWithText
import io.github.sekirankumo.fitlog.data.ai.AiSettings
import androidx.compose.runtime.CompositionLocalProvider
import io.github.sekirankumo.fitlog.ui.BojiLaiRoot
import io.github.sekirankumo.fitlog.ui.components.LocalReduceMotion
import io.github.sekirankumo.fitlog.ui.components.VoicePanel
import io.github.sekirankumo.fitlog.ui.theme.Brand
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.sekirankumo.fitlog.ui.theme.BojiLaiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 测试时不跑 App 的启动逻辑（WorkManager、自动检查等） */
class TestApp : Application()

/**
 * 用示例数据渲染各个页面并截图，输出到 app/screenshots/。
 * 运行：gradle :app:recordRoborazziDebug（中文字体目录通过环境变量 CJK_FONT_DIR 指定）
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xhdpi", application = TestApp::class)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var container: AppContainer

    private val font: FontFamily? by lazy {
        val dir = System.getenv("CJK_FONT_DIR")?.let(::File)?.takeIf { it.isDirectory } ?: return@lazy null
        fun f(name: String, weight: FontWeight) = File(dir, name).takeIf { it.exists() }?.let { Font(it, weight) }
        listOfNotNull(
            f("NotoSansSC-Regular.otf", FontWeight.Normal),
            f("NotoSansSC-Medium.otf", FontWeight.Medium),
            f("NotoSansSC-Medium.otf", FontWeight.SemiBold),
            f("NotoSansSC-Bold.otf", FontWeight.Bold),
            f("NotoSansSC-Bold.otf", FontWeight.ExtraBold),
        ).takeIf { it.isNotEmpty() }?.let { FontFamily(it) }
    }

    @Before
    fun setUp() {
        container = AppContainer(ApplicationProvider.getApplicationContext())
        runBlocking { SampleData.seed(container) }
    }

    private fun shot(
        name: String,
        route: String,
        dark: Boolean = false,
        after: () -> Unit = {},
        before: () -> Unit = {},
    ) {
        before()
        compose.setContent {
            BojiLaiTheme(darkTheme = dark, fontFamily = font) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    BojiLaiRoot(container, route, sideEffects = false)
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        after()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun today() = shot("today", "today")
    @Test fun todayDark() = shot("today_dark", "today", dark = true)

    /** 今日页往下滚动到训练和身体状态 */
    @Test fun todayBody() = shot("today_body", "today", after = {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
    })
    @Test fun diet() = shot("diet", "diet")
    @Test fun dietDark() = shot("diet_dark", "diet", dark = true)

    /** 饮食页往下滚到膳食质量卡片 */
    @Test fun dietQuality() = shot("diet_quality", "diet", after = {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(3)
    })

    /** 饮食页再往下：记录上的高盐标签 */
    @Test fun dietMeals() = shot("diet_meals", "diet", after = {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
    })
    @Test fun addFood() = shot("add_food", "diet_add/${java.time.LocalDate.now().toEpochDay()}") {
        container.draftInbox.value = SampleData.drafts()
    }
    @Test fun training() = shot("training", "training")
    @Test fun trainingDark() = shot("training_dark", "training", dark = true)

    /** 训练页往下滚动到当天的动作和打卡 */
    @Test fun trainingDay() = shot("training_day", "training", after = {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
    })
    @Test fun exercise() = shot("exercise", "exercise/Barbell_Bench_Press_-_Medium_Grip")
    @Test fun library() = shot("library", "exercise_library")
    @Test fun body() = shot("body", "body")
    @Test fun bodyDark() = shot("body_dark", "body", dark = true)

    /** 身体页往下：恢复状态和记录 */
    @Test fun bodyRecords() = shot("body_records", "body", after = {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
    })
    @Test fun assistant() = shot("assistant", "assistant") {
        container.assistant.showPreview(SampleData.chat())
    }
    @Test fun assistantTable() = shot("assistant_table", "assistant") {
        container.assistant.showPreview(SampleData.chatTable())
    }
    @Test fun assistantDark() = shot("assistant_dark", "assistant", dark = true) {
        container.assistant.showPreview(SampleData.chat())
    }

    /** 助手首屏：光球、模型名、建议 */
    @Test fun assistantWelcome() = shot("assistant_welcome", "assistant")
    @Test fun settings() = shot("settings", "settings")
    @Test fun settingsDark() = shot("settings_dark", "settings", dark = true)
    @Test fun aiSettings() = shot("ai_settings", "ai_settings")

    /** 还没配 AI 服务时的引导卡片 */
    @Test fun aiSettingsEmpty() = shot("ai_settings_empty", "ai_settings") {
        runBlocking { container.aiSettings.update { AiSettings() } }
    }

    /** 点「开始配置」后的添加服务弹层：默认选好智谱，下面是获取 Key 的引导 */
    @OptIn(ExperimentalRoborazziApi::class)
    @Test fun aiProviderGuide() {
        runBlocking { container.aiSettings.update { AiSettings() } }
        compose.setContent {
            BojiLaiTheme(fontFamily = font) {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    BojiLaiRoot(container, "ai_settings", sideEffects = false)
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("开始配置").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/ai_provider_guide.png")
    }
    @Test fun profile() = shot("profile", "profile")
    @Test fun operations() = shot("operations", "operations")
    @Test fun addFoodDark() = shot("add_food_dark", "diet_add/${java.time.LocalDate.now().toEpochDay()}", dark = true) {
        container.draftInbox.value = SampleData.drafts()
    }
    @Test fun favorites() = shot("favorites", "favorites")

    /** 个人信息没填全时，摄入卡片下方提示缺什么 */
    @Test fun todayMissing() = shot("today_missing", "today") {
        runBlocking { container.profile.update { it.copy(sex = null, birthYear = null) } }
    }

    /**
     * 对照：半透明图层里凸出边界的内容。左边是默认合成方式（半透明时走离屏画布，凸出部分会被裁掉），
     * 右边是底栏现在用的 ModulateAlpha（不应被裁）。用来确认截图环境能复现这类问题。
     */
    @Test fun alphaClipCheck() {
        compose.setContent {
            BojiLaiTheme(fontFamily = font) {
                Row(Modifier.fillMaxSize().background(Color.White).padding(top = 80.dp, start = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    listOf(CompositingStrategy.Auto, CompositingStrategy.ModulateAlpha).forEach { strategy ->
                        Box(
                            Modifier
                                .size(140.dp, 60.dp)
                                .graphicsLayer {
                                    alpha = 0.6f
                                    compositingStrategy = strategy
                                }
                                .background(Color(0xFF1F6F54)),
                        ) {
                            Box(Modifier.align(Alignment.TopCenter).offset(y = (-30).dp).size(56.dp).background(Color(0xFF7FD6B0), CircleShape))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/alpha_clip_check.png")
    }

    /** 底栏淡入到一半时截几帧，检查「问 AI」按钮有没有被裁 */
    @Test fun barFadeIn() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BojiLaiTheme(fontFamily = font) {
                BojiLaiRoot(container, sideEffects = false)
            }
        }
        repeat(4) { compose.mainClock.advanceTimeByFrame() }
        for (i in 1..4) {
            compose.mainClock.advanceTimeBy(40)
            compose.onRoot().captureRoboImage("screenshots/bar_fade_$i.png")
        }
    }

    /** 从 AI 助手返回今日页的过程中连续截几帧：底栏不能有空档、跳动，「问 AI」按钮要完整 */
    @Test fun returnFromAssistant() {
        compose.setContent {
            BojiLaiTheme(fontFamily = font) {
                BojiLaiRoot(container, "assistant", sideEffects = false)
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("返回").performClick()
        for (i in 1..5) {
            compose.mainClock.advanceTimeBy(50)
            compose.onRoot().captureRoboImage("screenshots/return_$i.png")
        }
    }

    /** 语音输入面板 */
    @Test fun voice() {
        compose.setContent {
            BojiLaiTheme(fontFamily = font) {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.BottomCenter) {
                    Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = Brand.colors.surface) {
                        Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 20.dp)) {
                            VoicePanel(
                                prompt = "说说吃了什么，比如：中午吃了一碗牛肉面",
                                text = "中午吃了一碗牛肉面，加了一个卤蛋",
                                levels = List(40) { i -> (0.15f + 0.6f * kotlin.math.abs(kotlin.math.sin(i * 0.55f))) * (i / 40f) },
                                ready = true,
                                stopping = false,
                                onCancel = {},
                                onFinish = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/voice.png")
    }
}
