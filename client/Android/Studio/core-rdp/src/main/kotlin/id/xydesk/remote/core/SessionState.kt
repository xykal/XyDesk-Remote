package id.xydesk.remote.core

/**
 * State machine sesi XyDesk (M1). Satu-satunya sumber kebenaran status sesi;
 * HUD (M2) dan UI (M1.2) hanya mengkonsumsi [SessionManager.state].
 *
 * Transisi legal:
 * ```
 * Idle -> Connecting -> Authenticating? -> Connected
 * Connecting/Authenticating -> Error
 * Connected -> Disconnecting -> Disconnected
 * Error/Disconnected -> Connecting  (connect() berikutnya)
 * ```
 */
sealed class SessionState {
    /** Tidak ada sesi. */
    data object Idle : SessionState()

    /** TCP/TLS handshake + NLA berjalan di thread native. */
    data object Connecting : SessionState()

    /** Server minta credential interaktif (NLA prompt). */
    data object Authenticating : SessionState()

    /** Sesi aktif — frame mengalir. */
    data object Connected : SessionState()

    /** Request disconnect (app atau remote). */
    data object Disconnecting : SessionState()

    /** Sesi selesai (normal/remote). Bukan error. */
    data object Disconnected : SessionState()

    /**
     * Sesi gagal. [code] stabil (bisa di-`when`), [message] manusia-readable
     * (dari FreeRDP — boleh berubah antar versi, jangan di-hardcode).
     */
    data class Error(val code: String, val message: String) : SessionState()
}
