package com.rgbws2812.controller.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val LightColorScheme = lightColorScheme(
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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7ED6DD),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFF9BEFF6),
    secondary = Color(0xFFD7C86F),
    onSecondary = Color(0xFF363000),
    secondaryContainer = Color(0xFF4D4500),
    onSecondaryContainer = Color(0xFFF4E48A),
    tertiary = Color(0xFFFFB77A),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDCC0),
    background = Color(0xFF101415),
    onBackground = Color(0xFFE0E3E3),
    surface = Color(0xFF171B1C),
    onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4849),
    onSurfaceVariant = Color(0xFFBEC8C9),
    outline = Color(0xFF899394),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun RgbControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val systemBarColor = colorScheme.surface.toArgb()
            view.context.findComponentActivity().enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    lightScrim = systemBarColor,
                    darkScrim = systemBarColor,
                    detectDarkMode = { darkTheme }
                ),
                navigationBarStyle = SystemBarStyle.auto(
                    lightScrim = systemBarColor,
                    darkScrim = systemBarColor,
                    detectDarkMode = { darkTheme }
                )
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private tailrec fun Context.findComponentActivity(): ComponentActivity =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> error("Expected a ComponentActivity context.")
    }
