package com.example.model

import com.example.ui.theme.ThemeAccent

enum class SpeedUnit(val label: String, val factorFromKmh: Float) {
    KMH("km/h", 1.0f),
    MPH("mph", 0.621371f)
}

enum class TempUnit(val label: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F")
}

enum class DisplayBrightnessMode(val label: String) {
    AUTO("Auto Ambient"),
    DAY("Day High-Contrast"),
    NIGHT_OLED("Night Pure OLED")
}

data class UserSettings(
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val isHudMirrored: Boolean = false,
    val brightnessMode: DisplayBrightnessMode = DisplayBrightnessMode.NIGHT_OLED,
    val redlineRpm: Int = 7000,
    val coolantAlertThreshold: Int = 105,
    val is24HourClock: Boolean = true,
    val defaultCarLauncher: String = "Android Auto",
    val themeAccent: ThemeAccent = ThemeAccent.I_BLUE,
    val isSimulatingTelemetry: Boolean = false, // Real OBD2 by default, no dummy data
    val testOverheatAlert: Boolean = false
)
