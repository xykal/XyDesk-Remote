package id.xydesk.remote.rdpfree

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RdpFreeStatusTest {
    @Test fun tokenFormat() {
        assertTrue(RdpFreeApi.TOKEN_PATTERN.matches("rdf_" + "a".repeat(43)))
        assertFalse(RdpFreeApi.TOKEN_PATTERN.matches("ghp_not_a_device_token"))
        assertFalse(RdpFreeApi.TOKEN_PATTERN.matches("rdf_short"))
    }
    private fun payload(active: Boolean = true, phase: String = "ready", port: Int = 3389): JSONObject =
        JSONObject().put("active", active).put("phase", phase).put("repo", "xykal/XyRDP")
            .put("run", JSONObject().put("id", 123))
            .put("connection", JSONObject().put("host", "rdp.example.com").put("port", port)
                .put("username", "xyadmin").put("transport", "tunnel"))
    @Test fun validConnection() {
        val result = RdpFreeStatus.parse(payload())
        assertEquals("rdp.example.com", result.connection?.host)
        assertEquals(3389, result.connection?.port)
        assertEquals(123L, result.runId)
    }
    @Test fun staleConnectionIsNotUsable() {
        assertNull(RdpFreeStatus.parse(payload(active = false)).connection)
        assertNull(RdpFreeStatus.parse(payload(phase = "preparing")).connection)
    }
    @Test fun invalidPortIsNotUsable() {
        assertNull(RdpFreeStatus.parse(payload(port = 0)).connection)
        assertNull(RdpFreeStatus.parse(payload(port = 65536)).connection)
    }
    @Test fun malformedHostIsNotUsable() {
        val json = payload()
        json.getJSONObject("connection").put("host", "https://bad.example")
        assertNull(RdpFreeStatus.parse(json).connection)
    }
}
