package com.rgbws2812.controller.model

import com.rgbws2812.controller.audio.MusicReactiveSettings

data class DeviceInfo(
    val name: String,
    val address: String,
    val bonded: Boolean,
    val transport: BluetoothTransport = BluetoothTransport.ClassicSpp,
    val preferred: Boolean = false
) {
    val displayName: String
        get() = if (name.isBlank()) "未命名设备" else name
}

enum class BluetoothTransport {
    BleUart,
    ClassicSpp
}

enum class BluetoothConnectionState {
    Disconnected,
    Connecting,
    Connected
}

data class BluetoothUiState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val isScanning: Boolean = false,
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.Disconnected,
    val pairedDevices: List<DeviceInfo> = emptyList(),
    val discoveredDevices: List<DeviceInfo> = emptyList(),
    val connectedDevice: DeviceInfo? = null,
    val statusMessage: String = "蓝牙未连接",
    val errorMessage: String? = null
)

data class SerialPortConfig(
    val baudRate: Int = 9600,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: SerialParity = SerialParity.None,
    val timeoutMs: Int = 50
) {
    fun clamped(): SerialPortConfig =
        copy(
            baudRate = baudRate.takeIf { it in SupportedBaudRates } ?: 9600,
            dataBits = dataBits.coerceIn(8, 9),
            stopBits = stopBits.coerceIn(1, 2),
            timeoutMs = timeoutMs.coerceIn(10, 1000)
        )

    companion object {
        val SupportedBaudRates = setOf(2400, 4800, 9600, 14400, 19200, 38400, 57600, 115200, 230400, 460800, 921600, 1000000)
    }
}

enum class SerialParity(val wireValue: Int, val title: String) {
    None(0, "无校验"),
    Odd(1, "奇校验"),
    Even(2, "偶校验")
}

data class Preset(
    val id: String,
    val name: String,
    val control: RgbControlState,
    val createdAt: Long,
    val updatedAt: Long
)

data class SendHistoryItem(
    val id: String,
    val timestamp: Long,
    val deviceName: String,
    val deviceAddress: String,
    val source: String,
    val summary: String,
    val hex: String
)

data class AppStorageState(
    val control: RgbControlState = RgbControlState.Default,
    val autoSendEnabled: Boolean = false,
    val showAdvancedSendPanel: Boolean = false,
    val useAdvancedFlowEditor: Boolean = false,
    val musicSettings: MusicReactiveSettings = MusicReactiveSettings(),
    val presets: List<Preset> = emptyList(),
    val history: List<SendHistoryItem> = emptyList()
)
