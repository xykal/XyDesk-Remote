package id.xydesk.remote.pcstream

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

class PcSignalClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: PcSignalClient
    private val observedAuthorization = CopyOnWriteArrayList<String>()
    private val observedSignalBodies = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/pc/rooms") { exchange ->
            if (exchange.requestMethod != "POST" || exchange.requestURI.path != "/api/pc/rooms") {
                respond(exchange, 404, "{\"status\":\"not_found\"}")
            } else {
                respond(
                    exchange,
                    201,
                    """{"room_id":"$ROOM_ID","host_token":"$HOST_TOKEN","pairing_code":"$PAIRING_CODE","expires_at":1735689600000}""",
                )
            }
        }
        server.createContext("/api/pc/rooms/$ROOM_ID/join") { exchange ->
            observedSignalBodies += String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
            respond(
                exchange,
                200,
                """{"status":"ok","viewer_token":"$VIEWER_TOKEN","expires_at":1735690000000}""",
            )
        }
        server.createContext("/api/pc/rooms/$ROOM_ID/signal") { exchange ->
            observedAuthorization += exchange.requestHeaders.getFirst("Authorization") ?: ""
            if (exchange.requestMethod == "POST") {
                observedSignalBodies += String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
                respond(exchange, 201, """{"status":"ok","seq":7}""")
            } else {
                respond(
                    exchange,
                    200,
                    """{"status":"ok","role":"viewer","events":[{"seq":7,"from":"host","type":"offer","payload":{"sdp":"v=0"},"created_at":1735689500000}],"cursor":7,"latest_seq":7}""",
                )
            }
        }
        server.start()
        client = PcSignalClient("http://127.0.0.1:${server.address.port}")
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun createsJoinsPostsAndPollsWithoutLeakingSecretsInDiagnostics() = runBlocking {
        val host = client.createRoom()
        assertEquals(ROOM_ID, host.credentials.roomId)
        assertEquals(HOST_TOKEN, host.credentials.bearerToken)
        assertEquals(PAIRING_CODE, host.pairingCode)
        assertFalse(host.toString().contains(PAIRING_CODE))
        assertFalse(host.toString().contains(HOST_TOKEN))

        val viewer = client.joinRoom(ROOM_ID, PAIRING_CODE)
        assertEquals(PcSignalRole.VIEWER, viewer.role)
        assertEquals(VIEWER_TOKEN, viewer.bearerToken)

        val sequence = client.postSignal(
            host.credentials,
            "offer",
            JSONObject().put("sdp", "v=0"),
        )
        assertEquals(7L, sequence)

        val batch = client.pollSignals(viewer)
        assertEquals(7L, batch.cursor)
        assertEquals(7L, batch.latestSequence)
        assertEquals(1, batch.events.size)
        assertEquals("offer", batch.events.single().type)
        assertEquals("v=0", batch.events.single().payload?.getString("sdp"))
        assertEquals("Bearer $HOST_TOKEN", observedAuthorization[0])
        assertEquals("Bearer $VIEWER_TOKEN", observedAuthorization[1])
        assertEquals(PAIRING_CODE, JSONObject(observedSignalBodies[0]).getString("pairing_code"))
        assertEquals("offer", JSONObject(observedSignalBodies[1]).getString("type"))
    }

    @Test
    fun acceptsCleartextOnlyForLoopbackAndRejectsMalformedIds() {
        assertThrows(IllegalArgumentException::class.java) {
            PcSignalClient("http://example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.joinRoom("not-a-room", PAIRING_CODE) }
        }
    }

    private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    companion object {
        private const val ROOM_ID = "00000000-0000-4000-8000-000000000001"
        private const val HOST_TOKEN = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        private const val VIEWER_TOKEN = "ccccccccccccccccccccccccccccccccccccccccccc"
        private const val PAIRING_CODE = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
