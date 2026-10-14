package id.xydesk.remote.pcstream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Sesi guest hub: JWT berumur pendek (±15 menit) + kunci pembaruan.
 * Keduanya rahasia — [toString] sengaja tidak membocorkannya.
 */
class XyHubGuestSession(
    val token: String,
    val refreshToken: String,
    val issuedAtEpochMs: Long,
) {
    override fun toString(): String =
        "XyHubGuestSession(token=[redacted], refreshToken=[redacted], issuedAtEpochMs=$issuedAtEpochMs)"
}

/** Kegagalan HTTP hub: [body] teks mentah (biasanya `{"error":...}`), [retryInSeconds] untuk 429. */
class XyHubHttpException(
    val httpStatus: Int,
    val body: String?,
    val retryInSeconds: Long?,
) : IOException("Permintaan hub gagal (HTTP $httpStatus${retryInSeconds?.let { ", retry ${it}s" } ?: ""})")

/** Respons hub tidak sesuai kontrak (bukan JSON, field wajib hilang, dsb.). */
class XyHubAuthException(message: String) : IOException(message)

/**
 * Satu ICE server dari `/turn-ice` (TURN ber-TTL / STUN tambahan).
 * [credential] rahasia — [toString] sengaja tidak membocorkannya.
 */
data class XyHubIceServer(
    val urls: List<String>,
    val username: String?,
    val credential: String?,
) {
    override fun toString(): String =
        "XyHubIceServer(urls=$urls, username=$username, credential=[redacted])"
}

/**
 * Onboarding klien hub signaling (`cloudflare/src/worker.js`):
 *
 * ```text
 * POST /auth/guest  {refresh?}          -> {token, refresh, guest:true}   (JWT ±15 menit)
 * GET  /signal-token?id=<deviceId>      -> teks tiket "v2.…" (±5 menit, terikat deviceId)
 * GET  /ws?id=<deviceId>&role=client&token=<tiket>  -> WebSocket (lihat XyHubSignalClient)
 * ```
 *
 * Jalur login penuh (OTP/Google) milik app XyDesk; XyDesk-Remote memakai
 * jalur guest yang memang disediakan `AuthStore.guest()`. Tiket client
 * ditandatangani `signBoundTicket` — hanya sah untuk role client dan deviceId
 * yang sama seperti saat diterbitkan, jadi tidak bisa dipinjam perangkat lain.
 *
 * Mengikuti [PcSignalClient]: HttpURLConnection murni (tanpa dependensi HTTP
 * baru), HTTPS wajib kecuali loopback untuk test.
 */
