package id.xydesk.remote.core

import android.net.Uri

/**
 * Builder URI `rdp://` — format yang dipahami inti FreeRDP
 * (`LibFreeRDP.setConnectionInfo(Context, long, Uri)`).
 *
 * Inti memetakan tiap query param jadi argumen CLI FreeRDP:
 *  - `key` tanpa nilai   -> `/key`
 *  - `key=+` / `key=-`   -> `+key` / `-key`
 *  - `key=value`         -> `/key:value`
 *
 * Contoh: `rdp://user@10.0.0.5:3390/?p=pass&domain=CORP&clipboard=%2b`
 */
object RdpUri {

    fun build(profile: ConnectionProfile, options: RdpOptions = RdpOptions()): Uri {
        var authority = profile.host
        if (profile.port != 3389) authority = "${profile.host}:${profile.port}"
        if (!profile.username.isNullOrBlank()) authority = "${profile.username}@$authority"

        val b = Uri.Builder().scheme("rdp").encodedAuthority(authority)
        if (!profile.password.isNullOrBlank()) b.appendQueryParameter("p", profile.password)
        if (!profile.domain.isNullOrBlank()) b.appendQueryParameter("domain", profile.domain)

        // Audio: /audio-mode:0|1|2, ditambah /sound saat diputar di perangkat.
        b.appendQueryParameter("audio-mode", options.audioMode.wire.toString())
        if (options.audioMode == XyAudioMode.DEVICE) b.appendQueryParameter("sound", "")
        if (options.microphone) b.appendQueryParameter("microphone", "")

        b.appendQueryParameter("clipboard", if (options.clipboard) "+" else "-")
        if (options.localDrive) b.appendQueryParameter("drive", "sdcard")
        if (options.camera) b.appendQueryParameter("dvc", "rdpecam")

        // Hanya dikirim saat aktif: "+multitransport" menyalakan RDP-UDP (FEC).
        if (options.udpTransport) b.appendQueryParameter("multitransport", "+")
        if (options.networkAutoDetect) b.appendQueryParameter("network", "auto")
        if (options.h264) b.appendQueryParameter("gfx", "AVC444")

        options.gateway?.let { g ->
            val value = buildString {
                append("g:").append(g.host)
                if (g.port != 443) append(':').append(g.port)
                g.username?.takeIf { it.isNotBlank() }?.let { append(",u:").append(it) }
                g.domain?.takeIf { it.isNotBlank() }?.let { append(",d:").append(it) }
                g.password?.takeIf { it.isNotBlank() }?.let { append(",p:").append(it) }
            }
            b.appendQueryParameter("gateway", value)
        }
        return b.build()
    }

    fun build(
        host: String,
        port: Int,
        user: String?,
        pass: String?,
        domain: String?,
    ): Uri = build(
        ConnectionProfile(host = host, port = port, username = user, password = pass, domain = domain)
    )
}
