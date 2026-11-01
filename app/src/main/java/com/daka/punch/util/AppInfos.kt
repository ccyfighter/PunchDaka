// 目标路径: app/src/main/java/com/daka/punch/util/AppInfos.kt
package com.daka.punch.util

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/**
 * 已安装应用信息（本地读取，仅用于列表展示与勾选）
 *
 * 为什么之前列表不全：
 *  1) Android 11+ 的「软件包可见性」会把 queryIntentActivities / getInstalledApplications 的
 *     结果过滤掉一大半，必须在清单里声明 <queries> 或 QUERY_ALL_PACKAGES 才能看全；
 *  2) 小米在原生之外又加了一层「获取应用列表」权限（com.android.permission.GET_INSTALLED_APPS），
 *     默认只给「仅在使用中允许」，被拒时 getInstalledPackage(s) 基本只能拿到自己；
 *  3) queryIntentActivities 带 MATCH_DEFAULT_ONLY 会额外要求 intent-filter 里声明
 *     CATEGORY_DEFAULT，部分应用（含一些游戏和系统应用）没声明，于是被漏掉；
 *  4) 万一上面几条都还没放开，还有最后一路兜底：系统使用记录（UsageEvents）里出现过的包名
 *     不受「软件包可见性」限制，能把"用过的应用"补回来。
 *
 * 所以这里改成 4 路合并：桌面应用 ∪ 已安装应用 ∪ 使用记录出现过 ∪ 已经勾选过的。
 */
object AppInfos {

    const val SRC_LAUNCHER = 0
    const val SRC_INSTALLED = 1
    const val SRC_USAGE = 2
    const val SRC_PICKED = 3

    /** 从使用记录里补包名时，回看多少天 */
    private const val USAGE_LOOKBACK_DAYS = 7

