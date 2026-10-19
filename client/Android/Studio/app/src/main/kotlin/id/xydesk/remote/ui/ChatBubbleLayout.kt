package id.xydesk.remote.ui

/** Pure geometry in dp: leave space for one avatar, row padding and its gap.
 * The parent constraint, not the device screen size, controls overlays/split screen.
 */
internal fun chatBubbleMaxWidth(availableDp: Float, compact: Boolean): Float {
    val cap = if (compact) 204f else 360f
    val reserved = if (compact) 32f else 44f
    return (availableDp - reserved).coerceAtLeast(0f).coerceAtMost(cap)
}
