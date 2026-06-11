package com.rgbws2812.controller

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorHuePreservationTest {
    @Test
    fun replaceHuePreservingColorKeepsBlackBlack() {
        val rgb = replaceHuePreservingColor(
            hue = 180f,
            saturation = 0f,
            value = 0f
        )

        assertEquals(0, rgb.first)
        assertEquals(0, rgb.second)
        assertEquals(0, rgb.third)
    }

    @Test
    fun replaceHuePreservingColorKeepsBrightnessForDimColor() {
        val rgb = replaceHuePreservingColor(
            hue = 240f,
            saturation = 1f,
            value = 0.25f
        )

        assertEquals(0, rgb.first)
        assertEquals(0, rgb.second)
        assertEquals(64, rgb.third)
    }
}
