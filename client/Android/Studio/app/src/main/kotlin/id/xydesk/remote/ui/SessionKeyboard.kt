package id.xydesk.remote.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Keyboard on-screen lengkap untuk sesi XyDesk.
 *
 * Kenapa tidak pakai keyboard inti FreeRDP: barisnya cuma F1-F12 + numpad
 * (2 halaman), tidak ada QWERTY, tidak bisa digeser/di-skala, dan tertutup
 * keyboard sistem. Overlay ini menutup semua itu dan mengirim keycode
 * Android lewat [SessionSurfaceController.sendVirtualKey] supaya pemetaan
 * ke scancode RDP tetap ditangani inti (KeyboardMapper).
 *
 * Halaman: ABC (QWERTY + modifier), Fn (F1-F12 + navigasi), NUM (numpad),
 * SIM (simbol), KOMBO (kombinasi siap pakai).
 *
 * Bentuk: tombol key persegi radius kecil, hanya border + teks (tanpa isi),
 * konsisten dengan aturan "border putih, tanpa background".
 */
enum class KeyPage(val tab: String) {
    ABC("ABC"),
    FN("Fn"),
    NUM("NUM"),
    SYM("SIM"),
    COMBO("KOMBO"),
}

/** Suatu tombol: keycode Android + perlu Shift atau tidak. */
private data class XyKey(
    val label: String,
    val code: Int,
    val shift: Boolean = false,
    val weight: Float = 1f,
    val chrome: Boolean = false,
    val wide: Boolean = false,
)

/** Kombinasi siap pakai. */
private data class XyCombo(val label: String, val codes: List<Int>)

private val CTRL = KeyEvent.KEYCODE_CTRL_LEFT
private val SHIFT = KeyEvent.KEYCODE_SHIFT_LEFT
private val ALT = KeyEvent.KEYCODE_ALT_LEFT
private val WIN = KeyEvent.KEYCODE_META_LEFT

private val combos = listOf(
    XyCombo("Ctrl+Alt+Del", listOf(CTRL, ALT, KeyEvent.KEYCODE_FORWARD_DEL)),
    XyCombo("Alt+Tab", listOf(ALT, KeyEvent.KEYCODE_TAB)),
    XyCombo("Ctrl+Shift+Esc", listOf(CTRL, SHIFT, KeyEvent.KEYCODE_ESCAPE)),
    XyCombo("Alt+F4", listOf(ALT, KeyEvent.KEYCODE_F4)),
    XyCombo("Win+D", listOf(WIN, KeyEvent.KEYCODE_D)),
    XyCombo("Win+E", listOf(WIN, KeyEvent.KEYCODE_E)),
    XyCombo("Win+R", listOf(WIN, KeyEvent.KEYCODE_R)),
    XyCombo("Win+L", listOf(WIN, KeyEvent.KEYCODE_L)),
    XyCombo("PrtSc", listOf(KeyEvent.KEYCODE_SYSRQ)),
    XyCombo("Ctrl+C", listOf(CTRL, KeyEvent.KEYCODE_C)),
    XyCombo("Ctrl+V", listOf(CTRL, KeyEvent.KEYCODE_V)),
    XyCombo("Ctrl+X", listOf(CTRL, KeyEvent.KEYCODE_X)),
    XyCombo("Ctrl+A", listOf(CTRL, KeyEvent.KEYCODE_A)),
    XyCombo("Ctrl+Z", listOf(CTRL, KeyEvent.KEYCODE_Z)),
    XyCombo("Ctrl+S", listOf(CTRL, KeyEvent.KEYCODE_S)),
    XyCombo("Esc", listOf(KeyEvent.KEYCODE_ESCAPE)),
)

