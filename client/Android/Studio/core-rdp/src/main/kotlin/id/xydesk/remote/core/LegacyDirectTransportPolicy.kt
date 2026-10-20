package id.xydesk.remote.core

/** Direct UDP :4433 transport policy for PC Stream, 120Hz RawInput, and QuicAudioBridge. */
internal object LegacyDirectTransportPolicy {
    @Volatile var enabled: Boolean = true
    fun mayStart(): Boolean = enabled
    const val BLOCKED_REASON = "Direct UDP dinonaktifkan oleh kebijakan sesi."
}
