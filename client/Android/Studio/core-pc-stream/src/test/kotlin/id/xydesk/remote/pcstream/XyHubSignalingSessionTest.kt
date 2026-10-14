package id.xydesk.remote.pcstream

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Orkestrator sesi diuji dengan onboarding + transport palsu: urutan langkah
 * (guest → tiket → TURN opsional → /ws → pair) dan penanganan setiap jawaban
 * hub diperiksa langkah-demi-langkah.
 */
class XyHubSignalingSessionTest {
    private lateinit var onboarding: FakeOnboarding
    private lateinit var transport: FakeHubTransport
    private lateinit var listener: RecordingSessionListener
    private lateinit var session: XyHubSignalingSession

    private val deviceId = "xyr-0a1b2c3d4e5f"
    private val hostId = "123456789"

    @Before
    fun setUp() {
        onboarding = FakeOnboarding()
        transport = FakeHubTransport()
        listener = RecordingSessionListener()
        session = XyHubSignalingSession(onboarding, { transport }, listener, deviceId)
    }

    /** start + open + welcome — pair otomatis sudah terkirim, siap dijawab. */
    private fun mulaiSampaiPairing() = runBlocking {
        session.start(hostId, "428193", "Pixel 8")
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")
    }

    @Test
    fun jalurBahagiaOnboardingPairingAccepted() = mulaiSampaiPairingUnit {
        // Onboarding: guestLogin(refresh=null) → signalToken → turnIce → buildUrl.
        assertEquals(listOf<String?>(null), onboarding.guestLogins)
        assertEquals(listOf("jwt-baru" to deviceId), onboarding.signalTokens)
        assertEquals(listOf(deviceId to "v2.tiket"), onboarding.turnIceCalls)
        assertEquals("ws://fake/ws?id=$deviceId&role=client&token=v2.tiket", transport.connectedUrl)

        // hello + pair otomatis setelah welcome.
        val hello = JSONObject(transport.sent[0])
        assertEquals("hello", hello.getString("type"))
        val pair = JSONObject(transport.sent[1])
        assertEquals("pair", pair.getString("type"))
        assertEquals(hostId, pair.getString("to"))
        assertEquals("428193", pair.getString("pin"))
        assertEquals("Pixel 8", pair.getString("name"))
        assertEquals("android", pair.getString("platform"))

        assertEquals(XyHubSessionState.PAIRING, session.state)

        transport.simulateText("""{"type":"pair-response","from":"$hostId","accepted":true}""")

        assertEquals(XyHubSessionState.PAIRED, session.state)
        assertEquals(listOf(hostId to onboarding.iceServers), listener.accepted)
        val stateNames = listener.states.map { it.first }
        assertEquals(
            listOf(
                XyHubSessionState.AUTHENTICATING,
                XyHubSessionState.CONNECTING,
                XyHubSessionState.REGISTERED,
                XyHubSessionState.PAIRING,
                XyHubSessionState.PAIRED,
            ),
            stateNames,
        )
    }

    @Test
    fun refreshTokenDipakaiDanYangBaruTersimpan() = runBlocking {
        session.start(hostId, "428193", null, refreshToken = "r0-lama")
        transport.simulateOpen()

        assertEquals(listOf("r0-lama"), onboarding.guestLogins)
        assertEquals("refresh-baru", session.guestRefreshToken)
    }

    @Test
    fun pinSalahBisaUlangiTanpaOnboardingUlang() = mulaiSampaiPairingUnit {
        transport.simulateText("""{"type":"pair-response","from":"$hostId","accepted":false,"reason":"PIN salah"}""")

        assertEquals(XyHubSessionState.REGISTERED, session.state)
        assertEquals(listOf("PIN salah"), listener.rejected)

        session.pair(hostId, "111222", "Pixel 8")
        val ulang = JSONObject(transport.sent.last())
        assertEquals("pair", ulang.getString("type"))
        assertEquals("111222", ulang.getString("pin"))
        assertEquals(XyHubSessionState.PAIRING, session.state)
        // Onboarding tidak disentuh ulang.
        assertEquals(1, onboarding.guestLogins.size)
    }

    @Test
    fun remPenebakDilaporkanDenganSisaWaktu() = mulaiSampaiPairingUnit {
        transport.simulateText("""{"type":"error","error":"pair-terkunci","reason":"client","retry_in":300}""")

        assertEquals(listOf(300L), listener.blocked)
        assertEquals(XyHubSessionState.REGISTERED, session.state)
    }

    @Test
    fun hostOfflineDitolakTanpaMenutupSesi() = mulaiSampaiPairingUnit {
        transport.simulateText("""{"type":"error","error":"peer-offline","reason":"$hostId"}""")

        assertEquals(listOf(hostId), listener.rejected)
        assertEquals(XyHubSessionState.REGISTERED, session.state)
    }

    @Test
    fun onboardingGagalTanpaMenyentuhTransport() = runBlocking {
        onboarding.failGuestLogin = IOException("jaringan hilang")
        session.start(hostId, "428193", null)

        assertEquals(XyHubSessionState.FAILED, session.state)
        assertEquals(1, listener.failures.size)
        assertEquals(0, transport.connectCount)
    }

    @Test
    fun turnIce403LanjutDenganStunSaja() = runBlocking {
        onboarding.failTurnIce = XyHubHttpException(403, "forbidden", null)
        session.start(hostId, "428193", null)
        transport.simulateOpen()
        transport.simulateText("""{"type":"welcome","from":"$deviceId"}""")
        transport.simulateText("""{"type":"pair-response","from":"$hostId","accepted":true}""")

        assertEquals(XyHubSessionState.PAIRED, session.state)
        assertEquals(listOf(hostId to emptyList<XyHubIceServer>()), listener.accepted)
        // Detail "turn-unavailable" pernah tercatat.
        assertTrue(listener.states.any { it.second == "turn-unavailable" })
    }

