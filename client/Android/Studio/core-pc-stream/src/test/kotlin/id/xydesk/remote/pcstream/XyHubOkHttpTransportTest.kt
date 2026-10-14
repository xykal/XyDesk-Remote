package id.xydesk.remote.pcstream

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test kawat transport OkHttp melawan server WebSocket stub in-process:
 * jabat tangan HTTP Upgrade diverifikasi (termasuk ABSEN-nya header Origin,
 * syarat native client di worker.js), frame teks dua arah, dan jabat tangan
 * penutupan RFC 6455 dari kedua arah.
 */
class XyHubOkHttpTransportTest {
    private lateinit var server: WsStubServer
    private lateinit var transport: XyHubOkHttpTransport

    @Before
    fun setUp() {
        server = WsStubServer()
        server.start()
        transport = XyHubOkHttpTransport()
    }

    @After
    fun tearDown() {
        transport.close(1000, "test-selesai")
        server.stop()
    }

    @Test
    fun jabatTanganTanpaOriginDanQueryUtuh() {
        val listener = RecordingListener()
        transport.connect(server.wsUrl("/ws?id=xyr-0a1b2c3d4e5f&role=client&token=v2.abc.def"), listener)

        assertTrue(listener.awaitOpen())
        server.awaitHandshake()

        assertEquals("/ws?id=xyr-0a1b2c3d4e5f&role=client&token=v2.abc.def", server.requestTarget)
        assertEquals("websocket", server.header("upgrade")?.lowercase())
        assertEquals("upgrade", server.header("connection")?.lowercase())
        assertEquals("13", server.header("sec-websocket-version"))
        assertTrue(server.header("sec-websocket-key")?.length ?: 0 >= 16)
        // Native client tidak mengirim Origin (worker.js hanya memeriksa Origin browser).
        assertNull("Transport tidak boleh mengirim Origin", server.header("origin"))
    }

    @Test
    fun frameTeksDuaArah() {
        val listener = RecordingListener()
        listener.onOpenExtra = {
            assertTrue(transport.send("""{"type":"hello","to":"xyr-0a1b2c3d4e5f","reason":"client"}"""))
        }
        transport.connect(server.wsUrl("/ws"), listener)

        assertTrue(listener.awaitOpen())
        server.awaitText(1)
        assertEquals(
            """{"type":"hello","to":"xyr-0a1b2c3d4e5f","reason":"client"}""",
            server.receivedTexts.single(),
        )

        server.sendText("""{"type":"welcome","from":"123456789"}""")
        assertTrue(listener.awaitText(1))
        assertEquals("""{"type":"welcome","from":"123456789"}""", listener.texts.single())
    }

    @Test
    fun closeKlienMembawaKodeDanAlasanLaluMenerimaBalasan() {
        val listener = RecordingListener()
        transport.connect(server.wsUrl("/ws"), listener)
        assertTrue(listener.awaitOpen())

        assertTrue(transport.close(1000, "selesai"))
        assertTrue(listener.awaitClosed())

        server.awaitClose()
        assertEquals(1000, server.closeCode!!)
        assertEquals("selesai", server.closeReason)
        assertEquals(1000, listener.closeCode)
    }

    @Test
    fun closeDariServerDibalasOtomatis() {
        val listener = RecordingListener()
        transport.connect(server.wsUrl("/ws"), listener)
        assertTrue(listener.awaitOpen())

        server.initiateClose(1001, "turun")
        assertTrue(listener.awaitClosed())

        assertEquals(1001, listener.closeCode)
        assertEquals("turun", listener.closeReason)
        // Balasan close dari klien harus sampai ke server (onClosing → webSocket.close).
        server.awaitClose()
        assertEquals(1000, server.closeCode!!)
    }

    @Test
    fun sendSebelumConnectDitolak() {
        assertFalse(transport.send("apa pun"))
        assertFalse(transport.close(1000, "selesai"))
    }

    /** Perekam kejadian transport dengan latch per kejadian. */
    private class RecordingListener : XyHubTransport.Listener {
        private val openLatch = CountDownLatch(1)
        private val closedLatch = CountDownLatch(1)
        val texts = CopyOnWriteArrayList<String>()

