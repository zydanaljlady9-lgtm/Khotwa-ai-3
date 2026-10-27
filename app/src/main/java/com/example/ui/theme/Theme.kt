package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = KhotwaEmeraldPrimary,
    onPrimary = KhotwaEmeraldOnPrimary,
    primaryContainer = KhotwaEmeraldContainer,
    onPrimaryContainer = KhotwaOnEmeraldContainer,
    secondary = KhotwaGoldSecondary,
    onSecondary = KhotwaOnGoldSecondary,
    secondaryContainer = KhotwaGoldContainer,
    onSecondaryContainer = KhotwaOnGoldContainer,
    tertiary = KhotwaCyanTertiary,
    background = KhotwaDarkBackground,
    onBackground = KhotwaDarkTextPrimary,
    surface = KhotwaDarkSurface,
    onSurface = KhotwaDarkTextPrimary,
    surfaceVariant = KhotwaDarkSurfaceVariant,
    onSurfaceVariant = KhotwaDarkTextSecondary,
    outline = KhotwaDarkOutline,
    error = KhotwaDangerRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = KhotwaEmeraldLightPrimary,
    onPrimary = KhotwaEmeraldLightOnPrimary,
    primaryContainer = KhotwaEmeraldLightContainer,
    onPrimaryContainer = KhotwaOnEmeraldLightContainer,
    secondary = KhotwaGoldLightSecondary,
    onSecondary = KhotwaOnGoldLightSecondary,
    secondaryContainer = KhotwaGoldLightContainer,
    onSecondaryContainer = KhotwaOnGoldLightContainer,
    tertiary = KhotwaCyanLightTertiary,
    background = KhotwaLightBackground,
    onBackground = KhotwaLightTextPrimary,
    surface = KhotwaLightSurface,
    onSurface = KhotwaLightTextPrimary,
    surfaceVariant = KhotwaLightSurfaceVariant,
    onSurfaceVariant = KhotwaLightTextSecondary,
    outline = KhotwaLightOutline,
    error = KhotwaDangerRed,
    onError = Color.White
)

@Composable
fun KhotwaAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceRtl: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val layoutDirection = if (forceRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    KhotwaAITheme(darkTheme = darkTheme, forceRtl = true, content = content)
}
