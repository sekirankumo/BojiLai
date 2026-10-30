package io.github.sekirankumo.fitlog

import android.app.Application
import io.github.sekirankumo.fitlog.background.Notifications
import io.github.sekirankumo.fitlog.background.WorkScheduler
import io.github.sekirankumo.fitlog.platform.androidPlatformDeps

class BojiLaiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(androidPlatformDeps(this))
        Notifications.createChannels(this)
        WorkScheduler.scheduleDaily(this)
        container.start()
    }
}
