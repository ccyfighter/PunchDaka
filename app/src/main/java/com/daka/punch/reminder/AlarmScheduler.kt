// 目标路径: app/src/main/java/com/daka/punch/reminder/AlarmScheduler.kt
package com.daka.punch.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.daka.punch.data.Reminder
import com.daka.punch.data.Repository
import com.daka.punch.util.TimeUtils

/** 定点提醒的排程（精确闹钟；无法精确时自动降级为窗口闹钟） */
object AlarmScheduler {

    const val EXTRA_ID = "reminder_id"
    const val ACTION_SNOOZE = "com.daka.punch.ACTION_SNOOZE"

    private fun pi(context: Context, id: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        return PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** 排下一次触发；enabled=false 时取消 */
    fun schedule(context: Context, r: Reminder) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pending = pi(context, r.id)
        if (!r.enabled) {
            am.cancel(pending)
            return
        }
        val trigger = TimeUtils.nextTriggerMs(
            r.hour, r.minute, r.daysMask, System.currentTimeMillis()
        )
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(trigger, pending), pending)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, pending)
            }
        } catch (e: SecurityException) {
            // 没有精确闹钟权限时降级
            am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 60_000L, pending)
        }
    }

    fun cancel(context: Context, id: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pi(context, id))
    }

    /** 开机 / 升级后恢复全部提醒 */
    fun scheduleAll(context: Context) {
        Repository.init(context)
        Repository.reminders().forEach { schedule(context, it) }
    }

    /** 稍后提醒：delayMs 分钟后再次响铃（不改变原提醒） */
    fun scheduleSnooze(context: Context, id: Long, delayMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_SNOOZE)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val pending = PendingIntent.getBroadcast(
            context,
            (id.toInt() xor 0x5f5f),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val trigger = System.currentTimeMillis() + delayMs
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 30_000L, pending)
            }
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, trigger, 30_000L, pending)
        }
    }
}
