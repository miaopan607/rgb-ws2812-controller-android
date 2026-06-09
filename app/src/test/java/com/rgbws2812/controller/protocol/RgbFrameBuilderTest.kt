package com.rgbws2812.controller.protocol

import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.RgbControlState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RgbFrameBuilderTest {
    @Test
    fun staticRedExampleMatchesProtocolDocument() {
        val frame = RgbFrameBuilder.build(
            RgbControlState(
                mode = ControlMode.Static,
                red = 255,
                green = 0,
                blue = 0,
                brightness = 128,
                period = 20,
                order = listOf(0, 1, 2, 3, 4, 5, 6, 7)
            )
        )

        assertEquals("AA 55 00 FF 00 00 80 14 00 01 02 03 04 05 06 07 6B", frame.spacedHex())
    }

    @Test
    fun flowGreenExampleMatchesProtocolDocument() {
        val frame = RgbFrameBuilder.build(
            RgbControlState(
                mode = ControlMode.Flow,
                red = 0,
                green = 255,
                blue = 0,
                brightness = 96,
                period = 20,
                order = listOf(0, 1, 2, 3, 4, 5, 6, 7)
            )
        )

        assertEquals("AA 55 01 00 FF 00 60 14 00 01 02 03 04 05 06 07 8A", frame.spacedHex())
    }

    @Test
    fun parsesCompactAndSpacedHex() {
        val spaced = RgbFrameBuilder.parseHex("AA 55 03 00 00 00 40 14 00 01 02 03 04 05 06 07 57")
        val compact = RgbFrameBuilder.parseHex("AA55030000004014000102030405060757")

        assertEquals(spaced.spacedHex(), compact.spacedHex())
        assertEquals(ControlMode.Gradient, spaced.mode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateOrder() {
        RgbFrameBuilder.build(
            RgbControlState.Default.copy(order = listOf(0, 0, 1, 2, 3, 4, 5, 6))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadChecksum() {
        RgbFrameBuilder.parseHex("AA 55 03 00 00 00 40 14 00 01 02 03 04 05 06 07 00")
    }

    @Test
    fun validatesFullOrderPermutation() {
        assertTrue(RgbFrameBuilder.isValidOrder(listOf(3, 2, 1, 0, 4, 5, 6, 7)))
    }
}
