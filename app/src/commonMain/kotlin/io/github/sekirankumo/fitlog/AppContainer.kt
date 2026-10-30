package io.github.sekirankumo.fitlog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.sekirankumo.fitlog.assistant.AssistantEngine
import io.github.sekirankumo.fitlog.assistant.OperationService
import io.github.sekirankumo.fitlog.assistant.ToolRegistry
import io.github.sekirankumo.fitlog.assistant.tools.AddBodyRecordTool
import io.github.sekirankumo.fitlog.assistant.tools.AddFavoriteTool
import io.github.sekirankumo.fitlog.assistant.tools.AddFoodTool
import io.github.sekirankumo.fitlog.assistant.tools.CheckinTool
import io.github.sekirankumo.fitlog.assistant.tools.ClearNutritionPlanTool
import io.github.sekirankumo.fitlog.assistant.tools.DeleteBodyRecordTool
import io.github.sekirankumo.fitlog.assistant.tools.DeleteFavoriteTool
import io.github.sekirankumo.fitlog.assistant.tools.DeleteFoodTool
import io.github.sekirankumo.fitlog.assistant.tools.DeletePlanDaysTool
import io.github.sekirankumo.fitlog.assistant.tools.DietQualityTool
import io.github.sekirankumo.fitlog.assistant.tools.GeneratePlanTool
import io.github.sekirankumo.fitlog.assistant.tools.GetNutritionPlanTool
import io.github.sekirankumo.fitlog.assistant.tools.GetPlanDayTool
import io.github.sekirankumo.fitlog.assistant.tools.GetPlanTool
import io.github.sekirankumo.fitlog.assistant.tools.GetProfileTool
import io.github.sekirankumo.fitlog.assistant.tools.ListFavoritesTool
import io.github.sekirankumo.fitlog.assistant.tools.ListOperationsTool
import io.github.sekirankumo.fitlog.assistant.tools.OpenPageTool
import io.github.sekirankumo.fitlog.assistant.tools.QueryBodyRecordsTool
import io.github.sekirankumo.fitlog.assistant.tools.QueryFoodTool
import io.github.sekirankumo.fitlog.assistant.tools.RememberTool
import io.github.sekirankumo.fitlog.assistant.tools.ReplaceExerciseTool
import io.github.sekirankumo.fitlog.assistant.tools.ReplanTool
import io.github.sekirankumo.fitlog.assistant.tools.SearchExercisesTool
import io.github.sekirankumo.fitlog.assistant.tools.SetNutritionPlanTool
import io.github.sekirankumo.fitlog.assistant.tools.SetPlanDaysTool
import io.github.sekirankumo.fitlog.assistant.tools.ShiftPlanTool
import io.github.sekirankumo.fitlog.assistant.tools.UndoOperationTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateBodyRecordTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateFavoriteTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateFoodTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateProfileTool
import io.github.sekirankumo.fitlog.background.BackupService
import io.github.sekirankumo.fitlog.background.HealthSync
import io.github.sekirankumo.fitlog.background.MissedWorkoutService
import io.github.sekirankumo.fitlog.background.NutritionPlanner
import io.github.sekirankumo.fitlog.data.NutritionPlanRepository
import io.github.sekirankumo.fitlog.data.ProfileRepository
import io.github.sekirankumo.fitlog.data.ThemeMode
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.data.db.BodyRecordDao
import io.github.sekirankumo.fitlog.data.db.KeyValueDao
import io.github.sekirankumo.fitlog.data.diet.DietAnalyzer
import io.github.sekirankumo.fitlog.data.diet.DietRepository
import io.github.sekirankumo.fitlog.data.diet.FoodRecognizer
import io.github.sekirankumo.fitlog.data.exercise.ExerciseRepository
import io.github.sekirankumo.fitlog.data.plan.PlanAi
import io.github.sekirankumo.fitlog.data.plan.PlanRepository
import io.github.sekirankumo.fitlog.platform.BackupStore
import io.github.sekirankumo.fitlog.platform.Notifier
import io.github.sekirankumo.fitlog.platform.PlanJobs
import io.github.sekirankumo.fitlog.platform.ReminderScheduler
import io.github.sekirankumo.fitlog.platform.SyncPrefs
import io.github.sekirankumo.fitlog.update.UpdateInstaller
import io.github.sekirankumo.fitlog.update.UpdateManager
import io.github.sekirankumo.fitlog.voice.SpeechEngine
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 手动依赖注入：全局单例都放在这里 */
class AppContainer(platform: PlatformDeps) {
    /** 不随页面关闭而取消的协程，用于保存数据等 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val httpClient: HttpClient = platform.httpClient

    val database: AppDatabase = platform.database

    val aiSettings = AiSettingsRepository(platform.aiSettingsStore)
    val appPrefs = io.github.sekirankumo.fitlog.data.AppPrefs(platform.prefs, platform.onThemeChanged)
    val aiClient = OpenAiClient(httpClient)

    val notifier: Notifier = platform.notifier
    val reminders: ReminderScheduler = platform.reminders
    val planJobs: PlanJobs = platform.planJobs

    val profile = ProfileRepository(database.kvDao(), database.bodyRecordDao())
    val diet = DietRepository(database.foodDao())
    val foodRecognizer = FoodRecognizer(aiSettings, aiClient)
    val dietAnalyzer = DietAnalyzer(aiSettings, aiClient, database.foodDao())
    val exercises = ExerciseRepository()
    val plans = PlanRepository(database.planDao(), database.kvDao(), exercises)
    val planAi = PlanAi(aiSettings, aiClient, plans, exercises, profile, database.bodyRecordDao())

    val backup = BackupService(platform.backupStore, database)
    val healthSync: HealthSync = platform.healthSync(database.bodyRecordDao(), database.kvDao())

    /** 内置离线语音识别 */
    val speech: SpeechEngine = platform.speech

