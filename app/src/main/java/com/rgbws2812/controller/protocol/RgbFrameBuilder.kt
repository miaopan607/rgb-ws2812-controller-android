package com.rgbws2812.controller.protocol

import com.rgbws2812.controller.model.ControlMode
import com.rgbws2812.controller.model.RgbControlState

data class RgbFrame(
    val mode: ControlMode,
    val red: Int,
    val green: Int,
    val blue: Int,
    val brightness: Int,
    val period: Int,
    val order: List<Int>,
    val checksum: Int,
    val bytes: ByteArray
) {
    fun spacedHex(): String = bytes.joinToString(" ") { it.toUnsignedHex() }
    fun compactHex(): String = bytes.joinToString("") { it.toUnsignedHex() }
}

object RgbFrameBuilder {
    const val FrameLength = 17
    val ByteLabels = listOf(
        "帧头", "帧头", "模式", "R", "G", "B", "亮度", "周期",
        "灯序0", "灯序1", "灯序2", "灯序3", "灯序4", "灯序5", "灯序6", "灯序7", "校验"
    )

    fun build(control: RgbControlState): RgbFrame {
        val clean = control.clamped()
        requireValidOrder(clean.order)

        val payload = listOf(
            clean.mode.wireValue,
            clean.red,
            clean.green,
            clean.blue,
            clean.brightness,
            clean.period
        ) + clean.order

        val checksum = payload.fold(0) { acc, value -> acc xor value }
        val bytes = (listOf(0xAA, 0x55) + payload + checksum)
            .map { it.coerceIn(0, 255).toByte() }
            .toByteArray()

        return RgbFrame(
            mode = clean.mode,
            red = clean.red,
            green = clean.green,
            blue = clean.blue,
            brightness = clean.brightness,
            period = clean.period,
            order = clean.order,
            checksum = checksum,
            bytes = bytes
        )
    }

    fun parseHex(text: String): RgbFrame {
        val bytes = parseHexBytes(text)
        require(bytes.size == FrameLength) { "需要 17 个字节，当前为 ${bytes.size} 个" }
        require(bytes[0] == 0xAA && bytes[1] == 0x55) { "帧头必须是 AA 55" }

        val checksum = bytes.subList(2, 16).fold(0) { acc, value -> acc xor value }
        require(checksum == bytes[16]) {
            "校验失败，应为 ${checksum.toHexByte()}，实际为 ${bytes[16].toHexByte()}"
        }

        val order = bytes.subList(8, 16)
        requireValidOrder(order)

        return RgbFrame(
            mode = ControlMode.fromWireValue(bytes[2]),
            red = bytes[3],
            green = bytes[4],
            blue = bytes[5],
            brightness = bytes[6],
            period = bytes[7].coerceAtLeast(1),
            order = order,
            checksum = bytes[16],
            bytes = bytes.map { it.toByte() }.toByteArray()
        )
    }

    fun parseHexBytes(text: String): List<Int> {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "请输入 Hex 字节" }

        val tokens = if (trimmed.contains(Regex("[\\s,;]"))) {
            trimmed.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
        } else {
            require(trimmed.length % 2 == 0) { "紧凑 Hex 长度必须是偶数" }
            trimmed.chunked(2)
        }

        return tokens.map { token ->
            val clean = token.removePrefix("0x").removePrefix("0X")
            require(clean.length in 1..2 && clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "非法 Hex 字节：$token"
            }
            clean.toInt(16).coerceIn(0, 255)
        }
    }

    fun isValidOrder(order: List<Int>): Boolean =
        order.size == 8 && order.all { it in 0..7 } && order.toSet().size == 8

    fun requireValidOrder(order: List<Int>) {
        require(isValidOrder(order)) { "流水灯序必须包含 0..7 且不能重复" }
    }
}

fun Int.toHexByte(): String = coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')

private fun Byte.toUnsignedHex(): String = (toInt() and 0xFF).toHexByte()
