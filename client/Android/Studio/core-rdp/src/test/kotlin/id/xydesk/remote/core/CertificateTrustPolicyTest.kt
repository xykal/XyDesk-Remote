package id.xydesk.remote.core

import org.junit.Assert.*
import org.junit.Test

class CertificateTrustPolicyTest {
    private fun certificate(flags: Long = 0L, fingerprint: String = "test-fingerprint", host: String = "host", port: Int = 3389) =
        CertificateInfo(host, port, "", "", "", fingerprint, flags)

    @Test fun cleanMatchingPinMayBeAccepted() {
        assertTrue(canAutoAcceptPinnedCertificate(certificate(), "test-fingerprint"))
    }
    @Test fun missingOrDifferentPinsAlwaysPrompt() {
        assertFalse(canAutoAcceptPinnedCertificate(certificate(), null))
        assertFalse(canAutoAcceptPinnedCertificate(certificate(), "other"))
        assertFalse(canAutoAcceptPinnedCertificate(certificate(fingerprint = " "), " "))
    }
    @Test fun everyWarningOrUnknownFlagRequiresReview() {
        for (flags in listOf(CertificateInfo.FLAG_CHANGED, CertificateInfo.FLAG_MISMATCH,
            CertificateInfo.FLAG_GATEWAY, CertificateInfo.FLAG_REDIRECT, CertificateInfo.FLAG_LEGACY, 0x1000L)) {
            assertFalse(canAutoAcceptPinnedCertificate(certificate(flags), "test-fingerprint"))
        }
    }
    @Test fun invalidEndpointCannotBeSilentlyTrusted() {
        assertFalse(canAutoAcceptPinnedCertificate(certificate(host = ""), "test-fingerprint"))
        assertFalse(canAutoAcceptPinnedCertificate(certificate(port = 0), "test-fingerprint"))
    }
}
