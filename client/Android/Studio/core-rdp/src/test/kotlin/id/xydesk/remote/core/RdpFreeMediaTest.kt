package id.xydesk.remote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpFreeMediaTest {
    @Test fun reconnectOnMeteredLinkStillNegotiatesWallpaper() {
        val profile = ConnectionProfile(host = "100.64.1.2", key = "rdpfree:owner/session", label = "RdpFree")
        val options = RdpOptions(desktopWallpaper = false).resolveForLink(true, true).forSessionProfile(profile)
        assertTrue(options.desktopWallpaper)
        assertEquals("+", RdpUri.queryParams(profile, options).toMap()["wallpaper"])
    }
    @Test fun unrelatedProfilesKeepTheirWallpaperPreference() {
        val profile = ConnectionProfile(host = "192.168.1.2", label = "Office")
        assertFalse(RdpOptions(desktopWallpaper = false).forSessionProfile(profile).desktopWallpaper)
    }

    @Test fun rdpFreeEnablesWallpaperAndRdpPlaybackWithoutBridge() {
        val options = RdpOptions().forRdpFree(microphone = false)
        assertTrue(options.desktopWallpaper)
        assertEquals(XyAudioMode.DEVICE, options.audioMode)
        assertFalse(options.pcConnectMode)
        assertFalse(options.quicAudio)
        assertFalse(options.microphone)
    }
    @Test fun optInMicrophoneUsesRdpWithoutDuplicateUdpCapture() {
        val options = RdpOptions().forRdpFree(microphone = true)
        assertTrue(options.microphone)
        assertFalse(options.usesQuicMicrophone())
    }
    @Test fun otherProfilesRetainOptInBridge() {
        assertTrue(RdpOptions(quicAudio = true, microphone = true).usesQuicMicrophone())
        assertFalse(RdpOptions(quicAudio = true, microphone = false).usesQuicMicrophone())
        assertFalse(RdpOptions(quicAudio = false, microphone = true).usesQuicMicrophone())
    }
}
