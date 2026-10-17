package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID

interface ObdTransport {
    fun connect(): Boolean
    fun sendCommand(cmd: String, timeoutMs: Long = 1200): String
    fun disconnect()
    val isConnected: Boolean
    val description: String
}

/**
 * Real Bluetooth Classic SPP Transport (RFCOMM)
 */
class BluetoothClassicObdTransport(
    private val device: BluetoothDevice
) : ObdTransport {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override var isConnected: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    override val description: String = "${device.name ?: "Unknown"} [${device.address}]"

    @SuppressLint("MissingPermission")
    override fun connect(): Boolean {
        disconnect()
        return try {
            socket = try {
                device.createInsecureRfcommSocketToServiceRecord(sppUuid)
            } catch (e: Exception) {
                device.createRfcommSocketToServiceRecord(sppUuid)
            }
            socket?.connect()
            inputStream = socket?.inputStream
            outputStream = socket?.outputStream
            isConnected = socket?.isConnected == true
            isConnected
        } catch (e: Exception) {
            Log.e("BluetoothObd", "Connection failed: ${e.message}")
            disconnect()
            false
        }
    }

    override fun sendCommand(cmd: String, timeoutMs: Long): String {
        val out = outputStream ?: return "ERROR: NOT CONNECTED"
        val inStream = inputStream ?: return "ERROR: NOT CONNECTED"

        return try {
            // ELM327 expects \r or \r\n
            val bytes = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
            out.write(bytes)
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
                            break // ELM327 finished response
                        }
                    }
                } else {
                    Thread.sleep(15)
                }
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e("BluetoothObd", "Send failed: ${e.message}")
            disconnect()
            "ERROR: CONNECTION LOST"
        }
    }

    override fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        isConnected = false
    }
}

/**
 * Real Wi-Fi TCP Socket Transport (ELM327 Wi-Fi gateway)
 */
class WifiObdTransport(
    private val host: String = "192.168.0.10",
    private val port: Int = 35000
) : ObdTransport {

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    override var isConnected: Boolean = false
        private set

    override val description: String = "Wi-Fi Gateway [$host:$port]"

    override fun connect(): Boolean {
        disconnect()
        return try {
            socket = Socket()
            socket?.connect(InetSocketAddress(host, port), 2500)
            socket?.soTimeout = 2000
            inputStream = socket?.getInputStream()
            outputStream = socket?.getOutputStream()
            isConnected = socket?.isConnected == true
            isConnected
        } catch (e: Exception) {
            Log.e("WifiObd", "Connection to $host:$port failed: ${e.message}")
            disconnect()
            false
        }
    }

    override fun sendCommand(cmd: String, timeoutMs: Long): String {
        val out = outputStream ?: return "ERROR: NOT CONNECTED"
        val inStream = inputStream ?: return "ERROR: NOT CONNECTED"

        return try {
            val bytes = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
            out.write(bytes)
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
                    Thread.sleep(15)
                }
            }
            sb.toString()
        } catch (e: Exception) {
            Log.e("WifiObd", "Send failed: ${e.message}")
            disconnect()
            "ERROR: CONNECTION LOST"
        }
    }

    override fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        isConnected = false
    }
}
