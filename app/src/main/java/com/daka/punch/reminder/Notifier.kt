// 目标路径: app/src/main/java/com/daka/punch/reminder/Notifier.kt
package com.daka.punch.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.daka.punch.R
import com.daka.punch.util.TimeUtils

/** 通知渠道与提醒通知（纯本地，无任何网络行为） */
object Notifier {

    const val CH_REMINDER = "punch_reminder"
    const val CH_STATUS = "punch_status"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val reminder = NotificationChannel(
            CH_REMINDER, "打卡提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "定点打卡提醒（可在锁屏上全屏显示）"
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }

        val status = NotificationChannel(
            CH_STATUS, "运行状态", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "统计服务运行状态提示"
        }

        nm.createNotificationChannel(reminder)
        nm.createNotificationChannel(status)
    }

    fun showReminder(context: Context, id: Long, label: String) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val fullScreenIntent = Intent(context, RingingActivity::class.java).apply {
            putExtra(AlarmScheduler.EXTRA_ID, id)
            putExtra("label", label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context,
            id.toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ID, id)
        }
        val snoozePi = PendingIntent.getBroadcast(
            context,
            (id.toInt() xor 0x5f5f),
            snoozeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val now = TimeUtils.formatClock(System.currentTimeMillis())

        val notification = Notification.Builder(context, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_punch)
            .setContentTitle(label.ifBlank { "打卡提醒" })
            .setContentText("现在 $now · 向右滑动关闭")
            .setSubText("打卡")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPi, true)
            .setContentIntent(fullScreenPi)
            .setPriority(Notification.PRIORITY_MAX)
            .addAction(
                Notification.Action.Builder(
                    null, "稍后 10 分钟", snoozePi
                ).build()
            )
            .build()

        nm.notify(id.toInt(), notification)
    }

    fun cancel(context: Context, id: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(id.toInt())
    }

    /** 提示统计服务未开启（低优先级，不打扰） */
    fun showServiceHint(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val intent = Intent(context, com.daka.punch.ui.MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            context, 9999, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_punch)
            .setContentTitle("统计服务未运行")
            .setContentText("点击打开打卡，确认无障碍服务已开启")
            .setContentIntent(pi)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        nm.notify(9999, n)
    }
}
