package id.xydesk.remote.ui

/** Temporary containment for the legacy unauthenticated UDP engine, NOT encryption/auth. */
internal fun isPrivateDirectHost(host: String): Boolean {
    val value = host.trim().lowercase()
    if (Regex("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.ts\\.net").matches(value)) return true
    val parts = value.split('.')
    if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || (it.length > 1 && it.startsWith('0')) || it.any { c -> c !in '0'..'9' } }) return false
    val bytes = parts.map { it.toIntOrNull() ?: return false }
    if (bytes.any { it !in 0..255 }) return false
    return bytes[0] == 10 || bytes[0] == 192 && bytes[1] == 168 ||
        bytes[0] == 172 && bytes[1] in 16..31 || bytes[0] == 100 && bytes[1] in 64..127
}