    /** 使用记录里的系统噪音包，不参与补全 */
    private val NOISE = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.packageinstaller",
        "com.miui.packageinstaller",
        "com.lbe.security.miui"
    )

    data class Entry(
        val packageName: String,
        val label: String,
        val isSystem: Boolean,
        val source: Int = SRC_LAUNCHER
    )

    /** 一次读取的结果，附带各来源数量，便于界面自检"是不是被系统挡住了" */
    data class Snapshot(
        val apps: List<Entry>,
        val launcherCount: Int,
        val installedCount: Int,
        val usageCount: Int,
        val pickedCount: Int
    )

    private val labelCache = HashMap<String, String>()
    private var homeCache: Set<String>? = null

    /** 应用显示名；查不到就退回包名 */
    fun label(context: Context, pkg: String): String {
        labelCache[pkg]?.let { return it }
        val name = try {
            val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            pkg
        }
        val fixed = if (name.isBlank()) pkg else name
        // 只在真正查到名字时缓存，避免"没权限时拿到包名"被永久记住
        if (fixed != pkg) labelCache[pkg] = fixed
        return fixed
    }

    fun isSystemApp(context: Context, pkg: String): Boolean {
        return try {
            val ai = context.packageManager.getApplicationInfo(pkg, 0)
            (ai.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 汇总所有可选应用。[extra] 传"已经勾选过的包名"，
     * 这样即使某个应用在当前条件下读不到，也不会从列表里消失。
     */
    fun allApps(context: Context, extra: Collection<String> = emptyList()): Snapshot {
        val merged = LinkedHashMap<String, Entry>()

        // ① 桌面可启动应用
        val launcher = launcherMap(context)
        launcher.values.forEach { merged[it.packageName] = it }

        // ② 已安装应用（非系统包全收；系统包只收带桌面入口的，避免几百个系统服务刷屏）
        var installedCount = 0
        installedMap(context).values.forEach { entry ->
            if (!merged.containsKey(entry.packageName)) {
                merged[entry.packageName] = entry
                installedCount++
            }
        }

        // ③ 使用记录兜底（这一路不受软件包可见性限制）
        var usageCount = 0
        val home = homePackages(context)
        usagePackages(context, USAGE_LOOKBACK_DAYS).forEach { pkg ->
            if (pkg == context.packageName) return@forEach
            if (home.contains(pkg)) return@forEach
            if (NOISE.contains(pkg)) return@forEach
            if (pkg.startsWith("com.android.providers.")) return@forEach
            if (merged.containsKey(pkg)) return@forEach
            merged[pkg] = Entry(pkg, label(context, pkg), isSystemApp(context, pkg), SRC_USAGE)
            usageCount++
        }

        // ④ 已勾选但当前读不到的，仍然保留在列表里
        var pickedCount = 0
        extra.forEach { pkg ->
            val p = pkg.trim()
            if (p.isEmpty()) return@forEach
            if (merged.containsKey(p)) return@forEach
            merged[p] = Entry(p, label(context, p), isSystemApp(context, p), SRC_PICKED)
            pickedCount++
        }

        val sorted = merged.values.sortedWith(
            compareBy<Entry>({ it.isSystem }, { it.label.lowercase() })
        )
        return Snapshot(
            apps = sorted,
            launcherCount = launcher.size,
            installedCount = installedCount,
            usageCount = usageCount,
            pickedCount = pickedCount
        )
    }

    /** 一次性取回全部可选应用（不区分来源，仅用于兼容旧调用） */
    fun launchableApps(context: Context): List<Entry> = allApps(context).apps

    private fun launcherMap(context: Context): Map<String, Entry> {
        val pm = context.packageManager
        val out = LinkedHashMap<String, Entry>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            // 注意：这里刻意不传 MATCH_DEFAULT_ONLY —— 它要求 intent-filter 里含 CATEGORY_DEFAULT，
            // 不少应用没写，加上就会被误过滤掉
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            if (out.containsKey(pkg)) continue
            val lab = try {
                ri.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            val fixed = if (lab.isBlank()) pkg else lab
            out[pkg] = Entry(pkg, fixed, isSystemApp(context, pkg), SRC_LAUNCHER)
            if (fixed != pkg) labelCache[pkg] = fixed
        }
        return out
    }

    private fun installedMap(context: Context): Map<String, Entry> {
        val pm = context.packageManager
        val out = LinkedHashMap<String, Entry>()
        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList<ApplicationInfo>()
        }
        for (ai in apps) {
            val pkg = ai.packageName ?: continue
            if (pkg == context.packageName) continue
            if (out.containsKey(pkg)) continue
            val isSys = (ai.flags and
                    (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            if (isSys) {
                // 系统应用：只保留能直接从桌面启动的
                val hasLauncher = try {
                    pm.getLaunchIntentForPackage(pkg) != null
                } catch (e: Exception) {
                    false
                }
                if (!hasLauncher) continue
            }
            val lab = try {
                pm.getApplicationLabel(ai).toString()
            } catch (e: Exception) {
                pkg
            }
            val fixed = if (lab.isBlank()) pkg else lab
            out[pkg] = Entry(pkg, fixed, isSys, SRC_INSTALLED)
            if (fixed != pkg) labelCache[pkg] = fixed
        }
        return out
    }

    /** 最近 [days] 天系统使用记录里出现过的包名（不受软件包可见性限制） */
    fun usagePackages(context: Context, days: Int = USAGE_LOOKBACK_DAYS): Set<String> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptySet()
        val end = System.currentTimeMillis()
        val begin = end - days.coerceIn(1, 30).toLong() * 24L * 3600_000L
        val set = LinkedHashSet<String>()
        try {
            val events = usm.queryEvents(begin, end)
            val ev = UsageEvents.Event()
            var guard = 0
            while (events.hasNextEvent() && guard < 200_000) {
                events.getNextEvent(ev)
                guard++
                val pkg = ev.packageName ?: continue
                if (pkg.isNotEmpty()) set.add(pkg)
            }
        } catch (e: Exception) {
            // 无权限或系统异常：安静返回
        }
        return set
    }

    /** 桌面（Launcher）应用的包名集合，用于判断"回到了主屏" */
    fun homePackages(context: Context): Set<String> {
        homeCache?.let { return it }
        val pm = context.packageManager
        val set = HashSet<String>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach {
                it.activityInfo?.packageName?.let { p -> set.add(p) }
            }
        } catch (e: Exception) {
            // ignore
        }
        // 常见厂商桌面兜底
        set += listOf(
            "com.android.launcher", "com.android.launcher2", "com.android.launcher3",
            "com.google.android.apps.nexuslauncher", "com.miui.home",
            "com.huawei.android.launcher", "com.oppo.launcher", "com.coloros.launcher",
            "com.vivo.launcher", "com.bbk.launcher2",
            "com.sec.android.app.launcher", "com.samsung.android.app.launcher",
            "com.honor.android.launcher", "com.oneplus.launcher",
            "com.realme.launcher", "com.meizu.flyme.launcher"
        )
        homeCache = set
        return set
    }

    // ------------------------------------------------------------------
    // 小米「获取应用列表」权限（MIUI 在原生 QUERY_ALL_PACKAGES 之外额外加的一层管控）
    // ------------------------------------------------------------------

    /** 当前系统是否是小米，并且支持动态申请「获取应用列表」 */
    fun miuiListPermissionSupported(context: Context): Boolean {
        return try {
            val info = context.packageManager.getPermissionInfo(MIUI_LIST_PERMISSION, 0)
            info.packageName == "com.lbe.security.miui"
        } catch (e: Exception) {
            false
        }
    }

    const val MIUI_LIST_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

    /** 直接打开小米的"应用权限管理"页（找不到就退回系统应用详情页） */
    fun openPermissionEditor(context: Context) {
        val pairs = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.permissions.PermissionsEditorActivity",
            "com.miui.securitycenter" to "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
        )
        for ((pkg, cls) in pairs) {
            try {
                val i = Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName(pkg, cls)
                    .putExtra("extra_pkgname", context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(i)
                return
            } catch (e: Exception) {
                // 换下一个
            }
        }
        openAppDetails(context)
    }

    fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // ignore
        }
    }
}
