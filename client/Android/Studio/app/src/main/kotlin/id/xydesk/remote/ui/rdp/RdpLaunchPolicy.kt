package id.xydesk.remote.ui.rdp

/** Pure preflight: no credentials, provisioning, DNS queries or API calls. */
internal fun validateRdpLaunch(
    duration: String, runner: String, hostname: String, username: String,
    access: String, portmapHost: String = "", portmapPort: String = "",
): List<String> = buildList {
    if ((duration.toIntOrNull() ?: -1) !in 15..360) add("Durasi harus 15–360 menit.")
    if (runner !in setOf("windows-2022", "windows-2025", "windows-latest", "ubuntu-22.04", "ubuntu-24.04", "ubuntu-latest"))
        add("Pilih image Windows/Ubuntu yang tersedia di menu OS.")
    val maxHost = if (runner.startsWith("windows")) 15 else 63
    if (hostname.length !in 1..maxHost || !Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?").matches(hostname) || hostname.all { it.isDigit() })
        add("Hostname: 1–$maxHost huruf/angka/tanda hubung, tanpa spasi; bukan hanya angka.")
    val maxUser = if (runner.startsWith("windows")) 20 else 32
    if (username.length !in 1..maxUser || !Regex("[A-Za-z_][A-Za-z0-9_.-]*").matches(username))
        add("Username: 1–$maxUser karakter, mulai huruf/underscore; gunakan huruf, angka, titik, underscore atau tanda hubung.")
    if (access !in setOf("tailscale", "portmap")) add("Pilih akses Tailscale atau Portmap.")
    if (access == "portmap") {
        if (portmapHost.isNotEmpty() && (portmapHost.length > 253 ||
            !Regex("[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?").matches(portmapHost) ||
            portmapHost.split('.').any { it.isBlank() || it.length > 63 || it.startsWith('-') || it.endsWith('-') }))
            add("Host Portmap harus hostname/IP saja, tanpa https://, path atau port.")
        if (portmapPort.isNotEmpty() && (portmapPort.toIntOrNull() ?: -1) !in 1..65535)
            add("Port Portmap harus 1–65535.")
    }
}
