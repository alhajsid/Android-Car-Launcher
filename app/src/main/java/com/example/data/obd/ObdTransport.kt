package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

interface ObdTransport {
    fun connect(): Boolean
    fun sendCommand(cmd: String, timeoutMs: Long = 1200): String
    suspend fun executeCommand(cmd: String, timeoutMs: Long = 1200): String {
        return withContext(Dispatchers.IO) {
            sendCommand(cmd, timeoutMs)
        }
    }
    fun disconnect()
    val isConnected: Boolean
    val description: String
}

/**
 * Shared helper for draining stale unread bytes from an InputStream before issuing a new ELM327 command,
 * ensuring timed-out responses from earlier commands cannot corrupt the current command's response.
 */
internal fun drainStaleInput(inStream: InputStream, scratchBuffer: ByteArray) {
    try {
        var drained = 0
        while (inStream.available() > 0 && drained < 4096) {
            val read = inStream.read(scratchBuffer, 0, minOf(scratchBuffer.size, inStream.available()))
            if (read <= 0) break
            drained += read
        }
    } catch (_: Exception) {
    }
}

/**
 * Real Bluetooth Classic SPP Transport (RFCOMM) with serialized command execution.
 */
class BluetoothClassicObdTransport(
    private val device: BluetoothDevice
) : ObdTransport {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val commandMutex = Mutex()
    private val ioLock = Any()
    private val sessionGen = AtomicInteger(0)

    @Volatile
    private var socket: BluetoothSocket? = null
    @Volatile
    private var inputStream: InputStream? = null
    @Volatile
    private var outputStream: OutputStream? = null

    override var isConnected: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    override val description: String = "${device.name ?: "Unknown"} [${device.address}]"

    @SuppressLint("MissingPermission")
    override fun connect(): Boolean {
        disconnect()
        val expectedGen = sessionGen.incrementAndGet()
        return try {
            val candidateSocket = try {
                device.createInsecureRfcommSocketToServiceRecord(sppUuid)
            } catch (e: Exception) {
                device.createRfcommSocketToServiceRecord(sppUuid)
            }
            candidateSocket.connect()
            if (sessionGen.get() != expectedGen) {
                try { candidateSocket.close() } catch (_: Exception) {}
                return false
            }
            socket = candidateSocket
            inputStream = candidateSocket.inputStream
            outputStream = candidateSocket.outputStream
            isConnected = candidateSocket.isConnected
            if (isConnected) {
                inputStream?.let { drainStaleInput(it, ByteArray(256)) }
            }
            isConnected
        } catch (e: Exception) {
            Log.e("BluetoothObd", "Connection failed: ${e.message}")
            disconnect()
            false
        }
    }

    override suspend fun executeCommand(cmd: String, timeoutMs: Long): String = commandMutex.withLock {
        withContext(Dispatchers.IO) {
            val gen = sessionGen.get()
            val out = outputStream ?: return@withContext "ERROR: NOT CONNECTED"
            val inStream = inputStream ?: return@withContext "ERROR: NOT CONNECTED"
            if (!isConnected) return@withContext "ERROR: NOT CONNECTED"

            try {
                val buffer = ByteArray(256)
                drainStaleInput(inStream, buffer)

                val bytes = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
                synchronized(ioLock) {
                    out.write(bytes)
                    out.flush()
                }

                val sb = StringBuilder(64)
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    if (sessionGen.get() != gen || !isConnected) {
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
                val result = sb.toString()
                if (result.isEmpty()) "ERROR: TIMEOUT" else result
            } catch (e: Exception) {
                Log.e("BluetoothObd", "Command '$cmd' failed: ${e.message}")
                disconnect()
                "ERROR: CONNECTION LOST"
            }
        }
    }

    override fun sendCommand(cmd: String, timeoutMs: Long): String = synchronized(ioLock) {
        val gen = sessionGen.get()
        val out = outputStream ?: return "ERROR: NOT CONNECTED"
        val inStream = inputStream ?: return "ERROR: NOT CONNECTED"
        if (!isConnected) return "ERROR: NOT CONNECTED"

        return try {
            val buffer = ByteArray(256)
            drainStaleInput(inStream, buffer)

            val bytes = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
            out.write(bytes)
            out.flush()

            val sb = StringBuilder(64)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (sessionGen.get() != gen || !isConnected) {
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
            val result = sb.toString()
            if (result.isEmpty()) "ERROR: TIMEOUT" else result
        } catch (e: Exception) {
            Log.e("BluetoothObd", "Send failed: ${e.message}")
            disconnect()
            "ERROR: CONNECTION LOST"
        }
    }

    override fun disconnect() {
        sessionGen.incrementAndGet()
        isConnected = false
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }
}

/**
 * Real Bluetooth Low Energy (BLE GATT) Transport for known ELM327 BLE UART profiles:
 * - Vgate iCar Pro / Veepeak BLE+ (Service FFF0, Write FFF2, Notify FFF1)
 * - HM-10 / KONNWEI / Viecar BLE (Service FFE0, Write/Notify FFE1)
 * - Nordic UART Service (Service 6E400001, Write 6E400002, Notify 6E400003)
 * - Microchip / ISSC Transparent UART (Service 49535343-FE7D-..., Write 49535343-8841-..., Notify 49535343-1E4D-...)
 *
 * If an adapter uses a proprietary/unsupported GATT profile, connect() cleanly releases GATT
 * and exposes a descriptive [unsupportedReason].
 */
class BleGattObdTransport(
    private val context: Context,
    private val device: BluetoothDevice
) : ObdTransport {

    data class BleProfileSpec(
        val name: String,
        val serviceUuid: UUID,
        val writeCharUuid: UUID,
        val notifyCharUuid: UUID
    )

    companion object {
        private const val TAG = "BleGattObd"
        val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val KNOWN_BLE_PROFILES = listOf(
            BleProfileSpec(
                name = "ELM327 BLE FFF0 (Vgate/Veepeak/Carista)",
                serviceUuid = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb"),
                writeCharUuid = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb"),
                notifyCharUuid = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
            ),
            BleProfileSpec(
                name = "ELM327 BLE FFE0 (Konnwei/Viecar/HM-10)",
                serviceUuid = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"),
                writeCharUuid = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"),
                notifyCharUuid = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
            ),
            BleProfileSpec(
                name = "Nordic UART BLE",
                serviceUuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
                writeCharUuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
                notifyCharUuid = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
            ),
            BleProfileSpec(
                name = "ISSC Transparent UART BLE",
                serviceUuid = UUID.fromString("49535343-fe7d-4ae5-8fa9-9fafd205e455"),
                writeCharUuid = UUID.fromString("49535343-8841-43f4-a8d4-ecbe34729bb3"),
                notifyCharUuid = UUID.fromString("49535343-1e4d-4bd9-ba61-23c647249616")
            )
        )
    }

    private val commandMutex = Mutex()
    private val responseLock = Object()
    private val responseBuffer = StringBuilder(128)
    private val sessionGen = AtomicInteger(0)

    @Volatile
    private var bluetoothGatt: BluetoothGatt? = null
    @Volatile
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    @Volatile
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null

    @Volatile
    var unsupportedReason: String? = null
        private set

    @Volatile
    var matchedProfileName: String? = null
        private set

    override var isConnected: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    override val description: String = "${device.name ?: "BLE OBD"} [${device.address}] (BLE GATT)"

    @SuppressLint("MissingPermission")
    override fun connect(): Boolean {
        disconnect()
        unsupportedReason = null
        matchedProfileName = null
        val expectedGen = sessionGen.incrementAndGet()
        val handshakeLatch = CountDownLatch(1)
        var connectedSuccess = false

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (sessionGen.get() != expectedGen) {
                    try { gatt.close() } catch (_: Exception) {}
                    handshakeLatch.countDown()
                    return
                }
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    isConnected = false
                    synchronized(responseLock) {
                        responseLock.notifyAll()
                    }
                    try { gatt.close() } catch (_: Exception) {}
                    if (bluetoothGatt === gatt) {
                        bluetoothGatt = null
                    }
                    handshakeLatch.countDown()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    unsupportedReason = "BLE service discovery failed (status $status)."
                    handshakeLatch.countDown()
                    return
                }

                var foundProfile: BleProfileSpec? = null
                var foundWrite: BluetoothGattCharacteristic? = null
                var foundNotify: BluetoothGattCharacteristic? = null

                for (profile in KNOWN_BLE_PROFILES) {
                    val service = gatt.getService(profile.serviceUuid) ?: continue
                    val wChar = service.getCharacteristic(profile.writeCharUuid)
                    val nChar = service.getCharacteristic(profile.notifyCharUuid)
                    if (wChar != null && nChar != null) {
                        foundProfile = profile
                        foundWrite = wChar
                        foundNotify = nChar
                        break
                    }
                }

                if (foundProfile == null || foundWrite == null || foundNotify == null) {
                    val discoveredUuids = gatt.services.joinToString(", ") { it.uuid.toString().take(8) }
                    unsupportedReason =
                        "Unsupported BLE GATT adapter profile. Expected ELM327 UART service (FFF0/FFE0/NUS), found: ${discoveredUuids.ifEmpty { "none" }}."
                    Log.w(TAG, unsupportedReason ?: "Unsupported BLE profile")
                    handshakeLatch.countDown()
                    return
                }

                matchedProfileName = foundProfile.name
                writeCharacteristic = foundWrite
                notifyCharacteristic = foundNotify

                gatt.setCharacteristicNotification(foundNotify, true)
                val cccd = foundNotify.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
                if (cccd != null) {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    val wroteDesc = gatt.writeDescriptor(cccd)
                    if (!wroteDesc) {
                        connectedSuccess = true
                        isConnected = true
                        handshakeLatch.countDown()
                    }
                } else {
                    connectedSuccess = true
                    isConnected = true
                    handshakeLatch.countDown()
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                if (descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIG_UUID) {
                    connectedSuccess = (status == BluetoothGatt.GATT_SUCCESS)
                    isConnected = connectedSuccess
                    if (!connectedSuccess) {
                        unsupportedReason = "BLE adapter rejected GATT notification subscription (status $status)."
                    }
                    handshakeLatch.countDown()
                }
            }

            @Deprecated("Deprecated in API 33")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val bytes = characteristic.value ?: return
                appendIncomingBytes(bytes)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                appendIncomingBytes(value)
            }
        }

        return try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
            val awaited = handshakeLatch.await(6500, TimeUnit.MILLISECONDS)
            if (!awaited || !connectedSuccess) {
                if (unsupportedReason == null) {
                    unsupportedReason = "BLE GATT connection timed out or failed."
                }
                disconnect()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            unsupportedReason = "BLE GATT error: ${e.message}"
            Log.e(TAG, "BLE connect exception: ${e.message}")
            disconnect()
            false
        }
    }

    private fun appendIncomingBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val chunk = String(bytes, Charsets.US_ASCII)
        synchronized(responseLock) {
            responseBuffer.append(chunk)
            if (chunk.contains('>')) {
                responseLock.notifyAll()
            }
        }
    }

    override suspend fun executeCommand(cmd: String, timeoutMs: Long): String = commandMutex.withLock {
        withContext(Dispatchers.IO) {
            sendCommandInternal(cmd, timeoutMs)
        }
    }

    override fun sendCommand(cmd: String, timeoutMs: Long): String {
        return sendCommandInternal(cmd, timeoutMs)
    }

    @SuppressLint("MissingPermission")
    private fun sendCommandInternal(cmd: String, timeoutMs: Long): String {
        val gatt = bluetoothGatt ?: return "ERROR: NOT CONNECTED"
        val writeChar = writeCharacteristic ?: return "ERROR: NOT CONNECTED"
        val gen = sessionGen.get()
        if (!isConnected) return "ERROR: NOT CONNECTED"

        synchronized(responseLock) {
            responseBuffer.setLength(0)
        }

        val payload = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
        val wrote = try {
            @Suppress("DEPRECATION")
            writeChar.value = payload
            writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(writeChar)
        } catch (e: Exception) {
            Log.e(TAG, "BLE write failed: ${e.message}")
            false
        }

        if (!wrote) {
            return "ERROR: BLE WRITE FAILED"
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(responseLock) {
            while (System.currentTimeMillis() < deadline) {
                if (sessionGen.get() != gen || !isConnected) {
                    return "ERROR: DISCONNECTED"
                }
                if (responseBuffer.indexOf(">") != -1) {
                    break
                }
                val remaining = deadline - System.currentTimeMillis()
                if (remaining > 0) {
                    try {
                        responseLock.wait(minOf(remaining, 30L))
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
            val resp = responseBuffer.toString()
            return if (resp.isEmpty()) "ERROR: TIMEOUT" else resp
        }
    }

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        sessionGen.incrementAndGet()
        isConnected = false
        val gatt = bluetoothGatt
        bluetoothGatt = null
        writeCharacteristic = null
        notifyCharacteristic = null
        if (gatt != null) {
            try {
                gatt.disconnect()
            } catch (_: Exception) {}
            try {
                gatt.close()
            } catch (_: Exception) {}
        }
        synchronized(responseLock) {
            responseBuffer.setLength(0)
            responseLock.notifyAll()
        }
    }
}

/**
 * Real Wi-Fi TCP Socket Transport (ELM327 Wi-Fi gateway) with serialized command execution.
 */
class WifiObdTransport(
    private val host: String = "192.168.0.10",
    private val port: Int = 35000
) : ObdTransport {

    private val commandMutex = Mutex()
    private val ioLock = Any()
    private val sessionGen = AtomicInteger(0)

    @Volatile
    private var socket: Socket? = null
    @Volatile
    private var inputStream: InputStream? = null
    @Volatile
    private var outputStream: OutputStream? = null

    override var isConnected: Boolean = false
        private set

    override val description: String = "Wi-Fi Gateway [$host:$port]"

    override fun connect(): Boolean {
        disconnect()
        val expectedGen = sessionGen.incrementAndGet()
        return try {
            val candidateSocket = Socket()
            candidateSocket.connect(InetSocketAddress(host, port), 2500)
            candidateSocket.soTimeout = 2000
            if (sessionGen.get() != expectedGen) {
                try { candidateSocket.close() } catch (_: Exception) {}
                return false
            }
            socket = candidateSocket
            inputStream = candidateSocket.getInputStream()
            outputStream = candidateSocket.getOutputStream()
            isConnected = candidateSocket.isConnected
            if (isConnected) {
                inputStream?.let { drainStaleInput(it, ByteArray(256)) }
            }
            isConnected
        } catch (e: Exception) {
            Log.e("WifiObd", "Connection to $host:$port failed: ${e.message}")
            disconnect()
            false
        }
    }

    override suspend fun executeCommand(cmd: String, timeoutMs: Long): String = commandMutex.withLock {
        withContext(Dispatchers.IO) {
            sendCommand(cmd, timeoutMs)
        }
    }

    override fun sendCommand(cmd: String, timeoutMs: Long): String = synchronized(ioLock) {
        val gen = sessionGen.get()
        val out = outputStream ?: return "ERROR: NOT CONNECTED"
        val inStream = inputStream ?: return "ERROR: NOT CONNECTED"
        if (!isConnected) return "ERROR: NOT CONNECTED"

        return try {
            val buffer = ByteArray(256)
            drainStaleInput(inStream, buffer)

            val bytes = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
            out.write(bytes)
            out.flush()

            val sb = StringBuilder(64)
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                if (sessionGen.get() != gen || !isConnected) {
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
            val result = sb.toString()
            if (result.isEmpty()) "ERROR: TIMEOUT" else result
        } catch (e: Exception) {
            Log.e("WifiObd", "Send failed: ${e.message}")
            disconnect()
            "ERROR: CONNECTION LOST"
        }
    }

    override fun disconnect() {
        sessionGen.incrementAndGet()
        isConnected = false
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
    }
}