        @Volatile var closeCode = -1
            private set

        @Volatile var closeReason: String? = null
            private set

        @Volatile var failure: Throwable? = null
            private set

        @Volatile var onOpenExtra: (() -> Unit)? = null

        override fun onOpen() {
            openLatch.countDown()
            onOpenExtra?.invoke()
        }

        override fun onMessage(text: String) {
            texts += text
        }

        override fun onClosed(code: Int, reason: String) {
            closeCode = code
            closeReason = reason
            closedLatch.countDown()
        }

        override fun onFailure(error: Throwable) {
            failure = error
        }

        fun awaitOpen(): Boolean = openLatch.await(5, TimeUnit.SECONDS)

        fun awaitClosed(): Boolean = closedLatch.await(5, TimeUnit.SECONDS)

        fun awaitText(count: Int): Boolean {
            val deadline = System.currentTimeMillis() + 5_000
            while (texts.size < count && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            return texts.size >= count
        }
    }

    // ── Server WebSocket stub (RFC 6455 minimal) ────────────────────────────

    /**
     * Server satu-koneksi: jabat tangan Upgrade + baca frame klien (selalu
     * ter-mask) + kirim frame tanpa mask. Hanya opcode text/close yang
     * diperlukan jalur ini.
     */
    private class WsStubServer {
        private val serverSocket = ServerSocket()
        private val stopped = AtomicBoolean(false)
        private val handshakeDone = CountDownLatch(1)
        private var worker: Thread? = null
        private var socket: Socket? = null
        private var output: OutputStream? = null

        val receivedTexts = CopyOnWriteArrayList<String>()

        @Volatile var requestTarget: String? = null
            private set

        @Volatile var requestHeaders: Map<String, String> = emptyMap()
            private set

        @Volatile var closeCode: Int? = null
            private set

        @Volatile var closeReason: String? = null
            private set

        @Volatile private var serverInitiatedClose = false

        val port: Int get() = serverSocket.localPort

        fun start() {
            serverSocket.reuseAddress = true
            serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
            worker = Thread {
                val client = try {
                    serverSocket.accept()
                } catch (_: Exception) {
                    return@Thread
                }
                socket = client
                try {
                    serve(client)
                } catch (_: Exception) {
                    // Koneksi ditutup di tengah jalan oleh test — bukan kegagalan.
                } finally {
                    runCatching { client.close() }
                    handshakeDone.countDown()
                }
            }.apply {
                isDaemon = true
                name = "xy-ws-stub"
                start()
            }
        }

        fun stop() {
            stopped.set(true)
            runCatching { socket?.close() }
            runCatching { serverSocket.close() }
            worker?.let { runCatching { it.join(2_000) } }
        }

        fun wsUrl(pathAndQuery: String): String = "ws://127.0.0.1:$port$pathAndQuery"

        fun header(name: String): String? = requestHeaders[name.lowercase()]

        fun awaitHandshake() {
            assertTrue("Jabat tangan tidak selesai", handshakeDone.await(5, TimeUnit.SECONDS))
        }

