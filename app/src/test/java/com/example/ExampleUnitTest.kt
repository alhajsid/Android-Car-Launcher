package com.example

import com.example.data.obd.ObdProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testObdProtocolResponseCleaning() {
        val raw = "41 0C 1A F8\r\n>"
        assertEquals("410C1AF8", ObdProtocol.cleanResponse(raw))
    }

    @Test
    fun testParseRpmCalculation() {
        // 410C 1A F8 -> (0x1A * 256 + 0xF8) / 4 = (26 * 256 + 248) / 4 = (6656 + 248) / 4 = 1726 RPM
        val raw = "41 0C 1A F8\r>"
        val rpm = ObdProtocol.parseRpm(raw)
        assertEquals(1726, rpm)

        // Idle 800 RPM: 800 * 4 = 3200 = 0x0C80
        val idleRaw = "410C0C80>"
        assertEquals(800, ObdProtocol.parseRpm(idleRaw))
    }

    @Test
    fun testParseSpeedCalculation() {
        // 410D 5A -> 0x5A = 90 km/h
        val raw = "41 0D 5A>"
        val speed = ObdProtocol.parseSpeed(raw)
        assertEquals(90, speed)

        // 0 km/h
        assertEquals(0, ObdProtocol.parseSpeed("410D00>"))
    }

    @Test
    fun testParseCoolantCalculation() {
        // 4105 82 -> 0x82 = 130 -> 130 - 40 = 90 °C
        val raw = "41 05 82>"
        val temp = ObdProtocol.parseCoolant(raw)
        assertEquals(90, temp)

        // Cold engine 0 °C: 40 = 0x28
        assertEquals(0, ObdProtocol.parseCoolant("410528>"))
    }

    @Test
    fun testParseEngineLoadCalculation() {
        // 4104 46 -> 0x46 = 70 -> (70 * 100) / 255 = 27 %
        val raw = "41 04 46>"
        val load = ObdProtocol.parseEngineLoad(raw)
        assertEquals(27, load)
    }

    @Test
    fun testParseThrottleCalculation() {
        // 4111 80 -> 0x80 = 128 -> (128 * 100) / 255 = 50 %
        val raw = "41 11 80>"
        val tps = ObdProtocol.parseThrottle(raw)
        assertEquals(50, tps)
    }

    @Test
    fun testParseMafCalculation() {
        // 4110 05 78 -> (5 * 256 + 120) / 100.0 = 1400 / 100 = 14.00 g/s
        val raw = "41 10 05 78>"
        val maf = ObdProtocol.parseMaf(raw)
        assertNotNull(maf)
        assertEquals(14.0f, maf!!, 0.01f)
    }

    @Test
    fun testParseVoltageCalculation() {
        val raw = "14.2V>"
        val volt = ObdProtocol.parseVoltage(raw)
        assertNotNull(volt)
        assertEquals(14.2f, volt!!, 0.05f)
    }

    @Test
    fun testParseErrorHandling() {
        assertNull(ObdProtocol.parseRpm("NO DATA>"))
        assertNull(ObdProtocol.parseRpm("UNABLE TO CONNECT>"))
        assertNull(ObdProtocol.parseSpeed("SEARCHING..."))
        assertNull(ObdProtocol.parseCoolant("ERROR>"))
    }
}
