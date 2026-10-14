package id.xydesk.remote.pcstream

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Mesin status klien hub diuji dengan transport palsu: frame dikendalikan
 * test satu-per-satu, semua kiriman dibaca kembali sebagai JSON. Urutan
 * hello → welcome → relay dipaksa supaya pelanggaran urutan tertangkap.
 */
class XyHubSignalClientTest {
    private lateinit var transport: FakeTransport
    private lateinit var listener: RecordingListener
    private lateinit var client: XyHubSignalClient

    private val deviceId = "xyr-0a1b2c3d4e5f"
    private val hostId = "123456789"

    @Before
    fun setUp() {
        transport = FakeTransport()
        listener = RecordingListener()
        client = XyHubSignalClient(deviceId, transport, listener)
    }

    @Test
    fun siklusPenuhHelloWelcomePairOfferIceBye() {
        assertEquals(XyHubConnectionState.IDLE, client.state)

        client.connect("wss://signal.xydesk.my.id/ws?id=$deviceId&role=client&token=v2.t")
        assertEquals("wss://signal.xydesk.my.id/ws?id=$deviceId&role=client&token=v2.t", transport.connectedUrl)

        transport.simulateOpen()
        assertEquals(XyHubConnectionState.CONNECTING, client.state)
        val hello = JSONObject(transport.sent.single())
        assertEquals("hello", hello.getString("type"))
        assertEquals(deviceId, hello.getString("to"))
        assertEquals("client", hello.getString("reason"))

        // Sebelum welcome, relay ditolak hub ("hello wajib sebelum relay").
        assertThrows(IllegalStateException::class.java) {
            client.pair(hostId, "428193", "Pixel 8")
        }

        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")
        assertEquals(XyHubConnectionState.REGISTERED, client.state)
        assertEquals(listOf(deviceId), listener.registered)

        client.pair(hostId, "428193", "Pixel 8")
        client.offer(hostId, "offer", "v=0\r\ns=-")
        client.ice(hostId, "candidate:1 1 UDP 1 192.168.1.5 54321 typ host", "0", 0)
        client.presence(listOf(hostId))
        client.requestDeviceList()
        client.ping()
        client.bye(hostId)

        val byType = transport.sent.drop(1).map { JSONObject(it).getString("type") }
        assertEquals(
            listOf("pair", "offer", "ice", "presence", "list", "ping", "bye"),
            byType,
        )
        val pair = JSONObject(transport.sent[1])
        assertEquals("123456789", pair.getString("to"))
        assertEquals("428193", pair.getString("pin"))
        assertEquals("Pixel 8", pair.getString("name"))
        assertEquals("android", pair.getString("platform"))
    }

    @Test
    fun balasPingAplikasiOtomatis() {
        client.connect("wss://example/ws")
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")

        transport.sent.clear()
        transport.simulateText("""{"type":"ping"}""")

        assertEquals("""{"type":"pong"}""", transport.sent.single())
        // Ping ditangani internal — tidak diteruskan sebagai pesan UI.
        assertTrue(listener.messages.isEmpty())
    }

    @Test
    fun pesanMasukDiteruskanTerketik() {
        client.connect("wss://example/ws")
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")

        transport.simulateText("""{"type":"pair-response","from":"$hostId","accepted":true}""")
        transport.simulateText("""{"type":"answer","from":"$hostId","sdp":{"type":"answer","sdp":"v=0"}}""")
        transport.simulateText(
            """{"type":"ice","from":"$hostId","candidate":{"candidate":"candidate:1","sdpMid":"0","sdpMLineIndex":0}}""",
        )
        transport.simulateText("""{"type":"bye","from":"$hostId"}""")
        transport.simulateText("""{"type":"error","error":"peer-offline","reason":"$hostId"}""")

        assertEquals(
            listOf(
                XyHubInbound.PairResponse(hostId, true, null),
                XyHubInbound.Answer(hostId, "answer", "v=0"),
                XyHubInbound.IceCandidate(hostId, "candidate:1", "0", 0),
                XyHubInbound.Bye(hostId),
                XyHubInbound.HubError("peer-offline", hostId, null),
            ),
            listener.messages,
        )
    }

