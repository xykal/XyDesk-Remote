package id.xydesk.remote.privacy

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Best-effort, anonymous lease reporter for the default-on, user-disableable session counter.
 * The only application payload is an action and a per-session random UUID.
 * Host/profile/account details are deliberately not accepted by this API.
 */
internal object ActiveSessionStatsReporter {
    private const val ENDPOINT = "https://api.xydeskremote.biz.id/api/session"

    // Layar sesi memanggil heartbeat tiap ±8 detik, tetapi lease di Worker
    // hidup 90 detik. Mengirim ulang lebih sering dari 60 detik hanya
    // membakar request Worker/Durable Object tanpa mengubah hasil hitungan.
    private const val HEARTBEAT_MIN_INTERVAL_MS = 60_000L
    private val lastHeartbeatAt = ConcurrentHashMap<UUID, Long>()

    fun heartbeat(sessionId: UUID): Boolean {
        val now = System.currentTimeMillis()
        val last = lastHeartbeatAt[sessionId]
        if (last != null && now - last < HEARTBEAT_MIN_INTERVAL_MS) return true
        val ok = send("heartbeat", sessionId)
        if (ok) lastHeartbeatAt[sessionId] = now
        return ok
    }

    fun end(sessionId: UUID): Boolean {
        lastHeartbeatAt.remove(sessionId)
        return send("end", sessionId)
    }

    private fun send(action: String, sessionId: UUID): Boolean {
        val connection = try {
            URL(ENDPOINT).openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return false
        }

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            val body = JSONObject()
                .put("action", action)
                .put("session_id", sessionId.toString())
                .toString()
                .toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            status in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }
}
