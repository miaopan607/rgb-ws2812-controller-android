package com.rgbws2812.controller.audio

import com.rgbws2812.controller.protocol.RealtimeLed
import kotlin.math.abs
import kotlin.math.cos
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
    val targetFps: Int = 20,
    val sensitivity: Int = 115,
    val punch: Int = 125,
    val ambientLimit: Int = 10,
    val detectionMode: MusicReactiveDetectionMode = MusicReactiveDetectionMode.LowFrequency,
    val audioSource: MusicReactiveAudioSource = MusicReactiveAudioSource.Microphone
) {
    fun clamped(): MusicReactiveSettings =
        copy(
            maxBrightness = maxBrightness.coerceIn(0, 255),
            targetFps = targetFps.coerceIn(1, 25),
            sensitivity = sensitivity.coerceIn(50, 200),
            punch = punch.coerceIn(0, 200),
            ambientLimit = ambientLimit.coerceIn(0, 40)
        )
}

enum class MusicReactiveDetectionMode(val title: String) {
    LowFrequency("低频律动"),
    BeatEnhanced("节拍增强")
}

enum class MusicReactiveAudioSource(val title: String) {
    Microphone("麦克风"),
    SystemPlayback("系统音频")
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

internal interface MusicReactiveAnalyzer {
    fun updateSettings(settings: MusicReactiveSettings)

    fun analyzeMonoAsStereo(samples: ShortArray, sampleCount: Int): StereoLevel

    fun analyzeInterleavedStereo(samples: ShortArray, sampleCount: Int): StereoLevel

