package com.rgbws2812.controller.data

import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.AppStorageState
import com.rgbws2812.controller.model.RgbControlState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppStorageTest {
    @Test
    fun appStorageStateDefaultsToHiddenAdvancedSendPanel() {
        assertFalse(com.rgbws2812.controller.model.AppStorageState().showAdvancedSendPanel)
    }

    @Test
    fun appStorageStateDefaultsToBasicFlowEditor() {
        assertFalse(com.rgbws2812.controller.model.AppStorageState().useAdvancedFlowEditor)
    }

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

    @Test
    fun decodeControlUsesSingleEmptyFrameWhenFlowFramesAreMissing() {
        val decoded = AppStorage.decodeControl(
            """
            {
              "mode": 1,
              "red": 0,
              "green": 255,
              "blue": 0,
              "brightness": 17,
              "flowInterval": 25,
              "order": [3, 2, 1, 0, 4, 5, 6, 7]
            }
            """.trimIndent()
        )

        assertEquals(RgbControlState.DefaultOrder, decoded.order)
        assertEquals(RgbControlState.EmptyFlowFrames, decoded.flowFrames)
    }

    @Test
    fun decodeControlKeepsExplicitAdvancedFlowFrames() {
        val decoded = AppStorage.restoreControlState(
            modeValue = ControlMode.Flow.wireValue,
            red = 0,
            green = 255,
            blue = 0,
            brightness = 17,
            flowInterval = 25,
            breathPeriod = Int.MIN_VALUE,
            gradientPeriod = Int.MIN_VALUE,
            legacyPeriod = Int.MIN_VALUE,
            order = RgbControlState.DefaultOrder,
            flowFrames = listOf(3, 12, 48, 192)
        )

        assertEquals(listOf(3, 12, 48, 192), decoded.flowFrames)
    }

    @Test
    fun decodeControlFallsBackToHiddenAdvancedSendPanelWhenFieldIsAbsent() {
        val state = AppStorageState(
            control = AppStorage.decodeControl(
                """
                {
                  "mode": 1,
                  "red": 0,
                  "green": 255,
                  "blue": 0,
                  "brightness": 17,
                  "flowInterval": 25,
                  "order": [3, 2, 1, 0, 4, 5, 6, 7]
                }
                """.trimIndent()
            )
        )

        assertFalse(state.showAdvancedSendPanel)
        assertFalse(state.useAdvancedFlowEditor)
    }
}
