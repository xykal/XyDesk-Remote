package id.xydesk.remote.ui

/** Queue acceptance is not a room acknowledgement. Never fabricate a sent receipt. */
internal fun deliveryTicksAfterAttempt(confirmed: Boolean, queued: Boolean): Int = when {
    confirmed -> 2
    queued -> 0
    else -> -1
}
