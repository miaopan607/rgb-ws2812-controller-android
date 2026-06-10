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
    fun monoAnalyzerCopiesLevelToLeftAndRight() {
        val sampleRate = 8_000
        val analyzer = LowFrequencyAnalyzer(sampleRate)
        val mono = monoSine(sampleRate, frequency = 120.0, durationMillis = 160)

        val level = analyzer.analyzeMonoAsStereo(mono, mono.size)

        assertTrue(level.left > 0f)
        assertEquals(level.left, level.right, 0.0001f)
    }

    private fun stereoSine(sampleRate: Int, frequency: Double, durationMillis: Int): ShortArray {
        val frames = sampleRate * durationMillis / 1_000
        val samples = ShortArray(frames * 2)
        repeat(frames) { frame ->
            val value = (sin(2.0 * PI * frequency * frame / sampleRate) * Short.MAX_VALUE * 0.45).toInt().toShort()
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