    @Test
    fun pesanMediaHostDiteruskanDanByeMenutup() = mulaiSampaiPairingUnit {
        transport.simulateText("""{"type":"pair-response","from":"$hostId","accepted":true}""")

        transport.simulateText("""{"type":"answer","from":"$hostId","sdp":{"type":"answer","sdp":"v=0"}}""")
        transport.simulateText(
            """{"type":"ice","from":"$hostId","candidate":{"candidate":"candidate:1","sdpMid":"0","sdpMLineIndex":0}}""",
        )

        assertEquals(listOf("answer" to "v=0"), listener.answers)
        assertEquals(listOf(Triple("candidate:1", "0", 0)), listener.ices)

        transport.simulateText("""{"type":"bye","from":"$hostId"}""")

        assertEquals(1, listener.hostByes)
        assertEquals(XyHubSessionState.CLOSED, session.state)
        assertEquals(1000, transport.closeCode)
        assertEquals("host-bye", transport.closeReason)
    }

    @Test
    fun startDuaKaliDitolak() = mulaiSampaiPairingUnit {
        assertThrows(IllegalStateException::class.java) {
            runBlocking { session.start(hostId, "428193", null) }
        }
    }

    @Test
    fun stopMenutupTransportDenganKodeNormal() = mulaiSampaiPairingUnit {
        session.stop()

        assertEquals(XyHubSessionState.CLOSED, session.state)
        assertEquals(1000, transport.closeCode)
        assertEquals("selesai", transport.closeReason)
    }

    /** Corong untuk test yang butuh keadaan setelah start (pair menunggu jawaban). */
    private fun mulaiSampaiPairingUnit(block: () -> Unit) {
        mulaiSampaiPairing()
        block()
    }

    private class FakeOnboarding : XyHubOnboarding {
        val guestLogins = CopyOnWriteArrayList<String?>()
        val signalTokens = CopyOnWriteArrayList<Pair<String, String>>()
        val turnIceCalls = CopyOnWriteArrayList<Pair<String, String>>()
        val iceServers = listOf(XyHubIceServer(listOf("turn:turn.example.com:3478"), "u", "p"))

        @Volatile var failGuestLogin: Exception? = null

        @Volatile var failTurnIce: XyHubHttpException? = null

        override suspend fun guestLogin(refreshToken: String?): XyHubGuestSession {
            failGuestLogin?.let { throw it }
            guestLogins += refreshToken
            return XyHubGuestSession(token = "jwt-baru", refreshToken = "refresh-baru", issuedAtEpochMs = 0L)
        }

        override suspend fun signalToken(sessionToken: String, deviceId: String): String {
            signalTokens += sessionToken to deviceId
            return "v2.tiket"
        }

        override suspend fun turnIceServers(deviceId: String, ticket: String): List<XyHubIceServer> {
            failTurnIce?.let { throw it }
            turnIceCalls += deviceId to ticket
            return iceServers
        }

        override fun buildWebSocketUrl(deviceId: String, ticket: String): String =
            "ws://fake/ws?id=$deviceId&role=client&token=$ticket"
    }

    private class FakeHubTransport : XyHubTransport {
        val sent = CopyOnWriteArrayList<String>()
        var closeCode: Int? = null
            private set

        var closeReason: String? = null
            private set

        var connectedUrl: String? = null
            private set

        var connectCount = 0
            private set

        private var listener: XyHubTransport.Listener? = null

        override fun connect(url: String, listener: XyHubTransport.Listener) {
            connectedUrl = url
            connectCount += 1
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
    }

    private class RecordingSessionListener : XyHubSignalingSession.Listener {
        val states = CopyOnWriteArrayList<Pair<XyHubSessionState, String?>>()
        val accepted = CopyOnWriteArrayList<Pair<String, List<XyHubIceServer>>>()
        val rejected = CopyOnWriteArrayList<String?>()
        val blocked = CopyOnWriteArrayList<Long>()
        val answers = CopyOnWriteArrayList<Pair<String, String>>()
        val ices = CopyOnWriteArrayList<Triple<String, String?, Int?>>()
        val hostByes = CopyOnWriteArrayList<String>()
        val failures = CopyOnWriteArrayList<Throwable>()
        val closed = CopyOnWriteArrayList<Pair<Int, String>>()

        override fun onSessionState(state: XyHubSessionState, detail: String?) {
            states += state to detail
        }

        override fun onPairAccepted(hostId: String, iceServers: List<XyHubIceServer>) {
            accepted += hostId to iceServers
        }

        override fun onPairRejected(reason: String?) {
            rejected += reason
        }

        override fun onPairBlocked(retryInSeconds: Long) {
            blocked += retryInSeconds
        }

        override fun onRemoteAnswer(sdpType: String, sdp: String) {
            answers += sdpType to sdp
        }

        override fun onRemoteIce(candidate: String, sdpMid: String?, sdpMLineIndex: Int?) {
            ices += Triple(candidate, sdpMid, sdpMLineIndex)
        }

        override fun onHostBye() {
            hostByes += "bye"
        }

        override fun onSignalingFailure(error: Throwable) {
            failures += error
        }

        override fun onSignalingClosed(code: Int, reason: String) {
            closed += code to reason
        }
    }
}
