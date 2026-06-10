package com.rgbws2812.controller.model

data class DeviceInfo(
    val name: String,
    val address: String,
    val bonded: Boolean
) {
    val displayName: String
        get() = if (name.isBlank()) "未命名设备" else name
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
    val presets: List<Preset> = emptyList(),
    val history: List<SendHistoryItem> = emptyList()
)
