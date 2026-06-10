package com.rgbws2812.controller.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioLevelCapture(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val sampleRate = 8_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val analyzer = LowFrequencyAnalyzer(sampleRate)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _state = MutableStateFlow(AudioCaptureState())
    val state: StateFlow<AudioCaptureState> = _state

    fun start() {
        stop()
        _state.update {
            it.copy(
                isCapturing = true,
                level = StereoLevel(),
                statusMessage = "正在启动麦克风采集",
                errorMessage = null
            )
        }
        startMicrophone()
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        analyzer.reset()
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        _state.update {
            it.copy(
                isCapturing = false,
                level = StereoLevel(),
                statusMessage = ""
            )
        }
    }

    fun release() {
        stop()
    }

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startMicrophone() {
        if (!hasMicrophonePermission()) {
            _state.update { it.copy(errorMessage = "需要录音权限后才能启动音乐律动") }
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuffer <= 0) {
            _state.update { it.copy(errorMessage = "当前设备不支持所需音频采样配置") }
            return
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuffer * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            _state.update { it.copy(errorMessage = "麦克风采集初始化失败") }
            return
        }
        audioRecord = record
        captureJob = launchReader(record, minBuffer)
    }

    private fun launchReader(record: AudioRecord, minBuffer: Int): Job = scope.launch(Dispatchers.IO) {
        val buffer = ShortArray((minBuffer / 2).coerceAtLeast(512))
        runCatching {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                error("麦克风采集未进入录制状态")
            }
            var totalRead = 0L
            var lastStatusAt = 0L
            while (currentCoroutineContext().isActive) {
                val count = record.read(buffer, 0, buffer.size)
                when {
                    count > 0 -> {
                        totalRead += count
                        val level = analyzer.analyzeMonoAsStereo(buffer, count)
                        val now = System.currentTimeMillis()
                        withContext(Dispatchers.Main.immediate) {
                            _state.update { current ->
                                current.copy(
                                    level = level,
                                    statusMessage = if (now - lastStatusAt >= 1_000L) {
                                        lastStatusAt = now
                                        "麦克风已读取 $totalRead 个采样"
                                    } else {
                                        current.statusMessage
                                    }
                                )
                            }
                        }
                    }
                    count == 0 -> Unit
                    else -> error("麦克风读取失败：${audioRecordReadErrorName(count)}")
                }
            }
        }.onFailure { error ->
            withContext(Dispatchers.Main.immediate) {
                _state.update { it.copy(errorMessage = error.message ?: "音频采集失败") }
            }
        }
    }

    private fun audioRecordReadErrorName(code: Int): String =
        when (code) {
            AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
            AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
            AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
            AudioRecord.ERROR -> "ERROR"
            else -> code.toString()
        }
}

data class AudioCaptureState(
    val isCapturing: Boolean = false,
    val level: StereoLevel = StereoLevel(),
    val statusMessage: String = "",
    val errorMessage: String? = null
)
