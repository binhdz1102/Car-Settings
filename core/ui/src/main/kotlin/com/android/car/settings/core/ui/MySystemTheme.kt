package com.android.car.settings.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8BC9FF),
        onPrimary = Color(0xFF00344F),
        primaryContainer = Color(0xFF004C70),
        secondary = Color(0xFFB8C9D9),
        surface = Color(0xFF0F1418),
        surfaceVariant = Color(0xFF27323A),
        background = Color(0xFF0F1418),
    )

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF006493),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC9E6FF),
        secondary = Color(0xFF50606E),
        surface = Color(0xFFF7F9FC),
        surfaceVariant = Color(0xFFDDE3EA),
        background = Color(0xFFF7F9FC),
    )

@Composable
fun MySystemTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
