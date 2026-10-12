package id.xydesk.remote.ui

/** RDP wheel units queued from one vertical touch delta and the carried remainder. */
internal data class ScrollWheelStep(val units: Int, val remainder: Float)

internal class ScrollWheelAccumulator {
    private var remainder = 0f

    fun consume(deltaY: Float, speed: Float): Int {
        val step = scrollWheelStep(deltaY, remainder, speed)
        remainder = step.remainder
        return step.units
    }

    fun reset() {
        remainder = 0f
    }
}

/**
 * Convert touch pixels to high-resolution RDP wheel units. One notch remains
 * 120 units at the default speed.
 *
 * Batch diturunkan dari 24 ke **12 unit** (1/10 takik, dari 1/5) supaya scroll
 * terasa halus, bukan melompat per seperlima takik. Windows mengakumulasi delta
 * roda di bawah 120, jadi batch kecil tetap menghasilkan satu takik penuh setelah
 * lima langkah; yang berubah hanya seberapa sering delta dikirim.
 *
 * Positive finger movement is down, so it produces negative wheel units.
 */
internal fun scrollWheelStep(
    deltaY: Float,
    remainder: Float,
    speed: Float,
): ScrollWheelStep {
    val safeRemainder = if (remainder.isFinite()) remainder.coerceIn(-11.99f, 11.99f) else 0f
    val safeDelta = if (deltaY.isFinite()) deltaY.coerceIn(-240f, 240f) else 0f
    val safeSpeed = if (speed.isFinite()) speed.coerceIn(0.4f, 2.5f) else 1f
    val accumulated = safeRemainder - safeDelta * 3f * safeSpeed
    val units = (accumulated / 12f).toInt() * 12
    return ScrollWheelStep(units, accumulated - units)
}

/** Keep a button's center fixed when its diameter changes. Coordinates are top-left normalized. */
internal fun resizeHudKeyPreservingCenter(
    key: HudKey,
    newSize: Float,
    viewportWidthDp: Float,
    viewportHeightDp: Float,
): HudKey {
    val oldSize = if (key.size.isFinite()) key.size.coerceIn(56f, 120f) else 64f
    val size = if (newSize.isFinite()) newSize.coerceIn(56f, 120f) else oldSize
    fun adjust(position: Float, viewport: Float): Float {
        val safePosition = if (position.isFinite()) position.coerceIn(0f, 1f) else 0f
        if (!viewport.isFinite() || viewport <= 0f) return safePosition
        val oldMax = (viewport - oldSize).coerceAtLeast(1f)
        val newMax = (viewport - size).coerceAtLeast(1f)
        val center = safePosition * oldMax + oldSize / 2f
        return ((center - size / 2f) / newMax).coerceIn(0f, 1f)
    }
    return key.copy(
        size = size,
        x = adjust(key.x, viewportWidthDp),
        y = adjust(key.y, viewportHeightDp),
    )
}

/**
 * Only layout editing (`mappingMode`) and the swipe-scroll button (`SCROLL_SLIDER`)
 * convert finger motion into a drag/scroll gesture after crossing touch slop.
 * Normal HUD buttons must not cancel taps on minor finger wobble.
 */
internal fun shouldStartHudButtonGesture(
    travelledPx: Float,
    touchSlopPx: Float,
    mappingMode: Boolean,
    kind: HudKind,
): Boolean {
    // Di luar mode atur posisi, hanya kontrol geser kontinu yang boleh memulai
    // gestur tarik: slider scroll dan joystick analog.
    if (!mappingMode && kind != HudKind.SCROLL_SLIDER && kind != HudKind.PAD_STICK) return false
    if (!travelledPx.isFinite() || travelledPx <= 0f) return false
    val threshold = if (touchSlopPx.isFinite() && touchSlopPx > 0f) touchSlopPx else 16f
    return travelledPx > threshold
}

/**
 * Hitung delta gerakan kursor trackpad dengan sensitivitas & akselerasi kecepatan.
 * Saat [acceleration] menyala, gerakan jari pelan tetap presisi piksel sementara
 * sapuan cepat mendapat pengali hingga ~1.88x.
 */
