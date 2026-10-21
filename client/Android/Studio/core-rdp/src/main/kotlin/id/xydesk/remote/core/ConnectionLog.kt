package id.xydesk.remote.core

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Persistent connection diagnostics. Writes to Android/media/<package>/log
 * and mirrors a private copy in filesDir so logs survive unavailable media storage.
 *
 * Ronde 48: Semua peristiwa penting dari mesin native otomatis diterjemahkan
 * dan digabungkan ke dalam Log XyDesk satu per satu (tidak ada lagi blok log
 * FreeRDP terpisah).
 */
object ConnectionLog {
    private const val MAX_ENTRIES = 300
    private const val MAX_FILE_BYTES = 128 * 1024L
    private const val FILE_NAME = "xydesk-boot.log"
    private val buf = CopyOnWriteArrayList<String>()
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    @Volatile private var files: List<File> = emptyList()
    @Volatile private var visiblePath: String = "Belum diinisialisasi"
    @Volatile private var lastSyncedNativeLines: Int = 0

    /** Call before GlobalApp.onCreate: that superclass initializes JNI. */
    @Synchronized
    fun init(context: Context) {
        val app = context.applicationContext
        val internal = File(app.filesDir, FILE_NAME)
        val external = app.externalMediaDirs
            .firstOrNull()
            ?.let { File(File(it, "log"), FILE_NAME) }
            ?.takeIf { target ->
                runCatching {
                    target.parentFile?.let { it.isDirectory || it.mkdirs() } == true
                    FileOutputStream(target, true).use { }
                    target.parentFile?.canWrite() == true
                }.getOrDefault(false)
            }
        files = if (external != null) listOf(external, internal) else listOf(internal)
        visiblePath = external?.absolutePath ?: internal.absolutePath
        add("APP: diagnostics initialized; external=${external != null}; file=$visiblePath")
    }

    /** Path log native (di-set app saat boot); diserap ke Log XyDesk. */
    @Volatile private var nativeLogPath: String? = null

    fun setNativeLogFile(path: String) {
        nativeLogPath = path
        lastSyncedNativeLines = 0
        // Bersihkan file log native sesi lama saat boot agar sinkronisasi mulai segar
        runCatching {
            val f = File(path)
            if (f.exists() && f.length() > 64 * 1024L) {
                f.writeText("")
            } else if (f.exists()) {
                lastSyncedNativeLines = f.readLines().size
            }
        }
    }

    fun nativeLogPath(): String? = nativeLogPath

    /**
     * Menyerap baris penting dari log native lalu memindahkannya ke dalam
     * Log XyDesk satu per satu dengan bahasa yang jelas (tanpa label FreeRDP).
     */
    @Synchronized
    fun syncNativeIntoXyDeskLog() {
        val path = nativeLogPath ?: return
        runCatching {
            val file = File(path)
            if (!file.exists()) return
            val allLines = file.readLines()
            if (allLines.size < lastSyncedNativeLines) {
                lastSyncedNativeLines = 0
            }
            if (allLines.size <= lastSyncedNativeLines) return
            val newLines = allLines.subList(lastSyncedNativeLines, allLines.size)
            lastSyncedNativeLines = allLines.size
            for (raw in newLines) {
                val translated = translateNativeLine(raw) ?: continue
                add(translated)
            }
        }
    }

    private fun translateNativeLine(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase(Locale.ROOT)
        // Abaikan baris spam internal atau pointer mentah yang tidak informatif
        if (lower.contains("wlog_") || lower.contains("loading dynamic") || lower.contains("primitives")) {
            return null
        }
        // Ambil pesan setelah tanda " - " bila ada format WinPR standar
        val msgPart = trimmed.substringAfter(" - ", trimmed)
            .replace(Regex("(?i)freerdp_"), "")
            .replace(Regex("(?i)freerdp"), "XyDesk Engine")
            .replace(Regex("(?i)com\\.freerdp\\.[a-z0-9_.]+"), "XyDesk")
            .replace(Regex("(?i)com\\.winpr\\.[a-z0-9_.]+"), "XyDesk")
            .trim()
        if (msgPart.isEmpty()) return null

        return when {
            lower.contains("[error]") || lower.contains("[fatal]") ->
                "ENGINE ERROR: $msgPart"
            lower.contains("[warn]") ->
                "ENGINE WARN: $msgPart"
            lower.contains("rdpsnd") || lower.contains("audin") ->
                "AUDIO: Kanal suara/mikrofon aktif ($msgPart)"
            lower.contains("cliprdr") ->
                "SES: Kanal sinkronisasi clipboard aktif"
            lower.contains("rdpdr") || lower.contains("drive") ->
                "SES: Kanal transfer file (Drive XyDesk) aktif"
            lower.contains("disp") && lower.contains("channel") ->
                "SES: Kanal resolusi layar dinamis aktif"
            lower.contains("rdpgfx") || lower.contains("gfx") ->
                "ENGINE: Akselerasi grafis GFX aktif"
            lower.contains("certificate") || lower.contains("tls") ->
                "ENGINE: Negosiasi keamanan TLS ($msgPart)"
            else -> null
        }
    }

