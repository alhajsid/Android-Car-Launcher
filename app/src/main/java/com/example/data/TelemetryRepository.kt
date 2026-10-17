package com.example.data

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.example.data.obd.RealObdManager
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
import kotlinx.coroutines.launch
import kotlin.random.Random

class TelemetryRepository(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val realObdManager = RealObdManager(context)

    private val _telemetry = MutableStateFlow(
        TelemetryData(
            speed = 0,
            rpm = 0,
            currentGear = "-",
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
            gpsSpeed = 0
        )
    )
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    private val _settings = MutableStateFlow(
        UserSettings(
            isSimulatingTelemetry = false // Default to REAL OBD2 connection, not dummy!
        )
    )
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    val detectedAdapters: StateFlow<List<ObdAdapterDevice>> = realObdManager.discoveredDevices
    val obdStatus: StateFlow<ObdConnectionStatus> = realObdManager.connectionStatus
    val diagnosticLogs: StateFlow<List<String>> = realObdManager.diagnosticLogs

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        try {
            val serviceIntent = android.content.Intent(context, com.example.data.obd.ObdTelemetryService::class.java)
            context.startService(serviceIntent)
        } catch (_: Exception) {}

        listenToRealObdMetrics()
        startTelemetryLoop()
        tryRegisterGps()
    }

    /**
     * Listens to live CAN-Bus packets parsed by RealObdManager & ObdTelemetryService
     */
    private fun listenToRealObdMetrics() {
        scope.launch {
            // Also listen to ObdTelemetryService if active
            launch {
                while (true) {
                    val service = com.example.data.obd.ObdTelemetryService.instance
                    if (service != null) {
                        service.telemetryState.collect { metrics ->
                            val isConnected = service.connectionStatus.value == ObdConnectionStatus.CONNECTED
                            if (isConnected) {
                                val spd = metrics.speedKmh ?: _telemetry.value.speed
                                val rpm = metrics.rpm ?: _telemetry.value.rpm
                                val calculatedGear = calculateGear(rpm, spd, _telemetry.value.driveMode)
                                val fuel = metrics.fuelLevelPercent ?: _telemetry.value.fuelLevelPercent
                                val calculatedRange = fuel?.let { (it * 6.5f).toInt() }
                                val instantFuel = if (metrics.mafGs != null && spd > 2) {
                                    ((metrics.mafGs * 32.65f) / spd).coerceIn(1.5f, 45f)
                                } else {
                                    _telemetry.value.instantFuelL100km
                                }

                                _telemetry.update { current ->
                                    current.copy(
                                        isObdConnected = true,
                                        obdAdapterName = service.connectedDeviceName.value,
                                        obdProtocol = metrics.protocolName ?: current.obdProtocol,
                                        speed = spd,
                                        rpm = rpm,
                                        currentGear = calculatedGear,
                                        coolantTemp = metrics.coolantTempC ?: current.coolantTemp,
                                        isOverheating = (metrics.coolantTempC ?: current.coolantTemp) >= current.coolantThreshold,
                                        engLoadPercent = metrics.engineLoadPercent ?: current.engLoadPercent,
                                        throttlePercent = metrics.throttlePercent ?: current.throttlePercent,
                                        intakeAirTemp = metrics.intakeTempC ?: current.intakeAirTemp,
                                        mafFlowGs = metrics.mafGs ?: current.mafFlowGs,
                                        turboBoostBar = metrics.turboBoostBar ?: current.turboBoostBar,
                                        fuelLevelPercent = fuel,
                                        remainingRangeKm = calculatedRange,
                                        instantFuelL100km = instantFuel,
                                        avgFuelL100km = if (fuel != null) 7.8f else null,
                                        alternatorVoltage = metrics.voltage ?: current.alternatorVoltage
                                    )
                                }
                            }
                        }
                    }
                    delay(1000)
                }
            }

            realObdManager.liveMetrics.collect { metrics ->
                val isConnected = realObdManager.connectionStatus.value == ObdConnectionStatus.CONNECTED
                if (isConnected) {
                    val spd = metrics.speedKmh ?: _telemetry.value.speed
                    val rpm = metrics.rpm ?: _telemetry.value.rpm
                    val calculatedGear = calculateGear(rpm, spd, _telemetry.value.driveMode)
                    val fuel = metrics.fuelLevelPercent ?: _telemetry.value.fuelLevelPercent
                    val calculatedRange = fuel?.let { (it * 6.5f).toInt() }
                    val instantFuel = if (metrics.mafGs != null && spd > 2) {
                        ((metrics.mafGs * 32.65f) / spd).coerceIn(1.5f, 45f)
                    } else {
                        _telemetry.value.instantFuelL100km
                    }

                    _telemetry.update { current ->
                        current.copy(
                            isObdConnected = true,
                            obdAdapterName = realObdManager.connectedDeviceName.value,
                            obdProtocol = metrics.protocolName ?: current.obdProtocol,
                            speed = spd,
                            rpm = rpm,
                            currentGear = calculatedGear,
                            coolantTemp = metrics.coolantTempC ?: current.coolantTemp,
                            isOverheating = (metrics.coolantTempC ?: current.coolantTemp) >= current.coolantThreshold,
                            engLoadPercent = metrics.engineLoadPercent ?: current.engLoadPercent,
                            throttlePercent = metrics.throttlePercent ?: current.throttlePercent,
                            intakeAirTemp = metrics.intakeTempC ?: current.intakeAirTemp,
                            mafFlowGs = metrics.mafGs ?: current.mafFlowGs,
                            turboBoostBar = metrics.turboBoostBar ?: current.turboBoostBar,
                            fuelLevelPercent = fuel,
                            remainingRangeKm = calculatedRange,
                            instantFuelL100km = instantFuel,
                            avgFuelL100km = if (fuel != null) 7.8f else null,
                            alternatorVoltage = metrics.voltage ?: current.alternatorVoltage
                        )
                    }
                } else if (!_settings.value.isSimulatingTelemetry) {
                    // When not connected and not simulating: OBD metrics collapse gracefully
                    _telemetry.update { current ->
                        current.copy(
                            isObdConnected = false,
                            obdAdapterName = "Disconnected",
                            obdProtocol = "Standby",
                            rpm = 0,
                            currentGear = "-",
                            coolantTemp = 0,
                            isOverheating = false,
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
                            speed = if (current.gpsSpeed > 0) current.gpsSpeed else 0
                        )
                    }
                }
            }
        }
    }

    private fun calculateGear(rpm: Int, speedKmh: Int, driveMode: DriveMode): String {
        if (speedKmh <= 2) return if (rpm < 500) "P" else "N"
        val ratio = rpm.toFloat() / speedKmh.coerceAtLeast(1)
        return when {
            ratio > 68f -> "1"
            ratio in 44f..68f -> "2"
            ratio in 30f..44f -> "3"
            ratio in 21f..30f -> "4"
            ratio in 16f..21f -> "5"
            else -> if (driveMode == DriveMode.SPORT) "S" else "D"
        }
    }

    private fun startTelemetryLoop() {
        scope.launch {
            var tick = 0
            while (true) {
                delay(250)
                tick++
                val current = _telemetry.value
                val userSet = _settings.value

                // If simulation mode is explicitly enabled by the user for desk testing
                if (userSet.isSimulatingTelemetry && !realObdManager.connectionStatus.value.equals(ObdConnectionStatus.CONNECTED)) {
                    val mode = current.driveMode
                    val rpmJitter = Random.nextInt(-35, 45)
                    val baseRpm = when (mode) {
                        DriveMode.SPORT -> 3850
                        DriveMode.COMFORT -> 2150
                        DriveMode.ECO_PRO -> 1650
                    }
                    val targetRpm = (baseRpm + (Math.sin(tick * 0.1) * 350).toInt() + rpmJitter)
                        .coerceIn(800, current.maxRpm)

                    val targetSpeed = when (mode) {
                        DriveMode.SPORT -> (92 + (Math.sin(tick * 0.08) * 8).toInt())
                        DriveMode.COMFORT -> (82 + (Math.sin(tick * 0.08) * 6).toInt())
                        DriveMode.ECO_PRO -> (76 + (Math.sin(tick * 0.08) * 4).toInt())
                    }

                    val calculatedLoad = when (mode) {
                        DriveMode.SPORT -> (35 + (targetRpm * 0.008f).toInt()).coerceIn(20, 85)
                        DriveMode.COMFORT -> (25 + (targetRpm * 0.005f).toInt()).coerceIn(15, 60)
                        DriveMode.ECO_PRO -> (18 + (targetRpm * 0.003f).toInt()).coerceIn(10, 45)
                    }

                    val boostBar = when (mode) {
                        DriveMode.SPORT -> (0.6f + (targetRpm / 8000f) * 0.45f)
                        DriveMode.COMFORT -> (0.2f + (targetRpm / 8000f) * 0.2f)
                        DriveMode.ECO_PRO -> 0.0f
                    }

                    val isOverheating = userSet.testOverheatAlert
                    val coolant = if (isOverheating) 116 else 92

                    _telemetry.update {
                        it.copy(
                            speed = targetSpeed,
                            gpsSpeed = targetSpeed,
                            rpm = targetRpm,
                            currentGear = mode.defaultGear,
                            coolantTemp = coolant,
                            isOverheating = isOverheating,
                            engLoadPercent = calculatedLoad,
                            throttlePercent = (calculatedLoad * 1.3f).toInt().coerceIn(10, 95),
                            turboBoostBar = boostBar,
                            mafFlowGs = (30f + (targetRpm * 0.004f) * 10f),
                            instantFuelL100km = when (mode) {
                                DriveMode.SPORT -> 8.8f + Random.nextFloat() * 0.8f
                                DriveMode.COMFORT -> 6.8f + Random.nextFloat() * 0.5f
                                DriveMode.ECO_PRO -> 4.9f + Random.nextFloat() * 0.3f
                            }
                        )
                    }
                }
            }
        }
    }

    private fun tryRegisterGps() {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            locationManager?.let { lm ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        if (loc.hasSpeed()) {
                            val speedKmh = (loc.speed * 3.6f).toInt()
                            _telemetry.update {
                                // If OBD is disconnected, use GPS speed directly as primary speedometer!
                                val newSpeed = if (!it.isObdConnected) speedKmh else it.speed
                                it.copy(speed = newSpeed, gpsSpeed = speedKmh)
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                }
                if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1.0f, listener)
                }
            }
        } catch (_: Exception) {}
    }

    fun setDriveMode(mode: DriveMode) {
        _telemetry.update {
            it.copy(
                driveMode = mode
            )
        }
    }

    fun toggleObdConnection() {
        if (realObdManager.connectionStatus.value == ObdConnectionStatus.CONNECTED) {
            realObdManager.disconnect()
        } else {
            // Attempt connecting to first paired adapter
            val first = detectedAdapters.value.firstOrNull()
            if (first != null) {
                realObdManager.connect(first)
            }
        }
    }

    fun connectToAdapter(adapterId: String) {
        val target = detectedAdapters.value.find { it.id == adapterId } ?: return
        com.example.data.obd.ObdTelemetryService.instance?.connect(target)
        realObdManager.connect(target)
    }

    fun disconnectAdapter() {
        com.example.data.obd.ObdTelemetryService.instance?.disconnect()
        realObdManager.disconnect()
        _telemetry.update { current ->
            current.copy(
                isObdConnected = false,
                obdAdapterName = "Disconnected",
                obdProtocol = "Standby",
                rpm = 0,
                currentGear = "-",
                coolantTemp = 0,
                isOverheating = false,
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
                speed = if (current.gpsSpeed > 0) current.gpsSpeed else 0
            )
        }
    }

    fun triggerScan() {
        scope.launch {
            _isScanning.value = true
            com.example.data.obd.ObdTelemetryService.instance?.autoConnectIfPaired()
            realObdManager.refreshDevices()
            delay(1200)
            _isScanning.value = false
        }
    }

    fun toggleMediaPlayback() {
        _telemetry.update { it.copy(isMediaPlaying = !it.isMediaPlaying) }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        _settings.update(transform)
    }
}
