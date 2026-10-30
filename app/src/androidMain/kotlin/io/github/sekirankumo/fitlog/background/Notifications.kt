package io.github.sekirankumo.fitlog.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.sekirankumo.fitlog.MainActivity
import io.github.sekirankumo.fitlog.R

object Notifications {
    const val CHANNEL_PLAN = "plan"
    const val CHANNEL_REMINDER = "reminder"
    const val EXTRA_OPEN = "open"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PLAN, "计划调整", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "漏打卡后自动调整训练计划时通知"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDER, "训练提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "训练日按设定时间提醒"
            }
        )
    }

    /** open：打开 App 后跳转的页面路由 */
    fun show(context: Context, id: Int, channel: String, title: String, text: String, open: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN, open)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
