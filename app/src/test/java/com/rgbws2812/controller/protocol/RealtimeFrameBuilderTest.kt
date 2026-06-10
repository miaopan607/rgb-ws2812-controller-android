package com.rgbws2812.controller.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeFrameBuilderTest {
    @Test
    fun buildsFixedLengthDirectFrame() {
        val frame = RealtimeFrameBuilder.build(
            leds = List(RealtimeFrameBuilder.LedCount) { index ->
                RealtimeLed(red = index, green = index + 1, blue = index + 2, level = 255 - index)
            },
            maxBrightness = 96,
            sequence = 0x123
        )

        assertEquals(RealtimeFrameBuilder.FrameLength, frame.bytes.size)
        assertEquals(0x23, frame.sequence)
        assertEquals(96, frame.maxBrightness)
        assertEquals(0xAA.toByte(), frame.bytes[0])
        assertEquals(0x5A.toByte(), frame.bytes[1])
        assertEquals(RealtimeFrameBuilder.TypeDirectV1.toByte(), frame.bytes[2])
        assertEquals(0x23.toByte(), frame.bytes[3])
        assertEquals(96.toByte(), frame.bytes[4])
        assertEquals(0.toByte(), frame.bytes[5])
        assertEquals(1.toByte(), frame.bytes[6])
        assertEquals(2.toByte(), frame.bytes[7])
        assertEquals(255.toByte(), frame.bytes[8])
    }

    @Test
    fun parsesSpacedHexAndVerifiesChecksum() {
        val original = RealtimeFrameBuilder.build(
            leds = List(RealtimeFrameBuilder.LedCount) { RealtimeLed(0, 255, 0, 128) },
            maxBrightness = 40,
            sequence = 7
        )

        val parsed = RealtimeFrameBuilder.parseHex(original.spacedHex())

        assertEquals(original.spacedHex(), parsed.spacedHex())
        assertEquals(7, parsed.sequence)
        assertEquals(40, parsed.maxBrightness)
        assertEquals(RealtimeLed(0, 255, 0, 128), parsed.leds.first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadChecksum() {
        val frame = RealtimeFrameBuilder.build(
            leds = List(RealtimeFrameBuilder.LedCount) { RealtimeLed(0, 0, 0, 0) },
            maxBrightness = 1,
            sequence = 0
        )
        val bytes = frame.bytes.copyOf()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()

        RealtimeFrameBuilder.parseHex(bytes.joinToString(" ") { (it.toInt() and 0xFF).toHexByte() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongLedCount() {
        RealtimeFrameBuilder.build(
            leds = List(7) { RealtimeLed(0, 0, 0, 0) },
            maxBrightness = 1,
            sequence = 0
        )
    }
}
