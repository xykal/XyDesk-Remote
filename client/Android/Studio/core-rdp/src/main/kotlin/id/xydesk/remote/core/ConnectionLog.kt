package id.xydesk.remote.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Log koneksi + boot sesi (ring buffer in-memory + FILE).
 *
 * Versi file penting untuk diagnosa CRASH NATIVE: kalau proses mati
 * karena SIGSEGV di native FreeRDP, baris terakhir file tetap ada
 * dan menunjukkan langkah mana yang sedang berjalan saat mati.
 * Thread-safe (dipanggil dari thread RDP + main).
 */
object ConnectionLog {
    private const val MAX_ENTRIES = 200
    private const val FILE_NAME = "xydesk-boot.log"
    private val buf = CopyOnWriteArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    @Volatile private var file: File? = null

    /**
     * Wajib dipanggil sekali (Application/Activity) agar log ke file.
     * Simpan di Android/media/<package>/log agar pemilik perangkat bisa
     * mengambil log lewat file manager tanpa storage permission legacy.
     */
    fun init(context: android.content.Context) {
        val app = context.applicationContext
        val mediaDir = app.externalMediaDirs.firstOrNull()?.let { File(it, "log") }
        val externalLog = mediaDir?.takeIf { (it.isDirectory || it.mkdirs()) && it.canWrite() }
            ?.let { File(it, FILE_NAME) }
        file = externalLog ?: File(app.filesDir, FILE_NAME)
    }

    @Synchronized
    fun add(msg: String) {
        val line = "${fmt.format(Date())}  $msg"
        buf.add(line)
        while (buf.size > MAX_ENTRIES) buf.removeAt(0)
        val f = file
        if (f != null) {
            try {
                val old = if (f.exists()) f.readText() else ""
                f.writeText((old + line + "\n").takeLast(64 * 1024))
            } catch (_: Throwable) {
            }
        }
    }

    /** Entri terakhir, paling baru di bawah. */
    fun last(n: Int = 60): List<String> =
        if (buf.size <= n) buf.toList() else buf.subList(buf.size - n, buf.size)

    /** Tail dari file (untuk diagnosa crash antar-launch). */
    fun tailFromFile(context: android.content.Context, n: Int = 12): List<String> {
        val f = file ?: File(context.applicationContext.filesDir, FILE_NAME)
        if (!f.exists()) return emptyList()
        return try {
            f.readText().trim().lineSequence().toList().takeLast(n)
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun clear(context: android.content.Context? = null) {
        buf.clear()
        val f = file ?: context?.let { File(it.applicationContext.filesDir, FILE_NAME) }
        try {
            f?.delete()
        } catch (_: Throwable) {
        }
    }
}
