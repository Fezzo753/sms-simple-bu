package com.simplebackup.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1F2630),
    onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D),
    error = Color(0xFFF85149)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6FEB),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F8FA)
)

@Composable
fun SimpleBackupTheme(useDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        content = content
    )
}
