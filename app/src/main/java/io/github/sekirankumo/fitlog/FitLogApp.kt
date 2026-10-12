package io.github.sekirankumo.fitlog

import android.app.Application
import io.github.sekirankumo.fitlog.data.db.AppDatabase
import io.github.sekirankumo.fitlog.update.UpdateManager
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
}
