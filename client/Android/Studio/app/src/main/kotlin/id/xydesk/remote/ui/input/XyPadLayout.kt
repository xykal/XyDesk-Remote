package id.xydesk.remote.ui.input

/**
 * Tata letak gamepad virtual: posisi tiap klaster disimpan sebagai pecahan
 * ukuran layar (0..1) supaya tata letak tetap masuk akal saat orientasi atau
 * ukuran layar berubah.
 *
 * Murni Kotlin (tanpa Android) agar matematika penjepitan, tumpang tindih, dan
 * codec-nya bisa diuji di JVM.
 */

/** Posisi kiri-atas sebuah klaster dalam pecahan lebar/tinggi layar. */
data class XyPadPos(val x: Float, val y: Float)

/** Klaster yang bisa dipindah sendiri-sendiri oleh pengguna. */
enum class XyPadCluster {
    LEFT_STICK,
    DPAD,
    ABXY,
    RIGHT_STICK,
    TOP_BAR,
}

object XyPadLayout {
    const val MIN_SCALE = 0.7f
    const val MAX_SCALE = 1.4f

    /**
     * Posisi bawaan. Sengaja menjauhi overlay lain di layar sesi:
     * pemutar Spotify mengambang di tengah-atas, HUD rail default di kanan
     * (x≈0.78, y≈0.42), dan pill scroll di tengah-kanan.
     */
    fun defaults(): Map<XyPadCluster, XyPadPos> =
        mapOf(
            XyPadCluster.TOP_BAR to XyPadPos(0.02f, 0.10f),
            XyPadCluster.DPAD to XyPadPos(0.02f, 0.38f),
            XyPadCluster.LEFT_STICK to XyPadPos(0.03f, 0.72f),
            XyPadCluster.ABXY to XyPadPos(0.80f, 0.62f),
            XyPadCluster.RIGHT_STICK to XyPadPos(0.79f, 0.30f),
        )

    /** Gabungkan tata letak tersimpan dengan bawaan; kunci asing diabaikan. */
    fun resolve(stored: Map<XyPadCluster, XyPadPos>?): Map<XyPadCluster, XyPadPos> {
        val base = defaults().toMutableMap()
        stored?.forEach { (cluster, pos) -> base[cluster] = pos }
        return base
    }

    /**
     * Geser posisi sebesar delta piksel lalu jepit supaya seluruh klaster tetap
     * terlihat di layar. [clusterW]/[clusterH] adalah ukuran klaster dalam piksel.
     */
    fun drag(
        pos: XyPadPos,
        dxPx: Float,
        dyPx: Float,
        screenW: Int,
        screenH: Int,
        clusterW: Float,
        clusterH: Float,
    ): XyPadPos {
        val current = topLeftPx(pos, screenW, screenH, clusterW, clusterH)
        return fromTopLeftPx(
            current.first + dxPx,
            current.second + dyPx,
            screenW,
            screenH,
            clusterW,
            clusterH,
        )
    }

    /** Posisi pecahan -> offset piksel kiri-atas, sudah dijepit ke layar. */
    fun topLeftPx(
        pos: XyPadPos,
        screenW: Int,
        screenH: Int,
        clusterW: Float,
        clusterH: Float,
    ): Pair<Float, Float> {
        val maxX = (screenW - clusterW).coerceAtLeast(0f)
        val maxY = (screenH - clusterH).coerceAtLeast(0f)
        val x = (pos.x * screenW).coerceIn(0f, maxX)
        val y = (pos.y * screenH).coerceIn(0f, maxY)
        return x to y
    }

    /** Offset piksel kiri-atas -> posisi pecahan, dijepit ke 0..(1 - rasio klaster). */
    fun fromTopLeftPx(
        leftPx: Float,
        topPx: Float,
        screenW: Int,
        screenH: Int,
        clusterW: Float,
        clusterH: Float,
    ): XyPadPos {
        val w = screenW.coerceAtLeast(1)
        val h = screenH.coerceAtLeast(1)
        val maxX = (w - clusterW).coerceAtLeast(0f)
        val maxY = (h - clusterH).coerceAtLeast(0f)
        return XyPadPos(
            x = leftPx.coerceIn(0f, maxX) / w,
            y = topPx.coerceIn(0f, maxY) / h,
        )
    }

    /** Dua kotak tumpang tindih? Dipakai untuk memperingatkan tata letak yang bertabrakan. */
    fun overlaps(
        ax: Float, ay: Float, aw: Float, ah: Float,
        bx: Float, by: Float, bw: Float, bh: Float,
    ): Boolean = ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah

    /** Skala dibatasi agar pad tidak hilang atau memenuhi layar. */
    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** `TOP_BAR:0.02,0.10|DPAD:0.02,0.38` — format ringkas, tanpa dependensi JSON. */
    fun encode(layout: Map<XyPadCluster, XyPadPos>): String =
        layout.entries.joinToString("|") { "${it.key.name}:${fmt(it.value.x)},${fmt(it.value.y)}" }

    fun decode(raw: String?): Map<XyPadCluster, XyPadPos>? {
        if (raw.isNullOrBlank()) return null
        val out = mutableMapOf<XyPadCluster, XyPadPos>()
        for (part in raw.split('|')) {
            val kv = part.split(':')
            if (kv.size != 2) continue
            val cluster = XyPadCluster.entries.firstOrNull { it.name == kv[0] } ?: continue
            val xy = kv[1].split(',')
            if (xy.size != 2) continue
            val x = xy[0].toFloatOrNull() ?: continue
            val y = xy[1].toFloatOrNull() ?: continue
            out[cluster] = XyPadPos(x, y)
        }
        return out.ifEmpty { null }
    }

    private fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.4f", value)
}
