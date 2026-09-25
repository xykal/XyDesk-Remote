package id.xydesk.remote.core

/**
 * Sampel telemetri sesi (M2) — dasar panel Stats HUD.
 *
 * Dipancarkan [SessionManager.telemetry] tiap 500ms. `fps` = jumlah
 * update grafik per detik (ekstrapolasi 2x dari window 500ms; angka
 * "kegiatan gambar", bukan frame rate presisi — cukup untuk Stats v1).
 */
data class TelemetrySample(
    val state: SessionState,
    val width: Int,
    val height: Int,
    val fps: Int,
) {
    companion object {
        val EMPTY = TelemetrySample(SessionState.Idle, 0, 0, 0)
    }
}
