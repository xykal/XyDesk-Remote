package id.xydesk.remote.core

/** A pin is not permission to suppress new connection-context warnings. */
fun canAutoAcceptPinnedCertificate(info: CertificateInfo, storedFingerprint: String?): Boolean {
    if (info.host.isBlank() || info.port !in 1..65535 || info.fingerprint.isBlank()) return false
    if (storedFingerprint.isNullOrBlank() || storedFingerprint != info.fingerprint) return false
    // Unknown flags also require a prompt: fail closed without blocking explicit user review.
    return info.flags == 0L
}
