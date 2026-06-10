package com.rgbws2812.controller.protocol

import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.GradientPattern
import com.rgbws2812.controller.model.RgbControlState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RgbFrameBuilderTest {
    @Test
    fun staticRedUsesMinimalPlaceholderFrame() {
        val frame = RgbFrameBuilder.build(
            RgbControlState(
                mode = ControlMode.Static,
                red = 255,
                green = 0,
                blue = 0,
                brightness = 128,
                flowFrames = listOf(0x08, 0x04, 0x02, 0x01, 0x10, 0x20, 0x40, 0x80)
            )
        )

        assertEquals(0x14, frame.period)
        assertEquals(listOf(0x00), frame.flowFrames)
        assertEquals("AA 55 00 FF 00 00 80 14 01 00 6A", frame.spacedHex())
    }

    @Test
    fun defaultFlowUses250msInterval() {
        val frame = RgbFrameBuilder.build(RgbControlState.Default)

        assertEquals(0x19, frame.period)
        assertEquals("AA 55 01 00 FF 00 11 19 08 08 04 02 01 10 20 40 80 01", frame.spacedHex())
    }

    @Test
    fun basicFlowOrderConvertsToSingleBitFrames() {
        val frames = RgbFrameBuilder.orderToFlowFrames(listOf(0, 1, 2, 3, 4, 5, 6, 7))
        val frame = RgbFrameBuilder.build(
            RgbControlState(
                mode = ControlMode.Flow,
                red = 0,
                green = 255,
                blue = 0,
                brightness = 96,
                flowInterval = 25,
                order = listOf(0, 1, 2, 3, 4, 5, 6, 7),
                flowFrames = frames
            )
        )

        assertEquals(listOf(0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, 0x80), frame.flowFrames)
        assertEquals("AA 55 01 00 FF 00 60 19 08 01 02 04 08 10 20 40 80 70", frame.spacedHex())
    }

    @Test
    fun advancedFlowCanLightMultipleLedsPerFrame() {
        val frame = RgbFrameBuilder.build(
            RgbControlState.Default.copy(
                mode = ControlMode.Flow,
                brightness = 96,
                flowFrames = listOf(0x03, 0x0C, 0x30, 0xC0)
            )
        )

        assertEquals(0x19, frame.period)
        assertEquals("AA 55 01 00 FF 00 60 19 04 03 0C 30 C0 7C", frame.spacedHex())
        assertEquals(4, frame.flowCount)
    }

    @Test
    fun parsesCompactAndSpacedVariableLengthHex() {
        val spaced = RgbFrameBuilder.parseHex("AA 55 03 00 00 00 40 14 01 00 56")
        val compact = RgbFrameBuilder.parseHex("AA55030000004014010056")

        assertEquals(spaced.spacedHex(), compact.spacedHex())
        assertEquals(ControlMode.Disco, spaced.mode)
        assertEquals(0x14, spaced.period)
        assertEquals(listOf(0x00), spaced.flowFrames)
    }

    @Test
    fun gradientModeUsesGradientPeriodByte() {
        val frame = RgbFrameBuilder.build(
            RgbControlState.Default.copy(
                mode = ControlMode.Gradient,
                brightness = 64,
                gradientPeriod = 20
            )
        )

        assertEquals(0x14, frame.period)
        assertEquals("AA 55 04 00 FF 00 40 14 01 00 AE", frame.spacedHex())
    }

    @Test
    fun flowGradientModeUsesNewWireValue() {
        val frame = RgbFrameBuilder.build(
            RgbControlState.Default.copy(
                mode = ControlMode.FlowGradient,
                brightness = 64,
                gradientPeriod = 20
            )
        )

        assertEquals("AA 55 05 00 FF 00 40 14 01 00 AF", frame.spacedHex())
    }

    @Test
    fun gradientPatternMatchesConfiguredColorLoop() {
        assertEquals(255, GradientPattern.gradientColor(0).red)
        assertEquals(0, GradientPattern.gradientColor(0).green)
        assertEquals(255, GradientPattern.gradientColor(255).green)
        assertEquals(255, GradientPattern.gradientColor(256).green)
        assertEquals(0, GradientPattern.gradientColor(512).red)
        assertEquals(255, GradientPattern.gradientColor(768).blue)
        assertEquals(255, GradientPattern.gradientColor(1280).blue)
        assertEquals(0, GradientPattern.gradientColor(1535).blue)
    }

    @Test
    fun breathUses20msPeriodUnits() {
        val frame = RgbFrameBuilder.build(
            RgbControlState.Default.copy(
                mode = ControlMode.Breath,
                red = 0,
                green = 0,
                blue = 255,
                brightness = 128,
                breathPeriod = 100
            )
        )

        assertEquals(0x64, frame.period)
        assertEquals(listOf(0x00), frame.flowFrames)
        assertEquals("AA 55 02 00 00 FF 80 64 01 00 18", frame.spacedHex())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadChecksum() {
        RgbFrameBuilder.parseHex("AA 55 03 00 00 00 40 14 01 00 00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroFlowCount() {
        RgbFrameBuilder.parseHex("AA 55 01 00 FF 00 60 19 00 00 00")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsFlowCountAboveLimit() {
        RgbFrameBuilder.parseHex("AA 55 01 00 FF 00 60 19 09 01 02 04 08 10 20 40 80 71")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLengthThatDoesNotMatchFlowCount() {
        RgbFrameBuilder.parseHex("AA 55 01 00 FF 00 60 19 08 01 02 04 08 10 20 40 F0")
    }

    @Test
    fun validatesFlowFramesInsteadOfRequiringFullBasicOrder() {
        assertTrue(RgbFrameBuilder.isValidFlowFrames(listOf(0x00)))
        assertTrue(RgbFrameBuilder.isValidFlowFrames(listOf(0x03, 0x0C)))
        assertFalse(RgbFrameBuilder.isValidFlowFrames(emptyList()))
        assertFalse(RgbFrameBuilder.isValidFlowFrames(List(9) { 0 }))
    }
}
