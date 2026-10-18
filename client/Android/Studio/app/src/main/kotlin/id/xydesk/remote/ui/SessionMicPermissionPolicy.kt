package id.xydesk.remote.ui

/** Permission results must not activate a different/background session. */
internal fun canApplySessionMicPermission(granted: Boolean, sameSession: Boolean, started: Boolean): Boolean =
    granted && sameSession && started
