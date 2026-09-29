package id.xydesk.remote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test untuk kontrak data inti yang gampang rusak tanpa kelihatan:
 * validasi profil, id stabil perangkat, nilai `wire` audio (dipakai sebagai
 * argumen CLI `/audio-mode:N`), dan flag sertifikat.
 */
class RdpOptionsTest {

    @Test
    fun profilTolakHostKosongAtauSpasi() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionProfile(host = "   ")
        }
    }

    @Test
    fun profilTolakPortDiLuarRentang() {
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionProfile(host = "10.0.0.5", port = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConnectionProfile(host = "10.0.0.5", port = 70000)
        }
    }

    @Test
    fun idProfilStabilHostPort() {
        assertEquals("10.0.0.5:3389", ConnectionProfile(host = "10.0.0.5").id)
        assertEquals("10.0.0.5:3390", ConnectionProfile(host = "10.0.0.5", port = 3390).id)
    }

    @Test
    fun audioModeWireSesuaiArgumenCliFreeRdp() {
        assertEquals(0, XyAudioMode.DEVICE.wire)
        assertEquals(1, XyAudioMode.REMOTE.wire)
        assertEquals(2, XyAudioMode.OFF.wire)
    }

    @Test
    fun gatewayIdGabunganHostPort() {
        assertEquals("gw.corp.id:443", XyGateway(host = "gw.corp.id").id)
        assertEquals("gw.corp.id:8443", XyGateway(host = "gw.corp.id", port = 8443).id)
    }

    @Test
    fun opsiDefaultAmanUntukUserBaru() {
        val o = RdpOptions()
        assertEquals(XyAudioMode.DEVICE, o.audioMode)
        assertTrue(o.clipboard)
        assertTrue(o.udpTransport)
        assertTrue(o.networkAutoDetect)
        assertTrue(!o.lowBandwidth)
        assertTrue(o.h264)
        assertTrue("resolusi dinamis (kanal DISP) nyala untuk user baru", o.dynamicResolution)
        assertEquals(false, o.microphone)
        assertEquals(false, o.localDrive)
        assertEquals(false, o.camera)
        assertNull(o.gateway)
    }

    @Test
    fun certificateFlagsDibacaDariBitmask() {
        val info = CertificateInfo(
            host = "10.0.0.5",
            port = 3389,
            commonName = "rdp.local",
            subject = "CN=rdp.local",
            issuer = "CN=CA",
            fingerprint = "AA:BB",
            flags = CertificateInfo.FLAG_CHANGED or CertificateInfo.FLAG_GATEWAY,
        )
        assertTrue(info.isChanged)
        assertTrue(info.isGateway)
        assertTrue(!info.isMismatch)
        assertTrue(!info.isRedirect)
    }

    @Test
    fun verifyKonstantaSesuaiKontrakInti() {
        // 1 = diterima, 0 = ditolak (android_verify_certificate_ex).
        assertEquals(1, CertificateInfo.VERIFY_ACCEPT)
        assertEquals(0, CertificateInfo.VERIFY_DENY)
    }
}
