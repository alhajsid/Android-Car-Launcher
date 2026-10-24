package com.example.data.obd

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Foreground Android Service for OBD2 ELM327 vehicle communications.
 * Keeps vehicle telemetry alive across app lifecycle and screen transitions.
 */
class ObdTelemetryService : Service() {

    companion object {
        private const val TAG = "ObdTelemetryService"
        private const val NOTIFICATION_ID = 4099
        private const val CHANNEL_ID = "obd_telemetry_channel"
        private const val PREFS_NAME = "vision_obd_service_prefs"
        private const val KEY_SAVED_ADAPTER_ADDRESS = "saved_adapter_address"
        private const val KEY_USER_DISCONNECTED = "user_explicitly_disconnected"
        private const val MAX_LOG_ENTRIES = 35

        private val _instanceFlow = MutableStateFlow<ObdTelemetryService?>(null)
        val instanceFlow: StateFlow<ObdTelemetryService?> = _instanceFlow.asStateFlow()

        var instance: ObdTelemetryService?
            get() = _instanceFlow.value
            private set(value) {
                _instanceFlow.value = value
            }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    lateinit var connectionManager: ObdBluetoothConnectionManager
        private set

    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null
    private var autoConnectJob: Job? = null
    private var isReceiverRegistered = false

    @Volatile
    private var userExplicitlyDisconnected: Boolean = false

    @Volatile
    var isVerboseLoggingEnabled: Boolean = false

    private val logLock = Any()
    private val logQueue = ArrayDeque<String>(MAX_LOG_ENTRIES + 2)
    private val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _telemetryState = MutableStateFlow(ParsedObdMetrics())
    val telemetryState: StateFlow<ParsedObdMetrics> = _telemetryState.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ObdConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ObdConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow("Disconnected")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<String>>(
        listOf("OBD-II CAN Telemetry Service initialized.")
    )
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

    private val _dtcCodes = MutableStateFlow<List<String>>(emptyList())
    val dtcCodes: StateFlow<List<String>> = _dtcCodes.asStateFlow()

    private val _isMilActive = MutableStateFlow(false)
    val isMilActive: StateFlow<Boolean> = _isMilActive.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ObdTelemetryService = this@ObdTelemetryService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        logQueue.addLast("OBD-II CAN Telemetry Service initialized.")
        connectionManager = ObdBluetoothConnectionManager(applicationContext)
        userExplicitlyDisconnected = getPrefs().getBoolean(KEY_USER_DISCONNECTED, false)
        instance = this

        startForegroundNotification()
        registerBluetoothReceiver()
        if (!userExplicitlyDisconnected) {
            autoConnectIfPaired()
        } else {
            addLog("Auto-connect paused (previous manual disconnect respected).")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_STICKY
    }

    private fun getPrefs() = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun getSavedAdapterAddress(): String? =
        getPrefs().getString(KEY_SAVED_ADAPTER_ADDRESS, null)

    private fun saveAdapterPreference(address: String?, userDisconnected: Boolean) {
        getPrefs().edit().apply {
            if (address != null) {
                putString(KEY_SAVED_ADAPTER_ADDRESS, address)
            }
            putBoolean(KEY_USER_DISCONNECTED, userDisconnected)
            apply()
        }
    }

    private fun startForegroundNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Vehicle OBD-II Telemetry",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Monitors CAN-Bus and OBD2 sensor data"
                    setShowBadge(false)
                }
                notificationManager?.createNotificationChannel(channel)
            }

            val launchIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Vision HUD Active")
                .setContentText("OBD-II vehicle telemetry monitoring running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting foreground service: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground notification: ${e.message}")
        }
    }

    /**
     * Bounded, low-allocation diagnostic log ring buffer.
     * High-frequency verbose logs are skipped unless [isVerboseLoggingEnabled] is enabled.
     */
    private fun addLog(entry: String, verboseOnly: Boolean = false) {
        if (verboseOnly && !isVerboseLoggingEnabled) return
        val snapshot = synchronized(logLock) {
            val timestamp = logTimeFormat.format(Date())
            if (logQueue.size >= MAX_LOG_ENTRIES) {
                logQueue.removeFirst()
            }
            logQueue.addLast("[$timestamp] $entry")
            logQueue.toList()
        }
        _diagnosticLogs.value = snapshot
        if (!verboseOnly) {
            Log.d(TAG, entry)
        }
    }

    private fun registerBluetoothReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            registerReceiver(bluetoothReceiver, filter)
            isReceiverRegistered = true
            addLog("Bluetooth system events listener registered.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bluetooth receiver: ${e.message}")
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    if (bondState == BluetoothDevice.BOND_BONDED && device != null) {
                        addLog("New Bluetooth device paired: ${device.name ?: device.address}")
                        if (!userExplicitlyDisconnected && connectionManager.isLikelyObdAdapter(device.name)) {
                            autoConnectIfPaired()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val savedAddr = getSavedAdapterAddress()
                    val isKnownAdapter = device != null && (
                        device.address == savedAddr || connectionManager.isLikelyObdAdapter(device.name)
                    )
                    if (isKnownAdapter && !userExplicitlyDisconnected) {
                        if (_connectionStatus.value != ObdConnectionStatus.CONNECTED &&
                            _connectionStatus.value != ObdConnectionStatus.CONNECTING &&
                            _connectionStatus.value != ObdConnectionStatus.INITIALIZING_ELM
                        ) {
                            addLog("OBD adapter link detected (${device?.name}). Auto-linking CAN...")
                            autoConnectIfPaired()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && device.address == connectionManager.activeDevice.value?.address) {
                        addLog("Vehicle link disconnected: ${device.name}")
                        if (!userExplicitlyDisconnected) {
                            handleConnectionLost()
                        }
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON && !userExplicitlyDisconnected) {
                        autoConnectIfPaired()
                    }
                }
            }
        }
    }

    /**
     * Automatically connects ONLY to an explicitly saved OBD adapter or a confidently identified
     * OBD adapter by name. Never falls back to unrelated paired Bluetooth devices (such as
     * headphones, car stereos, or phones), and respects explicit user disconnects.
     */
    fun autoConnectIfPaired(forceClearUserDisconnect: Boolean = false) {
        if (forceClearUserDisconnect) {
            userExplicitlyDisconnected = false
            saveAdapterPreference(getSavedAdapterAddress(), false)
        }
        if (userExplicitlyDisconnected) {
            return
        }
        if (autoConnectJob?.isActive == true ||
            _connectionStatus.value == ObdConnectionStatus.CONNECTED ||
            _connectionStatus.value == ObdConnectionStatus.CONNECTING ||
            _connectionStatus.value == ObdConnectionStatus.INITIALIZING_ELM
        ) {
            return
        }

        autoConnectJob = serviceScope.launch {
            delay(400)
            if (userExplicitlyDisconnected ||
                _connectionStatus.value == ObdConnectionStatus.CONNECTED ||
                _connectionStatus.value == ObdConnectionStatus.CONNECTING ||
                _connectionStatus.value == ObdConnectionStatus.INITIALIZING_ELM
            ) {
                return@launch
            }

            val pairedDevices = connectionManager.getPairedObdDevices()
            val savedAddress = getSavedAdapterAddress()

            // 1. Prefer explicitly saved adapter if still paired
            // 2. Otherwise select only a confidently identified OBD adapter
            val candidate = pairedDevices.firstOrNull { savedAddress != null && it.address == savedAddress }
                ?: pairedDevices.firstOrNull { connectionManager.isLikelyObdAdapter(it.name) }

            if (candidate != null) {
                addLog("Auto-connecting to OBD adapter: ${candidate.name} [${candidate.address}]")
                connect(candidate, isAutoConnect = true)
            } else {
                addLog("No recognized OBD-II adapter paired. Please select your adapter in OBD Settings.")
            }
        }
    }

    fun connect(device: ObdAdapterDevice, isAutoConnect: Boolean = false) {
        if (!isAutoConnect) {
            userExplicitlyDisconnected = false
            saveAdapterPreference(device.address, false)
        }
        autoConnectJob?.cancel()
        reconnectJob?.cancel()
        pollingJob?.cancel()

        _connectionStatus.value = ObdConnectionStatus.CONNECTING
        _connectedDeviceName.value = device.name

        pollingJob = serviceScope.launch {
            addLog("Connecting to ${device.name} [${device.address}]...")
            val connected = connectionManager.connect(device)

            if (!connected) {
                val errReason = connectionManager.lastErrorReason.value
                addLog(errReason ?: "Connection failed for ${device.name}.")
                _connectionStatus.value = ObdConnectionStatus.ERROR
                // Do not endlessly retry if the BLE profile is explicitly unsupported
                if (errReason == null || !errReason.contains("Unsupported BLE GATT", ignoreCase = true)) {
                    scheduleReconnection()
                }
                return@launch
            }

            // Persist successfully connected adapter
            saveAdapterPreference(device.address, false)

            _connectionStatus.value = ObdConnectionStatus.INITIALIZING_ELM
            addLog("Link established! Running serialized ELM327 handshake...")

            var supportedPidsFrom0100: Set<String> = emptySet()

            // Run standard ELM327 handshake through serialized command executor
            for (cmd in ObdProtocol.INIT_COMMANDS) {
                currentCoroutineContext().ensureActive()
                val timeout = if (cmd == "ATZ" || cmd == "0100") 1800L else 1200L
                val resp = connectionManager.executeCommand(cmd, timeoutMs = timeout)
                val clean = ObdProtocol.cleanResponse(resp)
                addLog("TX: $cmd -> RX: $clean")
                if (cmd == "0100") {
                    supportedPidsFrom0100 = ObdProtocol.parseSupportedPids0100(resp)
                }
                if (cmd == "ATZ") {
                    delay(650)
                } else {
                    delay(45)
                }
            }

            val protoResp = connectionManager.executeCommand(ObdProtocol.CMD_PROTOCOL, 1000)
            val protocolName = ObdProtocol.cleanResponse(protoResp)
                .takeIf { it.isNotEmpty() && !ObdProtocol.isErrorOrNoData(it) }
                ?: "ISO 15765-4 CAN"
            addLog("CAN Bus Active Protocol: $protocolName (Supported PIDs: ${supportedPidsFrom0100.size})")

            _connectionStatus.value = ObdConnectionStatus.CONNECTED
            addLog("Vehicle ECU Handshake Complete. Starting serialized PID telemetry loop...")

            // Initial check for MIL status and fault codes (also serialized)
            scanMilAndDtc()

            startPollingLoop(protocolName, supportedPidsFrom0100)
        }
    }

    private suspend fun scanMilAndDtc() {
        val milRaw = connectionManager.executeCommand(ObdProtocol.CMD_MIL_STATUS, 700)
        val milParsed = ObdProtocol.parseMilStatus(milRaw)
        if (milParsed != null) {
            _isMilActive.value = milParsed.first
            if (milParsed.second > 0 || milParsed.first) {
                val dtcRaw = connectionManager.executeCommand(ObdProtocol.CMD_READ_DTC, 1000)
                val codes = ObdProtocol.parseDtcCodes(dtcRaw)
                _dtcCodes.value = codes
                addLog("ECU Diagnostics: MIL=${milParsed.first}, DTCs=${codes.size} $codes")
            } else {
                _dtcCodes.value = emptyList()
            }
        }
    }

    /**
     * Serialized manual DTC scan. Executes atomically via [connectionManager.executeCommand]
     * so it never collides with live PID polling.
     */
    suspend fun manualScanDtc(): List<String> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected) {
            addLog("Manual DTC Scan aborted: adapter not connected.")
            return@withContext emptyList()
        }
        val milRaw = connectionManager.executeCommand(ObdProtocol.CMD_MIL_STATUS, 800)
        ObdProtocol.parseMilStatus(milRaw)?.let { (milOn, _) ->
            _isMilActive.value = milOn
        }
        val dtcRaw = connectionManager.executeCommand(ObdProtocol.CMD_READ_DTC, 1200)
        if (ObdProtocol.isErrorOrNoData(dtcRaw) && !ObdProtocol.cleanResponse(dtcRaw).contains("43")) {
            addLog("Manual DTC Scan: No stored fault codes or NO DATA (${ObdProtocol.cleanResponse(dtcRaw)}).")
            _dtcCodes.value = emptyList()
            return@withContext emptyList()
        }
        val codes = ObdProtocol.parseDtcCodes(dtcRaw)
        _dtcCodes.value = codes
        addLog("Manual DTC Scan complete: ${if (codes.isEmpty()) "0 fault codes" else codes.joinToString()}")
        codes
    }

    /**
     * Serialized manual DTC clear (OBD Mode 04).
     * Strictly validates positive Mode 04 acknowledgement ("44"), refreshes MIL/DTC status
     * from the ECU, and returns a structured [DtcClearResult] separating command acceptance
     * from verified post-clear ECU state.
     */
    suspend fun manualClearDtc(): DtcClearResult = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected) {
            addLog("Clear DTC failed: adapter not connected.")
            return@withContext DtcClearResult(
                status = DtcClearStatus.NOT_CONNECTED,
                commandAccepted = false,
                verifiedClear = false,
                remainingCodes = _dtcCodes.value,
                isMilStillActive = _isMilActive.value,
                message = "Clear aborted: OBD-II adapter is not connected."
            )
        }
        val resp = connectionManager.executeCommand(ObdProtocol.CMD_CLEAR_DTC, 1800)
        val clean = ObdProtocol.cleanResponse(resp)
        val accepted = ObdProtocol.isClearDtcSuccess(resp)

        if (!accepted) {
            val rxDesc = clean.ifEmpty { "TIMEOUT/EMPTY" }
            addLog("ECU rejected or failed Mode 04 Clear DTC (RX: $rxDesc).")
            return@withContext DtcClearResult(
                status = DtcClearStatus.REJECTED_OR_FAILED,
                commandAccepted = false,
                verifiedClear = false,
                remainingCodes = _dtcCodes.value,
                isMilStillActive = _isMilActive.value,
                message = "Clear rejected by ECU/adapter (RX: $rxDesc)."
            )
        }

        addLog("ECU accepted Mode 04 Clear DTC command (RX: $clean). Verifying MIL/DTC status...")
        delay(250)
        scanMilAndDtc()

        val remainingCodes = _dtcCodes.value
        val milStillOn = _isMilActive.value
        val isVerifiedClear = remainingCodes.isEmpty() && !milStillOn

        if (isVerifiedClear) {
            addLog("Confirmed: ECU fault codes cleared and MIL extinguished.")
            DtcClearResult(
                status = DtcClearStatus.VERIFIED_CLEARED,
                commandAccepted = true,
                verifiedClear = true,
                remainingCodes = emptyList(),
                isMilStillActive = false,
                message = "Mode 04 accepted & verified: 0 active fault codes, MIL off."
            )
        } else {
            addLog("Mode 04 accepted, but ECU still reports MIL=$milStillOn, Permanent/Active DTCs=$remainingCodes.")
            DtcClearResult(
                status = DtcClearStatus.ACCEPTED_CODES_REMAIN,
                commandAccepted = true,
                verifiedClear = false,
                remainingCodes = remainingCodes,
                isMilStillActive = milStillOn,
                message = "Mode 04 accepted, but ${remainingCodes.size} permanent/active code(s) or MIL remain."
            )
        }
    }

    /**
     * Adaptive, serialized OBD2 ELM327 PID polling loop:
     * - Prioritizes fast-changing primary telemetry (RPM, Speed) every cycle.
     * - Polls secondary metrics (Coolant, Load, Throttle, MAP/Boost, MAF, IAT) at staggered intervals.
     * - Tracks unsupported PIDs (from 0100 bitmask or repeated NO DATA) and skips them to save bus bandwidth.
     * - Emits StateFlow updates only when values materially change or freshness heartbeat expires.
     */
    private suspend fun startPollingLoop(
        protocol: String,
        initialSupportedPids: Set<String>
    ) {
        var cycle = 0
        val unsupportedPids = mutableSetOf<String>()
        val noDataCounts = mutableMapOf<String, Int>()
        var lastEmitTimeMs = 0L

        suspend fun pollPidIfSupported(pid: String, timeoutMs: Long): String? {
            if (pid in unsupportedPids) return null
            // If 0100 returned a valid bitmask, skip Mode 01 PIDs in 0101..0120 that are not advertised
            if (initialSupportedPids.isNotEmpty() && pid.startsWith("01") && pid != ObdProtocol.CMD_FUEL_LEVEL) {
                val pidNum = pid.removePrefix("01").toIntOrNull(16)
                if (pidNum != null && pidNum in 1..0x20 && pid !in initialSupportedPids) {
                    unsupportedPids.add(pid)
                    addLog("Skipping PID $pid (not advertised by ECU 0100 bitmask).", verboseOnly = true)
                    return null
                }
            }

            val raw = connectionManager.executeCommand(pid, timeoutMs)
            if (ObdProtocol.isNoDataResponse(raw)) {
                val count = (noDataCounts[pid] ?: 0) + 1
                noDataCounts[pid] = count
                // Keep core RPM/Speed probing longer; retire optional PIDs after 3 consecutive NO DATA responses
                val maxFailures = if (pid == ObdProtocol.CMD_RPM || pid == ObdProtocol.CMD_SPEED) 10 else 3
                if (count >= maxFailures) {
                    unsupportedPids.add(pid)
                    addLog("PID $pid marked unsupported after $count NO DATA responses.")
                }
                return null
            } else if (!ObdProtocol.isErrorOrNoData(raw)) {
                noDataCounts.remove(pid)
            }
            return raw
        }

        try {
            while (currentCoroutineContext().isActive && connectionManager.isConnected) {
                cycle++
                val cycleStartMs = SystemClock.elapsedRealtime()
                val previous = _telemetryState.value

                // Primary loop (every cycle): RPM and Speed
                val rpmVal = pollPidIfSupported(ObdProtocol.CMD_RPM, 300)?.let {
                    ObdProtocol.parseRpm(it)
                } ?: previous.rpm

                val spdVal = pollPidIfSupported(ObdProtocol.CMD_SPEED, 300)?.let {
                    ObdProtocol.parseSpeed(it)
                } ?: previous.speedKmh

                // Coolant & Engine Load every 3 cycles
                var ectVal = previous.coolantTempC
                var loadVal = previous.engineLoadPercent
                if (cycle % 3 == 0) {
                    pollPidIfSupported(ObdProtocol.CMD_COOLANT, 350)?.let {
                        ectVal = ObdProtocol.parseCoolant(it) ?: ectVal
                    }
                    pollPidIfSupported(ObdProtocol.CMD_ENGINE_LOAD, 350)?.let {
                        loadVal = ObdProtocol.parseEngineLoad(it) ?: loadVal
                    }
                }

                // Throttle & MAP/Boost every 4 cycles
                var tpsVal = previous.throttlePercent
                var mapVal = previous.mapKpa
                var boostVal = previous.turboBoostBar
                if (cycle % 4 == 0) {
                    pollPidIfSupported(ObdProtocol.CMD_THROTTLE, 300)?.let {
                        tpsVal = ObdProtocol.parseThrottle(it) ?: tpsVal
                    }
                    pollPidIfSupported(ObdProtocol.CMD_MAP_BOOST, 320)?.let { raw ->
                        mapVal = ObdProtocol.parseMapKpa(raw) ?: mapVal
                        boostVal = ObdProtocol.parseBoost(raw) ?: boostVal
                    }
                }

                // MAF & Intake Air Temp every 6 cycles
                var mafVal = previous.mafGs
                var iatVal = previous.intakeTempC
                if (cycle % 6 == 0) {
                    pollPidIfSupported(ObdProtocol.CMD_MAF, 350)?.let {
                        mafVal = ObdProtocol.parseMaf(it) ?: mafVal
                    }
                    pollPidIfSupported(ObdProtocol.CMD_INTAKE_TEMP, 300)?.let {
                        iatVal = ObdProtocol.parseIat(it) ?: iatVal
                    }
                }

                // Voltage & Fuel Level every 20 cycles (slow-changing metrics)
                var voltVal = previous.voltage
                var fuelVal = previous.fuelLevelPercent
                if (cycle == 1 || cycle % 20 == 0) {
                    val voltRaw = connectionManager.executeCommand(ObdProtocol.CMD_VOLTAGE, 350)
                    voltVal = ObdProtocol.parseVoltage(voltRaw) ?: voltVal

                    pollPidIfSupported(ObdProtocol.CMD_FUEL_LEVEL, 400)?.let {
                        fuelVal = ObdProtocol.parseFuelLevel(it) ?: fuelVal
                    }
                }

                // Re-enable core PIDs periodically every 120 cycles in case engine ignition state changed
                if (cycle % 120 == 0) {
                    unsupportedPids.remove(ObdProtocol.CMD_RPM)
                    unsupportedPids.remove(ObdProtocol.CMD_SPEED)
                }

                val nowElapsed = SystemClock.elapsedRealtime()
                val pollLatency = (nowElapsed - cycleStartMs).coerceAtLeast(1L)

                val hasMaterialChange =
                    spdVal != previous.speedKmh ||
                        rpmVal != previous.rpm ||
                        ectVal != previous.coolantTempC ||
                        loadVal != previous.engineLoadPercent ||
                        tpsVal != previous.throttlePercent ||
                        iatVal != previous.intakeTempC ||
                        mapVal != previous.mapKpa ||
                        fuelVal != previous.fuelLevelPercent ||
                        !floatEquals(mafVal, previous.mafGs, 0.05f) ||
                        !floatEquals(boostVal, previous.turboBoostBar, 0.01f) ||
                        !floatEquals(voltVal, previous.voltage, 0.05f) ||
                        protocol != previous.protocolName

                if (hasMaterialChange || (nowElapsed - lastEmitTimeMs) >= 1500L) {
                    lastEmitTimeMs = nowElapsed
                    _telemetryState.value = ParsedObdMetrics(
                        speedKmh = spdVal,
                        rpm = rpmVal,
                        coolantTempC = ectVal,
                        engineLoadPercent = loadVal,
                        throttlePercent = tpsVal,
                        intakeTempC = iatVal,
                        mafGs = mafVal,
                        mapKpa = mapVal,
                        turboBoostBar = boostVal,
                        fuelLevelPercent = fuelVal,
                        voltage = voltVal,
                        protocolName = protocol,
                        lastUpdatedMs = System.currentTimeMillis(),
                        supportedPidsCount = initialSupportedPids.size,
                        pollLatencyMs = pollLatency
                    )
                }

                // Adaptive inter-cycle pause so we don't saturate slow ELM327 UART buffers
                val pauseMs = if (pollLatency < 60L) 60L else 35L
                delay(pauseMs)
            }
        } catch (ce: CancellationException) {
            throw ce
        }

        if (!connectionManager.isConnected && !userExplicitlyDisconnected) {
            handleConnectionLost()
        }
    }

    private fun floatEquals(a: Float?, b: Float?, eps: Float): Boolean {
        if (a == null && b == null) return true
        if (a == null || b == null) return false
        return abs(a - b) <= eps
    }

    private fun handleConnectionLost() {
        if (userExplicitlyDisconnected) return
        _connectionStatus.value = ObdConnectionStatus.ERROR
        // Clear stale live speed/RPM so disconnected state never masquerades as live driving data
        _telemetryState.value = _telemetryState.value.copy(
            speedKmh = null,
            rpm = null,
            lastUpdatedMs = System.currentTimeMillis()
        )
        addLog("OBD-II connection lost. Scheduling automatic reconnect...")
        scheduleReconnection()
    }

    private fun scheduleReconnection() {
        if (userExplicitlyDisconnected) return
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(5000)
            if (!userExplicitlyDisconnected &&
                _connectionStatus.value != ObdConnectionStatus.CONNECTED &&
                _connectionStatus.value != ObdConnectionStatus.CONNECTING
            ) {
                addLog("Attempting auto-reconnection to saved OBD adapter...")
                autoConnectIfPaired()
            }
        }
    }

    fun disconnect() {
        userExplicitlyDisconnected = true
        saveAdapterPreference(getSavedAdapterAddress(), true)
        autoConnectJob?.cancel()
        reconnectJob?.cancel()
        pollingJob?.cancel()
        connectionManager.disconnect()
        _connectionStatus.value = ObdConnectionStatus.DISCONNECTED
        _connectedDeviceName.value = "Disconnected"
        _telemetryState.value = ParsedObdMetrics()
        addLog("OBD-II adapter disconnected by user (auto-reconnect paused until next selection).")
    }

    override fun onDestroy() {
        autoConnectJob?.cancel()
        reconnectJob?.cancel()
        pollingJob?.cancel()
        connectionManager.disconnect()
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(bluetoothReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }
}
