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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground Android Service for OBD2 ELM327 vehicle communications.
 * Keeps vehicle telemetry alive across app lifecycle and screen transitions.
 */
class ObdTelemetryService : Service() {

    companion object {
        private const val TAG = "ObdTelemetryService"
        private const val NOTIFICATION_ID = 4099
        private const val CHANNEL_ID = "obd_telemetry_channel"

        var instance: ObdTelemetryService? = null
            private set
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    lateinit var connectionManager: ObdBluetoothConnectionManager
        private set

    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null
    private var isReceiverRegistered = false

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
        instance = this
        connectionManager = ObdBluetoothConnectionManager(applicationContext)

        startForegroundNotification()
        registerBluetoothReceiver()
        autoConnectIfPaired()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_STICKY
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

    private fun addLog(entry: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        _diagnosticLogs.value = (_diagnosticLogs.value + "[$timestamp] $entry").takeLast(35)
        Log.d(TAG, entry)
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
                        if (connectionManager.isLikelyObdAdapter(device.name)) {
                            autoConnectIfPaired()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && connectionManager.isLikelyObdAdapter(device.name)) {
                        if (_connectionStatus.value != ObdConnectionStatus.CONNECTED &&
                            _connectionStatus.value != ObdConnectionStatus.CONNECTING) {
                            addLog("OBD adapter link detected (${device.name}). Auto-linking CAN...")
                            autoConnectIfPaired()
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && device.address == connectionManager.activeDevice.value?.address) {
                        addLog("Vehicle link disconnected: ${device.name}")
                        handleConnectionLost()
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        autoConnectIfPaired()
                    }
                }
            }
        }
    }

    fun autoConnectIfPaired() {
        serviceScope.launch {
            delay(500)
            if (_connectionStatus.value == ObdConnectionStatus.CONNECTED ||
                _connectionStatus.value == ObdConnectionStatus.CONNECTING) {
                return@launch
            }

            val pairedDevices = connectionManager.getPairedObdDevices()
            val candidate = pairedDevices.firstOrNull { connectionManager.isLikelyObdAdapter(it.name) }
                ?: pairedDevices.firstOrNull()

            if (candidate != null) {
                addLog("Auto-connecting to vehicle adapter: ${candidate.name} [${candidate.address}]")
                connect(candidate)
            } else {
                addLog("No paired ELM327 OBD2 adapter found. Pair in phone Bluetooth settings.")
            }
        }
    }

    fun connect(device: ObdAdapterDevice) {
        reconnectJob?.cancel()
        pollingJob?.cancel()
        _connectionStatus.value = ObdConnectionStatus.CONNECTING
        _connectedDeviceName.value = device.name

        pollingJob = serviceScope.launch {
            addLog("Connecting to ${device.name} [${device.address}]...")
            val connected = connectionManager.connect(device)

            if (!connected) {
                addLog("Socket connection failed for ${device.name}.")
                _connectionStatus.value = ObdConnectionStatus.ERROR
                scheduleReconnection()
                return@launch
            }

            _connectionStatus.value = ObdConnectionStatus.INITIALIZING_ELM
            addLog("Socket linked! Running ELM327 protocol handshake...")

            // Run standard ELM327 handshake
            for (cmd in ObdProtocol.INIT_COMMANDS) {
                if (!isActive) break
                val resp = connectionManager.sendCommand(cmd, timeoutMs = 1500)
                val clean = ObdProtocol.cleanResponse(resp)
                addLog("TX: $cmd -> RX: $clean")
                if (cmd == "ATZ") {
                    delay(800)
                } else {
                    delay(60)
                }
            }

            val protoResp = connectionManager.sendCommand(ObdProtocol.CMD_PROTOCOL, 1000)
            val protocolName = ObdProtocol.cleanResponse(protoResp).ifEmpty { "ISO 15765-4 CAN" }
            addLog("CAN Bus Active Protocol: $protocolName")

            _connectionStatus.value = ObdConnectionStatus.CONNECTED
            addLog("Vehicle ECU Handshake Complete. Starting PID telemetry loop (~3 Hz)...")

            // Initial check for MIL status and fault codes
            scanMilAndDtc()

            startPollingLoop(protocolName)
        }
    }

    private suspend fun scanMilAndDtc() {
        val milRaw = connectionManager.sendCommand(ObdProtocol.CMD_MIL_STATUS, 500)
        val milParsed = ObdProtocol.parseMilStatus(milRaw)
        if (milParsed != null) {
            _isMilActive.value = milParsed.first
            if (milParsed.second > 0) {
                val dtcRaw = connectionManager.sendCommand(ObdProtocol.CMD_READ_DTC, 800)
                val codes = ObdProtocol.parseDtcCodes(dtcRaw)
                _dtcCodes.value = codes
                addLog("ECU Diagnostics: MIL=${milParsed.first}, Codes: $codes")
            } else {
                _dtcCodes.value = emptyList()
            }
        }
    }

    suspend fun manualScanDtc(): List<String> = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected) return@withContext emptyList()
        val dtcRaw = connectionManager.sendCommand(ObdProtocol.CMD_READ_DTC, 1000)
        val codes = ObdProtocol.parseDtcCodes(dtcRaw)
        _dtcCodes.value = codes
        addLog("Manual DTC Scan result: $codes")
        codes
    }

    suspend fun manualClearDtc(): Boolean = withContext(Dispatchers.IO) {
        if (!connectionManager.isConnected) return@withContext false
        val resp = connectionManager.sendCommand(ObdProtocol.CMD_CLEAR_DTC, 1200)
        val success = !resp.contains("ERROR")
        if (success) {
            _dtcCodes.value = emptyList()
            _isMilActive.value = false
            addLog("ECU trouble codes cleared.")
        }
        success
    }

    /**
     * Realistic sequential OBD2 ELM327 PID polling loop (~2 to 4 Hz real transmission rate)
     */
    private suspend fun startPollingLoop(protocol: String) {
        var cycle = 0
        while (serviceScope.isActive && connectionManager.isConnected) {
            cycle++

            // Primary loop: RPM and Speed
            val rpmRaw = connectionManager.sendCommand(ObdProtocol.CMD_RPM, 300)
            val rpmVal = ObdProtocol.parseRpm(rpmRaw)

            val spdRaw = connectionManager.sendCommand(ObdProtocol.CMD_SPEED, 300)
            val spdVal = ObdProtocol.parseSpeed(spdRaw)

            // Coolant & Load every 2 cycles
            var ectVal = _telemetryState.value.coolantTempC
            var loadVal = _telemetryState.value.engineLoadPercent
            if (cycle % 2 == 0) {
                val ectRaw = connectionManager.sendCommand(ObdProtocol.CMD_COOLANT, 350)
                ectVal = ObdProtocol.parseCoolant(ectRaw) ?: ectVal

                val loadRaw = connectionManager.sendCommand(ObdProtocol.CMD_ENGINE_LOAD, 350)
                loadVal = ObdProtocol.parseEngineLoad(loadRaw) ?: loadVal
            }

            // Throttle, MAF, Intake Air every 4 cycles
            var tpsVal = _telemetryState.value.throttlePercent
            var iatVal = _telemetryState.value.intakeTempC
            var mafVal = _telemetryState.value.mafGs
            var boostVal = _telemetryState.value.turboBoostBar
            if (cycle % 4 == 0) {
                val tpsRaw = connectionManager.sendCommand(ObdProtocol.CMD_THROTTLE, 300)
                tpsVal = ObdProtocol.parseThrottle(tpsRaw) ?: tpsVal

                val mafRaw = connectionManager.sendCommand(ObdProtocol.CMD_MAF, 350)
                mafVal = ObdProtocol.parseMaf(mafRaw) ?: mafVal

                val mapRaw = connectionManager.sendCommand(ObdProtocol.CMD_MAP_BOOST, 350)
                boostVal = ObdProtocol.parseBoost(mapRaw) ?: boostVal

                val iatRaw = connectionManager.sendCommand(ObdProtocol.CMD_INTAKE_TEMP, 300)
                iatVal = ObdProtocol.parseIat(iatRaw) ?: iatVal
            }

            // Voltage & Fuel Level every 15 cycles (slow-changing metrics)
            var voltVal = _telemetryState.value.voltage
            var fuelVal = _telemetryState.value.fuelLevelPercent
            if (cycle % 15 == 0) {
                val voltRaw = connectionManager.sendCommand(ObdProtocol.CMD_VOLTAGE, 350)
                voltVal = ObdProtocol.parseVoltage(voltRaw) ?: voltVal

                val fuelRaw = connectionManager.sendCommand(ObdProtocol.CMD_FUEL_LEVEL, 400)
                fuelVal = ObdProtocol.parseFuelLevel(fuelRaw) ?: fuelVal
            }

            _telemetryState.value = ParsedObdMetrics(
                speedKmh = spdVal ?: _telemetryState.value.speedKmh,
                rpm = rpmVal ?: _telemetryState.value.rpm,
                coolantTempC = ectVal ?: _telemetryState.value.coolantTempC,
                engineLoadPercent = loadVal ?: _telemetryState.value.engineLoadPercent,
                throttlePercent = tpsVal,
                intakeTempC = iatVal,
                mafGs = mafVal,
                turboBoostBar = boostVal,
                fuelLevelPercent = fuelVal,
                voltage = voltVal,
                protocolName = protocol
            )

            // Inter-command pause for ELM327 UART buffer settling
            delay(80)
        }

        if (!connectionManager.isConnected) {
            handleConnectionLost()
        }
    }

    private fun handleConnectionLost() {
        _connectionStatus.value = ObdConnectionStatus.ERROR
        addLog("OBD-II connection lost. Initiating automatic reconnect daemon...")
        scheduleReconnection()
    }

    private fun scheduleReconnection() {
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(5000)
            if (_connectionStatus.value != ObdConnectionStatus.CONNECTED) {
                addLog("Attempting auto-reconnection...")
                autoConnectIfPaired()
            }
        }
    }

    fun disconnect() {
        reconnectJob?.cancel()
        pollingJob?.cancel()
        connectionManager.disconnect()
        _connectionStatus.value = ObdConnectionStatus.DISCONNECTED
        _connectedDeviceName.value = "Disconnected"
        addLog("OBD-II adapter disconnected by user.")
    }

    override fun onDestroy() {
        disconnect()
        if (isReceiverRegistered) {
            try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        instance = null
        super.onDestroy()
    }
}
