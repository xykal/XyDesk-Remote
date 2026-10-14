package id.xydesk.remote.pcstream

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vektor emas protokol hub — string kawat persis seperti yang dipancarkan /
 * diterima klien referensi XyDesk (`net/Signaling.kt`) dan hub produksi
 * (`cloudflare/src/hub.js`).
 *
 * Assertion dilakukan lewat parse-ulang (bukan perbandingan string mentah)
 * karena urutan kunci JSON tidak dijamin lintas implementasi org.json; satu
 * pengecualian: pesan satu-field (`ping`/`pong`/`list`) yang deterministik.
 */
class XyHubMessagesTest {

    // ── Outbound: format kawat ──────────────────────────────────────────────

    @Test
    fun helloMengikutiKlienReferensi() {
        val encoded = XyHubOutbound.Hello("xyr-0a1b2c3d4e5f").encode()
        val json = JSONObject(encoded)
        assertEquals("hello", json.getString("type"))
        assertEquals("xyr-0a1b2c3d4e5f", json.getString("to"))
        assertEquals("client", json.getString("reason"))
        assertEquals(3, json.length())
    }

    @Test
    fun pairMembawaPinNamaDanPlatform() {
        val encoded = XyHubOutbound.Pair("123456789", "428193", "Pixel 8").encode()
        val json = JSONObject(encoded)
        assertEquals("pair", json.getString("type"))
        assertEquals("123456789", json.getString("to"))
        assertEquals("428193", json.getString("pin"))
        assertEquals("Pixel 8", json.getString("name"))
        assertEquals("android", json.getString("platform"))
    }

    @Test
    fun pairTanpaNamaMenghilangkanField() {
        val json = JSONObject(XyHubOutbound.Pair("123456789", "428193", null).encode())
        assertEquals("pair", json.getString("type"))
        assertFalse(json.has("name"))
        assertEquals("android", json.getString("platform"))
    }

    @Test
    fun offerMembungkusSdpBersarang() {
        val sdp = "v=0\r\no=- 46117317 2 IN IP4 127.0.0.1\r\n"
        val json = JSONObject(XyHubOutbound.Offer("123456789", "offer", sdp).encode())
        assertEquals("offer", json.getString("type"))
        assertEquals("123456789", json.getString("to"))
        val nested = json.getJSONObject("sdp")
        assertEquals("offer", nested.getString("type"))
        assertEquals(sdp, nested.getString("sdp"))
        assertEquals(3, json.length())
    }

    @Test
    fun iceMembungkusKandidatBersarang() {
        val encoded = XyHubOutbound.Ice(
            hostId = "123456789",
            candidate = "candidate:1 1 UDP 2122252543 192.168.1.5 54321 typ host",
            sdpMid = "0",
            sdpMLineIndex = 0,
        ).encode()
        val json = JSONObject(encoded)
        assertEquals("ice", json.getString("type"))
        assertEquals("123456789", json.getString("to"))
        val nested = json.getJSONObject("candidate")
        assertEquals("candidate:1 1 UDP 2122252543 192.168.1.5 54321 typ host", nested.getString("candidate"))
        assertEquals("0", nested.getString("sdpMid"))
        assertEquals(0, nested.getInt("sdpMLineIndex"))
    }

    @Test
    fun iceTanpaMidMenghilangkanFieldOpsional() {
        val json = JSONObject(
            XyHubOutbound.Ice("123456789", "", null, -1).encode(),
        )
        val nested = json.getJSONObject("candidate")
        assertEquals("", nested.getString("candidate"))
        assertFalse(nested.has("sdpMid"))
        assertFalse(nested.has("sdpMLineIndex"))
    }

