// 目标路径: app/src/main/java/com/daka/punch/util/TimeUtils.kt
package com.daka.punch.util

import java.util.Calendar
import java.util.Locale

/** 打卡日（可设定从几点开始算新的一天）相关的时间工具 */
object TimeUtils {

    private val DAY_NAMES = arrayOf("一", "二", "三", "四", "五", "六", "日")

    /** 某个时刻属于哪个"打卡日"，返回 yyyy-MM-dd */
    fun dayKey(millis: Long, dayStartHour: Int): String {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        if (c.get(Calendar.HOUR_OF_DAY) < dayStartHour) {
            c.add(Calendar.DAY_OF_YEAR, -1)
        }
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** 当前时间所属的打卡日 */
    fun todayKey(dayStartHour: Int): String = dayKey(System.currentTimeMillis(), dayStartHour)

    /** 打卡日往前/往后推若干天 */
    fun shiftDay(dayKey: String, delta: Int): String {
        val p = dayKey.split("-")
        val c = Calendar.getInstance()
        c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 12, 0, 0)
        c.add(Calendar.DAY_OF_YEAR, delta)
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** 该打卡日的起始毫秒（当天 dayStartHour:00:00.000） */
    fun dayStartMs(dayKey: String, dayStartHour: Int): Long {
        val p = dayKey.split("-")
        val c = Calendar.getInstance()
        c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), dayStartHour, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    /** 该打卡日的结束毫秒（次日 dayStartHour 前一毫秒） */
    fun dayEndMs(dayKey: String, dayStartHour: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = dayStartMs(dayKey, dayStartHour)
        c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis - 1
    }

    /** 把 yyyy-MM-dd 显示成 "10月31日 周五" */
    fun prettyDay(dayKey: String): String {
        val p = dayKey.split("-")
        val c = Calendar.getInstance()
        c.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 12, 0, 0)
        val w = when (c.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "一"; Calendar.TUESDAY -> "二"; Calendar.WEDNESDAY -> "三"
            Calendar.THURSDAY -> "四"; Calendar.FRIDAY -> "五"; Calendar.SATURDAY -> "六"
            else -> "日"
        }
        return String.format(Locale.US, "%d月%d日 周%s", p[1].toInt(), p[2].toInt(), w)
    }

    fun formatDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> String.format(Locale.US, "%d小时%02d分", h, m)
            m > 0 -> String.format(Locale.US, "%d分%02d秒", m, sec)
            else -> String.format(Locale.US, "%d秒", sec)
        }
    }

    fun formatDurationShort(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        return when {
            h > 0 -> "${h}h${m}m"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    fun formatClock(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return String.format(
            Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)
        )
    }

    fun formatClockSec(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return String.format(
            Locale.US, "%02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)
        )
    }

    /** daysMask -> "每天" / "周一 周三" / "仅一次" */
    fun repeatText(mask: Int): String {
        if (mask == 0) return "每天"
        val sb = StringBuilder()
        for (i in 0..6) {
            if (mask and (1 shl i) != 0) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append("周").append(DAY_NAMES[i])
            }
        }
        return if (sb.isEmpty()) "仅一次" else sb.toString()
    }

    /** 计算下一次触发时间（严格晚于 now） */
    fun nextTriggerMs(hour: Int, minute: Int, daysMask: Int, now: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = now
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        if (c.timeInMillis <= now) c.add(Calendar.DAY_OF_YEAR, 1)
        if (daysMask == 0) return c.timeInMillis
        repeat(8) {
            val idx = when (c.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                else -> 6
            }
            if (daysMask and (1 shl idx) != 0) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    /** 今天（自然日）的星期索引：0=周一 ... 6=周日 */
    fun todayIndex(millis: Long = System.currentTimeMillis()): Int {
        val c = Calendar.getInstance()
        c.timeInMillis = millis
        return when (c.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 6
        }
    }
}
