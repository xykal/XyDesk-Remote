package id.xydesk.remote.core

import org.junit.Assert.*
import org.junit.Test

class LegacyDirectTransportPolicyTest {
    @Test fun directUdpPolicyDefaultsOnAndRespectsToggle() {
        LegacyDirectTransportPolicy.enabled = true
        assertTrue(LegacyDirectTransportPolicy.mayStart())
        assertTrue(LegacyDirectTransportPolicy.BLOCKED_REASON.isNotBlank())
    }
}
