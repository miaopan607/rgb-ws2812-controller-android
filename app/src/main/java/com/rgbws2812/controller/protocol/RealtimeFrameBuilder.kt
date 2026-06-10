package com.rgbws2812.controller.protocol

data class RealtimeLed(
    val red: Int,
    val green: Int,
    val blue: Int,
    val level: Int
) {
    fun clamped(): RealtimeLed =
        copy(
            red = red.coerceIn(0, 255),
            green = green.coerceIn(0, 255),
            blue = blue.coerceIn(0, 255),
            level = level.coerceIn(0, 255)
        )
}

data class RealtimeFrame(
    val sequence: Int,
    val maxBrightness: Int,
    val leds: List<RealtimeLed>,
    val checksum: Int,
    val bytes: ByteArray
) {
    fun spacedHex(): String = bytes.joinToString(" ") { (it.toInt() and 0xFF).toHexByte() }
}

object RealtimeFrameBuilder {
    const val FrameLength = 38
    const val LedCount = 8
    const val TypeDirectV1 = 0x10

    fun build(
        leds: List<RealtimeLed>,
        maxBrightness: Int,
        sequence: Int
    ): RealtimeFrame {
        require(leds.size == LedCount) { "实时图形帧必须包含 $LedCount 颗灯" }
        val cleanLeds = leds.map { it.clamped() }
        val payload = listOf(
            TypeDirectV1,
            sequence and 0xFF,
            maxBrightness.coerceIn(0, 255)
        ) + cleanLeds.flatMap { led ->
            listOf(led.red, led.green, led.blue, led.level)
        }
        val checksum = payload.fold(0) { acc, value -> acc xor value }
        val bytes = (listOf(0xAA, 0x5A) + payload + checksum)
            .map { it.coerceIn(0, 255).toByte() }
            .toByteArray()
        return RealtimeFrame(
            sequence = sequence and 0xFF,
            maxBrightness = maxBrightness.coerceIn(0, 255),
            leds = cleanLeds,
            checksum = checksum,
            bytes = bytes
        )
    }

    fun parseHex(text: String): RealtimeFrame {
        val bytes = RgbFrameBuilder.parseHexBytes(text)
        require(bytes.size == FrameLength) { "实时图形帧需要 $FrameLength 个字节，当前为 ${bytes.size} 个" }
        require(bytes[0] == 0xAA && bytes[1] == 0x5A) { "实时图形帧头必须是 AA 5A" }
        require(bytes[2] == TypeDirectV1) { "实时图形帧类型必须是 ${TypeDirectV1.toHexByte()}" }

        val checksum = bytes.subList(2, bytes.lastIndex).fold(0) { acc, value -> acc xor value }
        require(checksum == bytes.last()) {
            "校验失败，应为 ${checksum.toHexByte()}，实际为 ${bytes.last().toHexByte()}"
        }

        val leds = (0 until LedCount).map { index ->
            val offset = 5 + index * 4
            RealtimeLed(
                red = bytes[offset],
                green = bytes[offset + 1],
                blue = bytes[offset + 2],
                level = bytes[offset + 3]
            )
        }
        return RealtimeFrame(
            sequence = bytes[3],
            maxBrightness = bytes[4],
            leds = leds,
            checksum = bytes.last(),
            bytes = bytes.map { it.toByte() }.toByteArray()
        )
    }
}
