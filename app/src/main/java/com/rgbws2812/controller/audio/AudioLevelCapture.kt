package com.rgbws2812.controller.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
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
    private val microphoneChannelConfig = AudioFormat.CHANNEL_IN_MONO
    private val playbackChannelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val analyzer = LowFrequencyAnalyzer(sampleRate)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null

    private val _state = MutableStateFlow(AudioCaptureState())
    val state: StateFlow<AudioCaptureState> = _state

    suspend fun start(source: MusicReactiveAudioSource, mediaProjectionPermission: MediaProjectionPermission? = null): Boolean {
        stop()
        _state.update {
            it.copy(
                isCapturing = true,
                level = StereoLevel(),
                source = source,
                statusMessage = "正在启动${source.title}采集",
                errorMessage = null
            )
        }
        return when (source) {
            MusicReactiveAudioSource.Microphone -> startMicrophone()
            MusicReactiveAudioSource.SystemPlayback -> startSystemPlayback(mediaProjectionPermission)
        }
    }

    fun stop() {
        captureJob?.cancel()
        captureJob = null
        analyzer.reset()
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
        MediaProjectionCaptureService.stop(appContext)
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
    private fun startMicrophone(): Boolean {
        if (!hasMicrophonePermission()) {
            _state.update { it.copy(errorMessage = "需要录音权限后才能启动音乐律动") }
            return false
        }
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, microphoneChannelConfig, audioFormat)
        if (minBuffer <= 0) {
            _state.update { it.copy(errorMessage = "当前设备不支持所需音频采样配置") }
            return false
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            microphoneChannelConfig,
            audioFormat,
            minBuffer * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            _state.update { it.copy(errorMessage = "麦克风采集初始化失败") }
            return false
        }
        audioRecord = record
        captureJob = launchReader(record, minBuffer, MusicReactiveAudioSource.Microphone)
        return true
    }

    @SuppressLint("MissingPermission")
    private suspend fun startSystemPlayback(permission: MediaProjectionPermission?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _state.update { it.copy(errorMessage = "系统音频采集需要 Android 10 或更高版本") }
            return false
        }
        if (permission == null) {
            _state.update { it.copy(errorMessage = "需要授权系统音频采集后才能启动音乐律动") }
            return false
        }
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, playbackChannelConfig, audioFormat)
        if (minBuffer <= 0) {
            _state.update { it.copy(errorMessage = "当前设备不支持系统音频采样配置") }
            return false
        }

        val record = runCatching {
            if (!MediaProjectionCaptureService.startAndAwaitForeground(appContext)) {
                error("前台服务启动超时")
            }
            val projectionManager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(permission.resultCode, permission.data)
                ?: error("系统音频采集授权无效，请重新授权")
            mediaProjection = projection
            val captureConfig = android.media.AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(audioFormat)
                .setSampleRate(sampleRate)
                .setChannelMask(playbackChannelConfig)
                .build()
            AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBuffer * 2)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
        }.getOrElse { error ->
            runCatching { mediaProjection?.stop() }
            mediaProjection = null
            MediaProjectionCaptureService.stop(appContext)
            _state.update { it.copy(errorMessage = "系统音频采集启动失败：${error.message ?: error.javaClass.simpleName}") }
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            runCatching { mediaProjection?.stop() }
            mediaProjection = null
            MediaProjectionCaptureService.stop(appContext)
            _state.update { it.copy(errorMessage = "系统音频采集初始化失败") }
            return false
        }
        audioRecord = record
        captureJob = launchReader(record, minBuffer, MusicReactiveAudioSource.SystemPlayback)
        return true
    }

    private fun launchReader(record: AudioRecord, minBuffer: Int, source: MusicReactiveAudioSource): Job = scope.launch(Dispatchers.IO) {
        val buffer = ShortArray((minBuffer / 2).coerceAtLeast(512))
        runCatching {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                error("${source.title}采集未进入录制状态")
            }
            var totalRead = 0L
            var lastStatusAt = 0L
            while (currentCoroutineContext().isActive) {
                val count = record.read(buffer, 0, buffer.size)
                when {
                    count > 0 -> {
                        totalRead += count
                        val level = when (source) {
                            MusicReactiveAudioSource.Microphone -> analyzer.analyzeMonoAsStereo(buffer, count)
                            MusicReactiveAudioSource.SystemPlayback -> analyzer.analyzeInterleavedStereo(buffer, count)
                        }
                        val now = System.currentTimeMillis()
                        withContext(Dispatchers.Main.immediate) {
                            _state.update { current ->
                                current.copy(
                                    level = level,
                                    statusMessage = if (now - lastStatusAt >= 1_000L) {
                                        lastStatusAt = now
                                        "${source.title}已读取 $totalRead 个采样"
                                    } else {
                                        current.statusMessage
                                    }
                                )
                            }
                        }
                    }
                    count == 0 -> Unit
                    else -> error("${source.title}读取失败：${audioRecordReadErrorName(count)}")
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
    val source: MusicReactiveAudioSource = MusicReactiveAudioSource.Microphone,
    val level: StereoLevel = StereoLevel(),
    val statusMessage: String = "",
    val errorMessage: String? = null
)

data class MediaProjectionPermission(
    val resultCode: Int,
    val data: Intent
)
