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
    AUTO("Auto Ambient Sensor"),
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
    val speedLimitAlertKmh: Int = 0, // 0 = disabled, e.g. 120 km/h
    val is24HourClock: Boolean = true,
    val defaultCarLauncher: String = "Google Maps",
    val themeAccent: ThemeAccent = ThemeAccent.I_BLUE,
    val isSimulatingTelemetry: Boolean = false,
    val testOverheatAlert: Boolean = false,
    // Calibrated RPM/Speed ratios for each gear (RPM / Speed_kmh)
    val gearRatios: Map<Int, Float> = mapOf(
        1 to 72.0f,
        2 to 49.0f,
        3 to 35.0f,
        4 to 26.0f,
        5 to 20.0f,
        6 to 15.0f
    ),
    val favoritePackages: Set<String> = emptySet(),
    val hiddenPackages: Set<String> = emptySet()
)
