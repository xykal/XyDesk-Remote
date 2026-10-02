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
     * Ukuran 16:9 yang PAS dengan layar HP pada orientasi saat ini.
     * Dipakai saat connect (/size) supaya piksel remote 1:1 dengan layar —
     * di situlah teks paling tajam (tanpa downscale/upscale oleh HP).
     */
    fun forScreen(context: Context): String {
        val dm = context.resources.displayMetrics
        return forViewport(dm.widthPixels, dm.heightPixels)
    }

    /**
     * Resolusi 16:9 yang PAS dengan viewport (1:1 piksel), bukan salah satu
     * ukuran standar.
     *
     * Kenapa: kalau ukuran remote tidak sama dengan layar HP, desktop direntangkan
     * atau diperkecil oleh HP (resampling) sehingga huruf tipis Windows ikut
     * dilunakkan — inilah "teks pecah/bergerigi" yang sulit dibetulkan dari dalam
     * sesi. Dengan 1:1, ClearType yang digambar Windows tampil apa adanya.
     *
     * Portrait 1080x2400 -> 1080x606, landscape 2400x1080 -> 1920x1080.
     * Selalu genap (syarat server) dan di dalam batas MIN/MAX.
     */
    fun forViewport(viewportW: Int, viewportH: Int): String {
        val vw = viewportW.coerceAtLeast(1)
        val vh = viewportH.coerceAtLeast(1)
        val scale = minOf(vw / 16f, vh / 9f)
        var w = (scale * 16f).toInt() / 2 * 2
        var h = (scale * 9f).toInt() / 2 * 2
        if (w < MIN_W || h < MIN_H) {
            // Layar terlalu kecil untuk 16:9 penuh di batas server: pakai ukuran
            // 16:9 terkecil yang masih boleh, biarkan fit-to-screen yang bekerja.
            return "${CANDIDATES.first().first}x${CANDIDATES.first().second}"
        }
        w = w.coerceIn(MIN_W, MAX_W)
        h = h.coerceIn(MIN_H, MAX_H)
        return "${w}x$h"
    }

    /**
     * Skala yang dipakai HP saat menggambar desktop remote ke layar
     * (fit = muat, ambil sisi paling sempit). 1.0 = 1:1 piksel.
     *
     * Kenapa penting: ClearType (font smoothing RDP) menggambar di level
     * subpiksel; begitu desktop di-resample dengan skala != 1.0, tepi huruf
     * jadi bercak warna/bergerigi. Di kondisi itu font smoothing lebih baik
     * dimatikan supaya Windows memakai antialias abu-abu yang lebih tahan
     * diperkecil.
     */
    fun fitFactor(viewportW: Int, viewportH: Int, resW: Int, resH: Int): Float {
        if (viewportW <= 0 || viewportH <= 0 || resW <= 0 || resH <= 0) return 1f
        return minOf(viewportW.toFloat() / resW, viewportH.toFloat() / resH)
    }

    /** True kalau desktop remote digambar 1:1 (tanpa resample) di layar ini. */
    fun isPixelPerfect(viewportW: Int, viewportH: Int, resW: Int, resH: Int): Boolean {
        val f = fitFactor(viewportW, viewportH, resW, resH)
        return kotlin.math.abs(f - 1f) <= 0.02f
    }

    /** Versi string untuk pemanggil yang menyimpan resolusi sebagai "WxH". */
    fun isPixelPerfectForScreen(context: Context, resolution: String?): Boolean {
        val parsed = resolution?.let { parse(it) } ?: return true
        val dm = context.resources.displayMetrics
        return isPixelPerfect(dm.widthPixels, dm.heightPixels, parsed.first, parsed.second)
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
