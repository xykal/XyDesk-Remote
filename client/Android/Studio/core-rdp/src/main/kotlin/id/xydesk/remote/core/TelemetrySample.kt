package id.xydesk.remote.core

/**
 * Sampel telemetri sesi (M2+) — dasar panel Stats HUD & Live Telemetry Pill.
 *
 * Dipancarkan [SessionManager.telemetry] tiap 500ms. `fps` = jumlah
 * invalidasi grafik yang sudah dikoaleskan per detik (ekstrapolasi 2x dari
 * window 500ms), bukan FPS yang dijanjikan host RDP.
 * `rttMs` = RTT TCP live ke host:port layanan (probe handshake tiap 5 detik,
 * dihaluskan EMA lewat [RttSmoother]; TCP juga dipakai sekali saat konek),
 * bukan latensi input-ke-layar; `-1` bila belum diukur.
 */
data class TelemetrySample(
    val state: SessionState,
    val width: Int,
    val height: Int,
    val fps: Int,
    val rttMs: Int = -1,
    val codecLabel: String = "H.264 AVC444",
    val udpActive: Boolean = true,
    val colorDepth: Int = 32,
    val relayLabel: String = "RDP",
    val networkLabel: String = "Wi-Fi",
    // Statistik pipeline frame sisi HP (FramePipelineStats, issue #15 B.6).
    /** Jarak antar onGraphicsUpdate rata-rata EMA (ms); -1 = belum ada. */
    val frameIntervalMs: Int = -1,
    /** Latensi update pertama batch -> flush ke layar (ms); -1 = belum ada. */
    val pipelineLatencyMs: Int = -1,
    /** Durasi salin piksel updateGraphics per update EMA (ms); -1 = belum ada. */
    val frameCopyMs: Float = -1f,
    /** Rata-rata update per flush (antrean coalescing); -1 = belum ada flush. */
    val queueDepth: Float = -1f,
    /** Puncak update per flush sejak sampel sebelumnya; 0 = belum ada flush. */
    val queuePeak: Int = 0,
) {
    companion object {
        val EMPTY = TelemetrySample(SessionState.Idle, 0, 0, 0)
    }
}
