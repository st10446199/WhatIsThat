/*
 * WhatIsThat
 * Copyright (c) 2026 Mohammed Anas Patel
 * All Rights Reserved — see LICENSE file for details.
 * https://github.com/[your-username]/WhatIsThat
 */

package com.mohammedanaspatel.whatisthat.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mirrors themes.ts from the Figma design.
 * Each preset is a full color set; accent-derived tokens (dim/glow/bg/border)
 * are computed the same way the original accentTokens() function did.
 */
data class Theme(
    val id: String,
    val displayName: String,
    val emoji: String,
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val borderSubtle: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color
)

/** Derived accent tokens: dim (78% brightness), glow/bg/border (alpha variants). */
data class AccentTokens(
    val dim: Color,
    val glow: Color,
    val bg: Color,
    val border: Color
)

/**
 * Builds a complete Theme from just two user-picked colors (background + accent).
 * Surface/card/text/etc. are all derived automatically so a custom theme still
 * looks coherent - the user only has to pick 2 colors, not 12.
 */
fun buildCustomTheme(bg: Color, accent: Color): Theme {
    // Perceived luminance (standard formula) decides if this counts as a dark theme
    val luminance = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    val isDark = luminance < 0.5f

    // Lighten a color toward white, or darken toward black, by a given amount
    fun lighten(c: Color, amount: Float) = Color(
        red = c.red + (1f - c.red) * amount,
        green = c.green + (1f - c.green) * amount,
        blue = c.blue + (1f - c.blue) * amount,
        alpha = 1f
    )
    fun darken(c: Color, amount: Float) = Color(
        red = c.red * (1f - amount),
        green = c.green * (1f - amount),
        blue = c.blue * (1f - amount),
        alpha = 1f
    )

    val surface = if (isDark) lighten(bg, 0.08f) else darken(bg, 0.05f)
    val card = if (isDark) lighten(bg, 0.14f) else Color.White
    val border = if (isDark) lighten(bg, 0.20f) else darken(bg, 0.12f)
    val borderSubtle = if (isDark) lighten(bg, 0.12f) else darken(bg, 0.08f)
    val text = if (isDark) Color(0xFFF5F5F5) else Color(0xFF141414)
    val textSecondary = if (isDark) Color(0xFFB0B0B0) else Color(0xFF505050)
    val textMuted = if (isDark) Color(0xFF757575) else Color(0xFF8A8A8A)

    return Theme(
        id = "custom",
        displayName = "Custom",
        emoji = "🎨",
        isDark = isDark,
        bg = bg,
        surface = surface,
        card = card,
        border = border,
        borderSubtle = borderSubtle,
        text = text,
        textSecondary = textSecondary,
        textMuted = textMuted,
        accent = accent
    )
}

fun accentTokens(accent: Color): AccentTokens {
    val dim = Color(
        red = accent.red * 0.78f,
        green = accent.green * 0.78f,
        blue = accent.blue * 0.78f,
        alpha = 1f
    )
    val glow = accent.copy(alpha = 0.32f)
    val bg = accent.copy(alpha = 0.12f)
    val border = accent.copy(alpha = 0.35f)
    return AccentTokens(dim, glow, bg, border)
}

