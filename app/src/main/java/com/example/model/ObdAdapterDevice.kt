package com.example.model

enum class ObdConnectionType {
    BLUETOOTH_CLASSIC,
    BLUETOOTH_BLE,
    WIFI
}

enum class ObdConnectionStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    INITIALIZING_ELM,
    BUS_INIT_CAN,
    CONNECTED,
    ERROR
}

data class ObdAdapterDevice(
    val id: String,
    val name: String,
    val address: String, // MAC address or IP:Port
    val type: ObdConnectionType,
    val signalDbm: Int = -50,
    val isPaired: Boolean = false,
    val protocol: String = "ISO 15765-4 CAN",
    val latencyMs: Int = 15,
    val ipAddress: String = "192.168.0.10",
    val port: Int = 35000,
    val isRealHardware: Boolean = true
)
