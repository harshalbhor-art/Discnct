package com.discnct.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalDiscnctColors = staticCompositionLocalOf { DarkDiscnctColors }

@Composable
fun DiscnctTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkDiscnctColors else LightDiscnctColors

    // Material3 is used only as the interaction/accessibility substrate (ripple, focus,
    // Switch semantics); every visible color and type choice comes from DiscnctColors/DiscnctType.
    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.textDisplay,
            onPrimary = colors.black,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            background = colors.black,
            onBackground = colors.textPrimary,
            error = colors.accent,
        )
    } else {
        lightColorScheme(
            primary = colors.textDisplay,
            onPrimary = colors.black,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            background = colors.black,
            onBackground = colors.textPrimary,
            error = colors.accent,
        )
    }

    CompositionLocalProvider(LocalDiscnctColors provides colors) {
        MaterialTheme(colorScheme = materialScheme, content = content)
    }
}
