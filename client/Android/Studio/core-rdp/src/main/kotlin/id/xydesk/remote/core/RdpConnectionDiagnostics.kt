package id.xydesk.remote.core

/** Pure target classification. Never resolves DNS or probes a user's private network. */
object RdpConnectionDiagnostics {
    fun isSharedAddress(host: String): Boolean {
        val parts = host.trim().split('.')
        if (parts.size != 4 || parts.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } }) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        return octets.all { it in 0..255 } && octets[0] == 100 && octets[1] in 64..127
    }

    fun unreachableMessage(host: String, port: Int): String =
        "Tidak bisa menghubungi $host:$port pada tahap TCP, sebelum login. Periksa alamat/port, host, layanan RDP, firewall dan rute jaringan." +
            if (isSharedAddress(host)) " Alamat ini berada di shared-address 100.64.0.0/10, bukan IP publik biasa. Jika milik VPN/overlay, periksa koneksi kedua perangkat, rute dan ACL pada layanan tersebut; alamat ini saja tidak membuktikan VPN tertentu." else ""
}

/** Retry may change transport/codec, never silently weaken the requested authentication or TLS level. */
fun RdpOptions.forHandshakeCompatibility(stage: Int): RdpOptions = when (stage) {
    1 -> copy(udpTransport = false)
    2 -> copy(udpTransport = false, h264 = false)
    else -> this
}
