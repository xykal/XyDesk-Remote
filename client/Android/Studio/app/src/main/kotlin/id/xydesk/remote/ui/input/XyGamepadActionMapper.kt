package id.xydesk.remote.ui.input

import id.xydesk.remote.pcstream.XyGamepadPacketV1
import id.xydesk.remote.pcstream.XyGamepadState
import id.xydesk.remote.ui.XyMouseButton
import kotlin.math.abs

/** Keys the virtual pad can emit; the session translates these to Android key codes. */
enum class XyPadKey(val androidKeyCode: Int) {
    ENTER(66),
    ESCAPE(111),
    ARROW_UP(19),
    ARROW_DOWN(20),
    ARROW_LEFT(21),
    ARROW_RIGHT(22),
    W(51),
    A(29),
    S(47),
    D(32),
    I(37),
    J(38),
    K(39),
    L(40),
    NUMPAD_8(152),
    NUMPAD_4(148),
    NUMPAD_2(146),
    NUMPAD_6(150),
    B(30),
    C(31),
    E(33),
    F(34),
    G(35),
    H(36),
    M(41),
    N(42),
    O(43),
    P(44),
    Q(45),
    R(46),
    T(48),
    U(49),
    V(50),
    X(52),
    Y(53),
    Z(54),
    DIGIT_0(7),
    DIGIT_1(8),
    DIGIT_2(9),
    DIGIT_3(10),
    DIGIT_4(11),
    DIGIT_5(12),
    DIGIT_6(13),
    DIGIT_7(14),
    DIGIT_8(15),
    DIGIT_9(16),
    SPACE(62),
    TAB(61),
    SHIFT(59),
    CTRL(113),
    ALT(57),
}

/**
 * Tujuan stik kiri. [POINTER] menggerakkan kursor (perilaku lama), [WASD] dan
 * [ARROWS] mengirim tombol papan ketik sehingga stik bisa dipakai untuk game
 * yang tidak menerima gamepad.
 */
enum class XyStickMode {
    POINTER,
    WASD,
    ARROWS,
    // Append only: existing ordinals are stored in SessionPrefs.
    IJKL,
    NUMPAD,
    CUSTOM,
}

/** Arah digital yang diturunkan dari simpangan stik analog. */
enum class XyStickDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

/**
 * Output side of a gamepad. Implemented by the RDP session with the same hooks the
 * physical Bluetooth/USB pad already uses (pointer delta, scroll units, mouse click,
 * virtual key), so both pads end up on one code path.
 */
interface XyGamepadOutput {
    fun pointerDelta(dx: Float, dy: Float)
    fun scrollUnits(units: Int)
    fun mouseClick(button: XyMouseButton, down: Boolean)
    fun key(key: XyPadKey, down: Boolean)
}

/**
 * Translates gamepad state into RDP-side actions.
 *
 * Semantics intentionally mirror the existing physical-pad mapping in
 * `XyDeskSessionActivity` so an on-screen pad and a Bluetooth pad behave the same:
 * A = left click, B = right click, X = Enter, Y = Escape, left stick moves the
 * pointer at 18 units per full deflection, shoulders scroll one notch, and the
 * right stick / triggers scroll proportionally.
 *
 * Pure Kotlin (no Android imports) so the mapping is unit-testable on the JVM.
 */
object XyGamepadActionMapper {
    /** Pointer units per full stick deflection; matches the physical pad. */
    const val POINTER_UNITS_PER_DEFLECTION = 18f

    /** One scroll notch, in wheel units; matches the physical shoulder buttons. */
    const val SHOULDER_SCROLL_UNITS = 120

    /** Scroll units at full right-stick / trigger deflection. */
    const val SCROLL_UNITS_PER_DEFLECTION = 48f

    /** Trigger fraction that counts as "pressed" for scroll notches. */
    const val TRIGGER_THRESHOLD = 0.5f

    private const val AXIS_DENOMINATOR = 32_767f
    private const val STICK_EPSILON = 1e-3f

    /** Simpangan stik minimum agar arah digital (WASD/panah) aktif. */
    const val STICK_KEY_THRESHOLD = 0.5f

    private val EMPTY = XyGamepadState()

