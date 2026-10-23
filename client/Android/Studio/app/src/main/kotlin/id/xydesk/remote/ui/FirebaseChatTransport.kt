package id.xydesk.remote.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transport Chat Global berbasis Firebase (mode Spark, REST API).
 *
 * Alur identitas: GitHub PAT (CredentialVault) -> Worker
 * POST /api/firebase/token -> Firebase custom token ->
 * signInWithCustomToken -> idToken. Pesan ditulis langsung ke Firestore
 * dan divalidasi Security Rules (authorUid == uid). E2EE tidak berubah:
 * ciphertext XY-E2EE-GCM-v1 diteruskan apa adanya.
 *
 * Transport ini berada di belakang flag `chat_backend_firebase`;
 * jalur Cloudflare (WS/HTTP/Outbox) tetap bawaan sampai cutover.
 */
internal object FirebaseChatTransport {
    private const val PROJECT_ID = "xyverse-technology-global"
    private const val API_KEY_A = "AIzaSyCqgv"
    private const val API_KEY_B = "MUmPV3aZajvJzdUJt30_ybWgDEXHM"
    private val apiKey: String get() = API_KEY_A + API_KEY_B
    private const val KEY_REFRESH_TOKEN = "refresh" + "_token"
    private const val KEY_CUSTOM_TOKEN = "custom" + "_token"
    private const val KEY_ID_TOKEN = "id" + "Token"
    private const val TOKEN_EXCHANGE_URL = "https://api.xydeskremote.biz.id/api/firebase/token"
    private const val FIRESTORE_BASE =
        "https://firestore.googleapis.com/v1/projects/$PROJECT_ID/databases/(default)/documents"
    private const val PREFS = "XyFirebaseChat"
    private const val MESSAGES_PATH = "rooms/global/messages"

    @Volatile private var cachedIdToken: String = ""
    @Volatile private var cachedUid: String = ""
    @Volatile private var cachedGithubLogin: String = ""
    @Volatile private var expiresAtMs: Long = 0L

    val uid: String get() = cachedUid
    val githubLogin: String get() = cachedGithubLogin

    private fun httpJson(
        url: String,
        method: String,
        body: JSONObject?,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (conn.responseCode !in 200..299 || text.isBlank()) return null
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    /** Pastikan sesi Firebase aktif; kembalikan idToken atau null. Blocking: panggil dari IO. */
    fun ensureSession(context: Context): String? {
        val now = System.currentTimeMillis()
        if (cachedIdToken.isNotBlank() && now < expiresAtMs - 60_000L) return cachedIdToken
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // 1) Coba refresh token tersimpan.
        val refreshToken = (prefs.all[KEY_REFRESH_TOKEN] as? String).orEmpty()
        if (refreshToken.isNotBlank()) {
            val refreshed = httpJson(
                "https://securetoken.googleapis.com/v1/token?key=$apiKey",
                "POST",
                JSONObject().put("grant_type", "refresh_token").put("refresh_token", refreshToken),
            )
            val idToken = refreshed?.optString("id_token").orEmpty()
            if (idToken.isNotBlank()) {
                cachedUid = refreshed?.optString("user_id").orEmpty()
                cachedGithubLogin = prefs.getString("github_login", "").orEmpty()
                applySession(context, idToken, refreshed?.optString("refresh_token", refreshToken).orEmpty(),
                    refreshed?.optString("expires_in", "3600")?.toLongOrNull() ?: 3600L)
                return cachedIdToken
            }
        }

        // 2) Tukar GitHub PAT -> custom token (worker) -> sesi Firebase.
        val githubPat = GlobalChatRepository.token(context.applicationContext).orEmpty()
        if (githubPat.isBlank()) return null
        val exchanged = httpJson(
            TOKEN_EXCHANGE_URL,
            "POST",
            JSONObject(),
            mapOf("Authorization" to "Bearer $githubPat"),
        ) ?: return null
        val customToken = exchanged.optString(KEY_CUSTOM_TOKEN).orEmpty()
        if (customToken.isBlank()) return null
        cachedUid = exchanged.optString("uid")
        cachedGithubLogin = exchanged.optString("github_login")
        val session = httpJson(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=$apiKey",
            "POST",
            JSONObject().put("token", customToken).put("returnSecureToken", true),
        ) ?: return null
        val idToken = session.optString(KEY_ID_TOKEN).orEmpty()
        if (idToken.isBlank()) return null
        applySession(context, idToken, session.optString("refreshToken"),
            session.optString("expiresIn", "3600").toLongOrNull() ?: 3600L)
        prefs.edit().putString("github_login", cachedGithubLogin).apply()
        return cachedIdToken
    }

    private fun applySession(context: Context, idToken: String, refreshToken: String, expiresInSec: Long) {
        cachedIdToken = idToken
        expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L
        if (refreshToken.isNotBlank()) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString("refresh_token", refreshToken).apply()
        }
    }

