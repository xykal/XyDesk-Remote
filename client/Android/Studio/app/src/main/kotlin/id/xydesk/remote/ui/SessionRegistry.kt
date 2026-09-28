package id.xydesk.remote.ui

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Daftar sesi yang sedang hidup di proses ini.
 *
 * Satu sesi = satu XyDeskSessionActivity (masing-masing punya SessionManager
 * sendiri), dan default app memang membiarkan sesi jalan di latar. Registry
 * ini yang bikin sesi-sesi itu kelihatan dan bisa dipindah-pindah dari home —
 * tanpa ini, sesi kedua cuma bisa dijangkau lewat daftar app terakhir.
 */
object XySessionRegistry {

    class Live(
        val id: String,
        val label: String,
        val address: String,
        val open: () -> Unit,
        val kill: () -> Unit,
    )

    private val items = CopyOnWriteArrayList<Live>()

    fun add(item: Live) {
        remove(item.id)
        items.add(item)
    }

    fun remove(id: String) {
        items.removeAll { it.id == id }
    }

    fun list(): List<Live> = items.toList()

    fun count(): Int = items.size
}
