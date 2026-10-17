package com.example.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.example.model.ObdAdapterDevice
import com.example.model.ObdConnectionStatus
import com.example.model.ObdConnectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RealObdManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val connectionStatus: StateFlow<ObdConnectionStatus>
        get() = ObdTelemetryService.instance?.connectionStatus ?: _fallbackStatus

    val connectedDeviceName: StateFlow<String>
        get() = ObdTelemetryService.instance?.connectedDeviceName ?: _fallbackDeviceName

    val liveMetrics: StateFlow<ParsedObdMetrics>
        get() = ObdTelemetryService.instance?.telemetryState ?: _fallbackMetrics

    val diagnosticLogs: StateFlow<List<String>>
        get() = ObdTelemetryService.instance?.diagnosticLogs ?: _fallbackLogs

    val dtcCodes: StateFlow<List<String>>
        get() = ObdTelemetryService.instance?.dtcCodes ?: _fallbackDtc

    val isMilActive: StateFlow<Boolean>
        get() = ObdTelemetryService.instance?.isMilActive ?: _fallbackMil

    private val _fallbackStatus = MutableStateFlow(ObdConnectionStatus.DISCONNECTED)
    private val _fallbackDeviceName = MutableStateFlow("Disconnected")
    private val _fallbackMetrics = MutableStateFlow(ParsedObdMetrics())
    private val _fallbackLogs = MutableStateFlow(listOf("OBD-II subsystem initialized"))
    private val _fallbackDtc = MutableStateFlow<List<String>>(emptyList())
    private val _fallbackMil = MutableStateFlow(false)

    private val _discoveredDevices = MutableStateFlow<List<ObdAdapterDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<ObdAdapterDevice>> = _discoveredDevices.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
    }

    init {
        refreshDevices()
    }

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        val list = mutableListOf<ObdAdapterDevice>()

        // 1. Real Bluetooth Paired Devices
        try {
            bluetoothAdapter?.bondedDevices?.forEach { device ->
                val name = device.name ?: "Bluetooth Device"
                val isObd = ObdBluetoothConnectionManager.OBD_NAME_KEYWORDS.any { name.lowercase().contains(it) }

                list.add(
                    ObdAdapterDevice(
                        id = device.address,
                        name = name,
                        address = device.address,
                        type = if (device.type == android.bluetooth.BluetoothDevice.DEVICE_TYPE_LE)
                            ObdConnectionType.BLUETOOTH_BLE else ObdConnectionType.BLUETOOTH_CLASSIC,
                        isPaired = true,
                        protocol = if (isObd) "SAE J1979 / CAN" else "Standard Bluetooth",
                        isRealHardware = true
                    )
                )
            }
        } catch (_: Exception) {}

        // 2. Also incorporate any BLE adapters discovered by ObdBluetoothConnectionManager
        ObdTelemetryService.instance?.connectionManager?.discoveredBleDevices?.value?.let { bleList ->
            bleList.forEach { ble ->
                if (list.none { it.address == ble.address }) {
                    list.add(ble)
                }
            }
        }

        _discoveredDevices.value = list.sortedByDescending {
            ObdBluetoothConnectionManager.OBD_NAME_KEYWORDS.any { kw -> it.name.lowercase().contains(kw) }
        }
    }

    fun autoConnectIfAvailable() {
        ObdTelemetryService.instance?.autoConnectIfPaired()
    }

    fun connect(adapterDevice: ObdAdapterDevice) {
        val service = ObdTelemetryService.instance
        if (service != null) {
            service.connect(adapterDevice)
        }
    }

    fun disconnect() {
        ObdTelemetryService.instance?.disconnect()
    }

    suspend fun scanDtc(): List<String> {
        return ObdTelemetryService.instance?.manualScanDtc() ?: emptyList()
    }

    suspend fun clearDtc(): Boolean {
        return ObdTelemetryService.instance?.manualClearDtc() ?: false
    }
}
