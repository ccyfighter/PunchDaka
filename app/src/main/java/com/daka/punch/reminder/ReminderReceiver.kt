package com.daka.punch.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.daka.punch.data.Prefs
import com.daka.punch.data.Repository

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        Repository.init(context)
        Notifier.ensureChannels(context)

        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, -1L)
        if (id <= 0L) return

        if (intent.action == AlarmScheduler.ACTION_SNOOZE) {
            // 稍后提醒：取消当前通知，10 分钟后由 AlarmScheduler 再次触发
            Notifier.cancel(context, id)
            return
        }

        val reminder = Repository.getReminder(id) ?: return
        if (!reminder.enabled) {
            Notifier.cancel(context, id)
            return
        }

        // 弹出提醒（全屏意图由系统决定是否直接展示响铃界面）
        Notifier.showReminder(context, id, reminder.label)

        // 排下一次（同一 PendingIntent 会覆盖，不会重复）
        AlarmScheduler.schedule(context, reminder)
    }
}

/** 开机 / 应用升级后恢复提醒，并为上次未结算的记录收尾 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        Prefs.init(context)
        Repository.init(context)
        Notifier.ensureChannels(context)

        if (action == Intent.ACTION_BOOT_COMPLETED) {
            Repository.closeAllOpenSessions(System.currentTimeMillis(), "REBOOT")
        }
        AlarmScheduler.scheduleAll(context)
    }
}