    /**
     * Ekor log native (dipertahankan untuk kompatibilitas internal, sudah
     * diterjemahkan ke format XyDesk).
     */
    fun nativeTail(lines: Int = 120): List<String> {
        syncNativeIntoXyDeskLog()
        return emptyList()
    }

    @Synchronized
    fun add(msg: String) {
        val cleanedMsg = msg
            .replace("Detail FreeRDP:", "Detail XyRDP Engine:")
            .replace("FreeRDP", "XyRDP Engine")
        val line = "${fmt.format(Date())}  $cleanedMsg"
        buf.add(line)
        while (buf.size > MAX_ENTRIES) buf.removeAt(0)
        files.forEach { appendBounded(it, line) }
    }

    /** Record an exception with stack trace; redact credentials before calling. */
    fun addThrowable(tag: String, error: Throwable) {
        val stack = error.stackTraceToString().take(12_000)
        add("$tag: ${error.javaClass.name}: ${error.message ?: "(tanpa pesan)"}\n$stack")
    }

    private fun appendBounded(file: File, line: String) {
        try {
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > MAX_FILE_BYTES) {
                val tail = file.readText().takeLast((MAX_FILE_BYTES / 2).toInt())
                file.writeText(tail)
            }
            FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use {
                it.append(line).append('\n')
            }
        } catch (_: Throwable) {
            // Best effort; the other mirror remains available if one volume fails.
        }
    }

    fun path(): String = visiblePath

    /** Complete persistent diagnostic text for the in-app viewer / share action. */
    fun readAll(context: Context): String {
        syncNativeIntoXyDeskLog()
        val candidates = files.ifEmpty { listOf(File(context.applicationContext.filesDir, FILE_NAME)) }
        val content = candidates.firstNotNullOfOrNull { file ->
            runCatching { file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() } }.getOrNull()
        }.orEmpty()
        return content.ifBlank { last(MAX_ENTRIES).joinToString("\n") }
    }

    fun last(n: Int = 60): List<String> {
        syncNativeIntoXyDeskLog()
        return if (buf.size <= n) buf.toList() else buf.subList(buf.size - n, buf.size)
    }

    /** Tail from prior process launch; native SIGSEGV logs survive restart. */
    fun tailFromFile(context: Context, n: Int = 60): List<String> {
        syncNativeIntoXyDeskLog()
        val candidates = files.ifEmpty {
            listOf(File(context.applicationContext.filesDir, FILE_NAME))
        }
        val fromDisk = candidates.firstNotNullOfOrNull { file ->
            runCatching {
                file.takeIf { it.exists() }?.readText()?.trim()?.lineSequence()?.toList()?.takeLast(n)
                    ?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }
        return fromDisk ?: last(n)
    }

    /**
     * Menyusun Log XyDesk yang rapi dan jelas satu per satu (bernomor urut 1, 2, 3...)
     * untuk ditampilkan di layar Log XyDesk maupun detail koneksi.
     */
    fun formattedXyDeskLog(context: Context? = null, limit: Int = 80): String {
        syncNativeIntoXyDeskLog()
        val rawLines = if (buf.isNotEmpty()) {
            last(limit)
        } else if (context != null) {
            tailFromFile(context, limit)
        } else {
            emptyList()
        }
        if (rawLines.isEmpty()) {
            return "=== LOG XYDESK ===\n1. [Info] Belum ada catatan aktivitas sesi."
        }
        return buildString {
            appendLine("=== LOG XYDESK (${rawLines.size} langkah tercatat) ===")
            var index = 1
            for (raw in rawLines) {
                val singleLines = raw.lines()
                val first = singleLines.firstOrNull()?.trim().orEmpty()
                if (first.isEmpty()) continue
                val formatted = humanizeRawLine(first)
                appendLine("$index. $formatted")
                if (singleLines.size > 1) {
                    for (extra in singleLines.drop(1).take(4)) {
                        val trimmedExtra = extra.trim()
                        if (trimmedExtra.isNotEmpty()) {
                            appendLine("   ↳ $trimmedExtra")
                        }
                    }
                }
                index += 1
            }
        }.trimEnd()
    }

    private fun humanizeRawLine(line: String): String {
        // Format dasar: "yyyy-MM-dd HH:mm:ss.SSS  <pesan>"
        val timePart = if (line.length >= 23 && line[4] == '-' && line[13] == ':') {
            line.substring(11, 19)
        } else {
            null
        }
        val body = if (timePart != null && line.length > 25) {
            line.substring(25).trim()
        } else {
            line.trim()
        }
        val cleanBody = body
            .replace("Detail FreeRDP:", "Detail XyRDP Engine:")
            .replace("FreeRDP", "XyRDP Engine")
            .replace("APP: diagnostics initialized;", "[Aplikasi] Diagnostik XyDesk aktif ·")
            .replace("APP: entering GlobalApp.onCreate", "[Aplikasi] Memulai mesin XyDesk Remote")
            .replace("APP: GlobalApp.onCreate completed", "[Aplikasi] Mesin XyDesk siap digunakan")
            .replace("SES: activity created", "[Sesi] Layar sesi remote dibuka")
            .replace("SES: manager+controller ok, sink set", "[Sesi] Pengendali layar & input siap")
            .replace("SES: profil ok ->", "[Sesi] Target host ->")
            .replace("SES: memanggil manager.connect (native createSession + parse args)", "[Koneksi] Memulai inisialisasi koneksi ke host")
            .replace("SES: manager.connect kembali (worker jalan async)", "[Koneksi] Worker koneksi berjalan di latar")
            .replace("CM: native createSession mulai", "[Koneksi] Menyiapkan sesi ke")
            .replace("CM: createSession ok", "[Koneksi] Sesi siap ·")
            .replace("CM: worker: session.connect mulai (native parse args + spawn connect thread)", "[Koneksi] Menghubungi server & negosiasi protokol")
            .replace("CM: worker: session.connect kembali — connect thread jalan", "[Koneksi] Jabat tangan (handshake) sedang berlangsung")
            .replace("connect mulai", "[Koneksi] Memulai sambungan")
            .replace("koneksi SUKSES", "[Sukses] Terhubung ke komputer remote")
            .replace("koneksi GAGAL (event native)", "[Gagal] Sambungan ke server ditolak / gagal")
            .replace("disconnect (event native)", "[Terputus] Sesi remote berakhir")
            .replace("prompt NLA/credential", "[Keamanan] Server meminta autentikasi kredensial (NLA)")
            .let { s ->
                when {
                    s.startsWith("[") -> s
                    s.startsWith("ENGINE ERROR:") -> "[Engine Error] " + s.removePrefix("ENGINE ERROR:").trim()
                    s.startsWith("ENGINE WARN:") -> "[Engine Peringatan] " + s.removePrefix("ENGINE WARN:").trim()
                    s.startsWith("ENGINE:") -> "[Engine XyDesk] " + s.removePrefix("ENGINE:").trim()
                    s.startsWith("CM:") -> "[Koneksi] " + s.removePrefix("CM:").trim()
                    s.startsWith("SES:") -> "[Sesi] " + s.removePrefix("SES:").trim()
                    s.startsWith("AUDIO:") -> "[Audio] " + s.removePrefix("AUDIO:").trim()
                    s.startsWith("JNI:") -> "[Engine XyDesk] " + s.removePrefix("JNI:").trim()
                    s.startsWith("WATCHDOG:") -> "[Peringatan] " + s.removePrefix("WATCHDOG:").trim()
                    s.startsWith("BUILD:") -> "[Info Engine] " + s.removePrefix("BUILD:").trim()
                    s.startsWith("stage ->") -> "[Tahap] Status -> " + s.removePrefix("stage ->").trim()
                    s.startsWith("state:") -> "[Status Sesi] " + s.removePrefix("state:").trim()
                    else -> "[XyDesk] $s"
                }
            }

        return if (timePart != null) "[$timePart] $cleanBody" else cleanBody
    }

    @Synchronized
    fun clear(context: Context? = null) {
        buf.clear()
        val candidates = files.ifEmpty {
            context?.let { listOf(File(it.applicationContext.filesDir, FILE_NAME)) }.orEmpty()
        }
        candidates.forEach { runCatching { it.delete() } }
        nativeLogPath?.let { runCatching { File(it).writeText("") } }
        lastSyncedNativeLines = 0
    }
}
