// 目标路径: app/src/main/java/com/daka/punch/ui/theme/Theme.kt
package com.daka.punch.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.daka.punch.data.Prefs

private val BrandLight = lightColorScheme(
    primary = Color(0xFF6D5EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF1B1464),
    secondary = Color(0xFF0EA5E9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6EFFF),
    onSecondaryContainer = Color(0xFF023047),
    tertiary = Color(0xFF22C55E),
    onTertiary = Color.White,
    background = Color(0xFFF7F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEEEF6),
    error = Color(0xFFE5484D)
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFB3A6FF),
    onPrimary = Color(0xFF1B1464),
    primaryContainer = Color(0xFF3A2FA8),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondary = Color(0xFF7DD3FC),
    onSecondary = Color(0xFF023047),
    secondaryContainer = Color(0xFF0B4A63),
    onSecondaryContainer = Color(0xFFD6EFFF),
    tertiary = Color(0xFF4ADE80),
    onTertiary = Color(0xFF052E16),
    background = Color(0xFF12121A),
    surface = Color(0xFF1A1A25),
    surfaceVariant = Color(0xFF26263A),
    error = Color(0xFFFF6369)
)

@Composable
fun PunchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (Prefs.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val useDynamic = Prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        useDynamic && dark -> dynamicDarkColorScheme(context)
        useDynamic && !dark -> dynamicLightColorScheme(context)
        dark -> BrandDark
        else -> BrandLight
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
