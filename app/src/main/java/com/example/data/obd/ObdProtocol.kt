package com.example.data.obd

import java.util.Locale

object ObdProtocol {
    // Standard ELM327 Initialization Sequence
    val INIT_COMMANDS = listOf(
        "ATZ",    // Reset ELM327 chip
        "ATE0",   // Echo off
        "ATL0",   // Linefeeds off
        "ATS0",   // Spaces off
        "ATH0",   // Headers off
        "ATAT1",  // Adaptive timing 1 (balanced)
        "ATSP0",  // Protocol set to auto (scan CAN / ISO)
        "0100"    // Trigger ECU bus search and probe supported PIDs
    )

    // Standard Polling PIDs (Mode 01)
    const val CMD_RPM = "010C"
    const val CMD_SPEED = "010D"
    const val CMD_COOLANT = "0105"
    const val CMD_ENGINE_LOAD = "0104"
    const val CMD_THROTTLE = "0111"
    const val CMD_INTAKE_TEMP = "010F"
    const val CMD_MAF = "0110"
    const val CMD_MAP_BOOST = "010B"
    const val CMD_FUEL_LEVEL = "012F"
    const val CMD_VOLTAGE = "ATRV"
    const val CMD_PROTOCOL = "ATDP"

    /**
     * Cleans raw ELM327 response:
     * Removes prompt character '>', carriage returns, line feeds, spaces, null bytes.
     */
    fun cleanResponse(raw: String): String {
        return raw.replace(">", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(" ", "")
            .replace("\u0000", "")
            .trim()
            .uppercase(Locale.US)
    }

    /**
     * Extracts hex data payload immediately following the mode echo.
     * E.g. For PID "010C", response contains "410C". The payload is the hex characters following "410C".
     * Returns null if response does not contain the expected positive mode 01 response prefix.
     */
    fun extractPayload(cleanedResponse: String, expectedPid: String): String? {
        val pidSuffix = expectedPid.removePrefix("01").uppercase(Locale.US)
        val expectedPrefix = "41$pidSuffix"
        val index = cleanedResponse.indexOf(expectedPrefix)
        if (index != -1) {
            val afterPrefix = cleanedResponse.substring(index + expectedPrefix.length)
            // Extract only continuous valid hex digits
            val hexPayload = afterPrefix.takeWhile { it in '0'..'9' || it in 'A'..'F' }
            return hexPayload.ifEmpty { null }
        }
        return null
    }

    /**
     * Parses 010C Engine RPM: Formula = ((A * 256) + B) / 4 (rpm)
     */
    fun parseRpm(response: String): Int? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_RPM) ?: return null
        if (payload.length >= 4) {
            val a = payload.substring(0, 2).toIntOrNull(16) ?: return null
            val b = payload.substring(2, 4).toIntOrNull(16) ?: return null
            val rpm = ((a * 256) + b) / 4
            return if (rpm in 0..18000) rpm else null
        }
        return null
    }

    /**
     * Parses 010D Vehicle Speed: Formula = A (km/h)
     */
    fun parseSpeed(response: String): Int? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_SPEED) ?: return null
        if (payload.length >= 2) {
            val speed = payload.substring(0, 2).toIntOrNull(16) ?: return null
            return if (speed in 0..350) speed else null
        }
        return null
    }

    /**
     * Parses 0105 Engine Coolant Temperature: Formula = A - 40 (°C)
     */
    fun parseCoolant(response: String): Int? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_COOLANT) ?: return null
        if (payload.length >= 2) {
            val a = payload.substring(0, 2).toIntOrNull(16) ?: return null
            val temp = a - 40
            return if (temp in -40..215) temp else null
        }
        return null
    }

    /**
     * Parses 0104 Calculated Engine Load: Formula = (A * 100) / 255 (%)
     */
    fun parseEngineLoad(response: String): Int? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_ENGINE_LOAD) ?: return null
        if (payload.length >= 2) {
            val a = payload.substring(0, 2).toIntOrNull(16) ?: return null
            return ((a * 100) / 255).coerceIn(0, 100)
        }
        return null
    }

    /**
     * Parses 0111 Throttle Position: Formula = (A * 100) / 255 (%)
     */
    fun parseThrottle(response: String): Int? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_THROTTLE) ?: return null
        if (payload.length >= 2) {
            val a = payload.substring(0, 2).toIntOrNull(16) ?: return null
            return ((a * 100) / 255).coerceIn(0, 100)
        }
        return null
    }

    /**
     * Parses 010F Intake Air Temp: Formula = A - 40 (°C)
     */
    fun parseIat(response: String): Int? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_INTAKE_TEMP) ?: return null
        if (payload.length >= 2) {
            val a = payload.substring(0, 2).toIntOrNull(16) ?: return null
            val temp = a - 40
            return if (temp in -40..215) temp else null
        }
        return null
    }

    /**
     * Parses 0110 MAF Air Flow Rate: Formula = ((A * 256) + B) / 100 (g/s)
     */
    fun parseMaf(response: String): Float? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_MAF) ?: return null
        if (payload.length >= 4) {
            val a = payload.substring(0, 2).toIntOrNull(16) ?: return null
            val b = payload.substring(2, 4).toIntOrNull(16) ?: return null
            val maf = ((a * 256) + b) / 100f
            return if (maf in 0f..655f) maf else null
        }
        return null
    }

    /**
     * Parses 010B Manifold Pressure (MAP) in kPa and calculates gauge boost (bar):
     * Formula = (MAP - 101.3 kPa atmospheric) / 100 bar
     */
    fun parseBoost(response: String): Float? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_MAP_BOOST) ?: return null
        if (payload.length >= 2) {
            val mapKpa = payload.substring(0, 2).toIntOrNull(16) ?: return null
            val boostBar = (mapKpa - 101.3f) / 100f
            return boostBar.coerceAtLeast(0f)
        }
        return null
    }

    /**
     * Parses 012F Fuel Tank Level: Formula = (A * 100) / 255 (%)
     */
    fun parseFuelLevel(response: String): Int? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_FUEL_LEVEL) ?: return null
        if (payload.length >= 2) {
            val a = payload.substring(0, 2).toIntOrNull(16) ?: return null
            return ((a * 100) / 255).coerceIn(0, 100)
        }
        return null
    }

    /**
     * Parses ATRV battery/alternator voltage: e.g. "14.2V" -> 14.2f
     */
    fun parseVoltage(response: String): Float? {
        val clean = response.replace(">", "").trim()
        val regex = Regex("""([0-9]+\.?[0-9]*)""")
        val match = regex.find(clean)
        val volt = match?.groupValues?.get(1)?.toFloatOrNull()
        return if (volt != null && volt in 5f..32f) volt else null
    }
}
