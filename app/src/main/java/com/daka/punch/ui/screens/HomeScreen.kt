// 目标路径: app/src/main/java/com/daka/punch/ui/screens/HomeScreen.kt
package com.daka.punch.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.daka.punch.data.DayStat
import com.daka.punch.data.DaySummary
import com.daka.punch.data.Prefs
import com.daka.punch.data.Repository
import com.daka.punch.data.UsageSession
import com.daka.punch.util.AppInfos
import com.daka.punch.util.TimeUtils
import com.daka.punch.util.UsageCollector
import com.daka.punch.util.UsageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val AVATAR_COLORS = listOf(
    Color(0xFF6D5EFF), Color(0xFF0EA5E9), Color(0xFF22C55E),
    Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF14B8A6)
)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var day by remember { mutableStateOf(TimeUtils.todayKey(Prefs.dayStartHour)) }
    var stats by remember { mutableStateOf(emptyList<DayStat>()) }
    var summary by remember { mutableStateOf(DaySummary(0, 0, 0, 0)) }
    var hourly by remember { mutableStateOf(LongArray(24)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    val details = remember { mutableStateListOf<UsageSession>() }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(day, refreshKey, tick) {
        val d = day
        val result = withContext(Dispatchers.IO) {
            // 先从系统同步一次使用记录（内部有 20 秒节流，不会频繁查询）
            if (UsageCollector.usingUsageMode()) UsageCollector.syncIfNeeded(context)
            Triple(
                Repository.dayStats(d),
                Repository.daySummary(d),
                Repository.hourlyOfDay(d)
            )
        }
        stats = result.first
        summary = result.second
        hourly = result.third
    }

    // 界面停留期间每 15 秒自动刷新，保证看到的数字一直是最新的
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            tick++
        }
    }

    // 每次回到前台刷新（统计服务在后台持续写入）
    LifecycleResumeEffect(day) {
        refreshKey++
        onPauseOrDispose { }
    }

    LaunchedEffect(expandedPkg, refreshKey) {
        val pkg = expandedPkg
        if (pkg == null) {
            details.clear()
            return@LaunchedEffect
        }
        val list = withContext(Dispatchers.IO) {
            Repository.sessionsOfDayForApp(day, pkg)
        }
        details.clear()
        details.addAll(list)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DaySwitcher(
                day = day,
                onPrev = { day = TimeUtils.shiftDay(day, -1) },
                onNext = { day = TimeUtils.shiftDay(day, 1) },
                onToday = { day = TimeUtils.todayKey(Prefs.dayStartHour) }
            )
        }

        item { CollectSourceCard() }

        item { SummaryCard(summary) }

        item { HourlyCard(hourly) }

        if (stats.isEmpty()) {
            item { EmptyCard() }
        } else {
            item {
                Text(
                    text = "应用明细",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )
            }
            items(stats, key = { it.packageName }) { stat ->
                AppStatCard(
                    stat = stat,
                    label = AppInfos.label(context, stat.packageName),
                    expanded = expandedPkg == stat.packageName,
                    details = if (expandedPkg == stat.packageName) details else emptyList(),
                    onToggle = {
                        expandedPkg = if (expandedPkg == stat.packageName) null else stat.packageName
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(76.dp)) }
    }
}

