package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TacticalColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = TextDark,
    primaryContainer = CyanPrimaryDark,
    onPrimaryContainer = TextWhite,
    secondary = TransmitAmber,
    onSecondary = TextDark,
    tertiary = SignalGreen,
    onTertiary = TextDark,
    background = DarkNavyBackground,
    onBackground = TextWhite,
    surface = DarkSlateSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSlateCard,
    onSurfaceVariant = TextMuted,
    outline = SurfaceBorder,
    error = AlertRed,
    onError = TextWhite
)

@Composable
fun LanTalkieTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TacticalColorScheme,
        typography = Typography,
        content = content
    )
}
