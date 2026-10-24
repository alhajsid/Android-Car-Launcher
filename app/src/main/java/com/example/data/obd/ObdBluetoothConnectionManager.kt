package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import com.example.model.ObdConnectionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages Bluetooth Classic RFCOMM SPP and BLE GATT lifecycle, adapter filtering,
 * and serialized atomic command transactions for real OBD2 ELM327 adapters.
 */
class ObdBluetoothConnectionManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ObdBluetoothMgr"
        private const val BLE_SCAN_TIMEOUT_MS = 12_000L
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // Specific OBD2 adapter name identifiers (excludes generic "car" or "link" substrings that match car stereos)
        val OBD_NAME_KEYWORDS = listOf(
            "obd", "elm", "elm327", "vlink", "v-link", "ios-vlink",
            "obdlink", "torque", "veepeak", "bafx", "konnwei",
            "icar", "viecar", "vgate", "kw902", "nexpeak", "carista",
            "obdii", "obd2", "scanmatik", "bluedriver"
        )
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Single serialized command executor lock per active connection
    private val commandMutex = Mutex()
    private val ioLock = Any()
    private val sessionGeneration = AtomicInteger(0)

    @Volatile
    private var activeSocket: BluetoothSocket? = null
    @Volatile
    private var inputStream: InputStream? = null
    @Volatile
    private var outputStream: OutputStream? = null
    @Volatile
    private var activeBleTransport: BleGattObdTransport? = null

    // Retained BLE scan callback and timeout job so scans are always stopped cleanly
    @Volatile
    private var activeScanCallback: ScanCallback? = null
    private var bleScanTimeoutJob: Job? = null

    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ObdConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ObdConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activeDevice = MutableStateFlow<ObdAdapterDevice?>(null)
    val activeDevice: StateFlow<ObdAdapterDevice?> = _activeDevice.asStateFlow()

    private val _discoveredBleDevices = MutableStateFlow<List<ObdAdapterDevice>>(emptyList())
    val discoveredBleDevices: StateFlow<List<ObdAdapterDevice>> = _discoveredBleDevices.asStateFlow()

    private val _lastErrorReason = MutableStateFlow<String?>(null)
    val lastErrorReason: StateFlow<String?> = _lastErrorReason.asStateFlow()

    val isConnected: Boolean
        get() {
            val classicOk = activeSocket?.isConnected == true
            val bleOk = activeBleTransport?.isConnected == true
            return (classicOk || bleOk) && _connectionStatus.value == ObdConnectionStatus.CONNECTED
        }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Returns paired/bonded devices filtered and scored for OBD2 capabilities.
     */
    @SuppressLint("MissingPermission")
    fun getPairedObdDevices(): List<ObdAdapterDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        val list = mutableListOf<ObdAdapterDevice>()

        try {
            adapter.bondedDevices?.forEach { device ->
                val name = device.name ?: "Unknown Bluetooth"
                val isObd = isLikelyObdAdapter(name)
                val isBleOnly = device.type == BluetoothDevice.DEVICE_TYPE_LE

                list.add(
                    ObdAdapterDevice(
                        id = device.address,
                        name = name,
                        address = device.address,
                        type = if (isBleOnly) ObdConnectionType.BLUETOOTH_BLE else ObdConnectionType.BLUETOOTH_CLASSIC,
                        isPaired = true,
                        protocol = when {
                            isBleOnly && isObd -> "BLE GATT ELM327"
                            isBleOnly -> "BLE Peripheral"
                            isObd -> "SAE J1979 / RFCOMM SPP"
                            else -> "Bluetooth Classic Device"
                        },
                        isRealHardware = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating bonded devices: ${e.message}")
        }

        return list.sortedByDescending { isLikelyObdAdapter(it.name) }
    }

    /**
     * Determines if a Bluetooth device is confidently identified as an OBD-II adapter based on name.
     */
    fun isLikelyObdAdapter(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.trim().lowercase()
        return OBD_NAME_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Starts a bounded BLE scan for nearby low-energy OBD adapters (Vgate, Veepeak BLE, Konnwei).
     * Retains the ScanCallback reference and automatically stops scanning after [BLE_SCAN_TIMEOUT_MS].
     */
    @SuppressLint("MissingPermission")
    fun startBleScan() {
        stopBleScan()
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return
        try {
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val device = result?.device ?: return
                    val name = device.name ?: result.scanRecord?.deviceName ?: return
                    if (isLikelyObdAdapter(name)) {
                        val current = _discoveredBleDevices.value.toMutableList()
                        if (current.none { it.address == device.address }) {
                            current.add(
                                ObdAdapterDevice(
                                    id = device.address,
                                    name = name,
                                    address = device.address,
                                    type = ObdConnectionType.BLUETOOTH_BLE,
                                    isPaired = device.bondState == BluetoothDevice.BOND_BONDED,
                                    protocol = "BLE GATT UART (FFF0/FFE0/NUS)",
                                    isRealHardware = true
                                )
                            )
                            _discoveredBleDevices.value = current
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w(TAG, "BLE scan failed with code: $errorCode")
                    _isBleScanning.value = false
                }
            }
            activeScanCallback = callback
            _isBleScanning.value = true
            scanner.startScan(callback)

            bleScanTimeoutJob = managerScope.launch {
                delay(BLE_SCAN_TIMEOUT_MS)
                stopBleScan()
            }
        } catch (e: Exception) {
            Log.d(TAG, "BLE scan not available: ${e.message}")
            _isBleScanning.value = false
            activeScanCallback = null
        }
    }

    /**
     * Stops any active BLE scan and releases the retained ScanCallback.
     */
    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        bleScanTimeoutJob?.cancel()
        bleScanTimeoutJob = null
        val callback = activeScanCallback
        activeScanCallback = null
        _isBleScanning.value = false
        if (callback != null) {
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
            } catch (e: Exception) {
                Log.d(TAG, "Stop BLE scan ignored: ${e.message}")
            }
        }
    }

    /**
     * Establishes either a BLE GATT transport (for BLUETOOTH_BLE adapters) or an RFCOMM socket
     * connection (for BLUETOOTH_CLASSIC adapters).
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: ObdAdapterDevice): Boolean = withContext(Dispatchers.IO) {
        stopBleScan()
        disconnect()

        val currentGen = sessionGeneration.incrementAndGet()
        _lastErrorReason.value = null
        _connectionStatus.value = ObdConnectionStatus.CONNECTING
        _activeDevice.value = device

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _lastErrorReason.value = "Bluetooth is disabled or unavailable."
            Log.e(TAG, "Bluetooth is disabled or unavailable")
            _connectionStatus.value = ObdConnectionStatus.ERROR
            return@withContext false
        }

        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (_: Exception) {}

        val bluetoothDevice = try {
            adapter.getRemoteDevice(device.address)
        } catch (e: Exception) {
            _lastErrorReason.value = "Invalid Bluetooth MAC address: ${device.address}"
            Log.e(TAG, "Invalid Bluetooth address: ${device.address}", e)
            _connectionStatus.value = ObdConnectionStatus.ERROR
            return@withContext false
        }

        // Route BLE adapters through real BLE GATT transport
        val isBleDevice = device.type == ObdConnectionType.BLUETOOTH_BLE ||
            bluetoothDevice.type == BluetoothDevice.DEVICE_TYPE_LE

        if (isBleDevice) {
            val bleTransport = BleGattObdTransport(context, bluetoothDevice)
            val connected = bleTransport.connect()
            if (sessionGeneration.get() != currentGen) {
                bleTransport.disconnect()
                return@withContext false
            }
            if (connected) {
                activeBleTransport = bleTransport
                _connectionStatus.value = ObdConnectionStatus.CONNECTED
                Log.i(TAG, "Connected via BLE GATT (${bleTransport.matchedProfileName}) to ${device.name}")
                return@withContext true
            } else {
                val reason = bleTransport.unsupportedReason
                    ?: "BLE GATT connection failed for ${device.name}."
                _lastErrorReason.value = reason
                bleTransport.disconnect()
                // If dual-mode device, only fall back to RFCOMM if it was not strictly LE-only
                if (bluetoothDevice.type == BluetoothDevice.DEVICE_TYPE_LE) {
                    _connectionStatus.value = ObdConnectionStatus.ERROR
                    return@withContext false
                }
                Log.w(TAG, "$reason — attempting Classic RFCOMM fallback for dual-mode adapter...")
            }
        }

        var socket: BluetoothSocket? = null

        // Strategy 1: Standard Insecure RFCOMM (compatible with 99% of ELM327 Classic dongles)
        try {
            socket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
        } catch (e1: Exception) {
            Log.w(TAG, "Insecure RFCOMM failed (${e1.message}), attempting Secure RFCOMM...")
            try { socket?.close() } catch (_: Exception) {}

            // Strategy 2: Standard Secure RFCOMM
            try {
                socket = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            } catch (e2: Exception) {
                Log.w(TAG, "Secure RFCOMM failed (${e2.message}), attempting reflection channel 1...")
                try { socket?.close() } catch (_: Exception) {}

                // Strategy 3: Hidden channel fallback (port 1 RFCOMM reflection for clones)
                try {
                    val method = bluetoothDevice.javaClass.getMethod(
                        "createRfcommSocket",
                        Int::class.javaPrimitiveType
                    )
                    socket = method.invoke(bluetoothDevice, 1) as? BluetoothSocket
                    socket?.connect()
                } catch (e3: Exception) {
                    _lastErrorReason.value = "RFCOMM SPP connection refused by ${device.name}: ${e3.message}"
                    Log.e(TAG, "All RFCOMM socket attempts failed: ${e3.message}")
                    try { socket?.close() } catch (_: Exception) {}
                    _connectionStatus.value = ObdConnectionStatus.ERROR
                    return@withContext false
                }
            }
        }

        if (sessionGeneration.get() != currentGen) {
            try { socket?.close() } catch (_: Exception) {}
            return@withContext false
        }

        if (socket?.isConnected == true) {
            activeSocket = socket
            inputStream = socket.inputStream
            outputStream = socket.outputStream
            inputStream?.let { drainStaleInput(it, ByteArray(256)) }
            _connectionStatus.value = ObdConnectionStatus.CONNECTED
            Log.i(TAG, "Successfully connected via RFCOMM to ${device.name} [${device.address}]")
            return@withContext true
        } else {
            disconnect()
            _connectionStatus.value = ObdConnectionStatus.ERROR
            return@withContext false
        }
    }

    /**
     * Serialized, cancellation-aware coroutine command executor.
     * Guarantees each write/read transaction is atomic across initialization, polling,
     * manual DTC scan, and manual DTC clear.
     */
    suspend fun executeCommand(cmd: String, timeoutMs: Long = 1000): String = commandMutex.withLock {
        currentCoroutineContext().ensureActive()
        val expectedGen = sessionGeneration.get()

        val ble = activeBleTransport
        if (ble != null && ble.isConnected) {
            return@withLock ble.executeCommand(cmd, timeoutMs)
        }

        withContext(Dispatchers.IO) {
            val out = outputStream ?: return@withContext "ERROR: NOT CONNECTED"
            val inStream = inputStream ?: return@withContext "ERROR: NOT CONNECTED"
            if (activeSocket?.isConnected != true) return@withContext "ERROR: NOT CONNECTED"

            try {
                val buffer = ByteArray(256)
                // Drain any late bytes from an earlier timed-out command before writing
                drainStaleInput(inStream, buffer)

                val payload = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
                synchronized(ioLock) {
                    out.write(payload)
                    out.flush()
                }

                val sb = StringBuilder(64)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    currentCoroutineContext().ensureActive()
                    if (sessionGeneration.get() != expectedGen || activeSocket?.isConnected != true) {
                        return@withContext "ERROR: DISCONNECTED"
                    }
                    val avail = inStream.available()
                    if (avail > 0) {
                        val count = inStream.read(buffer, 0, minOf(buffer.size, avail))
                        if (count > 0) {
                            val chunk = String(buffer, 0, count, Charsets.US_ASCII)
                            sb.append(chunk)
                            if (chunk.contains('>')) {
                                break
                            }
                        }
                    } else {
                        delay(12)
                    }
                }
                val response = sb.toString()
                if (response.isEmpty()) "ERROR: TIMEOUT" else response
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Command '$cmd' execution error: ${e.message}")
                disconnect()
                "ERROR: LOST CONNECTION"
            }
        }
    }

    /**
     * Synchronous fallback command sender (serialized on ioLock).
     * Prefer [executeCommand] from coroutines.
     */
    fun sendCommand(cmd: String, timeoutMs: Long = 1000): String = synchronized(ioLock) {
        val expectedGen = sessionGeneration.get()
        val ble = activeBleTransport
        if (ble != null && ble.isConnected) {
            return ble.sendCommand(cmd, timeoutMs)
        }

        val out = outputStream ?: return "ERROR: NOT CONNECTED"
        val inStream = inputStream ?: return "ERROR: NOT CONNECTED"
        if (activeSocket?.isConnected != true) return "ERROR: NOT CONNECTED"

        return try {
            val buffer = ByteArray(256)
            drainStaleInput(inStream, buffer)

            val payload = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
            out.write(payload)
            out.flush()

            val sb = StringBuilder(64)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (sessionGeneration.get() != expectedGen || activeSocket?.isConnected != true) {
                    return "ERROR: DISCONNECTED"
                }
                val avail = inStream.available()
                if (avail > 0) {
                    val count = inStream.read(buffer, 0, minOf(buffer.size, avail))
                    if (count > 0) {
                        val chunk = String(buffer, 0, count, Charsets.US_ASCII)
                        sb.append(chunk)
                        if (chunk.contains('>')) {
                            break
                        }
                    }
                } else {
                    Thread.sleep(12)
                }
            }
            val response = sb.toString()
            if (response.isEmpty()) "ERROR: TIMEOUT" else response
        } catch (e: Exception) {
            Log.e(TAG, "Command send error: ${e.message}")
            disconnect()
            "ERROR: LOST CONNECTION"
        }
    }

    fun disconnect() {
        sessionGeneration.incrementAndGet()
        stopBleScan()
        try {
            activeBleTransport?.disconnect()
        } catch (_: Exception) {}
        activeBleTransport = null

        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null
        inputStream = null
        outputStream = null
        _connectionStatus.value = ObdConnectionStatus.DISCONNECTED
    }
}
