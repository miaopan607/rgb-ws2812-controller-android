package com.rgbws2812.controller

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rgbws2812.controller.bluetooth.BluetoothSppClient
import com.rgbws2812.controller.data.AppStorage
import com.rgbws2812.controller.model.AppStorageState
import com.rgbws2812.controller.model.BluetoothConnectionState
import com.rgbws2812.controller.model.BluetoothUiState
import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.DeviceInfo
import com.rgbws2812.controller.model.Preset
import com.rgbws2812.controller.model.RgbControlState
import com.rgbws2812.controller.model.SendHistoryItem
import com.rgbws2812.controller.protocol.RgbFrame
import com.rgbws2812.controller.protocol.RgbFrameBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MainUiState(
    val control: RgbControlState = RgbControlState.Default,
    val frame: RgbFrame = RgbFrameBuilder.build(RgbControlState.Default),
    val orderValid: Boolean = true,
    val autoSendEnabled: Boolean = false,
    val presets: List<Preset> = emptyList(),
    val history: List<SendHistoryItem> = emptyList(),
    val bluetooth: BluetoothUiState = BluetoothUiState(),
    val manualHex: String = "",
    val importExportText: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)
    private val bluetoothClient = BluetoothSppClient(application)
    private val manualState = MutableStateFlow(ManualUiState())
    private var autoSendJob: Job? = null

    val uiState: StateFlow<MainUiState> =
        combine(storage.state, bluetoothClient.state, manualState) { storageState, bluetoothState, manual ->
            val frame = RgbFrameBuilder.build(storageState.control)
            val orderValid = RgbFrameBuilder.isValidOrder(storageState.control.order)
            MainUiState(
                control = storageState.control,
                frame = frame,
                orderValid = orderValid,
                autoSendEnabled = storageState.autoSendEnabled,
                presets = storageState.presets,
                history = storageState.history,
                bluetooth = bluetoothState,
                manualHex = manual.manualHex,
                importExportText = manual.importExportText,
                statusMessage = manual.statusMessage,
                errorMessage = manual.errorMessage ?: bluetoothState.errorMessage
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainUiState()
        )

    fun refreshBluetooth() {
        bluetoothClient.refreshPairedDevices()
    }

    fun startDiscovery() {
        bluetoothClient.startDiscovery()
    }

    fun stopDiscovery() {
        bluetoothClient.stopDiscovery()
    }

    fun connect(device: DeviceInfo) {
        bluetoothClient.connect(device)
    }

    fun disconnect() {
        bluetoothClient.disconnect()
    }

    fun updateMode(mode: ControlMode) = updateControl { it.copy(mode = mode) }

    fun updateColor(red: Int, green: Int, blue: Int) =
        updateControl { it.copy(red = red, green = green, blue = blue) }

    fun updateBrightness(value: Int) =
        updateControl { it.copy(brightness = value.coerceIn(0, 255)) }

    fun updatePeriod(value: Int) =
        updateControl { it.copy(period = value.coerceIn(1, 255)) }

    fun toggleOrderLed(led: Int) {
        if (led !in 0..7) return
        updateControl { current ->
            val mutable = current.order.toMutableList()
            if (mutable.contains(led)) {
                mutable.remove(led)
            } else if (mutable.size < 8) {
                mutable.add(led)
            }
            current.copy(order = mutable)
        }
    }

    fun setOrder(order: List<Int>) {
        updateControl { it.copy(order = order.filter { value -> value in 0..7 }.distinct().take(8)) }
    }

    fun setAutoSend(enabled: Boolean) {
        viewModelScope.launch {
            storage.saveAutoSend(enabled)
            manualState.update { it.copy(statusMessage = if (enabled) "自动发送已开启" else "自动发送已关闭") }
            if (enabled) scheduleAutoSend()
        }
    }

    fun sendCurrent() {
        viewModelScope.launch {
            val state = uiState.value
            sendFrame(state.frame, "手动发送")
        }
    }

    fun updateManualHex(text: String) {
        manualState.update { it.copy(manualHex = text) }
    }

    fun loadCurrentFrameToManualHex() {
        manualState.update { it.copy(manualHex = uiState.value.frame.spacedHex(), statusMessage = "已填入当前帧") }
    }

    fun sendManualHex() {
        viewModelScope.launch {
            val parsed = runCatching { RgbFrameBuilder.parseHex(uiState.value.manualHex) }
            parsed.onSuccess { frame ->
                sendFrame(frame, "手动 Hex")
            }.onFailure { throwable ->
                manualState.update { it.copy(errorMessage = throwable.message ?: "Hex 解析失败") }
            }
        }
    }

    fun savePreset(name: String) {
        val cleanName = name.trim().ifBlank { "预设 ${uiState.value.presets.size + 1}" }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val preset = Preset(
                id = UUID.randomUUID().toString(),
                name = cleanName,
                control = uiState.value.control,
                createdAt = now,
                updatedAt = now
            )
            storage.savePresets(listOf(preset) + uiState.value.presets)
            manualState.update { it.copy(statusMessage = "已保存预设：$cleanName") }
        }
    }

    fun loadPreset(preset: Preset) {
        viewModelScope.launch {
            storage.saveControl(preset.control)
            manualState.update { it.copy(statusMessage = "已加载预设：${preset.name}") }
            scheduleAutoSend()
        }
    }

    fun renamePreset(preset: Preset, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            val updated = uiState.value.presets.map {
                if (it.id == preset.id) it.copy(name = cleanName, updatedAt = System.currentTimeMillis()) else it
            }
            storage.savePresets(updated)
            manualState.update { it.copy(statusMessage = "预设已重命名") }
        }
    }

    fun deletePreset(preset: Preset) {
        viewModelScope.launch {
            storage.savePresets(uiState.value.presets.filterNot { it.id == preset.id })
            manualState.update { it.copy(statusMessage = "已删除预设：${preset.name}") }
        }
    }

    fun resendHistory(item: SendHistoryItem) {
        viewModelScope.launch {
            val parsed = runCatching { RgbFrameBuilder.parseHex(item.hex) }
            parsed.onSuccess { frame ->
                sendFrame(frame, "历史重发")
            }.onFailure { throwable ->
                manualState.update { it.copy(errorMessage = throwable.message ?: "历史帧无效") }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            storage.saveHistory(emptyList())
            manualState.update { it.copy(statusMessage = "历史已清空") }
        }
    }

    fun exportData() {
        val state = uiState.value
        val text = storage.exportPortableData(state.presets, state.history)
        manualState.update { it.copy(importExportText = text, statusMessage = "已生成导出 JSON") }
    }

    fun updateImportExportText(text: String) {
        manualState.update { it.copy(importExportText = text) }
    }

    fun importData() {
        viewModelScope.launch {
            val result = runCatching { storage.importPortableData(uiState.value.importExportText) }
            result.onSuccess { (presets, history) ->
                storage.replacePortableData(presets, history)
                manualState.update { it.copy(statusMessage = "导入完成：${presets.size} 个预设，${history.size} 条历史") }
            }.onFailure { throwable ->
                manualState.update { it.copy(errorMessage = throwable.message ?: "导入失败") }
            }
        }
    }

    fun clearMessages() {
        bluetoothClient.clearError()
        manualState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    override fun onCleared() {
        bluetoothClient.release()
        super.onCleared()
    }

    private fun updateControl(transform: (RgbControlState) -> RgbControlState) {
        viewModelScope.launch {
            val current = uiState.value.control
            storage.saveControl(transform(current).clamped())
            scheduleAutoSend()
        }
    }

    private fun scheduleAutoSend() {
        autoSendJob?.cancel()
        val state = uiState.value
        if (!state.autoSendEnabled ||
            !state.orderValid ||
            state.bluetooth.connectionState != BluetoothConnectionState.Connected
        ) {
            return
        }
        autoSendJob = viewModelScope.launch {
            delay(220)
            sendFrame(uiState.value.frame, "自动发送")
        }
    }

    private suspend fun sendFrame(frame: RgbFrame, source: String) {
        val state = uiState.value
        if (!RgbFrameBuilder.isValidOrder(frame.order)) {
            manualState.update { it.copy(errorMessage = "流水灯序无效：请按顺序点满 8 个灯。") }
            return
        }

        if (state.bluetooth.connectionState != BluetoothConnectionState.Connected) {
            manualState.update { it.copy(errorMessage = "请先连接蓝牙设备") }
            return
        }

        val result = bluetoothClient.send(frame.bytes)
        result.onSuccess {
            appendHistory(frame, source)
            manualState.update { it.copy(statusMessage = "$source 成功：${frame.spacedHex()}", errorMessage = null) }
        }.onFailure { throwable ->
            manualState.update { it.copy(errorMessage = throwable.message ?: "发送失败") }
        }
    }

    private suspend fun appendHistory(frame: RgbFrame, source: String) {
        val state = uiState.value
        val device = state.bluetooth.connectedDevice
        val item = SendHistoryItem(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            deviceName = device?.displayName.orEmpty(),
            deviceAddress = device?.address.orEmpty(),
            source = source,
            summary = "${frame.mode.title} 亮度 ${frame.brightness}",
            hex = frame.spacedHex()
        )
        storage.saveHistory((listOf(item) + state.history).take(AppStorage.MaxHistoryItems))
    }

    private data class ManualUiState(
        val manualHex: String = "",
        val importExportText: String = "",
        val statusMessage: String? = null,
        val errorMessage: String? = null
    )
}
