package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.example.data.media.VisionMediaNotificationListenerService
import com.example.data.obd.ObdTelemetryService
import com.example.data.obd.ParsedObdMetrics
import com.example.data.obd.RealObdManager
import com.example.data.weather.WeatherRepository
import com.example.model.DriveMode
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import com.example.model.TelemetryData
import com.example.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class TelemetryRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val settingsDataStore = SettingsDataStore(context)
    private val weatherRepository = WeatherRepository()

    val realObdManager = RealObdManager(context)

    private val _settings = MutableStateFlow(UserSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private val _telemetry = MutableStateFlow(
        TelemetryData(
            speed = 0,
            rpm = 0,
            currentGear = "-",
            isGearEstimated = true,
            coolantTemp = 0,
            engLoadPercent = 0,
            intakeAirTemp = 0,
            throttlePercent = 0,
            turboBoostBar = 0f,
            mafFlowGs = 0f,
            fuelLevelPercent = null,
            remainingRangeKm = null,
            instantFuelL100km = null,
            avgFuelL100km = null,
            alternatorVoltage = null,
            isObdConnected = false,
            obdAdapterName = "Disconnected",
            obdProtocol = "Standby",
            gpsSpeed = 0,
            hasGpsFix = false,
            gpsSignalLevel = 0,
            cellularSignalLevel = 0,
            networkLabel = "OFFLINE"
        )
    )
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    val detectedAdapters: StateFlow<List<ObdAdapterDevice>> = realObdManager.discoveredDevices
    val obdStatus: StateFlow<ObdConnectionStatus> = realObdManager.connectionStatus
    val diagnosticLogs: StateFlow<List<String>> = realObdManager.diagnosticLogs

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Cumulative trip tracking
    private var totalTripMeters = 0.0
    private var totalFuelGrams = 0.0
    private var tripStartTimestamp = System.currentTimeMillis()

    private var gpsLocationListener: LocationListener? = null
    private var isGpsRegistered = false

    init {
        // Load persistent settings from DataStore
        scope.launch {
            settingsDataStore.userSettingsFlow.collect { loadedSettings ->
                _settings.value = loadedSettings
                _telemetry.update { current ->
                    current.copy(
                        redlineRpm = loadedSettings.redlineRpm,
                        coolantThreshold = loadedSettings.coolantAlertThreshold,
                        isOverheating = (current.coolantTemp >= loadedSettings.coolantAlertThreshold) || loadedSettings.testOverheatAlert
                    )
                }
            }
        }

        startObdServiceIfPermitted()

        observeObdServiceMetrics()
        observeMediaSessions()
        startTripTicker()
        startNetworkMonitor()
        registerGpsListener()
    }

    private fun observeObdServiceMetrics() {
        scope.launch {
            while (isActive) {
                val service = ObdTelemetryService.instance
                if (service != null) {
                    launch {
                        service.telemetryState.collect { metrics ->
                            val isConnected = service.connectionStatus.value == ObdConnectionStatus.CONNECTED
                            if (isConnected) {
                                val currentSettings = _settings.value
                                val spd = metrics.speedKmh ?: _telemetry.value.speed
                                val rpm = metrics.rpm ?: _telemetry.value.rpm
                                val calculatedGear = calculateGear(rpm, spd, currentSettings.gearRatios, _telemetry.value.driveMode)
                                val coolant = metrics.coolantTempC ?: _telemetry.value.coolantTemp
                                val isOverheat = (coolant >= currentSettings.coolantAlertThreshold) || currentSettings.testOverheatAlert

                                // Real MAF instantaneous fuel calculation (L/100km)
                                val instantFuel = if (metrics.mafGs != null && spd > 3) {
                                    ((metrics.mafGs * 32.65f) / spd).coerceIn(1.2f, 40f)
                                } else {
                                    null
                                }

                                // Overspeed alert based on user setting
                                val overspeed = currentSettings.speedLimitAlertKmh > 0 && spd > currentSettings.speedLimitAlertKmh

                                _telemetry.update { current ->
                                    current.copy(
                                        isObdConnected = true,
                                        obdAdapterName = service.connectedDeviceName.value,
                                        obdProtocol = metrics.protocolName ?: current.obdProtocol,
                                        speed = spd,
                                        rpm = rpm,
                                        currentGear = calculatedGear,
                                        isGearEstimated = true,
                                        coolantTemp = coolant,
                                        coolantThreshold = currentSettings.coolantAlertThreshold,
                                        isOverheating = isOverheat,
                                        engLoadPercent = metrics.engineLoadPercent ?: current.engLoadPercent,
                                        throttlePercent = metrics.throttlePercent ?: current.throttlePercent,
                                        intakeAirTemp = metrics.intakeTempC ?: current.intakeAirTemp,
                                        mafFlowGs = metrics.mafGs ?: current.mafFlowGs,
                                        turboBoostBar = metrics.turboBoostBar ?: current.turboBoostBar,
                                        fuelLevelPercent = metrics.fuelLevelPercent,
                                        remainingRangeKm = null,
                                        instantFuelL100km = instantFuel,
                                        avgFuelL100km = computeAverageFuel(),
                                        alternatorVoltage = metrics.voltage,
                                        overspeedAlert = overspeed,
                                        dtcCodes = service.dtcCodes.value,
                                        isMilActive = service.isMilActive.value
                                    )
                                }
                            } else if (!_settings.value.isSimulatingTelemetry) {
                                // OBD disconnected: collapse to zero/null state
                                _telemetry.update { current ->
                                    val spd = if (current.gpsSpeed > 0) current.gpsSpeed else 0
                                    val overspeed = _settings.value.speedLimitAlertKmh > 0 && spd > _settings.value.speedLimitAlertKmh
                                    current.copy(
                                        isObdConnected = false,
                                        obdAdapterName = "Disconnected",
                                        obdProtocol = "Standby",
                                        rpm = 0,
                                        currentGear = "-",
                                        coolantTemp = 0,
                                        isOverheating = _settings.value.testOverheatAlert,
                                        engLoadPercent = 0,
                                        throttlePercent = 0,
                                        turboBoostBar = 0f,
                                        mafFlowGs = 0f,
                                        intakeAirTemp = 0,
                                        fuelLevelPercent = null,
                                        remainingRangeKm = null,
                                        instantFuelL100km = null,
                                        avgFuelL100km = null,
                                        alternatorVoltage = null,
                                        speed = spd,
                                        overspeedAlert = overspeed,
                                        dtcCodes = emptyList(),
                                        isMilActive = false
                                    )
                                }
                            }
                        }
                    }
                    break
                }
                delay(800)
            }
        }
    }

    private fun observeMediaSessions() {
        scope.launch {
            VisionMediaNotificationListenerService.mediaState.collect { media ->
                _telemetry.update { current ->
                    current.copy(
                        hasRealMediaSession = media.hasActivePlayer,
                        songTitle = if (media.hasActivePlayer) media.title else null,
                        songArtist = if (media.hasActivePlayer) media.artist else null,
                        isMediaPlaying = media.isPlaying,
                        mediaProgress = media.progress
                    )
                }
            }
        }
    }

    /**
     * Calculates estimated gear ratio using calibrated vehicle ratios
     */
    fun calculateGear(rpm: Int, speedKmh: Int, ratios: Map<Int, Float>, driveMode: DriveMode): String {
        if (speedKmh <= 2) {
            return if (rpm < 500) "P" else "N"
        }
        val currentRatio = rpm.toFloat() / speedKmh.coerceAtLeast(1)

        // Match against calibrated ratios
        var bestGear: Int? = null
        var minDiff = Float.MAX_VALUE
        ratios.forEach { (gear, expectedRatio) ->
            val diff = Math.abs(currentRatio - expectedRatio)
            if (diff < minDiff && diff < (expectedRatio * 0.22f)) {
                minDiff = diff
                bestGear = gear
            }
        }

        return bestGear?.toString() ?: if (driveMode == DriveMode.SPORT) "S" else "D"
    }

    private fun computeAverageFuel(): Float? {
        if (totalTripMeters < 1000 || totalFuelGrams <= 0) return null
        val distanceKm = totalTripMeters / 1000.0
        val fuelLiters = totalFuelGrams / 750.0 // density of petrol ~ 750 g/L
        val avg = (fuelLiters / distanceKm) * 100.0
        return if (avg in 1.5..45.0) Math.round(avg * 10f) / 10f else null
    }

    private fun startTripTicker() {
        scope.launch {
            while (isActive) {
                delay(1000)
                val current = _telemetry.value
                val userSet = _settings.value

                // If simulation is explicitly enabled by the user in settings
                if (userSet.isSimulatingTelemetry && !current.isObdConnected) {
                    val tick = (System.currentTimeMillis() / 250).toInt()
                    val targetSpeed = 85 + (Math.sin(tick * 0.08) * 8).toInt()
                    val targetRpm = (2800 + Math.sin(tick * 0.1) * 350).toInt()
                    _telemetry.update {
                        it.copy(
                            speed = targetSpeed,
                            gpsSpeed = targetSpeed,
                            rpm = targetRpm,
                            currentGear = "4",
                            coolantTemp = if (userSet.testOverheatAlert) 116 else 92,
                            isOverheating = userSet.testOverheatAlert,
                            engLoadPercent = 42,
                            throttlePercent = 38,
                            turboBoostBar = 0.4f,
                            mafFlowGs = 35f,
                            instantFuelL100km = 6.8f
                        )
                    }
                }

                // Update Trip stats
                val speed = _telemetry.value.speed
                if (speed > 0) {
                    val metersDelta = (speed * 1000.0) / 3600.0
                    totalTripMeters += metersDelta

                    val maf = _telemetry.value.mafFlowGs
                    if (maf > 0) {
                        val fuelPerSec = maf / 14.7 // stoichiometric ratio
                        totalFuelGrams += fuelPerSec
                    }
                }

                val elapsedSec = (System.currentTimeMillis() - tripStartTimestamp) / 1000L
                _telemetry.update {
                    it.copy(
                        tripDistanceKm = Math.round((totalTripMeters / 1000.0) * 10f) / 10f,
                        tripDurationSec = elapsedSec
                    )
                }
            }
        }
    }

    private fun startNetworkMonitor() {
        scope.launch {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            while (isActive) {
                try {
                    val activeNetwork = cm?.activeNetwork
                    val caps = cm?.getNetworkCapabilities(activeNetwork)
                    if (caps != null) {
                        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        val label = when {
                            isWifi -> "WIFI"
                            isCell -> "5G/LTE"
                            else -> "CONNECTED"
                        }
                        _telemetry.update { it.copy(cellularSignalLevel = 4, networkLabel = label) }
                    } else {
                        _telemetry.update { it.copy(cellularSignalLevel = 0, networkLabel = "NO SERVICE") }
                    }
                } catch (_: Exception) {}
                delay(5000)
            }
        }
    }

    /**
     * Registers real GPS Location listener with accuracy tracking and weather lookup
     */
    @SuppressLint("MissingPermission")
    fun registerGpsListener() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return
            }

            if (gpsLocationListener != null && isGpsRegistered) {
                lm.removeUpdates(gpsLocationListener!!)
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0
                    val accuracy = if (loc.hasAccuracy()) loc.accuracy else null

                    val signalLevel = when {
                        accuracy == null -> 1
                        accuracy <= 6f -> 4
                        accuracy <= 14f -> 3
                        accuracy <= 30f -> 2
                        else -> 1
                    }

                    _telemetry.update { current ->
                        val activeSpeed = if (!current.isObdConnected) speedKmh else current.speed
                        val overspeed = _settings.value.speedLimitAlertKmh > 0 && activeSpeed > _settings.value.speedLimitAlertKmh
                        current.copy(
                            gpsSpeed = speedKmh,
                            speed = activeSpeed,
                            hasGpsFix = true,
                            gpsAccuracyMeters = accuracy,
                            gpsSignalLevel = signalLevel,
                            overspeedAlert = overspeed
                        )
                    }

                    // Fetch real current weather for current GPS coordinates
                    scope.launch {
                        val weather = weatherRepository.fetchWeatherForLocation(loc.latitude, loc.longitude)
                        if (weather != null && weather.isLoaded) {
                            _telemetry.update {
                                it.copy(
                                    weatherTempC = weather.temperatureC,
                                    weatherCondition = weather.condition
                                )
                            }
                        }
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            gpsLocationListener = listener
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, listener)
            // Also register NETWORK_PROVIDER as immediate coarse fix
            try {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 5.0f, listener)
            } catch (_: Exception) {}
            isGpsRegistered = true
        } catch (_: Exception) {}
    }

    fun unregisterGpsListener() {
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            gpsLocationListener?.let { lm?.removeUpdates(it) }
            isGpsRegistered = false
        } catch (_: Exception) {}
    }

    fun setDriveMode(mode: DriveMode) {
        _telemetry.update { it.copy(driveMode = mode) }
    }

    fun startObdServiceIfPermitted() {
        val hasBtPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        if (hasBtPermission) {
            try {
                val serviceIntent = Intent(context, ObdTelemetryService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                android.util.Log.e("TelemetryRepository", "Failed to start OBD service: ${e.message}")
            }
        }
    }

    fun connectToAdapter(adapterId: String) {
        startObdServiceIfPermitted()
        val target = detectedAdapters.value.find { it.id == adapterId } ?: return
        realObdManager.connect(target)
    }

    fun disconnectAdapter() {
        realObdManager.disconnect()
        _telemetry.update { current ->
            current.copy(
                isObdConnected = false,
                obdAdapterName = "Disconnected",
                obdProtocol = "Standby",
                rpm = 0,
                currentGear = "-",
                coolantTemp = 0,
                isOverheating = _settings.value.testOverheatAlert,
                engLoadPercent = 0,
                throttlePercent = 0,
                turboBoostBar = 0f,
                mafFlowGs = 0f,
                intakeAirTemp = 0,
                fuelLevelPercent = null,
                remainingRangeKm = null,
                instantFuelL100km = null,
                avgFuelL100km = null,
                alternatorVoltage = null,
                speed = if (current.gpsSpeed > 0) current.gpsSpeed else 0,
                dtcCodes = emptyList(),
                isMilActive = false
            )
        }
    }

    fun triggerScan() {
        scope.launch {
            _isScanning.value = true
            realObdManager.refreshDevices()
            ObdTelemetryService.instance?.connectionManager?.startBleScan()
            delay(1500)
            realObdManager.refreshDevices()
            _isScanning.value = false
        }
    }

    fun toggleMediaPlayback() {
        VisionMediaNotificationListenerService.togglePlayPause()
    }

    fun skipNextMedia() {
        VisionMediaNotificationListenerService.skipNext()
    }

    fun skipPrevMedia() {
        VisionMediaNotificationListenerService.skipPrevious()
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val newSettings = transform(_settings.value)
        _settings.value = newSettings
        scope.launch {
            settingsDataStore.saveSettings(newSettings)
        }
        _telemetry.update { current ->
            current.copy(
                redlineRpm = newSettings.redlineRpm,
                coolantThreshold = newSettings.coolantAlertThreshold,
                isOverheating = (current.coolantTemp >= newSettings.coolantAlertThreshold) || newSettings.testOverheatAlert
            )
        }
    }

    suspend fun calibrateCurrentGear(gearNumber: Int) {
        val spd = _telemetry.value.speed
        val rpm = _telemetry.value.rpm
        if (spd > 5 && rpm > 600) {
            val ratio = Math.round((rpm.toFloat() / spd.toFloat()) * 10f) / 10f
            updateSettings { current ->
                val updated = current.gearRatios.toMutableMap()
                updated[gearNumber] = ratio
                current.copy(gearRatios = updated)
            }
        }
    }

    suspend fun scanDtc(): List<String> {
        val codes = realObdManager.scanDtc()
        _telemetry.update { it.copy(dtcCodes = codes, isMilActive = codes.isNotEmpty()) }
        return codes
    }

    suspend fun clearDtc(): Boolean {
        val success = realObdManager.clearDtc()
        if (success) {
            _telemetry.update { it.copy(dtcCodes = emptyList(), isMilActive = false) }
        }
        return success
    }
}
