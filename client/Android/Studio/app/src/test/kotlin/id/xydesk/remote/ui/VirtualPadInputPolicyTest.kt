package id.xydesk.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualPadInputPolicyTest {
    @Test fun onlyResumedFocusedConnectedNonEditingInputIsAllowed() {
        for (mask in 0..15) {
            val editing = mask and 1 != 0
            val resumed = mask and 2 != 0
            val focused = mask and 4 != 0
            val connected = mask and 8 != 0
            assertEquals("combination $mask", mask == 14,
                virtualPadInputEnabled(editing, resumed, focused, connected))
        }
    }
}
