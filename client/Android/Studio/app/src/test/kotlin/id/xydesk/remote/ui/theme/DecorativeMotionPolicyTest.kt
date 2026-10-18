package id.xydesk.remote.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class DecorativeMotionPolicyTest {
    @Test fun requiresForegroundSystemMotionAndNoPowerSaver() {
        for (started in listOf(false, true)) for (motion in listOf(false, true)) for (saving in listOf(false, true)) {
            assertEquals(started && motion && !saving, shouldRunDecorativeMotion(started, motion, saving))
        }
    }
}
