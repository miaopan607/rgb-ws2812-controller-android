package com.rgbws2812.controller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = Color(0xFF006D77),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7EBEF),
    onPrimaryContainer = Color(0xFF002023),
    secondary = Color(0xFF665A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E48A),
    onSecondaryContainer = Color(0xFF201B00),
    tertiary = Color(0xFF8A4A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCC0),
    onTertiaryContainer = Color(0xFF2C1600),
    background = Color(0xFFF8FAFA),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4849),
    outline = Color(0xFF6F797A),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun RgbControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