    @Test
    fun byeListPingPongPesanMinimal() {
        val bye = JSONObject(XyHubOutbound.Bye("123456789").encode())
        assertEquals("bye", bye.getString("type"))
        assertEquals("123456789", bye.getString("to"))
        assertEquals(2, bye.length())

        // Tiga pesan satu-field: deterministik, aman dibandingkan persis.
        assertEquals("""{"type":"list"}""", XyHubOutbound.ListDevices.encode())
        assertEquals("""{"type":"ping"}""", XyHubOutbound.Ping.encode())
        assertEquals("""{"type":"pong"}""", XyHubOutbound.Pong.encode())
    }

    @Test
    fun presenceMembawaDaftarId() {
        val json = JSONObject(XyHubOutbound.Presence(listOf("123456789", "987654321")).encode())
        assertEquals("presence", json.getString("type"))
        val ids = json.getJSONArray("ids")
        assertEquals(2, ids.length())
        assertEquals("123456789", ids.getString(0))
        assertEquals("987654321", ids.getString(1))
    }

    @Test
    fun namaTeracakTetapSelamatBolakBalik() {
        val tricky = """He said "hi" \ / 😀 — <script>"""
        val json = JSONObject(XyHubOutbound.Pair("123456789", "111111", tricky).encode())
        assertEquals(tricky, json.getString("name"))
    }

    // ── Inbound: parse vektor nyata dari hub ────────────────────────────────

    @Test
    fun parseWelcome() {
        assertEquals(
            XyHubInbound.Welcome("123456789"),
            XyHubProtocol.parseInbound("""{"type":"welcome","from":"123456789"}"""),
        )
    }

    @Test
    fun parsePairResponseDiterimaDanDitolak() {
        assertEquals(
            XyHubInbound.PairResponse("123456789", true, null),
            XyHubProtocol.parseInbound("""{"type":"pair-response","from":"123456789","accepted":true}"""),
        )
        assertEquals(
            XyHubInbound.PairResponse("123456789", false, "PIN salah"),
            XyHubProtocol.parseInbound("""{"type":"pair-response","from":"123456789","accepted":false,"reason":"PIN salah"}"""),
        )
    }

    @Test
    fun parseAnswerDenganSdpBersarang() {
        val sdp = "v=0\r\no=- 46117317 2 IN IP4 127.0.0.1\r\ns=-\r\n"
        assertEquals(
            XyHubInbound.Answer("123456789", "answer", sdp),
            XyHubProtocol.parseInbound(
                """{"type":"answer","from":"123456789","sdp":{"type":"answer","sdp":"${sdp.replace("\r\n", "\\r\\n")}"}}""",
            ),
        )
    }

    @Test
    fun parseAnswerTanpaTypeSdpDefaultAnswer() {
        assertEquals(
            XyHubInbound.Answer("123456789", "answer", "v=0"),
            XyHubProtocol.parseInbound("""{"type":"answer","from":"123456789","sdp":{"sdp":"v=0"}}"""),
        )
    }

    @Test
    fun parseIceDenganDanTanpaMid() {
        assertEquals(
            XyHubInbound.IceCandidate("123456789", "candidate:842163049 1 udp 1677729535 192.168.1.5 54321 typ srflx", "0", 0),
            XyHubProtocol.parseInbound(
                """{"type":"ice","from":"123456789","candidate":{"candidate":"candidate:842163049 1 udp 1677729535 192.168.1.5 54321 typ srflx","sdpMid":"0","sdpMLineIndex":0}}""",
            ),
        )
        assertEquals(
            XyHubInbound.IceCandidate("123456789", "", null, null),
            XyHubProtocol.parseInbound("""{"type":"ice","from":"123456789","candidate":{"candidate":""}}"""),
        )
    }

    @Test
    fun parseByePingPong() {
        assertEquals(XyHubInbound.Bye("123456789"), XyHubProtocol.parseInbound("""{"type":"bye","from":"123456789"}"""))
        assertEquals(XyHubInbound.Ping, XyHubProtocol.parseInbound("""{"type":"ping"}"""))
        assertEquals(XyHubInbound.Pong, XyHubProtocol.parseInbound("""{"type":"pong"}"""))
    }

