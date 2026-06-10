package com.rgbws2812.controller.data

import com.rgbws2812.controller.model.ControlMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStorageTest {
    @Test
    fun restoreControlStatePrefersVersion2TimingFields() {
        val decoded = AppStorage.restoreControlState(
            modeValue = ControlMode.Breath.wireValue,
            red = 0,
            green = 0,
            blue = 255,
            brightness = 128,
            flowInterval = 25,
            breathPeriod = 100,
            gradientPeriod = 20,
            legacyPeriod = 20,
            order = listOf(3, 2, 1, 0, 4, 5, 6, 7),
            flowFrames = listOf(8, 4, 2, 1, 16, 32, 64, 128)
        )

        assertEquals(ControlMode.Breath, decoded.mode)
        assertEquals(25, decoded.flowInterval)
        assertEquals(100, decoded.breathPeriod)
        assertEquals(20, decoded.gradientPeriod)
    }

    @Test
    fun restoreControlStateCopiesLegacyPeriodWithoutUnitMigration() {
        val decoded = AppStorage.restoreControlState(
            modeValue = ControlMode.Breath.wireValue,
            red = 0,
            green = 0,
            blue = 255,
            brightness = 128,
            flowInterval = Int.MIN_VALUE,
            breathPeriod = Int.MIN_VALUE,
            gradientPeriod = Int.MIN_VALUE,
            legacyPeriod = 20,
            order = listOf(3, 2, 1, 0, 4, 5, 6, 7),
            flowFrames = listOf(8, 4, 2, 1, 16, 32, 64, 128)
        )

        assertEquals(ControlMode.Breath, decoded.mode)
        assertEquals(20, decoded.flowInterval)
        assertEquals(20, decoded.breathPeriod)
        assertEquals(20, decoded.gradientPeriod)
    }
}
