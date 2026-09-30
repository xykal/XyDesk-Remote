package id.xydesk.remote.core

import android.content.Context

/**
 * Sumber ukuran resolusi 16:9.
 *
 * Aturan produk (ronde 8): desktop remote itu desktop WINDOWS — rasionya
 * 16:9, bukan rasio layar HP. "Otomatis" berarti "16:9 terbesar yang muat",
 * bukan "ikuti dimensi layar HP" (yang menghasilkan desktop 20:9, taskbar
 * mini, dan teks yang tidak bisa dibaca). Kalau user memang mau rasio layar
 * HP (video fullscreen, game), ada opsi eksplisit "Ikuti layar HP".
 */
object SmartResolution {

    /** Resolusi 16:9 standar yang ditawarkan, kecil ke besar. */
    val CANDIDATES: List<Pair<Int, Int>> = listOf(
        1280 to 720,
        1600 to 900,
        1920 to 1080,
        2560 to 1440,
        3840 to 2160,
    )

    /** Batas yang sama dengan validasi ukuran manual (SessionManager/DisplayPrefs). */
    const val MIN_W = 640
    const val MIN_H = 480
    const val MAX_W = 8192
    const val MAX_H = 8192

    /**
     * 16:9 standar terbesar yang sisi pendeknya masih muat di layar HP.
     * Dipakai saat connect (/size) supaya desktop rasionya PC tapi tanpa
     * downscale berlebihan.
     */
    fun forScreen(context: Context): String {
        val dm = context.resources.displayMetrics
        val minSide = minOf(dm.widthPixels, dm.heightPixels)
        val pick = CANDIDATES.lastOrNull { it.second <= minSide } ?: CANDIDATES.first()
        return "${pick.first}x${pick.second}"
    }

    /**
     * Resolusi 16:9 standar yang sesuai dengan kapasitas layar perangkat.
     * Dipakai saat mode Otomatis agar desktop Windows tetap memakai resolusi
     * 16:9 standar PC yang tajam (minimal 1280x720, atau 1920x1080 di layar
     * FHD) tanpa menyusut menjadi 1080x606 saat portrait atau berubah menjadi
     * ukuran ganjil saat keyboard/inset muncul.
     */
    fun forViewport(viewportW: Int, viewportH: Int): String {
        val vw = viewportW.coerceAtLeast(1)
        val vh = viewportH.coerceAtLeast(1)
        val maxSide = maxOf(vw, vh)
        val minSide = minOf(vw, vh)
        val pick = CANDIDATES.lastOrNull { (cw, ch) ->
            cw <= maxSide && ch <= minSide
        } ?: CANDIDATES.first()
        return "${pick.first}x${pick.second}"
    }

    /** "1920x1080" -> (1920, 1080); null kalau tidak valid. */
    fun parse(preset: String): Pair<Int, Int>? {
        val parts = preset.split('x')
        if (parts.size != 2) return null
        val w = parts[0].toIntOrNull() ?: return null
        val h = parts[1].toIntOrNull() ?: return null
        if (w !in MIN_W..MAX_W || w % 2 != 0 || h !in MIN_H..MAX_H) return null
        return w to h
    }

    /** Label rasio paling sederhana dari ukuran (16:9, 9:20, dst). */
    fun ratioLabel(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return ""
        var a = width
        var b = height
        while (b != 0) {
            val t = a % b
            a = b
            b = t
        }
        val g = if (a == 0) 1 else a
        val rw = width / g
        val rh = height / g
        // Rasio hasil gcd sering jelekan (42:13); kalau mendekati 16:9,
        // tampilkan yang bersih.
        val is169 = kotlin.math.abs(width * 9f / height - 16f) < 0.06f
        return if (is169) "16:9" else "$rw:$rh"
    }
}
