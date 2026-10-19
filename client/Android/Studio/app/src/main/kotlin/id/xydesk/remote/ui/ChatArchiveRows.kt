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
    val seen = HashSet<String>()
    return buildList {
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            require(!row.optBoolean("view_once") && !row.optString("id").startsWith("local-"))
            val id = row.optString("id")
            require(id.isNotBlank() && id.length <= 256 && seen.add(id) && row.optLong("revision", 0) >= 0)
            // Do not pass imported crypto/authority/media fields through the live parser.
            val message = ChatMessage(
                id = id, name = row.optString("name").take(100), githubLogin = row.optString("github_login").take(100),
                country = row.optString("country").take(8), flag = "", text = row.optString("text").take(4000),
                translated = row.optString("translated").take(4000), targetLang = row.optString("target_lang").take(32),
                at = row.optLong("at", 0), mine = false, authorId = 0, isFounder = false,
                revision = row.optLong("revision", 0), deleted = row.optBoolean("deleted"), deletedAt = row.optLong("deleted_at", 0))
            add(if (message.deleted) message.deletedPlaceholder() else message)
        }
    }
}
