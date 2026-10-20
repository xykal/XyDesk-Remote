package id.xydesk.remote.core

/** Raw unauthenticated UDP is not a secure Direct transport. No release override. */
internal object LegacyDirectTransportPolicy {
    fun mayStart(): Boolean = false
    const val BLOCKED_REASON = "Direct UDP lama dinonaktifkan; pairing/transport aman belum tersedia."
}