    /**
     * Emit the actions implied by moving from [previous] to [state].
     *
     * Sticks are continuous (like a physical `ACTION_MOVE` stream); buttons and
     * triggers are edge-triggered so holding a control never spams the host.
     */
    fun apply(
        state: XyGamepadState,
        previous: XyGamepadState?,
        out: XyGamepadOutput,
        stickMode: XyStickMode = XyStickMode.POINTER,
        custom: XyCustomStickBindings = XyCustomStickBindings(),
    ) {
        val before = previous ?: EMPTY

        val leftX = axis(state.leftX)
        val leftY = axis(state.leftY)
        if (stickMode == XyStickMode.POINTER) {
            if (abs(leftX) > STICK_EPSILON || abs(leftY) > STICK_EPSILON) {
                out.pointerDelta(
                    leftX * POINTER_UNITS_PER_DEFLECTION,
                    leftY * POINTER_UNITS_PER_DEFLECTION,
                )
            }
        }
        // Aggregate every keyboard source before diffing, including shared custom/D-pad keys.
        val nowKeys = keyboardKeys(state, stickMode, custom)
        val wasKeys = keyboardKeys(before, stickMode, custom)
        (wasKeys - nowKeys).forEach { out.key(it, false) }
        (nowKeys - wasKeys).forEach { out.key(it, true) }

        val rightY = axis(state.rightY)
        if (abs(rightY) > STICK_EPSILON) {
            val units = (-rightY * SCROLL_UNITS_PER_DEFLECTION).toInt()
            if (units != 0) out.scrollUnits(units)
        }

        if (crossed(before.leftTrigger, state.leftTrigger)) out.scrollUnits(SHOULDER_SCROLL_UNITS)
        if (crossed(before.rightTrigger, state.rightTrigger)) out.scrollUnits(-SHOULDER_SCROLL_UNITS)

        edge(before.buttons, state.buttons, XyGamepadPacketV1.A) { down ->
            out.mouseClick(XyMouseButton.LEFT, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.B) { down ->
            out.mouseClick(XyMouseButton.RIGHT, down)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.LEFT_THUMB) { down ->
            out.mouseClick(XyMouseButton.MIDDLE, down)
        }
        // START/BACK have no unambiguous RDP target and are intentionally unmapped;
        // they still travel to a PC/Game Stream host inside the packet.
        edge(before.buttons, state.buttons, XyGamepadPacketV1.LEFT_SHOULDER) { down ->
            if (down) out.scrollUnits(SHOULDER_SCROLL_UNITS)
        }
        edge(before.buttons, state.buttons, XyGamepadPacketV1.RIGHT_SHOULDER) { down ->
            if (down) out.scrollUnits(-SHOULDER_SCROLL_UNITS)
        }
    }

    private fun keyboardKeys(state: XyGamepadState, mode: XyStickMode, custom: XyCustomStickBindings): Set<XyPadKey> = buildSet {
        if (mode != XyStickMode.POINTER) directions(axis(state.leftX), axis(state.leftY)).forEach { add(keyFor(it, mode, custom)) }
        for ((bit, key) in listOf(
            XyGamepadPacketV1.X to XyPadKey.ENTER, XyGamepadPacketV1.Y to XyPadKey.ESCAPE,
            XyGamepadPacketV1.DPAD_UP to XyPadKey.ARROW_UP, XyGamepadPacketV1.DPAD_DOWN to XyPadKey.ARROW_DOWN,
            XyGamepadPacketV1.DPAD_LEFT to XyPadKey.ARROW_LEFT, XyGamepadPacketV1.DPAD_RIGHT to XyPadKey.ARROW_RIGHT,
        )) if ((state.buttons and bit) != 0) add(key)
    }

    /**
     * Arah digital dari stik analog. Ambang [STICK_KEY_THRESHOLD] dipakai agar
     * simpangan kecil tidak memicu tombol; diagonal menghasilkan dua arah.
     */
    fun directions(x: Float, y: Float): Set<XyStickDirection> {
        val out = mutableSetOf<XyStickDirection>()
        if (y <= -STICK_KEY_THRESHOLD) out += XyStickDirection.UP
        if (y >= STICK_KEY_THRESHOLD) out += XyStickDirection.DOWN
        if (x <= -STICK_KEY_THRESHOLD) out += XyStickDirection.LEFT
        if (x >= STICK_KEY_THRESHOLD) out += XyStickDirection.RIGHT
        return out
    }

    /** Kunci untuk satu arah pada mode stik papan ketik. */
    fun keyFor(direction: XyStickDirection, mode: XyStickMode, custom: XyCustomStickBindings = XyCustomStickBindings()): XyPadKey {
        val keys = when (mode) {
            XyStickMode.CUSTOM -> listOf(custom.up, custom.down, custom.left, custom.right)
            XyStickMode.WASD -> listOf(XyPadKey.W, XyPadKey.S, XyPadKey.A, XyPadKey.D)
            XyStickMode.IJKL -> listOf(XyPadKey.I, XyPadKey.K, XyPadKey.J, XyPadKey.L)
            XyStickMode.NUMPAD -> listOf(XyPadKey.NUMPAD_8, XyPadKey.NUMPAD_2, XyPadKey.NUMPAD_4, XyPadKey.NUMPAD_6)
            else -> listOf(XyPadKey.ARROW_UP, XyPadKey.ARROW_DOWN, XyPadKey.ARROW_LEFT, XyPadKey.ARROW_RIGHT)
        }
        return keys[direction.ordinal]
    }

    private fun axis(rawValue: Int): Float = rawValue / AXIS_DENOMINATOR

    private inline fun edge(before: Int, after: Int, bit: Int, emit: (Boolean) -> Unit) {
        val was = (before and bit) != 0
        val now = (after and bit) != 0
        if (was != now) emit(now)
    }

    /** True only on the rising edge past [TRIGGER_THRESHOLD]. */
    private fun crossed(before: Int, after: Int): Boolean =
        before < TRIGGER_THRESHOLD * 255f && after >= TRIGGER_THRESHOLD * 255f
}

/** Named keyboard mappings only; never advertised as a Windows analog device. */
data class XyCustomStickBindings(
    val up: XyPadKey = XyPadKey.W, val down: XyPadKey = XyPadKey.S,
    val left: XyPadKey = XyPadKey.A, val right: XyPadKey = XyPadKey.D,
) {
    fun encode(): String = listOf(up, down, left, right).joinToString(",") { it.name }
    companion object {
        fun decode(value: String?): XyCustomStickBindings = runCatching {
            val parts = requireNotNull(value).split(',')
            require(parts.size == 4)
            XyCustomStickBindings(XyPadKey.valueOf(parts[0]), XyPadKey.valueOf(parts[1]), XyPadKey.valueOf(parts[2]), XyPadKey.valueOf(parts[3]))
        }.getOrDefault(XyCustomStickBindings())
    }
}
