package id.xydesk.remote.ui.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XyRdpParseTest {
    @Test
    fun `tunnel ip tanpa tailscale terbaca`() {
        val json = """
        {"akses":{"mode":"keduanya",
          "tunnel":{"status":"ok","provider":"bore","host":"bore.pub","port":12345,
                    "address":"bore.pub:12345","selftest":"ok","outside":"ok (asia)"},
          "tailscale":{"ip":""},
          "rustdesk":{"id":"147088785"}}}
        """.trimIndent()
        val a = XyRdpClient.parseAccess(json)!!
        assertEquals("bore.pub:12345", a.tunnelAddress)
        assertEquals(12345, a.tunnelPort)
        assertEquals("ok", a.selftest)
        assertEquals("147088785", a.rustdeskId)
        assertEquals("", a.tailscaleIp)
    }

    @Test
    fun `skema tailscale v5 tanpa objek tunnel otomatis mengisi host dan port 3389`() {
        val json = """
        {"run_id":"777","run_attempt":"1","active":true,"expires_at":"2099-01-01T00:00:00Z",
         "rdp_user":"xyadmin",
         "akses":{"mode":"tailscale","tailscale":{"status":"ok","ip":"100.88.12.34"}}}
        """.trimIndent()
        val a = XyRdpClient.parseAccess(json)!!
        assertEquals("100.88.12.34", a.tunnelHost)
        assertEquals(3389, a.tunnelPort)
        assertEquals("100.88.12.34:3389", a.tunnelAddress)

        val run = XyRdpRun(id = 777L, number = 12L, attempt = 1, status = "in_progress", conclusion = null, url = "")
        val state = XyRdpClient.evaluateSession(run, a, null, nowMillis = 1_700_000_000_000L)
        assertEquals(XyRdpPhase.READY, state.phase)
        assertTrue(state.isVerifiedLive)
        assertEquals("100.88.12.34", state.validatedHost)
        assertEquals(3389, state.validatedPort)
    }

    @Test
    fun `skema portmap v5 tervalidasi dan menolak run_id usang dari sesi lama`() {
        val json = """
        {"run_id":"888","run_attempt":"1","active":true,"expires_at":"2099-01-01T00:00:00Z",
         "rdp_user":"xyadmin",
         "akses":{"mode":"portmap","portmap":{"status":"ok","host":"xykal.portmap.io","port":41858}}}
        """.trimIndent()
        val a = XyRdpClient.parseAccess(json)!!
        assertEquals("xykal.portmap.io", a.tunnelHost)
        assertEquals(41858, a.tunnelPort)

        val matchingRun = XyRdpRun(id = 888L, number = 15L, attempt = 1, status = "in_progress", conclusion = null, url = "")
        val okState = XyRdpClient.evaluateSession(matchingRun, a, null, nowMillis = 1_700_000_000_000L)
        assertEquals(XyRdpPhase.READY, okState.phase)
        assertEquals("xykal.portmap.io", okState.validatedHost)
        assertEquals(41858, okState.validatedPort)

        // Jika run baru (#889) sedang menyiapkan sesi, file status lama (#888) tidak boleh dianggap READY
        val newerRun = XyRdpRun(id = 889L, number = 16L, attempt = 1, status = "in_progress", conclusion = null, url = "")
        val staleState = XyRdpClient.evaluateSession(newerRun, a, null, nowMillis = 1_700_000_000_000L)
        assertEquals(XyRdpPhase.PREPARING, staleState.phase)
        assertFalse(staleState.isVerifiedLive)
        assertNull(staleState.validatedHost)
    }

    @Test
    fun `tanpa blok akses menghasilkan null`() {
        assertNull(XyRdpClient.parseAccess("{}"))
        assertNull(XyRdpClient.parseAccess("bukan json"))
    }

    @Test
    fun `field hilang tidak melempar`() {
        val a = XyRdpClient.parseAccess("""{"akses":{"mode":"tunnel"}}""")!!
        assertEquals("tunnel", a.mode)
        assertEquals("", a.tunnelAddress)
        assertEquals(0, a.tunnelPort)
    }
}