val PRESETS = listOf(
    Theme(
        id = "obsidian",
        displayName = "Obsidian",
        emoji = "🔮",
        isDark = true,
        bg = Color(0xFF0E0E14),
        surface = Color(0xFF18182A),
        card = Color(0xFF20203A),
        border = Color(0xFF2A2A44),
        borderSubtle = Color(0xFF1C1C32),
        text = Color(0xFFF0EFFF),
        textSecondary = Color(0xFFA8A8CC),
        textMuted = Color(0xFF64648A),
        accent = Color(0xFFA78BFA)
    ),
    Theme(
        id = "ocean",
        displayName = "Ocean",
        emoji = "🌊",
        isDark = true,
        bg = Color(0xFF050F1E),
        surface = Color(0xFF0C1E35),
        card = Color(0xFF112847),
        border = Color(0xFF1A3558),
        borderSubtle = Color(0xFF0E2240),
        text = Color(0xFFE8F4FF),
        textSecondary = Color(0xFF90B8D8),
        textMuted = Color(0xFF4A7090),
        accent = Color(0xFF38BDF8)
    ),
    Theme(
        id = "ember",
        displayName = "Ember",
        emoji = "🔥",
        isDark = true,
        bg = Color(0xFF100A04),
        surface = Color(0xFF1E1008),
        card = Color(0xFF2A1810),
        border = Color(0xFF3A2218),
        borderSubtle = Color(0xFF241410),
        text = Color(0xFFFFF5EE),
        textSecondary = Color(0xFFC8A090),
        textMuted = Color(0xFF806050),
        accent = Color(0xFFF97316)
    ),
    Theme(
        id = "forest",
        displayName = "Forest",
        emoji = "🌿",
        isDark = true,
        bg = Color(0xFF060F08),
        surface = Color(0xFF0E1E12),
        card = Color(0xFF14281A),
        border = Color(0xFF1E3824),
        borderSubtle = Color(0xFF102018),
        text = Color(0xFFEDFFF2),
        textSecondary = Color(0xFF88C898),
        textMuted = Color(0xFF486858),
        accent = Color(0xFF4ADE80)
    ),
    Theme(
        id = "rose",
        displayName = "Rose",
        emoji = "🌸",
        isDark = true,
        bg = Color(0xFF100610),
        surface = Color(0xFF1E0E1E),
        card = Color(0xFF2A1428),
        border = Color(0xFF3A1C38),
        borderSubtle = Color(0xFF221022),
        text = Color(0xFFFFF0F8),
        textSecondary = Color(0xFFC888B8),
        textMuted = Color(0xFF805870),
        accent = Color(0xFFF472B6)
    ),
    Theme(
        id = "slate",
        displayName = "Slate",
        emoji = "🪨",
        isDark = true,
        bg = Color(0xFF090C12),
        surface = Color(0xFF111620),
        card = Color(0xFF181E2C),
        border = Color(0xFF242C3E),
        borderSubtle = Color(0xFF141A28),
        text = Color(0xFFE8EEFF),
        textSecondary = Color(0xFF8898B8),
        textMuted = Color(0xFF485878),
        accent = Color(0xFF818CF8)
    ),
    Theme(
        id = "paper",
        displayName = "Paper",
        emoji = "📄",
        isDark = false,
        bg = Color(0xFFFAF7F2),
        surface = Color(0xFFF0EBE0),
        card = Color(0xFFFFFFFF),
        border = Color(0xFFE0D8C8),
        borderSubtle = Color(0xFFECE5D8),
        text = Color(0xFF1C1810),
        textSecondary = Color(0xFF5A5040),
        textMuted = Color(0xFF9A9080),
        accent = Color(0xFFD97706)
    ),
    Theme(
        id = "sky",
        displayName = "Sky",
        emoji = "☁️",
        isDark = false,
        bg = Color(0xFFF0F8FF),
        surface = Color(0xFFE0EEF8),
        card = Color(0xFFFFFFFF),
        border = Color(0xFFC8DDF0),
        borderSubtle = Color(0xFFDAEAF8),
        text = Color(0xFF0C1A28),
        textSecondary = Color(0xFF3A5878),
        textMuted = Color(0xFF7898B0),
        accent = Color(0xFF0284C7)
    ),
    Theme(
        id = "sage",
        displayName = "Sage",
        emoji = "🌱",
        isDark = false,
        bg = Color(0xFFF2F6F2),
        surface = Color(0xFFE4ECE4),
        card = Color(0xFFFFFFFF),
        border = Color(0xFFCCDACC),
        borderSubtle = Color(0xFFDBE6DB),
        text = Color(0xFF0E1E0E),
        textSecondary = Color(0xFF3A5A3A),
        textMuted = Color(0xFF7A9A7A),
        accent = Color(0xFF16A34A)
    )
)
