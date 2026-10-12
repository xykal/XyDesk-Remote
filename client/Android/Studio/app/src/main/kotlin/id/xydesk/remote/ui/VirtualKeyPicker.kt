package id.xydesk.remote.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton

/**
 * Keyboard virtual "super lengkap" untuk menyusun kombinasi tombol HUD
 * (permintaan pemilik 2026-10-12): seluruh papan ketik fisik — baris fungsi,
 * angka, QWERTY, modifier, navigasi, sampai numpad — tanpa batas urutan
 * (maksimal 24 langkah demi kewarasan). Ketuk = tambahkan ke urutan; ketuk
 * chip = hapus langkah itu.
 */
@Composable
internal fun VirtualKeyPicker(
    initial: List<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(initial.toList()) }

    XyOverlay(
        title = xy("Keyboard virtual", "Virtual keyboard"),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                xy(
                    "Ketuk tombol untuk menambahkannya ke urutan kombinasi. Ketuk chip untuk menghapus langkah.",
                    "Tap keys to append them to the combination. Tap a chip to remove that step.",
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- urutan terpilih ----
            if (picked.isEmpty()) {
                Text(
                    xy("Belum ada tombol dipilih.", "No keys picked yet."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 108.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    picked.chunked(4).forEachIndexed { rowIndex, rowCodes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowCodes.forEachIndexed { colIndex, code ->
                                val absoluteIndex = rowIndex * 4 + colIndex
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                        .clickable {
                                            picked = picked.filterIndexed { i, _ -> i != absoluteIndex }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        "${absoluteIndex + 1}. ${virtualKeyLabel(code)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- papan ketik ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                virtualKeyRows.forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        row.forEach { (code, label) ->
                            val keyWidth = if (label.length > 3) 56.dp else 38.dp
                            Box(
                                Modifier
                                    .width(keyWidth)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(7.dp),
                                    )
                                    .clickable { picked = (picked + code).take(MAX_COMBO_STEPS) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    label,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    text = xy("Hapus semua", "Clear all"),
                    onClick = { picked = emptyList() },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = xy("Batal", "Cancel"),
                    onClick = onDismiss,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = xy("Pakai ({0})", "Use ({0})", picked.size),
                    onClick = { onConfirm(picked) },
                    compact = true,
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Batas langkah kombinasi (dulu 5; kini nyaris bebas sesuai permintaan). */
internal const val MAX_COMBO_STEPS = 24

/** Label pendek tiap kode tombol — mencakup semua tombol di [virtualKeyRows]. */
internal fun virtualKeyLabel(code: Int): String = when (code) {
    KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl"
    KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift"
    KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> "Alt"
    KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> "Win"
    KeyEvent.KEYCODE_ESCAPE -> "Esc"
    KeyEvent.KEYCODE_TAB -> "Tab"
    KeyEvent.KEYCODE_CAPS_LOCK -> "Caps"
    KeyEvent.KEYCODE_SPACE -> "Spasi"
    KeyEvent.KEYCODE_ENTER -> "Enter"
    KeyEvent.KEYCODE_DEL -> "Bksp"
    KeyEvent.KEYCODE_FORWARD_DEL -> "Del"
    KeyEvent.KEYCODE_INSERT -> "Ins"
    KeyEvent.KEYCODE_MOVE_HOME -> "Home"
    KeyEvent.KEYCODE_MOVE_END -> "End"
    KeyEvent.KEYCODE_PAGE_UP -> "PgUp"
    KeyEvent.KEYCODE_PAGE_DOWN -> "PgDn"
    KeyEvent.KEYCODE_DPAD_UP -> "↑"
    KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
    KeyEvent.KEYCODE_DPAD_LEFT -> "←"
    KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
    KeyEvent.KEYCODE_SYSRQ -> "PrtSc"
    KeyEvent.KEYCODE_SCROLL_LOCK -> "ScrLk"
    KeyEvent.KEYCODE_BREAK -> "Pause"
    KeyEvent.KEYCODE_NUM_LOCK -> "Num"
    KeyEvent.KEYCODE_NUMPAD_DIVIDE -> "Num /"
    KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "Num *"
    KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> "Num -"
    KeyEvent.KEYCODE_NUMPAD_ADD -> "Num +"
    KeyEvent.KEYCODE_NUMPAD_ENTER -> "Num ⏎"
    KeyEvent.KEYCODE_NUMPAD_DOT -> "Num ."
    KeyEvent.KEYCODE_MINUS -> "-"
    KeyEvent.KEYCODE_EQUALS -> "="
    KeyEvent.KEYCODE_LEFT_BRACKET -> "["
    KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
    KeyEvent.KEYCODE_BACKSLASH -> "\\"
    KeyEvent.KEYCODE_SEMICOLON -> ";"
    KeyEvent.KEYCODE_APOSTROPHE -> "'"
    KeyEvent.KEYCODE_COMMA -> ","
    KeyEvent.KEYCODE_PERIOD -> "."
    KeyEvent.KEYCODE_SLASH -> "/"
    KeyEvent.KEYCODE_GRAVE -> "`"
    else -> HudKeyCatalog.keyName(code)
}

private typealias VkRow = List<Pair<Int, String>>

/** Susunan papan ketik penuh: fungsi, angka, QWERTY, modifier, navigasi, numpad. */
private val virtualKeyRows: List<VkRow> = listOf(
    listOf(
        KeyEvent.KEYCODE_ESCAPE to "Esc",
        KeyEvent.KEYCODE_F1 to "F1",
        KeyEvent.KEYCODE_F2 to "F2",
        KeyEvent.KEYCODE_F3 to "F3",
        KeyEvent.KEYCODE_F4 to "F4",
        KeyEvent.KEYCODE_F5 to "F5",
        KeyEvent.KEYCODE_F6 to "F6",
        KeyEvent.KEYCODE_F7 to "F7",
        KeyEvent.KEYCODE_F8 to "F8",
        KeyEvent.KEYCODE_F9 to "F9",
        KeyEvent.KEYCODE_F10 to "F10",
        KeyEvent.KEYCODE_F11 to "F11",
        KeyEvent.KEYCODE_F12 to "F12",
    ),
    listOf(
        KeyEvent.KEYCODE_GRAVE to "`",
        KeyEvent.KEYCODE_1 to "1",
        KeyEvent.KEYCODE_2 to "2",
        KeyEvent.KEYCODE_3 to "3",
        KeyEvent.KEYCODE_4 to "4",
        KeyEvent.KEYCODE_5 to "5",
        KeyEvent.KEYCODE_6 to "6",
        KeyEvent.KEYCODE_7 to "7",
        KeyEvent.KEYCODE_8 to "8",
        KeyEvent.KEYCODE_9 to "9",
        KeyEvent.KEYCODE_0 to "0",
        KeyEvent.KEYCODE_MINUS to "-",
        KeyEvent.KEYCODE_EQUALS to "=",
        KeyEvent.KEYCODE_DEL to "Bksp",
    ),
    listOf(
        KeyEvent.KEYCODE_TAB to "Tab",
        KeyEvent.KEYCODE_Q to "Q",
        KeyEvent.KEYCODE_W to "W",
        KeyEvent.KEYCODE_E to "E",
        KeyEvent.KEYCODE_R to "R",
        KeyEvent.KEYCODE_T to "T",
        KeyEvent.KEYCODE_Y to "Y",
        KeyEvent.KEYCODE_U to "U",
        KeyEvent.KEYCODE_I to "I",
        KeyEvent.KEYCODE_O to "O",
        KeyEvent.KEYCODE_P to "P",
        KeyEvent.KEYCODE_LEFT_BRACKET to "[",
        KeyEvent.KEYCODE_RIGHT_BRACKET to "]",
        KeyEvent.KEYCODE_BACKSLASH to "\\",
    ),
    listOf(
        KeyEvent.KEYCODE_CAPS_LOCK to "Caps",
        KeyEvent.KEYCODE_A to "A",
        KeyEvent.KEYCODE_S to "S",
        KeyEvent.KEYCODE_D to "D",
        KeyEvent.KEYCODE_F to "F",
        KeyEvent.KEYCODE_G to "G",
        KeyEvent.KEYCODE_H to "H",
        KeyEvent.KEYCODE_J to "J",
        KeyEvent.KEYCODE_K to "K",
        KeyEvent.KEYCODE_L to "L",
        KeyEvent.KEYCODE_SEMICOLON to ";",
        KeyEvent.KEYCODE_APOSTROPHE to "'",
        KeyEvent.KEYCODE_ENTER to "Enter",
    ),
    listOf(
        KeyEvent.KEYCODE_SHIFT_LEFT to "Shift",
        KeyEvent.KEYCODE_Z to "Z",
        KeyEvent.KEYCODE_X to "X",
        KeyEvent.KEYCODE_C to "C",
        KeyEvent.KEYCODE_V to "V",
        KeyEvent.KEYCODE_B to "B",
        KeyEvent.KEYCODE_N to "N",
        KeyEvent.KEYCODE_M to "M",
        KeyEvent.KEYCODE_COMMA to ",",
        KeyEvent.KEYCODE_PERIOD to ".",
        KeyEvent.KEYCODE_SLASH to "/",
        KeyEvent.KEYCODE_SHIFT_RIGHT to "Shift",
    ),
    listOf(
        KeyEvent.KEYCODE_CTRL_LEFT to "Ctrl",
        KeyEvent.KEYCODE_META_LEFT to "Win",
        KeyEvent.KEYCODE_ALT_LEFT to "Alt",
        KeyEvent.KEYCODE_SPACE to "Spasi",
        KeyEvent.KEYCODE_ALT_RIGHT to "Alt",
        KeyEvent.KEYCODE_CTRL_RIGHT to "Ctrl",
    ),
    listOf(
        KeyEvent.KEYCODE_SYSRQ to "PrtSc",
        KeyEvent.KEYCODE_SCROLL_LOCK to "ScrLk",
        KeyEvent.KEYCODE_BREAK to "Pause",
        KeyEvent.KEYCODE_INSERT to "Ins",
        KeyEvent.KEYCODE_MOVE_HOME to "Home",
        KeyEvent.KEYCODE_PAGE_UP to "PgUp",
        KeyEvent.KEYCODE_FORWARD_DEL to "Del",
        KeyEvent.KEYCODE_MOVE_END to "End",
        KeyEvent.KEYCODE_PAGE_DOWN to "PgDn",
    ),
    listOf(
        KeyEvent.KEYCODE_DPAD_UP to "↑",
        KeyEvent.KEYCODE_DPAD_LEFT to "←",
        KeyEvent.KEYCODE_DPAD_DOWN to "↓",
        KeyEvent.KEYCODE_DPAD_RIGHT to "→",
    ),
    listOf(
        KeyEvent.KEYCODE_NUM_LOCK to "Num",
        KeyEvent.KEYCODE_NUMPAD_DIVIDE to "Num /",
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY to "Num *",
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT to "Num -",
    ),
    listOf(
        KeyEvent.KEYCODE_NUMPAD_7 to "7",
        KeyEvent.KEYCODE_NUMPAD_8 to "8",
        KeyEvent.KEYCODE_NUMPAD_9 to "9",
        KeyEvent.KEYCODE_NUMPAD_ADD to "Num +",
    ),
    listOf(
        KeyEvent.KEYCODE_NUMPAD_4 to "4",
        KeyEvent.KEYCODE_NUMPAD_5 to "5",
        KeyEvent.KEYCODE_NUMPAD_6 to "6",
        KeyEvent.KEYCODE_NUMPAD_ENTER to "Num ⏎",
    ),
    listOf(
        KeyEvent.KEYCODE_NUMPAD_1 to "1",
        KeyEvent.KEYCODE_NUMPAD_2 to "2",
        KeyEvent.KEYCODE_NUMPAD_3 to "3",
        KeyEvent.KEYCODE_NUMPAD_DOT to "Num .",
    ),
    listOf(
        KeyEvent.KEYCODE_NUMPAD_0 to "0",
    ),
)
