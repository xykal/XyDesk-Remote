package id.xydesk.remote.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpFreeMediaTest {
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
