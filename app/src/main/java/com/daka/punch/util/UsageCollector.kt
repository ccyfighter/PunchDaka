// 目标路径: app/src/main/java/com/daka/punch/util/UsageCollector.kt
package com.daka.punch.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import com.daka.punch.data.ImportRow
import com.daka.punch.data.Prefs
import com.daka.punch.data.Repository

/**
 * 【低功耗采集引擎】—— 不再依赖后台常驻的无障碍服务
 *
 * 原理：Android 系统自己就在记录每个应用的 ACTIVITY_RESUMED / ACTIVITY_PAUSED
 * 事件（这正是"数字健康/屏幕使用时间"用的那份日志）。我们只需要在打开本 App 时，
 * 向系统查询一次这段时间内的事件，把它们换算成"打开次数 + 每次打开时长"写进本地库。
 *
 * 好处：
 *  1. 不需要无障碍服务（可以完全关掉）；
 *  2. 本 App 在后台可以完全不运行 —— 不轮询、不保活、不唤醒 CPU，几乎零耗电；
 *  3. 哪怕中途被小米杀掉进程，系统日志照样在记，下次打开一次性补齐，不会漏记。
 *
 * 隐私：只读系统使用事件的时间戳与包名，不读取任何内容、不联网。
 */
object UsageSource {

    /** 相邻两次前台停留间隔小于该值视为同一次使用（旋转屏幕、短暂切后台再回来） */
    private const val MERGE_GAP_MS = 5_000L

    /** 判断"疑似闪退"时，离开后多久内跳到桌面才算 */
    private const val CRASH_LOOKAHEAD_MS = 2_000L

    /** 单次记录的最大时长上限（超过则截断，避免异常数据） */
    private const val MAX_SESSION_MS = 6 * 3600_000L

    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (ignored: Exception) {
            }
        }
    }

    /** 是否已获得"使用情况访问"授权 */
    fun appOpsAllowed(context: Context): Boolean {
        return try {
            val aom = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = aom.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    data class Probe(
        val allowed: Boolean,
        val queried: Boolean,
        val eventCount: Int,
        val message: String
    )

    /** 自检：能不能真的查到数据（小米部分机型 AppOps 判断不准，所以要实查一次） */
    fun probe(context: Context): Probe {
        val allowed = appOpsAllowed(context)
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return Probe(allowed, false, 0, "系统未提供使用记录服务")
        return try {
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 6 * 3600_000L, now)
            val e = UsageEvents.Event()
            var n = 0
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                n++
            }
            Probe(allowed, true, n, "")
        } catch (se: SecurityException) {
            Probe(allowed, false, 0, "未获得使用情况访问权限")
        } catch (t: Throwable) {
            Probe(allowed, false, 0, t.message ?: "未知错误")
        }
    }

    /**
     * 读取 [fromMs, toMs] 内被监控应用的一次次前台停留。
     * 返回按开始时间升序的记录列表（已完成去重与合并）。
     */
    fun collect(
        context: Context,
        fromMs: Long,
        toMs: Long,
        monitored: Set<String>,
        homePkgs: Set<String>,
        crashThresholdMs: Long
    ): List<ImportRow> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        val events = try {
            usm.queryEvents(fromMs, toMs)
        } catch (e: Exception) {
            return emptyList()
        }

        val opened = HashMap<String, Long>()
        val raw = ArrayList<ImportRow>()
        val ev = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName?.toString()
            if (pkg.isNullOrEmpty()) continue
            val t = ev.timeStamp
            if (t <= 0L) continue

            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (!monitored.contains(pkg)) continue
                    val prev = opened[pkg]
                    if (prev != null && t > prev) {
                        raw.add(ImportRow(pkg, prev, t, false, "SWITCH"))
                    }
                    opened[pkg] = t
                }

                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val start = opened.remove(pkg)
                    if (start != null && t > start) {
                        raw.add(ImportRow(pkg, start, t, false, "PAUSED"))
                    }
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    val it = opened.entries.iterator()
                    while (it.hasNext()) {
                        val entry = it.next()
                        if (t > entry.value) {
                            raw.add(ImportRow(entry.key, entry.value, t, false, "SCREEN_OFF"))
                        }
                        it.remove()
                    }
                }
            }
        }

        // 直到查询终点仍在前台的（比如正在用），按 toMs 收尾
        val tail = opened.entries.iterator()
        while (tail.hasNext()) {
            val entry = tail.next()
            val end = entry.value + MAX_SESSION_MS
            raw.add(
                ImportRow(
                    entry.key, entry.value,
                    if (end > toMs) toMs else end,
                    false, "ONGOING"
                )
            )
        }

        return postProcess(raw, homePkgs, crashThresholdMs)
    }

    private fun postProcess(
        raw: List<ImportRow>,
        homePkgs: Set<String>,
        crashThresholdMs: Long
    ): List<ImportRow> {
        if (raw.isEmpty()) return emptyList()

        // 1) 排序
        val sorted = raw.sortedWith(compareBy<ImportRow> { it.pkg }.thenBy { it.startMs })

        // 2) 同一应用相邻记录间隔很短 -> 视为同一次使用
        val merged = ArrayList<ImportRow>()
        for (s in sorted) {
            val last = merged.lastOrNull()
            if (last != null && last.pkg == s.pkg && s.startMs - last.endMs <= MERGE_GAP_MS) {
                merged[merged.lastIndex] = last.copy(
                    endMs = if (s.endMs > last.endMs) s.endMs else last.endMs
                )
            } else {
                merged.add(s)
            }
        }

        // 3) 判断"疑似闪退"：时长低于阈值，且紧接着跳到了桌面
        val byStart = merged.sortedBy { it.startMs }
        val out = ArrayList<ImportRow>(merged.size)
        for (i in byStart.indices) {
            val s = byStart[i]
            val duration = (s.endMs - s.startMs).coerceAtLeast(0L)
            val next = byStart.getOrNull(i + 1)
            val jumpedToHome = next != null &&
                next.startMs - s.endMs <= CRASH_LOOKAHEAD_MS &&
                homePkgs.contains(next.pkg)
            out.add(
                s.copy(
                    crash = duration < crashThresholdMs && jumpedToHome,
                    reason = if (jumpedToHome) "HOME" else s.reason
                )
            )
        }
        return out
    }
}

