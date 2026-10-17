package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// OLED True Black Canvas
val OledBlack = Color(0xFF000000)
val CockpitSurfaceLowest = Color(0xFF05070A)
val CockpitSurface = Color(0xFF0C1017)
val CockpitSurfaceHigh = Color(0xFF141A24)
val CockpitSurfaceHighest = Color(0xFF1E2633)

// Glassmorphism overlays & borders
val GlassCardFill = Color(0xB2101722) // 70% opacity dark glass
val GlassCardBorder = Color(0x2EFFFFFF) // Subtle 1px specular border
val GlassCardBorderActive = Color(0x664CC2FF)

// BMW Neue Klasse Signature Accents
val BmwCyan = Color(0xFF4CC2FF)        // BMW i-Blue electric cyan
val BmwDeepBlue = Color(0xFF1C69D4)    // BMW i-Blue deep structural
val BmwPureBlue = Color(0xFF0066B1)    // Classic BMW Motorsport Blue
val BmwCrimson = Color(0xFFFF2A38)     // M Power Crimson / Redline
val BmwAmber = Color(0xFFFFB000)       // Caution / Fuel / Amber warning
val BmwEcoTeal = Color(0xFF00E5A3)     // Eco Pro efficient teal
val BmwOptimalGreen = Color(0xFF10B981)// Normal status / Optimal temperature

// High Contrast Text & Telemetry
val TextPrimaryWhite = Color(0xFFF2F5F8)
val TextSecondaryGrey = Color(0xFF8FA0B5)
val TextMuted = Color(0xFF5A697A)
val TextDark = Color(0xFF05070A)

// Drive Mode Accent Definitions
enum class ThemeAccent(val label: String, val primary: Color, val accent: Color, val glow: Color) {
    I_BLUE("BMW i-Blue (Neue Klasse)", BmwCyan, BmwDeepBlue, Color(0x664CC2FF)),
    M_CRIMSON("M-Sport Crimson", BmwCrimson, Color(0xFFFF7700), Color(0x66FF2A38)),
    EMBER_AMBER("Ember Amber", BmwAmber, Color(0xFFFF8800), Color(0x66FFB000)),
    ECO_PRO("Eco Pro Teal", BmwEcoTeal, Color(0xFF00B4D8), Color(0x6600E5A3))
}
