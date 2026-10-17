package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import com.example.model.ObdConnectionType
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

data class ParsedObdMetrics(
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantTempC: Int? = null,
    val engineLoadPercent: Int? = null,
    val throttlePercent: Int? = null,
    val intakeTempC: Int? = null,
    val mafGs: Float? = null,
    val turboBoostBar: Float? = null,
    val fuelLevelPercent: Int? = null,
    val voltage: Float? = null,
    val protocolName: String? = null
)

class RealObdManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollingJob: Job? = null
    private var currentTransport: ObdTransport? = null
    private var lastSuccessfulDevice: ObdAdapterDevice? = null

    private val _connectionStatus = MutableStateFlow(ObdConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ObdConnectionStatus> = _connectionStatus.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow("None")
    val connectedDeviceName: StateFlow<String> = _connectedDeviceName.asStateFlow()

    private val _liveMetrics = MutableStateFlow(ParsedObdMetrics())
    val liveMetrics: StateFlow<ParsedObdMetrics> = _liveMetrics.asStateFlow()

    private val _diagnosticLogs = MutableStateFlow<List<String>>(
        listOf(
            "OBD-II CAN subsystem initialized",
            "Checking paired Bluetooth adapters..."
        )
    )
    val diagnosticLogs: StateFlow<List<String>> = _diagnosticLogs.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<ObdAdapterDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ObdAdapterDevice>> = _discoveredDevices.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    init {
        refreshDevices()
        observeTelemetryService()
    }

    /**
     * Observes background ObdTelemetryService if active to mirror its status and logs
     */
    private fun observeTelemetryService() {
        scope.launch {
            while (true) {
                val service = ObdTelemetryService.instance
                if (service != null) {
                    launch {
                        service.telemetryState.collect { m ->
                            _liveMetrics.value = m
                        }
                    }
                    launch {
                        service.connectionStatus.collect { s ->
                            _connectionStatus.value = s
                        }
                    }
                    launch {
                        service.connectedDeviceName.collect { n ->
                            _connectedDeviceName.value = n
                        }
                    }
                    launch {
                        service.diagnosticLogs.collect { logs ->
                            _diagnosticLogs.value = logs
                        }
                    }
                    break
                }
                delay(1000)
            }
        }
    }

    private fun addLog(entry: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
        _diagnosticLogs.value = (_diagnosticLogs.value + "[$timestamp] $entry").takeLast(35)
    }

    /**
     * Automatically attempts to connect to paired car adapter without requiring user intervention
     */
    @SuppressLint("MissingPermission")
    fun autoConnectIfAvailable() {
        val service = ObdTelemetryService.instance
        if (service != null) {
            service.autoConnectIfPaired()
            return
        }

        scope.launch {
            delay(500)
            if (_connectionStatus.value == ObdConnectionStatus.CONNECTED ||
                _connectionStatus.value == ObdConnectionStatus.CONNECTING) {
                return@launch
            }

            val devices = _discoveredDevices.value
            val target = lastSuccessfulDevice ?: devices.firstOrNull { d ->
                val name = d.name.lowercase()
                ObdBluetoothConnectionManager.OBD_NAME_KEYWORDS.any { name.contains(it) }
            } ?: devices.firstOrNull { it.type == ObdConnectionType.BLUETOOTH_CLASSIC && it.isPaired }

            if (target != null) {
                addLog("Auto-connecting to detected adapter: ${target.name} [${target.address}]")
                connect(target)
            } else {
                addLog("No paired OBD-II adapter found. Please pair in Android Bluetooth settings.")
            }
        }
    }

    /**
     * Scans and loads real paired and available adapters from Bluetooth & Wi-Fi
     */
    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        val list = mutableListOf<ObdAdapterDevice>()

        // 1. Real Bluetooth Paired Devices
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                val name = device.name ?: "Unknown Bluetooth"
                val isObd = ObdBluetoothConnectionManager.OBD_NAME_KEYWORDS.any { name.lowercase().contains(it) }

                list.add(
                    ObdAdapterDevice(
                        id = device.address,
                        name = name,
                        address = device.address,
                        type = ObdConnectionType.BLUETOOTH_CLASSIC,
                        signalDbm = if (isObd) -42 else -65,
                        isPaired = true,
                        protocol = "SAE J1979 / CAN",
                        isRealHardware = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("RealObd", "Error reading bonded devices: ${e.message}")
        }

        // 2. Add standard Wi-Fi Gateway adapter option (192.168.0.10:35000)
        list.add(
            ObdAdapterDevice(
                id = "wifi_elm327",
                name = "ELM327 Wi-Fi Gateway (192.168.0.10)",
                address = "192.168.0.10:35000",
                type = ObdConnectionType.WIFI,
                signalDbm = -40,
                isPaired = false,
                protocol = "TCP/IP Socket CAN",
                ipAddress = "192.168.0.10",
                port = 35000,
                isRealHardware = true
            )
        )

        _discoveredDevices.value = list.sortedByDescending { it.type == ObdConnectionType.BLUETOOTH_CLASSIC && it.isPaired }
    }

    /**
     * Connects to a real Bluetooth device or Wi-Fi adapter
     */
    @SuppressLint("MissingPermission")
    fun connect(adapterDevice: ObdAdapterDevice) {
        val service = ObdTelemetryService.instance
        if (adapterDevice.type == ObdConnectionType.BLUETOOTH_CLASSIC && service != null) {
            service.connect(adapterDevice)
            return
        }

        disconnect()
        _connectionStatus.value = ObdConnectionStatus.CONNECTING
        _connectedDeviceName.value = adapterDevice.name
        lastSuccessfulDevice = adapterDevice
        addLog("Connecting to ${adapterDevice.name} [${adapterDevice.address}]...")

        pollingJob = scope.launch {
            val transport: ObdTransport = when (adapterDevice.type) {
                ObdConnectionType.WIFI -> {
                    WifiObdTransport(adapterDevice.ipAddress, adapterDevice.port)
                }
                else -> {
                    val btDevice = try {
                        bluetoothAdapter?.getRemoteDevice(adapterDevice.address)
                    } catch (_: Exception) { null }

                    if (btDevice != null) {
                        BluetoothClassicObdTransport(btDevice)
                    } else {
                        WifiObdTransport(adapterDevice.ipAddress, adapterDevice.port)
                    }
                }
            }

            currentTransport = transport

            // 1. Establish socket connection
            val connected = withContext(Dispatchers.IO) { transport.connect() }
            if (!connected) {
                addLog("Failed to establish transport socket with ${adapterDevice.name}")
                _connectionStatus.value = ObdConnectionStatus.ERROR
                return@launch
            }

            addLog("Socket connected! Initializing ELM327 protocol handshake...")
            _connectionStatus.value = ObdConnectionStatus.INITIALIZING_ELM

            // 2. Run ELM327 Initialization sequence
            for (cmd in ObdProtocol.INIT_COMMANDS) {
                if (!isActive) break
                val resp = withContext(Dispatchers.IO) { transport.sendCommand(cmd, timeoutMs = 1500) }
                val clean = ObdProtocol.cleanResponse(resp)
                addLog("TX: $cmd -> RX: $clean")
                if (cmd == "ATZ") delay(800) else delay(50)
            }

            val protoResp = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_PROTOCOL, 1000) }
            val protoName = ObdProtocol.cleanResponse(protoResp).ifEmpty { "ISO 15765-4 CAN" }
            addLog("Active CAN Protocol: $protoName")

            _connectionStatus.value = ObdConnectionStatus.CONNECTED
            addLog("Vehicle ECU linked! Starting live SAE J1979 PID streaming...")

            // 3. High-frequency PID polling loop
            runPollingLoop(transport, protoName)
        }
    }

    private suspend fun runPollingLoop(transport: ObdTransport, protocol: String) {
        var loopCount = 0
        while (scope.isActive && transport.isConnected) {
            loopCount++

            val rpmRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_RPM, 350) }
            val rpmVal = ObdProtocol.parseRpm(rpmRaw)

            val spdRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_SPEED, 350) }
            val spdVal = ObdProtocol.parseSpeed(spdRaw)

            val ectRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_COOLANT, 400) }
            val ectVal = ObdProtocol.parseCoolant(ectRaw)

            val loadRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_ENGINE_LOAD, 400) }
            val loadVal = ObdProtocol.parseEngineLoad(loadRaw)

            var tpsVal = _liveMetrics.value.throttlePercent
            var iatVal = _liveMetrics.value.intakeTempC
            var mafVal = _liveMetrics.value.mafGs
            var boostVal = _liveMetrics.value.turboBoostBar
            var fuelVal = _liveMetrics.value.fuelLevelPercent
            var voltVal = _liveMetrics.value.voltage

            if (loopCount % 3 == 0) {
                val tpsRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_THROTTLE, 300) }
                tpsVal = ObdProtocol.parseThrottle(tpsRaw) ?: tpsVal

                val iatRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_INTAKE_TEMP, 300) }
                iatVal = ObdProtocol.parseIat(iatRaw) ?: iatVal

                val mafRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_MAF, 350) }
                mafVal = ObdProtocol.parseMaf(mafRaw) ?: mafVal

                val mapRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_MAP_BOOST, 350) }
                boostVal = ObdProtocol.parseBoost(mapRaw) ?: boostVal
            }

            if (loopCount % 10 == 0) {
                val voltRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_VOLTAGE, 400) }
                voltVal = ObdProtocol.parseVoltage(voltRaw) ?: voltVal

                val fuelRaw = withContext(Dispatchers.IO) { transport.sendCommand(ObdProtocol.CMD_FUEL_LEVEL, 400) }
                fuelVal = ObdProtocol.parseFuelLevel(fuelRaw) ?: fuelVal
            }

            _liveMetrics.value = ParsedObdMetrics(
                speedKmh = spdVal ?: _liveMetrics.value.speedKmh,
                rpm = rpmVal ?: _liveMetrics.value.rpm,
                coolantTempC = ectVal ?: _liveMetrics.value.coolantTempC,
                engineLoadPercent = loadVal ?: _liveMetrics.value.engineLoadPercent,
                throttlePercent = tpsVal,
                intakeTempC = iatVal,
                mafGs = mafVal,
                turboBoostBar = boostVal,
                fuelLevelPercent = fuelVal,
                voltage = voltVal,
                protocolName = protocol
            )

            delay(50)
        }

        if (!transport.isConnected) {
            addLog("Connection to adapter lost.")
            _connectionStatus.value = ObdConnectionStatus.ERROR
        }
    }

    fun disconnect() {
        val service = ObdTelemetryService.instance
        service?.disconnect()
        pollingJob?.cancel()
        pollingJob = null
        currentTransport?.disconnect()
        currentTransport = null
        _connectionStatus.value = ObdConnectionStatus.DISCONNECTED
        _connectedDeviceName.value = "None"
        addLog("OBD-II adapter disconnected")
    }
}
