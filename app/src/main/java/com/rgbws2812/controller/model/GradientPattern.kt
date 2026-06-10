package com.rgbws2812.controller.model

data class RgbColor(
    val red: Int,
    val green: Int,
    val blue: Int
)

object GradientPattern {
    const val PhaseCount = 1536
    private const val SegmentLength = 256
    const val FlowPhaseOffset = PhaseCount / 8

    fun discoColor(position: Int): RgbColor =
        when (position.floorMod(8)) {
            0, 3, 6 -> RgbColor(red = 0, green = 255, blue = 0)
            1, 4, 7 -> RgbColor(red = 255, green = 0, blue = 0)
            else -> RgbColor(red = 0, green = 0, blue = 255)
        }

    fun gradientColor(phase: Int): RgbColor {
        val wrappedPhase = phase.floorMod(PhaseCount)
        val segment = wrappedPhase / SegmentLength
        val offset = wrappedPhase % SegmentLength
        return when (segment) {
            0 -> RgbColor(red = 255, green = offset, blue = 0)
            1 -> RgbColor(red = 255 - offset, green = 255, blue = 0)
            2 -> RgbColor(red = 0, green = 255, blue = offset)
            3 -> RgbColor(red = 0, green = 255 - offset, blue = 255)
            4 -> RgbColor(red = offset, green = 0, blue = 255)
            else -> RgbColor(red = 255, green = 0, blue = 255 - offset)
        }
    }

    fun flowGradientColor(basePhase: Int, led: Int): RgbColor =
        gradientColor(basePhase + led * FlowPhaseOffset)
}

private fun Int.floorMod(divisor: Int): Int {
    val result = this % divisor
    return if (result < 0) result + divisor else result
}
