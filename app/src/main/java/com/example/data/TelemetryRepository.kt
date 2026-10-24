package com.example.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.data.media.VisionMediaNotificationListenerService
import com.example.data.obd.ObdTelemetryService
import com.example.data.obd.RealObdManager
import com.example.data.weather.WeatherRepository
import com.example.model.DriveMode
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import com.example.model.TelemetryData
import com.example.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

class TelemetryRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val settingsDataStore = SettingsDataStore(context)
    val weatherRepository = WeatherRepository()

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
            mapKpa = null,
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

    // Cumulative trip tracking using real elapsed sample deltas
    private var totalTripMeters = 0.0
    private var totalFuelGrams = 0.0
    private var activeTripDurationSec = 0.0
    private var lastTripSampleElapsedMs = SystemClock.elapsedRealtime()
    private var isTripPaused = false

    // GPS & Weather pipeline state
    private val latestCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    private var gpsLocationListener: LocationListener? = null
    private var isGpsRegistered = false

    // NetworkCallback state
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isNetworkCallbackRegistered = false

    // Collector job references to prevent duplicate collectors
    private var obdCollectorJob: Job? = null
    private var weatherCollectorJob: Job? = null

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
        observeWeatherUpdates()
        startTripTicker()
        startNetworkMonitor()
        registerGpsListener()
    }

    /**
     * Single structured collector for OBD telemetry.
     * Reactively observes [ObdTelemetryService.instanceFlow] via [collectLatest] so obsolete
     * collectors are automatically cancelled on service reconnect/recreation and duplicate
     * child collectors can never accumulate.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeObdServiceMetrics() {
        obdCollectorJob?.cancel()
        obdCollectorJob = scope.launch {
            ObdTelemetryService.instanceFlow.collectLatest { service ->
                if (service == null) {
                    applyDisconnectedObdState()
                    return@collectLatest
                }

                combine(
                    service.connectionStatus,
                    service.telemetryState,
                    service.connectedDeviceName,
                    service.dtcCodes,
                    service.isMilActive
                ) { status, metrics, deviceName, dtcs, milActive ->
                     ObdSnapshot(status, metrics, deviceName, dtcs, milActive)
                }.collect { snapshot ->
                    if (snapshot.status == ObdConnectionStatus.CONNECTED) {
                        val currentSettings = _settings.value
                        val spd = snapshot.metrics.speedKmh ?: _telemetry.value.speed
                        val rpm = snapshot.metrics.rpm ?: _telemetry.value.rpm
                        val calculatedGear = calculateGear(
                            rpm = rpm,
                            speedKmh = spd,
                            ratios = currentSettings.gearRatios,
                            driveMode = _telemetry.value.driveMode
                        )
                        val coolant = snapshot.metrics.coolantTempC ?: _telemetry.value.coolantTemp
                        val isOverheat =
                            (coolant >= currentSettings.coolantAlertThreshold) || currentSettings.testOverheatAlert

                        // Real MAF instantaneous fuel calculation (L/100km, estimated from stoichiometric air-fuel ratio)
                        val instantFuel = if (snapshot.metrics.mafGs != null && spd > 3) {
                            ((snapshot.metrics.mafGs * 32.65f) / spd).coerceIn(1.2f, 40f)
                        } else {
                            null
                        }

                        val overspeed =
                            currentSettings.speedLimitAlertKmh > 0 && spd > currentSettings.speedLimitAlertKmh

                        _telemetry.update { current ->
                            current.copy(
                                isObdConnected = true,
                                obdAdapterName = snapshot.deviceName,
                                obdProtocol = snapshot.metrics.protocolName ?: current.obdProtocol,
                                speed = spd,
                                rpm = rpm,
                                currentGear = calculatedGear,
                                isGearEstimated = true,
                                coolantTemp = coolant,
                                coolantThreshold = currentSettings.coolantAlertThreshold,
                                isOverheating = isOverheat,
                                engLoadPercent = snapshot.metrics.engineLoadPercent ?: current.engLoadPercent,
                                throttlePercent = snapshot.metrics.throttlePercent ?: current.throttlePercent,
                                intakeAirTemp = snapshot.metrics.intakeTempC ?: current.intakeAirTemp,
                                mafFlowGs = snapshot.metrics.mafGs ?: current.mafFlowGs,
                                mapKpa = snapshot.metrics.mapKpa ?: current.mapKpa,
                                turboBoostBar = snapshot.metrics.turboBoostBar ?: current.turboBoostBar,
                                fuelLevelPercent = snapshot.metrics.fuelLevelPercent,
                                remainingRangeKm = null,
                                instantFuelL100km = instantFuel,
                                avgFuelL100km = computeAverageFuel(),
                                alternatorVoltage = snapshot.metrics.voltage,
                                overspeedAlert = overspeed,
                                dtcCodes = snapshot.dtcCodes,
                                isMilActive = snapshot.isMilActive
                            )
                        }
                    } else if (!_settings.value.isSimulatingTelemetry) {
                        applyDisconnectedObdState(
                            adapterName = if (snapshot.status == ObdConnectionStatus.CONNECTING ||
                                snapshot.status == ObdConnectionStatus.INITIALIZING_ELM
                            ) snapshot.deviceName else "Disconnected"
                        )
                    }
                }
            }
        }
    }

    private data class ObdSnapshot(
        val status: ObdConnectionStatus,
        val metrics: com.example.data.obd.ParsedObdMetrics,
        val deviceName: String,
        val dtcCodes: List<String>,
        val isMilActive: Boolean
    )

    private fun applyDisconnectedObdState(adapterName: String = "Disconnected") {
        _telemetry.update { current ->
            val spd = if (current.hasGpsFix && current.gpsSpeed > 0) current.gpsSpeed else 0
            val overspeed = _settings.value.speedLimitAlertKmh > 0 && spd > _settings.value.speedLimitAlertKmh
            current.copy(
                isObdConnected = false,
                obdAdapterName = adapterName,
                obdProtocol = "Standby",
                rpm = 0,
                currentGear = "-",
                isGearEstimated = true,
                coolantTemp = 0,
                isOverheating = _settings.value.testOverheatAlert,
                engLoadPercent = 0,
                throttlePercent = 0,
                mapKpa = null,
                turboBoostBar = 0f,
                mafFlowGs = 0f,
                intakeAirTemp = 0,
                fuelLevelPercent = null,
                remainingRangeKm = null,
                instantFuelL100km = null,
                avgFuelL100km = computeAverageFuel(),
                alternatorVoltage = null,
                speed = spd,
                overspeedAlert = overspeed,
                dtcCodes = emptyList(),
                isMilActive = false
            )
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
     * Dedicated weather pipeline decoupled from the 1 Hz GPS listener callback.
     * Queries [WeatherRepository] only when coordinates have moved meaningfully (>= 2.5 km)
     * or the 15-minute cache has expired.
     */
    private fun observeWeatherUpdates() {
        weatherCollectorJob?.cancel()
        weatherCollectorJob = scope.launch {
            latestCoordinates
                .filterNotNull()
                .collectLatest { (lat, lon) ->
                    if (!weatherRepository.shouldFetchWeather(lat, lon)) {
                        return@collectLatest
                    }
                    // Small debounce so rapid initial coarse -> fine GPS fixes coalesce into one HTTP call
                    delay(600)
                    val weather = weatherRepository.fetchWeatherForLocation(lat, lon)
                    if (weather != null && weather.isLoaded) {
                        _telemetry.update { current ->
                            if (current.weatherTempC == weather.temperatureC &&
                                current.weatherCondition == weather.condition
                            ) {
                                current
                            } else {
                                current.copy(
                                    weatherTempC = weather.temperatureC,
                                    weatherCondition = weather.condition
                                )
                            }
                        }
                    }
                }
        }
    }

    fun refreshWeatherNow() {
        val coords = latestCoordinates.value ?: return
        scope.launch {
            val weather = weatherRepository.fetchWeatherForLocation(
                coords.first,
                coords.second,
                forceRefresh = true
            )
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

    /**
     * Calculates estimated gear from RPM/Speed ratio using calibrated vehicle ratios.
     * Never reports confirmed "P" or "N" PRNDL states because standard Mode 01 RPM/Speed PIDs
     * do not expose transmission range selector switch position.
     * Returns "-" when engine is off, "IDLE" when stationary with engine running, or estimated gear.
     */
    fun calculateGear(rpm: Int, speedKmh: Int, ratios: Map<Int, Float>, driveMode: DriveMode): String {
        if (rpm <= 350 && speedKmh <= 2) {
            return "-"
        }
        if (speedKmh <= 2) {
            return "IDLE"
        }
        if (rpm <= 400) {
            return "-"
        }
        val currentRatio = rpm.toFloat() / speedKmh.coerceAtLeast(1)

        // Match against calibrated ratios
        var bestGear: Int? = null
        var minDiff = Float.MAX_VALUE
        ratios.forEach { (gear, expectedRatio) ->
            val diff = abs(currentRatio - expectedRatio)
            if (diff < minDiff && diff < (expectedRatio * 0.22f)) {
                minDiff = diff
                bestGear = gear
            }
        }

        return bestGear?.toString() ?: if (driveMode == DriveMode.SPORT) "S" else "D"
    }

    private fun computeAverageFuel(): Float? {
        if (totalTripMeters < 500 || totalFuelGrams <= 0) return null
        val distanceKm = totalTripMeters / 1000.0
        val fuelLiters = totalFuelGrams / 750.0 // density of petrol ~ 750 g/L
        val avg = (fuelLiters / distanceKm) * 100.0
        return if (avg in 1.5..45.0) (avg * 10.0).roundToInt() / 10f else null
    }

    /**
     * Resets accumulated trip distance, duration, and fuel consumption.
     */
    fun resetTrip() {
        totalTripMeters = 0.0
        totalFuelGrams = 0.0
        activeTripDurationSec = 0.0
        lastTripSampleElapsedMs = SystemClock.elapsedRealtime()
        _telemetry.update {
            it.copy(
                tripDistanceKm = 0f,
                tripDurationSec = 0L,
                avgFuelL100km = null
            )
        }
    }

    private fun startTripTicker() {
        scope.launch {
            lastTripSampleElapsedMs = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000)
                val nowElapsed = SystemClock.elapsedRealtime()
                val rawDeltaSec = (nowElapsed - lastTripSampleElapsedMs) / 1000.0
                lastTripSampleElapsedMs = nowElapsed

                // Ignore large gaps (> 5s) caused by device sleep or background suspension
                val sampleDeltaSec = if (isTripPaused || rawDeltaSec <= 0.0 || rawDeltaSec > 5.0) {
                    0.0
                } else {
                    rawDeltaSec
                }

                val current = _telemetry.value
                val userSet = _settings.value

                // If simulation is explicitly enabled by the user in settings
                if (userSet.isSimulatingTelemetry && !current.isObdConnected) {
                    val tick = (nowElapsed / 250).toInt()
                    val targetSpeed = 85 + (sin(tick * 0.08) * 8).toInt()
                    val targetRpm = (2800 + sin(tick * 0.1) * 350).toInt()
                    _telemetry.update {
                        it.copy(
                            speed = targetSpeed,
                            gpsSpeed = targetSpeed,
                            rpm = targetRpm,
                            currentGear = "4",
                            isGearEstimated = true,
                            coolantTemp = if (userSet.testOverheatAlert) 116 else 92,
                            isOverheating = userSet.testOverheatAlert,
                            engLoadPercent = 42,
                            throttlePercent = 38,
                            mapKpa = 141,
                            turboBoostBar = 0.4f,
                            mafFlowGs = 35f,
                            instantFuelL100km = 6.8f
                        )
                    }
                }

                // Integrate trip distance, fuel, and active duration using real elapsed time delta
                val latest = _telemetry.value
                val activeSpeed = if (latest.isObdConnected || userSet.isSimulatingTelemetry) {
                    latest.speed
                } else if (latest.hasGpsFix) {
                    latest.gpsSpeed
                } else {
                    0
                }

                if (sampleDeltaSec > 0.0 && (activeSpeed > 0 || latest.rpm > 400)) {
                    activeTripDurationSec += sampleDeltaSec
                    if (activeSpeed > 0) {
                        val metersPerSec = (activeSpeed * 1000.0) / 3600.0
                        totalTripMeters += metersPerSec * sampleDeltaSec
                    }
                    val maf = latest.mafFlowGs
                    if (maf > 0f) {
                        val fuelGramsPerSec = maf / 14.7 // stoichiometric gasoline air-fuel ratio
                        totalFuelGrams += fuelGramsPerSec * sampleDeltaSec
                    }
                }

                val newTripDistanceKm = ((totalTripMeters / 1000.0) * 10.0).roundToInt() / 10f
                val newTripDurationSec = activeTripDurationSec.toLong()
                if (newTripDistanceKm != latest.tripDistanceKm || newTripDurationSec != latest.tripDurationSec) {
                    _telemetry.update {
                        it.copy(
                            tripDistanceKm = newTripDistanceKm,
                            tripDurationSec = newTripDurationSec,
                            avgFuelL100km = computeAverageFuel() ?: it.avgFuelL100km
                        )
                    }
                }
            }
        }
    }

    /**
     * Event-driven connectivity monitor using [ConnectivityManager.NetworkCallback] instead of
     * a 5-second polling loop. Only emits StateFlow updates when network state changes.
     */
    private fun startNetworkMonitor() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        if (isNetworkCallbackRegistered) return

        // Evaluate initial state once
        updateNetworkCapabilities(cm, cm.activeNetwork)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateNetworkCapabilities(cm, network)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                applyNetworkCapabilities(networkCapabilities)
            }

            override fun onLost(network: Network) {
                updateNetworkCapabilities(cm, cm.activeNetwork)
            }
        }

        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
            isNetworkCallbackRegistered = true
        } catch (_: Exception) {
            updateNetworkCapabilities(cm, cm.activeNetwork)
        }
    }

    private fun updateNetworkCapabilities(cm: ConnectivityManager, network: Network?) {
        val caps = try {
            if (network != null) cm.getNetworkCapabilities(network) else null
        } catch (_: Exception) {
            null
        }
        applyNetworkCapabilities(caps)
    }

    private fun applyNetworkCapabilities(caps: NetworkCapabilities?) {
        val (signalBars, label) = if (caps != null) {
            val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val transportLabel = when {
                isWifi -> "WIFI"
                isCell -> "CELLULAR"
                isEthernet -> "ETH"
                else -> "ONLINE"
            }
            // Use OS signal strength only if reported; otherwise indicate active link (1) without fabricating dBm bars
            val rawSignal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                caps.signalStrength
            } else {
                Int.MIN_VALUE
            }
            val bars = when {
                rawSignal == Int.MIN_VALUE || rawSignal <= -120 -> 1
                rawSignal >= -65 -> 4
                rawSignal >= -78 -> 3
                rawSignal >= -92 -> 2
                else -> 1
            }
            bars to transportLabel
        } else {
            0 to "OFFLINE"
        }

        _telemetry.update { current ->
            if (current.cellularSignalLevel == signalBars && current.networkLabel == label) {
                current
            } else {
                current.copy(
                    cellularSignalLevel = signalBars,
                    networkLabel = label
                )
            }
        }
    }

    fun unregisterNetworkMonitor() {
        if (!isNetworkCallbackRegistered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback?.let { cb ->
            try {
                cm?.unregisterNetworkCallback(cb)
            } catch (_: Exception) {}
        }
        networkCallback = null
        isNetworkCallbackRegistered = false
    }

    /**
     * Idempotent GPS Location listener registration.
     * Keeps GPS speed and accuracy updates responsive while delegating weather requests
     * to the debounced [latestCoordinates] collector.
     */
    @SuppressLint("MissingPermission")
    fun registerGpsListener() {
        isTripPaused = false
        lastTripSampleElapsedMs = SystemClock.elapsedRealtime()
        if (isGpsRegistered && gpsLocationListener != null) {
            return
        }

        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            val hasFine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!hasFine && !hasCoarse) {
                return
            }

            val listener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    val speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).roundToInt().coerceAtLeast(0) else 0
                    val accuracy = if (loc.hasAccuracy()) loc.accuracy else null

                    val signalLevel = when {
                        accuracy == null -> 1
                        accuracy <= 6f -> 4
                        accuracy <= 14f -> 3
                        accuracy <= 30f -> 2
                        else -> 1
                    }

                    _telemetry.update { current ->
                        // Keep GPS speed distinct from OBD speed; only fall back to GPS speed when OBD is disconnected
                        val activeSpeed = if (!current.isObdConnected) speedKmh else current.speed
                        val overspeed =
                            _settings.value.speedLimitAlertKmh > 0 && activeSpeed > _settings.value.speedLimitAlertKmh
                        current.copy(
                            gpsSpeed = speedKmh,
                            speed = activeSpeed,
                            hasGpsFix = true,
                            gpsAccuracyMeters = accuracy,
                            gpsSignalLevel = signalLevel,
                            overspeedAlert = overspeed
                        )
                    }

                    // Publish coordinates to the throttled/cached weather collector
                    latestCoordinates.value = loc.latitude to loc.longitude
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }

            gpsLocationListener = listener
            var registeredAny = false
            if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, listener)
                registeredAny = true
            }
            try {
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 10.0f, listener)
                    registeredAny = true
                }
            } catch (_: Exception) {}

            isGpsRegistered = registeredAny
        } catch (_: Exception) {}
    }

    fun unregisterGpsListener() {
        isTripPaused = true
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            gpsLocationListener?.let { lm?.removeUpdates(it) }
        } catch (_: Exception) {}
        gpsLocationListener = null
        isGpsRegistered = false
    }

    fun setDriveMode(mode: DriveMode) {
        _telemetry.update { current ->
            val updatedGear = if (current.isObdConnected) {
                calculateGear(current.rpm, current.speed, _settings.value.gearRatios, mode)
            } else {
                current.currentGear
            }
            current.copy(driveMode = mode, currentGear = updatedGear)
        }
    }

    fun startObdServiceIfPermitted() {
        val hasBtPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
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
        applyDisconnectedObdState()
    }

    fun triggerScan() {
        scope.launch {
            _isScanning.value = true
            realObdManager.refreshDevices(triggerBleScan = true)
            delay(1800)
            realObdManager.refreshDevices(triggerBleScan = false)
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
            val ratio = ((rpm.toFloat() / spd.toFloat()) * 10f).roundToInt() / 10f
            updateSettings { current ->
                val updated = current.gearRatios.toMutableMap()
                updated[gearNumber] = ratio
                current.copy(gearRatios = updated)
            }
        }
    }

    suspend fun scanDtc(): List<String> {
        val codes = realObdManager.scanDtc()
        val mil = ObdTelemetryService.instance?.isMilActive?.value ?: codes.isNotEmpty()
        _telemetry.update { it.copy(dtcCodes = codes, isMilActive = mil) }
        return codes
    }

    suspend fun clearDtc(): Boolean {
        val success = realObdManager.clearDtc()
        if (success) {
            val remaining = ObdTelemetryService.instance?.dtcCodes?.value ?: emptyList()
            val mil = ObdTelemetryService.instance?.isMilActive?.value ?: false
            _telemetry.update { it.copy(dtcCodes = remaining, isMilActive = mil) }
        }
        return success
    }
}
