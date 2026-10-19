package id.xydesk.remote.core

/**
 * Apply current Android permissions at EVERY native connection/reconnect boundary.
 * This is session-only: a denied prompt must not erase the user's saved preference.
 * Legacy PC profiles must not open independent RDP camera/mic capture channels.
 */
fun RdpOptions.withCapturePermissions(microphoneGranted: Boolean, cameraGranted: Boolean): RdpOptions = copy(
    microphone = microphone && microphoneGranted && !pcConnectMode,
    camera = camera && cameraGranted && !pcConnectMode,
)
