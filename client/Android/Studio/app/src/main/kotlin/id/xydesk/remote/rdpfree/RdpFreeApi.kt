package id.xydesk.remote.rdpfree

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Native HTTPS control plane. Never sends admin credentials or follows redirects with a bearer token. */
class RdpFreeApi(private val token: () -> String?) {
    class ApiException(val status: Int, message: String) : Exception(message)

    suspend fun status(): RdpFreeStatus = RdpFreeStatus.parse(request("GET", "/status"))
    suspend fun settings(): JSONObject = request("GET", "/settings")
    suspend fun saveSettings(inputs: JSONObject): JSONObject =
        request("PUT", "/settings", JSONObject().put("inputs", inputs))
    suspend fun start(): JSONObject = request("POST", "/runs", JSONObject().put("confirm", true))
    suspend fun stop(runId: Long): JSONObject {
        require(runId > 0) { "Run ID tidak valid" }
        return request("POST", "/runs/$runId/cancel", JSONObject().put("confirm", true))
    }
    suspend fun runs(): JSONObject = request("GET", "/runs")
    suspend fun revoke(): JSONObject = request("POST", "/device/revoke", JSONObject())

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val bearer = token()?.trim().orEmpty()
            if (!TOKEN_PATTERN.matches(bearer)) throw ApiException(401, "Masukkan token perangkat RdpFree yang valid")
            val connection = URL(BASE_URL + path).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 25000
            connection.requestMethod = method
            connection.setRequestProperty("Authorization", "Bearer $bearer")
            connection.setRequestProperty("Accept", "application/json")
            try {
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(8192)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        if (out.length + count > 1_048_576) throw ApiException(502, "Respons server terlalu besar")
                        out.append(buffer, 0, count)
                    }
                    out.toString()
                }.orEmpty()
                val result = runCatching { JSONObject(text) }.getOrNull()
                if (code !in 200..299) {
                    throw ApiException(code, result?.optString("error")?.takeIf { it.isNotBlank() }
                        ?: "Server RdpFree mengembalikan HTTP $code")
                }
                result ?: throw ApiException(502, "Respons server bukan JSON")
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        const val BASE_URL = "https://rdpfree.projectkal.my.id/api/v1"
        const val DEVICES_URL = "https://rdpfree.projectkal.my.id/dashboard/devices"
        const val VAULT_KEY = "rdpfree-api-device-v1"
        val TOKEN_PATTERN = Regex("^rdf_[A-Za-z0-9_-]{43}$")
    }
}

data class RdpFreeConnection(val host: String, val port: Int, val username: String, val transport: String)
data class RdpFreeStatus(
    val repo: String,
    val phase: String,
    val active: Boolean,
    val runId: Long?,
    val connection: RdpFreeConnection?,
    val expiresAt: String?,
) {
    companion object {
        fun parse(json: JSONObject): RdpFreeStatus {
            val raw = json.optJSONObject("connection")
            val host = raw?.optString("host").orEmpty()
            val port = raw?.optInt("port", 0) ?: 0
            val active = json.optBoolean("active", false)
            val phase = json.optString("phase", "stopped")
            val usable = active && phase == "ready" && host.isNotBlank() &&
                host.length <= 253 && !host.any { it.isWhitespace() || it == '/' || it == '@' } && port in 1..65535
            return RdpFreeStatus(
                repo = json.optString("repo"), phase = phase, active = active,
                runId = json.optJSONObject("run")?.optLong("id")?.takeIf { it > 0 },
                connection = if (usable) RdpFreeConnection(host, port,
                    raw?.optString("username")?.takeIf { it.isNotBlank() } ?: "xyadmin",
                    raw?.optString("transport").orEmpty()) else null,
                expiresAt = json.optJSONObject("session")?.optString("expires_at")?.takeIf { it.isNotBlank() },
            )
        }
    }
}