    @Test
    fun tipeMasaDepanDiteruskanSebagaiUnknown() {
        client.connect("wss://example/ws")
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")

        transport.simulateText("""{"type":"future-thing"}""")

        assertEquals(listOf<XyHubInbound>(XyHubInbound.Unknown("future-thing")), listener.messages)
    }

    @Test
    fun frameRusakTidakFatal() {
        client.connect("wss://example/ws")
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")

        transport.simulateText("bukan json")
        transport.simulateText("""{"type":"welcome"}""")

        assertEquals(2, listener.malformed.size)
        assertEquals(XyHubConnectionState.REGISTERED, client.state)
        client.ping() // koneksi tetap hidup
        assertEquals("ping", JSONObject(transport.sent.last()).getString("type"))
    }

    @Test
    fun tutupNormalMenggunakanKodeSeribu() {
        client.connect("wss://example/ws")
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")

        client.close()

        assertEquals(1000, transport.closeCode)
        assertEquals("selesai", transport.closeReason)
        assertEquals(XyHubConnectionState.CLOSED, client.state)

        transport.simulateClosed(1000, "selesai")
        assertEquals(listOf(1000 to "selesai"), listener.closed)
    }

    @Test
    fun kegagalanTransportMenjadiFailure() {
        client.connect("wss://example/ws")
        transport.simulateOpen()

        val error = java.io.IOException("jaringan hilang")
        transport.simulateFailure(error)

        assertEquals(listOf<Throwable>(error), listener.failures)
        assertEquals(XyHubConnectionState.FAILED, client.state)
    }

    @Test
    fun relaySebelumConnectDitolak() {
        assertThrows(IllegalStateException::class.java) {
            client.pair(hostId, "428193")
        }
    }

    @Test
    fun connectKeduaDitolak() {
        client.connect("wss://satu/ws")
        assertThrows(IllegalStateException::class.java) {
            client.connect("wss://dua/ws")
        }
    }

    @Test
    fun presenceDipotongBatasHub() {
        client.connect("wss://example/ws")
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")

        val banyak = (1..60).map { "%09d".format(it) }
        client.presence(banyak)

        val sent = transport.sent.last()
        val ids = JSONObject(sent).getJSONArray("ids")
        assertEquals(XyHubProtocol.PRESENCE_LIMIT, ids.length())
        assertEquals("000000001", ids.getString(0))
    }

    @Test
    fun hostIdDivalidasiSembilanDigit() {
        client.connect("wss://example/ws")
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")

        assertThrows(IllegalArgumentException::class.java) {
            client.pair("12345", "111111")
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.offer("12345678a", "offer", "v=0")
        }
    }

    private class RecordingListener : XyHubSignalClient.Listener {
        val registered = CopyOnWriteArrayList<String>()
        val messages = CopyOnWriteArrayList<XyHubInbound>()
        val malformed = CopyOnWriteArrayList<String>()
        val closed = CopyOnWriteArrayList<Pair<Int, String>>()
        val failures = CopyOnWriteArrayList<Throwable>()

        override fun onHubRegistered(deviceId: String) {
            registered += deviceId
        }

        override fun onHubMessage(message: XyHubInbound) {
            messages += message
        }

        override fun onHubMalformed(text: String) {
            malformed += text
        }

        override fun onHubClosed(code: Int, reason: String) {
            closed += code to reason
        }

        override fun onHubFailure(error: Throwable) {
            failures += error
        }
    }

    private class FakeTransport : XyHubTransport {
        val sent = CopyOnWriteArrayList<String>()
        var closeCode: Int? = null
            private set
        var closeReason: String? = null
            private set
        var connectedUrl: String? = null
            private set
        private var listener: XyHubTransport.Listener? = null

        override fun connect(url: String, listener: XyHubTransport.Listener) {
            connectedUrl = url
            this.listener = listener
        }

        override fun send(text: String): Boolean {
            sent += text
            return true
        }

        override fun close(code: Int, reason: String): Boolean {
            closeCode = code
            closeReason = reason
            return true
        }

        fun simulateOpen() = requireNotNull(listener).onOpen()
        fun simulateText(text: String) = requireNotNull(listener).onMessage(text)
        fun simulateClosed(code: Int, reason: String) = requireNotNull(listener).onClosed(code, reason)
        fun simulateFailure(error: Throwable) = requireNotNull(listener).onFailure(error)
    }
}
