package com.rgbws2812.controller.model

enum class ControlMode(val wireValue: Int, val title: String) {
    Static(0, "静态"),
    Flow(1, "流水"),
    Breath(2, "呼吸"),
    Disco(3, "Disco"),
    Gradient(4, "渐变"),
    FlowGradient(5, "流动渐变"),
    MusicReactive(6, "音乐律动"),
    CustomRealtimeFlow(7, "自定义流水");

    companion object {
        fun fromWireValue(value: Int): ControlMode =
            entries.firstOrNull { it.wireValue == value } ?: Static
    }
}

val ControlMode.isGradientFamily: Boolean
    get() = this == ControlMode.Gradient || this == ControlMode.FlowGradient
