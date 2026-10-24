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
    const val CMD_MIL_STATUS = "0101"
    const val CMD_READ_DTC = "03"
    const val CMD_CLEAR_DTC = "04"
    const val CMD_VOLTAGE = "ATRV"
    const val CMD_PROTOCOL = "ATDP"

    private val ERROR_TOKENS = listOf(
        "ERROR",
        "NODATA",
        "UNABLETOCONNECT",
        "CANERROR",
        "BUSINIT",
        "BUSERROR",
        "STOPPED",
        "FBERROR",
        "DATAERROR",
        "BUFFERFULL",
        "ACTALERT",
        "LVRESET",
        "TIMEOUT",
        "LOSTCONNECTION",
        "NOTCONNECTED",
        "DISCONNECTED"
    )

    private val VOLTAGE_REGEX = Regex("""([0-9]+\.?[0-9]*)""")

    /**
     * Cleans raw ELM327 response:
     * Removes prompt character '>', carriage returns, line feeds, spaces, null bytes.
     */
    fun cleanResponse(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (i in raw.indices) {
            val ch = raw[i]
            if (ch != '>' && ch != '\r' && ch != '\n' && ch != ' ' && ch != '\u0000' && !ch.isWhitespace()) {
                sb.append(ch.uppercaseChar())
            }
        }
        return sb.toString()
    }

    /**
     * Checks whether the raw response indicates an ELM327 / ECU error, timeout, or NO DATA state.
     */
    fun isErrorOrNoData(raw: String): Boolean {
        val clean = cleanResponse(raw)
        if (clean.isEmpty() || clean == "?") return true
        return ERROR_TOKENS.any { clean.contains(it) }
    }

    /**
     * Checks specifically whether the ECU reported NO DATA or unsupported PID for a query.
     */
    fun isNoDataResponse(raw: String): Boolean {
        val clean = cleanResponse(raw)
        return clean.contains("NODATA") || clean == "?"
    }

    /**
     * Validates the response to OBD Mode 04 (Clear/Reset emission-related diagnostic information).
     * Requires a positive Mode 04 response frame ("44") and absence of adapter/ECU error tokens.
     * Never treats empty strings, timeouts, "NO DATA", or "?" as success.
     */
    fun isClearDtcSuccess(raw: String): Boolean {
        val clean = cleanResponse(raw)
        if (clean.isEmpty() || clean == "?") return false
        if (ERROR_TOKENS.any { clean.contains(it) }) return false

        // Remove optional echoed command prefix "04" if echo was still active
        val withoutEcho = if (clean.startsWith("0444")) clean.removePrefix("04") else clean

        // Positive response SID for Mode 0x04 is 0x44
        val idx = withoutEcho.indexOf("44")
        if (idx == -1) return false

        val after = withoutEcho.substring(idx + 2)
        return after.all { it in '0'..'9' || it in 'A'..'F' }
    }

    /**
     * Parses PID 0100 bitmask to determine which Mode 01 PIDs (0101..0120) the ECU supports.
     * Returns empty set if response could not be parsed (caller should fall back to probing).
     */
    fun parseSupportedPids0100(raw: String): Set<String> {
        val clean = cleanResponse(raw)
        val payload = extractPayload(clean, "0100") ?: return emptySet()
        if (payload.length < 8) return emptySet()
        val mask = payload.substring(0, 8).toLongOrNull(16) ?: return emptySet()
        val supported = mutableSetOf<String>()
        for (bit in 0 until 32) {
            val isSet = (mask and (1L shl (31 - bit))) != 0L
            if (isSet) {
                val pidNum = bit + 1
                supported.add(String.format(Locale.US, "01%02X", pidNum))
            }
        }
        return supported
    }

    /**
     * Extracts hex data payload immediately following the mode echo.
     * E.g. For PID "010C", response contains "410C". The payload is the hex characters following "410C".
     * Returns null if response does not contain the expected positive mode 01 response prefix.
     */
    fun extractPayload(cleanedResponse: String, expectedPid: String): String? {
        if (cleanedResponse.isEmpty() || ERROR_TOKENS.any { cleanedResponse.contains(it) }) {
            return null
        }
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
     * Parses raw 010B Intake Manifold Absolute Pressure (MAP) in kPa (0..255 kPa).
     */
    fun parseMapKpa(response: String): Int? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_MAP_BOOST) ?: return null
        if (payload.length >= 2) {
            val mapKpa = payload.substring(0, 2).toIntOrNull(16) ?: return null
            return if (mapKpa in 0..255) mapKpa else null
        }
        return null
    }

    /**
     * Parses 010B Manifold Absolute Pressure (MAP) in kPa and calculates signed
     * estimated gauge pressure (boost / vacuum) in bar:
     * Formula = (MAP - baroKpa) / 100 bar.
     * Negative values represent manifold vacuum (bar), positive values represent boost (bar).
     */
    fun parseBoost(response: String, baroKpa: Float = 101.3f): Float? {
        val mapKpa = parseMapKpa(response) ?: return null
        val gaugeBar = (mapKpa - baroKpa) / 100f
        return gaugeBar.coerceIn(-1.05f, 3.0f)
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
        if (clean.isEmpty()) return null
        val match = VOLTAGE_REGEX.find(clean)
        val volt = match?.groupValues?.get(1)?.toFloatOrNull()
        return if (volt != null && volt in 5f..32f) volt else null
    }

    /**
     * Parses PID 0101: Returns Pair(isMilActive: Boolean, dtcCount: Int)
     */
    fun parseMilStatus(response: String): Pair<Boolean, Int>? {
        val clean = cleanResponse(response)
        val payload = extractPayload(clean, CMD_MIL_STATUS) ?: return null
        if (payload.length >= 2) {
            val a = payload.substring(0, 2).toIntOrNull(16) ?: return null
            val isMilOn = (a and 0x80) != 0
            val count = a and 0x7F
            return Pair(isMilOn, count)
        }
        return null
    }

    /**
     * Parses Mode 03 diagnostic trouble codes response: e.g. "43 01 33 00 00 00 00" -> ["P0133"]
     */
    fun parseDtcCodes(response: String): List<String> {
        val clean = cleanResponse(response)
        if (clean.isEmpty() || ERROR_TOKENS.any { clean.contains(it) }) return emptyList()
        val dtcList = mutableListOf<String>()
        val index = clean.indexOf("43")
        if (index == -1) return emptyList()

        val payload = clean.substring(index + 2).takeWhile { it in '0'..'9' || it in 'A'..'F' }
        var i = 0
        while (i + 4 <= payload.length) {
            val hex = payload.substring(i, i + 4)
            i += 4
            if (hex == "0000") continue

            val b1 = hex.substring(0, 2).toIntOrNull(16) ?: continue
            val b2 = hex.substring(2, 4).toIntOrNull(16) ?: continue

            val prefix = when ((b1 and 0xC0) shr 6) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                3 -> "U"
                else -> "P"
            }
            val digit2 = (b1 and 0x30) shr 4
            val digit3 = (b1 and 0x0F).toString(16).uppercase(Locale.US)
            val digit4_5 = String.format(Locale.US, "%02X", b2)

            val code = "$prefix$digit2$digit3$digit4_5"
            if (!dtcList.contains(code)) {
                dtcList.add(code)
            }
        }
        return dtcList
    }
}

data class ParsedObdMetrics(
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val engineLoadPercent: Int? = null,
    val throttlePercent: Int? = null,
    val intakeTempC: Int? = null,
    val mafGs: Float? = null,
    val mapKpa: Int? = null,
    val turboBoostBar: Float? = null,
    val fuelLevelPercent: Int? = null,
    val voltage: Float? = null,
    val protocolName: String? = null,
    val lastUpdatedMs: Long = 0L,
    val supportedPidsCount: Int = 0,
    val pollLatencyMs: Long = 0L
)
