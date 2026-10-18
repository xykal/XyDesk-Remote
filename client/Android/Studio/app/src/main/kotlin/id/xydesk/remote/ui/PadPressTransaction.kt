package id.xydesk.remote.ui

/**
 * A started press always attempts release, even if disabled while held or cancelled.
 * This is callback cleanup, not acknowledgement that the host received a neutral frame.
 */
internal suspend fun dispatchPadPress(send: (Boolean) -> Unit, awaitRelease: suspend () -> Unit) {
    try {
        send(true)
        awaitRelease()
    } finally {
        send(false)
    }
}
