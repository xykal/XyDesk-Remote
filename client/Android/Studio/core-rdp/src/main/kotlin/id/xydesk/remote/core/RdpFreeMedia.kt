package id.xydesk.remote.core

/** Standard RDP media, without requiring the optional XyDesk UDP host agent. */
fun RdpOptions.forRdpFree(microphone: Boolean): RdpOptions = copy(
    pcConnectMode = false,
    audioMode = XyAudioMode.DEVICE,
    microphone = microphone,
    desktopWallpaper = true,
    quicAudio = false,
    lowBandwidth = false,
    networkAutoDetect = false,
    h264 = true,
    colorDepth = 32,
)

/** A disabled bridge must never open a second microphone capture device. */
fun RdpOptions.usesQuicMicrophone(): Boolean = quicAudio && microphone

/** Reconnect/favorites must also keep the RdpFree wallpaper, not just the pairing screen. */
fun RdpOptions.forSessionProfile(profile: ConnectionProfile): RdpOptions =
    if (profile.key?.startsWith("rdpfree:") == true || profile.key?.startsWith("xyrdp:") == true || profile.label?.startsWith("XyRDP") == true || profile.label == "RdpFree")
        copy(
            desktopWallpaper = true,
            lowBandwidth = false,
            networkAutoDetect = false,
            h264 = true,
            colorDepth = 32,
        )
    else this
