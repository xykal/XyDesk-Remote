package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class RdpErrorsReportedLogTest {
    @Test fun genericLogonRejectionDoesNotAccuseWrongPassword() {
        assertEquals("Logon ditolak server", RdpErrors.shortTitle("connect_failed", "Logon failed.").first)
        assertEquals("Password salah", RdpErrors.shortTitle("connect_failed", "wrong password supplied").first)
    }
    @Test fun securityNegotiationHasActionableNonBypassHint() {
        val detail = "The connection failed at negotiating security settings."
        assertEquals("Negosiasi keamanan gagal", RdpErrors.shortTitle("connect_failed", detail).first)
        assertTrue(RdpErrors.hint("connect_failed", detail)!!.first.contains("Jangan mematikan NLA"))
        assertFalse(RdpErrors.isCredentialError("connect_failed", detail))
    }
}
