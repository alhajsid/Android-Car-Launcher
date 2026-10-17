package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppsRepository
import com.example.data.obd.ObdBluetoothConnectionManager
import com.example.data.obd.ObdProtocol
import com.example.model.DriveMode
import com.example.model.SpeedUnit
import com.example.model.TelemetryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    // RPM: ((0x1A * 256) + 0xF8) / 4 = ((26 * 256) + 248) / 4 = 6904 / 4 = 1726
    val parsedRpm = ObdProtocol.parseRpm("41 0C 1A F8\r>")
    assertEquals(1726, parsedRpm)

    // Speed: 0x5A = 90 km/h
    val parsedSpeed = ObdProtocol.parseSpeed("41 0D 5A\r>")
    assertEquals(90, parsedSpeed)

    // Coolant: 0x7B = 123 -> 123 - 40 = 83°C
    val parsedCoolant = ObdProtocol.parseCoolant("41 05 7B\r>")
    assertEquals(83, parsedCoolant)

    // Engine Load: 0x57 = 87 -> 87 * 100 / 255 = 34%
    val parsedLoad = ObdProtocol.parseEngineLoad("41 04 57\r>")
    assertEquals(34, parsedLoad)

    // Voltage: 14.2V -> 14.2f
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

    // Verifies isDefaultLauncher method runs cleanly without throwing
    val isDefault = repo.isDefaultLauncher()
    assertNotNull(isDefault)
  }

  @Test
  fun `obd bluetooth connection manager filters and identifiers`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val btManager = ObdBluetoothConnectionManager(context)

    assertTrue(btManager.isLikelyObdAdapter("OBDLink MX+ Bluetooth"))
    assertTrue(btManager.isLikelyObdAdapter("vLinker MC+"))
    assertTrue(btManager.isLikelyObdAdapter("Veepeak OBDCheck BLE"))
    assertTrue(btManager.isLikelyObdAdapter("ELM327 v1.5"))
    assertTrue(btManager.isLikelyObdAdapter("Torque OBD"))

    assertFalse(btManager.isLikelyObdAdapter("Sony WH-1000XM5"))
    assertFalse(btManager.isLikelyObdAdapter("Galaxy Buds Pro"))

    val devices = btManager.getPairedObdDevices()
    assertNotNull(devices)
  }
}
