package com.rgbws2812.controller

import android.app.Application
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rgbws2812.controller.audio.AudioLevelCapture
import com.rgbws2812.controller.audio.MediaProjectionPermission
import com.rgbws2812.controller.audio.MusicReactiveDetectionMode
import com.rgbws2812.controller.audio.MusicReactiveMapper
import com.rgbws2812.controller.audio.MusicReactiveAudioSource
import com.rgbws2812.controller.audio.MusicReactiveRuntimeState
import com.rgbws2812.controller.audio.MusicReactiveSettings
import com.rgbws2812.controller.bluetooth.BluetoothSppClient
import com.rgbws2812.controller.data.AppStorage
import com.rgbws2812.controller.model.AppStorageState
import com.rgbws2812.controller.model.BluetoothConnectionState
import com.rgbws2812.controller.model.BluetoothUiState
import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.DeviceInfo
import com.rgbws2812.controller.model.Preset
import com.rgbws2812.controller.model.RealtimeFlowFrame
import com.rgbws2812.controller.model.RgbControlState
import com.rgbws2812.controller.model.SendHistoryItem
import com.rgbws2812.controller.model.SerialParity
import com.rgbws2812.controller.model.SerialPortConfig
import com.rgbws2812.controller.protocol.RgbFrame
import com.rgbws2812.controller.protocol.RgbFrameBuilder
import com.rgbws2812.controller.protocol.RealtimeFrame
import com.rgbws2812.controller.protocol.RealtimeFrameBuilder
import com.rgbws2812.controller.protocol.RealtimeLed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MainUiState(
    val control: RgbControlState = RgbControlState.Default,
    val effectiveControl: RgbControlState = RgbControlState.Default,
    val frame: RgbFrame = RgbFrameBuilder.build(RgbControlState.Default),
    val orderValid: Boolean = true,
    val flowFramesValid: Boolean = true,
    val autoSendEnabled: Boolean = false,
    val showAdvancedSendPanel: Boolean = false,
    val useAdvancedFlowEditor: Boolean = false,
    val realtimeFlowAddCopiesLast: Boolean = false,
    val presets: List<Preset> = emptyList(),
    val history: List<SendHistoryItem> = emptyList(),
    val bluetooth: BluetoothUiState = BluetoothUiState(),
    val musicSettings: MusicReactiveSettings = MusicReactiveSettings(),
    val serialConfig: SerialPortConfig = SerialPortConfig(),
    val musicRuntime: MusicReactiveRuntimeState = MusicReactiveRuntimeState(),
    val realtimeFlowRunning: Boolean = false,
    val realtimeFlowSentFps: Int = 0,
    val realtimeFlowStatus: String = "自定义流水未启动",
    val manualHex: String = "",
    val importExportText: String = "",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = AppStorage(application)
    private val bluetoothClient = BluetoothSppClient(application)
    private val audioCapture = AudioLevelCapture(application, viewModelScope)
    private val manualState = MutableStateFlow(ManualUiState())
    private val musicState = MutableStateFlow(MusicUiState())
    @Volatile
    private var currentMusicSettings = MusicReactiveSettings()
    private var autoSendJob: Job? = null
    private var realtimeSendJob: Job? = null
    private var customRealtimeFlowJob: Job? = null
    private var realtimeSendInFlight = false
    private var customRealtimeFlowInFlight = false
    private var realtimeSequence = 0

    init {
        viewModelScope.launch {
            storage.state.collect { storageState ->
                currentMusicSettings = storageState.musicSettings.clamped()
            }
        }
    }

    val uiState: StateFlow<MainUiState> =
        combine(
            storage.state,
            bluetoothClient.state,
            manualState,
            musicState,
            audioCapture.state
        ) { storageState, bluetoothState, manual, music, audio ->
            val effectiveControl = effectiveControl(storageState)
            val frame = RgbFrameBuilder.build(effectiveControl)
            val orderValid = RgbFrameBuilder.isValidOrder(storageState.control.order)
            val flowFramesValid = RgbFrameBuilder.isValidFlowFrames(effectiveControl.flowFrames)
            val musicRuntime = MusicReactiveRuntimeState(
                isRunning = music.isRunning,
                level = audio.level,
                sentFps = music.sentFps,
                audioStatus = audio.statusMessage,
                status = music.status
            )
            MainUiState(
                control = storageState.control,
                effectiveControl = effectiveControl,
                frame = frame,
                orderValid = orderValid,
                flowFramesValid = flowFramesValid,
                autoSendEnabled = storageState.autoSendEnabled,
                showAdvancedSendPanel = storageState.showAdvancedSendPanel,
                useAdvancedFlowEditor = storageState.useAdvancedFlowEditor,
                realtimeFlowAddCopiesLast = storageState.realtimeFlowAddCopiesLast,
                presets = storageState.presets,
                history = storageState.history,
                bluetooth = bluetoothState,
                musicSettings = storageState.musicSettings,
                serialConfig = manual.serialConfig,
                musicRuntime = musicRuntime,
                realtimeFlowRunning = manual.realtimeFlowRunning,
                realtimeFlowSentFps = manual.realtimeFlowSentFps,
                realtimeFlowStatus = manual.realtimeFlowStatus,
                manualHex = manual.manualHex,
                importExportText = manual.importExportText,
                statusMessage = manual.statusMessage,
                errorMessage = manual.errorMessage ?: audio.errorMessage ?: bluetoothState.errorMessage
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

    fun setSerialBaudRate(value: Int) {
        manualState.update { it.copy(serialConfig = it.serialConfig.copy(baudRate = value).clamped()) }
    }

    fun setSerialDataBits(value: Int) {
        manualState.update { it.copy(serialConfig = it.serialConfig.copy(dataBits = value).clamped()) }
    }

    fun setSerialStopBits(value: Int) {
        manualState.update { it.copy(serialConfig = it.serialConfig.copy(stopBits = value).clamped()) }
    }

    fun setSerialParity(value: SerialParity) {
        manualState.update { it.copy(serialConfig = it.serialConfig.copy(parity = value).clamped()) }
    }

    fun applySerialConfig() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.bluetooth.connectionState != BluetoothConnectionState.Connected) {
                manualState.update { it.copy(errorMessage = "请先连接 CH9143BLE2U") }
                return@launch
            }
            val config = state.serialConfig.clamped()
            val result = bluetoothClient.configureSerialPort(config)
            result.onSuccess {
                manualState.update { it.copy(serialConfig = config) }
                manualState.update {
                    it.copy(
                        statusMessage = "已发送串口配置：${config.baudRate}, ${config.dataBits}N${config.stopBits}",
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                manualState.update { it.copy(errorMessage = throwable.message ?: "串口配置失败") }
            }
        }
    }

    fun updateMode(mode: ControlMode) {
        if (mode != ControlMode.MusicReactive) stopMusicReactive()
        if (mode != ControlMode.CustomRealtimeFlow) stopCustomRealtimeFlow()
        updateControl { it.copy(mode = mode) }
    }

    fun updateColor(red: Int, green: Int, blue: Int) =
        updateControl { it.copy(red = red, green = green, blue = blue) }

    fun updateBrightness(value: Int) =
        updateControl { it.copy(brightness = value.coerceIn(0, 255)) }

    fun updateActivePeriod(value: Int) =
        updateControl { current ->
            val cleanValue = value.coerceIn(1, 255)
            when (current.mode) {
                ControlMode.Flow -> current.copy(flowInterval = cleanValue)
                ControlMode.Breath -> current.copy(breathPeriod = cleanValue)
                ControlMode.Gradient,
                ControlMode.FlowGradient -> current.copy(gradientPeriod = cleanValue)
                ControlMode.CustomRealtimeFlow -> {
                    val frames = current.realtimeFlowFrames.toMutableList()
                    if (frames.isNotEmpty()) {
                        frames[0] = frames[0].copy(durationTicks = cleanValue)
                    }
                    current.copy(realtimeFlowFrames = frames)
                }
                else -> current
            }
        }

    fun toggleOrderLed(led: Int) {
        if (led !in 0..7) return
        updateControl { current ->
            val mutable = current.order.toMutableList()
            if (mutable.contains(led)) {
                mutable.remove(led)
            } else if (mutable.size < 8) {
                mutable.add(led)
            }
            val order = mutable.filter { it in 0..7 }.distinct().take(RgbControlState.MaxFlowFrames)
            current.copy(order = order)
        }
    }

    fun setOrder(order: List<Int>) {
        updateControl {
            val cleanOrder = order.filter { value -> value in 0..7 }.distinct().take(RgbControlState.MaxFlowFrames)
            it.copy(order = cleanOrder)
        }
    }

    fun generateFlowFramesFromOrder() {
        updateControl { current ->
            current.copy(flowFrames = RgbFrameBuilder.orderToFlowFrames(current.order))
        }
    }

    fun addFlowFrame() {
        updateControl { current ->
            if (current.flowFrames.size >= RgbControlState.MaxFlowFrames) {
                current
            } else {
                current.copy(flowFrames = current.flowFrames + 0)
            }
        }
    }

    fun deleteFlowFrame(index: Int) {
        updateControl { current ->
            if (index !in current.flowFrames.indices || current.flowFrames.size <= 1) {
                current
            } else {
                val frames = current.flowFrames.toMutableList()
                frames.removeAt(index)
                current.copy(flowFrames = frames.ifEmpty { listOf(0) })
            }
        }
    }

    fun moveFlowFrameUp(index: Int) {
        updateControl { current ->
            if (index !in 1..current.flowFrames.lastIndex) {
                current
            } else {
                val frames = current.flowFrames.toMutableList()
                val previous = frames[index - 1]
                frames[index - 1] = frames[index]
                frames[index] = previous
                current.copy(flowFrames = frames)
            }
        }
    }

    fun moveFlowFrameDown(index: Int) {
        updateControl { current ->
            if (index !in 0 until current.flowFrames.lastIndex) {
                current
            } else {
                val frames = current.flowFrames.toMutableList()
                val next = frames[index + 1]
                frames[index + 1] = frames[index]
                frames[index] = next
                current.copy(flowFrames = frames)
            }
        }
    }

    fun toggleFlowFrameLed(frameIndex: Int, led: Int) {
        if (frameIndex !in 0 until RgbControlState.MaxFlowFrames || led !in 0..7) return
        updateControl { current ->
            val frames = current.flowFrames.toMutableList()
            while (frames.size <= frameIndex) frames.add(0)
            frames[frameIndex] = frames[frameIndex] xor (1 shl led)
            current.copy(flowFrames = frames.take(RgbControlState.MaxFlowFrames))
        }
    }

    fun setFlowFrames(frames: List<Int>) {
        updateControl { it.copy(flowFrames = RgbControlState.sanitizeFlowFrames(frames)) }
    }

    fun addRealtimeFlowFrame() {
        updateRealtimeFlowFrames { frames ->
            val newFrame = if (uiState.value.realtimeFlowAddCopiesLast) frames.lastOrNull() ?: RealtimeFlowFrame() else RealtimeFlowFrame()
            frames.add(newFrame)
        }
    }

    fun duplicateRealtimeFlowFrame(index: Int) {
        updateRealtimeFlowFrames { frames ->
            if (index in frames.indices) {
                frames.add(index + 1, frames[index])
            }
        }
    }

    fun deleteRealtimeFlowFrame(index: Int) {
        updateRealtimeFlowFrames { frames ->
            if (frames.size > 1 && index in frames.indices) {
                frames.removeAt(index)
            }
        }
    }

    fun moveRealtimeFlowFrameUp(index: Int) {
        updateRealtimeFlowFrames { frames ->
            if (index in 1..frames.lastIndex) {
                frames.swap(index, index - 1)
            }
        }
    }

    fun moveRealtimeFlowFrameDown(index: Int) {
        updateRealtimeFlowFrames { frames ->
            if (index in 0 until frames.lastIndex) {
                frames.swap(index, index + 1)
            }
        }
    }

    fun setRealtimeFlowFrameDuration(index: Int, durationTicks: Int) {
        updateRealtimeFlowFrame(index) { it.copy(durationTicks = durationTicks.coerceIn(1, 255)) }
    }

    fun updateRealtimeFlowLed(frameIndex: Int, ledIndex: Int, led: RealtimeLed) {
        if (ledIndex !in 0 until RealtimeFrameBuilder.LedCount) return
        updateRealtimeFlowFrame(frameIndex) { frame ->
            val leds = RealtimeFlowFrame.sanitizeLeds(frame.leds).toMutableList()
            leds[ledIndex] = led.clamped()
            frame.copy(leds = leds)
        }
    }

    fun setAutoSend(enabled: Boolean) {
        viewModelScope.launch {
            storage.saveAutoSend(enabled)
            manualState.update { it.copy(statusMessage = if (enabled) "自动发送已开启" else "自动发送已关闭") }
            if (enabled) scheduleAutoSend()
        }
    }

    fun setShowAdvancedSendPanel(enabled: Boolean) {
        viewModelScope.launch {
            storage.saveShowAdvancedSendPanel(enabled)
            manualState.update { it.copy(statusMessage = if (enabled) "高级发送面板已显示" else "高级发送面板已隐藏") }
        }
    }

    fun setUseAdvancedFlowEditor(enabled: Boolean) {
        viewModelScope.launch {
            storage.saveUseAdvancedFlowEditor(enabled)
            scheduleAutoSend()
        }
    }

    fun setRealtimeFlowAddCopiesLast(enabled: Boolean) {
        viewModelScope.launch {
            storage.saveRealtimeFlowAddCopiesLast(enabled)
            manualState.update { it.copy(statusMessage = if (enabled) "添加画面将复制最后画面" else "添加画面将使用空画面") }
        }
    }

    fun sendCurrent() {
        viewModelScope.launch {
            val state = uiState.value
            if (state.control.mode == ControlMode.MusicReactive) {
                if (state.musicRuntime.isRunning) {
                    stopMusicReactive()
                } else {
                    startMusicReactive()
                }
                return@launch
            }
            if (state.control.mode == ControlMode.CustomRealtimeFlow) {
                if (state.realtimeFlowRunning) {
                    stopCustomRealtimeFlow()
                } else {
                    startCustomRealtimeFlow()
                }
                return@launch
            }
            sendFrame(state.frame, "手动发送")
        }
    }

    fun setMusicMaxBrightness(value: Int) {
        persistMusicSettings { it.copy(maxBrightness = value).clamped() }
    }

    fun setMusicSensitivity(value: Int) {
        persistMusicSettings { it.copy(sensitivity = value).clamped() }
    }

    fun setMusicPunch(value: Int) {
        persistMusicSettings { it.copy(punch = value).clamped() }
    }

    fun setMusicAmbientLimit(value: Int) {
        persistMusicSettings { it.copy(ambientLimit = value).clamped() }
    }

    fun setMusicDetectionMode(mode: MusicReactiveDetectionMode) {
        persistMusicSettings { it.copy(detectionMode = mode).clamped() }
    }

    fun setMusicAudioSource(source: MusicReactiveAudioSource) {
        val wasRunning = musicState.value.isRunning
        if (wasRunning) stopMusicReactive()
        persistMusicSettings { it.copy(audioSource = source).clamped() }
    }

    private fun persistMusicSettings(transform: (MusicReactiveSettings) -> MusicReactiveSettings) {
        val updated = transform(currentMusicSettings).clamped()
        currentMusicSettings = updated
        viewModelScope.launch {
            storage.saveMusicSettings(updated)
        }
        audioCapture.updateSettings(updated)
    }

    fun hasMicrophonePermission(): Boolean = audioCapture.hasMicrophonePermission()

    fun createSystemAudioCaptureIntent() =
        (getApplication<Application>().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
            .createScreenCaptureIntent()

    fun startMusicReactive(mediaProjectionPermission: MediaProjectionPermission? = null) {
        val state = uiState.value
        val cleanSettings = currentMusicSettings.clamped()
        val source = cleanSettings.audioSource
        if (!hasMicrophonePermission()) {
            manualState.update { it.copy(errorMessage = "需要录音权限后才能启动音乐律动") }
            return
        }
        if (source == MusicReactiveAudioSource.SystemPlayback) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                manualState.update { it.copy(errorMessage = "系统音频采集需要 Android 10 或更高版本") }
                return
            }
            if (mediaProjectionPermission == null) {
                manualState.update { it.copy(errorMessage = "需要授权系统音频采集后才能启动音乐律动") }
                return
            }
        }
        viewModelScope.launch {
            storage.saveControl(state.control.copy(mode = ControlMode.MusicReactive).clamped())
            storage.saveMusicSettings(cleanSettings)
        }
        currentMusicSettings = cleanSettings
        audioCapture.updateSettings(cleanSettings)
        musicState.update {
            it.copy(
                isRunning = true,
                sentFps = 0,
                sentThisSecond = 0,
                fpsWindowStartedAt = System.currentTimeMillis(),
                status = if (state.bluetooth.connectionState == BluetoothConnectionState.Connected) {
                    "${source.title} ${cleanSettings.detectionMode.title}运行中"
                } else {
                    "${source.title} ${cleanSettings.detectionMode.title}预览中，未连接蓝牙"
                }
            )
        }
        viewModelScope.launch {
            val started = audioCapture.start(source, mediaProjectionPermission)
            if (started) {
                startRealtimeSender()
            } else {
                realtimeSendJob?.cancel()
                realtimeSendJob = null
                realtimeSendInFlight = false
                musicState.update {
                    it.copy(
                        isRunning = false,
                        sentFps = 0,
                        sentThisSecond = 0,
                        status = "音乐律动启动失败"
                    )
                }
            }
        }
    }

    fun stopMusicReactive() {
        realtimeSendJob?.cancel()
        realtimeSendJob = null
        realtimeSendInFlight = false
        audioCapture.stop()
        musicState.update {
            it.copy(
                isRunning = false,
                sentFps = 0,
                sentThisSecond = 0,
                status = "音乐律动已停止"
            )
        }
    }

    fun stopCustomRealtimeFlow() {
        customRealtimeFlowJob?.cancel()
        customRealtimeFlowJob = null
        customRealtimeFlowInFlight = false
        manualState.update {
            it.copy(
                realtimeFlowRunning = false,
                realtimeFlowSentFps = 0,
                realtimeFlowSentThisSecond = 0,
                realtimeFlowStatus = "自定义流水已停止"
            )
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

    fun resetControlParameters() {
        viewModelScope.launch {
            storage.saveControl(RgbControlState.Default)
            manualState.update { it.copy(statusMessage = "参数已还原为默认值") }
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
        stopMusicReactive()
        audioCapture.release()
        stopCustomRealtimeFlow()
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
            !state.flowFramesValid ||
            state.control.mode == ControlMode.MusicReactive ||
            state.control.mode == ControlMode.CustomRealtimeFlow ||
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
        if (!RgbFrameBuilder.isValidFlowFrames(frame.flowFrames)) {
            manualState.update { it.copy(errorMessage = "流水画面无效：画面数量必须为 1..8。") }
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

    private fun startRealtimeSender() {
        realtimeSendJob?.cancel()
        realtimeSendJob = viewModelScope.launch {
            while (true) {
                val state = uiState.value
                val settings = currentMusicSettings.clamped()
                if (!state.musicRuntime.isRunning) {
                    return@launch
                }
                if (!realtimeSendInFlight) {
                    realtimeSendInFlight = true
                    val frame = RealtimeFrameBuilder.build(
                        leds = MusicReactiveMapper.ledsForLevel(state.musicRuntime.level),
                        maxBrightness = settings.maxBrightness,
                        sequence = realtimeSequence++
                    )
                    if (state.bluetooth.connectionState == BluetoothConnectionState.Connected) {
                        sendRealtimeFrame(frame)
                    } else {
                        updateRealtimePreviewTick()
                    }
                    realtimeSendInFlight = false
                }
                delay((1_000L / settings.targetFps.coerceIn(1, 25)).coerceAtLeast(40L))
            }
        }
    }

    private fun startCustomRealtimeFlow() {
        val state = uiState.value
        if (state.bluetooth.connectionState != BluetoothConnectionState.Connected) {
            manualState.update { it.copy(errorMessage = "请先连接蓝牙设备") }
            return
        }
        customRealtimeFlowJob?.cancel()
        customRealtimeFlowInFlight = false
        manualState.update {
            it.copy(
                realtimeFlowRunning = true,
                realtimeFlowSentFps = 0,
                realtimeFlowSentThisSecond = 0,
                realtimeFlowFpsWindowStartedAt = System.currentTimeMillis(),
                realtimeFlowStatus = "自定义流水运行中",
                errorMessage = null
            )
        }
        customRealtimeFlowJob = viewModelScope.launch {
            var frameIndex = 0
            while (true) {
                val currentState = uiState.value
                if (!manualState.value.realtimeFlowRunning || currentState.control.mode != ControlMode.CustomRealtimeFlow) {
                    return@launch
                }
                val frames = RgbControlState.sanitizeRealtimeFlowFrames(currentState.control.realtimeFlowFrames)
                if (frameIndex !in frames.indices) frameIndex = 0
                val flowFrame = frames[frameIndex]
                if (!customRealtimeFlowInFlight) {
                    customRealtimeFlowInFlight = true
                    val frame = RealtimeFrameBuilder.build(
                        leds = flowFrame.leds,
                        maxBrightness = currentState.control.brightness,
                        sequence = realtimeSequence++
                    )
                    sendCustomRealtimeFlowFrame(frame)
                    customRealtimeFlowInFlight = false
                }
                delay(flowFrame.durationTicks.coerceIn(1, 255) * 10L)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    private suspend fun sendCustomRealtimeFlowFrame(frame: RealtimeFrame) {
        val result = bluetoothClient.send(frame.bytes)
        result.onSuccess {
            manualState.update { current ->
                val now = System.currentTimeMillis()
                val elapsed = now - current.realtimeFlowFpsWindowStartedAt
                if (elapsed >= 1_000L) {
                    val sentFps = current.realtimeFlowSentThisSecond + 1
                    current.copy(
                        realtimeFlowSentFps = sentFps,
                        realtimeFlowSentThisSecond = 1,
                        realtimeFlowFpsWindowStartedAt = now,
                        realtimeFlowStatus = "自定义流水运行中：$sentFps FPS",
                        errorMessage = null
                    )
                } else {
                    current.copy(
                        realtimeFlowSentThisSecond = current.realtimeFlowSentThisSecond + 1,
                        errorMessage = null
                    )
                }
            }
        }.onFailure { throwable ->
            stopCustomRealtimeFlow()
            manualState.update { it.copy(errorMessage = throwable.message ?: "实时流水发送失败") }
        }
    }

    private fun updateRealtimePreviewTick() {
        val source = currentMusicSettings.audioSource
        musicState.update { current ->
            val now = System.currentTimeMillis()
            val elapsed = now - current.fpsWindowStartedAt
            if (elapsed >= 1_000L) {
                val fps = current.sentThisSecond + 1
                current.copy(
                    sentFps = fps,
                    sentThisSecond = 1,
                    fpsWindowStartedAt = now,
                    status = "${source.title}律动预览中，未连接蓝牙"
                )
            } else {
                current.copy(sentThisSecond = current.sentThisSecond + 1)
            }
        }
    }

    private suspend fun sendRealtimeFrame(frame: RealtimeFrame) {
        val result = bluetoothClient.send(frame.bytes)
        result.onSuccess {
            val source = currentMusicSettings.audioSource
            musicState.update { current ->
                val now = System.currentTimeMillis()
                val elapsed = now - current.fpsWindowStartedAt
                if (elapsed >= 1_000L) {
                    val sentFps = current.sentThisSecond + 1
                    current.copy(
                        sentFps = sentFps,
                        sentThisSecond = 1,
                        fpsWindowStartedAt = now,
                        status = "${source.title}律动运行中：$sentFps FPS"
                    )
                } else {
                    current.copy(sentThisSecond = current.sentThisSecond + 1)
                }
            }
        }.onFailure { throwable ->
            stopMusicReactive()
            manualState.update { it.copy(errorMessage = throwable.message ?: "实时发送失败") }
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

    private fun updateRealtimeFlowFrame(index: Int, transform: (RealtimeFlowFrame) -> RealtimeFlowFrame) {
        updateRealtimeFlowFrames { frames ->
            if (index !in frames.indices) {
                return@updateRealtimeFlowFrames
            }
            frames[index] = transform(frames[index]).clamped()
        }
    }

    private fun updateRealtimeFlowFrames(transform: (MutableList<RealtimeFlowFrame>) -> Unit) {
        updateControl { current ->
            val frames = RgbControlState.sanitizeRealtimeFlowFrames(current.realtimeFlowFrames).toMutableList()
            transform(frames)
            current.copy(realtimeFlowFrames = frames)
        }
    }

    private fun <T> MutableList<T>.swap(firstIndex: Int, secondIndex: Int) {
        val first = this[firstIndex]
        this[firstIndex] = this[secondIndex]
        this[secondIndex] = first
    }

    private fun effectiveControl(storageState: AppStorageState): RgbControlState {
        val control = storageState.control
        val effectiveFrames = if (storageState.useAdvancedFlowEditor) {
            control.flowFrames
        } else {
            RgbFrameBuilder.orderToFlowFrames(control.order)
        }
        return control.copy(flowFrames = effectiveFrames).clamped()
    }

    private data class ManualUiState(
        val manualHex: String = "",
        val importExportText: String = "",
        val serialConfig: SerialPortConfig = SerialPortConfig(),
        val realtimeFlowRunning: Boolean = false,
        val realtimeFlowSentFps: Int = 0,
        val realtimeFlowSentThisSecond: Int = 0,
        val realtimeFlowFpsWindowStartedAt: Long = System.currentTimeMillis(),
        val realtimeFlowStatus: String = "自定义流水未启动",
        val statusMessage: String? = null,
        val errorMessage: String? = null
    )

    private data class MusicUiState(
        val isRunning: Boolean = false,
        val sentFps: Int = 0,
        val sentThisSecond: Int = 0,
        val fpsWindowStartedAt: Long = System.currentTimeMillis(),
        val status: String = "音乐律动未启动"
    )

}
