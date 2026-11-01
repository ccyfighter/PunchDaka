// 目标路径: app/src/main/java/com/daka/punch/data/Models.kt
package com.daka.punch.data

/** 被监控的应用 */
data class MonitoredApp(
    val packageName: String,
    val label: String,
    val enabled: Boolean
)

/** 一次"打开到离开"的记录 */
data class UsageSession(
    val id: Long = 0,
    val packageName: String = "",
    val startMs: Long = 0,
    val endMs: Long = -1,          // -1 表示仍在进行中
    val durationMs: Long = 0,
    val dayKey: String = "",
    val suspiciousCrash: Boolean = false,
    val endReason: String = ""     // SWITCH / HOME / SCREEN_OFF / SERVICE_RESTART / REBOOT
)

/** 某应用在某打卡日的汇总 */
data class DayStat(
    val packageName: String,
    val count: Int,
    val totalMs: Long,
    val crashCount: Int,
    val maxMs: Long
)

/** 全天汇总 */
data class DaySummary(
    val totalCount: Int,
    val totalMs: Long,
    val crashCount: Int,
    val appCount: Int
)

/** 从系统使用记录导入的一行原始数据 */
data class ImportRow(
    val pkg: String,
    val startMs: Long,
    val endMs: Long,
    val crash: Boolean,
    val reason: String
)

/** 定时提醒 */
data class Reminder(
    val id: Long = 0,
    val hour: Int = 8,
    val minute: Int = 0,
    val label: String = "打卡提醒",
    val enabled: Boolean = true,
    val daysMask: Int = 0,         // 0=每天；否则 bit0=周一 ... bit6=周日
    val snoozeUntilMs: Long = 0
)
