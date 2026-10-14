package id.xydesk.remote.pcstream

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Transport WebSocket tunggal-sambungan. Satu instance = satu siklus koneksi
 * (WebSocket tidak bisa dipakai ulang setelah tertutup — pihak pemanggil
 * membuat instance baru untuk menyambung ulang).
 *
 * Implementasi produksi dibungkus tipis di sekitar klien WS pilihan app
 * (mis. OkHttp); core-pc-stream sengaja tidak memilih dependensi itu supaya
 * mesin status protokol bisa diuji JVM dengan transport palsu.
 */
interface XyHubTransport {
    /** Buka koneksi; keempat callback [Listener] dipanggil dari thread transport. */
    fun connect(url: String, listener: Listener)

    /** Antre satu frame teks. @return `false` bila antrean ditolak (sudah menutup). */
    fun send(text: String): Boolean

    /** Tutup dengan kode + alasan. @return `false` bila sudah tertutup. */
    fun close(code: Int, reason: String): Boolean

    /** Kejadian koneksi mentah — bukan pesan protokol (lihat [XyHubInbound]). */
    interface Listener {
        fun onOpen()
        fun onMessage(text: String)
        fun onClosed(code: Int, reason: String)
        fun onFailure(error: Throwable)
    }
}

/** Status koneksi dari sudut pandang client hub. */
enum class XyHubConnectionState {
    /** Belum disambungkan. */
    IDLE,

    /** WebSocket terbuka, menunggu `welcome` setelah mengirim `hello`. */
    CONNECTING,

    /** `welcome` diterima — pesan relay boleh dikirim. */
    REGISTERED,

    /** Ditutup secara normal. */
    CLOSED,

    /** Gagal (jaringan/protokol). */
    FAILED,
}

/**
 * Mesin status klien hub signaling produksi.
 *
 * Siklus: [connect] → (onOpen) kirim `hello` → `welcome` ⇒ REGISTERED →
 * `pair`/`offer`/`ice`/`bye`/`presence`/`list`/`ping` → [close].
 * Pesan masuk diteruskan ke [Listener] sebagai [XyHubInbound] terketik;
 * ping level aplikasi dibalas pong otomatis (hub membalas ping ↔ pong juga).
 *
 * Pemakaian thread aman: callback transport datang dari thread pembacanya,
 * sedangkan API publik dipanggil dari thread UI/coroutine — semua transisi
 * lewat satu kunci. Media (SDP/ICE) hanya untuk negosiasi; paket video/input
 * harus lewat jalur WebRTC, bukan sini.
 */
class XyHubSignalClient(
    private val deviceId: String,
    private val transport: XyHubTransport,
    private val listener: Listener,
) {
    private val lock = ReentrantLock()

    @Volatile
    var state: XyHubConnectionState = XyHubConnectionState.IDLE
        private set

    /** Konsumsi event protokol klien (UI sesi, 3c). */
    interface Listener {
        /** `welcome` diterima — [deviceId] terdaftar di hub. */
        fun onHubRegistered(deviceId: String)

        /** Pesan masuk terketik; [XyHubInbound.Unknown] = abaikan dengan aman. */
        fun onHubMessage(message: XyHubInbound)

        /** Frame rusak (bukan JSON / tanpa type). Dipantau, tidak fatal. */
        fun onHubMalformed(text: String)

        fun onHubClosed(code: Int, reason: String)

        fun onHubFailure(error: Throwable)
    }

    /** Sambungkan ke URL `/ws` dari [XyHubAuth.buildWebSocketUrl]. */
    fun connect(url: String) {
        lock.withLock {
            check(state == XyHubConnectionState.IDLE) { "Klien hub hanya bisa menyambung sekali" }
            state = XyHubConnectionState.CONNECTING
        }
        transport.connect(url, object : XyHubTransport.Listener {
            override fun onOpen() {
                // hello dikirim sebelum welcome — tanpa guard relay.
                transport.send(XyHubOutbound.Hello(deviceId).encode())
            }

            override fun onMessage(text: String) {
                val inbound = XyHubProtocol.parseInbound(text)
                if (inbound == null) {
                    listener.onHubMalformed(text)
                    return
                }
                when (inbound) {
                    is XyHubInbound.Welcome -> {
                        lock.withLock {
                            if (state == XyHubConnectionState.CONNECTING) {
                                state = XyHubConnectionState.REGISTERED
                            }
                        }
                        listener.onHubRegistered(inbound.from)
                    }
                    is XyHubInbound.Ping -> transport.send(XyHubOutbound.Pong.encode())
                    else -> listener.onHubMessage(inbound)
                }
            }

            override fun onClosed(code: Int, reason: String) {
                lock.withLock {
                    state = XyHubConnectionState.CLOSED
                }
                listener.onHubClosed(code, reason)
            }

            override fun onFailure(error: Throwable) {
                lock.withLock {
                    state = XyHubConnectionState.FAILED
                }
                listener.onHubFailure(error)
            }
        })
    }

    /** Minta pairing ke host dengan PIN. */
    fun pair(hostId: String, pin: String, name: String? = null) {
        dispatch(XyHubOutbound.Pair(XyHubIds.requireHostId(hostId), pin, name))
    }

    /** Kirim SDP offer (client = peng-initiator). */
    fun offer(hostId: String, sdpType: String, sdp: String) {
        dispatch(XyHubOutbound.Offer(XyHubIds.requireHostId(hostId), sdpType, sdp))
    }

    /** Kirim kandidat ICE (trickle). */
    fun ice(hostId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        dispatch(XyHubOutbound.Ice(XyHubIds.requireHostId(hostId), candidate, sdpMid, sdpMLineIndex))
    }

    /** Beritahu host sesi selesai. */
    fun bye(hostId: String) {
        dispatch(XyHubOutbound.Bye(XyHubIds.requireHostId(hostId)))
    }

    /** Tanya status online ID host (maks [XyHubProtocol.PRESENCE_LIMIT]). */
    fun presence(ids: List<String>) {
        dispatch(XyHubOutbound.Presence(ids.take(XyHubProtocol.PRESENCE_LIMIT)))
    }

    /** Minta daftar perangkat (produksi selalu kosong). */
    fun requestDeviceList() {
        dispatch(XyHubOutbound.ListDevices)
    }

    /** Keep-alive level aplikasi; hub membalas `pong`. */
    fun ping() {
        dispatch(XyHubOutbound.Ping)
    }

    /** Tutup WebSocket dengan kode normal (1000). */
    fun close(reason: String = DEFAULT_CLOSE_REASON) {
        lock.withLock {
            state = XyHubConnectionState.CLOSED
        }
        transport.close(NORMAL_CLOSE_CODE, reason)
    }

    private fun dispatch(message: XyHubOutbound) {
        lock.withLock {
            check(state == XyHubConnectionState.REGISTERED) {
                "Relay hub hanya boleh setelah welcome (state=$state)"
            }
        }
        transport.send(message.encode())
    }

    companion object {
        const val NORMAL_CLOSE_CODE = 1000
        const val DEFAULT_CLOSE_REASON = "selesai"
    }
}
