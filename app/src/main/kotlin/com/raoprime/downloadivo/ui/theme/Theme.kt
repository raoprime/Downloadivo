package com.raoprime.downloadivo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryEmerald,
    onPrimary = Color.Black,
    primaryContainer = SurfaceCardDark,
    onPrimaryContainer = TextPrimary,
    secondary = PrimaryEmerald,
    onSecondary = Color.Black,
    secondaryContainer = BadgeBackground,
    onSecondaryContainer = PrimaryEmerald,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = BackgroundDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCardDark,
    onSurfaceVariant = TextMuted,
    outline = CardBorderDark,
    error = ErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = TextPrimary
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
