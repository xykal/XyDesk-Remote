package id.xydesk.remote.pcstream

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Transport WebSocket produksi untuk hub signaling, dibangkus dari OkHttp
 * (klien yang sama dipakai app referensi XyDesk). Tipis dengan sengaja:
 * seluruh logika protokol ada di [XyHubSignalClient] yang diuji dengan
 * transport palsu; class ini hanya kawat.
 *
 * Perilaku yang dijamin:
 *  - Tidak mengirim header `Origin` (worker.js menolak Origin browser asing,
 *    native client memang tanpa Origin).
 *  - `pingInterval` level WebSocket: OkHttp otomatis membalas ping level WS
 *    dari hub (hibernation), menjaga koneksi tetap hidup tanpa kode aplikasi.
 *  - Satu instance = satu siklus koneksi (WebSocket OkHttp tidak bisa
 *    dipakai ulang setelah tertutup) — sama dengan kontrak [XyHubTransport].
 */
class XyHubOkHttpTransport(
    client: OkHttpClient? = null,
) : XyHubTransport {
    private val http: OkHttpClient = client
        ?: OkHttpClient.Builder()
            .connectTimeout(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .pingInterval(DEFAULT_PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .build()

    @Volatile
    private var webSocket: WebSocket? = null

    override fun connect(url: String, listener: XyHubTransport.Listener) {
        check(webSocket == null) { "Transport OkHttp tunggal-sambungan: connect sudah pernah dipanggil" }
        webSocket = http.newWebSocket(
            Request.Builder().url(url).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    listener.onMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    // Balas frame close agar jabat tangan penutupan RFC 6455 selesai;
                    // onClosed resmi menyusul setelah balasan terkirim.
                    webSocket.close(XyHubSignalClient.NORMAL_CLOSE_CODE, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    listener.onClosed(code, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    listener.onFailure(t)
                }
            },
        )
    }

    override fun send(text: String): Boolean = webSocket?.send(text) ?: false

    override fun close(code: Int, reason: String): Boolean = webSocket?.close(code, reason) ?: false

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 8_000
        private const val DEFAULT_PING_INTERVAL_MS: Long = 20_000
    }
}
