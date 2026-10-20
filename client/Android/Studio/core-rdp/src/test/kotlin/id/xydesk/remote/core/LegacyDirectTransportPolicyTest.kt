package id.xydesk.remote.core

import org.junit.Assert.*
import org.junit.Test

class LegacyDirectTransportPolicyTest {
    @Test fun rawUdpCannotBeEnabledInReleaseCandidate() {
        assertFalse(LegacyDirectTransportPolicy.mayStart())
        assertTrue(LegacyDirectTransportPolicy.BLOCKED_REASON.isNotBlank())
    }
}
