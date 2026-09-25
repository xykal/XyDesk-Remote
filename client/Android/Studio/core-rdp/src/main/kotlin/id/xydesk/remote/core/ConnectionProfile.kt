package id.xydesk.remote.core

/**
 * Profil koneksi RDP — data class bersama (M1). Dipakai oleh form koneksi,
 * favorites (features-sessions), dan [SessionManager].
 *
 * @param host hostname/IP tailnet (mis. `abc-1234.ts.net`)
 * @param port default 3389
 * @param username null = minta interaktif (NLA prompt)
 * @param password null = minta interaktif / tidak disimpan
 * @param domain opsional (Active Directory)
 * @param label nama tampilan (favorites)
 */
data class ConnectionProfile(
    val host: String,
    val port: Int = 3389,
    val username: String? = null,
    val password: String? = null,
    val domain: String? = null,
    val label: String? = null,
) {
    /** Identifier stabil untuk favorites/vault: `host:port`. */
    val id: String
        get() = "$host:$port"

    require(host.isNotBlank()) { "host wajib diisi" }
    require(port in 1..65535) { "port invalid: $port" }
}
