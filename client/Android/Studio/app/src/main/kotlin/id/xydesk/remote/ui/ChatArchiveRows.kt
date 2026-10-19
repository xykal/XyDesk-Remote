package id.xydesk.remote.ui

import org.json.JSONArray
import org.json.JSONObject

/** Bounded text-only rows; intentionally excludes view-once, payloads and pending sends. */
internal fun encodeChatArchive(messages: List<ChatMessage>): ByteArray {
    val rows = JSONArray()
    messages.filter { !it.viewOnce && !it.mutationPending && !it.id.startsWith("local-") }.takeLast(80).forEach { original ->
                val msg = if (original.deleted) original.deletedPlaceholder() else original
                rows.put(JSONObject().apply {
                    put("id", msg.id); put("client_msg_id", msg.clientMessageId); put("name", msg.name); put("github_login", msg.githubLogin)
                    put("author_id", msg.authorId); put("revision", msg.revision); put("edited_at", msg.editedAt)
                    put("deleted", msg.deleted); put("deleted_at", msg.deletedAt)
                    put("text", msg.text.take(4_000)); put("at", msg.at); put("country", msg.country)
                    put("translated", msg.translated.take(4_000)); put("target_lang", msg.targetLang)
                    put("source_lang", msg.sourceLang); put("is_founder", msg.isFounder)
                    put("is_announcement", msg.isAnnouncement); put("reply_to_id", msg.replyToId)
                    put("reply_to_name", msg.replyToName); put("reply_to_text", msg.replyToText.take(500))
                    put("attachment_type", msg.attachmentType); put("attachment_name", msg.attachmentName.take(256))
                    put("attachment_mime", msg.attachmentMime); put("attachment_size", msg.attachmentSize)
                    put("avatar_url", msg.avatarUrl)
                })
            }
    return rows.toString().toByteArray(Charsets.UTF_8)
}

internal fun decodeChatArchive(bytes: ByteArray): List<ChatMessage> {
    require(bytes.size <= ChatBackupCrypto.MAX_PLAINTEXT)
    val rows = JSONArray(String(bytes, Charsets.UTF_8))
    require(rows.length() <= 80) { "Arsip maksimal 80 pesan teks." }
    return buildList {
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            require(!row.optBoolean("view_once") && !row.optString("id").startsWith("local-"))
            row.remove("attachment_data")
            val message = parseMessageFromJson(row, "", null).copy(
                mine = false, authorId = 0, isFounder = false, e2eeVerified = false, e2eeSig = "",
                attachmentData = "", mutationPending = false, mutationError = "", mutationId = "")
            add(if (message.deleted) message.deletedPlaceholder() else message)
        }
    }
}
