package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DisplayBrightnessMode
import com.example.model.HeadUnitScale
import com.example.model.SpeedUnit
import com.example.model.TempUnit
import com.example.model.UserSettings
import com.example.ui.theme.ThemeAccent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vision_hud_settings")

class SettingsDataStore(private val context: Context) {

    private object PreferencesKeys {
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
        val IS_HUD_MIRRORED = booleanPreferencesKey("is_hud_mirrored")
        val BRIGHTNESS_MODE = stringPreferencesKey("brightness_mode")
        val HEAD_UNIT_SCALE = stringPreferencesKey("head_unit_scale")
        val REDLINE_RPM = intPreferencesKey("redline_rpm")
        val COOLANT_THRESHOLD = intPreferencesKey("coolant_threshold")
        val SPEED_LIMIT_ALERT = intPreferencesKey("speed_limit_alert")
        val IS_24H_CLOCK = booleanPreferencesKey("is_24h_clock")
        val DEFAULT_CAR_LAUNCHER = stringPreferencesKey("default_car_launcher")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
        val IS_SIMULATING = booleanPreferencesKey("is_simulating")
        val GEAR_RATIOS = stringPreferencesKey("gear_ratios_csv")
        val FAVORITE_PACKAGES = stringSetPreferencesKey("favorite_packages")
        val HIDDEN_PACKAGES = stringSetPreferencesKey("hidden_packages")
        val CUSTOM_CARPLAY_COMPONENT = stringPreferencesKey("custom_carplay_component")
        val CUSTOM_ANDROID_AUTO_COMPONENT = stringPreferencesKey("custom_android_auto_component")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val speedUnit = try {
                SpeedUnit.valueOf(prefs[PreferencesKeys.SPEED_UNIT] ?: SpeedUnit.KMH.name)
            } catch (_: Exception) { SpeedUnit.KMH }

            val tempUnit = try {
                TempUnit.valueOf(prefs[PreferencesKeys.TEMP_UNIT] ?: TempUnit.CELSIUS.name)
            } catch (_: Exception) { TempUnit.CELSIUS }

            val brightnessMode = try {
                DisplayBrightnessMode.valueOf(prefs[PreferencesKeys.BRIGHTNESS_MODE] ?: DisplayBrightnessMode.NIGHT_OLED.name)
            } catch (_: Exception) { DisplayBrightnessMode.NIGHT_OLED }

            val headUnitScale = try {
                HeadUnitScale.valueOf(prefs[PreferencesKeys.HEAD_UNIT_SCALE] ?: HeadUnitScale.LARGE.name)
            } catch (_: Exception) { HeadUnitScale.LARGE }

            val themeAccent = try {
                ThemeAccent.valueOf(prefs[PreferencesKeys.THEME_ACCENT] ?: ThemeAccent.I_BLUE.name)
            } catch (_: Exception) { ThemeAccent.I_BLUE }

            val ratiosMap = parseGearRatios(prefs[PreferencesKeys.GEAR_RATIOS])

            UserSettings(
                speedUnit = speedUnit,
                tempUnit = tempUnit,
                isHudMirrored = prefs[PreferencesKeys.IS_HUD_MIRRORED] ?: false,
                brightnessMode = brightnessMode,
                headUnitScale = headUnitScale,
                redlineRpm = prefs[PreferencesKeys.REDLINE_RPM] ?: 7000,
                coolantAlertThreshold = prefs[PreferencesKeys.COOLANT_THRESHOLD] ?: 105,
                speedLimitAlertKmh = prefs[PreferencesKeys.SPEED_LIMIT_ALERT] ?: 0,
                is24HourClock = prefs[PreferencesKeys.IS_24H_CLOCK] ?: true,
                defaultCarLauncher = prefs[PreferencesKeys.DEFAULT_CAR_LAUNCHER] ?: "Google Maps",
                themeAccent = themeAccent,
                isSimulatingTelemetry = prefs[PreferencesKeys.IS_SIMULATING] ?: false,
                gearRatios = ratiosMap,
                favoritePackages = prefs[PreferencesKeys.FAVORITE_PACKAGES] ?: emptySet(),
                hiddenPackages = prefs[PreferencesKeys.HIDDEN_PACKAGES] ?: emptySet(),
                customCarPlayComponent = prefs[PreferencesKeys.CUSTOM_CARPLAY_COMPONENT] ?: "",
                customAndroidAutoComponent = prefs[PreferencesKeys.CUSTOM_ANDROID_AUTO_COMPONENT] ?: ""
            )
        }

    suspend fun saveSettings(settings: UserSettings) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SPEED_UNIT] = settings.speedUnit.name
            prefs[PreferencesKeys.TEMP_UNIT] = settings.tempUnit.name
            prefs[PreferencesKeys.IS_HUD_MIRRORED] = settings.isHudMirrored
            prefs[PreferencesKeys.BRIGHTNESS_MODE] = settings.brightnessMode.name
            prefs[PreferencesKeys.HEAD_UNIT_SCALE] = settings.headUnitScale.name
            prefs[PreferencesKeys.REDLINE_RPM] = settings.redlineRpm
            prefs[PreferencesKeys.COOLANT_THRESHOLD] = settings.coolantAlertThreshold
            prefs[PreferencesKeys.SPEED_LIMIT_ALERT] = settings.speedLimitAlertKmh
            prefs[PreferencesKeys.IS_24H_CLOCK] = settings.is24HourClock
            prefs[PreferencesKeys.DEFAULT_CAR_LAUNCHER] = settings.defaultCarLauncher
            prefs[PreferencesKeys.THEME_ACCENT] = settings.themeAccent.name
            prefs[PreferencesKeys.IS_SIMULATING] = settings.isSimulatingTelemetry
            prefs[PreferencesKeys.GEAR_RATIOS] = serializeGearRatios(settings.gearRatios)
            prefs[PreferencesKeys.FAVORITE_PACKAGES] = settings.favoritePackages
            prefs[PreferencesKeys.HIDDEN_PACKAGES] = settings.hiddenPackages
            prefs[PreferencesKeys.CUSTOM_CARPLAY_COMPONENT] = settings.customCarPlayComponent
            prefs[PreferencesKeys.CUSTOM_ANDROID_AUTO_COMPONENT] = settings.customAndroidAutoComponent
        }
    }

    private fun serializeGearRatios(ratios: Map<Int, Float>): String {
        return ratios.entries.joinToString(";") { "${it.key}:${it.value}" }
    }

    private fun parseGearRatios(raw: String?): Map<Int, Float> {
        val default = mapOf(1 to 72.0f, 2 to 49.0f, 3 to 35.0f, 4 to 26.0f, 5 to 20.0f, 6 to 15.0f)
        if (raw.isNullOrBlank()) return default
        return try {
            val map = mutableMapOf<Int, Float>()
            raw.split(";").forEach { item ->
                val parts = item.split(":")
                if (parts.size == 2) {
                    val gear = parts[0].trim().toIntOrNull()
                    val ratio = parts[1].trim().toFloatOrNull()
                    if (gear != null && ratio != null) {
                        map[gear] = ratio
                    }
                }
            }
            if (map.isNotEmpty()) map else default
        } catch (_: Exception) {
            default
        }
    }
}
