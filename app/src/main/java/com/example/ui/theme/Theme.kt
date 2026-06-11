package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = HogPurpleBright,
    onPrimary = HogPurpleDeep,
    primaryContainer = HogPurpleDeep,
    onPrimaryContainer = HogPurpleSoft,
    secondary = HogMagenta,
    onSecondary = Color.White,
    tertiary = HogOrange,
    onTertiary = HogInk,
    background = HogInk,
    surface = HogInkCard,
    surfaceVariant = HogBorderDark,
    onSurfaceVariant = Color(0xFFCDBFDB),
    onBackground = HogMist,
    onSurface = HogMist,
    outline = HogBorderDark,
    error = HogRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = HogPurple,
    onPrimary = Color.White,
    primaryContainer = HogPurpleSoft,
    onPrimaryContainer = HogPurpleDeep,
    secondary = HogMagenta,
    onSecondary = Color.White,
    tertiary = HogOrange,
    onTertiary = Color.White,
    background = HogMist,
    surface = Color.White,
    surfaceVariant = HogPurpleSoft,
    onSurfaceVariant = Color(0xFF5E5468),
    onBackground = HogInk,
    onSurface = HogInk,
    outline = HogBorder,
    error = HogRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
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
