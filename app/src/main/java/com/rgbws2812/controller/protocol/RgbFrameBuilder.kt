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
    val flowFrames: List<Int>,
    val checksum: Int,
    val bytes: ByteArray
) {
    val flowCount: Int
        get() = flowFrames.size

    fun spacedHex(): String = bytes.joinToString(" ") { it.toUnsignedHex() }
    fun compactHex(): String = bytes.joinToString("") { it.toUnsignedHex() }
}

object RgbFrameBuilder {
    const val MinFrameLength = 11
    const val MaxFrameLength = 18
    const val MaxFlowFrames = RgbControlState.MaxFlowFrames

    fun byteLabel(index: Int, frameLength: Int): String =
        when {
            index == 0 || index == 1 -> "帧头"
            index == 2 -> "模式"
            index == 3 -> "R"
            index == 4 -> "G"
            index == 5 -> "B"
            index == 6 -> "亮度"
            index == 7 -> "周期"
            index == 8 -> "画面数"
            index == frameLength - 1 -> "校验"
            index in 9 until frameLength - 1 -> "画面${index - 9}"
            else -> ""
        }

    fun build(control: RgbControlState): RgbFrame {
        val clean = control.clamped()
        val payloadFrames = toPayloadFlowFrames(clean)

        val payload = listOf(
            clean.mode.wireValue,
            clean.red,
            clean.green,
            clean.blue,
            clean.brightness,
            clean.period,
            payloadFrames.size
        ) + payloadFrames

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
            flowFrames = payloadFrames,
            checksum = checksum,
            bytes = bytes
        )
    }

    fun parseHex(text: String): RgbFrame {
        val bytes = parseHexBytes(text)
        require(bytes.size in MinFrameLength..MaxFrameLength) {
            "需要 $MinFrameLength..$MaxFrameLength 个字节，当前为 ${bytes.size} 个"
        }
        require(bytes[0] == 0xAA && bytes[1] == 0x55) { "帧头必须是 AA 55" }

        val flowCount = bytes[8]
        require(flowCount in 1..MaxFlowFrames) { "流水画面数量必须为 1..$MaxFlowFrames" }
        val expectedLength = 2 + 7 + flowCount + 1
        require(bytes.size == expectedLength) { "画面数量与帧长度不匹配，应为 $expectedLength 个字节" }

        val checksumIndex = bytes.lastIndex
        val checksum = bytes.subList(2, checksumIndex).fold(0) { acc, value -> acc xor value }
        require(checksum == bytes[checksumIndex]) {
            "校验失败，应为 ${checksum.toHexByte()}，实际为 ${bytes[checksumIndex].toHexByte()}"
        }

        val flowFrames = bytes.subList(9, checksumIndex)
        val order = flowFramesToOrder(flowFrames)

        return RgbFrame(
            mode = ControlMode.fromWireValue(bytes[2]),
            red = bytes[3],
            green = bytes[4],
            blue = bytes[5],
            brightness = bytes[6],
            period = bytes[7].coerceAtLeast(1),
            order = order,
            flowFrames = flowFrames,
            checksum = bytes[checksumIndex],
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
        order.size == MaxFlowFrames && order.all { it in 0..7 } && order.toSet().size == MaxFlowFrames

    fun isValidFlowFrames(frames: List<Int>): Boolean =
        frames.size in 1..MaxFlowFrames && frames.all { it in 0..255 }

    fun requireValidFlowFrames(frames: List<Int>) {
        require(isValidFlowFrames(frames)) { "流水画面数量必须为 1..$MaxFlowFrames，且每个画面为 0..255" }
    }

    fun orderToFlowFrames(order: List<Int>): List<Int> =
        RgbControlState.orderToFlowFrames(order)

    fun flowFramesToOrder(frames: List<Int>): List<Int> {
        // 只有单 bit 画面能无损还原成基础流水灯序，高级多灯画面不会强行映射。
        return frames.mapNotNull { mask ->
            (0 until 8).firstOrNull { led -> mask == (1 shl led) }
        }
    }

    fun toPayloadFlowFrames(frames: List<Int>): List<Int> =
        RgbControlState.sanitizeFlowFrames(frames)

    private fun toPayloadFlowFrames(control: RgbControlState): List<Int> =
        if (control.mode == ControlMode.Flow) {
            toPayloadFlowFrames(control.flowFrames)
        } else {
            listOf(0x00)
        }
}

fun Int.toHexByte(): String = coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')

private fun Byte.toUnsignedHex(): String = (toInt() and 0xFF).toHexByte()
