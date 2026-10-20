package id.xydesk.remote.ui

import org.json.JSONObject

internal enum class OutboxRetryState { READY, WAIT, NEEDS_LOGIN, EXPIRED, PERMANENT, TERMINAL, WRONG_ACCOUNT, CLOCK }
internal data class OutboxTextPreview(val text: String, val media: String, val viewOnce: Boolean)

/** Manual controls never mint a nonce, refresh creation time or override server backoff. */
internal object ChatOutboxControlPolicy {
    fun retryState(e: OutboxEntry, binding: String, verifiedOwner: Long, now: Long): OutboxRetryState = when {
        !ChatOutboxPolicy.belongsTo(e, binding, verifiedOwner) -> OutboxRetryState.WRONG_ACCOUNT
        e.state != "pending" -> OutboxRetryState.TERMINAL
        e.issue == "outbox_expired" || now >= e.createdAt && now - e.createdAt >= ChatOutboxPolicy.MAX_AGE_MS -> OutboxRetryState.EXPIRED
        e.createdAt > now && e.createdAt - now > 300_000 -> OutboxRetryState.CLOCK
        e.nextAttemptAt == Long.MAX_VALUE && e.issue != "authentication_required" -> OutboxRetryState.PERMANENT
        e.retryNotBefore > now || e.nextAttemptAt != Long.MAX_VALUE && e.nextAttemptAt > now -> OutboxRetryState.WAIT
        e.issue == "authentication_required" -> OutboxRetryState.NEEDS_LOGIN
        else -> OutboxRetryState.READY
    }

    fun retry(e: OutboxEntry, binding: String, verifiedOwner: Long, now: Long): OutboxEntry {
        require(verifiedOwner > 0) { "Identitas akun harus diperiksa ke server." }
        require(retryState(e, binding, verifiedOwner, now) in setOf(OutboxRetryState.READY, OutboxRetryState.NEEDS_LOGIN)) {
            "Retry belum diizinkan; waktu tunggu, identitas atau status pesan harus diperiksa."
        }
        return e.copy(ownerId = verifiedOwner, nextAttemptAt = 0, issue = "", lastAttemptFingerprint = binding)
    }

    fun cancel(e: OutboxEntry, binding: String, verifiedOwner: Long): OutboxEntry {
        require(ChatOutboxPolicy.belongsTo(e, binding, verifiedOwner)) { "Akun berubah; antrean tidak diubah." }
        return if (e.state == "pending") e.copy(state = "cancelled", payload = "", issue = "cancelled") else e
    }

    fun preview(e: OutboxEntry): OutboxTextPreview {
        require(e.state == "pending")
        val body = JSONObject(e.payload)
        return OutboxTextPreview(body.optString("text").take(500), body.optString("attachment_type").take(24), body.optBoolean("view_once"))
    }

    fun copyText(e: OutboxEntry, binding: String, verifiedOwner: Long): String {
        require(ChatOutboxPolicy.belongsTo(e, binding, verifiedOwner)) { "Akun berubah; isi tidak disalin." }
        val preview = preview(e)
        require(!preview.viewOnce && preview.text.isNotBlank()) { "Media sekali lihat atau pesan tanpa teks tidak diekspor." }
        return preview.text
    }
}
