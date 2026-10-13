package id.xydesk.remote.ui

import android.content.Context
import org.json.JSONObject

/**
 * Sinkronisasi cloud untuk setelan & kontrol kustom (permintaan pemilik
 * ronde 14, dieksekusi 2026-10-13).
 *
 * Prinsip:
 * - TIDAK ADA kredensial yang ikut: kata sandi (`xydesk.pass`), sertifikat,
 *   token, dan riwayat clipboard tidak pernah meninggalkan perangkat; kunci
 *   yang namanya mencurigakan juga disaring.
 * - Blob disimpan di worker per ID akun anonim; setelah pasang ulang,
 *   pulihkan akun lewat kode pemulihan lalu "Pulihkan dari Cloud".
 * - Nilai diberi tag tipe (s/i/l/f/b/set) agar Long/Float/Set tidak tertukar
 *   saat dipulihkan dari JSON.
 */
object CloudSync {

    /** Berkas SharedPreferences yang ikut dicadangkan. */
    val SYNC_FILES = listOf(
        "xydesk.app",
        "xydesk.input",
        "xydesk.key",
        "xydesk.session",
        "xydesk.remote.display",
        "xydesk.lang",
        "xydesk.splash",
        "xydesk.label",
        "xydesk.channel",
        "xydesk.host",
        "xydesk.port",
        "xydesk.user",
        "xydesk.domain",
        "xydesk.rdp.options",
    )

    private val SENSITIVE_PARTS = listOf("pass", "token", "secret", "pat", "cookie", "oauth", "auth", "credential")

    private fun isSensitive(key: String): Boolean {
        val lower = key.lowercase()
        return SENSITIVE_PARTS.any { lower.contains(it) }
    }

    /** Kumpulkan semua setelan yang boleh disinkronkan jadi satu blob JSON. */
    fun collect(context: Context): String {
        val bundle = JSONObject()
        for (name in SYNC_FILES) {
            val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
            val obj = JSONObject()
            for ((key, value) in prefs.all) {
                if (isSensitive(key)) continue
                val tagged = when (value) {
                    is String -> JSONObject().put("t", "s").put("v", value)
                    is Int -> JSONObject().put("t", "i").put("v", value)
                    is Long -> JSONObject().put("t", "l").put("v", value)
                    is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                    is Boolean -> JSONObject().put("t", "b").put("v", value)
                    is Set<*> -> JSONObject().put("t", "set").put("v", value.joinToString("\u0001"))
                    else -> null
                }
                if (tagged != null) obj.put(key, tagged)
            }
            if (obj.length() > 0) bundle.put(name, obj)
        }
        return bundle.toString()
    }

    /** Terapkan blob cloud ke setelan lokal. Mengembalikan jumlah nilai diterapkan. */
    fun restore(context: Context, blob: String): Int {
        val bundle = JSONObject(blob)
        var applied = 0
        val names = bundle.keys()
        while (names.hasNext()) {
            val name = names.next()
            if (name !in SYNC_FILES) continue
            val obj = bundle.optJSONObject(name) ?: continue
            val editor = context.applicationContext
                .getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (isSensitive(key)) continue
                val tagged = obj.optJSONObject(key) ?: continue
                when (tagged.optString("t")) {
                    "s" -> editor.putString(key, tagged.optString("v"))
                    "i" -> editor.putInt(key, tagged.optInt("v"))
                    "l" -> editor.putLong(key, tagged.optLong("v"))
                    "f" -> editor.putFloat(key, tagged.optDouble("v").toFloat())
                    "b" -> editor.putBoolean(key, tagged.optBoolean("v"))
                    "set" -> editor.putStringSet(
                        key,
                        tagged.optString("v").split("\u0001").filter { it.isNotEmpty() }.toSet(),
                    )
                    else -> continue
                }
                applied++
            }
            editor.apply()
        }
        return applied
    }

    /** Unggah cadangan sekarang; mengembalikan cap waktu dari server. */
    suspend fun upload(context: Context): String {
        val blob = collect(context)
        val resp = CommunityJokesApi(context).accountSettingsPut(blob)
        return resp.optString("updated_at")
    }

    /** Unduh blob cadangan; null bila belum ada / gagal. */
    suspend fun download(context: Context): String? {
        return try {
            CommunityJokesApi(context)
                .accountSettingsGet()
                .optString("blob")
                .takeIf { it.isNotBlank() }
        } catch (error: Exception) {
            null
        }
    }
}
