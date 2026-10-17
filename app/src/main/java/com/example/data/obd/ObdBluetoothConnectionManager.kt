package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import com.example.model.ObdConnectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Manages Bluetooth Classic RFCOMM SPP socket lifecycle, adapter filtering,
 * and command transmissions for real OBD2 ELM327 adapters.
 */
class ObdBluetoothConnectionManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ObdBluetoothMgr"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        
        // Common OBD2 adapter name identifiers
        val OBD_NAME_KEYWORDS = listOf(
            "obd", "elm", "vlink", "link", "car", "torque",
            "veepeak", "bafx", "konnwei", "icar", "scan", "viecar",
            "vgate", "obdlink", "kw902", "nexpeak"
        )
    }

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    val bluetoothAdapter: BluetoothAdapter? by lazy {
        bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    private var activeSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _connectionStatus = MutableStateFlow(ObdConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ObdConnectionStatus> = _connectionStatus.asStateFlow()

    private val _activeDevice = MutableStateFlow<ObdAdapterDevice?>(null)
    val activeDevice: StateFlow<ObdAdapterDevice?> = _activeDevice.asStateFlow()

    val isConnected: Boolean
        get() = activeSocket?.isConnected == true && _connectionStatus.value == ObdConnectionStatus.CONNECTED

    /**
     * Checks if Bluetooth hardware is available and enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Returns paired/bonded devices filtered and scored for OBD2 capabilities
     */
    @SuppressLint("MissingPermission")
    fun getPairedObdDevices(): List<ObdAdapterDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        val list = mutableListOf<ObdAdapterDevice>()

        try {
            adapter.bondedDevices?.forEach { device ->
                val name = device.name ?: "Unknown Bluetooth"
                val isObd = isLikelyObdAdapter(name)

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
            Log.e(TAG, "Error enumerating bonded devices: ${e.message}")
        }

        // Sort likely OBD adapters first
        return list.sortedByDescending { isLikelyObdAdapter(it.name) }
    }

    /**
     * Determines if a Bluetooth device is likely an OBD-II adapter based on name
     */
    fun isLikelyObdAdapter(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return OBD_NAME_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Establishes RFCOMM socket connection to the target Bluetooth device
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: ObdAdapterDevice): Boolean = withContext(Dispatchers.IO) {
        disconnect()
        _connectionStatus.value = ObdConnectionStatus.CONNECTING
        _activeDevice.value = device

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is disabled or unavailable")
            _connectionStatus.value = ObdConnectionStatus.ERROR
            return@withContext false
        }

        // Always cancel active discovery before establishing RFCOMM socket
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (_: Exception) {}

        val bluetoothDevice = try {
            adapter.getRemoteDevice(device.address)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid Bluetooth address: ${device.address}", e)
            _connectionStatus.value = ObdConnectionStatus.ERROR
            return@withContext false
        }

        var socket: BluetoothSocket? = null

        // Strategy 1: Standard Insecure RFCOMM (compatible with 99% of ELM327 dongles)
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
                    val method = bluetoothDevice.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    socket = method.invoke(bluetoothDevice, 1) as? BluetoothSocket
                    socket?.connect()
                } catch (e3: Exception) {
                    Log.e(TAG, "All RFCOMM socket attempts failed: ${e3.message}")
                    try { socket?.close() } catch (_: Exception) {}
                    _connectionStatus.value = ObdConnectionStatus.ERROR
                    return@withContext false
                }
            }
        }

        if (socket?.isConnected == true) {
            activeSocket = socket
            inputStream = socket.inputStream
            outputStream = socket.outputStream
            _connectionStatus.value = ObdConnectionStatus.CONNECTED
            Log.i(TAG, "Successfully connected to ${device.name} [${device.address}]")
            return@withContext true
        } else {
            disconnect()
            _connectionStatus.value = ObdConnectionStatus.ERROR
            return@withContext false
        }
    }

    /**
     * Sends an ELM327 ASCII command and reads until the '>' prompt character
     */
    fun sendCommand(cmd: String, timeoutMs: Long = 1200): String {
        val out = outputStream ?: return "ERROR: NOT CONNECTED"
        val inStream = inputStream ?: return "ERROR: NOT CONNECTED"

        return try {
            val payload = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
            out.write(payload)
            out.flush()

            val sb = StringBuilder()
            val startTime = System.currentTimeMillis()
            val buffer = ByteArray(256)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (inStream.available() > 0) {
                    val count = inStream.read(buffer)
                    if (count > 0) {
                        val chunk = String(buffer, 0, count, Charsets.US_ASCII)
                        sb.append(chunk)
                        if (chunk.contains(">")) {
                            break
                        }
                    }
                } else {
                    Thread.sleep(12)
                }
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Command send error: ${e.message}")
            disconnect()
            "ERROR: LOST CONNECTION"
        }
    }

    /**
     * Closes the active Bluetooth socket and streams
     */
    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            activeSocket?.close()
        } catch (_: Exception) {}
        activeSocket = null
        inputStream = null
        outputStream = null
        _connectionStatus.value = ObdConnectionStatus.DISCONNECTED
    }
}