@Composable
fun SessionKeyboard(
    deviceId: String,
    scale: Float,
    haptics: Boolean,
    onKey: (code: Int, down: Boolean) -> Unit,
    onCombo: (codes: List<Int>) -> Unit,
    onScaleChange: (Float) -> Unit,
    onClose: () -> Unit,
) {
    val prefs = remember { SessionPrefs(LocalView.current.context) }
    val view = LocalView.current
    var page by remember { mutableStateOf(KeyPage.ABC) }
    var posX by remember { mutableStateOf(prefs.overlayX(deviceId)) }
    var posY by remember { mutableStateOf(prefs.overlayY(deviceId)) }
    // Modifier berdiri sendiri: nyala sekali pakai (one-shot) untuk key berikutnya.
    var sticky by remember { mutableStateOf(setOf<Int>()) }

    fun tap() {
        if (haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun pressKey(key: XyKey) {
        tap()
        val mods = sticky.toMutableSet()
        if (key.shift && !mods.contains(SHIFT)) mods += SHIFT
        mods.forEach { onKey(it, true) }
        onKey(key.code, true)
        onKey(key.code, false)
        mods.reversed().forEach { onKey(it, false) }
        if (sticky.isNotEmpty()) sticky = emptySet()
    }

    fun toggleModifier(code: Int) {
        tap()
        sticky = if (sticky.contains(code)) sticky - code else sticky + code
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val boardW = with(density) { (322f * scale).dp.toPx() }
        val boardH = with(density) { (250f * scale).dp.toPx() }
        val maxX = (constraints.maxWidth - boardW).coerceAtLeast(1f)
        val maxY = (constraints.maxHeight - boardH).coerceAtLeast(1f)

        Column(
            Modifier
                .offset {
                    IntOffset(
                        (posX * maxX).roundToInt(),
                        (posY * maxY).roundToInt(),
                    )
                }
                .width((322 * scale).dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xF00B0D10))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp * scale),
        ) {
            // ---- kepala: grip geser, tab halaman, ukuran, tutup
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                posX = (posX + drag.x / maxX).coerceIn(0f, 1f)
                                posY = (posY + drag.y / maxY).coerceIn(0f, 1f)
                                prefs.setOverlayPos(deviceId, posX, posY)
                            }
                        }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(
                        "GESER",
                        fontSize = 8.sp,
                        letterSpacing = 1.1.sp,
                        color = Color(0xFFB9C2CA),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                KeyPage.entries.forEach { p ->
                    TabChip(p.tab, active = p == page, scale) {
                        tap()
                        page = p
                    }
                }
                Box(Modifier.weight(1f))
                ChromeChip("-", scale) { onScaleChange((scale - 0.1f).coerceIn(0.7f, 1.6f)) }
                ChromeChip("+", scale) { onScaleChange((scale + 0.1f).coerceIn(0.7f, 1.6f)) }
                ChromeChip("×", scale) { onClose() }
            }

            when (page) {
                KeyPage.ABC -> QwertyPage(
                    scale = scale,
                    sticky = sticky,
                    onKey = { pressKey(it) },
                    onModifier = { toggleModifier(it) },
                )
                KeyPage.FN -> FnPage(scale) { pressKey(it) }
                KeyPage.NUM -> NumPage(scale) { pressKey(it) }
                KeyPage.SYM -> SymPage(scale) { pressKey(it) }
                KeyPage.COMBO -> ComboPage(scale) {
                    tap()
                    onCombo(it)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- halaman

@Composable
private fun QwertyPage(
    scale: Float,
    sticky: Set<Int>,
    onKey: (XyKey) -> Unit,
    onModifier: (Int) -> Unit,
) {
    val rows = listOf(
        listOf(
            XyKey("Esc", KeyEvent.KEYCODE_ESCAPE, chrome = true),
            XyKey("1", KeyEvent.KEYCODE_1), XyKey("2", KeyEvent.KEYCODE_2),
            XyKey("3", KeyEvent.KEYCODE_3), XyKey("4", KeyEvent.KEYCODE_4),
            XyKey("5", KeyEvent.KEYCODE_5), XyKey("6", KeyEvent.KEYCODE_6),
            XyKey("7", KeyEvent.KEYCODE_7), XyKey("8", KeyEvent.KEYCODE_8),
            XyKey("9", KeyEvent.KEYCODE_9), XyKey("0", KeyEvent.KEYCODE_0),
            XyKey("-", KeyEvent.KEYCODE_MINUS),
            XyKey("=", KeyEvent.KEYCODE_EQUALS),
            XyKey("⌫", KeyEvent.KEYCODE_DEL, wide = true),
        ),
        listOf(
            XyKey("Tab", KeyEvent.KEYCODE_TAB, chrome = true, wide = true),
            XyKey("q", KeyEvent.KEYCODE_Q), XyKey("w", KeyEvent.KEYCODE_W),
            XyKey("e", KeyEvent.KEYCODE_E), XyKey("r", KeyEvent.KEYCODE_R),
            XyKey("t", KeyEvent.KEYCODE_T), XyKey("y", KeyEvent.KEYCODE_Y),
            XyKey("u", KeyEvent.KEYCODE_U), XyKey("i", KeyEvent.KEYCODE_I),
            XyKey("o", KeyEvent.KEYCODE_O), XyKey("p", KeyEvent.KEYCODE_P),
            XyKey("[", KeyEvent.KEYCODE_LEFT_BRACKET),
            XyKey("]", KeyEvent.KEYCODE_RIGHT_BRACKET),
            XyKey("\\", KeyEvent.KEYCODE_BACKSLASH),
        ),
        listOf(
            XyKey("Caps", KeyEvent.KEYCODE_CAPS_LOCK, chrome = true, wide = true),
            XyKey("a", KeyEvent.KEYCODE_A), XyKey("s", KeyEvent.KEYCODE_S),
            XyKey("d", KeyEvent.KEYCODE_D), XyKey("f", KeyEvent.KEYCODE_F),
            XyKey("g", KeyEvent.KEYCODE_G), XyKey("h", KeyEvent.KEYCODE_H),
            XyKey("j", KeyEvent.KEYCODE_J), XyKey("k", KeyEvent.KEYCODE_K),
            XyKey("l", KeyEvent.KEYCODE_L),
            XyKey(";", KeyEvent.KEYCODE_SEMICOLON),
            XyKey("'", KeyEvent.KEYCODE_APOSTROPHE),
            XyKey("↵", KeyEvent.KEYCODE_ENTER, wide = true),
        ),
        listOf(
            XyKey("◀", KeyEvent.KEYCODE_DPAD_LEFT, chrome = true),
            XyKey("z", KeyEvent.KEYCODE_Z), XyKey("x", KeyEvent.KEYCODE_X),
            XyKey("c", KeyEvent.KEYCODE_C), XyKey("v", KeyEvent.KEYCODE_V),
            XyKey("b", KeyEvent.KEYCODE_B), XyKey("n", KeyEvent.KEYCODE_N),
            XyKey("m", KeyEvent.KEYCODE_M),
            XyKey(",", KeyEvent.KEYCODE_COMMA),
            XyKey(".", KeyEvent.KEYCODE_PERIOD),
            XyKey("/", KeyEvent.KEYCODE_SLASH),
            XyKey("▲", KeyEvent.KEYCODE_DPAD_UP, chrome = true),
            XyKey("▼", KeyEvent.KEYCODE_DPAD_DOWN, chrome = true),
            XyKey("▶", KeyEvent.KEYCODE_DPAD_RIGHT, chrome = true),
        ),
        listOf(
            XyKey("Ctrl", CTRL, chrome = true),
            XyKey("Win", WIN, chrome = true),
            XyKey("Alt", ALT, chrome = true),
            XyKey("Space", KeyEvent.KEYCODE_SPACE, weight = 4f),
            XyKey("⇧", SHIFT, chrome = true, wide = true),
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
        rows.forEach { row ->
            KeyRow(scale, row, sticky, onKey, onModifier)
        }
    }
}

@Composable
private fun FnPage(scale: Float, onKey: (XyKey) -> Unit) {
    val rows = listOf(
        (1..12).chunked(6).map { group ->
            group.map { n -> XyKey("F$n", KeyEvent.KEYCODE_F1 + (n - 1)) }
        },
        listOf(
            XyKey("Ins", KeyEvent.KEYCODE_INSERT),
            XyKey("Home", KeyEvent.KEYCODE_MOVE_HOME),
            XyKey("PgUp", KeyEvent.KEYCODE_PAGE_UP),
            XyKey("Del", KeyEvent.KEYCODE_FORWARD_DEL),
            XyKey("End", KeyEvent.KEYCODE_MOVE_END),
            XyKey("PgDn", KeyEvent.KEYCODE_PAGE_DOWN),
        ),
        listOf(
            XyKey("PrtSc", KeyEvent.KEYCODE_SYSRQ),
            XyKey("Menu", KeyEvent.KEYCODE_MENU),
            XyKey("◀", KeyEvent.KEYCODE_DPAD_LEFT),
            XyKey("▲", KeyEvent.KEYCODE_DPAD_UP),
            XyKey("▼", KeyEvent.KEYCODE_DPAD_DOWN),
            XyKey("▶", KeyEvent.KEYCODE_DPAD_RIGHT),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
        rows.forEach { row -> KeyRow(scale, row, emptySet(), false, onKey) {} }
    }
}

@Composable
private fun NumPage(scale: Float, onKey: (XyKey) -> Unit) {
    val rows = listOf(
        listOf(
            XyKey("Num", KeyEvent.KEYCODE_NUM_LOCK, chrome = true),
            XyKey("/", KeyEvent.KEYCODE_NUMPAD_DIVIDE),
            XyKey("*", KeyEvent.KEYCODE_NUMPAD_MULTIPLY),
            XyKey("-", KeyEvent.KEYCODE_NUMPAD_SUBTRACT),
        ),
        listOf(
            XyKey("7", KeyEvent.KEYCODE_NUMPAD_7),
            XyKey("8", KeyEvent.KEYCODE_NUMPAD_8),
            XyKey("9", KeyEvent.KEYCODE_NUMPAD_9),
            XyKey("+", KeyEvent.KEYCODE_NUMPAD_ADD),
        ),
        listOf(
            XyKey("4", KeyEvent.KEYCODE_NUMPAD_4),
            XyKey("5", KeyEvent.KEYCODE_NUMPAD_5),
            XyKey("6", KeyEvent.KEYCODE_NUMPAD_6),
            XyKey("=", KeyEvent.KEYCODE_NUMPAD_EQUALS),
        ),
        listOf(
            XyKey("1", KeyEvent.KEYCODE_NUMPAD_1),
            XyKey("2", KeyEvent.KEYCODE_NUMPAD_2),
            XyKey("3", KeyEvent.KEYCODE_NUMPAD_3),
            XyKey("↵", KeyEvent.KEYCODE_NUMPAD_ENTER),
        ),
        listOf(
            XyKey("0", KeyEvent.KEYCODE_NUMPAD_0, weight = 2f),
            XyKey(".", KeyEvent.KEYCODE_NUMPAD_DOT),
            XyKey("(", KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
        rows.forEach { row -> KeyRow(scale, row, emptySet(), onKey) {} }
    }
}

@Composable
private fun SymPage(scale: Float, onKey: (XyKey) -> Unit) {
    val rows = listOf(
        listOf(
            XyKey("!", KeyEvent.KEYCODE_1, shift = true),
            XyKey("@", KeyEvent.KEYCODE_2, shift = true),
            XyKey("#", KeyEvent.KEYCODE_3, shift = true),
            XyKey("$", KeyEvent.KEYCODE_4, shift = true),
            XyKey("%", KeyEvent.KEYCODE_5, shift = true),
            XyKey("^", KeyEvent.KEYCODE_6, shift = true),
            XyKey("&", KeyEvent.KEYCODE_7, shift = true),
            XyKey("*", KeyEvent.KEYCODE_8, shift = true),
            XyKey("(", KeyEvent.KEYCODE_9, shift = true),
            XyKey(")", KeyEvent.KEYCODE_0, shift = true),
        ),
        listOf(
            XyKey("_", KeyEvent.KEYCODE_MINUS, shift = true),
            XyKey("+", KeyEvent.KEYCODE_EQUALS, shift = true),
            XyKey("{", KeyEvent.KEYCODE_LEFT_BRACKET, shift = true),
            XyKey("}", KeyEvent.KEYCODE_RIGHT_BRACKET, shift = true),
            XyKey("|", KeyEvent.KEYCODE_BACKSLASH, shift = true),
            XyKey(":", KeyEvent.KEYCODE_SEMICOLON, shift = true),
            XyKey("\"", KeyEvent.KEYCODE_APOSTROPHE, shift = true),
            XyKey("<", KeyEvent.KEYCODE_COMMA, shift = true),
            XyKey(">", KeyEvent.KEYCODE_PERIOD, shift = true),
            XyKey("?", KeyEvent.KEYCODE_SLASH, shift = true),
        ),
        listOf(
            XyKey("`", KeyEvent.KEYCODE_GRAVE),
            XyKey("~", KeyEvent.KEYCODE_GRAVE, shift = true),
            XyKey("=", KeyEvent.KEYCODE_EQUALS),
            XyKey("-", KeyEvent.KEYCODE_MINUS),
            XyKey("[", KeyEvent.KEYCODE_LEFT_BRACKET),
            XyKey("]", KeyEvent.KEYCODE_RIGHT_BRACKET),
            XyKey("\\", KeyEvent.KEYCODE_BACKSLASH),
            XyKey(";", KeyEvent.KEYCODE_SEMICOLON),
            XyKey("'", KeyEvent.KEYCODE_APOSTROPHE),
            XyKey(",", KeyEvent.KEYCODE_COMMA),
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
        rows.forEach { row -> KeyRow(scale, row, emptySet(), onKey) {} }
    }
}

@Composable
private fun ComboPage(scale: Float, onCombo: (List<Int>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp * scale)) {
        combos.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp * scale),
            ) {
                row.forEach { combo ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height((34f * scale).dp)
                            .clip(RoundedCornerShape((7 * scale).dp))
                            .border(1.dp, Color(0x59FFFFFF), RoundedCornerShape((7 * scale).dp))
                            .clickable { onCombo(combo.codes) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            combo.label,
                            color = Color(0xFFE7EDF2),
                            fontSize = (10f * scale).sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- potongan UI

@Composable
private fun KeyRow(
    scale: Float,
    row: List<XyKey>,
    sticky: Set<Int>,
    onKey: (XyKey) -> Unit,
    onModifier: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp * scale),
    ) {
        row.forEach { key ->
            val active = sticky.contains(key.code)
            Box(
                Modifier
                    .weight(key.weight)
                    .height(((if (key.wide) 30f else 34f) * scale).dp)
                    .clip(RoundedCornerShape((7 * scale).dp))
                    .border(
                        1.dp,
                        if (active) Color(0xFFEFF3F6) else Color(0x59FFFFFF),
                        RoundedCornerShape((7 * scale).dp),
                    )
                    .pointerInput(key.code, sticky) {
                        detectTapGestures(
                            onPress = {
                                val isMod = key.code == CTRL || key.code == ALT ||
                                    key.code == SHIFT || key.code == WIN
                                if (isMod) {
                                    onModifier(key.code)
                                } else {
                                    onKey(key)
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    key.label,
                    color = if (key.chrome) Color(0xFF9AA4AD) else Color(0xFFEFF3F6),
                    fontSize = (12.5f * scale).sp,
                    fontWeight = if (key.chrome) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, if (active) Color(0xFFEFF3F6) else Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            color = if (active) Color(0xFFEFF3F6) else Color(0xFF9AA4AD),
            fontSize = (8.5f * scale).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun ChromeChip(label: String, scale: Float, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            color = Color(0xFFB9C2CA),
            fontSize = (10f * scale).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
