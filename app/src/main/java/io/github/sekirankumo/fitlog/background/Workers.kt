package io.github.sekirankumo.fitlog.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.sekirankumo.fitlog.BojiLaiApp
import io.github.sekirankumo.fitlog.data.db.DayStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** 每隔几小时：检查漏打卡并自动调整计划、每日自动备份 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BojiLaiApp).container
        runCatching { container.missedWorkouts.check() }
        runCatching { container.backup.autoBackup() }
        return Result.success()
    }
}

/** 训练日提醒 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BojiLaiApp).container
        val day = container.plans.get(LocalDate.now()) ?: return Result.success()
        if (day.status != DayStatus.PLANNED) return Result.success()
        val count = container.plans.exercisesOf(day).size
        Notifications.show(
            applicationContext,
            1002,
            Notifications.CHANNEL_REMINDER,
            "今天要练：${day.title}",
            "共 $count 个动作，练完记得打卡",
            "training",
        )
        return Result.success()
    }
}

object WorkScheduler {
    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<DailyWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** minuteOfDay 为 null 时取消提醒 */
    fun scheduleReminder(context: Context, minuteOfDay: Int?) {
        val wm = WorkManager.getInstance(context)
        if (minuteOfDay == null) {
            wm.cancelUniqueWork("reminder")
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atStartOfDay().plusMinutes(minuteOfDay.toLong())
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMinutes()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork("reminder", ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }
}
