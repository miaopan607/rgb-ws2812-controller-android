package com.rgbws2812.controller.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MusicReactiveEngineTest {
    @Test
    fun mapsStereoLevelsToTwoRowsOfFourLeds() {
        val leds = MusicReactiveMapper.ledsForLevel(StereoLevel(left = 0.625f, right = 1f))

        assertEquals(255, leds[3].level)
        assertEquals(255, leds[2].level)
        assertEquals(127, leds[1].level)
        assertEquals(0, leds[0].level)
        assertEquals(255, leds[4].level)
        assertEquals(255, leds[5].level)
        assertEquals(255, leds[6].level)
        assertEquals(255, leds[7].level)
    }

    @Test
    fun lowFrequencyAnalyzerRespondsMoreToBassThanHighTone() {
        val sampleRate = 8_000
        val analyzer = LowFrequencyAnalyzer(sampleRate)
        val bass = stereoSine(sampleRate, frequency = 120.0, durationMillis = 160)
        val high = stereoSine(sampleRate, frequency = 1_200.0, durationMillis = 160)

        val bassLevel = analyzer.analyzeInterleavedStereo(bass, bass.size)
        analyzer.reset()
        val highLevel = analyzer.analyzeInterleavedStereo(high, high.size)

        assertTrue(bassLevel.left > highLevel.left)
        assertTrue(bassLevel.right > highLevel.right)
    }

    @Test
    fun beatEnhancedAnalyzerRespondsMoreStronglyToHighFrequencyPulse() {
        val sampleRate = 8_000
        val lowAnalyzer = LowFrequencyAnalyzer(sampleRate)
        val beatAnalyzer = BeatEnhancedAnalyzer(sampleRate)
        val settings = MusicReactiveSettings(ambientLimit = 0, sensitivity = 120, punch = 130)
        lowAnalyzer.updateSettings(settings)
        beatAnalyzer.updateSettings(settings)
        val warmup = stereoSine(sampleRate, frequency = 1_200.0, durationMillis = 80, amplitude = 0.12)
        val pulse = stereoSine(sampleRate, frequency = 1_200.0, durationMillis = 80, amplitude = 0.62)

        repeat(6) {
            lowAnalyzer.analyzeInterleavedStereo(warmup, warmup.size)
            beatAnalyzer.analyzeInterleavedStereo(warmup, warmup.size)
        }

        val lowLevel = lowAnalyzer.analyzeInterleavedStereo(pulse, pulse.size)
        val beatLevel = beatAnalyzer.analyzeInterleavedStereo(pulse, pulse.size)

        assertTrue("beat-enhanced mode should lift high-frequency pulses more than bass-only mode", beatLevel.left > lowLevel.left + 0.08f)
        assertTrue("beat-enhanced mode should lift high-frequency pulses more than bass-only mode", beatLevel.right > lowLevel.right + 0.08f)
        assertTrue("beat-enhanced mode should still produce a visible response", beatLevel.left > 0.1f)
        assertTrue("beat-enhanced mode should still produce a visible response", beatLevel.right > 0.1f)
    }

    @Test
    fun monoAnalyzerCopiesLevelToLeftAndRight() {
        val sampleRate = 8_000
        val analyzer = LowFrequencyAnalyzer(sampleRate)
        val mono = monoSine(sampleRate, frequency = 120.0, durationMillis = 160)

        val level = analyzer.analyzeMonoAsStereo(mono, mono.size)

        assertTrue(level.left > 0f)
        assertEquals(level.left, level.right, 0.0001f)
    }

    @Test
    fun sustainedLoudBassDoesNotStayPinnedToMaximum() {
        val sampleRate = 8_000
        val analyzer = LowFrequencyAnalyzer(sampleRate)
        val loudBass = stereoSine(sampleRate, frequency = 120.0, durationMillis = 80, amplitude = 0.85)

        var level = StereoLevel()
        repeat(18) {
            level = analyzer.analyzeInterleavedStereo(loudBass, loudBass.size)
        }

        assertTrue("sustained loud bass should leave headroom", level.left < 0.92f)
        assertTrue("sustained loud bass should leave headroom", level.right < 0.92f)
        assertTrue("sustained loud bass should still be visible", level.left > 0.2f)
        assertTrue("sustained loud bass should still be visible", level.right > 0.2f)
    }

    @Test
    fun lowFrequencyPulseCreatesStrongerResponseThanSteadyTone() {
        val sampleRate = 8_000
        val analyzer = LowFrequencyAnalyzer(sampleRate)
        val quietBass = stereoSine(sampleRate, frequency = 120.0, durationMillis = 80, amplitude = 0.16)
        val pulseBass = stereoSine(sampleRate, frequency = 120.0, durationMillis = 80, amplitude = 0.82)

        var steady = StereoLevel()
        repeat(10) {
            steady = analyzer.analyzeInterleavedStereo(quietBass, quietBass.size)
        }
        val pulse = analyzer.analyzeInterleavedStereo(pulseBass, pulseBass.size)

        assertTrue(pulse.left > steady.left + 0.18f)
        assertTrue(pulse.right > steady.right + 0.18f)
    }

    @Test
    fun silenceAfterBassDecaysTowardZero() {
        val sampleRate = 8_000
        val analyzer = LowFrequencyAnalyzer(sampleRate)
        val bass = stereoSine(sampleRate, frequency = 120.0, durationMillis = 80, amplitude = 0.75)
        val silence = ShortArray(sampleRate * 80 / 1_000 * 2)

        analyzer.analyzeInterleavedStereo(bass, bass.size)
        var level = StereoLevel()
        repeat(16) {
            level = analyzer.analyzeInterleavedStereo(silence, silence.size)
        }

        assertTrue(level.left < 0.08f)
        assertTrue(level.right < 0.08f)
    }

    @Test
    fun highToneKeepsLimitedAmbientLevel() {
        val sampleRate = 8_000
        val analyzer = LowFrequencyAnalyzer(sampleRate)
        analyzer.updateSettings(MusicReactiveSettings(ambientLimit = 12))
        val high = stereoSine(sampleRate, frequency = 1_200.0, durationMillis = 80, amplitude = 0.55)

        var level = StereoLevel()
        repeat(8) {
            level = analyzer.analyzeInterleavedStereo(high, high.size)
        }

        assertTrue("high tone should keep a little ambient response", level.left > 0.015f)
        assertTrue("high tone should keep a little ambient response", level.right > 0.015f)
        assertTrue("ambient response must stay under configured cap", level.left <= 0.125f)
        assertTrue("ambient response must stay under configured cap", level.right <= 0.125f)
    }

    @Test
    fun ambientLimitCanDisableNonBassResponse() {
        val sampleRate = 8_000
        val analyzer = LowFrequencyAnalyzer(sampleRate)
        analyzer.updateSettings(MusicReactiveSettings(ambientLimit = 0))
        val high = stereoSine(sampleRate, frequency = 1_200.0, durationMillis = 80, amplitude = 0.55)

        var level = StereoLevel()
        repeat(8) {
            level = analyzer.analyzeInterleavedStereo(high, high.size)
        }

        assertTrue(level.left < 0.015f)
        assertTrue(level.right < 0.015f)
    }

    private fun stereoSine(sampleRate: Int, frequency: Double, durationMillis: Int): ShortArray {
        return stereoSine(sampleRate, frequency, durationMillis, amplitude = 0.45)
    }

    private fun stereoSine(sampleRate: Int, frequency: Double, durationMillis: Int, amplitude: Double): ShortArray {
        val frames = sampleRate * durationMillis / 1_000
        val samples = ShortArray(frames * 2)
        repeat(frames) { frame ->
            val value = (sin(2.0 * PI * frequency * frame / sampleRate) * Short.MAX_VALUE * amplitude).toInt().toShort()
            samples[frame * 2] = value
            samples[frame * 2 + 1] = value
        }
        return samples
    }

    private fun monoSine(sampleRate: Int, frequency: Double, durationMillis: Int): ShortArray {
        val frames = sampleRate * durationMillis / 1_000
        return ShortArray(frames) { frame ->
            (sin(2.0 * PI * frequency * frame / sampleRate) * Short.MAX_VALUE * 0.45).toInt().toShort()
        }
    }
}