@Composable
private fun DaySwitcher(
    day: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "前一天")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = TimeUtils.prettyDay(day),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "打卡日 $day（${Prefs.dayStartHour}:00 起算）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "后一天")
        }
        Text(
            text = "今天",
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onToday() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ServiceWarning() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "统计服务未运行",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "请在「设置」中开启无障碍服务，否则采集不到数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: DaySummary) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF6D5EFF), Color(0xFF4F46E5), Color(0xFF0EA5E9))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "今日打卡",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = TimeUtils.formatDuration(summary.totalMs),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 34.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell("打开次数", "${summary.totalCount}", Modifier.weight(1f))
                    StatCell("应用数", "${summary.appCount}", Modifier.weight(1f))
                    StatCell("疑似闪退", "${summary.crashCount}", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatCell(title: String, value: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun HourlyCard(hourly: LongArray) {
    val primary = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.surfaceVariant
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "使用分布",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
            ) {
                val max = hourly.maxOrNull()?.coerceAtLeast(60_000L) ?: 60_000L
                val w = size.width / 24f
                for (i in 0..23) {
                    val v = hourly[i].toFloat() / max.toFloat()
                    val h = (size.height * v).coerceAtLeast(
                        if (hourly[i] > 0) 4.dp.toPx() else 2.dp.toPx()
                    )
                    drawRoundRect(
                        color = if (hourly[i] > 0) primary else idle,
                        topLeft = Offset(i * w + w * 0.18f, size.height - h),
                        size = Size(w * 0.64f, h),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "6时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "12时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "18时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "23时",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "这一天还没有记录",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "先在「应用」页勾选要统计的应用，并在「设置」里开启无障碍服务",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AppStatCard(
    stat: DayStat,
    label: String,
    expanded: Boolean,
    details: List<UsageSession>,
    onToggle: () -> Unit
) {
    val color = AVATAR_COLORS[(stat.packageName.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val avg = if (stat.count > 0) stat.totalMs / stat.count else 0L
                    Text(
                        text = "${stat.count} 次 · 平均 ${TimeUtils.formatDurationShort(avg)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = TimeUtils.formatDuration(stat.totalMs),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (stat.crashCount > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "疑似闪退 ${stat.crashCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                if (details.isEmpty()) {
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    details.takeLast(30).reversed().forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = TimeUtils.formatClockSec(s.startMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(14.dp)
                            )
                            Text(
                                text = TimeUtils.formatDuration(s.durationMs),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (s.suspiciousCrash) {
                                Text(
                                    text = "疑似闪退",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = reasonText(s.endReason),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun reasonText(reason: String): String = when (reason) {
    "HOME" -> "回桌面"
    "BACK" -> "返回上一应用"
    "SCREEN_OFF" -> "熄屏"
    "SERVICE_RESTART" -> "服务重启"
    "SERVICE_STOP" -> "服务停止"
    "REBOOT" -> "关机/重启"
    "PAUSED" -> "离开前台"
    "ONGOING" -> "正在使用"
    "SWITCH" -> "切换应用"
    else -> "切换应用"
}

/** 数据来源状态卡片：未授权时给出小米机型的设置路径引导 */
@Composable
private fun CollectSourceCard() {
    val context = LocalContext.current
    val usageMode = UsageCollector.usingUsageMode()
    val lastSync = Prefs.lastSyncMs
    val granted = remember(lastSync) { UsageSource.appOpsAllowed(context) }
    val needAuth = usageMode && !granted

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (needAuth) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (needAuth) Icons.Default.Warning
                    else Icons.Default.ChevronRight,
                    tint = if (needAuth) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.primary,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (needAuth) "需要授权：使用情况访问"
                    else if (usageMode) "数据来源：系统使用记录"
                    else "数据来源：无障碍服务",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (needAuth) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (needAuth) {
                Text(
                    text = "新版本不用常驻后台：系统在自己记录每个应用的打开与离开，" +
                        "打开本 App 时取回来即可。\n\n" +
                        "小米 / 澎湃 OS 开启路径：\n" +
                        "设置 → 搜索「使用情况访问」（或 隐私保护 → 特殊权限设置 → 使用情况访问）" +
                        "→ 找到「打卡」→ 打开开关。\n\n" +
                        "若搜不到该项，说明当前系统不允许第三方读取，请改用无障碍模式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = { UsageSource.openUsageAccessSettings(context) }) {
                    Text("去开启")
                }
            } else {
                Text(
                    text = buildString {
                        append(
                            if (usageMode) "打开界面自动同步，App 后台不运行、不轮询、不耗电。"
                            else "无障碍模式：App 需在后台常驻，被系统清理时会漏记。"
                        )
                        if (lastSync > 0L) {
                            append("\n最后同步 ")
                            append(TimeUtils.formatClockSec(lastSync))
                            append("，共 ")
                            append(Prefs.importedCount)
                            append(" 条记录")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
