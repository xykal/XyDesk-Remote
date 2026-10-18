package id.xydesk.remote.ui

/** Local input gate only, not transport acknowledgement or host-side authorization. */
internal fun virtualPadInputEnabled(
    editMode: Boolean,
    resumed: Boolean,
    windowFocused: Boolean,
    transportReady: Boolean,
): Boolean = !editMode && resumed && windowFocused && transportReady
