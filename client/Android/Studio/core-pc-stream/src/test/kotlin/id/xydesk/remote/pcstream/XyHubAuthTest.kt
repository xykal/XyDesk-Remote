package id.xydesk.remote.pcstream

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Test onboarding hub (`/auth/guest` + `/signal-token`) melawan stub HTTP
 * in-process. Stub memakai [ServerSocket] mentah (bukan com.sun.net.httpserver)
 * karena modul itu tidak ada di classpath unit-test Android — sama seperti
 * [PcSignalClientTest].
 */
class XyHubAuthTest {
    private lateinit var server: StubHttpEndpoint
    private lateinit var auth: XyHubAuth

    private val observedRequests = CopyOnWriteArrayList<ObservedRequest>()

    private val guestToken = "guest.jwt.payload"
    private val guestRefresh = "refresh-secret-value"

    private data class ObservedRequest(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val authorization: String?,
        val body: String,
    )

    @Before
    fun setUp() {
        observedRequests.clear()
        server = StubHttpEndpoint { method, path, query, headers, body ->
            observedRequests += ObservedRequest(method, path, query, headers["authorization"], body)
            when {
                method == "POST" && path == "/auth/guest" -> StubResponse(
                    200,
                    """{"token":"$guestToken","refresh":"$guestRefresh","guest":true}""",
                )

                method == "GET" && path == "/signal-token" -> StubResponse(
                    200,
                    "v2.eyJhbGciOiJIUzI1NiJ9.ticket",
                )

                else -> StubResponse(404, """{"error":"not_found"}""")
            }
        }
        server.start()
        auth = XyHubAuth("http://127.0.0.1:${server.port}")
    }

    @After
    fun tearDown() {
        server.stop()
    }

    @Test
    fun guestLoginBaruMengirimBodyKosongDanMenyimpanSesi() = runBlocking {
        val session = auth.guestLogin()

        assertEquals(guestToken, session.token)
        assertEquals(guestRefresh, session.refreshToken)
        assertTrue(session.issuedAtEpochMs > 0)

        val request = observedRequests.single()
        assertEquals("POST", request.method)
        assertEquals("/auth/guest", request.path)
        assertEquals("{}", JSONObject(request.body).toString())
        assertNull(request.authorization)
        assertFalse(session.toString().contains(guestToken))
        assertFalse(session.toString().contains(guestRefresh))
    }

    @Test
    fun guestLoginPerbaruiMembawaRefresh() = runBlocking {
        auth.guestLogin(refreshToken = guestRefresh)

        val body = JSONObject(observedRequests.single().body)
        assertEquals(guestRefresh, body.getString("refresh"))
    }

    @Test
    fun guestLoginRateLimitedMembawaRetryIn() {
        server.route = { _, path, _, _ ->
            if (path == "/auth/guest") StubResponse(429, """{"error":"rate-limited","retry_in":42}""")
            else StubResponse(404, "{}")
        }
        val error = assertThrows(XyHubHttpException::class.java) {
            runBlocking { auth.guestLogin() }
        }
        assertEquals(429, error.httpStatus)
        assertEquals(42L, error.retryInSeconds)
    }

    @Test
    fun guestLoginResponsTanpaFieldWajibDitolak() {
        server.route = { _, path, _, _ ->
            if (path == "/auth/guest") StubResponse(200, """{"guest":true}""")
            else StubResponse(404, "{}")
        }
        assertThrows(XyHubAuthException::class.java) {
            runBlocking { auth.guestLogin() }
        }
    }

    @Test
    fun guestLoginBukanGuestDitolak() {
        server.route = { _, path, _, _ ->
            if (path == "/auth/guest") StubResponse(200, """{"token":"a","refresh":"b","guest":false}""")
            else StubResponse(404, "{}")
        }
        assertThrows(XyHubAuthException::class.java) {
            runBlocking { auth.guestLogin() }
        }
    }

    @Test
    fun signalTokenMenukarJwtDenganTiket() = runBlocking {
        val deviceId = "xyr-0a1b2c3d4e5f"
        val ticket = auth.signalToken(guestToken, deviceId)

        assertEquals("v2.eyJhbGciOiJIUzI1NiJ9.ticket", ticket)

        val request = observedRequests.single()
        assertEquals("GET", request.method)
        assertEquals("/signal-token", request.path)
        assertEquals(deviceId, request.query["id"])
        assertEquals("Bearer $guestToken", request.authorization)
    }

    @Test
    fun signalTokenMenolak401() {
        server.route = { method, path, _, _ ->
            if (method == "GET" && path == "/signal-token") StubResponse(401, "unauthorized")
            else StubResponse(404, "{}")
        }
        val error = assertThrows(XyHubHttpException::class.java) {
            runBlocking { auth.signalToken(guestToken, "xyr-0a1b2c3d4e5f") }
        }
        assertEquals(401, error.httpStatus)
        assertNull(error.retryInSeconds)
    }

