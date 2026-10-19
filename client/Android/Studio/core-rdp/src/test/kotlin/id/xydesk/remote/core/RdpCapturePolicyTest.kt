package id.xydesk.remote.core

import org.junit.Assert.*
import org.junit.Test

class RdpCapturePolicyTest {
    private val profile = ConnectionProfile(host = "192.168.1.2", label = "RDP")

    @Test fun rdpMicrophoneRequiresPermissionAndExplicitPreference() {
        val saved = RdpOptions(microphone = true, camera = true)
        val denied = saved.withCapturePermissions(false, false)
        assertFalse(denied.microphone)
        assertFalse(denied.camera)
        assertTrue(saved.microphone) // Permission denial must not overwrite saved intent.
        assertTrue(saved.camera)
        val args = RdpUri.queryParams(profile, denied).toMap()
        assertFalse(args.containsKey("microphone"))
        assertFalse(args.containsKey("dvc"))
        assertFalse(RdpOptions(microphone = false).withCapturePermissions(true, true).microphone)
    }

    @Test fun nativeRdpMicrophoneDoesNotNeedLegacyHostBridge() {
        val options = RdpOptions().forRdpFree(true).withCapturePermissions(true, false)
        val args = RdpUri.queryParams(profile, options).toMap()
        assertEquals("sys:opensles,format:1", args["microphone"])
        assertEquals("sys:opensles,format:1,quality:high", args["sound"])
        assertFalse(options.quicAudio)
        assertFalse(options.usesQuicMicrophone())
    }

    @Test fun cameraAndMicrophonePermissionsAreIndependent() {
        val saved = RdpOptions(camera = true, microphone = true)
        assertTrue(saved.withCapturePermissions(false, true).camera)
        assertFalse(saved.withCapturePermissions(false, true).microphone)
        assertTrue(saved.withCapturePermissions(true, false).microphone)
        assertFalse(saved.withCapturePermissions(true, false).camera)
    }

    @Test fun revokedPermissionIsReevaluatedOnNextConnection() {
        val saved = RdpOptions(microphone = true)
        assertTrue(saved.withCapturePermissions(true, false).microphone)
        assertFalse(saved.withCapturePermissions(false, false).microphone)
        assertTrue(saved.withCapturePermissions(true, false).microphone)
    }

    @Test fun legacyPcProfilesNeverStartNativeRdpCapture() {
        val options = RdpOptions(pcConnectMode = true, microphone = true, camera = true)
            .withCapturePermissions(true, true)
        assertFalse(options.microphone)
        assertFalse(options.camera)
    }
}
