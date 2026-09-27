package id.xydesk.remote.ui

import android.view.KeyEvent
import id.xydesk.remote.core.XyAudioMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Model tombol HUD sesi.
 *
 * Aturan produk: SATU tombol = SATU aksi, bentuknya bulat penuh, dan tiap
 * tombol bisa digeser, diubah ukurannya, serta diatur aksinya sendiri
 * (sekali klik / tahan / toggle). Tidak ada tombol gabungan.
 *
 * Semua tombol disimpan per perangkat (layar sesi bisa beda-beda layout).
 */
enum class HudAction(val title: String, val detail: String) {
    TAP("Sekali klik", "Tekan lalu lepas"),
    HOLD("Tahan", "Aktif selama ditahan (drag, pilih banyak)"),
    TOGGLE("Toggle", "Sekali klik nyala, klik lagi mati"),
}

enum class HudKind(val title: String) {
    MOUSE_LEFT("Klik kiri"),
    MOUSE_RIGHT("Klik kanan"),
    MOUSE_MIDDLE("Klik tengah"),
    SCROLL_UP("Scroll naik"),
    SCROLL_DOWN("Scroll turun"),
    INPUT_SWITCH("Ganti mode input"),
    KEY("Tombol keyboard"),
    COMBO("Kombinasi"),
}

data class HudKey(
    val id: String,
    val kind: HudKind,
    val label: String,
    val keyCode: Int = 0,
    val shift: Boolean = false,
    val combo: List<Int> = emptyList(),
    val action: HudAction = HudAction.TAP,
    /** Posisi ternormalisasi 0..1 dari kiri-atas area sesi. */
    val x: Float = 0.78f,
    val y: Float = 0.42f,
    /** Diameter tombol (dp). Bulat penuh: radius = setengah diameter. */
    val size: Float = 48f,
    /** Ikut tampil di toolbar yang menempel di atas keyboard HP. */
    val inToolbar: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.name)
        put("label", label)
        put("code", keyCode)
        put("shift", shift)
        put("combo", JSONArray(combo))
        put("action", action.name)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("size", size.toDouble())
        put("toolbar", inToolbar)
    }

    companion object {
        fun fromJson(o: JSONObject): HudKey? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val kind = runCatching { HudKind.valueOf(o.optString("kind")) }.getOrNull() ?: return null
            val action = runCatching { HudAction.valueOf(o.optString("action")) }
                .getOrDefault(HudAction.TAP)
            val comboJson = o.optJSONArray("combo") ?: JSONArray()
            val combo = (0 until comboJson.length()).map { comboJson.optInt(it) }
            return HudKey(
                id = id,
                kind = kind,
                label = o.optString("label", kind.title),
                keyCode = o.optInt("code"),
                shift = o.optBoolean("shift"),
                combo = combo,
                action = action,
                x = o.optDouble("x", 0.78).toFloat().coerceIn(0f, 1f),
                y = o.optDouble("y", 0.42).toFloat().coerceIn(0f, 1f),
                size = o.optDouble("size", 48.0).toFloat().coerceIn(32f, 96f),
                inToolbar = o.optBoolean("toolbar", true),
            )
        }

        fun encode(list: List<HudKey>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun decode(raw: String?): List<HudKey>? {
            if (raw.isNullOrBlank()) return null
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.let { fromJson(it) }
                }
            }.getOrNull()
        }

        /**
         * Set awal: klik kiri, klik kanan, klik tengah, scroll naik, scroll
         * turun, ganti mode input. Semua terpisah, tersusun satu kolom di
         * kanan layar; user bebas geser/ubah/ tambah.
         */
        fun defaults(): List<HudKey> = listOf(
            HudKey("kiri", HudKind.MOUSE_LEFT, "Kiri", x = 0.84f, y = 0.40f),
            HudKey("kanan", HudKind.MOUSE_RIGHT, "Kanan", x = 0.84f, y = 0.52f),
            HudKey("tengah", HudKind.MOUSE_MIDDLE, "Tengah", x = 0.84f, y = 0.64f),
            HudKey(
                "naik", HudKind.SCROLL_UP, "Naik",
                action = HudAction.HOLD, x = 0.72f, y = 0.40f,
            ),
            HudKey(
                "turun", HudKind.SCROLL_DOWN, "Turun",
                action = HudAction.HOLD, x = 0.72f, y = 0.52f,
            ),
            HudKey(
                "switch", HudKind.INPUT_SWITCH, "Mode",
                x = 0.72f, y = 0.64f,
            ),
        )
    }
}

/**
 * Tombol mouse yang bisa dikirim HUD. Dulu dideklarasikan di SessionControls
 * lama; sekarang di model HUD karena HUD yang memakainya.
 */
enum class XyMouseButton { LEFT, RIGHT, MIDDLE }

