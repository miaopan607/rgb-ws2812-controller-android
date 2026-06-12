package com.rgbws2812.controller.data

import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.AppStorageState
import com.rgbws2812.controller.model.RealtimeFlowFrame
import com.rgbws2812.controller.model.RgbControlState
import com.rgbws2812.controller.protocol.RealtimeFrameBuilder
import com.rgbws2812.controller.protocol.RealtimeLed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppStorageTest {
    @Test
    fun appStorageStateDefaultsToHiddenAdvancedSendPanel() {
        assertFalse(AppStorageState().showAdvancedSendPanel)
    }

    @Test
    fun appStorageStateDefaultsToBasicFlowEditor() {
        assertFalse(AppStorageState().useAdvancedFlowEditor)
    }

    @Test
    fun appStorageStateDefaultsRealtimeFlowAddToEmptyFrame() {
        assertFalse(AppStorageState().realtimeFlowAddCopiesLast)
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
    fun realtimeFlowFramesClampWithoutHardCountLimit() {
        val frames = List(72) { index ->
            RealtimeFlowFrame(
                durationTicks = if (index == 0) 0 else 300,
                leds = listOf(RealtimeLed(300, -1, index, 999))
            )
        }

        val decoded = RgbControlState(realtimeFlowFrames = frames).clamped()

        assertEquals(72, decoded.realtimeFlowFrames.size)
        assertEquals(1, decoded.realtimeFlowFrames.first().durationTicks)
        assertEquals(255, decoded.realtimeFlowFrames.last().durationTicks)
        assertEquals(RealtimeFrameBuilder.LedCount, decoded.realtimeFlowFrames.first().leds.size)
        assertEquals(RealtimeLed(255, 0, 0, 255), decoded.realtimeFlowFrames.first().leds.first())
    }

    @Test
    fun restoreControlStateKeepsRealtimeFlowFrames() {
        val decoded = AppStorage.restoreControlState(
            modeValue = ControlMode.CustomRealtimeFlow.wireValue,
            red = 0,
            green = 255,
            blue = 0,
            brightness = 88,
            flowInterval = 25,
            breathPeriod = Int.MIN_VALUE,
            gradientPeriod = Int.MIN_VALUE,
            legacyPeriod = Int.MIN_VALUE,
            order = RgbControlState.DefaultOrder,
            flowFrames = RgbControlState.EmptyFlowFrames,
            realtimeFlowFrames = listOf(
                RealtimeFlowFrame(
                    durationTicks = 33,
                    leds = listOf(RealtimeLed(1, 2, 3, 4))
                ),
                RealtimeFlowFrame(
                    durationTicks = 44,
                    leds = listOf(RealtimeLed(5, 6, 7, 8))
                )
            )
        )

        assertEquals(ControlMode.CustomRealtimeFlow, decoded.mode)
        assertEquals(2, decoded.realtimeFlowFrames.size)
        assertEquals(33, decoded.realtimeFlowFrames.first().durationTicks)
        assertEquals(RealtimeLed(1, 2, 3, 4), decoded.realtimeFlowFrames.first().leds.first())
        assertEquals(44, decoded.realtimeFlowFrames.last().durationTicks)
    }

    @Test
    fun restoreControlStateDefaultsRealtimeFlowFramesWhenMissing() {
        val decoded = AppStorage.restoreControlState(
            modeValue = ControlMode.CustomRealtimeFlow.wireValue,
            red = 0,
            green = 255,
            blue = 0,
            brightness = 120,
            flowInterval = 25,
            breathPeriod = Int.MIN_VALUE,
            gradientPeriod = Int.MIN_VALUE,
            legacyPeriod = Int.MIN_VALUE,
            order = RgbControlState.DefaultOrder,
            flowFrames = RgbControlState.EmptyFlowFrames
        )

        assertEquals(ControlMode.CustomRealtimeFlow, decoded.mode)
        assertEquals(120, decoded.brightness)
        assertEquals(RgbControlState.DefaultRealtimeFlowFrames, decoded.realtimeFlowFrames)
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
