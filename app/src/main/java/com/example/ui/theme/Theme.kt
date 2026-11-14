package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme =
  darkColorScheme(
    primary = StudioCyan,
    onPrimary = Color.White,
    primaryContainer = StudioCyanContainer,
    onPrimaryContainer = StudioOnCyanContainer,
    secondary = StudioCyanBright,
    onSecondary = StudioBg,
    secondaryContainer = StudioSurfaceHighest,
    onSecondaryContainer = StudioTextPrimary,
    tertiary = StudioSuccess,
    onTertiary = StudioBg,
    background = StudioBg,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorder,
    outlineVariant = Color(0xFF262C36),
    error = StudioError,
    onError = Color.White,
  )

@Composable
fun EasyStudioTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = StudioDarkColorScheme, typography = Typography, content = content)
}
