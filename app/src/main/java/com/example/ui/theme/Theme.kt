package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PostHogOrange,
    secondary = PostHogOrangeLight,
    background = PostHogCharcoal,
    surface = PostHogDarkCard,
    onPrimary = Color.White,
    onSecondary = PostHogCharcoal,
    onBackground = PostHogSand,
    onSurface = PostHogSand,
    outline = PostHogBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = PostHogOrange,
    secondary = PostHogCharcoal,
    background = PostHogSand,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PostHogCharcoal,
    onSurface = PostHogCharcoal,
    outline = PostHogBorder
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