/** Satu pilihan di daftar "tambah tombol". */
data class HudKeyOption(
    val group: String,
    val label: String,
    val kind: HudKind,
    val keyCode: Int = 0,
    val shift: Boolean = false,
    val combo: List<Int> = emptyList(),
)

/**
 * Katalog tombol yang bisa ditambahkan. Sengaja pilih yang benar-benar
 * dipetakan KeyboardMapper inti; tombol eksotis yang tidak ada padanannya
 * dibuang supaya tidak jadi tombol mati.
 */
object HudKeyCatalog {

    private const val CTRL = KeyEvent.KEYCODE_CTRL_LEFT
    private const val SHIFT = KeyEvent.KEYCODE_SHIFT_LEFT
    private const val ALT = KeyEvent.KEYCODE_ALT_LEFT
    private const val WIN = KeyEvent.KEYCODE_META_LEFT

    val mouse = listOf(
        HudKeyOption("Aksi", "Klik kiri", HudKind.MOUSE_LEFT),
        HudKeyOption("Aksi", "Klik kanan", HudKind.MOUSE_RIGHT),
        HudKeyOption("Aksi", "Klik tengah", HudKind.MOUSE_MIDDLE),
        HudKeyOption("Aksi", "Scroll naik", HudKind.SCROLL_UP),
        HudKeyOption("Aksi", "Scroll turun", HudKind.SCROLL_DOWN),
        HudKeyOption("Aksi", "Ganti mode input", HudKind.INPUT_SWITCH),
    )

    val modifiers = listOf(
        HudKeyOption("Modifier", "Ctrl", HudKind.KEY, CTRL),
        HudKeyOption("Modifier", "Shift", HudKind.KEY, SHIFT),
        HudKeyOption("Modifier", "Alt", HudKind.KEY, ALT),
        HudKeyOption("Modifier", "Win", HudKind.KEY, WIN),
    )

    val general = listOf(
        HudKeyOption("Umum", "Esc", HudKind.KEY, KeyEvent.KEYCODE_ESCAPE),
        HudKeyOption("Umum", "Tab", HudKind.KEY, KeyEvent.KEYCODE_TAB),
        HudKeyOption("Umum", "Enter", HudKind.KEY, KeyEvent.KEYCODE_ENTER),
        HudKeyOption("Umum", "Backspace", HudKind.KEY, KeyEvent.KEYCODE_DEL),
        HudKeyOption("Umum", "Del", HudKind.KEY, KeyEvent.KEYCODE_FORWARD_DEL),
        HudKeyOption("Umum", "Space", HudKind.KEY, KeyEvent.KEYCODE_SPACE),
        HudKeyOption("Umum", "PrtSc", HudKind.KEY, KeyEvent.KEYCODE_SYSRQ),
        HudKeyOption("Umum", "Menu", HudKind.KEY, KeyEvent.KEYCODE_MENU),
        HudKeyOption("Umum", "Caps", HudKind.KEY, KeyEvent.KEYCODE_CAPS_LOCK),
        HudKeyOption("Umum", "Ins", HudKind.KEY, KeyEvent.KEYCODE_INSERT),
        HudKeyOption("Umum", "Home", HudKind.KEY, KeyEvent.KEYCODE_MOVE_HOME),
        HudKeyOption("Umum", "End", HudKind.KEY, KeyEvent.KEYCODE_MOVE_END),
        HudKeyOption("Umum", "PgUp", HudKind.KEY, KeyEvent.KEYCODE_PAGE_UP),
        HudKeyOption("Umum", "PgDn", HudKind.KEY, KeyEvent.KEYCODE_PAGE_DOWN),
    )

    val arrows = listOf(
        HudKeyOption("Panah", "\u25c0", HudKind.KEY, KeyEvent.KEYCODE_DPAD_LEFT),
        HudKeyOption("Panah", "\u25b2", HudKind.KEY, KeyEvent.KEYCODE_DPAD_UP),
        HudKeyOption("Panah", "\u25bc", HudKind.KEY, KeyEvent.KEYCODE_DPAD_DOWN),
        HudKeyOption("Panah", "\u25b6", HudKind.KEY, KeyEvent.KEYCODE_DPAD_RIGHT),
    )

    val functionKeys = (1..12).map {
        HudKeyOption("Fungsi", "F$it", HudKind.KEY, KeyEvent.KEYCODE_F1 + (it - 1))
    }

    val numpad = listOf(
        HudKeyOption("Numpad", "Num", HudKind.KEY, KeyEvent.KEYCODE_NUM_LOCK),
        HudKeyOption("Numpad", "/", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_DIVIDE),
        HudKeyOption("Numpad", "*", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_MULTIPLY),
        HudKeyOption("Numpad", "-", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_SUBTRACT),
        HudKeyOption("Numpad", "+", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_ADD),
        HudKeyOption("Numpad", ".", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_DOT),
        HudKeyOption("Numpad", "↵", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_ENTER),
    ) + (0..9).map {
        HudKeyOption("Numpad", "$it", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_0 + it)
    }

