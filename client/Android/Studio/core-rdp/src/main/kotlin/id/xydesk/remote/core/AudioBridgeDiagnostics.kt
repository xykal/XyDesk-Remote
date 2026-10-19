package id.xydesk.remote.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Existing XYST1 host claims, NOT authenticated endpoint or game-input detection. */
data class HostAudioReport(
    val receivedAtMs: Long,
    val flags: Int,
    val micLevel: Int,
    val audioPackets: Long,
    val micFrames: Long,
    val detail: String,
) {
    fun isFresh(nowMs: Long): Boolean = nowMs >= receivedAtMs && nowMs - receivedAtMs <= 5_000L
}

data class AudioBridgeDiagnostics(
    val requested: Boolean = false,
    val capturing: Boolean = false,
    val host: HostAudioReport? = null,
)

internal fun parseHostAudioReport(bytes: ByteArray, length: Int, receivedAtMs: Long): HostAudioReport? {
    if (length < 16 || length > bytes.size || receivedAtMs < 0) return null
    if (bytes[0] != 'X'.code.toByte() || bytes[1] != 'Y'.code.toByte() ||
        bytes[2] != 'S'.code.toByte() || bytes[3] != 'T'.code.toByte() || bytes[4] != '1'.code.toByte()) return null
    val counters = ByteBuffer.wrap(bytes, 8, 8).order(ByteOrder.LITTLE_ENDIAN)
    return HostAudioReport(
        receivedAtMs, bytes[5].toInt() and 0xFF, bytes[6].toInt() and 0xFF,
        counters.int.toLong() and 0xFFFF_FFFFL, counters.int.toLong() and 0xFFFF_FFFFL,
        if (length > 16) String(bytes, 16, (length - 16).coerceAtMost(496), Charsets.UTF_8) else "",
    )
}
