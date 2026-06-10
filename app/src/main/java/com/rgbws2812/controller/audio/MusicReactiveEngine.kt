package com.rgbws2812.controller.audio

import com.rgbws2812.controller.protocol.RealtimeLed
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class StereoLevel(
    val left: Float = 0f,
    val right: Float = 0f
) {
    fun clamped(): StereoLevel =
        StereoLevel(left = left.coerceIn(0f, 1f), right = right.coerceIn(0f, 1f))
}

data class MusicReactiveSettings(
    val maxBrightness: Int = 64,
    val targetFps: Int = 20
) {
    fun clamped(): MusicReactiveSettings =
        copy(
            maxBrightness = maxBrightness.coerceIn(0, 255),
            targetFps = targetFps.coerceIn(1, 25)
        )
}

data class MusicReactiveRuntimeState(
    val isRunning: Boolean = false,
    val level: StereoLevel = StereoLevel(),
    val sentFps: Int = 0,
    val audioStatus: String = "",
    val status: String = "音乐律动未启动"
)

object MusicReactiveMapper {
    private val LeftHardwareOrder = listOf(3, 2, 1, 0)
    private val RightHardwareOrder = listOf(4, 5, 6, 7)
    private val SegmentColors = listOf(
        RealtimeLed(red = 0, green = 255, blue = 0, level = 0),
        RealtimeLed(red = 160, green = 255, blue = 0, level = 0),
        RealtimeLed(red = 255, green = 128, blue = 0, level = 0),
        RealtimeLed(red = 255, green = 0, blue = 0, level = 0)
    )

    fun ledsForLevel(level: StereoLevel): List<RealtimeLed> {
        val leds = MutableList(8) { RealtimeLed(red = 0, green = 0, blue = 0, level = 0) }
        applyChannel(leds, LeftHardwareOrder, level.left)
        applyChannel(leds, RightHardwareOrder, level.right)
        return leds
    }

    private fun applyChannel(target: MutableList<RealtimeLed>, hardwareOrder: List<Int>, rawValue: Float) {
        val scaled = rawValue.coerceIn(0f, 1f) * hardwareOrder.size
        hardwareOrder.forEachIndexed { segment, ledIndex ->
            val level = ((scaled - segment).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val color = SegmentColors[segment]
            target[ledIndex] = color.copy(level = level)
        }
    }
}

class LowFrequencyAnalyzer(
    private val sampleRate: Int,
    private val lowCutHz: Float = 60f,
    private val highCutHz: Float = 250f
) {
    private val leftBandPass = BiquadBandPass(sampleRate, lowCutHz, highCutHz)
    private val rightBandPass = BiquadBandPass(sampleRate, lowCutHz, highCutHz)
    private val monoBandPass = BiquadBandPass(sampleRate, lowCutHz, highCutHz)

    fun analyzeMonoAsStereo(samples: ShortArray, sampleCount: Int): StereoLevel {
        if (sampleCount <= 0) return StereoLevel()
        var energy = 0.0
        val limit = min(samples.size, sampleCount)
        for (index in 0 until limit) {
            val sample = samples[index] / 32768f
            val filtered = monoBandPass.process(sample)
            energy += filtered * filtered
        }
        if (limit == 0) return StereoLevel()

        val rms = sqrt(energy / limit).toFloat()
        val level = rmsToDisplayLevel(rms)
        return StereoLevel(left = level, right = level)
    }

    fun analyzeInterleavedStereo(samples: ShortArray, sampleCount: Int): StereoLevel {
        if (sampleCount <= 0) return StereoLevel()
        var leftEnergy = 0.0
        var rightEnergy = 0.0
        var frames = 0
        var index = 0
        val limit = min(samples.size, sampleCount)
        while (index + 1 < limit) {
            val left = samples[index] / 32768f
            val right = samples[index + 1] / 32768f
            val filteredLeft = leftBandPass.process(left)
            val filteredRight = rightBandPass.process(right)
            leftEnergy += filteredLeft * filteredLeft
            rightEnergy += filteredRight * filteredRight
            frames++
            index += 2
        }
        if (frames == 0) return StereoLevel()

        val leftRms = sqrt(leftEnergy / frames).toFloat()
        val rightRms = sqrt(rightEnergy / frames).toFloat()
        return StereoLevel(
            left = rmsToDisplayLevel(leftRms),
            right = rmsToDisplayLevel(rightRms)
        )
    }

    fun reset() {
        leftBandPass.reset()
        rightBandPass.reset()
        monoBandPass.reset()
    }

    private fun rmsToDisplayLevel(rms: Float): Float {
        if (rms <= 0.00001f) return 0f
        val db = 20f * (ln(rms) / ln(10f))
        return ((db + 54f) / 42f).coerceIn(0f, 1f)
    }
}

private class BiquadBandPass(
    sampleRate: Int,
    lowCutHz: Float,
    highCutHz: Float
) {
    private val b0: Float
    private val b1: Float
    private val b2: Float
    private val a1: Float
    private val a2: Float
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    init {
        val centerHz = sqrt(lowCutHz * highCutHz)
        val q = centerHz / max(1f, highCutHz - lowCutHz)
        val omega = (2.0 * Math.PI * centerHz / sampleRate).toFloat()
        val alpha = kotlin.math.sin(omega) / (2f * q)
        val a0 = 1f + alpha
        b0 = alpha / a0
        b1 = 0f
        b2 = -alpha / a0
        a1 = (-2f * cos(omega)) / a0
        a2 = (1f - alpha) / a0
    }

    fun process(input: Float): Float {
        val cleanInput = if (input.isFinite()) input else 0f
        val output = b0 * cleanInput + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = cleanInput
        y2 = y1
        y1 = if (output.isFinite()) output else 0f
        return y1
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }
}

fun pcmPeakLevel(samples: ShortArray, sampleCount: Int): StereoLevel {
    var left = 0f
    var right = 0f
    var index = 0
    val limit = min(samples.size, sampleCount)
    while (index + 1 < limit) {
        left = max(left, abs(samples[index] / 32768f))
        right = max(right, abs(samples[index + 1] / 32768f))
        index += 2
    }
    return StereoLevel(left, right)
}
