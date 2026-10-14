package id.xydesk.remote.pcstream

import kotlinx.coroutines.CancellationException

/**
 * Onboarding hub — langkah jaringan sebelum WebSocket. Interface ini agar
 * [XyHubSignalingSession] bisa diuji JVM dengan onboarding palsu;
 * implementasi produksinya [XyHubAuth].
 */
interface XyHubOnboarding {
    /** Login guest baru, atau perbarui lewat [refreshToken] (rate limit 429 → `retry_in`). */
    suspend fun guestLogin(refreshToken: String? = null): XyHubGuestSession

    /** Tukar JWT sesi dengan tiket WebSocket 5 menit yang terikat [deviceId]. */
    suspend fun signalToken(sessionToken: String, deviceId: String): String

    /** TURN/STUN tambahan; daftar kosong berarti lanjut dengan STUN default. */
    suspend fun turnIceServers(deviceId: String, ticket: String): List<XyHubIceServer>

    /** URL `/ws` siap dibuka transport. */
    fun buildWebSocketUrl(deviceId: String, ticket: String): String
}

/** Status sesi signaling dari sisi klien remote. */
enum class XyHubSessionState {
    /** Login guest + tukar tiket. */
    AUTHENTICATING,

    /** WebSocket dibuka, menunggu `welcome`. */
    CONNECTING,

    /** Terdaftar di hub; pairing belum/tidak sedang berjalan. */
    REGISTERED,

    /** `pair` terkirim, menunggu `pair-response` host. */
    PAIRING,

    /** Host menerima pairing — media (offer/answer/ICE) boleh berjalan. */
    PAIRED,

    /** Ditutup normal. */
    CLOSED,

    /** Gagal (onboarding, jaringan, atau protokol). */
    FAILED,
}

/**
 * Orkestrator sesi PC-stream produksi: onboarding → WebSocket hub → pairing
 * PIN → pesan media host diteruskan ke pemanggil.
 *
 * ```text
 * start(hostId, pin, name, refresh?)
 *   ├─ guestLogin ── signalToken ── turnIceServers (opsional)
 *   ├─ connect /ws → hello → welcome → pair(hostId, pin)
 *   └─ listener.onPairAccepted → [WebRTC wiring pemanggil:
 *        signaling.offer(...) / signaling.ice(...) — jawaban host masuk
 *        lewat onRemoteAnswer/onRemoteIce]
 * stop(reason) → signaling.bye(hostId) sebaiknya dikirim pemanggil lebih dulu.
 * ```
 *
 * Pairing ditolak (`pair-response` accepted=false, `peer-offline`) TIDAK
 * menutup sesi — pemanggil boleh [pair] lagi dengan PIN/id sama tanpa
 * onboarding ulang. Rem anti-penebak dilaporkan lewat [Listener.onPairBlocked]
 * beserta sisa waktunya.
 *
 * Callback datang dari thread transport/coroutine internal; UI wajib
 * berpindah ke thread utama sendiri. Class ini TIDAK menyentuh WebRTC —
 * lapisan media menyusul di atas [signaling] + [XyHostInputCodec].
 */
