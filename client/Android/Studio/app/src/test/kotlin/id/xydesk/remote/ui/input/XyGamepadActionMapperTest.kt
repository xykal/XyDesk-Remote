package id.xydesk.remote.ui.input

import id.xydesk.remote.pcstream.XyGamepadPacketV1
import id.xydesk.remote.pcstream.XyGamepadState
import id.xydesk.remote.ui.XyMouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class XyGamepadActionMapperTest {
    private class Recorder : XyGamepadOutput {
        val pointer = mutableListOf<Pair<Float, Float>>()
        val scroll = mutableListOf<Int>()
        val clicks = mutableListOf<Pair<XyMouseButton, Boolean>>()
        val keys = mutableListOf<Pair<XyPadKey, Boolean>>()

        override fun pointerDelta(dx: Float, dy: Float) { pointer += dx to dy }
        override fun scrollUnits(units: Int) { scroll += units }
        override fun mouseClick(button: XyMouseButton, down: Boolean) { clicks += button to down }
        override fun key(key: XyPadKey, down: Boolean) { keys += key to down }
    }

    private fun state(buttons: Int = 0, leftX: Int = 0, leftY: Int = 0, rightY: Int = 0) =
        XyGamepadState(buttons = buttons, leftX = leftX, leftY = leftY, rightY = rightY)

    @Test
    fun leftStickMovesPointerAtEighteenUnitsPerDeflection() {
        val out = Recorder()
        XyGamepadActionMapper.apply(state(leftX = 32_767, leftY = -32_767), null, out)
        assertEquals(1, out.pointer.size)
        assertEquals(18f, out.pointer[0].first, 0.01f)
        assertEquals(-18f, out.pointer[0].second, 0.01f)
        assertTrue(out.scroll.isEmpty() && out.clicks.isEmpty() && out.keys.isEmpty())
    }

    @Test
    fun centeredStickEmitsNothingSoThePointerNeverDrifts() {
        val out = Recorder()
        XyGamepadActionMapper.apply(state(), null, out)
        assertTrue(out.pointer.isEmpty())
    }

    @Test
    fun buttonPressIsEdgeTriggeredAndNeverSpamsWhileHeld() {
        val out = Recorder()
        val idle = state()
        val pressed = state(buttons = XyGamepadPacketV1.A)

        XyGamepadActionMapper.apply(pressed, idle, out)
        XyGamepadActionMapper.apply(pressed, pressed, out)
        XyGamepadActionMapper.apply(pressed, pressed, out)
        XyGamepadActionMapper.apply(idle, pressed, out)

        assertEquals(listOf(XyMouseButton.LEFT to true, XyMouseButton.LEFT to false), out.clicks)
    }

    @Test
    fun faceButtonsMapToTheSameActionsAsThePhysicalPad() {
        val out = Recorder()
        XyGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.B), null, out)
        XyGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.X), null, out)
        XyGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.Y), null, out)

        assertEquals(XyMouseButton.RIGHT to true, out.clicks.single())
        assertEquals(XyPadKey.ENTER to true, out.keys[0])
        assertEquals(XyPadKey.ESCAPE to true, out.keys[1])
    }

    @Test
    fun dpadEmitsArrowKeysAndShouldersEmitOneScrollNotch() {
        val out = Recorder()
        XyGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.DPAD_UP), null, out)
        assertEquals(XyPadKey.ARROW_UP to true, out.keys.single())

        val scrollOut = Recorder()
        XyGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.LEFT_SHOULDER), null, scrollOut)
        assertEquals(listOf(120), scrollOut.scroll)

        val rightOut = Recorder()
        XyGamepadActionMapper.apply(state(buttons = XyGamepadPacketV1.RIGHT_SHOULDER), null, rightOut)
        assertEquals(listOf(-120), rightOut.scroll)
    }

    @Test
    fun rightStickScrollsProportionallyAndTriggersOnlyOnRisingEdge() {
        val out = Recorder()
        XyGamepadActionMapper.apply(state(rightY = 32_767), null, out)
        assertEquals(listOf(-48), out.scroll)

        val trigger = Recorder()
        val released = XyGamepadState(rightTrigger = 0)
        val pulled = XyGamepadState(rightTrigger = 200)
        XyGamepadActionMapper.apply(pulled, released, trigger)
        XyGamepadActionMapper.apply(pulled, pulled, trigger)
        assertEquals(listOf(-120), trigger.scroll)
    }

    @Test
    fun everyEmittedValueStaysFiniteAndBounded() {
        val out = Recorder()
        for (axis in intArrayOf(-32_768, -1, 0, 1, 32_767)) {
            XyGamepadActionMapper.apply(state(leftX = axis, leftY = axis, rightY = axis), null, out)
        }
        out.pointer.forEach { (dx, dy) ->
            assertTrue(abs(dx) <= 18.01f && abs(dy) <= 18.01f)
        }
        out.scroll.forEach { assertTrue(abs(it) <= 120) }
    }
}
