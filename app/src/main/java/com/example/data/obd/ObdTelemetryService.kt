package com.example.data.obd

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.IBinder
import android.util.Log
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

/**
 * Background Android Service for OBD2 ELM327 communications.
 * Automatically handles Bluetooth pairing events, reconnection, and real-time PID streaming.
 */
class ObdTelemetryService : Service() {

    companion object {
        private const val TAG = "ObdTelemetryService"
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
        listOf("OBD-II Telemetry Service initialized.")
    )
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): ObdTelemetryService = this@ObdTelemetryService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        instance = this
        connectionManager = ObdBluetoothConnectionManager(applicationContext)

        registerBluetoothReceiver()
        autoConnectIfPaired()
    }

    private fun addLog(entry: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        _diagnosticLogs.value = (_diagnosticLogs.value + "[$timestamp] $entry").takeLast(35)
        Log.d(TAG, entry)
    }

    /**
     * Registers system broadcast receiver to react to Bluetooth pairing and hardware state changes
     */
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
                // When an OBD2 device completes pairing in phone Bluetooth settings
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)

                    if (bondState == BluetoothDevice.BOND_BONDED && device != null) {
                        addLog("New Bluetooth device paired: ${device.name ?: device.address}")
                        if (connectionManager.isLikelyObdAdapter(device.name)) {
                            addLog("Detected OBD2 adapter pairing! Initiating auto-connection...")
                            autoConnectIfPaired()
                        }
                    }
                }

                // When car ignition powers on adapter and ACL link connects
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && connectionManager.isLikelyObdAdapter(device.name)) {
                        if (_connectionStatus.value != ObdConnectionStatus.CONNECTED &&
                            _connectionStatus.value != ObdConnectionStatus.CONNECTING) {
                            addLog("OBD adapter ACL link detected (${device.name}). Auto-linking CAN...")
                            autoConnectIfPaired()
                        }
                    }
                }

                // When signal is lost or car ignition turns off
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && device.address == connectionManager.activeDevice.value?.address) {
                        addLog("Vehicle link disconnected: ${device.name}")
                        handleConnectionLost()
                    }
                }

                // When user turns on Bluetooth in Android Quick Settings
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_ON) {
                        addLog("Bluetooth turned ON. Checking paired vehicle adapters...")
                        autoConnectIfPaired()
                    }
                }
            }
        }
    }

    /**
     * Automatically discovers paired OBD adapters and connects without manual intervention
     */
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
                addLog("No paired ELM327 OBD2 adapter found. Pair adapter in phone Bluetooth settings.")
            }
        }
    }

    /**
     * Connects to a specific adapter device
     */
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
            addLog("Socket linked! Running ELM327 handshake commands...")

            // Run handshake with delay after ATZ
            for (cmd in ObdProtocol.INIT_COMMANDS) {
                if (!isActive) break
                val resp = connectionManager.sendCommand(cmd, timeoutMs = 1500)
                val clean = ObdProtocol.cleanResponse(resp)
                addLog("TX: $cmd -> RX: $clean")
                if (cmd == "ATZ") {
                    delay(800) // Allow ELM327 chip reset settling time
                } else {
                    delay(50)
                }
            }

            val protoResp = connectionManager.sendCommand(ObdProtocol.CMD_PROTOCOL, 1000)
            val protocolName = ObdProtocol.cleanResponse(protoResp).ifEmpty { "ISO 15765-4 CAN" }
            addLog("CAN Bus Active Protocol: $protocolName")

            _connectionStatus.value = ObdConnectionStatus.CONNECTED
            addLog("Vehicle ECU Handshake Complete. Starting high-frequency PID polling...")

            startPollingLoop(protocolName)
        }
    }

    private suspend fun startPollingLoop(protocol: String) {
        var cycle = 0
        while (serviceScope.isActive && connectionManager.isConnected) {
            cycle++

            // Fast loop: RPM (010C) & Speed (010D)
            val rpmRaw = connectionManager.sendCommand(ObdProtocol.CMD_RPM, 350)
            val rpmVal = ObdProtocol.parseRpm(rpmRaw)

            val spdRaw = connectionManager.sendCommand(ObdProtocol.CMD_SPEED, 350)
            val spdVal = ObdProtocol.parseSpeed(spdRaw)

            val ectRaw = connectionManager.sendCommand(ObdProtocol.CMD_COOLANT, 400)
            val ectVal = ObdProtocol.parseCoolant(ectRaw)

            val loadRaw = connectionManager.sendCommand(ObdProtocol.CMD_ENGINE_LOAD, 400)
            val loadVal = ObdProtocol.parseEngineLoad(loadRaw)

            var tpsVal = _telemetryState.value.throttlePercent
            var iatVal = _telemetryState.value.intakeTempC
            var mafVal = _telemetryState.value.mafGs
            var boostVal = _telemetryState.value.turboBoostBar
            var fuelVal = _telemetryState.value.fuelLevelPercent
            var voltVal = _telemetryState.value.voltage

            if (cycle % 3 == 0) {
                val tpsRaw = connectionManager.sendCommand(ObdProtocol.CMD_THROTTLE, 300)
                tpsVal = ObdProtocol.parseThrottle(tpsRaw) ?: tpsVal

                val iatRaw = connectionManager.sendCommand(ObdProtocol.CMD_INTAKE_TEMP, 300)
                iatVal = ObdProtocol.parseIat(iatRaw) ?: iatVal

                val mafRaw = connectionManager.sendCommand(ObdProtocol.CMD_MAF, 350)
                mafVal = ObdProtocol.parseMaf(mafRaw) ?: mafVal

                val mapRaw = connectionManager.sendCommand(ObdProtocol.CMD_MAP_BOOST, 350)
                boostVal = ObdProtocol.parseBoost(mapRaw) ?: boostVal
            }

            if (cycle % 10 == 0) {
                val voltRaw = connectionManager.sendCommand(ObdProtocol.CMD_VOLTAGE, 400)
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

            if (cycle % 12 == 0 && (rpmVal != null || spdVal != null)) {
                addLog("CAN DATA: RPM=${rpmVal ?: "-"} | SPD=${spdVal ?: "-"} km/h | ECT=${ectVal ?: "-"}°C")
            }

            delay(50) // ~15 Hz high refresh rate
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
