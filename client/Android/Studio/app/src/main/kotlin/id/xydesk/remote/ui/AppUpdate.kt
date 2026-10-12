package id.xydesk.remote.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Info versi terbaru dari portal (worker /api/update-check). */
internal data class AppUpdateInfo(
    val version: String,
    val url: String,
    val changelog: String,
)

/**
 * Pemeriksa pembaruan app: satu panggilan GET ringan ke portal, tanpa
 * dependensi tambahan. Pembandingan versi per segmen numerik (1.1.10 > 1.1.9).
 */
internal object AppUpdate {
    private const val ENDPOINT = "https://api.xydeskremote.biz.id/api/update-check"

    suspend fun fetch(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = java.net.URL(ENDPOINT).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            val version = body.optString("version", "").trim()
            if (version.isEmpty()) return@runCatching null
            AppUpdateInfo(
                version = version,
                url = body.optString("url", ""),
                changelog = body.optString("changelog", ""),
            )
        }.getOrNull()
    }

    /** True bila [remote] lebih baru dari [current] (per segmen numerik). */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String): List<Int> =
            value.trim().removePrefix("v").split(".").map { seg ->
                seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