/** 同步调度：负责调用 UsageSource 拉取并把结果写进数据库 */
object UsageCollector {

    /** 两次同步的最小间隔，避免频繁查询 */
    private const val MIN_INTERVAL_MS = 20_000L

    @Volatile
    private var lastRun: Long = 0L

    @Volatile
    var lastMessage: String = ""
        private set

    fun usingUsageMode(): Boolean = Prefs.collectMode == Prefs.MODE_USAGE

    /** 带节流的同步，可在每次刷新界面时安全调用 */
    fun syncIfNeeded(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastRun < MIN_INTERVAL_MS) return
        lastRun = now
        syncNow(context)
    }

    /** 立即同步一次（忽略节流） */
    fun syncNow(context: Context): Int {
        if (!UsageSource.appOpsAllowed(context)) {
            lastMessage = "未获得使用情况访问权限"
            return 0
        }
        val monitored = Repository.monitoredPackages()
        if (monitored.isEmpty()) {
            lastMessage = "还没有选择要统计的应用"
            return 0
        }

        val startHour = Prefs.dayStartHour
        val days = Prefs.importDays
        val today = TimeUtils.todayKey(startHour)
        val from = TimeUtils.dayStartMs(TimeUtils.shiftDay(today, -(days - 1)), startHour)
        val to = System.currentTimeMillis()
        if (to <= from) {
            lastMessage = "时间区间异常"
            return 0
        }

        val home = AppInfos.homePackages(context)
        val rows = UsageSource.collect(
            context = context,
            fromMs = from,
            toMs = to,
            monitored = monitored,
            homePkgs = home,
            crashThresholdMs = Prefs.crashThresholdMs
        )

        Repository.replaceImported(from, to, rows)
        Prefs.lastSyncMs = to
        Prefs.importedCount = rows.size
        lastMessage = if (rows.isEmpty()) "未查到记录" else "已同步 ${rows.size} 条"
        return rows.size
    }
}
