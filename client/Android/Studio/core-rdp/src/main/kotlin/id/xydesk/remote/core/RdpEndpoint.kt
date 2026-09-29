package id.xydesk.remote.core

/** Parsed host and optional RDP port from the single address field. */
data class RdpEndpoint(val host: String, val port: Int = 3389) {
    init {
        require(host.isNotBlank()) { "host wajib diisi" }
        require(port in 1..65535) { "port harus 1-65535" }
    }
}

/**
 * Accepts a hostname/IP with an optional port. IPv6 addresses may be entered
 * bare when using the default port, or bracketed when an explicit port follows:
 * `pc.example`, `pc.example:3390`, `192.0.2.4:3390`, `[2001:db8::1]:3390`.
 */
fun parseRdpEndpoint(input: String, defaultPort: Int = 3389): RdpEndpoint? {
    if (defaultPort !in 1..65535) return null
    val value = input.trim()
    if (value.isEmpty() || value.any { it.isWhitespace() }) return null

    val host: String
    val port: Int
    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        if (end <= 1) return null
        host = value.substring(1, end)
        val suffix = value.substring(end + 1)
        if (':' !in host || (suffix.isNotEmpty() && !suffix.startsWith(':'))) return null
        port = if (suffix.isEmpty()) defaultPort else {
            if (suffix.length == 1 || suffix.substring(1).any { !it.isDigit() }) return null
            suffix.substring(1).toIntOrNull() ?: return null
        }
    } else {
        when (value.count { it == ':' }) {
            0 -> {
                host = value
                port = defaultPort
            }
            1 -> {
                val colon = value.indexOf(':')
                host = value.substring(0, colon)
                val portText = value.substring(colon + 1)
                if (portText.isEmpty() || portText.any { !it.isDigit() }) return null
                port = portText.toIntOrNull() ?: return null
            }
            else -> {
                // Unbracketed IPv6 is only unambiguous with the default port.
                host = value
                port = defaultPort
            }
        }
    }
    if (host.isBlank() || port !in 1..65535) return null
    return RdpEndpoint(host, port)
}

/** Format an existing saved profile into the one-field address syntax. */
fun formatRdpEndpoint(host: String, port: Int): String {
    val formattedHost = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
    return if (port == 3389) formattedHost else "$formattedHost:$port"
}