    val updateManager = UpdateManager(httpClient, platform.updateInstaller).also { um ->
        um.beforeInstall = { backup.autoBackup(force = true) }
    }

    /** 待「记一餐」页面确认的食物（打开页面时取走） */
    val draftInbox = kotlinx.coroutines.flow.MutableStateFlow<List<io.github.sekirankumo.fitlog.data.diet.FoodDraft>>(emptyList())

    /** AI 助手请求跳转页面时发出的路由 */
    val navEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val operations = OperationService(database.opLogDao())

    /** 每天的营养安排 */
    val nutritionPlan = NutritionPlanRepository(database.nutritionDao(), database.kvDao(), profile, database.bodyRecordDao())

    val tools = ToolRegistry(
        listOf(
            // 身体数据
            QueryBodyRecordsTool(database.bodyRecordDao()),
            AddBodyRecordTool(database.bodyRecordDao()),
            UpdateBodyRecordTool(database.bodyRecordDao()),
            DeleteBodyRecordTool(database.bodyRecordDao()),
            // 饮食
            QueryFoodTool(database.foodDao(), nutritionPlan),
            DietQualityTool(database.foodDao()),
            AddFoodTool(database.foodDao()),
            UpdateFoodTool(database.foodDao()),
            DeleteFoodTool(database.foodDao()),
            ListFavoritesTool(database.foodDao()),
            AddFavoriteTool(database.foodDao()),
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
            // 每天的营养安排
            GetNutritionPlanTool(nutritionPlan, appPrefs),
            SetNutritionPlanTool(nutritionPlan),
            ClearNutritionPlanTool(nutritionPlan),
            // 通用
            OpenPageTool(navEvents),
            ListOperationsTool(database.opLogDao()),
            UndoOperationTool(database.opLogDao(), operations),
        )
    ).also { operations.registry = it }

    val missedWorkouts = MissedWorkoutService(notifier, plans, database.kvDao(), operations, tools)

    val nutritionPlanner = NutritionPlanner(
        aiSettings, aiClient, nutritionPlan, database, plans, appPrefs, operations, tools,
        beforeCheck = { missedWorkouts.check() },
    )

    val assistant = AssistantEngine(aiSettings, aiClient, tools, operations, database, profile)

    /**
     * 打开 App 时：排好提醒，检查漏打卡、自动备份；
     * 膳食分析：先把还没分析过的记录补上（包括升级前的旧记录），
     * 之后每当有新的待分析记录（手动添加、AI 助手记录等），稍等几秒再补
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        appScope.launch {
            runCatching { reminders.schedule(profile.current().reminderMinuteOfDay) }
            runCatching { missedWorkouts.check() }
            runCatching { backup.autoBackup() }
        }
        appScope.launch {
            val dao = database.foodDao()
            var first = true
            combine(dao.observePendingCount(), dao.observePendingFavoriteCount()) { a, b -> a + b }
                .distinctUntilChanged()
                .debounce(3_000)
                .collect { pending ->
                    if (pending > 0) runCatching { dietAnalyzer.run(force = first) }
                    first = false
                }
        }
    }
}

/** 各平台提供的实现 */
class PlatformDeps(
    val database: AppDatabase,
    val aiSettingsStore: DataStore<Preferences>,
    val prefs: SyncPrefs,
    val httpClient: HttpClient,
    val notifier: Notifier,
    val reminders: ReminderScheduler,
    val planJobs: PlanJobs,
    val backupStore: BackupStore,
    val healthSync: (BodyRecordDao, KeyValueDao) -> HealthSync,
    val speech: SpeechEngine,
    val updateInstaller: UpdateInstaller,
    val onThemeChanged: (ThemeMode) -> Unit = {},
)
