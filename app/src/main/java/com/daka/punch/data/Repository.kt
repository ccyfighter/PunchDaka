// 目标路径: app/src/main/java/com/daka/punch/data/Repository.kt
package com.daka.punch.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.daka.punch.util.TimeUtils
import java.util.Calendar

/** 全部数据读写入口（纯本地 SQLite） */
object Repository {

    private var helper: DbHelper? = null

    @Volatile
    private var dataVersion = 0L

    fun init(context: Context) {
        if (helper == null) helper = DbHelper(context)
    }

    /** 数据版本号，UI 用它判断是否需要刷新 */
    fun version(): Long = dataVersion

    fun touch() {
        dataVersion++
    }

    private val db: SQLiteDatabase
        get() = helper!!.writableDatabase

    // ------------------------------------------------------------------
    // 被监控应用
    // ------------------------------------------------------------------

    fun monitoredApps(): List<MonitoredApp> {
        val list = ArrayList<MonitoredApp>()
        db.query("monitored", null, null, null, null, null, "label ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    MonitoredApp(
                        packageName = c.getString(c.getColumnIndexOrThrow("pkg")),
                        label = c.getString(c.getColumnIndexOrThrow("label")),
                        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1
                    )
                )
            }
        }
        return list
    }

    fun monitoredPackages(): Set<String> {
        val set = HashSet<String>()
        db.query(
            "monitored", arrayOf("pkg"), "enabled=1", null, null, null, null
        ).use { c ->
            while (c.moveToNext()) set.add(c.getString(0))
        }
        return set
    }

    fun setMonitored(pkg: String, label: String, enabled: Boolean = true) {
        val cv = ContentValues().apply {
            put("pkg", pkg)
            put("label", label)
            put("enabled", if (enabled) 1 else 0)
        }
        db.insertWithOnConflict("monitored", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        touch()
    }

    fun setMonitoredEnabled(pkg: String, enabled: Boolean) {
        val cv = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        db.update("monitored", cv, "pkg=?", arrayOf(pkg))
        touch()
    }

    fun removeMonitored(pkg: String) {
        db.delete("monitored", "pkg=?", arrayOf(pkg))
        db.delete("sessions", "pkg=?", arrayOf(pkg))
        touch()
    }

    // ------------------------------------------------------------------
    // 使用记录
    // ------------------------------------------------------------------

    fun openSession(pkg: String, startMs: Long, day: String): Long {
        val cv = ContentValues().apply {
            put("pkg", pkg)
            put("start_ms", startMs)
            put("end_ms", -1)
            put("duration_ms", 0)
            put("day", day)
            put("crash", 0)
            put("reason", "")
            put("source", 0)
        }
        val id = db.insert("sessions", null, cv)
        touch()
        return id
    }

    fun closeSession(id: Long, endMs: Long, crash: Boolean, reason: String) {
        val cv = ContentValues().apply {
            put("end_ms", endMs)
            put("duration_ms", endMs - startOf(id))
            put("crash", if (crash) 1 else 0)
            put("reason", reason)
        }
        db.update("sessions", cv, "id=? AND end_ms=-1", arrayOf(id.toString()))
        touch()
    }

    private fun startOf(id: Long): Long {
        db.query("sessions", arrayOf("start_ms"), "id=?", arrayOf(id.toString()), null, null, null)
            .use { c ->
                return if (c.moveToFirst()) c.getLong(0) else 0L
            }
    }

    /**
     * 用系统使用记录覆盖 [fromMs, toMs] 区间的数据。
     * 只删除 source=1 的旧记录后重插，因此是幂等的，重复调用不会产生重复数据；
     * 无障碍模式产生的记录（source=0）不会被影响。
     */
    fun replaceImported(fromMs: Long, toMs: Long, rows: List<ImportRow>) {
        val wdb = db
        wdb.beginTransaction()
        try {
            wdb.delete(
                "sessions",
                "source=1 AND start_ms>=? AND start_ms<=?",
                arrayOf(fromMs.toString(), toMs.toString())
            )
            for (r in rows) {
                if (r.endMs <= r.startMs) continue
                val cv = ContentValues().apply {
                    put("pkg", r.pkg)
                    put("start_ms", r.startMs)
                    put("end_ms", r.endMs)
                    put("duration_ms", r.endMs - r.startMs)
                    put("day", TimeUtils.dayKey(r.startMs, Prefs.dayStartHour))
                    put("crash", if (r.crash) 1 else 0)
                    put("reason", r.reason)
                    put("source", 1)
                }
                wdb.insert("sessions", null, cv)
            }
            wdb.setTransactionSuccessful()
        } finally {
            wdb.endTransaction()
        }
        touch()
    }

    /** 服务重启 / 开机时，把"仍在进行中"的记录按当前时间收尾 */
    fun closeAllOpenSessions(nowMs: Long, reason: String) {
        // 单次时长上限 6 小时，避免服务被杀后残留记录产生夸张时长
        db.execSQL(
            "UPDATE sessions SET end_ms=?, duration_ms=MIN(?-start_ms, 21600000), reason=? WHERE end_ms=-1",
            arrayOf(nowMs.toString(), nowMs.toString(), reason)
        )
        touch()
    }

    fun lastOpenSession(): UsageSession? {
        db.query(
            "sessions", null, "end_ms=-1", null, null, null, "start_ms DESC", "1"
        ).use { c ->
            return if (c.moveToFirst()) fromCursor(c) else null
        }
    }

    fun sessionsOfDay(day: String): List<UsageSession> {
        val list = ArrayList<UsageSession>()
        db.query("sessions", null, "day=? AND end_ms<>-1", arrayOf(day), null, null, "start_ms ASC")
            .use { c ->
                while (c.moveToNext()) list.add(fromCursor(c))
            }
        return list
    }

    fun sessionsOfDayForApp(day: String, pkg: String): List<UsageSession> {
        val list = ArrayList<UsageSession>()
        db.query(
            "sessions", null, "day=? AND pkg=? AND end_ms<>-1",
            arrayOf(day, pkg), null, null, "start_ms ASC"
        ).use { c ->
            while (c.moveToNext()) list.add(fromCursor(c))
        }
        return list
    }

    fun dayStats(day: String): List<DayStat> {
        val map = LinkedHashMap<String, DayStat>()
        db.rawQuery(
            "SELECT pkg, COUNT(*), SUM(duration_ms), SUM(crash), MAX(duration_ms) " +
                    "FROM sessions WHERE day=? AND end_ms<>-1 GROUP BY pkg",
            arrayOf(day)
        ).use { c ->
            while (c.moveToNext()) {
                map[c.getString(0)] = DayStat(
                    packageName = c.getString(0),
                    count = c.getInt(1),
                    totalMs = c.getLong(2),
                    crashCount = c.getInt(3),
                    maxMs = c.getLong(4)
                )
            }
        }
        return map.values.sortedByDescending { it.totalMs }
    }

    fun daySummary(day: String): DaySummary {
        var count = 0
        var total = 0L
        var crash = 0
        db.rawQuery(
            "SELECT COUNT(*), SUM(duration_ms), SUM(crash) FROM sessions WHERE day=? AND end_ms<>-1",
            arrayOf(day)
        ).use { c ->
            if (c.moveToFirst()) {
                count = c.getInt(0)
                total = c.getLong(1)
                crash = c.getInt(2)
            }
        }
        return DaySummary(count, total, crash, dayStats(day).size)
    }

    /**
     * 该打卡日内每小时的使用总时长（毫秒数组，长度 24，下标 = 0~23 点）。
     * 跨越整点的一次使用会被拆分到对应的每个小时，避免整段时长都被算在开始那个小时里。
     */
    fun hourlyOfDay(day: String): LongArray {
        val arr = LongArray(24)
        val limit = TimeUtils.dayEndMs(day, Prefs.dayStartHour)
        db.query(
            "sessions", arrayOf("start_ms", "duration_ms"),
            "day=? AND end_ms<>-1", arrayOf(day), null, null, null
        ).use { c ->
            val cal = Calendar.getInstance()
            while (c.moveToNext()) {
                var cur = c.getLong(0)
                var remain = c.getLong(1)
                while (remain > 0L && cur <= limit) {
                    cal.timeInMillis = cur
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.add(Calendar.HOUR_OF_DAY, 1)
                    val boundary = cal.timeInMillis
                    val take = if (remain > boundary - cur) boundary - cur else remain
                    arr[hour] += take
                    remain -= take
                    cur += take
                }
            }
        }
        return arr
    }

    /** 有记录的日期列表（倒序），用于日历/历史切换 */
    fun daysWithData(limit: Int = 60): List<String> {
        val list = ArrayList<String>()
        db.query(
            true, "sessions", arrayOf("day"), "end_ms<>-1",
            null, null, null, "day DESC", limit.toString()
        ).use { c ->
            while (c.moveToNext()) list.add(c.getString(0))
        }
        return list
    }

    fun clearSessions() {
        db.delete("sessions", null, null)
        touch()
    }

    fun deleteSession(id: Long) {
        db.delete("sessions", "id=?", arrayOf(id.toString()))
        touch()
    }

    private fun fromCursor(c: Cursor): UsageSession = UsageSession(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        packageName = c.getString(c.getColumnIndexOrThrow("pkg")),
        startMs = c.getLong(c.getColumnIndexOrThrow("start_ms")),
        endMs = c.getLong(c.getColumnIndexOrThrow("end_ms")),
        durationMs = c.getLong(c.getColumnIndexOrThrow("duration_ms")),
        dayKey = c.getString(c.getColumnIndexOrThrow("day")),
        suspiciousCrash = c.getInt(c.getColumnIndexOrThrow("crash")) == 1,
        endReason = c.getString(c.getColumnIndexOrThrow("reason"))
    )

    // ------------------------------------------------------------------
    // 提醒
    // ------------------------------------------------------------------

    fun reminders(): List<Reminder> {
        val list = ArrayList<Reminder>()
        db.query("reminders", null, null, null, null, null, "hour ASC, minute ASC").use { c ->
            while (c.moveToNext()) {
                list.add(
                    Reminder(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        hour = c.getInt(c.getColumnIndexOrThrow("hour")),
                        minute = c.getInt(c.getColumnIndexOrThrow("minute")),
                        label = c.getString(c.getColumnIndexOrThrow("label")),
                        enabled = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1,
                        daysMask = c.getInt(c.getColumnIndexOrThrow("days"))
                    )
                )
            }
        }
        return list
    }

    fun getReminder(id: Long): Reminder? = reminders().firstOrNull { it.id == id }

    fun addReminder(r: Reminder): Long {
        val cv = ContentValues().apply {
            put("hour", r.hour)
            put("minute", r.minute)
            put("label", r.label)
            put("enabled", if (r.enabled) 1 else 0)
            put("days", r.daysMask)
        }
        val id = db.insert("reminders", null, cv)
        touch()
        return id
    }

    fun updateReminder(r: Reminder) {
        val cv = ContentValues().apply {
            put("hour", r.hour)
            put("minute", r.minute)
            put("label", r.label)
            put("enabled", if (r.enabled) 1 else 0)
            put("days", r.daysMask)
        }
        db.update("reminders", cv, "id=?", arrayOf(r.id.toString()))
        touch()
    }

    fun setReminderEnabled(id: Long, enabled: Boolean) {
        val cv = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        db.update("reminders", cv, "id=?", arrayOf(id.toString()))
        touch()
    }

    fun deleteReminder(id: Long) {
        db.delete("reminders", "id=?", arrayOf(id.toString()))
        touch()
    }
}
