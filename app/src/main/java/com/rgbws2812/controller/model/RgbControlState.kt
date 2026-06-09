package com.rgbws2812.controller.model

data class RgbControlState(
    val mode: ControlMode = ControlMode.Flow,
    val red: Int = 0,
    val green: Int = 255,
    val blue: Int = 0,
    val brightness: Int = 17,
    val period: Int = 20,
    val order: List<Int> = DefaultOrder
) {
    val rgbHex: String
        get() = "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))

    fun clamped(): RgbControlState = copy(
        red = red.coerceIn(0, 255),
        green = green.coerceIn(0, 255),
        blue = blue.coerceIn(0, 255),
        brightness = brightness.coerceIn(0, 255),
        period = period.coerceIn(1, 255),
        order = order.filter { it in 0..7 }.distinct().take(8)
    )

    companion object {
        val DefaultOrder = listOf(3, 2, 1, 0, 4, 5, 6, 7)
        val Default = RgbControlState()
    }
}
