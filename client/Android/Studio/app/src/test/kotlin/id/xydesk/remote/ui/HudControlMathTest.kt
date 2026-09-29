package id.xydesk.remote.ui

import android.view.KeyEvent
import com.freerdp.freerdpcore.utils.KeyboardMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HudControlMathTest {
    @Test
    fun dragScrollAccumulatesSmallMovesAndKeepsWheelDirection() {
        val first = scrollWheelStep(deltaY = 4f, remainder = 0f, speed = 1f)
        assertEquals(0, first.units)
        assertEquals(-12f, first.remainder, 0.001f)

        val second = scrollWheelStep(deltaY = 4f, remainder = first.remainder, speed = 1f)
        assertEquals(-24, second.units)
        assertEquals(0f, second.remainder, 0.001f)

        assertEquals(120, scrollWheelStep(-40f, 0f, 1f).units)
        assertEquals(-120, scrollWheelStep(40f, 0f, 1f).units)
    }

    @Test
    fun accumulatorResetsBetweenGestures() {
        val accumulator = ScrollWheelAccumulator()
        assertEquals(0, accumulator.consume(4f, 1f))
        assertEquals(-24, accumulator.consume(4f, 1f))
        accumulator.reset()
        assertEquals(0, accumulator.consume(4f, 1f))
    }

    @Test
    fun dragScrollBoundsExtremeAndNonFiniteInput() {
        val extreme = scrollWheelStep(Float.MAX_VALUE, 0f, 2.5f)
        assertTrue(extreme.units in -1800..0)
        assertTrue(extreme.remainder in -24f..24f)
        assertEquals(0, scrollWheelStep(Float.NaN, Float.NaN, Float.NaN).units)
    }

    @Test
    fun resizingKeepsButtonCenterAndClampsAtViewportEdges() {
        val key = HudKey("test", HudKind.MOUSE_LEFT, "Left", x = 0.5f, y = 0.4f, size = 64f)
        val resized = resizeHudKeyPreservingCenter(key, 120f, 400f, 800f)
        assertEquals(120f, resized.size, 0.001f)
        val oldCenterX = key.x * (400f - key.size) + key.size / 2f
        val newCenterX = resized.x * (400f - resized.size) + resized.size / 2f
        val oldCenterY = key.y * (800f - key.size) + key.size / 2f
        val newCenterY = resized.y * (800f - resized.size) + resized.size / 2f
        assertEquals(oldCenterX, newCenterX, 0.01f)
        assertEquals(oldCenterY, newCenterY, 0.01f)

        val edge = resizeHudKeyPreservingCenter(key.copy(x = 1f, y = 1f), 120f, 200f, 200f)
        assertEquals(1f, edge.x, 0.001f)
        assertEquals(1f, edge.y, 0.001f)
    }

    @Test
    fun everyHudCatalogKeyAndComboTranslatesToWindowsVirtualKey() {
        val mapper = KeyboardMapper()
        assertEquals(0x0D, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_ENTER))
        assertEquals(0x08, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_DEL))
        assertEquals(0x1B, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(0x09, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_TAB))
        assertEquals(0x20, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_SPACE))
        assertEquals(0xA2, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(0xA0, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT))
        assertEquals(0xA4, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_ALT_LEFT))
        assertEquals(0x15B, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_META_LEFT))
        assertEquals(0x15D, mapper.translateAndroidKeyCode(KeyEvent.KEYCODE_MENU))

        HudKeyCatalog.groups.flatMap { it.third }.forEach { option ->
            when (option.kind) {
                HudKind.KEY -> {
                    val vk = mapper.translateAndroidKeyCode(option.keyCode)
                    assertNotEquals("Unmapped HUD key: ${option.label}", 0, vk)
                }
                HudKind.COMBO -> {
                    assertTrue("Empty combo: ${option.label}", option.combo.isNotEmpty())
                    option.combo.forEach { code ->
                        val vk = mapper.translateAndroidKeyCode(code)
                        assertNotEquals("Unmapped combo member in ${option.label}: $code", 0, vk)
                    }
                }
                else -> Unit
            }
        }
    }

    @Test
    fun hudGestureThresholdRequiresTouchSlopAndDraggableOrScrollControl() {
        assertFalse(shouldStartHudButtonGesture(travelledPx = 24f, touchSlopPx = 18f, mappingMode = false, kind = HudKind.COMBO))
        assertFalse(shouldStartHudButtonGesture(travelledPx = 24f, touchSlopPx = 18f, mappingMode = false, kind = HudKind.KEY))
        assertFalse(shouldStartHudButtonGesture(travelledPx = 12f, touchSlopPx = 18f, mappingMode = true, kind = HudKind.KEY))
        assertTrue(shouldStartHudButtonGesture(travelledPx = 20f, touchSlopPx = 18f, mappingMode = true, kind = HudKind.KEY))
        assertTrue(shouldStartHudButtonGesture(travelledPx = 20f, touchSlopPx = 18f, mappingMode = false, kind = HudKind.SCROLL_SLIDER))
    }

    @Test
    fun catalogModifiersAndHoldClickHaveIntendedDefaultActions() {
        assertTrue(HudKeyCatalog.modifiers.all { it.defaultAction == HudAction.TOGGLE })
        val holdClick = HudKeyCatalog.mouse.first { it.label.contains("tahan") }
        assertEquals(HudAction.HOLD, holdClick.defaultAction)
    }
}
