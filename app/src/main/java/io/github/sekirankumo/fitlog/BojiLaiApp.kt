package io.github.sekirankumo.fitlog

import android.app.Application
import io.github.sekirankumo.fitlog.assistant.AssistantEngine
import io.github.sekirankumo.fitlog.assistant.OperationService
import io.github.sekirankumo.fitlog.assistant.ToolRegistry
import io.github.sekirankumo.fitlog.assistant.tools.AddBodyRecordTool
import io.github.sekirankumo.fitlog.assistant.tools.AddFoodTool
import io.github.sekirankumo.fitlog.assistant.tools.CheckinTool
import io.github.sekirankumo.fitlog.assistant.tools.DeleteBodyRecordTool
import io.github.sekirankumo.fitlog.assistant.tools.DeleteFoodTool
import io.github.sekirankumo.fitlog.assistant.tools.DeletePlanDaysTool
import io.github.sekirankumo.fitlog.assistant.tools.GeneratePlanTool
import io.github.sekirankumo.fitlog.assistant.tools.GetPlanDayTool
import io.github.sekirankumo.fitlog.assistant.tools.GetPlanTool
import io.github.sekirankumo.fitlog.assistant.tools.GetProfileTool
import io.github.sekirankumo.fitlog.assistant.tools.ListFavoritesTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateFavoriteTool
import io.github.sekirankumo.fitlog.assistant.tools.DeleteFavoriteTool
import io.github.sekirankumo.fitlog.assistant.tools.ListOperationsTool
import io.github.sekirankumo.fitlog.assistant.tools.OpenPageTool
import io.github.sekirankumo.fitlog.assistant.tools.QueryBodyRecordsTool
import io.github.sekirankumo.fitlog.assistant.tools.QueryFoodTool
import io.github.sekirankumo.fitlog.assistant.tools.RememberTool
import io.github.sekirankumo.fitlog.assistant.tools.ReplaceExerciseTool
import io.github.sekirankumo.fitlog.assistant.tools.ReplanTool
import io.github.sekirankumo.fitlog.assistant.tools.SearchExercisesTool
import io.github.sekirankumo.fitlog.assistant.tools.SetPlanDaysTool
import io.github.sekirankumo.fitlog.assistant.tools.ShiftPlanTool
import io.github.sekirankumo.fitlog.assistant.tools.UndoOperationTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateBodyRecordTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateFoodTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateProfileTool
import io.github.sekirankumo.fitlog.background.BackupService
import io.github.sekirankumo.fitlog.background.HealthSync
import io.github.sekirankumo.fitlog.background.MissedWorkoutService
import io.github.sekirankumo.fitlog.background.Notifications
import io.github.sekirankumo.fitlog.background.WorkScheduler
import io.github.sekirankumo.fitlog.data.ProfileRepository
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.data.diet.DietRepository
import io.github.sekirankumo.fitlog.data.diet.FoodRecognizer
import io.github.sekirankumo.fitlog.data.exercise.ExerciseRepository
import io.github.sekirankumo.fitlog.data.plan.PlanAi
import io.github.sekirankumo.fitlog.data.plan.PlanRepository
import io.github.sekirankumo.fitlog.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class BojiLaiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        WorkScheduler.scheduleDaily(this)
        container.appScope.launch {
            runCatching { WorkScheduler.scheduleReminder(this@BojiLaiApp, container.profile.current().reminderMinuteOfDay) }
            runCatching { container.missedWorkouts.check() }
            runCatching { container.backup.autoBackup() }
        }
    }
}

/** 手动依赖注入：全局单例都放在这里 */
class AppContainer(app: Application) {
    /** 不随页面关闭而取消的协程，用于保存数据等 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    val database: AppDatabase = AppDatabase.create(app)

    val aiSettings = AiSettingsRepository(app)
    val aiClient = OpenAiClient(httpClient)

    val profile = ProfileRepository(database.kvDao(), database.bodyRecordDao())
    val diet = DietRepository(database.foodDao())
    val foodRecognizer = FoodRecognizer(aiSettings, aiClient)
    val exercises = ExerciseRepository(app)
    val plans = PlanRepository(database.planDao(), database.kvDao(), exercises)
    val planAi = PlanAi(aiSettings, aiClient, plans, exercises, profile, database.bodyRecordDao())

    val backup = BackupService(app, database)
    val healthSync = HealthSync(app, database.bodyRecordDao(), database.kvDao())

    /** 内置离线语音识别 */
    val speech = io.github.sekirankumo.fitlog.voice.SpeechEngine(app, httpClient)

    val updateManager = UpdateManager(app, httpClient).also { um ->
        um.beforeInstall = { backup.autoBackup(force = true) }
    }

    /** 待「记一餐」页面确认的食物（打开页面时取走） */
    val draftInbox = kotlinx.coroutines.flow.MutableStateFlow<List<io.github.sekirankumo.fitlog.data.diet.FoodDraft>>(emptyList())

    /** AI 助手请求跳转页面时发出的路由 */
    val navEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val operations = OperationService(database.opLogDao())

    val tools = ToolRegistry(
        listOf(
            // 身体数据
            QueryBodyRecordsTool(database.bodyRecordDao()),
            AddBodyRecordTool(database.bodyRecordDao()),
            UpdateBodyRecordTool(database.bodyRecordDao()),
            DeleteBodyRecordTool(database.bodyRecordDao()),
            // 饮食
            QueryFoodTool(database.foodDao(), profile),
            AddFoodTool(database.foodDao()),
            UpdateFoodTool(database.foodDao()),
            DeleteFoodTool(database.foodDao()),
            ListFavoritesTool(database.foodDao()),
            UpdateFavoriteTool(database.foodDao()),
            DeleteFavoriteTool(database.foodDao()),
            // 训练
            GetPlanTool(plans),
            GetPlanDayTool(plans),
            SearchExercisesTool(exercises),
            SetPlanDaysTool(plans),
            DeletePlanDaysTool(plans),
            ShiftPlanTool(plans),
            ReplaceExerciseTool(plans, exercises),
            CheckinTool(plans),
            GeneratePlanTool(plans, planAi),
            ReplanTool(plans, planAi),
            // 个人信息
            GetProfileTool(profile, database.bodyRecordDao()),
            UpdateProfileTool(profile),
            RememberTool(profile),
            // 通用
            OpenPageTool(navEvents),
            ListOperationsTool(database.opLogDao()),
            UndoOperationTool(database.opLogDao(), operations),
        )
    ).also { operations.registry = it }

    val missedWorkouts = MissedWorkoutService(app, plans, database.kvDao(), operations, tools)

    val assistant = AssistantEngine(aiSettings, aiClient, tools, operations, database, profile)
}