    fun reset()
}

class LowFrequencyAnalyzer(
    private val sampleRate: Int,
    private val lowCutHz: Float = 55f,
    private val highCutHz: Float = 260f
) : MusicReactiveAnalyzer {
    private val leftBandPass = BiquadBandPass(sampleRate, lowCutHz, highCutHz)
    private val rightBandPass = BiquadBandPass(sampleRate, lowCutHz, highCutHz)
    private val monoBandPass = BiquadBandPass(sampleRate, lowCutHz, highCutHz)
    private val leftDynamics = RhythmEnvelope()
    private val rightDynamics = RhythmEnvelope()
    private val monoDynamics = RhythmEnvelope()
    private val leftAmbient = AmbientEnvelope()
    private val rightAmbient = AmbientEnvelope()
    private val monoAmbient = AmbientEnvelope()
    private var sensitivity = 1.15f
    private var punch = 1.25f
    private var ambientLimit = 0.1f

    override fun updateSettings(settings: MusicReactiveSettings) {
        val cleanSettings = settings.clamped()
        sensitivity = cleanSettings.sensitivity / 100f
        punch = cleanSettings.punch / 100f
        ambientLimit = cleanSettings.ambientLimit / 100f
    }

    override fun analyzeMonoAsStereo(samples: ShortArray, sampleCount: Int): StereoLevel {
        if (sampleCount <= 0) return StereoLevel()
        var lowEnergy = 0.0
        var fullEnergy = 0.0
        val limit = min(samples.size, sampleCount)
        for (index in 0 until limit) {
            val sample = samples[index] / 32768f
            val filtered = monoBandPass.process(sample)
            lowEnergy += filtered * filtered
            fullEnergy += sample * sample
        }
        if (limit == 0) return StereoLevel()

        val lowRms = sqrt(lowEnergy / limit).toFloat()
        val fullRms = sqrt(fullEnergy / limit).toFloat()
        val rhythm = monoDynamics.process(lowRms, sensitivity, punch) * bassWeight(lowRms, fullRms)
        val ambient = monoAmbient.process(fullRms, ambientLimit)
        val level = max(rhythm, ambient)
        return StereoLevel(left = level, right = level)
    }

    override fun analyzeInterleavedStereo(samples: ShortArray, sampleCount: Int): StereoLevel {
        if (sampleCount <= 0) return StereoLevel()
        var leftLowEnergy = 0.0
        var rightLowEnergy = 0.0
        var leftFullEnergy = 0.0
        var rightFullEnergy = 0.0
        var frames = 0
        var index = 0
        val limit = min(samples.size, sampleCount)
        while (index + 1 < limit) {
            val left = samples[index] / 32768f
            val right = samples[index + 1] / 32768f
            val filteredLeft = leftBandPass.process(left)
            val filteredRight = rightBandPass.process(right)
            leftLowEnergy += filteredLeft * filteredLeft
            rightLowEnergy += filteredRight * filteredRight
            leftFullEnergy += left * left
            rightFullEnergy += right * right
            frames++
            index += 2
        }
        if (frames == 0) return StereoLevel()

        val leftRms = sqrt(leftLowEnergy / frames).toFloat()
        val rightRms = sqrt(rightLowEnergy / frames).toFloat()
        val leftFullRms = sqrt(leftFullEnergy / frames).toFloat()
        val rightFullRms = sqrt(rightFullEnergy / frames).toFloat()
        return StereoLevel(
            left = max(
                leftDynamics.process(leftRms, sensitivity, punch) * bassWeight(leftRms, leftFullRms),
                leftAmbient.process(leftFullRms, ambientLimit)
            ),
            right = max(
                rightDynamics.process(rightRms, sensitivity, punch) * bassWeight(rightRms, rightFullRms),
                rightAmbient.process(rightFullRms, ambientLimit)
            )
        )
    }

    override fun reset() {
        leftBandPass.reset()
        rightBandPass.reset()
        monoBandPass.reset()
        leftDynamics.reset()
        rightDynamics.reset()
        monoDynamics.reset()
        leftAmbient.reset()
        rightAmbient.reset()
        monoAmbient.reset()
    }

    private fun bassWeight(lowRms: Float, fullRms: Float): Float {
        if (fullRms <= 0.00001f) return 0f
        val ratio = (lowRms / fullRms).coerceIn(0f, 1f)
        return ((ratio - 0.18f) / 0.22f).coerceIn(0f, 1f)
    }
}

class BeatEnhancedAnalyzer(
    private val sampleRate: Int
) : MusicReactiveAnalyzer {
    private val monoLowBand = BiquadBandPass(sampleRate, 70f, 180f)
    private val monoMidBand = BiquadBandPass(sampleRate, 180f, 900f)
    private val monoHighBand = BiquadBandPass(sampleRate, 900f, 3200f)
    private val leftLowBand = BiquadBandPass(sampleRate, 70f, 180f)
    private val leftMidBand = BiquadBandPass(sampleRate, 180f, 900f)
    private val leftHighBand = BiquadBandPass(sampleRate, 900f, 3200f)
    private val rightLowBand = BiquadBandPass(sampleRate, 70f, 180f)
    private val rightMidBand = BiquadBandPass(sampleRate, 180f, 900f)
    private val rightHighBand = BiquadBandPass(sampleRate, 900f, 3200f)
    private val monoLowDynamics = RhythmEnvelope()
    private val monoMidDynamics = RhythmEnvelope()
    private val monoHighDynamics = RhythmEnvelope()
    private val leftLowDynamics = RhythmEnvelope()
    private val leftMidDynamics = RhythmEnvelope()
    private val leftHighDynamics = RhythmEnvelope()
    private val rightLowDynamics = RhythmEnvelope()
    private val rightMidDynamics = RhythmEnvelope()
    private val rightHighDynamics = RhythmEnvelope()
    private val monoAmbient = AmbientEnvelope()
    private val leftAmbient = AmbientEnvelope()
    private val rightAmbient = AmbientEnvelope()
    private var sensitivity = 1.15f
    private var punch = 1.25f
    private var ambientLimit = 0.1f

    override fun updateSettings(settings: MusicReactiveSettings) {
        val cleanSettings = settings.clamped()
        sensitivity = cleanSettings.sensitivity / 100f
        punch = cleanSettings.punch / 100f
        ambientLimit = cleanSettings.ambientLimit / 100f
    }

    override fun analyzeMonoAsStereo(samples: ShortArray, sampleCount: Int): StereoLevel {
        if (sampleCount <= 0) return StereoLevel()
        var lowEnergy = 0.0
        var midEnergy = 0.0
        var highEnergy = 0.0
        var fullEnergy = 0.0
        val limit = min(samples.size, sampleCount)
        for (index in 0 until limit) {
            val sample = samples[index] / 32768f
            lowEnergy += monoLowBand.process(sample).let { it * it }
            midEnergy += monoMidBand.process(sample).let { it * it }
            highEnergy += monoHighBand.process(sample).let { it * it }
            fullEnergy += sample * sample
        }
        if (limit == 0) return StereoLevel()

        val lowRms = sqrt(lowEnergy / limit).toFloat()
        val midRms = sqrt(midEnergy / limit).toFloat()
        val highRms = sqrt(highEnergy / limit).toFloat()
        val fullRms = sqrt(fullEnergy / limit).toFloat()
        val level = combineBandLevels(
            low = monoLowDynamics.process(lowRms * LowBandGain, sensitivity, punch) * LowBandWeight,
            mid = monoMidDynamics.process(midRms * MidBandGain, sensitivity, punch),
            high = monoHighDynamics.process(highRms * HighBandGain, sensitivity, punch) * HighBandWeight,
            ambient = monoAmbient.process(fullRms, ambientLimit)
        )
        return StereoLevel(left = level, right = level)
    }

    override fun analyzeInterleavedStereo(samples: ShortArray, sampleCount: Int): StereoLevel {
        if (sampleCount <= 0) return StereoLevel()
        var leftLowEnergy = 0.0
        var leftMidEnergy = 0.0
        var leftHighEnergy = 0.0
        var leftFullEnergy = 0.0
        var rightLowEnergy = 0.0
        var rightMidEnergy = 0.0
        var rightHighEnergy = 0.0
        var rightFullEnergy = 0.0
        var frames = 0
        var index = 0
        val limit = min(samples.size, sampleCount)
        while (index + 1 < limit) {
            val left = samples[index] / 32768f
            val right = samples[index + 1] / 32768f
            leftLowEnergy += leftLowBand.process(left).let { it * it }
            leftMidEnergy += leftMidBand.process(left).let { it * it }
            leftHighEnergy += leftHighBand.process(left).let { it * it }
            rightLowEnergy += rightLowBand.process(right).let { it * it }
            rightMidEnergy += rightMidBand.process(right).let { it * it }
            rightHighEnergy += rightHighBand.process(right).let { it * it }
            leftFullEnergy += left * left
            rightFullEnergy += right * right
            frames++
            index += 2
        }
        if (frames == 0) return StereoLevel()

        val leftLowRms = sqrt(leftLowEnergy / frames).toFloat()
        val leftMidRms = sqrt(leftMidEnergy / frames).toFloat()
        val leftHighRms = sqrt(leftHighEnergy / frames).toFloat()
        val leftFullRms = sqrt(leftFullEnergy / frames).toFloat()
        val rightLowRms = sqrt(rightLowEnergy / frames).toFloat()
        val rightMidRms = sqrt(rightMidEnergy / frames).toFloat()
        val rightHighRms = sqrt(rightHighEnergy / frames).toFloat()
        val rightFullRms = sqrt(rightFullEnergy / frames).toFloat()
        return StereoLevel(
            left = combineBandLevels(
                low = leftLowDynamics.process(leftLowRms * LowBandGain, sensitivity, punch) * LowBandWeight,
                mid = leftMidDynamics.process(leftMidRms * MidBandGain, sensitivity, punch),
                high = leftHighDynamics.process(leftHighRms * HighBandGain, sensitivity, punch) * HighBandWeight,
                ambient = leftAmbient.process(leftFullRms, ambientLimit)
            ),
            right = combineBandLevels(
                low = rightLowDynamics.process(rightLowRms * LowBandGain, sensitivity, punch) * LowBandWeight,
                mid = rightMidDynamics.process(rightMidRms * MidBandGain, sensitivity, punch),
                high = rightHighDynamics.process(rightHighRms * HighBandGain, sensitivity, punch) * HighBandWeight,
                ambient = rightAmbient.process(rightFullRms, ambientLimit)
            )
        )
    }

    override fun reset() {
        monoLowBand.reset()
        monoMidBand.reset()
        monoHighBand.reset()
        leftLowBand.reset()
        leftMidBand.reset()
        leftHighBand.reset()
        rightLowBand.reset()
        rightMidBand.reset()
        rightHighBand.reset()
        monoLowDynamics.reset()
        monoMidDynamics.reset()
        monoHighDynamics.reset()
        leftLowDynamics.reset()
        leftMidDynamics.reset()
        leftHighDynamics.reset()
        rightLowDynamics.reset()
        rightMidDynamics.reset()
        rightHighDynamics.reset()
        monoAmbient.reset()
        leftAmbient.reset()
        rightAmbient.reset()
    }

    private fun combineBandLevels(low: Float, mid: Float, high: Float, ambient: Float): Float {
        val beat = max(low, max(mid, high))
        return max(beat, ambient).coerceIn(0f, 1f)
    }

    private companion object {
        const val LowBandGain = 1.1f
        const val MidBandGain = 1.35f
        const val HighBandGain = 1.65f
        const val LowBandWeight = 0.95f
        const val HighBandWeight = 1.12f
    }
}

private class RhythmEnvelope {
    private var floor = 0.0006f
    private var peak = 0.018f
    private var bodyEnvelope = 0f
    private var fallEnvelope = 0f

