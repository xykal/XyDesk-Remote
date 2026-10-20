package id.xydesk.remote.ui

import java.util.Locale

internal data class OfficialChannel(val id: String, val tag: String, val title: String)
internal val officialChannels = listOf(
    OfficialChannel("panduan-pengguna", "PanduanPengguna", "Panduan Pengguna"),
    OfficialChannel("informasi", "Informasi", "Informasi"),
    OfficialChannel("announcement", "Announcement", "Announcement"),
)
internal data class ChannelTagMatch(val start: Int, val end: Int, val channelId: String)
internal fun officialChannelId(tag: String): String? = when (tag.removePrefix("#").lowercase(Locale.ROOT)) {
    "panduanpengguna", "panduan-pengguna", "panduan" -> "panduan-pengguna"
    "informasi", "info" -> "informasi"
    "announcement", "pengumuman" -> "announcement"
    else -> null
}
internal fun channelTags(text: String): List<ChannelTagMatch> =
    Regex("(?<![\\p{L}\\p{N}_/#])#([\\p{L}\\p{N}_-]+)").findAll(text).mapNotNull { match ->
        officialChannelId(match.groupValues[1])?.let { ChannelTagMatch(match.range.first, match.range.last + 1, it) }
    }.toList()

/** A new message never pulls a reader away from older messages. Explicit send may. */
internal fun shouldFollowChat(nearBottom: Boolean, explicitOwnSend: Boolean = false): Boolean = nearBottom || explicitOwnSend

/** Server IDs only; HTTP, WS and history ACK paths may race. Memory is bounded. */
internal class ChatSentSoundGate(private val capacity: Int = 256) {
    init { require(capacity > 0) }
    private val played = LinkedHashSet<String>()
    fun accept(serverId: String): Boolean {
        if (serverId.isBlank() || serverId.startsWith("local-") || !played.add(serverId)) return false
        if (played.size > capacity) played.remove(played.first())
        return true
    }
}
