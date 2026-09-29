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
 * 120 units at the default speed; 24-unit batches make the motion less jumpy.
 * Positive finger movement is down, so it produces negative wheel units.
 */
internal fun scrollWheelStep(
    deltaY: Float,
    remainder: Float,
    speed: Float,
): ScrollWheelStep {
    val safeRemainder = if (remainder.isFinite()) remainder.coerceIn(-23.99f, 23.99f) else 0f
    val safeDelta = if (deltaY.isFinite()) deltaY.coerceIn(-240f, 240f) else 0f
    val safeSpeed = if (speed.isFinite()) speed.coerceIn(0.4f, 2.5f) else 1f
    val accumulated = safeRemainder - safeDelta * 3f * safeSpeed
    val units = (accumulated / 24f).toInt() * 24
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
