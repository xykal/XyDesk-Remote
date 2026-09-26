package id.xydesk.remote.security

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnosa crash untuk pengguna: exception tak tertangkap ditulis ke
 * file (survives process death). Layar home menampilkan banner "terjadi
 * error" + tombol lihat log kalau file ada.
 * Dipasang di XyApp.onCreate.
 */
object CrashLog {
    private const val FILE_NAME = "xydesk-crash.log"

    fun install(context: Context) {
        val appCtx = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                write(appCtx, t, e)
            } catch (_: Throwable) {
            }
            // teruskan ke handler default (tombol "App terhenti" tetap muncul)
            default?.uncaughtException(t, e)
        }
    }

    private fun write(ctx: Context, t: Thread, e: Throwable) {
        val sw = java.io.StringWriter()
        e.printStackTrace(java.io.PrintWriter(sw))
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = "=== $ts  thread=${t.name} ===\n${sw.toString()}\n"
        val f = File(ctx.filesDir, FILE_NAME)
        val old = if (f.exists()) f.readText() else ""
        f.writeText((line + old).take(64 * 1024))
    }

    /** Catat insiden (tanpa proses mati) ke log yang sama. */
    fun note(context: Context, msg: String) {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val f = File(context.filesDir, FILE_NAME)
        val old = if (f.exists()) f.readText() else ""
        f.writeText("=== $ts  insiden: $msg ===\n$old".take(64 * 1024))
    }

    /** Isi log terakhir; null jika tidak ada crash tercatat. */
    fun last(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f.readText() else null
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE_NAME).delete()
    }
}
