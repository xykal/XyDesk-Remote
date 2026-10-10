package id.xydesk.remote.core

/** Standard RDP media, without requiring the optional XyDesk UDP host agent. */
fun RdpOptions.forRdpFree(microphone: Boolean): RdpOptions = copy(
    pcConnectMode = false,
    audioMode = XyAudioMode.DEVICE,
    microphone = microphone,
    desktopWallpaper = true,
    quicAudio = false,
)

/** A disabled bridge must never open a second microphone capture device. */
fun RdpOptions.usesQuicMicrophone(): Boolean = quicAudio && microphone
