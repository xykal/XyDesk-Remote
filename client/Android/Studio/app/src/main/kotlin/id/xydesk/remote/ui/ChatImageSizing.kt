package id.xydesk.remote.ui

/** Power-of-two downsample, reject invalid/unreasonably large decoded dimensions. */
internal fun chatImageSampleSize(width: Int, height: Int, maxEdge: Int): Int? {
    require(maxEdge > 0)
    if (width !in 1..16_384 || height !in 1..16_384) return null
    var sample = 1
    while ((maxOf(width, height) + sample - 1) / sample > maxEdge) sample *= 2
    return sample
}
