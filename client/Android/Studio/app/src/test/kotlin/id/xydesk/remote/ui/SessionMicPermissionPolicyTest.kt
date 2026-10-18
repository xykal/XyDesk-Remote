package id.xydesk.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionMicPermissionPolicyTest {
    @Test fun permissionCannotEnableChangedStoppedOrDeniedSession() {
        for (mask in 0..7) {
            assertEquals("combination $mask", mask == 7,
                canApplySessionMicPermission(mask and 1 != 0, mask and 2 != 0, mask and 4 != 0))
        }
    }
}
