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

    /**
     * Normalisasi input host, port, username (`DOMAIN\user` atau `.\user`), dan domain
     * agar format yang biasa diketik pengguna di Microsoft Windows App (mis. `host:port`,
     * `.\Administrator`, `WORKGROUP\User`, `user@outlook.com`) selalu valid di FreeRDP.
     */
    fun normalizeProfile(profile: ConnectionProfile): ConnectionProfile {
        var rawHost = profile.host.trim()
            .removePrefix("rdp://")
            .removePrefix("tcp://")
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        var resolvedPort = profile.port

        // Jika pengguna mengetik host:port (satu titik dua, bukan IPv6 literal)
        if (!rawHost.startsWith('[') && rawHost.count { it == ':' } == 1) {
            val idx = rawHost.lastIndexOf(':')
            val maybeHost = rawHost.substring(0, idx).trim()
            val maybePort = rawHost.substring(idx + 1).trim().toIntOrNull()
            if (maybeHost.isNotEmpty() && maybePort != null && maybePort in 1..65535) {
                rawHost = maybeHost
                if (resolvedPort == 3389) {
                    resolvedPort = maybePort
                }
            }
        } else if (rawHost.startsWith('[') && rawHost.contains("]:")) {
            val closeIdx = rawHost.indexOf("]:")
            val maybeHost = rawHost.substring(1, closeIdx).trim()
            val maybePort = rawHost.substring(closeIdx + 2).trim().toIntOrNull()
            if (maybeHost.isNotEmpty() && maybePort != null && maybePort in 1..65535) {
                rawHost = maybeHost
                if (resolvedPort == 3389) {
                    resolvedPort = maybePort
                }
            }
        }

        var resolvedUser = profile.username?.trim()?.takeIf { it.isNotEmpty() }
        var resolvedDomain = profile.domain?.trim()?.takeIf { it.isNotEmpty() }

        // Pisahkan DOMAIN\username atau .\username bila kolom domain kosong
        if (resolvedUser != null && resolvedUser.contains('\\') && resolvedDomain == null) {
            val slashIdx = resolvedUser.indexOf('\\')
            val domPart = resolvedUser.substring(0, slashIdx).trim()
            val usrPart = resolvedUser.substring(slashIdx + 1).trim()
            if (usrPart.isNotEmpty()) {
                resolvedUser = usrPart
                if (domPart.isNotEmpty() && domPart != ".") {
                    resolvedDomain = domPart
                }
            }
        }

        return profile.copy(
            host = rawHost.ifEmpty { profile.host },
            port = resolvedPort,
            username = resolvedUser,
            domain = resolvedDomain,
        )
    }

    private fun needsQueryParamUser(user: String?): Boolean {
        val u = user?.trim() ?: return false
        if (u.isEmpty()) return false
        return u.any { it == '@' || it == '\\' || it == ':' || it == '/' || it == '%' || it.isWhitespace() }
    }

    /** `[user@]host[:port]`; port default 3389 tidak ditulis. */
    fun authority(profile: ConnectionProfile): String {
        val norm = normalizeProfile(profile)
        val isIpv6 = norm.host.count { it == ':' } >= 2 && !norm.host.startsWith('[')
        val uriHost = if (isIpv6) "[${norm.host}]" else norm.host
        var authority = uriHost
        if (norm.port != 3389) authority = "$uriHost:${norm.port}"
        val u = norm.username
        if (!u.isNullOrBlank() && !needsQueryParamUser(u)) {
            authority = "$u@$authority"
        }
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
     * multitransport, network, gfx, dynamic-resolution, streaming/latency
     * flags, security flags, lalu gateway.
     *
     * Catatan: `sound` dikirim dengan nilai kosong — inti menerjemahkan
     * param bernilai kosong jadi argumen tanpa nilai (`/sound`).
     */
    fun queryParams(
        profile: ConnectionProfile,
        options: RdpOptions = RdpOptions(),
    ): List<Pair<String, String>> {
        val norm = normalizeProfile(profile)
        val out = ArrayList<Pair<String, String>>(26)
        if (!norm.username.isNullOrBlank() && needsQueryParamUser(norm.username)) {
            out += "u" to norm.username!!
        }
        if (!norm.password.isNullOrBlank()) out += "p" to norm.password!!
        if (!norm.domain.isNullOrBlank()) out += "domain" to norm.domain!!

        // Audio: /audio-mode:0|1|2, ditambah /sound:sys:opensles,format:1,quality:high saat diputar di perangkat.
        out += "audio-mode" to options.audioMode.wire.toString()
        if (options.audioMode == XyAudioMode.DEVICE) {
            out += "sound" to "sys:opensles,format:1,quality:high"
        }
        if (options.microphone && !options.pcConnectMode) {
            out += "microphone" to "sys:opensles,format:1"
        }

        out += "clipboard" to if (options.clipboard) "+" else "-"
        if (options.localDrive && !options.pcConnectMode) out += "drive" to "XyDesk"
        if (options.camera && !options.pcConnectMode) out += "dvc" to "rdpecam"

        // Hanya dikirim saat aktif: "+multitransport" menyalakan RDP-UDP (FEC).
        if (options.udpTransport) out += "multitransport" to "+"
        when {
            options.lowBandwidth -> out += "network" to "broadband-low"
            options.networkAutoDetect -> out += "network" to "auto"
        }
        if (options.h264) {
            val gfxCodec = buildString {
                append(if (options.lowBandwidth) "AVC420" else "AVC444")
                if (options.gfxSmallCache) append(",small-cache")
            }
            out += "gfx" to gfxCodec
            // ---- Engine Performa Lanjutan (custom FreeRDP, ronde 42) ----
            // /video hanya masuk akal saat pipeline GFX aktif.
            if (options.videoRemoting) out += "video" to ""
        }
        if (options.frameAck in 1..10) out += "frame-ack" to options.frameAck.toString()
        if (options.responsiveLoopMs in 1..1000) {
            out += "max-loop-time" to options.responsiveLoopMs.toString()
        }

        // Kanal DISP (Display Control): bikin resolusi remote bisa diubah
        // saat sesi hidup lewat LibFreeRDP.sendMonitorLayout.
        // Profil PC saat ini tetap RDP (/admin); DISP dinonaktifkan, tetapi resolusi monitor host tidak dijamin 1:1.
        if (options.dynamicResolution && !options.pcConnectMode) out += "dynamic-resolution" to ""

        // ---- Streaming & Latency CLI flags ----
        if (options.colorDepth != 32 || options.streamProfile != XyStreamProfile.AUTO) {
            out += "bpp" to options.colorDepth.coerceIn(16, 32).toString()
        }
        if (options.asyncUpdate) out += "async-update" to "+"
        if (options.asyncChannels) out += "async-channels" to "+"
        when (options.compressionLevel) {
            0 -> out += "compression" to "-"
            2 -> out += "compression-level" to "2"
        }
        if (options.fontSmoothing) out += "fonts" to "+"
        if (options.desktopWallpaper) {
            out += "wallpaper" to "+"
        } else if (options.streamProfile != XyStreamProfile.AUTO) {
            out += "wallpaper" to "-"
        }
        if (options.windowDrag) {
            out += "window-drag" to "+"
        } else if (options.streamProfile != XyStreamProfile.AUTO) {
            out += "window-drag" to "-"
        }
        if (options.menuAnimations) {
            out += "menu-anims" to "+"
        } else if (options.streamProfile != XyStreamProfile.AUTO) {
            out += "menu-anims" to "-"
        }
        if (!options.visualThemes) {
            out += "themes" to "-"
        } else if (options.streamProfile == XyStreamProfile.HIGH_VISUAL) {
            out += "themes" to "+"
        }
        if (options.desktopComposition) out += "aero" to "+"

        // ---- Security & Session Hardening CLI flags ----
        options.securityProtocol.wire?.let { out += "sec" to it }
        if (options.tlsSecLevel in 0..2) {
            out += "tls" to "seclevel:${options.tlsSecLevel}"
        }
        if (options.consoleAdmin || options.pcConnectMode) out += "admin" to ""
        if (options.restrictedAdmin && !options.pcConnectMode) out += "restricted-admin" to ""
        if (!options.pcConnectMode) {
            options.remoteProgram?.trim()?.takeIf { it.isNotEmpty() }?.let {
                out += "shell" to it
            }
            options.remoteWorkDir?.trim()?.takeIf { it.isNotEmpty() }?.let {
                out += "shell-dir" to it
            }
            options.gateway?.let { out += "gateway" to gatewayArg(it) }
        }
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
