package io.github.sekirankumo.fitlog

import android.app.Application
import io.github.sekirankumo.fitlog.assistant.AssistantEngine
import io.github.sekirankumo.fitlog.assistant.OperationService
import io.github.sekirankumo.fitlog.assistant.ToolRegistry
import io.github.sekirankumo.fitlog.assistant.tools.AddBodyRecordTool
import io.github.sekirankumo.fitlog.assistant.tools.DeleteBodyRecordTool
import io.github.sekirankumo.fitlog.assistant.tools.ListOperationsTool
import io.github.sekirankumo.fitlog.assistant.tools.OpenPageTool
import io.github.sekirankumo.fitlog.assistant.tools.QueryBodyRecordsTool
import io.github.sekirankumo.fitlog.assistant.tools.UndoOperationTool
import io.github.sekirankumo.fitlog.assistant.tools.UpdateBodyRecordTool
import io.github.sekirankumo.fitlog.data.ai.AiSettingsRepository
import io.github.sekirankumo.fitlog.data.ai.OpenAiClient
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.update.UpdateManager
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FitLogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 手动依赖注入：全局单例都放在这里 */
class AppContainer(app: Application) {
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    val database: AppDatabase = AppDatabase.create(app)

    val updateManager = UpdateManager(app, httpClient)

    val aiSettings = AiSettingsRepository(app)
    val aiClient = OpenAiClient(httpClient)

    /** AI 助手请求跳转页面时发出的路由 */
    val navEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)

    val operations = OperationService(database.opLogDao())

    val tools = ToolRegistry(
        listOf(
            QueryBodyRecordsTool(database.bodyRecordDao()),
            AddBodyRecordTool(database.bodyRecordDao()),
            UpdateBodyRecordTool(database.bodyRecordDao()),
            DeleteBodyRecordTool(database.bodyRecordDao()),
            OpenPageTool(navEvents),
            ListOperationsTool(database.opLogDao()),
            UndoOperationTool(database.opLogDao(), operations),
        )
    ).also { operations.registry = it }

    val assistant = AssistantEngine(aiSettings, aiClient, tools, operations, database)
}