    @Test
    fun parseErrorHubDenganRetryIn() {
        assertEquals(
            XyHubInbound.HubError("peer-offline", "123456789", null),
            XyHubProtocol.parseInbound("""{"type":"error","error":"peer-offline","reason":"123456789"}"""),
        )
        // Rem anti-penebak (hub.js): client terkunci, sisa waktu ikut dibawa.
        assertEquals(
            XyHubInbound.HubError("pair-terkunci", "client", 300),
            XyHubProtocol.parseInbound("""{"type":"error","error":"pair-terkunci","reason":"client","retry_in":300}"""),
        )
    }

    @Test
    fun parsePresenceDanDevices() {
        assertEquals(
            XyHubInbound.PresenceOnline(listOf("123456789")),
            XyHubProtocol.parseInbound("""{"type":"presence","online":["123456789"]}"""),
        )
        assertEquals(
            XyHubInbound.Devices(emptyList()),
            XyHubProtocol.parseInbound("""{"type":"devices","devices":[]}"""),
        )
    }

    @Test
    fun parseTipeMasaDepanMenjadiUnknown() {
        assertEquals(
            XyHubInbound.Unknown("future-thing"),
            XyHubProtocol.parseInbound("""{"type":"future-thing","payload":1}"""),
        )
    }

    @Test
    fun kawatRusakMenghasilkanNull() {
        assertNull(XyHubProtocol.parseInbound("bukan json"))
        assertNull(XyHubProtocol.parseInbound("[1,2,3]"))
        assertNull(XyHubProtocol.parseInbound("""{"nope":1}"""))
        assertNull(XyHubProtocol.parseInbound("null"))
        assertNull(XyHubProtocol.parseInbound(""))
        // Field wajib hilang → rusak, bukan crash thread transport.
        assertNull(XyHubProtocol.parseInbound("""{"type":"welcome"}"""))
        assertNull(XyHubProtocol.parseInbound("""{"type":"error"}"""))
        // Payload bersarang hilang → tipe tidak bisa dipercaya.
        assertNull(XyHubProtocol.parseInbound("""{"type":"answer","from":"123456789"}"""))
        assertNull(XyHubProtocol.parseInbound("""{"type":"ice","from":"123456789"}"""))
    }

    // ── ID perangkat ────────────────────────────────────────────────────────

    @Test
    fun normalizeMembuangSpasiDanTandaHubung() {
        assertEquals("123456789", XyHubIds.normalize(" 123-456 789 "))
        assertEquals("xyrabc", XyHubIds.normalize("xyr-abc"))
    }

    @Test
    fun validasiIdHostHarusSembilanDigit() {
        assertEquals("123456789", XyHubIds.requireHostId("123456789"))
        assertThrows(IllegalArgumentException::class.java) { XyHubIds.requireHostId("12345") }
        assertThrows(IllegalArgumentException::class.java) { XyHubIds.requireHostId("12345678a") }
    }

    @Test
    fun validasiIdClientMengikutiWorker() {
        assertEquals("xyr-0a1b2c3d4e5f", XyHubIds.requireClientId("xyr-0a1b2c3d4e5f"))
        assertThrows(IllegalArgumentException::class.java) { XyHubIds.requireClientId("ab") }
        assertThrows(IllegalArgumentException::class.java) { XyHubIds.requireClientId("ada spasi") }
        assertThrows(IllegalArgumentException::class.java) { XyHubIds.requireClientId("x".repeat(65)) }
    }

    @Test
    fun clientIdPerInstallUnikDanBerformat() {
        val first = XyHubIds.generateClientId()
        val second = XyHubIds.generateClientId()
        assertTrue(Regex("^xyr-[0-9a-f]{12}$").matches(first))
        assertNotEquals(first, second)
        assertEquals(first, XyHubIds.requireClientId(first))
    }
}
