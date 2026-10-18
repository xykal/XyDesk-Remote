package id.xydesk.remote.ui

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal const val CHAT_SEND_URL = "https://api.xydeskremote.biz.id/api/chat/send"
internal const val CHAT_PIN_URL = "https://api.xydeskremote.biz.id/api/chat/pin"
internal const val CHAT_DELETE_URL = "https://api.xydeskremote.biz.id/api/chat/delete"

/** Blocking transport: callers must use Dispatchers.IO. Exceptions propagate to caller fallbacks.
 * WS lifecycle and history-state reconciliation remain in the screen for now.
 */
internal object GlobalChatRepository {
    fun postAction(endpoint: String, payload: JSONObject) {
        post(endpoint, payload, 8_000, false) { it.responseCode; Unit }
    }

    fun send(payload: JSONObject): JSONObject? =
        post(CHAT_SEND_URL, payload, 10_000, true) { connection ->
            if (connection.responseCode in 200..299) {
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } else null
        }

    private fun <T> post(
        endpoint: String,
        payload: JSONObject,
        timeoutMs: Int,
        acceptJson: Boolean,
        readResponse: (HttpURLConnection) -> T,
    ): T {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (acceptJson) connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            return readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }
}

internal fun parseMessageFromJson(m: JSONObject, myName: String, myGithubLogin: String?): ChatMessage {
    val msgId = m.optString("id")
    val sender = m.optString("name", "User")
    val ghLogin = m.optString("github_login", "").trim().removePrefix("@")
    val rawText = m.optString("text", "")
    val sourceLang = m.optString("source_lang", "")
    val rawCountry = m.optString("country", "ID").ifBlank { "ID" }
    val e2eeCipher = m.optString("e2ee_cipher", "")
    val e2eeIv = m.optString("e2ee_iv", "")
    val e2eeSig = m.optString("e2ee_sig", "")
    val e2eeProto = m.optString("e2ee_v", "XY-E2EE-GCM-v1").ifBlank { "XY-E2EE-GCM-v1" }

    val (openedText, verified) = ChatE2eeCrypto.openAndVerify(
        fallbackText = rawText,
        senderName = sender,
        githubLogin = ghLogin,
        cipherBase64 = e2eeCipher,
        ivBase64 = e2eeIv,
        signatureHex = e2eeSig,
    )

    val smartCountry = resolveSmartCountryClient(openedText, sourceLang, rawCountry)
    val flag = countryCodeToFlag(smartCountry)
    val rawAttachmentType = m.optString("attachment_type", "").ifBlank {
        val kind = m.optString("attachment_kind", "")
        if (kind == "photo") "image" else kind
    }
    val cleanedMediaText = if (rawAttachmentType == "image" || rawAttachmentType == "video" || rawAttachmentType == "sticker") {
        val trimmed = openedText.trim()
        if (trimmed.matches(Regex("^\\[(?:Foto|Video|Stiker|Media)[^\\]]*\\]$", RegexOption.IGNORE_CASE)) ||
            (rawAttachmentType == "sticker" && XY_STICKERS.any { it.title.equals(trimmed, ignoreCase = true) })
        ) {
            ""
        } else {
            openedText
        }
    } else {
        openedText
    }
    val resolvedAvatar = m.optString("avatar_url", "").ifBlank {
        if (ghLogin.isNotBlank()) "https://github.com/$ghLogin.png?size=120" else ""
    }
    val isMine = sender.equals(myName, ignoreCase = true) ||
        (!myGithubLogin.isNullOrBlank() && ghLogin.equals(myGithubLogin, ignoreCase = true))

    return ChatMessage(
        id = msgId,
        clientMessageId = m.optString("client_msg_id", ""),
        name = sender,
        githubLogin = ghLogin,
        country = smartCountry,
        flag = flag,
        text = cleanedMediaText,
        translated = if (isMine || cleanedMediaText.isBlank()) "" else m.optString("translated", ""),
        targetLang = m.optString("target_lang", "EN").ifBlank { "EN" },
        sourceLang = sourceLang,
        at = m.optLong("at", System.currentTimeMillis()),
        mine = isMine,
        isFounder = m.optBoolean("is_founder", isFounderAccount(sender) || isFounderAccount(ghLogin)),
        isAnnouncement = m.optBoolean("is_announcement", false),
        replyToId = m.optString("reply_to_id", ""),
        replyToName = m.optString("reply_to_name", ""),
        replyToText = m.optString("reply_to_text", ""),
        attachmentType = rawAttachmentType,
        attachmentName = m.optString("attachment_name", ""),
        attachmentMime = m.optString("attachment_mime", ""),
        attachmentSize = m.optInt("attachment_size", 0),
        attachmentData = m.optString("attachment_data", ""),
        viewOnce = m.optBoolean("view_once", false),
        avatarUrl = resolvedAvatar,
        deliveryTicks = 2,
        e2eeProtocol = e2eeProto,
        // Signature apa adanya dari server — jangan pernah dipabrikasi lokal,
        // itu membuat pesan tanpa signature tampak "terverifikasi".
        e2eeSig = e2eeSig,
        e2eeVerified = verified,
    )
}

internal fun parsePinnedMessageFromJson(p: JSONObject?): PinnedChatMessage? {
    if (p == null) return null
    val id = p.optString("id", "").trim()
    val text = p.optString("text", "").trim()
    if (id.isEmpty() && text.isEmpty()) return null
    return PinnedChatMessage(
        id = id,
        name = p.optString("name", "User"),
        githubLogin = p.optString("github_login", ""),
        text = text,
        pinnedBy = p.optString("pinned_by", ""),
        at = p.optLong("at", 0L),
    )
}