class XyHubAuth(
    baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
) : XyHubOnboarding {
    private val baseUrl: String = normalizeBaseUrl(baseUrl)

    init {
        require(connectTimeoutMs > 0) { "connectTimeoutMs must be positive" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be positive" }
    }

    /**
     * Ambil (atau perbarui lewat [refreshToken]) sesi guest.
     * Rate limit per-IP di server: 429 membawa `retry_in`.
     */
    override suspend fun guestLogin(refreshToken: String?): XyHubGuestSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (!refreshToken.isNullOrBlank()) body.put("refresh", refreshToken)
        val response = parseJson(request("POST", "/auth/guest", body))
        val token = response.requiredField("token")
        val refresh = response.requiredField("refresh")
        if (!response.optBoolean("guest", false)) {
            throw XyHubAuthException("Respons guest hub tidak berupa guest")
        }
        XyHubGuestSession(token = token, refreshToken = refresh, issuedAtEpochMs = System.currentTimeMillis())
    }

    /** Tukar [sessionToken] (JWT guest) dengan tiket WebSocket untuk [deviceId]. */
    override suspend fun signalToken(sessionToken: String, deviceId: String): String = withContext(Dispatchers.IO) {
        require(sessionToken.isNotBlank()) { "Token sesi kosong" }
        XyHubIds.requireClientId(deviceId)
        val path = "/signal-token?id=" + urlEncode(deviceId)
        val response = request("GET", path, bearerToken = sessionToken, plainText = true)
        val ticket = response.trim()
        if (!ticket.startsWith("v2.") || ticket.length < MIN_TICKET_LENGTH) {
            throw XyHubAuthException("Tiket hub tidak dikenali")
        }
        ticket
    }

    /**
     * Kredensial TURN/STUN tambahan dari `/turn-ice?id=&role=client&token=`
     * (token perangkat sama seperti saat connect `/ws` — klien tidak boleh
     * memegang ADMIN_SECRET). TURN opsional: 503 (belum dikonfigurasi /
     * otorisasi sedang gangguan) dan 5xx lain mengembalikan daftar kosong
     * supaya media tetap bisa jalan dengan STUN, seperti klien referensi.
     */
    override suspend fun turnIceServers(deviceId: String, ticket: String): List<XyHubIceServer> =
        withContext(Dispatchers.IO) {
            XyHubIds.requireClientId(deviceId)
            require(ticket.isNotBlank()) { "Tiket kosong" }
            val path = "/turn-ice?id=" + urlEncode(deviceId) +
                "&role=client&token=" + urlEncode(ticket)
            val responseText = try {
                request("GET", path)
            } catch (failure: XyHubHttpException) {
                if (failure.httpStatus == 503 || failure.httpStatus >= 500) {
                    return@withContext emptyList()
                }
                throw failure
            }
            val root = parseJson(responseText)
            val servers = root.optJSONArray("iceServers") ?: return@withContext emptyList()
            buildList(servers.length()) {
                for (index in 0 until servers.length()) {
                    val entry = servers.optJSONObject(index) ?: continue
                    val urls = entry.hubIceUrls()
                    if (urls.isEmpty()) continue
                    add(
                        XyHubIceServer(
                            urls = urls,
                            username = entry.optionalString("username"),
                            credential = entry.optionalString("credential"),
                        ),
                    )
                }
            }
        }

    /** `urls` di `/turn-ice` bisa string tunggal atau array (bentuk Cloudflare). */
    private fun JSONObject.hubIceUrls(): List<String> = when (val raw = opt("urls")) {
        is String -> if (raw.isBlank()) emptyList() else listOf(raw)
        is org.json.JSONArray -> buildList(raw.length()) {
            for (index in 0 until raw.length()) {
                raw.optString(index).takeIf { it.isNotBlank() && it != "null" }?.let { add(it) }
            }
        }
        else -> emptyList()
    }

    /** URL WebSocket `/ws` siap dibuka [XyHubSignalClient]. */
    override fun buildWebSocketUrl(deviceId: String, ticket: String): String {
        XyHubIds.requireClientId(deviceId)
        require(ticket.isNotBlank()) { "Tiket kosong" }
        val base = URI(baseUrl)
        val scheme = when (base.scheme.lowercase()) {
            "https" -> "wss"
            else -> "ws"
        }
        return "$scheme://${base.rawAuthority}/ws" +
            "?id=" + urlEncode(deviceId) +
            "&role=client" +
            "&token=" + urlEncode(ticket)
    }

    /**
     * Kirim satu permintaan dan kembalikan teks respons (pemanggil yang
     * mem-parse). [plainText] hanya mengatur header Accept untuk endpoint tiket.
     */
    private fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        bearerToken: String? = null,
        plainText: Boolean = false,
    ): String {
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Accept", if (plainText) "text/plain" else "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (bearerToken != null) setRequestProperty("Authorization", "Bearer $bearerToken")
        }

        try {
            if (body != null) {
                val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
                if (bytes.size > MAX_REQUEST_BYTES) {
                    throw IllegalArgumentException("Permintaan hub melebihi batas ukuran")
                }
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBytes = stream?.use { readBounded(it, MAX_RESPONSE_BYTES) } ?: ByteArray(0)
            val responseText = String(responseBytes, StandardCharsets.UTF_8)
            if (status !in 200..299) {
                throw XyHubHttpException(status, responseText, responseText.retryInSeconds())
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun String.retryInSeconds(): Long? = try {
        JSONObject(this).optLong("retry_in", -1).takeIf { it >= 0 }
    } catch (_: Exception) {
        null
    }

    private fun parseJson(text: String): JSONObject = try {
        JSONObject(text)
    } catch (_: Exception) {
        throw XyHubAuthException("Hub mengembalikan JSON tidak valid")
    }

    private fun JSONObject.requiredField(key: String): String =
        optString(key).takeIf { it.isNotBlank() && it != "null" }
            ?: throw XyHubAuthException("Field respons hub wajib hilang: $key")

    private fun JSONObject.optionalString(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        const val DEFAULT_BASE_URL = "https://signal.xydesk.my.id"
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000
        private const val DEFAULT_READ_TIMEOUT_MS = 8_000
        private const val MAX_REQUEST_BYTES = 8_192
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private const val MIN_TICKET_LENGTH = 16

        private fun normalizeBaseUrl(value: String): String {
            val parsed = try {
                URI(value.trim().trimEnd('/'))
            } catch (_: Exception) {
                throw IllegalArgumentException("Base URL hub tidak valid")
            }
            val host = parsed.host?.removeSurrounding("[", "]")?.lowercase()
            val isLoopbackHttp = parsed.scheme.equals("http", ignoreCase = true) &&
                host in setOf("localhost", "127.0.0.1", "::1")
            require(parsed.scheme.equals("https", ignoreCase = true) || isLoopbackHttp) {
                "Hub memakai HTTPS (HTTP hanya untuk test lokal)"
            }
            require(parsed.rawUserInfo == null) { "Base URL tidak boleh berisi user-info" }
            require(parsed.rawQuery == null && parsed.rawFragment == null) {
                "Base URL tidak boleh berisi query atau fragment"
            }
            require(parsed.path.isNullOrEmpty() || parsed.path == "/") {
                "Base URL harus origin tanpa path"
            }
            require(host != null) { "Base URL harus punya host" }
            return "${parsed.scheme}://${parsed.rawAuthority}"
        }

        private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream(minOf(limit, 8 * 1024))
            val buffer = ByteArray(4 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw XyHubAuthException("Respons hub melebihi batas ukuran")
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}
