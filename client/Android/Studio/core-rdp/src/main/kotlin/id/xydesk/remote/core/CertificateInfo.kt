package id.xydesk.remote.core

/**
 * Info sertifikat yang diminta inti FreeRDP saat handshake TLS
 * (`OnVerifiyCertificateEx` — typo-nya memang dari upstream).
 *
 * UI (M1.2) menampilkan dialog trust dengan [fingerprint] SHA-256;
 * policy default XyDesk = verifikasi penuh (PLAN §5.2).
 */
data class CertificateInfo(
    val host: String,
    val port: Int,
    val commonName: String,
    val subject: String,
    val issuer: String,
    val fingerprint: String,
    val flags: Long,
) {
    val isChanged: Boolean get() = (flags and FLAG_CHANGED) != 0L
    val isMismatch: Boolean get() = (flags and FLAG_MISMATCH) != 0L
    val isGateway: Boolean get() = (flags and FLAG_GATEWAY) != 0L
    val isRedirect: Boolean get() = (flags and FLAG_REDIRECT) != 0L

    companion object {
        // Hasil reply ke SessionManager:
        const val VERIFY_ACCEPT = 0
        const val VERIFY_DENY = 1

        // Flags dari inti (subset yang relevan untuk UI):
        const val FLAG_LEGACY = 0x02L
        const val FLAG_REDIRECT = 0x10L
        const val FLAG_GATEWAY = 0x20L
        const val FLAG_CHANGED = 0x40L
        const val FLAG_MISMATCH = 0x80L
    }
}
