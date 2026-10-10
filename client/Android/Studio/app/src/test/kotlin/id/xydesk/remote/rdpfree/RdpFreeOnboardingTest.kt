package id.xydesk.remote.rdpfree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class RdpFreeOnboardingTest {
    @Test fun browserSetupUrlsNeverCarryDeviceCredentials() {
        listOf(RdpFreeApi.CONNECTION_URL, RdpFreeApi.SETUP_URL, RdpFreeApi.DEVICES_URL).forEach {
            val uri = URI(it)
            assertEquals("https", uri.scheme)
            assertEquals("rdpfree.projectkal.my.id", uri.host)
            assertEquals(null, uri.query)
            assertEquals(null, uri.userInfo)
        }
    }
    @Test fun templateComesBeforeAuthorizationAndDeviceToken() {
        assertEquals(4, RdpFreeApi.ONBOARDING_STEPS.size)
        assertTrue(RdpFreeApi.ONBOARDING_STEPS[0].contains("template"))
        assertTrue(RdpFreeApi.ONBOARDING_STEPS[1].contains("Only select repositories"))
        assertTrue(RdpFreeApi.ONBOARDING_STEPS[2].contains("Setup"))
        assertTrue(RdpFreeApi.ONBOARDING_STEPS[3].contains("token perangkat"))
    }
}
