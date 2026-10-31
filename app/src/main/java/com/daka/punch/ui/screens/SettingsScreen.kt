// 目标路径: app/src/main/java/com/daka/punch/ui/screens/SettingsScreen.kt
package com.daka.punch.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.daka.punch.data.Prefs
import com.daka.punch.data.Repository
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }

    // 设置项本地状态（与 Prefs 同步）
    var dayStartHour by remember { mutableIntStateOf(Prefs.dayStartHour) }
    var crashMs by remember { mutableStateOf(Prefs.crashThresholdMs.toFloat()) }
    var graceMs by remember { mutableStateOf(Prefs.interruptGraceMs.toFloat()) }
    var themeMode by remember { mutableIntStateOf(Prefs.themeMode) }
    var dynamicColor by remember { mutableStateOf(Prefs.dynamicColor) }

    LifecycleResumeEffect(Unit) {
        refreshKey++      // 回到前台重新检测权限状态
        dayStartHour = Prefs.dayStartHour
        crashMs = Prefs.crashThresholdMs.toFloat()
        graceMs = Prefs.interruptGraceMs.toFloat()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        // ---------- 权限 ----------
        SectionTitle("运行权限")
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                PermissionRow(
                    title = "无障碍服务（统计必需）",
                    desc = "用于识别前台应用包名，开启后才能统计",
                    ok = isAccessibilityOn(context),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                )
                PermissionRow(
                    title = "通知权限",
                    desc = "提醒需要通知栏权限",
                    ok = notificationEnabled(context),
                    onClick = { openAppNotificationSettings(context) }
                )
                PermissionRow(
                    title = "精确闹钟",
                    desc = "让提醒在设定时刻准时触发",
                    ok = canExactAlarm(context),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 31) {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .setData(Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
                PermissionRow(
                    title = "全屏通知",
                    desc = "到点直接弹出全屏响铃界面（类似闹钟）",
                    ok = canFullScreenIntent(context),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 34) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                    .setData(Uri.parse("package:${context.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                )
                PermissionRow(
                    title = "忽略电池优化",
                    desc = "避免统计服务被系统回收",
                    ok = ignoringBattery(context),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                .setData(Uri.parse("package:${context.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    last = true
                )
            }
        }

        // ---------- 统计规则 ----------
        SectionTitle("统计规则")
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "新的一天从几点开始：${dayStartHour}:00",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = dayStartHour.toFloat(),
                    onValueChange = {
                        dayStartHour = it.roundToInt()
                        Prefs.dayStartHour = dayStartHour
                    },
                    valueRange = 0f..6f,
                    steps = 5
                )
                Text(
                    text = "例如设为 4 点：今天凌晨 4 点之前的记录都算前一天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "疑似闪退阈值：${(crashMs / 1000f).let { String.format("%.1f", it) }} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = crashMs,
                    onValueChange = {
                        crashMs = it
                        Prefs.crashThresholdMs = it.roundToInt().toLong()
                    },
                    valueRange = 500f..5000f,
                    steps = 8
                )
                Text(
                    text = "打开后持续时间短于此值，且直接回到桌面/上一个应用，记为疑似闪退",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "中断容忍：${(graceMs / 1000f).let { String.format("%.1f", it) }} 秒",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = graceMs,
                    onValueChange = {
                        graceMs = it
                        Prefs.interruptGraceMs = it.roundToInt().toLong()
                    },
                    valueRange = 1000f..10000f,
                    steps = 8
                )
                Text(
                    text = "短暂切到通知栏、分享等界面，在此时间内回来算作同一次使用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- 外观 ----------
        SectionTitle("外观")
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "主题",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    listOf("跟随系统", "浅色", "深色").forEachIndexed { index, name ->
                        FilterChip(
                            selected = themeMode == index,
                            onClick = {
                                themeMode = index
                                Prefs.themeMode = index
                                (context as? Activity)?.recreate()
                            },
                            label = { Text(name) },
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "跟随壁纸动态取色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = {
                            dynamicColor = it
                            Prefs.dynamicColor = it
                            (context as? Activity)?.recreate()
                        }
                    )
                }
            }
        }

        // ---------- 数据 ----------
        SectionTitle("数据")
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "全部数据仅保存在本机的私有数据库中，卸载应用即彻底删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("清空所有统计记录")
                }
            }
        }

        // ---------- 关于 ----------
        SectionTitle("关于")
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "打卡 v1.0.0",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "本应用不含任何网络权限（无 INTERNET），不会联网、不会上传任何数据；" +
                            "无障碍服务只读取前台应用包名，不读取窗口内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(90.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text("确认清空？") },
            text = { Text("将删除本机全部使用记录，该操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    Repository.clearSessions()
                    confirmClear = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    ok: Boolean,
    onClick: () -> Unit,
    last: Boolean = false
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (ok) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(
                        imageVector = if (ok) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (ok) MaterialTheme.colorScheme.onTertiaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .padding(8.dp)
                            .height(16.dp)
                            .width(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!last) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

// ---------------- 权限检测 ----------------

private fun isAccessibilityOn(context: Context): Boolean {
    val expected = "${context.packageName}/com.daka.punch.service.PunchAccessibilityService"
    val enabled = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: ""
    val switchOn = Settings.Secure.getInt(
        context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0
    ) == 1
    return switchOn && enabled.split(":").any { it.equals(expected, ignoreCase = true) }
}

private fun notificationEnabled(context: Context): Boolean {
    val nm = context.getSystemService(android.app.NotificationManager::class.java)
    return nm.areNotificationsEnabled()
}

private fun canExactAlarm(context: Context): Boolean {
    val am = context.getSystemService(android.app.AlarmManager::class.java)
    return if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
}

private fun canFullScreenIntent(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 34) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        nm.canUseFullScreenIntent()
    } else true
}

private fun ignoringBattery(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java)
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= 31) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