internal fun applyTrackpadDelta(
    dx: Float,
    dy: Float,
    sensitivity: Float,
    acceleration: Boolean,
): Pair<Float, Float> {
    val safeDx = if (dx.isFinite()) dx.coerceIn(-400f, 400f) else 0f
    val safeDy = if (dy.isFinite()) dy.coerceIn(-400f, 400f) else 0f
    val sens = if (sensitivity.isFinite()) sensitivity.coerceIn(0.4f, 2.5f) else 1f
    val accelFactor = if (acceleration) {
        val speed = kotlin.math.hypot(safeDx, safeDy)
        1f + (speed / 18f).coerceIn(0f, 1.6f) * 0.55f
    } else {
        1f
    }
    val scale = sens * accelFactor
    return (safeDx * scale) to (safeDy * scale)
}

/**
 * Haluskan delta gerakan kursor antar-frame dengan exponential moving average.
 * Sapuan jari mengirim banyak delta kecil yang kasar; tanpa perataan kursor
 * terasa "muter-muter" di dalam game. [alpha] tinggi = responsif (0 = kaku,
 * 1 = tanpa perataan). Delta pertama (prevDx/prevDy NaN) dilewatkan apa
 * adanya supaya tidak ada jeda di awal gestur.
 */
internal fun smoothPointerDelta(
    prevDx: Float,
    prevDy: Float,
    dx: Float,
    dy: Float,
    alpha: Float,
): Pair<Float, Float> {
    if (!dx.isFinite() || !dy.isFinite()) return 0f to 0f
    if (!prevDx.isFinite() || !prevDy.isFinite()) return dx to dy
    val a = if (alpha.isFinite()) alpha.coerceIn(0.15f, 1f) else 0.45f
    return (prevDx + (dx - prevDx) * a) to (prevDy + (dy - prevDy) * a)
}

/**
 * Deteksi apakah titik sentuh awal berada di jalur scroll tepi kanan layar
 * (Edge-Scroll ala touchpad laptop).
 */
internal fun isInRightEdgeScrollZone(
    touchX: Float,
    viewportWidthPx: Float,
    zoneWidthPx: Float,
    enabled: Boolean,
): Boolean {
    if (!enabled) return false
    if (!touchX.isFinite() || !viewportWidthPx.isFinite() || viewportWidthPx <= 0f) return false
    val safeZone = if (zoneWidthPx.isFinite()) zoneWidthPx.coerceIn(12f, 120f) else 32f
    return touchX >= (viewportWidthPx - safeZone)
}

/** Langkah peluruhan kecepatan kinetic/inertial scroll per frame. */
internal fun inertialScrollDecayStep(
    velocityY: Float,
    friction: Float = 0.88f,
): Float {
    if (!velocityY.isFinite()) return 0f
    val safeFriction = if (friction.isFinite()) friction.coerceIn(0.5f, 0.96f) else 0.88f
    val next = velocityY * safeFriction
    return if (kotlin.math.abs(next) < 0.8f) 0f else next
}

/**
 * Jepit simpangan joystick ke dalam lingkaran berjari-jari [radiusPx] supaya
 * knob tidak pernah keluar dari alasnya. Mengembalikan [x, y] dalam piksel.
 */
internal fun clampStickOffset(dx: Float, dy: Float, radiusPx: Float): FloatArray {
    val radius = if (radiusPx.isFinite() && radiusPx > 0f) radiusPx else 1f
    val safeX = if (dx.isFinite()) dx else 0f
    val safeY = if (dy.isFinite()) dy else 0f
    val magnitude = kotlin.math.sqrt(safeX * safeX + safeY * safeY)
    if (magnitude <= radius || magnitude == 0f) return floatArrayOf(safeX, safeY)
    val scale = radius / magnitude
    return floatArrayOf(safeX * scale, safeY * scale)
}

/**
 * Normalisasi simpangan joystick ke -1..1 per sumbu. Arah dipertahankan
 * (tidak menempel ke sumbu) sehingga gerak diagonal tetap diagonal.
 */
internal fun stickAxis(dx: Float, dy: Float, radiusPx: Float): FloatArray {
    val radius = if (radiusPx.isFinite() && radiusPx > 0f) radiusPx else 1f
    val clamped = clampStickOffset(dx, dy, radiusPx)
    return floatArrayOf(
        (clamped[0] / radius).coerceIn(-1f, 1f),
        (clamped[1] / radius).coerceIn(-1f, 1f),
    )
}

/**
 * Ukuran pelat (dp) sesuai bentuk tombol: bumper L1/R1 lebar-pendek, trigger
 * L2/R2 tinggi-sempit, lainnya lingkaran berdiameter [HudKey.size]. Perender
 * dan perhitungan snap tata letak memakai helper ini supaya geometrinya sama.
 */
