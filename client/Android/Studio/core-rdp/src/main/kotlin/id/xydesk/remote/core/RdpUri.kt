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
 *
 * Bagian pemetaan ([authority], [gatewayArg], [queryParams]) sengaja murni
 * (tanpa `android.net.Uri`) supaya bisa dites unit di JVM — lihat
 * `src/test/kotlin/.../RdpUriTest.kt`. [build] hanya merangkainya jadi Uri.
 */
object RdpUri {

    /** `[user@]host[:port]`; port default 3389 tidak ditulis. */
    fun authority(profile: ConnectionProfile): String {
        val uriHost = if (profile.host.contains(':') && !profile.host.startsWith('[')) "[${profile.host}]" else profile.host
        var authority = uriHost
        if (profile.port != 3389) authority = "$uriHost:${profile.port}"
        if (!profile.username.isNullOrBlank()) authority = "${profile.username}@$authority"
        return authority
    }

    /** Argumen gateway FreeRDP: `g:host[:port][,u:user][,d:domain][,p:pass]`. */
    fun gatewayArg(g: XyGateway): String = buildString {
        append("g:").append(g.host)
        if (g.port != 443) append(':').append(g.port)
        g.username?.takeIf { it.isNotBlank() }?.let { append(",u:").append(it) }
        g.domain?.takeIf { it.isNotBlank() }?.let { append(",d:").append(it) }
        g.password?.takeIf { it.isNotBlank() }?.let { append(",p:").append(it) }
    }

    /**
     * Semua query param, urutannya tetap (menentukan argumen CLI di inti):
     * p, domain, audio-mode, sound, microphone, clipboard, drive, dvc,
     * multitransport, network, gfx, gateway.
     *
     * Catatan: `sound` dikirim dengan nilai kosong — inti menerjemahkan
     * param bernilai kosong jadi argumen tanpa nilai (`/sound`).
     */
    fun queryParams(
        profile: ConnectionProfile,
        options: RdpOptions = RdpOptions(),
    ): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(12)
        if (!profile.password.isNullOrBlank()) out += "p" to profile.password!!
        if (!profile.domain.isNullOrBlank()) out += "domain" to profile.domain!!

        // Audio: /audio-mode:0|1|2, ditambah /sound saat diputar di perangkat.
        out += "audio-mode" to options.audioMode.wire.toString()
        if (options.audioMode == XyAudioMode.DEVICE) out += "sound" to ""
        if (options.microphone) out += "microphone" to ""

        out += "clipboard" to if (options.clipboard) "+" else "-"
        if (options.localDrive) out += "drive" to "XyDesk"
        if (options.camera) out += "dvc" to "rdpecam"

        // Hanya dikirim saat aktif: "+multitransport" menyalakan RDP-UDP (FEC).
        if (options.udpTransport) out += "multitransport" to "+"
        when {
            options.lowBandwidth -> out += "network" to "broadband-low"
            options.networkAutoDetect -> out += "network" to "auto"
        }
        if (options.h264) {
            val gfxCodec = if (options.lowBandwidth) "AVC420" else "AVC444"
            out += "gfx" to gfxCodec
        }

        // Kanal DISP (Display Control): bikin resolusi remote bisa diubah
        // saat sesi hidup lewat LibFreeRDP.sendMonitorLayout.
        if (options.dynamicResolution) out += "dynamic-resolution" to ""

        options.gateway?.let { out += "gateway" to gatewayArg(it) }
        return out
    }

    fun build(profile: ConnectionProfile, options: RdpOptions = RdpOptions()): Uri {
        val b = Uri.Builder().scheme("rdp").encodedAuthority(authority(profile))
        queryParams(profile, options).forEach { (key, value) -> b.appendQueryParameter(key, value) }
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
