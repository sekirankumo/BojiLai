package io.github.sekirankumo.fitlog

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.runtime.CompositionLocalProvider
import io.github.sekirankumo.fitlog.ui.BojiLaiRoot
import io.github.sekirankumo.fitlog.ui.components.LocalReduceMotion
import io.github.sekirankumo.fitlog.ui.components.VoicePanel
import io.github.sekirankumo.fitlog.ui.theme.Brand
import androidx.compose.foundation.background
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

    /** 今日页往下滚动到身体状态 */
    @Test fun todayBody() = shot("today_body", "today", after = {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
    })
    @Test fun diet() = shot("diet", "diet")
    @Test fun addFood() = shot("add_food", "diet_add/${java.time.LocalDate.now().toEpochDay()}") {
        container.draftInbox.value = SampleData.drafts()
    }
    @Test fun training() = shot("training", "training")

    /** 训练页往下滚动到当天的动作和打卡 */
    @Test fun trainingDay() = shot("training_day", "training", after = {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
    })
    @Test fun exercise() = shot("exercise", "exercise/Barbell_Bench_Press_-_Medium_Grip")
    @Test fun library() = shot("library", "exercise_library")
    @Test fun body() = shot("body", "body")
    @Test fun assistant() = shot("assistant", "assistant") {
        container.assistant.showPreview(SampleData.chat())
    }
    @Test fun assistantTable() = shot("assistant_table", "assistant") {
        container.assistant.showPreview(SampleData.chatTable())
    }
    @Test fun settings() = shot("settings", "settings")
    @Test fun favorites() = shot("favorites", "favorites")

    /** 个人信息没填全时，摄入卡片下方提示缺什么 */
    @Test fun todayMissing() = shot("today_missing", "today") {
        runBlocking { container.profile.update { it.copy(sex = null, birthYear = null) } }
    }

    /** 语音输入面板 */
    @Test fun voice() {
        compose.setContent {
            BojiLaiTheme(fontFamily = font) {
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.BottomCenter) {
                    Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = Brand.colors.surface) {
                        Box(Modifier.padding(top = 28.dp)) {
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
