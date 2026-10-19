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

    @Test fun extraKeyboardStickModesSendAndReleaseDiagonalKeysWithoutSpam() {
        for ((mode, keys) in listOf(
            XyStickMode.IJKL to listOf(XyPadKey.I, XyPadKey.L),
            XyStickMode.NUMPAD to listOf(XyPadKey.NUMPAD_8, XyPadKey.NUMPAD_6),
        )) {
            val out = Recorder()
            val pressed = state(leftX = 32_767, leftY = -32_767)
            XyGamepadActionMapper.apply(pressed, null, out, mode)
            XyGamepadActionMapper.apply(pressed, pressed, out, mode)
            XyGamepadActionMapper.apply(state(), pressed, out, mode)
            assertEquals(keys.map { it to true } + keys.map { it to false }, out.keys)
            assertTrue(out.pointer.isEmpty())
        }
    }
    @Test fun customMappingsSerializeSafelyAndDeduplicateHeldDirections() {
        val custom = XyCustomStickBindings(up = XyPadKey.Q, left = XyPadKey.Q, down = XyPadKey.Z, right = XyPadKey.E)
        assertEquals(custom, XyCustomStickBindings.decode(custom.encode()))
        assertEquals(XyCustomStickBindings(), XyCustomStickBindings.decode("bad,input"))
        val out = Recorder()
        val diagonal = state(leftX = -32767, leftY = -32767)
        val onlyLeft = state(leftX = -32767)
        XyGamepadActionMapper.apply(diagonal, null, out, XyStickMode.CUSTOM, custom)
        XyGamepadActionMapper.apply(onlyLeft, diagonal, out, XyStickMode.CUSTOM, custom)
        assertEquals(listOf(XyPadKey.Q to true), out.keys)
        XyGamepadActionMapper.apply(state(), onlyLeft, out, XyStickMode.CUSTOM, custom)
        assertEquals(listOf(XyPadKey.Q to true, XyPadKey.Q to false), out.keys)
    }
    @Test fun sharedStickAndDpadKeysStayDownUntilBothSourcesRelease() {
        val out = Recorder()
        val both = state(buttons = XyGamepadPacketV1.DPAD_UP, leftY = -32767)
        val dpadOnly = state(buttons = XyGamepadPacketV1.DPAD_UP)
        XyGamepadActionMapper.apply(both, null, out, XyStickMode.ARROWS)
        XyGamepadActionMapper.apply(dpadOnly, both, out, XyStickMode.ARROWS)
        assertEquals(listOf(XyPadKey.ARROW_UP to true), out.keys)
        XyGamepadActionMapper.apply(state(), dpadOnly, out, XyStickMode.ARROWS)
        assertEquals(listOf(XyPadKey.ARROW_UP to true, XyPadKey.ARROW_UP to false), out.keys)
    }
    @Test fun keyCodesAndExistingStickOrdinalsStayStable() {
        assertEquals(3, XyStickMode.IJKL.ordinal)
        assertEquals(4, XyStickMode.NUMPAD.ordinal)
        assertEquals(5, XyStickMode.CUSTOM.ordinal)
        assertEquals(29, XyPadKey.A.androidKeyCode)
        assertEquals(54, XyPadKey.Z.androidKeyCode)
        assertEquals(152, XyPadKey.NUMPAD_8.androidKeyCode)
        assertEquals(7, XyPadKey.DIGIT_0.androidKeyCode)
    }

    @Test fun savedStickModeOrdinalsRemainCompatible() {
        assertEquals(0, XyStickMode.POINTER.ordinal)
        assertEquals(1, XyStickMode.WASD.ordinal)
        assertEquals(2, XyStickMode.ARROWS.ordinal)
    }

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
    fun wasdModeSendsKeyboardKeysInsteadOfMovingThePointer() {
        val out = Recorder()
        val up = state(leftY = -32_767)
        XyGamepadActionMapper.apply(up, null, out, XyStickMode.WASD)
        assertEquals(listOf(XyPadKey.W to true), out.keys)
        assertTrue("stik tidak boleh menggerakkan kursor di mode WASD", out.pointer.isEmpty())

        // Melepas stik harus mengangkat tombol, bukan membiarkannya tersangkut.
        val released = Recorder()
        XyGamepadActionMapper.apply(state(), up, released, XyStickMode.WASD)
        assertEquals(listOf(XyPadKey.W to false), released.keys)
    }

    @Test
    fun diagonalStickPressesTwoKeysAndReversingSwapsThemInOneFrame() {
        val out = Recorder()
        val upRight = state(leftX = 32_767, leftY = -32_767)
        XyGamepadActionMapper.apply(upRight, null, out, XyStickMode.WASD)
        assertEquals(setOf(XyPadKey.W to true, XyPadKey.D to true), out.keys.toSet())

        val down = Recorder()
        val downLeft = state(leftX = -32_767, leftY = 32_767)
        XyGamepadActionMapper.apply(downLeft, upRight, down, XyStickMode.WASD)
        assertEquals(
            setOf(XyPadKey.W to false, XyPadKey.D to false, XyPadKey.S to true, XyPadKey.A to true),
            down.keys.toSet(),
        )
    }

    @Test
    fun arrowModeUsesArrowKeysAndIgnoresSmallDeflection() {
        val out = Recorder()
        XyGamepadActionMapper.apply(state(leftX = -32_767), null, out, XyStickMode.ARROWS)
        assertEquals(listOf(XyPadKey.ARROW_LEFT to true), out.keys)

        val small = Recorder()
        // 0.3 defleksi di bawah ambang 0.5 -> tidak ada tombol.
        XyGamepadActionMapper.apply(state(leftX = (0.3f * 32_767).toInt()), null, small, XyStickMode.ARROWS)
        assertTrue(small.keys.isEmpty())
        assertTrue(small.pointer.isEmpty())
    }

    @Test
    fun pointerModeIsUnchangedByTheNewStickModes() {
        val out = Recorder()
        XyGamepadActionMapper.apply(state(leftX = 32_767, leftY = -32_767), null, out)
        assertEquals(1, out.pointer.size)
        assertTrue(out.keys.isEmpty())
    }

    @Test
    fun directionsHelperMatchesTheDocumentedThreshold() {
        assertEquals(0, XyGamepadActionMapper.directions(0f, 0f).size)
        assertEquals(
            setOf(XyStickDirection.UP, XyStickDirection.RIGHT),
            XyGamepadActionMapper.directions(0.9f, -0.9f),
        )
        assertEquals(0, XyGamepadActionMapper.directions(0.49f, -0.49f).size)
        assertEquals(XyPadKey.S, XyGamepadActionMapper.keyFor(XyStickDirection.DOWN, XyStickMode.WASD))
        assertEquals(
            XyPadKey.ARROW_DOWN,
            XyGamepadActionMapper.keyFor(XyStickDirection.DOWN, XyStickMode.ARROWS),
        )
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
