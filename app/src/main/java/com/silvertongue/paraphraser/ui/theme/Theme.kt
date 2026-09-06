package com.silvertongue.paraphraser.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4C5BD4),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFE3E5FB),
    onSecondaryContainer = Color(0xFF1A1C2E),
    surface = Color(0xFFFBFBFE),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF5A5D72),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FA8FF),
    onPrimary = Color(0xFF1B2478),
    secondaryContainer = Color(0xFF343757),
    onSecondaryContainer = Color(0xFFE0E1F9),
    surface = Color(0xFF1A1B21),
    onSurface = Color(0xFFE4E2E6),
    onSurfaceVariant = Color(0xFFC5C6DD),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun SilvertongueTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkScheme else LightScheme,
        content = content
    )
}
