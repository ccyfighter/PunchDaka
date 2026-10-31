// 目标路径: app/src/main/java/com/daka/punch/ui/MainActivity.kt
package com.daka.punch.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.daka.punch.data.Prefs
import com.daka.punch.data.Repository
import com.daka.punch.reminder.Notifier
import com.daka.punch.ui.screens.AppsScreen
import com.daka.punch.ui.screens.HomeScreen
import com.daka.punch.ui.screens.ReminderScreen
import com.daka.punch.ui.screens.SettingsScreen
import com.daka.punch.ui.theme.PunchTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        Repository.init(this)
        Notifier.ensureChannels(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            PunchTheme {
                AppRoot(onRequestNotificationPermission = {
                    requestNotificationPermissionIfNeeded()
                })
            }
        }
        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 忽略结果 */ }
}

private enum class Tab(val title: String, val icon: ImageVector) {
    HOME("统计", Icons.Default.Home),
    APPS("应用", Icons.Default.Apps),
    REMINDER("提醒", Icons.Default.Notifications),
    SETTINGS("设置", Icons.Default.Settings)
}

@Composable
private fun AppRoot(onRequestNotificationPermission: () -> Unit) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = Tab.entries.toTypedArray()

    LaunchedEffect(Unit) {
        onRequestNotificationPermission()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        icon = {
                            Icon(imageVector = tab.icon, contentDescription = tab.title)
                        },
                        label = { Text(tab.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tabs[tabIndex]) {
                Tab.HOME -> HomeScreen()
                Tab.APPS -> AppsScreen()
                Tab.REMINDER -> ReminderScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