    val letters = ('A'..'Z').map { ch ->
        HudKeyOption("Huruf", "$ch", HudKind.KEY, KeyEvent.KEYCODE_A + (ch - 'A'))
    }

    val symbols = listOf(
        HudKeyOption("Simbol", "/", HudKind.KEY, KeyEvent.KEYCODE_SLASH),
        HudKeyOption("Simbol", "\\", HudKind.KEY, KeyEvent.KEYCODE_BACKSLASH),
        HudKeyOption("Simbol", "-", HudKind.KEY, KeyEvent.KEYCODE_MINUS),
        HudKeyOption("Simbol", "=", HudKind.KEY, KeyEvent.KEYCODE_EQUALS),
        HudKeyOption("Simbol", "[", HudKind.KEY, KeyEvent.KEYCODE_LEFT_BRACKET),
        HudKeyOption("Simbol", "]", HudKind.KEY, KeyEvent.KEYCODE_RIGHT_BRACKET),
        HudKeyOption("Simbol", ";", HudKind.KEY, KeyEvent.KEYCODE_SEMICOLON),
        HudKeyOption("Simbol", "'", HudKind.KEY, KeyEvent.KEYCODE_APOSTROPHE),
        HudKeyOption("Simbol", ",", HudKind.KEY, KeyEvent.KEYCODE_COMMA),
        HudKeyOption("Simbol", ".", HudKind.KEY, KeyEvent.KEYCODE_PERIOD),
        HudKeyOption("Simbol", "`", HudKind.KEY, KeyEvent.KEYCODE_GRAVE),
    )

    val combos = listOf(
        HudKeyOption("Kombinasi", "Ctrl+C", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_C)),
        HudKeyOption("Kombinasi", "Ctrl+V", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_V)),
        HudKeyOption("Kombinasi", "Ctrl+X", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_X)),
        HudKeyOption("Kombinasi", "Ctrl+Z", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_Z)),
        HudKeyOption("Kombinasi", "Ctrl+A", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_A)),
        HudKeyOption("Kombinasi", "Ctrl+S", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_S)),
        HudKeyOption("Kombinasi", "Ctrl+F", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_F)),
        HudKeyOption("Kombinasi", "Ctrl+W", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_W)),
        HudKeyOption("Kombinasi", "Ctrl+Shift+Esc", HudKind.COMBO, combo = listOf(CTRL, SHIFT, KeyEvent.KEYCODE_ESCAPE)),
        HudKeyOption("Kombinasi", "Ctrl+Alt+Del", HudKind.COMBO, combo = listOf(CTRL, ALT, KeyEvent.KEYCODE_FORWARD_DEL)),
        HudKeyOption("Kombinasi", "Alt+Tab", HudKind.COMBO, combo = listOf(ALT, KeyEvent.KEYCODE_TAB)),
        HudKeyOption("Kombinasi", "Alt+F4", HudKind.COMBO, combo = listOf(ALT, KeyEvent.KEYCODE_F4)),
        HudKeyOption("Kombinasi", "Win+D", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_D)),
        HudKeyOption("Kombinasi", "Win+E", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_E)),
        HudKeyOption("Kombinasi", "Win+R", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_R)),
        HudKeyOption("Kombinasi", "Win+L", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_L)),
        HudKeyOption("Kombinasi", "Win+Tab", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_TAB)),
    )

    val groups: List<Pair<String, List<HudKeyOption>>> = listOf(
        "Aksi mouse & scroll" to mouse,
        "Modifier" to modifiers,
        "Kombinasi siap pakai" to combos,
        "Umum" to general,
        "Panah" to arrows,
        "F1 - F12" to functionKeys,
        "Numpad" to numpad,
        "Huruf" to letters,
        "Simbol" to symbols,
    )

    /** Dipakai dialog "ubah tombol": label ringkas untuk aksi non-keyboard. */
    fun labelFor(kind: HudKind): String = when (kind) {
        HudKind.MOUSE_LEFT -> "Kiri"
        HudKind.MOUSE_RIGHT -> "Kanan"
        HudKind.MOUSE_MIDDLE -> "Tengah"
        HudKind.SCROLL_UP -> "Naik"
        HudKind.SCROLL_DOWN -> "Turun"
        HudKind.INPUT_SWITCH -> "Mode"
        else -> "Tombol"
    }
}

/** Satu-satunya tempat yang tahu arti XyAudioMode buat label UI. */
internal fun XyAudioMode.shortTitle(): String = when (this) {
    XyAudioMode.DEVICE -> "Perangkat"
    XyAudioMode.REMOTE -> "Remote"
    XyAudioMode.OFF -> "Mati"
}
