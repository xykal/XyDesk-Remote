package id.xydesk.remote.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Log koneksi in-memory (ring buffer) untuk diagnosa "koneksi
 * menggantung" — ditampilkan di dialog error (tombol Detail).
 * Thread-safe (dipanggil dari thread RDP + main).
 */
object ConnectionLog {
    private const val MAX_ENTRIES = 200
    private val buf = CopyOnWriteArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun add(msg: String) {
        buf.add("${fmt.format(Date())}  $msg")
        while (buf.size > MAX_ENTRIES) buf.removeAt(0)
    }

    /** Entri terakhir, paling baru di bawah. */
    fun last(n: Int = 60): List<String> =
        if (buf.size <= n) buf.toList() else buf.subList(buf.size - n, buf.size)

    fun clear() { buf.clear() }
}
