package com.reaream.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9C7CF4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A3780),
    secondary = Color(0xFF64DFDF),
    onSecondary = Color.Black,
    background = Color(0xFF0D0D1A),
    onBackground = Color.White,
    surface = Color(0xFF1A1A2E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF252540),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFFF6B6B),
    onError = Color.White,
)

@Composable
fun ReareamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content,
    )
}
