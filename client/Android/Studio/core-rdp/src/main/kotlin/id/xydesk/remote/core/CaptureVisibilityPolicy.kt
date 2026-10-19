package id.xydesk.remote.core

/** Background keep-alive is not consent to open Android microphone/camera capture. */
fun RdpOptions.forCaptureVisibility(foreground: Boolean): RdpOptions =
    if (foreground) this else copy(microphone = false, camera = false)
