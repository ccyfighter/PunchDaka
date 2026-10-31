// 目标路径: app/src/main/java/com/daka/punch/service/PunchAccessibilityService.kt
package com.daka.punch.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.daka.punch.data.Prefs
import com.daka.punch.data.Repository
import com.daka.punch.util.AppInfos
import com.daka.punch.util.TimeUtils

/**
 * 使用统计采集服务（Android 14+ 唯一可行的第三方方案）
 *
 * 工作原理：
 *  - 监听 TYPE_WINDOW_STATE_CHANGED，拿到"当前前台应用的包名"
 *  - 包名属于你勾选的应用 -> 开一次记录；离开 -> 结算时长
 *  - 离开时间很短（默认 1.5s）且回到桌面/上一个应用 -> 标记为疑似闪退
 *  - 短暂跳到其它界面（通知栏、分享等，默认 3s 内回来）不打断本次记录
 *
 * 隐私：只使用包名，不读取任何窗口内容（canRetrieveWindowContent=false），不联网。
 */
class PunchAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        private const val MONITOR_REFRESH_MS = 10_000L
        private val IGNORE_PREFIX = listOf(
            "android",
            "com.android.systemui",
            "com.android.keyguard",
            "com.android.permissioncontroller",
            "com.android.inputmethod",
            "com.google.android.inputmethod",
            "com.baidu.input",
            "com.iflytek.inputmethod",
            "com.samsung.android.honeyboard",
            "com.miui.voiceassist"
        )
    }

    // 当前正在记录的会话
    private var currentPkg: String? = null
    private var currentId: Long = -1L
    private var currentStart: Long = 0L
    private var prevPkg: String? = null      // 打开当前会话之前的前台应用

    // 暂时离开（跳到非监控/系统界面）
    private var leavePkg: String? = null
    private var leaveAt: Long = 0L

    private var cachedMonitored: Set<String> = emptySet()
    private var lastMonitoredLoad: Long = 0L

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var flushTask: Runnable? = null

    private var lastEventWrite: Long = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> { /* 下一个窗口事件会重新记录 */ }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Prefs.init(this)
        Repository.init(this)

        isRunning = true
        Prefs.serviceConnected = true

        // 服务（重新）启动时，把上一次没来得及结算的记录收尾
        Repository.closeAllOpenSessions(System.currentTimeMillis(), "SERVICE_RESTART")

        cachedMonitored = Repository.monitoredPackages()
        lastMonitoredLoad = System.currentTimeMillis()
        currentPkg = null
        leavePkg = null

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        if (now - lastEventWrite > 5_000L) {
            lastEventWrite = now
            Prefs.lastEventMs = now
        }
        onForegroundChanged(pkg, now)
    }

    private fun monitoredNow(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - lastMonitoredLoad > MONITOR_REFRESH_MS) {
            cachedMonitored = Repository.monitoredPackages()
            lastMonitoredLoad = now
        }
        return cachedMonitored
    }

    private fun onForegroundChanged(pkg: String, now: Long) {
        val monitored = monitoredNow()

        // 1) 回到正在记录的应用：取消"暂时离开"
        if (pkg == currentPkg) {
            cancelLeaveCheck()
            return
        }

        // 2) 跳到另一个被监控的应用：立即结算上一段
        if (monitored.contains(pkg)) {
            if (currentPkg != null) {
                closeCurrent(now, pkg)
            }
            openCurrent(pkg, now)
            cancelLeaveCheck()
            return
        }

        // 3) 跳到非监控界面：记为"暂时离开"，超过容忍时长才真正结算
        if (currentPkg != null) {
            if (leavePkg != pkg) {
                leavePkg = pkg
                leaveAt = now
            }
            scheduleLeaveCheck()
        }
    }

    private fun scheduleLeaveCheck() {
        cancelLeaveCheck()
        val task = Runnable {
            val now = System.currentTimeMillis()
            val grace = Prefs.interruptGraceMs
            if (currentPkg != null && leavePkg != null && now - leaveAt >= grace) {
                closeCurrent(leaveAt, leavePkg!!)
                leavePkg = null
            }
        }
        flushTask = task
        handler.postDelayed(task, Prefs.interruptGraceMs + 100L)
    }

    private fun cancelLeaveCheck() {
        flushTask?.let { handler.removeCallbacks(it) }
        flushTask = null
        leavePkg = null
    }

    private fun openCurrent(pkg: String, now: Long) {
        val day = TimeUtils.dayKey(now, Prefs.dayStartHour)
        currentPkg = pkg
        currentStart = now
        currentId = Repository.openSession(pkg, now, day)
    }

    /** 结算当前会话。endMs 传"真正离开的时刻"，toPkg 为离开后去到的包名 */
    private fun closeCurrent(endMs: Long, toPkg: String?) {
        val pkg = currentPkg ?: return
        val id = currentId
        val duration = (endMs - currentStart).coerceAtLeast(0)

        val isHome = toPkg != null && isHomePackage(toPkg)
        val backToPrev = toPkg != null && toPkg == prevPkg
        val crash = duration < Prefs.crashThresholdMs && (isHome || backToPrev)

        val reason = when {
            toPkg == null -> "SCREEN_OFF"
            isHome -> "HOME"
            backToPrev -> "BACK"
            else -> "SWITCH"
        }

        Repository.closeSession(id, endMs, crash, reason)

        prevPkg = pkg
        currentPkg = null
        currentId = -1L
    }

    private fun onScreenOff() {
        if (currentPkg != null) {
            closeCurrent(System.currentTimeMillis(), null)
        }
        cancelLeaveCheck()
    }

    private fun isHomePackage(pkg: String): Boolean {
        if (IGNORE_PREFIX.any { pkg.startsWith(it) }) return false
        return AppInfos.homePackages(this).contains(pkg)
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onUnbind(intent: Intent?): Boolean {
        flushAndStop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        flushAndStop()
        super.onDestroy()
    }

    private fun flushAndStop() {
        handler.removeCallbacksAndMessages(null)
        if (currentPkg != null) {
            Repository.closeSession(
                currentId, System.currentTimeMillis(), false, "SERVICE_STOP"
            )
            currentPkg = null
            currentId = -1L
        }
        isRunning = false
        Prefs.serviceConnected = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // 可能已注销
        }
    }
}
