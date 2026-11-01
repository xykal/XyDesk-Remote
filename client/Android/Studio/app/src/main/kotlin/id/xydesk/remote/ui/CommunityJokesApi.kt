package id.xydesk.remote.ui

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `JSONObject.optString` mengembalikan STRING "null" untuk nilai JSON null
 * (jebakan klasik Android) — laporan pemilik 2026-10-12: nama profil muncul
 * sebagai "null". Helper ini mengembalikan null sungguhan.
 */
private fun org.json.JSONObject.strOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/**
 * Klien akun anonim XyDesk: identitas pemasangan (client ID), nama tampilan,
 * avatar profil, sinkronisasi setelan cloud, dan resolve direktori relay PC.
 *
 * Feed komunitas/jokes, leaderboard, notifikasi feed, dan reaksi sudah
 * dihapus total (2026-10-26) bersama UI-nya — yang tersisa di kelas ini
 * hanya fungsi yang masih benar-benar dipanggil aplikasi.
 */
internal class CommunityJokesApi(context: Context) {
    private val appContext = context.applicationContext
    // Getter dinamis (bukan lazy val): setelah "Pulihkan dari Cloud" menulis
    // client ID lama ke prefs, instance API yang sudah ada langsung memakai
    // identitas lama tanpa perlu restart app.
    private val clientId: String get() = loadOrCreateClientId()

    /** Cloud sync setelan: unggah blob (lihat [CloudSync]). */
    suspend fun accountSettingsPut(blob: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("id", clientId).put("blob", blob)
        request(method = "PUT", path = "/api/account/settings", payload = payload)
    }

    /** Cloud sync setelan: unduh blob; melempar CommunityJokesException bila belum ada. */
    suspend fun accountSettingsGet(): JSONObject = withContext(Dispatchers.IO) {
        request(method = "GET", path = "/api/account/settings?id=$clientId")
    }

    /**
     * Resolve ID PC global (XY+8 hex) lewat direktori relay. Null bila PC
     * tidak terdaftar/offline (404) atau jaringan gagal.
     */
    suspend fun pcRelayResolve(globalId: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { request(method = "GET", path = "/api/pc/relay?id=$globalId") }.getOrNull()
    }

    /** Unggah avatar profil (maks ~200KB setelah kompresi). */
    suspend fun uploadAvatar(bytes: ByteArray, mime: String): String? = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("client_id", clientId)
            .put("avatar", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            .put("avatar_mime", mime)
        val body = request(method = "PUT", path = "/api/jokes/me", payload = payload)
        body.strOrNull("avatar_url")
    }

    private fun request(
        method: String,
        path: String,
        payload: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val connection = try {
            (URL("$ENDPOINT$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
        } catch (_: Exception) {
            throw CommunityJokesException("network")
        }

        return try {
            if (payload != null) {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (statusCode !in 200..299) {
                throw CommunityJokesException(body.optString("status").ifBlank { "http_$statusCode" })
            }
            body
        } catch (error: CommunityJokesException) {
            throw error
        } catch (_: Exception) {
            throw CommunityJokesException("network")
        } finally {
            connection.disconnect()
        }
    }

    // ---- akun anonim ringan (guest) ---------------------------------------
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** ID ramah pengguna, contoh: XY-7F3A9C. Turunan stabil dari client id. */
    fun friendlyAccountId(): String = "XY-" + clientId.replace("-", "").take(6).uppercase()

    /** ID klien mentah (UUID) untuk endpoint akun/feedback. */
    fun rawClientId(): String = clientId

    fun displayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }

    fun setDisplayNameLocally(name: String?) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name?.trim()?.takeIf { it.isNotBlank() }).apply()
    }

    suspend fun setDisplayName(name: String): Boolean = withContext(Dispatchers.IO) {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return@withContext false
        val ok = runCatching {
            request(
                method = "PUT",
                path = "/api/jokes/me",
                payload = JSONObject().put("client_id", clientId).put("display_name", clean),
            )
        }.isSuccess
        if (ok) setDisplayNameLocally(clean)
        ok
    }

    private fun loadOrCreateClientId(): String {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_CLIENT_ID, null)?.let { existing ->
            if (runCatching { UUID.fromString(existing) }.isSuccess) return existing
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CLIENT_ID, generated).apply()
        return generated
    }

    companion object {
        private const val ENDPOINT = "https://api.xydeskremote.biz.id"
        private const val PREFS_NAME = "xydesk.community.jokes"
        private const val KEY_CLIENT_ID = "anonymous_client_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}

/** Statistik pemakaian lokal (di HP saja) untuk ringkasan di Buat RDP & Beranda. */
internal fun recordAndGetLocalUsageSeconds(context: Context, addDeltaSeconds: Long = 0L): Long {
    val prefs = context.getSharedPreferences("xydesk_usage_stats", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val firstInstall = prefs.getLong("first_install_at", 0L).let {
        if (it <= 0L) {
            prefs.edit().putLong("first_install_at", now).putInt("sessions_count", 1).apply()
            now
        } else it
    }
    val prevSec = prefs.getLong("total_active_seconds", 900L).coerceAtLeast(300L)
    val updated = (prevSec + addDeltaSeconds.coerceIn(0L, 7200L)).coerceAtLeast(300L)
    if (addDeltaSeconds > 0L) {
        prefs.edit().putLong("total_active_seconds", updated).apply()
    }
    return updated
}

internal fun incrementLocalUsageSessionCount(context: Context) {
    val prefs = context.getSharedPreferences("xydesk_usage_stats", Context.MODE_PRIVATE)
    val c = prefs.getInt("sessions_count", 1).coerceAtLeast(1)
    prefs.edit().putInt("sessions_count", c + 1).apply()
}

internal class CommunityJokesException(val reason: String) : Exception(reason)