    fun process(rms: Float, sensitivity: Float, punch: Float): Float {
        val cleanRms = if (rms.isFinite()) rms.coerceAtLeast(0f) else 0f
        updateRange(cleanRms)

        val range = max(MinDynamicRange, (peak - floor) * PeakHeadroom)
        val normalized = (((cleanRms - floor) / range) * sensitivity).coerceIn(0f, 1f)
        val attack = if (normalized > bodyEnvelope) BodyAttack else BodyRelease
        val previousEnvelope = bodyEnvelope
        bodyEnvelope += (normalized - bodyEnvelope) * attack

        val body = normalized.powCompat(BodyGamma) * BodyWeight
        val onset = (normalized - previousEnvelope - OnsetThreshold).coerceAtLeast(0f)
        val pulse = (onset * PulseGain * punch).coerceIn(0f, 1f)
        val target = (body + pulse * PulseWeight).coerceIn(0f, 1f)

        fallEnvelope = max(fallEnvelope * FallRelease, target)
        if (cleanRms < SilenceRms && bodyEnvelope < 0.02f) {
            fallEnvelope *= SilenceRelease
        }
        return if (fallEnvelope < NoiseGate) 0f else fallEnvelope.coerceIn(0f, 1f)
    }

    fun reset() {
        floor = 0.0006f
        peak = 0.018f
        bodyEnvelope = 0f
        fallEnvelope = 0f
    }

