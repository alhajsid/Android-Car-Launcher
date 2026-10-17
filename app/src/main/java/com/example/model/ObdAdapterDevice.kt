package com.example.model

enum class ObdConnectionType(val label: String) {
    BLUETOOTH_CLASSIC("Bluetooth SPP"),
    BLUETOOTH_BLE("Bluetooth Low Energy"),
    WIFI("TCP/IP Socket")
}

enum class ObdConnectionStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    SCANNING("Scanning Devices..."),
    CONNECTING("Connecting..."),
    INITIALIZING_ELM("Handshake ELM327..."),
    BUS_INIT_CAN("Negotiating CAN..."),
    CONNECTED("Connected"),
    ERROR("Connection Failed")
}

data class ObdAdapterDevice(
    val id: String,
    val name: String,
    val address: String, // MAC address or IP:Port
    val type: ObdConnectionType,
    val isPaired: Boolean = false,
    val protocol: String = "ISO 15765-4 CAN",
    val latencyMs: Int = 15,
    val ipAddress: String = "192.168.0.10",
    val port: Int = 35000,
    val isRealHardware: Boolean = true
)
