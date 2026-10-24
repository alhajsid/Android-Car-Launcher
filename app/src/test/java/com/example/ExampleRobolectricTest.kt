package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppsRepository
import com.example.data.TelemetryRepository
import com.example.data.obd.ObdBluetoothConnectionManager
import com.example.data.obd.ObdProtocol
import com.example.model.DriveMode
import com.example.model.SpeedUnit
import com.example.model.TelemetryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Vision HUD", appName)
    }

    @Test
    fun `telemetry data defaults and drive modes`() {
        val telemetry = TelemetryData()
        assertEquals(0, telemetry.speed)
        assertFalse(telemetry.isObdConnected)
        assertEquals(null, telemetry.fuelLevelPercent)
        assertEquals(null, telemetry.remainingRangeKm)
        assertEquals(DriveMode.SPORT, telemetry.driveMode)
        assertEquals("-", telemetry.currentGear)
        assertTrue(telemetry.isGearEstimated)

        val comfort = DriveMode.COMFORT
        assertEquals("D", comfort.defaultGear)
        assertEquals("COMFORT", comfort.title)
    }

    @Test
    fun `speed unit conversion`() {
        val speedKmh = 100
        val speedMph = (speedKmh * SpeedUnit.MPH.factorFromKmh).toInt()
        assertEquals(62, speedMph)
    }

    @Test
    fun `obd protocol parsing tests`() {
        val parsedRpm = ObdProtocol.parseRpm("41 0C 1A F8\r>")
        assertEquals(1726, parsedRpm)

        val parsedSpeed = ObdProtocol.parseSpeed("41 0D 5A\r>")
        assertEquals(90, parsedSpeed)

        val parsedCoolant = ObdProtocol.parseCoolant("41 05 7B\r>")
        assertEquals(83, parsedCoolant)

        val parsedLoad = ObdProtocol.parseEngineLoad("41 04 57\r>")
        assertEquals(34, parsedLoad)

        val parsedVoltage = ObdProtocol.parseVoltage("14.2V\r>")
        assertEquals(14.2f, parsedVoltage)
    }

    @Test
    fun `apps repository loads apps and verifies launcher check`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppsRepository(context)
        val apps = repo.getInstalledApps()
        assertNotNull(apps)
        assertTrue(apps.isNotEmpty())

        val isDefault = repo.isDefaultLauncher()
        assertNotNull(isDefault)
    }

    @Test
    fun `obd bluetooth connection manager filters and rejects unrelated devices`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val btManager = ObdBluetoothConnectionManager(context)

        // Genuine OBD2 adapters
        assertTrue(btManager.isLikelyObdAdapter("OBDLink MX+ Bluetooth"))
        assertTrue(btManager.isLikelyObdAdapter("vLinker MC+"))
        assertTrue(btManager.isLikelyObdAdapter("Veepeak OBDCheck BLE"))
        assertTrue(btManager.isLikelyObdAdapter("ELM327 v1.5"))
        assertTrue(btManager.isLikelyObdAdapter("Torque OBD"))
        assertTrue(btManager.isLikelyObdAdapter("Vgate iCar Pro BLE4.0"))
        assertTrue(btManager.isLikelyObdAdapter("KONNWEI KW902"))

        // Unrelated Bluetooth devices (including tricky names like "My Car Multimedia" or "TP-Link")
        assertFalse(btManager.isLikelyObdAdapter("Sony WH-1000XM5"))
        assertFalse(btManager.isLikelyObdAdapter("Galaxy Buds Pro"))
        assertFalse(btManager.isLikelyObdAdapter("My Car Multimedia"))
        assertFalse(btManager.isLikelyObdAdapter("TP-Link Audio Receiver"))
        assertFalse(btManager.isLikelyObdAdapter(null))
        assertFalse(btManager.isLikelyObdAdapter("   "))

        val devices = btManager.getPairedObdDevices()
        assertNotNull(devices)
    }

    @Test
    fun `gear estimation edge cases never claim confirmed P or N`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = TelemetryRepository(context)
        val defaultRatios = mapOf(
            1 to 115f,
            2 to 68f,
            3 to 46f,
            4 to 34f,
            5 to 27f,
            6 to 22f
        )

        // Engine off & stationary -> "-"
        val offGear = repo.calculateGear(rpm = 0, speedKmh = 0, ratios = defaultRatios, driveMode = DriveMode.COMFORT)
        assertEquals("-", offGear)
        assertNotEquals("P", offGear)
        assertNotEquals("N", offGear)

        // Stationary with engine idling at 780 RPM -> "IDLE" (never falsely claiming "P" or "N")
        val idleGear = repo.calculateGear(rpm = 780, speedKmh = 0, ratios = defaultRatios, driveMode = DriveMode.SPORT)
        assertEquals("IDLE", idleGear)
        assertNotEquals("P", idleGear)
        assertNotEquals("N", idleGear)

        // Moving in 3rd gear (e.g. 2300 RPM at 50 km/h -> ratio 46)
        val thirdGear = repo.calculateGear(rpm = 2300, speedKmh = 50, ratios = defaultRatios, driveMode = DriveMode.SPORT)
        assertEquals("3", thirdGear)

        // Moving in 6th gear (e.g. 2200 RPM at 100 km/h -> ratio 22)
        val sixthGear = repo.calculateGear(rpm = 2200, speedKmh = 100, ratios = defaultRatios, driveMode = DriveMode.COMFORT)
        assertEquals("6", sixthGear)

        // Cleanup repository listeners
        repo.unregisterGpsListener()
        repo.unregisterNetworkMonitor()
    }

    @Test
    fun `idempotent gps registration and trip reset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = TelemetryRepository(context)

        // Calling registerGpsListener multiple times should be safe and idempotent
        repo.registerGpsListener()
        repo.registerGpsListener()

        repo.resetTrip()
        assertEquals(0f, repo.telemetry.value.tripDistanceKm, 0.01f)
        assertEquals(0L, repo.telemetry.value.tripDurationSec)

        repo.unregisterGpsListener()
        repo.unregisterNetworkMonitor()
    }

    @Test
    fun `carplay and android auto shortcuts present in apps repository`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = AppsRepository(context)
        val apps = repo.getInstalledApps()
        val hasCarPlay = apps.any { it.id == "carplay" || it.name.contains("CarPlay", ignoreCase = true) }
        val hasAndroidAuto = apps.any { it.id == "android_auto" || it.name.contains("Android Auto", ignoreCase = true) }
        assertTrue(hasCarPlay)
        assertTrue(hasAndroidAuto)
    }
}