    @Test
    fun signalTokenMenolakDeviceIdSesuaiAturanWorker() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { auth.signalToken(guestToken, "ab") }
        }
        // Validasi terjadi sebelum permintaan jaringan apa pun.
        assertTrue(observedRequests.isEmpty())
    }

    @Test
    fun signalTokenMenolakTiketTakDikenali() {
        server.route = { method, path, _, _ ->
            if (method == "GET" && path == "/signal-token") StubResponse(200, "<html>gateway error</html>")
            else StubResponse(404, "{}")
        }
        assertThrows(XyHubAuthException::class.java) {
            runBlocking { auth.signalToken(guestToken, "xyr-0a1b2c3d4e5f") }
        }
    }

    @Test
    fun buildWebSocketUrlHttpsMenjadiWss() {
        val https = XyHubAuth("https://signal.xydesk.my.id")
        val url = https.buildWebSocketUrl("xyr-0a1b2c3d4e5f", "v2.abc.def-ghi_jkl")
        assertEquals(
            "wss://signal.xydesk.my.id/ws?id=xyr-0a1b2c3d4e5f&role=client&token=v2.abc.def-ghi_jkl",
            url,
        )
    }

    @Test
    fun buildWebSocketUrlLoopbackMenjadiWs() {
        val url = auth.buildWebSocketUrl("xyr-0a1b2c3d4e5f", "v2.t")
        assertTrue(url.startsWith("ws://127.0.0.1:${server.port}/ws?"))
        assertTrue(url.contains("id=xyr-0a1b2c3d4e5f"))
        assertTrue(url.contains("role=client"))
        assertTrue(url.contains("token=v2.t"))
    }

    @Test
    fun buildWebSocketUrlMenolakDeviceIdTakSah() {
        assertThrows(IllegalArgumentException::class.java) {
            auth.buildWebSocketUrl("spasi ada", "v2.t")
        }
    }

    @Test
    fun baseUrlHarusHttpsKecualiLoopback() {
        assertThrows(IllegalArgumentException::class.java) { XyHubAuth("http://example.com") }
        assertThrows(IllegalArgumentException::class.java) { XyHubAuth("https://host/path") }
        assertThrows(IllegalArgumentException::class.java) { XyHubAuth("https://host/?q=1") }
        assertNotNull(XyHubAuth("https://signal.xydesk.my.id/"))
    }

    private data class StubResponse(val status: Int, val body: String)

    /** Minimal single-threaded HTTP/1.1 responder dengan dukungan query string. */
    private class StubHttpEndpoint(
        var route: (method: String, path: String, query: Map<String, String>, headers: Map<String, String>, body: String) -> StubResponse,
    ) {
        private val serverSocket = ServerSocket()
        private val stopped = AtomicBoolean(false)
        private var worker: Thread? = null

        val port: Int get() = serverSocket.localPort

        fun start() {
            serverSocket.reuseAddress = true
            serverSocket.bind(InetSocketAddress("127.0.0.1", 0))
            worker = Thread {
                while (!stopped.get()) {
                    val socket = try {
                        serverSocket.accept()
                    } catch (_: Exception) {
                        break
                    }
                    try {
                        serve(socket)
                    } catch (_: Exception) {
                        // Permintaan stub rusak tidak boleh mematikan test lain.
                    } finally {
                        runCatching { socket.close() }
                    }
                }
            }.apply {
                isDaemon = true
                name = "xy-hub-auth-stub"
                start()
            }
        }

        fun stop() {
            stopped.set(true)
            runCatching { serverSocket.close() }
            worker?.let { runCatching { it.join(2_000) } }
        }

        private fun serve(socket: Socket) {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val head = readHeaders(input)
            val requestLine = head.lines().firstOrNull().orEmpty().split(" ")
            if (requestLine.size < 2) return
            val method = requestLine[0]
            val target = requestLine[1]
            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "").split('&')
                .filter { it.contains('=') }
                .associate {
                    val key = it.substringBefore('=')
                    java.net.URLDecoder.decode(key, StandardCharsets.UTF_8.name()) to
                        java.net.URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8.name())
                }
            val headers = HashMap<String, String>()
            head.lines().drop(1).forEach { line ->
                val separator = line.indexOf(':')
                if (separator > 0) {
                    headers[line.substring(0, separator).trim().lowercase()] =
                        line.substring(separator + 1).trim()
                }
            }
            val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
            val body = if (contentLength > 0) {
                String(readExactly(input, contentLength), StandardCharsets.UTF_8)
            } else {
                ""
            }
            val response = route(method, path, query, headers, body)
            val payload = response.body.toByteArray(StandardCharsets.UTF_8)
            val statusText = when (response.status) {
                200 -> "OK"
                404 -> "Not Found"
                401 -> "Unauthorized"
                429 -> "Too Many Requests"
                else -> "Error"
            }
            val header = buildString {
                append("HTTP/1.1 ").append(response.status).append(' ').append(statusText).append("\r\n")
                append("Content-Type: text/plain; charset=utf-8\r\n")
                append("Content-Length: ").append(payload.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(header.toByteArray(StandardCharsets.UTF_8))
            output.write(payload)
            output.flush()
        }

        private fun readHeaders(input: InputStream): String {
            val buffer = ByteArrayOutputStream()
            var lastFour = 0
            while (true) {
                val byte = input.read()
                if (byte < 0) break
                buffer.write(byte)
                lastFour = (lastFour shl 8) or byte
                if (lastFour == 0x0D0A0D0A) break
            }
            return buffer.toString(StandardCharsets.UTF_8.name())
        }

        private fun readExactly(input: InputStream, count: Int): ByteArray {
            val bytes = ByteArray(count)
            var offset = 0
            while (offset < count) {
                val read = input.read(bytes, offset, count - offset)
                if (read < 0) break
                offset += read
            }
            return bytes.copyOf(offset)
        }
    }
}
