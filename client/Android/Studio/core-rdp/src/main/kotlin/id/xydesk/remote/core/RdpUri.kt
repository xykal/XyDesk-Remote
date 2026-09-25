package id.xydesk.remote.core

import android.net.Uri

/**
 * Builder URI `rdp://` — format yang dipahami inti FreeRDP
 * (lihat `LibFreeRDP.setConnectionInfo(Context, long, Uri)`).
 *
 * Contoh: `rdp://user@10.0.0.5:3390/?p=pass&domain=CORP`
 */
object RdpUri {

    fun build(profile: ConnectionProfile): Uri =
        build(profile.host, profile.port, profile.username, profile.password, profile.domain)

    fun build(
        host: String,
        port: Int,
        user: String?,
        pass: String?,
        domain: String?,
    ): Uri {
        var authority = host
        if (port != 3389) authority = "$host:$port"
        if (!user.isNullOrBlank()) authority = "$user@$authority"

        val b = Uri.Builder().scheme("rdp").encodedAuthority(authority)
        if (!pass.isNullOrBlank()) b.appendQueryParameter("p", pass)
        if (!domain.isNullOrBlank()) b.appendQueryParameter("domain", domain)
        return b.build()
    }
}