    private fun updateRange(rms: Float) {
        floor += (rms - floor) * if (rms < floor) FloorDownRate else FloorUpRate
        floor = floor.coerceIn(0.0001f, 0.08f)

        if (rms > peak) {
            peak += (rms - peak) * PeakAttack
        } else {
            peak += (rms - peak) * PeakRelease
        }
        peak = peak.coerceAtLeast(floor + MinDynamicRange)
    }

    private fun Float.powCompat(power: Float): Float =
        Math.pow(toDouble(), power.toDouble()).toFloat()

    private companion object {
        const val MinDynamicRange = 0.018f
        const val FloorUpRate = 0.004f
        const val FloorDownRate = 0.08f
        const val PeakAttack = 0.72f
        const val PeakRelease = 0.006f
        const val PeakHeadroom = 2.25f
        const val BodyAttack = 0.42f
        const val BodyRelease = 0.16f
        const val BodyGamma = 1.35f
        const val BodyWeight = 0.72f
        const val OnsetThreshold = 0.07f
        const val PulseGain = 2.4f
        const val PulseWeight = 0.55f
        const val FallRelease = 0.82f
        const val SilenceRelease = 0.35f
        const val SilenceRms = 0.0009f
        const val NoiseGate = 0.035f
    }
}

private class AmbientEnvelope {
    private var floor = 0.0015f
    private var peak = 0.08f
    private var envelope = 0f

    fun process(fullRms: Float, limit: Float): Float {
        if (limit <= 0f) return 0f
        val cleanRms = if (fullRms.isFinite()) fullRms.coerceAtLeast(0f) else 0f
        updateRange(cleanRms)

        val range = max(MinDynamicRange, (peak - floor) * PeakHeadroom)
        val normalized = ((cleanRms - floor) / range).coerceIn(0f, 1f)
        val target = (normalized * limit).coerceIn(0f, limit.coerceIn(0f, MaxAmbientLimit))
        val rate = if (target > envelope) Attack else Release
        envelope += (target - envelope) * rate
        return if (envelope < NoiseGate) 0f else envelope
    }

    fun reset() {
        floor = 0.0015f
        peak = 0.08f
        envelope = 0f
    }

    private fun updateRange(rms: Float) {
        floor += (rms - floor) * if (rms < floor) FloorDownRate else FloorUpRate
        floor = floor.coerceIn(0.0004f, 0.12f)

        if (rms > peak) {
            peak += (rms - peak) * PeakAttack
        } else {
            peak += (rms - peak) * PeakRelease
        }
        peak = peak.coerceAtLeast(floor + MinDynamicRange)
    }

    private companion object {
        const val MinDynamicRange = 0.04f
        const val PeakHeadroom = 2.0f
        const val FloorUpRate = 0.002f
        const val FloorDownRate = 0.06f
        const val PeakAttack = 0.35f
        const val PeakRelease = 0.004f
        const val Attack = 0.28f
        const val Release = 0.08f
        const val NoiseGate = 0.006f
        const val MaxAmbientLimit = 0.4f
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