internal fun hudPlateSizeDp(key: HudKey): Pair<Float, Float> {
    val isBumper = key.label.equals("L1", ignoreCase = true) || key.label.equals("R1", ignoreCase = true)
    val isTrigger = key.label.equals("L2", ignoreCase = true) || key.label.equals("R2", ignoreCase = true)
    return when {
        isBumper -> key.size * 1.55f to key.size * 0.62f
        isTrigger -> key.size * 0.92f to key.size * 1.3f
        else -> key.size to key.size
    }
}

/** Langkah grid (dp) saat posisi tombol ditempelkan di mode atur tata letak. */
internal const val HUD_SNAP_GRID_DP = 12f

/** Jarak maksimum (dp) tepi tombol menempel ke tepi/tengah tombol lain. */
internal const val HUD_SNAP_MAGNET_DP = 10f

/** Satu garis bantu sejajar (ungu, putus-putus) di mode atur letak, dalam px. */
internal data class HudGuideLine(val vertical: Boolean, val positionPx: Float)

private fun hudEdgeCandidates(startPx: Float, sizePx: Float): FloatArray =
    floatArrayOf(startPx, startPx + sizePx / 2f, startPx + sizePx)

private fun hudWithin(value: Float, target: Float, limit: Float): Boolean {
    val d = target - value
    return (if (d < 0f) -d else d) <= limit
}

/**
 * Tempel posisi kiri-atas tombol (px) yang sedang digeser: menempel ke
 * tepi/tengah tombol lain bila cukup dekat (magnet), selain itu dibulatkan ke
 * garis grid terdekat. Hasil dijepit ke rentang [maxX]/[maxY]. [others] berisi
 * geometri tombol lain dalam bentuk [x, y, w, h] piksel.
 */
internal fun snapHudPosition(
    xPx: Float,
    yPx: Float,
    wPx: Float,
    hPx: Float,
    maxX: Float,
    maxY: Float,
    others: List<FloatArray>,
    gridPx: Float,
    magnetPx: Float,
): FloatArray {
    var bestX: Float? = null
    var bestXDist = magnetPx
    var bestY: Float? = null
    var bestYDist = magnetPx
    for (o in others) {
        if (o.size < 4) continue
        for (t in hudEdgeCandidates(xPx, wPx)) {
            for (line in hudEdgeCandidates(o[0], o[2])) {
                val shift = line - t
                val dist = if (shift < 0f) -shift else shift
                if (dist < bestXDist) {
                    bestXDist = dist
                    bestX = xPx + shift
                }
            }
        }
        for (t in hudEdgeCandidates(yPx, hPx)) {
            for (line in hudEdgeCandidates(o[1], o[3])) {
                val shift = line - t
                val dist = if (shift < 0f) -shift else shift
                if (dist < bestYDist) {
                    bestYDist = dist
                    bestY = yPx + shift
                }
            }
        }
    }
    val grid = if (gridPx.isFinite() && gridPx > 0f) gridPx else 1f
    val nx = bestX ?: (kotlin.math.round(xPx / grid) * grid)
    val ny = bestY ?: (kotlin.math.round(yPx / grid) * grid)
    return floatArrayOf(nx.coerceIn(0f, maxX), ny.coerceIn(0f, maxY))
}

/**
 * Garis bantu sejajar untuk tombol yang sedang digeser, memakai kriteria yang
 * sama dengan [snapHudPosition]: satu garis untuk setiap tepi/tengah tombol
 * lain yang sedang disentuh tombol yang digeser.
 */
internal fun hudAlignmentGuides(
    xPx: Float,
    yPx: Float,
    wPx: Float,
    hPx: Float,
    others: List<FloatArray>,
    magnetPx: Float,
): List<HudGuideLine> {
    val guides = mutableListOf<HudGuideLine>()
    fun add(vertical: Boolean, positionPx: Float) {
        if (guides.none { it.vertical == vertical && kotlin.math.abs(it.positionPx - positionPx) < 1f }) {
            guides.add(HudGuideLine(vertical, positionPx))
        }
    }
    val tx = hudEdgeCandidates(xPx, wPx)
    val ty = hudEdgeCandidates(yPx, hPx)
    for (o in others) {
        if (o.size < 4) continue
        for (line in hudEdgeCandidates(o[0], o[2])) {
            if (tx.any { hudWithin(it, line, magnetPx) }) add(vertical = true, positionPx = line)
        }
        for (line in hudEdgeCandidates(o[1], o[3])) {
            if (ty.any { hudWithin(it, line, magnetPx) }) add(vertical = false, positionPx = line)
        }
    }
    return guides
}
