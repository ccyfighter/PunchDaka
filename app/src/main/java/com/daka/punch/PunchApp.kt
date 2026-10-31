// 目标路径: app/src/main/java/com/daka/punch/PunchApp.kt
package com.daka.punch

import android.app.Application
import com.daka.punch.data.Prefs
import com.daka.punch.data.Repository
import com.daka.punch.reminder.Notifier

class PunchApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Repository.init(this)
        Notifier.ensureChannels(this)
    }
}
