package com.example

import android.Manifest
import android.os.Build
import com.example.data.obd.DtcClearResult
import com.example.data.obd.DtcClearStatus
import com.example.data.obd.ObdProtocol
import com.example.data.obd.ObdTransport
import com.example.data.weather.WeatherRepository
import com.example.ui.screens.areBluetoothScanPermissionsGranted
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ExampleUnitTest {

    @Test
    fun testObdProtocolResponseCleaning() {
        val raw = "41 0C 1A F8\r\n>"
        assertEquals("410C1AF8", ObdProtocol.cleanResponse(raw))
    }

    @Test
    fun testParseRpmCalculation() {
        val raw = "41 0C 1A F8\r>"
        val rpm = ObdProtocol.parseRpm(raw)
        assertEquals(1726, rpm)

        val idleRaw = "410C0C80>"
        assertEquals(800, ObdProtocol.parseRpm(idleRaw))
    }

    @Test
    fun testParseSpeedCalculation() {
        val raw = "41 0D 5A>"
        val speed = ObdProtocol.parseSpeed(raw)
        assertEquals(90, speed)

        assertEquals(0, ObdProtocol.parseSpeed("410D00>"))
    }

    @Test
    fun testParseCoolantCalculation() {
        val raw = "41 05 82>"
        val temp = ObdProtocol.parseCoolant(raw)
        assertEquals(90, temp)

        assertEquals(0, ObdProtocol.parseCoolant("410528>"))
    }

    @Test
    fun testParseEngineLoadAndThrottleCalculation() {
        assertEquals(27, ObdProtocol.parseEngineLoad("41 04 46>"))
        assertEquals(50, ObdProtocol.parseThrottle("41 11 80>"))
    }

    @Test
    fun testParseMafAndVoltageCalculation() {
        val maf = ObdProtocol.parseMaf("41 10 05 78>")
        assertNotNull(maf)
        assertEquals(14.0f, maf!!, 0.01f)

        val volt = ObdProtocol.parseVoltage("14.2V>")
        assertNotNull(volt)
        assertEquals(14.2f, volt!!, 0.05f)
    }

    @Test
    fun testParseMapKpaAndSignedBoostOrVacuum() {
        // 1. Positive Turbo Boost: 0xAA = 170 kPa -> (170 - 101.3) / 100 = +0.687 bar
        val boostRaw = "41 0B AA>"
        assertEquals(170, ObdProtocol.parseMapKpa(boostRaw))
        val boostBar = ObdProtocol.parseBoost(boostRaw)
        assertNotNull(boostBar)
        assertEquals(0.687f, boostBar!!, 0.01f)

        // 2. Manifold Vacuum at Idle: 0x23 = 35 kPa -> (35 - 101.3) / 100 = -0.663 bar
        val vacuumRaw = "41 0B 23>"
        assertEquals(35, ObdProtocol.parseMapKpa(vacuumRaw))
        val vacuumBar = ObdProtocol.parseBoost(vacuumRaw)
        assertNotNull(vacuumBar)
        assertTrue("Expected negative manifold vacuum bar, got $vacuumBar", vacuumBar!! < -0.60f)
        assertEquals(-0.663f, vacuumBar, 0.01f)
    }

    @Test
    fun testClearDtcMode04ValidationAndDtcClearResultContract() {
        // Valid Mode 04 positive acknowledgement (SID 0x44)
        assertTrue(ObdProtocol.isClearDtcSuccess("44\r>"))
        assertTrue(ObdProtocol.isClearDtcSuccess("44 00\r\n>"))
        assertTrue(ObdProtocol.isClearDtcSuccess("04\r44\r>"))

        // Invalid / rejected / empty / timeout / error responses must NEVER be reported as success
        assertFalse(ObdProtocol.isClearDtcSuccess(""))
        assertFalse(ObdProtocol.isClearDtcSuccess("   >"))
        assertFalse(ObdProtocol.isClearDtcSuccess("?"))
        assertFalse(ObdProtocol.isClearDtcSuccess("NO DATA>"))
        assertFalse(ObdProtocol.isClearDtcSuccess("ERROR>"))
        assertFalse(ObdProtocol.isClearDtcSuccess("ERROR: TIMEOUT"))
        assertFalse(ObdProtocol.isClearDtcSuccess("ERROR: NOT CONNECTED"))
        assertFalse(ObdProtocol.isClearDtcSuccess("UNABLE TO CONNECT>"))
        assertFalse(ObdProtocol.isClearDtcSuccess("CAN ERROR>"))
        assertFalse(ObdProtocol.isClearDtcSuccess("STOPPED>"))

        // Verify DtcClearResult distinguishes commandAccepted from verifiedClear
        val acceptedButCodesRemain = DtcClearResult(
            status = DtcClearStatus.ACCEPTED_CODES_REMAIN,
            commandAccepted = true,
            verifiedClear = false,
            remainingCodes = listOf("P0420"),
            isMilStillActive = true,
            message = "Mode 04 accepted, but 1 code remains."
        )
        assertTrue(acceptedButCodesRemain.commandAccepted)
        assertFalse(acceptedButCodesRemain.verifiedClear)
        assertEquals(listOf("P0420"), acceptedButCodesRemain.remainingCodes)

        val verifiedCleared = DtcClearResult(
            status = DtcClearStatus.VERIFIED_CLEARED,
            commandAccepted = true,
            verifiedClear = true,
            remainingCodes = emptyList(),
            isMilStillActive = false,
            message = "Mode 04 accepted & verified."
        )
        assertTrue(verifiedCleared.commandAccepted)
        assertTrue(verifiedCleared.verifiedClear)
    }

    @Test
    fun testBluetoothScanPermissionGrantValidation() {
        // Android 12+ (API 31+): requires BOTH BLUETOOTH_CONNECT and BLUETOOTH_SCAN
        assertFalse(areBluetoothScanPermissionsGranted(emptyMap(), Build.VERSION_CODES.S))
        assertFalse(
            areBluetoothScanPermissionsGranted(
                mapOf(
                    Manifest.permission.BLUETOOTH_CONNECT to true,
                    Manifest.permission.BLUETOOTH_SCAN to false
                ),
                Build.VERSION_CODES.S
            )
        )
        assertFalse(
            areBluetoothScanPermissionsGranted(
                mapOf(
                    Manifest.permission.BLUETOOTH_CONNECT to false,
                    Manifest.permission.BLUETOOTH_SCAN to false
                ),
                Build.VERSION_CODES.S
            )
        )
        assertTrue(
            areBluetoothScanPermissionsGranted(
                mapOf(
                    Manifest.permission.BLUETOOTH_CONNECT to true,
                    Manifest.permission.BLUETOOTH_SCAN to true
                ),
                Build.VERSION_CODES.S
            )
        )

        // Android 11 and below (API 30): requires location permission
        assertFalse(
            areBluetoothScanPermissionsGranted(
                mapOf(Manifest.permission.ACCESS_FINE_LOCATION to false),
                Build.VERSION_CODES.R
            )
        )
        assertTrue(
            areBluetoothScanPermissionsGranted(
                mapOf(Manifest.permission.ACCESS_FINE_LOCATION to true),
                Build.VERSION_CODES.R
            )
        )
    }

    @Test
    fun testParseSupportedPids0100Bitmask() {
        val raw = "41 00 BE 3E B8 11>"
        val supported = ObdProtocol.parseSupportedPids0100(raw)
        assertTrue(supported.isNotEmpty())
        assertTrue(supported.contains("0101"))
        assertTrue(supported.contains("0104"))
        assertTrue(supported.contains("0105"))
        assertTrue(supported.contains("010C"))
        assertTrue(supported.contains("010D"))
    }

    @Test
    fun testParseDtcTroubleCodes() {
        val raw = "43 01 33 03 00 00 00>"
        val dtcs = ObdProtocol.parseDtcCodes(raw)
        assertEquals(2, dtcs.size)
        assertEquals("P0133", dtcs[0])
        assertEquals("P0300", dtcs[1])

        val emptyRaw = "43 00 00 00 00 00 00>"
        assertEquals(0, ObdProtocol.parseDtcCodes(emptyRaw).size)
        assertEquals(0, ObdProtocol.parseDtcCodes("NO DATA>").size)
    }

    @Test
    fun testParseMilStatus() {
        val milOn = ObdProtocol.parseMilStatus("41 01 82 07 E5 00>")
        assertNotNull(milOn)
        assertEquals(true, milOn?.first)
        assertEquals(2, milOn?.second)

        val milOff = ObdProtocol.parseMilStatus("41 01 00 07 E5 00>")
        assertNotNull(milOff)
        assertEquals(false, milOff?.first)
        assertEquals(0, milOff?.second)
    }

    @Test
    fun testParseErrorHandling() {
        assertNull(ObdProtocol.parseRpm("NO DATA>"))
        assertNull(ObdProtocol.parseRpm("UNABLE TO CONNECT>"))
        assertNull(ObdProtocol.parseSpeed("SEARCHING..."))
        assertNull(ObdProtocol.parseCoolant("ERROR>"))
        assertTrue(ObdProtocol.isNoDataResponse("NO DATA\r>"))
        assertTrue(ObdProtocol.isNoDataResponse("?\r>"))
    }

    @Test
    fun testObdCommandSerializationUnderConcurrency() = runBlocking {
        val mutex = Mutex()
        val activeInFlight = AtomicInteger(0)
        val maxObservedConcurrency = AtomicInteger(0)

        val fakeTransport = object : ObdTransport {
            override var isConnected: Boolean = true
            override val description: String = "FakeSerializedTransport"
            override fun connect(): Boolean = true
            override fun disconnect() { isConnected = false }

            override suspend fun executeCommand(cmd: String, timeoutMs: Long): String = mutex.withLock {
                val current = activeInFlight.incrementAndGet()
                maxObservedConcurrency.updateAndGet { maxOf(it, current) }
                delay(15)
                activeInFlight.decrementAndGet()
                when (cmd) {
                    "010C" -> "410C0C80>"
                    "010D" -> "410D5A>"
                    "03" -> "4301330000>"
                    "04" -> "44>"
                    else -> "OK>"
                }
            }
        }

        val commands = listOf("010C", "010D", "03", "010C", "04", "010D")
        val results = commands.map { cmd ->
            async { fakeTransport.executeCommand(cmd, 500) }
        }.awaitAll()

        assertEquals(1, maxObservedConcurrency.get())
        assertEquals(800, ObdProtocol.parseRpm(results[0]))
        assertEquals(90, ObdProtocol.parseSpeed(results[1]))
        assertEquals(listOf("P0133"), ObdProtocol.parseDtcCodes(results[2]))
        assertTrue(ObdProtocol.isClearDtcSuccess(results[4]))
    }

    @Test
    fun testWeatherSpatialDistanceAndThrottling() {
        val repo = WeatherRepository(
            cacheExpiryMs = 15 * 60 * 1000L,
            minDistanceDisplacementMeters = 2_500.0
        )
        assertTrue(repo.shouldFetchWeather(48.1351, 11.5820, nowMs = 100_000L))

        val distSmall = WeatherRepository.calculateDistanceMeters(
            48.137154, 11.576124,
            48.137500, 11.576500
        )
        assertTrue("Expected < 200m, got $distSmall", distSmall < 200.0)

        val distLarge = WeatherRepository.calculateDistanceMeters(
            48.1351, 11.5820,
            52.5200, 13.4050
        )
        assertTrue("Expected > 400km, got $distLarge", distLarge > 400_000.0)
    }
}
