// 目标路径: app/src/main/java/com/daka/punch/ui/screens/AppsScreen.kt
package com.daka.punch.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.daka.punch.data.Repository
import com.daka.punch.util.AppInfos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AVATAR_COLORS = listOf(
    Color(0xFF6D5EFF), Color(0xFF0EA5E9), Color(0xFF22C55E),
    Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF14B8A6)
)

/** 少于这个数量就认为"被系统挡住了"，给出提示 */
private const val TOO_FEW_THRESHOLD = 20

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf(AppInfos.Snapshot(emptyList(), 0, 0, 0, 0)) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var newPkg by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf("") }

    LaunchedEffect(refreshKey) {
        loading = true
        val current = withContext(Dispatchers.IO) { Repository.monitoredPackages() }
        selected = current
        val snap = withContext(Dispatchers.IO) { AppInfos.allApps(context, current) }
        snapshot = snap
        loading = false
    }

    // 从系统设置授权回来，自动重读一次列表
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    val allApps = snapshot.apps
    val filtered = allApps.filter { entry ->
        (showSystem || !entry.isSystem) &&
                (query.isBlank() ||
                        entry.label.contains(query, ignoreCase = true) ||
                        entry.packageName.contains(query, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "选择要统计的应用",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "已选 ${selected.size} 个 · 共读到 ${allApps.size} 个应用" +
                    "（桌面 ${snapshot.launcherCount} / 已装 ${snapshot.installedCount} / " +
                    "使用记录 ${snapshot.usageCount}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (!loading && allApps.size < TOO_FEW_THRESHOLD) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "只读到 ${allApps.size} 个应用，明显偏少",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "小米在原生「软件包可见性」之外还加了一层「获取应用列表」权限，" +
                                "被拒绝时只能读到极少应用。请点下面的按钮，把「获取应用列表 / " +
                                "读取已安装应用」设为始终允许；没有这个选项的话，用「手动添加包名」也能选到。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { AppInfos.openPermissionEditor(context) }) {
                            Text("打开权限设置")
                        }
                        TextButton(onClick = { refreshKey++ }) {
                            Text("重新读取")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索应用名称或包名") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "显示系统应用",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = showSystem, onCheckedChange = { showSystem = it })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { showAdd = true }) { Text("手动添加包名") }
            TextButton(onClick = { refreshKey++ }) { Text("刷新列表") }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "当前 ${filtered.size} 项",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "正在读取应用列表…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.packageName }) { entry ->
                    AppRow(
                        entry = entry,
                        checked = selected.contains(entry.packageName),
                        onCheckedChange = { checked ->
                            Repository.setMonitored(entry.packageName, entry.label, checked)
                            selected = if (checked) {
                                selected + entry.packageName
                            } else {
                                selected - entry.packageName
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("手动添加包名") },
            text = {
                Column {
                    Text(
                        text = "列表里找不到的应用，可以直接填包名。常用示例：\n" +
                                "哔哩哔哩 tv.danmaku.bili\n" +
                                "王者荣耀 com.tencent.tmgp.sgame\n" +
                                "明日方舟 com.hypergryph.arknights",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPkg,
                        onValueChange = { newPkg = it; addError = "" },
                        placeholder = { Text("例如 tv.danmaku.bili") },
                        singleLine = true,
                        isError = addError.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = addError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pkg = newPkg.trim()
                    if (!pkg.contains(".") || pkg.contains(" ")) {
                        addError = "包名格式不对，应类似 com.xxx.yyy"
                    } else {
                        val lab = AppInfos.label(context, pkg)
                        Repository.setMonitored(pkg, lab, true)
                        selected = selected + pkg
                        newPkg = ""
                        addError = ""
                        showAdd = false
                        refreshKey++
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false; addError = "" }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AppRow(
    entry: AppInfos.Entry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val color = AVATAR_COLORS[(entry.packageName.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = entry.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
