package id.xydesk.remote.core

import org.junit.Assert.*
import org.junit.Test

class RdpConnectionDiagnosticsTest {
    @Test fun reportedSharedAddressesAndBoundariesAreDetectedWithoutDns() {
        for (host in listOf("100.99.33.76", "100.70.237.47", "100.64.0.0", "100.127.255.255")) assertTrue(host, RdpConnectionDiagnostics.isSharedAddress(host))
        for (host in listOf("100.63.255.255", "100.128.0.0", "192.168.1.2", "relay.example", "100.99.3.999", "100.99.33.76.example")) assertFalse(host, RdpConnectionDiagnostics.isSharedAddress(host))
    }
    @Test fun tcpFailureDoesNotClaimPasswordOrOperatingSystemDiagnosis() {
        val message = RdpConnectionDiagnostics.unreachableMessage("100.99.33.76", 3389)
        assertTrue(message.contains("sebelum login"))
        assertTrue(message.contains("VPN/overlay"))
        assertFalse(message.contains("Windows Home"))
    }
    @Test fun compatibilityRetriesPreserveExplicitSecurityAndCapturePreferences() {
        for (protocol in XySecurityProtocol.entries) {
            val saved = RdpOptions(securityProtocol = protocol, tlsSecLevel = 2, microphone = true)
            for (stage in 0..2) {
                val retry = saved.forHandshakeCompatibility(stage)
                assertEquals(protocol, retry.securityProtocol)
                assertEquals(2, retry.tlsSecLevel)
                assertTrue(retry.microphone)
                if (stage > 0) assertFalse(retry.udpTransport)
                if (stage == 2) assertFalse(retry.h264)
            }
        }
    }
    @Test fun onlyExpectedNativeCancellationIsNormalAfterUserRequest() {
        assertTrue(RdpFailure.isExpectedCancellation(true, "The connection was cancelled."))
        assertTrue(RdpFailure.isExpectedCancellation(true, ""))
        assertFalse(RdpFailure.isExpectedCancellation(false, "The connection was cancelled."))
        assertFalse(RdpFailure.isExpectedCancellation(true, "Logon failed."))
        assertFalse(RdpFailure.isExpectedCancellation(true, "The connection failed at negotiating security settings."))
    }
    @Test fun negotiationFailureIsNotInferredToBeWrongPasswordOrCertificate() {
        assertEquals(0x0C, RdpFailure.SECURITY_NEGOTIATION)
        assertEquals(RdpFailure.SECURITY_NEGOTIATION, RdpFailure.codeFor("connect_failed", "The connection failed at negotiating security settings."))
        assertEquals(RdpFailure.AUTH_LOGON, RdpFailure.codeFor("connect_failed", "Logon failed."))
        assertEquals(RdpFailure.TLS, RdpFailure.codeFor("connect_failed", "self-signed certificate"))
    }
}
