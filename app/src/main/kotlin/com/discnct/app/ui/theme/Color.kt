package com.discnct.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Token set ported directly from the Nothing-inspired design system reference
 * (dot-matrix motif, single-accent grayscale hierarchy). Dark is the primary
 * "instrument panel" feel; light is the "printed technical manual" pair.
 */
data class DiscnctColors(
    val black: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderVisible: Color,
    val textDisabled: Color,
    val textSecondary: Color,
    val textPrimary: Color,
    val textDisplay: Color,
    val accent: Color,
    val accentSubtle: Color,
    val success: Color,
    val warning: Color,
    val interactive: Color,
)

val DarkDiscnctColors = DiscnctColors(
    black = Color(0xFF000000),
    surface = Color(0xFF111111),
    surfaceRaised = Color(0xFF1A1A1A),
    border = Color(0xFF222222),
    borderVisible = Color(0xFF333333),
    textDisabled = Color(0xFF666666),
    textSecondary = Color(0xFF999999),
    textPrimary = Color(0xFFE8E8E8),
    textDisplay = Color(0xFFFFFFFF),
    accent = Color(0xFFD71921),
    accentSubtle = Color(0x26D71921),
    success = Color(0xFF4A9E5C),
    warning = Color(0xFFD4A843),
    interactive = Color(0xFF5B9BF6),
)

val LightDiscnctColors = DiscnctColors(
    black = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF0F0F0),
    border = Color(0xFFE8E8E8),
    borderVisible = Color(0xFFCCCCCC),
    textDisabled = Color(0xFF999999),
    textSecondary = Color(0xFF666666),
    textPrimary = Color(0xFF1A1A1A),
    textDisplay = Color(0xFF000000),
    accent = Color(0xFFD71921),
    accentSubtle = Color(0x26D71921),
    success = Color(0xFF4A9E5C),
    warning = Color(0xFFD4A843),
    interactive = Color(0xFF007AFF),
)
