package id.xydesk.remote.ui

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Terjemahan runtime untuk string sebaris `xy()`.
 *
 * Tabel manual di [XyText] tetap prioritas untuk layar inti. Untuk bahasa
 * di luar ID/EN/MS, string EN diterjemahkan massal lewat endpoint worker
 * `/api/ui/translate`, dicache permanen di SharedPreferences, lalu UI
 * render ulang lewat [XyText.notifyTranslationsUpdated]. Selama unduhan
 * berjalan, UI menampilkan EN (tidak pernah kosong/berkedip).
 */
object UiTranslator {
    private const val ENDPOINT = "https://api.xydeskremote.biz.id/api/ui/translate"
    private const val PREFS = "xydesk.ui_tr"
    private const val BATCH_MAX = 40

    @Volatile private var prefs: SharedPreferences? = null
    private val cache = ConcurrentHashMap<String, String>()
    private val queued = ConcurrentHashMap.newKeySet<String>()
    private val queue = ConcurrentLinkedQueue<Pair<XyLang, String>>()
    private val flushScheduled = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "xy-ui-translate").apply { isDaemon = true }
    }

    fun install(context: Context) {
        if (prefs != null) return
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = sp
        for ((k, v) in sp.all) {
            val value = v as? String ?: continue
            if (value.isNotBlank()) cache[k] = value
        }
    }

    private fun keyOf(lang: XyLang, sourceEn: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(sourceEn.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return lang.code + ":" + hex
    }

    /** Terjemahan tersimpan untuk [sourceEn]; null bila belum ada (EN ditampilkan dulu). */
    fun lookup(lang: XyLang, sourceEn: String): String? {
        if (lang == XyLang.ID || lang == XyLang.EN || sourceEn.isBlank()) return null
        val key = keyOf(lang, sourceEn)
        cache[key]?.let { return it }
        enqueue(lang, sourceEn)
        return null
    }

    private fun enqueue(lang: XyLang, sourceEn: String) {
        val key = keyOf(lang, sourceEn)
        if (!queued.add(key)) return
        queue.add(lang to sourceEn)
        if (flushScheduled.compareAndSet(false, true)) {
            executor.execute {
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                }
                flushScheduled.set(false)
                flush()
            }
        }
    }

    private fun protect(text: String): String {
        var out = text
        for (i in 0..9) out = out.replace("{$i}", " ZXQPH${i}ZX ")
        out = out.replace("%s", " ZXQPHSZX ").replace("%d", " ZXQPHDZX ")
        return out
    }

    private fun restore(text: String): String {
        var out = text
        for (i in 0..9) out = out.replace("ZXQPH${i}ZX", "{$i}", ignoreCase = true)
        out = out.replace("ZXQPHSZX", "%s", ignoreCase = true)
            .replace("ZXQPHDZX", "%d", ignoreCase = true)
        return out.trim()
    }

    private fun flush() {
        val byLang = HashMap<XyLang, MutableList<String>>()
        while (true) {
            val item = queue.poll() ?: break
            byLang.getOrPut(item.first) { mutableListOf() }.add(item.second)
        }
        if (byLang.isEmpty()) return
        var changed = false
        for ((lang, sources) in byLang) {
            for (chunk in sources.chunked(BATCH_MAX)) {
                val translated = postBatch(lang, chunk) ?: continue
                val editor = prefs?.edit()
                chunk.forEachIndexed { index, source ->
                    queued.remove(keyOf(lang, source))
                    val value = translated.getOrNull(index)?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
                    val key = keyOf(lang, source)
                    cache[key] = value
                    editor?.putString(key, value)
                    changed = true
                }
                editor?.apply()
            }
        }
        if (changed) XyText.notifyTranslationsUpdated()
    }

    private fun postBatch(lang: XyLang, sources: List<String>): List<String>? {
        return try {
            val body = JSONObject()
                .put("target", lang.code)
                .put("items", JSONArray(sources.map { protect(it) }))
                .toString()
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 20000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) return null
            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val items = JSONObject(raw).optJSONArray("items") ?: return null
            List(items.length()) { i -> restore(items.optString(i)) }
        } catch (_: Exception) {
            null
        }
    }
}
