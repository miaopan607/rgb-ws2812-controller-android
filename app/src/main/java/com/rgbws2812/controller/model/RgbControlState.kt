package com.rgbws2812.controller.model

import com.rgbws2812.controller.protocol.RealtimeFrameBuilder
import com.rgbws2812.controller.protocol.RealtimeLed

data class RealtimeFlowFrame(
    val durationTicks: Int = RgbControlState.DefaultRealtimeFlowDurationTicks,
    val leds: List<RealtimeLed> = RgbControlState.DefaultRealtimeFlowLeds
) {
    fun clamped(): RealtimeFlowFrame =
        copy(
            durationTicks = durationTicks.coerceIn(1, 255),
            leds = sanitizeLeds(leds)
        )

    companion object {
        fun sanitizeLeds(leds: List<RealtimeLed>): List<RealtimeLed> {
            val clean = leds.take(RealtimeFrameBuilder.LedCount).map { it.clamped() }
            return clean + List(RealtimeFrameBuilder.LedCount - clean.size) { RgbControlState.DefaultRealtimeFlowLed }
        }
    }
}

data class RgbControlState(
    val mode: ControlMode = ControlMode.Flow,
    val red: Int = 0,
    val green: Int = 255,
    val blue: Int = 0,
    val brightness: Int = 17,
    val flowInterval: Int = DefaultFlowInterval,
    val breathPeriod: Int = DefaultBreathPeriod,
    val gradientPeriod: Int = DefaultGradientPeriod,
    val order: List<Int> = DefaultOrder,
    val flowFrames: List<Int> = DefaultFlowFrames,
    val realtimeFlowFrames: List<RealtimeFlowFrame> = DefaultRealtimeFlowFrames
) {
    val rgbHex: String
        get() = "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

    fun clamped(): RgbControlState = copy(
        red = red.coerceIn(0, 255),
        green = green.coerceIn(0, 255),
        blue = blue.coerceIn(0, 255),
        brightness = brightness.coerceIn(0, 255),
        flowInterval = flowInterval.coerceIn(1, 255),
        breathPeriod = breathPeriod.coerceIn(1, 255),
        gradientPeriod = gradientPeriod.coerceIn(1, 255),
        order = order.filter { it in 0..7 }.distinct().take(MaxFlowFrames),
        flowFrames = sanitizeFlowFrames(flowFrames),
        realtimeFlowFrames = sanitizeRealtimeFlowFrames(realtimeFlowFrames)
    )

    fun activePeriodForMode(): Int =
        when (mode) {
            ControlMode.Flow -> flowInterval
            ControlMode.Breath -> breathPeriod
            ControlMode.Gradient,
            ControlMode.FlowGradient -> gradientPeriod
            ControlMode.CustomRealtimeFlow -> DefaultRealtimeFlowDurationTicks
            else -> DefaultUnusedPeriod
        }

    companion object {
        const val MaxFlowFrames = 8
        const val DefaultFlowInterval = 25
        const val DefaultBreathPeriod = 100
        const val DefaultGradientPeriod = 20
        const val DefaultUnusedPeriod = 20
        const val DefaultRealtimeFlowDurationTicks = 25
        val EmptyFlowFrames = listOf(0)
        val DefaultOrder = listOf(3, 2, 1, 0, 4, 5, 6, 7)
        val DefaultFlowFrames = EmptyFlowFrames
        val DefaultRealtimeFlowLed = RealtimeLed(red = 0, green = 0, blue = 0, level = 0)
        val DefaultRealtimeFlowLeds = List(RealtimeFrameBuilder.LedCount) { DefaultRealtimeFlowLed }
        val DefaultRealtimeFlowFrames = listOf(RealtimeFlowFrame())
        val Default = RgbControlState()

        fun orderToFlowFrames(order: List<Int>): List<Int> =
            order.filter { it in 0..7 }
                .distinct()
                .take(MaxFlowFrames)
                .map { led -> 1 shl led }
                .ifEmpty { listOf(0) }

        fun sanitizeFlowFrames(frames: List<Int>): List<Int> =
            frames.take(MaxFlowFrames)
                .map { it.coerceIn(0, 255) }
                .ifEmpty { EmptyFlowFrames }

        fun sanitizeRealtimeFlowFrames(frames: List<RealtimeFlowFrame>): List<RealtimeFlowFrame> =
            frames.map { it.clamped() }
                .ifEmpty { DefaultRealtimeFlowFrames }
    }
}
