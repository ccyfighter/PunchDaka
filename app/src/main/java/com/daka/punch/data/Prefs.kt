// 目标路径: app/src/main/java/com/daka/punch/data/Prefs.kt
package com.daka.punch.data

import android.content.Context
import android.content.SharedPreferences

/** 本地偏好设置（仅存本机 SharedPreferences，不联网、不备份） */
object Prefs {

    private const val NAME = "punch_prefs"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    // 打卡日切分时刻（小时）：默认凌晨 4 点，即 04:00 之前算前一天
    var dayStartHour: Int
        get() = sp.getInt("day_start_hour", 4)
        set(value) = sp.edit().putInt("day_start_hour", value).apply()

    // 疑似闪退判定阈值：一次打开持续时间小于该值，且随后回到桌面/上一个应用
    var crashThresholdMs: Long
        get() = sp.getLong("crash_threshold_ms", 1500L)
        set(value) = sp.edit().putLong("crash_threshold_ms", value).apply()

    // 中断容忍时长：短暂跳到其它应用/桌面（如分享、通知栏）不超过该值则不打断本次记录
    var interruptGraceMs: Long
        get() = sp.getLong("interrupt_grace_ms", 3000L)
        set(value) = sp.edit().putLong("interrupt_grace_ms", value).apply()

    // 主题：0=跟随系统 1=浅色 2=深色
    var themeMode: Int
        get() = sp.getInt("theme_mode", 0)
        set(value) = sp.edit().putInt("theme_mode", value).apply()

    // 是否使用 Android 12+ 动态取色
    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic_color", true)
        set(value) = sp.edit().putBoolean("dynamic_color", value).apply()

    // 无障碍服务是否已连接（由服务写入，UI 读取显示状态）
    var serviceConnected: Boolean
        get() = sp.getBoolean("service_connected", false)
        set(value) = sp.edit().putBoolean("service_connected", value).apply()

    // 服务最后一次收到事件的时间（用于判断"是否还在工作"）
    var lastEventMs: Long
        get() = sp.getLong("last_event_ms", 0L)
        set(value) = sp.edit().putLong("last_event_ms", value).apply()

    // 响铃时长（秒），超时自动静音
    var ringSeconds: Int
        get() = sp.getInt("ring_seconds", 60)
        set(value) = sp.edit().putInt("ring_seconds", value).apply()

    // ------------------------------------------------------------------
    // 采集方式
    // ------------------------------------------------------------------

    const val MODE_USAGE = 0         // 系统使用记录拉取（推荐：零后台、零耗电）
    const val MODE_ACCESSIBILITY = 1 // 无障碍服务实时监听（备用）

    /** 采集模式，默认使用新的低功耗模式 */
    var collectMode: Int
        get() = sp.getInt("collect_mode", MODE_USAGE)
        set(value) = sp.edit().putInt("collect_mode", value).apply()

    /** 拉取多少天的历史使用记录 */
    var importDays: Int
        get() = sp.getInt("import_days", 3)
        set(value) = sp.edit().putInt("import_days", value).apply()

    /** 上次同步时间戳 */
    var lastSyncMs: Long
        get() = sp.getLong("last_sync_ms", 0L)
        set(value) = sp.edit().putLong("last_sync_ms", value).apply()

    /** 上次同步到的记录条数 */
    var importedCount: Int
        get() = sp.getInt("imported_count", 0)
        set(value) = sp.edit().putInt("imported_count", value).apply()
}