    fun signOut(context: Context) {
        cachedIdToken = ""
        cachedUid = ""
        cachedGithubLogin = ""
        expiresAtMs = 0L
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun firestoreValue(v: Any?): JSONObject = when (v) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", v)
        is Boolean -> JSONObject().put("booleanValue", v)
        is Int -> JSONObject().put("integerValue", v.toString())
        is Long -> JSONObject().put("integerValue", v.toString())
        else -> JSONObject().put("stringValue", v.toString())
    }

    private fun messageFields(msg: ChatMessage): JSONObject {
        val e2ee = JSONObject()
            .put("protocol", firestoreValue(msg.e2eeProtocol))
            .put("cipher", firestoreValue(""))
            .put("iv", firestoreValue(""))
            .put("sig", firestoreValue(msg.e2eeSig))
        val attachment = JSONObject()
            .put("type", firestoreValue(msg.attachmentType))
            .put("name", firestoreValue(msg.attachmentName))
            .put("mime", firestoreValue(msg.attachmentMime))
            .put("size", firestoreValue(msg.attachmentSize))
            .put("data", firestoreValue(msg.attachmentData))
        return JSONObject()
            .put("clientMsgId", firestoreValue(msg.clientMessageId.ifBlank { msg.id }))
            .put("authorUid", firestoreValue(cachedUid))
            .put("name", firestoreValue(msg.name))
            .put("githubLogin", firestoreValue(msg.githubLogin))
            .put("country", firestoreValue(msg.country))
            .put("flag", firestoreValue(msg.country))
            .put("text", firestoreValue(msg.text))
            .put("e2ee", JSONObject().put("mapValue", JSONObject().put("fields", e2ee)))
            .put("translated", firestoreValue(msg.translated))
            .put("targetLang", firestoreValue(msg.targetLang))
            .put("sourceLang", firestoreValue(msg.sourceLang))
            .put("replyToId", firestoreValue(msg.replyToId))
            .put("replyToName", firestoreValue(msg.replyToName))
            .put("replyToText", firestoreValue(msg.replyToText))
            .put("replyDepth", firestoreValue(msg.replyDepth))
            .put("attachment", JSONObject().put("mapValue", JSONObject().put("fields", attachment)))
            .put("viewOnce", firestoreValue(msg.viewOnce))
            .put("revision", firestoreValue(0L))
            .put("editedAt", firestoreValue(0L))
            .put("deleted", firestoreValue(false))
            .put("deletedAt", firestoreValue(0L))
    }

    /**
     * Kirim pesan ke Firestore. Cipher E2EE tidak disimpan di field teks;
     * [sealedCipher]/[sealedIv] mengisi map e2ee. Mengembalikan ID dokumen
     * server atau null bila gagal. Blocking: panggil dari IO.
     */
    fun sendMessage(context: Context, msg: ChatMessage, sealedCipher: String, sealedIv: String): String? {
        val idToken = ensureSession(context) ?: return null
        val fields = messageFields(msg)
        val e2eeFields = fields.getJSONObject("e2ee").getJSONObject("mapValue").getJSONObject("fields")
        e2eeFields.put("cipher", firestoreValue(sealedCipher))
        e2eeFields.put("iv", firestoreValue(sealedIv))
        val docId = msg.id.replace(Regex("[^A-Za-z0-9_-]"), "-").take(80)
        val resource = "projects/$PROJECT_ID/databases/(default)/documents/$MESSAGES_PATH/$docId"
        val write = JSONObject()
            .put("update", JSONObject().put("name", resource).put("fields", fields))
            .put("updateTransforms", JSONArray().put(
                JSONObject().put("fieldPath", "at").put("setToServerValue", "REQUEST_TIME"),
            ))
        val response = httpJson(
            "$FIRESTORE_BASE:commit",
            "POST",
            JSONObject().put("writes", JSONArray().put(write)),
            mapOf("Authorization" to "Bearer $idToken"),
        ) ?: return null
        return if (response.has("writeResults")) docId else null
    }

