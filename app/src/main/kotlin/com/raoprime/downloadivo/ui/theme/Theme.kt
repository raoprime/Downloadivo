package com.raoprime.downloadivo.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = TextCrisp,
    onPrimary = CanvasDark,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = TextCrisp,
    secondary = TextCrisp,
    onSecondary = CanvasDark,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = TextCrisp,
    background = CanvasDark,
    onBackground = TextCrisp,
    surface = CanvasDark,
    onSurface = TextCrisp,
    surfaceVariant = SurfaceDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderHairline,
    outlineVariant = BorderSubtle,
    error = StatusError,
    errorContainer = StatusErrorBg,
    onErrorContainer = TextCrisp
)

@Composable
fun DownloadivoTheme(
    darkTheme: Boolean = true, // Force modern obsidian aesthetic
    dynamicColor: Boolean = false, // Keep pristine synced theme with website
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
