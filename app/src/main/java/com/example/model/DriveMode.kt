package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.BmwCrimson
import com.example.ui.theme.BmwCyan
import com.example.ui.theme.BmwDeepBlue
import com.example.ui.theme.BmwEcoTeal

enum class DriveMode(
    val title: String,
    val badge: String,
    val primaryColor: Color,
    val accentColor: Color,
    val glowColor: Color,
    val shiftLabel: String,
    val baseRpm: Int,
    val baseSpeed: Int,
    val defaultGear: String
) {
    SPORT(
        title = "SPORT",
        badge = "SPORT MODE",
        primaryColor = BmwCrimson,
        accentColor = Color(0xFFFF7700),
        glowColor = Color(0x66FF2A38),
        shiftLabel = "SHIFT LIGHTS",
        baseRpm = 3850,
        baseSpeed = 94,
        defaultGear = "S"
    ),
    COMFORT(
        title = "COMFORT",
        badge = "COMFORT MODE",
        primaryColor = BmwCyan,
        accentColor = BmwDeepBlue,
        glowColor = Color(0x664CC2FF),
        shiftLabel = "BALANCED RPM",
        baseRpm = 2150,
        baseSpeed = 84,
        defaultGear = "D"
    ),
    ECO_PRO(
        title = "ECO PRO",
        badge = "ECO PRO MODE",
        primaryColor = BmwEcoTeal,
        accentColor = Color(0xFF00B4D8),
        glowColor = Color(0x6600E5A3),
        shiftLabel = "EFFICIENCY GUIDANCE",
        baseRpm = 1650,
        baseSpeed = 78,
        defaultGear = "D"
    )
}