class XyHubSignalingSession(
    private val onboarding: XyHubOnboarding,
    private val transportFactory: (url: String) -> XyHubTransport,
    private val listener: Listener,
    val deviceId: String = XyHubIds.generateClientId(),
) {
    private val lock = Any()

    @Volatile
    private var started = false

    @Volatile
    private var terminal = false

    @Volatile
    private var currentHostId: String? = null

    @Volatile
    private var lastIceServers: List<XyHubIceServer> = emptyList()

    @Volatile
    var state: XyHubSessionState = XyHubSessionState.AUTHENTICATING
        private set

    /** Kunci pembaruan sesi guest terakhir — simpan pemanggil untuk login tanpa hitung ulang. */
    @Volatile
    var guestRefreshToken: String? = null
        private set

    /** Klien signaling aktif setelah [start]; null sebelum itu. */
    @Volatile
    var signaling: XyHubSignalClient? = null
        private set

    /** Event sesi untuk UI/media (3c). */
    interface Listener {
        fun onSessionState(state: XyHubSessionState, detail: String?)

        /** Pairing diterima — mulai negosiasi media via [XyHubSignalingSession.signaling]. */
        fun onPairAccepted(hostId: String, iceServers: List<XyHubIceServer>)

        /** Ditolak host (PIN salah, dsb.) atau host tidak online. Sesi tetap hidup. */
        fun onPairRejected(reason: String?)

        /** Rem anti-penebak: tahan pairing dan tampilkan sisa waktu. */
        fun onPairBlocked(retryInSeconds: Long)

        /** SDP answer host — setRemoteDescription di lapisan media. */
        fun onRemoteAnswer(sdpType: String, sdp: String)

        /** Kandidat ICE host — addIceCandidate di lapisan media. */
        fun onRemoteIce(candidate: String, sdpMid: String?, sdpMLineIndex: Int?)

        /** Host menutup sesi. */
        fun onHostBye()

        fun onSignalingFailure(error: Throwable)

        fun onSignalingClosed(code: Int, reason: String)
    }

    /**
     * Jalankan onboarding lalu sambungkan WebSocket dan otomatis `pair`
     * setelah `welcome`. Kegagalan onboarding/URL = [XyHubSessionState.FAILED]
     * tanpa menyentuh transport.
     */
    suspend fun start(hostId: String, pin: String, name: String?, refreshToken: String? = null) {
        synchronized(lock) {
            check(!started) { "Sesi signaling hanya bisa start sekali" }
            started = true
        }
        val target = XyHubIds.requireHostId(hostId)
        currentHostId = target
        try {
            setState(XyHubSessionState.AUTHENTICATING, null)
            val guest = onboarding.guestLogin(refreshToken)
            guestRefreshToken = guest.refreshToken
            val ticket = onboarding.signalToken(guest.token, deviceId)
            val iceServers = try {
                onboarding.turnIceServers(deviceId, ticket)
            } catch (nonFatal: XyHubHttpException) {
                // TURN opsional — 429/403 di sini tidak boleh membatalkan sesi.
                lastIceServers = emptyList()
                setState(XyHubSessionState.AUTHENTICATING, "turn-unavailable")
                emptyList()
            }
            lastIceServers = iceServers
            val url = onboarding.buildWebSocketUrl(deviceId, ticket)

            synchronized(lock) {
                check(!terminal) { "Sesi sudah ditutup sebelum tersambung" }
                setState(XyHubSessionState.CONNECTING, null)
            }
            val transport = transportFactory(url)
            val client = XyHubSignalClient(deviceId, transport, HubListener(target, pin, name))
            signaling = client
            client.connect(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            setState(XyHubSessionState.FAILED, failure.message)
            listener.onSignalingFailure(failure)
        }
    }

    /**
     * Ulangi pairing (mis. PIN salah tadi) tanpa onboarding ulang — hanya
     * valid saat masih terdaftar di hub.
     */
    fun pair(hostId: String, pin: String, name: String?) {
        val target = XyHubIds.requireHostId(hostId)
        currentHostId = target
        val client = signaling ?: throw IllegalStateException("Sesi belum start")
        client.pair(target, pin, name)
        setState(XyHubSessionState.PAIRING, null)
    }

    /** Tutup signaling. Kirim `bye` lebih dulu bila media sedang berjalan. */
    fun stop(reason: String = XyHubSignalClient.DEFAULT_CLOSE_REASON) {
        synchronized(lock) { terminal = true }
        val client = signaling
        setState(XyHubSessionState.CLOSED, null)
        client?.close(reason)
    }

    private fun setState(next: XyHubSessionState, detail: String?) {
        state = next
        listener.onSessionState(next, detail)
    }

    /** Terjemahan callback [XyHubSignalClient] menjadi event sesi. */
    private inner class HubListener(
        private val initialHostId: String,
        private val initialPin: String,
        private val initialName: String?,
    ) : XyHubSignalClient.Listener {
        override fun onHubRegistered(deviceId: String) {
            setState(XyHubSessionState.REGISTERED, null)
            // Pairing pertama otomatis: satu tombol "Sambung" di UI.
            val client = signaling ?: return
            setState(XyHubSessionState.PAIRING, null)
            client.pair(initialHostId, initialPin, initialName)
        }

        override fun onHubMessage(message: XyHubInbound) {
            when (message) {
                is XyHubInbound.PairResponse -> {
                    if (message.accepted) {
                        setState(XyHubSessionState.PAIRED, null)
                        listener.onPairAccepted(message.from, lastIceServers)
                    } else {
                        setState(XyHubSessionState.REGISTERED, message.reason)
                        listener.onPairRejected(message.reason)
                    }
                }
                is XyHubInbound.Answer -> listener.onRemoteAnswer(message.sdpType, message.sdp)
                is XyHubInbound.IceCandidate -> listener.onRemoteIce(
                    message.candidate,
                    message.sdpMid,
                    message.sdpMLineIndex,
                )
                is XyHubInbound.Bye -> {
                    listener.onHostBye()
                    stop("host-bye")
                }
                is XyHubInbound.HubError -> when (message.error) {
                    ERROR_PAIR_LOCKED -> {
                        setState(XyHubSessionState.REGISTERED, message.error)
                        listener.onPairBlocked(message.retryInSeconds ?: 0L)
                    }
                    ERROR_PEER_OFFLINE -> {
                        setState(XyHubSessionState.REGISTERED, message.error)
                        listener.onPairRejected(message.reason ?: message.error)
                    }
                    else -> listener.onSignalingFailure(
                        XyHubAuthException("${message.error}${message.reason?.let { ": $it" } ?: ""}"),
                    )
                }
                // presence/devices/pong/unknown bukan urusan sesi media.
                is XyHubInbound.PresenceOnline,
                is XyHubInbound.Devices,
                is XyHubInbound.Pong,
                is XyHubInbound.Unknown,
                -> Unit
                is XyHubInbound.Welcome,
                is XyHubInbound.Ping,
                -> Unit
            }
        }

        override fun onHubMalformed(text: String) = Unit

        override fun onHubClosed(code: Int, reason: String) {
            synchronized(lock) { terminal = true }
            setState(XyHubSessionState.CLOSED, reason)
            listener.onSignalingClosed(code, reason)
        }

        override fun onHubFailure(error: Throwable) {
            synchronized(lock) { terminal = true }
            setState(XyHubSessionState.FAILED, error.message)
            listener.onSignalingFailure(error)
        }
    }

    companion object {
        /** Rem anti-penebak hub (`hub.js` PAIR_BRAKE). */
        const val ERROR_PAIR_LOCKED = "pair-terkunci"

        /** Host ID ditanyakan tidak sedang online. */
        const val ERROR_PEER_OFFLINE = "peer-offline"
    }
}
