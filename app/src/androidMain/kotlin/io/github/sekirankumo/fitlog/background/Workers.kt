package io.github.sekirankumo.fitlog.background

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import io.github.sekirankumo.fitlog.BojiLaiApp
import io.github.sekirankumo.fitlog.data.db.DayStatus
import java.time.Duration
import kotlinx.datetime.LocalDate
import io.github.sekirankumo.fitlog.util.now
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

/**
 * 生成训练计划。AI 生成可能要一两分钟，放在后台任务里跑：
 * 切出 App 也会继续，完成或失败都会发通知。
 */
class PlanGenerateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BojiLaiApp).container
        val args = inputData.getString(KEY_ARGS)?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: return Result.failure(workDataOf(KEY_ERROR to "参数错误"))
        val tool = container.tools["generate_training_plan"] ?: return Result.failure(workDataOf(KEY_ERROR to "找不到生成工具"))
        return try {
            val (outcome, _) = container.operations.runWrite(tool, args)
            Notifications.show(applicationContext, 1003, Notifications.CHANNEL_PLAN, "训练计划生成好了", outcome.result, "training")
            Result.success(workDataOf(KEY_RESULT to outcome.result))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            Notifications.show(applicationContext, 1003, Notifications.CHANNEL_PLAN, "训练计划生成失败", msg, "generate_plan")
            Result.failure(workDataOf(KEY_ERROR to msg))
        }
    }

    /** Android 12 以下加急任务以前台服务运行，需要一个通知 */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_PLAN)
            .setSmallIcon(io.github.sekirankumo.fitlog.R.drawable.ic_notification)
            .setContentTitle("正在生成训练计划")
            .setOngoing(true)
            .build()
        return ForegroundInfo(1004, notification)
    }

    companion object {
        const val UNIQUE = "generate_plan"
        const val KEY_ARGS = "args"
        const val KEY_RESULT = "result"
        const val KEY_ERROR = "error"

        fun enqueue(context: Context, args: String): java.util.UUID {
            val request = OneTimeWorkRequestBuilder<PlanGenerateWorker>()
                .setInputData(workDataOf(KEY_ARGS to args))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
            return request.id
        }
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
