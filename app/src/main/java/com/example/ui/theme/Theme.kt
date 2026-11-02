package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Amber400,
    onPrimary = Slate950,
    primaryContainer = Color(0xFF452B03),
    onPrimaryContainer = Amber100,
    secondary = Cyan400,
    onSecondary = Slate950,
    secondaryContainer = Color(0xFF083344),
    onSecondaryContainer = Cyan100,
    tertiary = Color(0xFFF472B6),
    onTertiary = Slate950,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    outline = Slate700
)

private val LightColorScheme = lightColorScheme(
    primary = Amber600,
    onPrimary = Color.White,
    primaryContainer = Amber100,
    onPrimaryContainer = Color(0xFF451A03),
    secondary = Cyan600,
    onSecondary = Color.White,
    secondaryContainer = Cyan100,
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFFDB2777),
    onTertiary = Color.White,
    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate200,
    onSurfaceVariant = Slate600,
    outline = Slate400
)

@Composable
fun OmniDocTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
