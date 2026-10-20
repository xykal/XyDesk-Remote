package id.xydesk.remote.ui

import org.json.JSONObject
import java.security.MessageDigest

internal data class OutboxEntry(
    val id: String, val login: String, val credentialFingerprint: String, val createdAt: Long,
    val payload: String, val ownerId: Long = 0, val state: String = "pending",
    val attempts: Int = 0, val nextAttemptAt: Long = 0, val issue: String = "",
    val lastAttemptFingerprint: String = "", val receipt: String = "", val retryNotBefore: Long = 0,
)

internal object ChatOutboxPolicy {
    const val MAX_AGE_MS = 604_800_000L
    val LOCAL_ID = Regex("local-[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
    val PAYLOAD_FIELDS = setOf("type", "name", "github_login", "country", "text", "is_announcement",
        "e2ee_v", "e2ee_cipher", "e2ee_iv", "e2ee_sig", "client_msg_id", "reply_to_id", "reply_to_name", "reply_to_text",
        "attachment_type", "attachment_kind", "attachment_name", "attachment_mime", "attachment_size", "attachment_data", "view_once", "avatar_url")

    fun fingerprint(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun belongsTo(entry: OutboxEntry, fingerprint: String, verifiedOwner: Long = 0): Boolean =
        if (entry.ownerId > 0 && verifiedOwner > 0) entry.ownerId == verifiedOwner
        else entry.credentialFingerprint == fingerprint

    fun delayMs(attempt: Int, retryAfterSeconds: Long = 0): Long = maxOf(
        (30_000L * (1L shl attempt.coerceIn(0, 7))).coerceAtMost(3_600_000L),
        retryAfterSeconds.coerceIn(0, 86_400) * 1000L)

    fun receipt(response: JSONObject, entry: OutboxEntry, owner: Long): JSONObject? {
        if (response.optString("status") != "ok" || response.optInt("send_protocol") != 1) return null
        val receipt = response.optJSONObject("receipt") ?: return null
        val id = receipt.optString("id")
        if (receipt.optLong("author_id") != owner || receipt.optString("client_msg_id") != entry.id ||
            id.isBlank() || id.startsWith("local-") || id.length > 80) return null
        val message = response.optJSONObject("message")
        if (message != null && (message.optString("id") != id || message.optLong("author_id") != owner ||
                message.optString("client_msg_id") != entry.id)) return null
        if (message == null && !response.optBoolean("receipt_only")) return null
        // Only metadata in the durable acceptance journal, not stale queued content.
        return JSONObject().put("id", id).put("author_id", owner).put("client_msg_id", entry.id)
            .put("at", receipt.optLong("at")).put("github_login", receipt.optString("github_login"))
    }

    fun pendingMessage(entry: OutboxEntry, nickname: String, login: String): ChatMessage {
        val body = JSONObject(entry.payload).put("id", entry.id).put("at", entry.createdAt)
        return parseMessageFromJson(body, nickname, body.optString("github_login")).copy(
            mine = true, githubLogin = login, authorId = entry.ownerId,
            deliveryTicks = if (entry.nextAttemptAt == Long.MAX_VALUE) -1 else 0,
            clientMessageId = entry.id, e2eeVerified = false)
    }
}
