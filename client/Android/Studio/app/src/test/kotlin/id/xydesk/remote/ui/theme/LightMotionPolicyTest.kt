package id.xydesk.remote.ui.theme
import org.junit.Assert.*
import org.junit.Test
class LightMotionPolicyTest {
    @Test fun animationsRespectSystemAccessibilityAndBatterySaving() {
        assertTrue(shouldRunDecorativeMotion(true, true, false))
        assertFalse(shouldRunDecorativeMotion(false, true, false))
        assertFalse(shouldRunDecorativeMotion(true, false, false))
        assertFalse(shouldRunDecorativeMotion(true, true, true))
    }
}