        fun awaitText(count: Int) {
            val deadline = System.currentTimeMillis() + 5_000
            while (receivedTexts.size < count && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
        }

        fun awaitClose() {
            val deadline = System.currentTimeMillis() + 5_000
            while (closeCode == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertTrue("Server tidak menerima frame close", closeCode != null)
        }

        /** Kirim frame text tanpa mask (server → klien). */
        fun sendText(text: String) {
            writeFrame(0x81, text.toByteArray(StandardCharsets.UTF_8))
        }

        /** Kirim frame close dari sisi server. */
        fun initiateClose(code: Int, reason: String) {
            serverInitiatedClose = true
            val reasonBytes = reason.toByteArray(StandardCharsets.UTF_8)
            val payload = ByteArray(2 + reasonBytes.size)
            payload[0] = (code ushr 8).toByte()
            payload[1] = code.toByte()
            reasonBytes.copyInto(payload, 2)
            writeFrame(0x88, payload)
        }

        private fun writeFrame(opcode: Int, payload: ByteArray) {
            val stream = output ?: return
            val header = ByteArrayOutputStream()
            header.write(opcode)
            when {
                payload.size < 126 -> header.write(payload.size)
                payload.size <= 0xFFFF -> {
                    header.write(126)
                    header.write(payload.size ushr 8)
                    header.write(payload.size and 0xFF)
                }
                else -> {
                    header.write(127)
                    var remaining = payload.size.toLong()
                    repeat(8) {
                        header.write(((remaining ushr 56) and 0xFF).toInt())
                        remaining = remaining shl 8
                    }
                }
            }
            synchronized(stream) {
                stream.write(header.toByteArray())
                stream.write(payload)
                stream.flush()
            }
        }

        private fun serve(client: Socket) {
            val input = client.getInputStream()
            output = client.getOutputStream()

            // ── Jabat tangan HTTP Upgrade ──
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val byte = input.read()
                if (byte < 0) return
                head.append(byte.toChar())
            }
            val lines = head.toString().split("\r\n")
            val requestLine = lines.firstOrNull().orEmpty().split(" ")
            if (requestLine.size < 2) return
            requestTarget = requestLine[1]
            requestHeaders = lines.drop(1).associate { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) {
                    line.lowercase() to ""
                } else {
                    line.substring(0, separator).trim().lowercase() to line.substring(separator + 1).trim()
                }
            }
            val key = requestHeaders["sec-websocket-key"] ?: return
            val accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1")
                    .digest((key + WEBSOCKET_GUID).toByteArray(StandardCharsets.US_ASCII)),
            )
            val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n"
            output.write(response.toByteArray(StandardCharsets.US_ASCII))
            output.flush()
            handshakeDone.countDown()

            // ── Loop frame klien (selalu ter-mask) ──
            while (!stopped.get()) {
                val first = input.read()
                if (first < 0) return
                val second = input.read()
                if (second < 0) return
                val opcode = first and 0x0F
                val masked = (second and 0x80) != 0
                var length = (second and 0x7F).toLong()
                if (length == 126L) {
                    length = ((input.read() and 0xFF).toLong() shl 8) or (input.read() and 0xFF).toLong()
                } else if (length == 127L) {
                    length = 0
                    repeat(8) { length = (length shl 8) or (input.read() and 0xFF).toLong() }
                }
                if (length > MAX_FRAME_BYTES) return
                val maskKey = ByteArray(4)
                if (masked) {
                    if (input.readFully(maskKey) < 4) return
                }
                val payload = ByteArray(length.toInt())
                if (input.readFully(payload) < length) return
                if (masked) {
                    for (index in payload.indices) {
                        payload[index] = (payload[index].toInt() xor maskKey[index and 3].toInt()).toByte()
                    }
                }
                when (opcode) {
                    OPCODE_TEXT -> receivedTexts += String(payload, StandardCharsets.UTF_8)
                    OPCODE_CLOSE -> {
                        if (payload.size >= 2) {
                            closeCode = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
                            closeReason = if (payload.size > 2) {
                                String(payload, 2, payload.size - 2, StandardCharsets.UTF_8)
                            } else {
                                ""
                            }
                        } else {
                            closeCode = 1005
                            closeReason = ""
                        }
                        if (!serverInitiatedClose) {
                            // Klien memulai penutupan: balas dengan kode yang sama.
                            val reply = ByteArray(2)
                            reply[0] = payload.getOrNull(0) ?: (1000 ushr 8).toByte()
                            reply[1] = payload.getOrNull(1) ?: 1000.toByte()
                            writeFrame(0x88, reply)
                        }
                        return
                    }
                    // ping/pong tidak dipakai jalur test ini (pingInterval 20 detik > durasi test).
                    else -> Unit
                }
            }
        }

        private fun InputStream.readFully(buffer: ByteArray): Long {
            var offset = 0
            while (offset < buffer.size) {
                val read = read(buffer, offset, buffer.size - offset)
                if (read < 0) break
                offset += read
            }
            return offset.toLong()
        }

        companion object {
            private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            private const val OPCODE_TEXT = 0x1
            private const val OPCODE_CLOSE = 0x8
            private const val MAX_FRAME_BYTES = 1 shl 20
        }
    }
}