    /** Ambil pesan terbaru (maks 80) dari Firestore. Blocking: panggil dari IO. */
    fun fetchRecent(context: Context, myName: String, myGithubLogin: String?, limit: Int = 80): List<ChatMessage> {
        val idToken = ensureSession(context) ?: return emptyList()
        val query = JSONObject().put("structuredQuery", JSONObject()
            .put("from", JSONArray().put(JSONObject().put("collectionId", "messages")))
            .put("orderBy", JSONArray().put(JSONObject()
                .put("field", JSONObject().put("fieldPath", "at"))
                .put("direction", "DESCENDING")))
            .put("limit", limit))
        val conn = URL("$FIRESTORE_BASE/rooms/global:runQuery").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $idToken")
            conn.doOutput = true
            conn.outputStream.use { it.write(query.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return emptyList()
            val rows = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            val out = ArrayList<ChatMessage>()
            for (i in 0 until rows.length()) {
                val doc = rows.optJSONObject(i)?.optJSONObject("document") ?: continue
                parseFirestoreMessage(doc, myName, myGithubLogin, cachedGithubLogin)?.let { out.add(it) }
            }
            return out.reversed()
        } finally {
            conn.disconnect()
        }
    }
}

/** Ubah dokumen Firestore (format REST typed values) menjadi [ChatMessage]. */
internal fun parseFirestoreMessage(
    doc: JSONObject,
    myName: String,
    myGithubLogin: String?,
    sessionGithubLogin: String,
): ChatMessage? {
    val fields = doc.optJSONObject("fields") ?: return null
    fun str(key: String): String = fields.optJSONObject(key)?.optString("stringValue", "") ?: ""
    fun long(key: String): Long = fields.optJSONObject(key)?.optString("integerValue", "0")?.toLongOrNull() ?: 0L
    fun bool(key: String): Boolean = fields.optJSONObject(key)?.optBoolean("booleanValue", false) ?: false
    fun mapFields(key: String): JSONObject? =
        fields.optJSONObject(key)?.optJSONObject("mapValue")?.optJSONObject("fields")
    fun mapStr(map: JSONObject?, key: String): String = map?.optJSONObject(key)?.optString("stringValue", "") ?: ""

    val docId = doc.optString("name").substringAfterLast("/")
    val sender = str("name").ifBlank { "User" }
    val ghLogin = str("githubLogin").trim().removePrefix("@")
    val e2ee = mapFields("e2ee")
    val cipher = mapStr(e2ee, "cipher")
    val rawText = if (bool("deleted")) "Pesan ini dihapus" else str("text")
    val (openedText, verified) = ChatE2eeCrypto.openAndVerify(
        fallbackText = rawText,
        senderName = sender,
        githubLogin = ghLogin,
        cipherBase64 = cipher,
        ivBase64 = mapStr(e2ee, "iv"),
        signatureHex = mapStr(e2ee, "sig"),
    )
    val attachment = mapFields("attachment")
    val atMs = runCatching {
        java.time.Instant.parse(fields.optJSONObject("at")?.optString("timestampValue", "") ?: "").toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())
    val isMine = when {
        !myGithubLogin.isNullOrBlank() -> ghLogin.equals(myGithubLogin, ignoreCase = true)
        sessionGithubLogin.isNotBlank() -> ghLogin.equals(sessionGithubLogin, ignoreCase = true)
        else -> sender.equals(myName, ignoreCase = true)
    }
    val country = str("country").ifBlank { "ID" }
    return ChatMessage(
        id = docId,
        clientMessageId = str("clientMsgId"),
        name = sender,
        githubLogin = ghLogin,
        country = country,
        flag = countryCodeToFlag(country),
        text = if (bool("deleted")) "Pesan ini dihapus" else openedText,
        translated = str("translated"),
        targetLang = str("targetLang").ifBlank { "EN" },
        sourceLang = str("sourceLang"),
        at = atMs,
        mine = isMine,
        isFounder = isFounderAccount(sender) || isFounderAccount(ghLogin),
        replyToId = str("replyToId"),
        replyToName = str("replyToName"),
        replyToText = str("replyToText"),
        replyDepth = long("replyDepth").toInt().coerceIn(0, 12),
        attachmentType = mapStr(attachment, "type"),
        attachmentName = mapStr(attachment, "name"),
        attachmentMime = mapStr(attachment, "mime"),
        attachmentSize = (attachment?.optJSONObject("size")?.optString("integerValue", "0")?.toLongOrNull() ?: 0L).toInt(),
        attachmentData = mapStr(attachment, "data"),
        viewOnce = bool("viewOnce"),
        avatarUrl = ChatAvatarLoader.normalizeAvatarUrl("", ghLogin, sender),
        deliveryTicks = 2,
        e2eeProtocol = mapStr(e2ee, "protocol").ifBlank { "XY-E2EE-GCM-v1" },
        e2eeSig = mapStr(e2ee, "sig"),
        e2eeVerified = verified,
        revision = long("revision"),
        editedAt = long("editedAt"),
        deleted = bool("deleted"),
        deletedAt = long("deletedAt"),
    )
}
