// 目标路径: app/src/main/java/com/daka/punch/util/AppInfos.kt
package com.daka.punch.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** 已安装应用信息（本地读取，仅用于列表展示与勾选） */
object AppInfos {

    data class Entry(
        val packageName: String,
        val label: String,
        val isSystem: Boolean
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
        labelCache[pkg] = name
        return name
    }

    /** 桌面上可启动的应用列表（按名称排序） */
    fun launchableApps(context: Context): List<Entry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (e: Exception) {
            emptyList()
        }
        val self = context.packageName
        val map = LinkedHashMap<String, Entry>()
        for (ri in resolved) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == self) continue
            if (map.containsKey(pkg)) continue
            val label = try {
                ri.loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            val isSystem = try {
                val ai = pm.getApplicationInfo(pkg, 0)
                (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (e: Exception) {
                false
            }
            map[pkg] = Entry(pkg, label.ifBlank { pkg }, isSystem)
            labelCache[pkg] = label
        }
        return map.values.sortedBy { it.label.lowercase() }
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
}
