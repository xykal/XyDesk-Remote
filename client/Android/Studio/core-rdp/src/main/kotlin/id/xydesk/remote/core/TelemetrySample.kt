package id.xydesk.remote.core

/**
 * Sampel telemetri sesi (M2+) — dasar panel Stats HUD & Live Telemetry Pill.
 *
 * Dipancarkan [SessionManager.telemetry] tiap 500ms. `fps` = jumlah
 * update grafik per detik (ekstrapolasi 2x dari window 500ms).
 * `rttMs` = estimasi latensi TCP round-trip dalam milidetik (`-1` bila belum diukur).
 */
data class TelemetrySample(
    val state: SessionState,
    val width: Int,
    val height: Int,
    val fps: Int,
    val rttMs: Int = -1,
    val codecLabel: String = "AVC444",
    val udpActive: Boolean = true,
    val colorDepth: Int = 32,
) {
    companion object {
        val EMPTY = TelemetrySample(SessionState.Idle, 0, 0, 0)
    }
}
