package com.rgbws2812.controller.model

enum class ControlMode(val wireValue: Int, val title: String) {
    Static(0, "静态"),
    Flow(1, "流水"),
    Breath(2, "呼吸"),
    Gradient(3, "渐变");

    companion object {
        fun fromWireValue(value: Int): ControlMode =
            entries.firstOrNull { it.wireValue == value } ?: Static
    }
}
