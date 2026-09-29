package id.xydesk.remote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RdpEndpointTest {
    @Test
    fun parsesHostWithDefaultAndOptionalPort() {
        assertEquals(RdpEndpoint("rdp.example"), parseRdpEndpoint("rdp.example"))
        assertEquals(RdpEndpoint("192.0.2.4", 3390), parseRdpEndpoint("192.0.2.4:3390"))
        assertEquals(RdpEndpoint("rdp.example", 3390), parseRdpEndpoint(" rdp.example:3390 "))
    }

    @Test
    fun parsesIpv6WithDefaultOrBracketedPort() {
        assertEquals(RdpEndpoint("2001:db8::1"), parseRdpEndpoint("2001:db8::1"))
        assertEquals(RdpEndpoint("2001:db8::1", 3390), parseRdpEndpoint("[2001:db8::1]:3390"))
    }

    @Test
    fun rejectsInvalidPortSyntax() {
        assertNull(parseRdpEndpoint("rdp.example:"))
        assertNull(parseRdpEndpoint("rdp.example:abc"))
        assertNull(parseRdpEndpoint("rdp.example:0"))
        assertNull(parseRdpEndpoint("rdp.example:65536"))
        assertNull(parseRdpEndpoint("[2001:db8::1]:abc"))
        assertNull(parseRdpEndpoint(""))
    }

    @Test
    fun formatsExistingSavedPortWithoutChangingDefault() {
        assertEquals("rdp.example", formatRdpEndpoint("rdp.example", 3389))
        assertEquals("rdp.example:3390", formatRdpEndpoint("rdp.example", 3390))
        assertEquals("[2001:db8::1]:3390", formatRdpEndpoint("2001:db8::1", 3390))
    }
}
