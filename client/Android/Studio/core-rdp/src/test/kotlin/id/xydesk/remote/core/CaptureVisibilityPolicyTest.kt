package id.xydesk.remote.core

import org.junit.Assert.*
import org.junit.Test

class CaptureVisibilityPolicyTest {
    @Test fun backgroundCannotRequestNativeCaptureEvenWithPermission() {
        val options = RdpOptions(microphone = true, camera = true)
            .withCapturePermissions(true, true).forCaptureVisibility(false)
        assertFalse(options.microphone)
        assertFalse(options.camera)
        val args = RdpUri.queryParams(ConnectionProfile(host = "192.168.1.2"), options).toMap()
        assertFalse(args.containsKey("microphone"))
        assertFalse(args.containsKey("dvc"))
    }

    @Test fun foregroundDoesNotEnableCaptureAgainstUserPreference() {
        val options = RdpOptions(microphone = false, camera = false).forCaptureVisibility(true)
        assertFalse(options.microphone)
        assertFalse(options.camera)
    }

    @Test fun foregroundCannotBypassDeniedPermission() {
        val options = RdpOptions(microphone = true, camera = true)
            .withCapturePermissions(false, false).forCaptureVisibility(true)
        assertFalse(options.microphone)
        assertFalse(options.camera)
    }

    @Test fun desktopOnlyBackgroundKeepsPlaybackAndOtherOptions() {
        val saved = RdpOptions(microphone = true, camera = true, audioMode = XyAudioMode.DEVICE)
        val background = saved.forCaptureVisibility(false)
        assertEquals(saved.copy(microphone = false, camera = false), background)
        assertTrue(saved.microphone)
        assertTrue(saved.camera)
    }

    @Test fun futureForegroundConnectionUsesSavedPreferenceWithoutChangingProfile() {
        val saved = RdpOptions(microphone = true, camera = false)
        assertFalse(saved.forCaptureVisibility(false).microphone)
        assertTrue(saved.withCapturePermissions(true, false).forCaptureVisibility(true).microphone)
        assertFalse(saved.withCapturePermissions(false, false).forCaptureVisibility(true).microphone)
    }
}
