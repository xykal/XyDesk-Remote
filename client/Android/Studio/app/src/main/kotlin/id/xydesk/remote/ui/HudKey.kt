package id.xydesk.remote.ui

import android.view.KeyEvent
import id.xydesk.remote.core.XyAudioMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Model tombol HUD sesi.
 *
 * Aturan produk: SATU tombol = SATU aksi, berbentuk lingkaran dengan aksen
 * kontras, dan tiap tombol bisa digeser, diubah ukurannya, serta diatur aksinya sendiri
 * (sekali klik / tahan / toggle). Tidak ada tombol gabungan.
 *
 * Semua tombol disimpan per perangkat (layar sesi bisa beda-beda layout).
 */
enum class HudAction(val title: String, val titleEn: String, val detail: String, val detailEn: String) {
    TAP("Sekali klik", "Single tap", "Tekan lalu lepas", "Press and release"),
    ONE_SHOT("Sekali pakai", "One-shot", "Aktif untuk 1 tombol/klik berikutnya lalu lepas otomatis", "Arms for the next key/click then auto-releases"),
    HOLD("Tahan", "Hold", "Aktif selama ditahan (drag, pilih banyak)", "Active while held (drag, multi-select)"),
    TOGGLE("Toggle", "Toggle", "Sekali klik nyala, klik lagi mati", "Tap to turn on, tap again to turn off"),
}

enum class HudKind(val title: String, val titleEn: String) {
    MOUSE_LEFT("Klik kiri", "Left click"),
    MOUSE_RIGHT("Klik kanan", "Right click"),
    MOUSE_MIDDLE("Klik tengah", "Middle click"),
    SCROLL_UP("Scroll naik", "Scroll up"),
    SCROLL_DOWN("Scroll turun", "Scroll down"),
    SCROLL_SLIDER("Geser scroll", "Swipe to scroll"),
    INPUT_SWITCH("Ganti mode input", "Switch input mode"),
    KEYBOARD("Buka keyboard", "Show keyboard"),
    KEY("Tombol keyboard", "Keyboard key"),
    COMBO("Kombinasi", "Combo"),
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
    /** Diameter tombol HUD (dp). */
    val size: Float = 64f,
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
    }

    companion object {
        fun fromJson(o: JSONObject): HudKey? {
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return null
            val kind = runCatching { HudKind.valueOf(o.optString("kind")) }.getOrNull() ?: return null
            val parsedAction = runCatching { HudAction.valueOf(o.optString("action")) }
                .getOrDefault(HudAction.TAP)
            val action = parsedAction.takeIf { it in allowedActions(kind) } ?: HudAction.TAP
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
                size = o.optDouble("size", 64.0).toFloat().coerceIn(56f, 120f),
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

        /** Set awal 2x3 lingkaran; jarak baris aman juga di layar landscape pendek. */
        fun defaults(size: Float = 64f): List<HudKey> {
            val diameter = size.coerceIn(56f, 80f)
            return listOf(
                HudKey("kiri", HudKind.MOUSE_LEFT, "Kiri", x = 0.12f, y = 0.12f),
                HudKey("kanan", HudKind.MOUSE_RIGHT, "Kanan", x = 0.50f, y = 0.12f),
                HudKey("tengah", HudKind.MOUSE_MIDDLE, "Tengah", x = 0.12f, y = 0.44f),
                HudKey("naik", HudKind.SCROLL_UP, "Naik", x = 0.50f, y = 0.44f),
                HudKey("turun", HudKind.SCROLL_DOWN, "Turun", x = 0.12f, y = 0.76f),
                HudKey("switch", HudKind.INPUT_SWITCH, "Mode", x = 0.50f, y = 0.76f),
            ).map { it.copy(size = diameter) }
        }

        /** Action yang benar-benar punya makna untuk jenis kontrol terkait. */
        fun allowedActions(kind: HudKind): List<HudAction> = when (kind) {
            HudKind.KEY ->
                listOf(HudAction.TAP, HudAction.ONE_SHOT, HudAction.HOLD, HudAction.TOGGLE)
            HudKind.MOUSE_LEFT, HudKind.MOUSE_RIGHT, HudKind.MOUSE_MIDDLE ->
                listOf(HudAction.TAP, HudAction.HOLD, HudAction.TOGGLE)
            else -> listOf(HudAction.TAP)
        }

        fun isModifierKeyCode(keyCode: Int): Boolean = keyCode in setOf(
            KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT,
            KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_META_LEFT,
            KeyEvent.KEYCODE_META_RIGHT,
        )

        fun exportLayoutJson(list: List<HudKey>): String = JSONObject().apply {
            put("app", "XyDesk Remote")
            put("version", 1)
            val arr = JSONArray()
            list.take(36).forEach { arr.put(it.toJson()) }
            put("keys", arr)
        }.toString()

        fun importLayoutJson(raw: String?, defaultSize: Float = 64f): List<HudKey>? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val arr = runCatching {
                if (trimmed.startsWith("{")) {
                    JSONObject(trimmed).optJSONArray("keys")
                } else {
                    JSONArray(trimmed)
                }
            }.getOrNull() ?: return null
            val seenIds = mutableSetOf<String>()
            val parsed = (0 until minOf(arr.length(), 36)).mapNotNull { i ->
                val item = arr.optJSONObject(i)?.let { fromJson(it) } ?: return@mapNotNull null
                if (item.kind == HudKind.KEYBOARD || item.id.startsWith("aux_")) return@mapNotNull null
                val uniqueId = if (seenIds.add(item.id)) item.id else "${item.id}_$i"
                seenIds.add(uniqueId)
                item.copy(
                    id = uniqueId,
                    size = item.size.coerceIn(56f, 120f).takeIf { it > 0f } ?: defaultSize.coerceIn(56f, 120f),
                )
            }
            return parsed.takeIf { it.isNotEmpty() }
        }

        /**
         * Layout lama (ronde 4 dan sebelumnya) memakai konsep "baris atas
         * keyboard" dengan id `aux_*`. Baris itu sudah dihapus dari produk,
         * jadi entri `aux_*` dibuang. Posisi/ukuran kontrol yang sudah diatur
         * user dipertahankan; aksi yang tidak didukung dinormalisasi.
         *
         * Kalau hasilnya kosong (semua tombol lama cuma baris atas), pakai
         * set bawaan supaya layar tidak kosong tanpa kontrol.
         */
        fun migrate(list: List<HudKey>, defaultSize: Float = 76f): List<HudKey> {
            if (list.isEmpty()) return defaults(defaultSize)
            val legacyStock = list.size == 7 && list.all { key ->
                val stock = when (key.id) {
                    "kiri" -> key.kind == HudKind.MOUSE_LEFT && key.label == "Kiri" && key.x == 0.84f && key.y == 0.40f
                    "kanan" -> key.kind == HudKind.MOUSE_RIGHT && key.label == "Kanan" && key.x == 0.84f && key.y == 0.52f
                    "tengah" -> key.kind == HudKind.MOUSE_MIDDLE && key.label == "Tengah" && key.x == 0.84f && key.y == 0.64f
                    "naik" -> key.kind == HudKind.SCROLL_UP && key.label == "Naik" && key.x == 0.72f && key.y == 0.40f
                    "turun" -> key.kind == HudKind.SCROLL_DOWN && key.label == "Turun" && key.x == 0.72f && key.y == 0.52f
                    "switch" -> key.kind == HudKind.INPUT_SWITCH && key.label == "Mode" && key.x == 0.72f && key.y == 0.64f
                    "keyboard" -> key.kind == HudKind.KEYBOARD && key.label == "Keyboard" && key.x == 0.84f && key.y == 0.76f
                    else -> false
                }
                stock && key.action == HudAction.TAP && key.size == 56f && key.keyCode == 0 &&
                    !key.shift && key.combo.isEmpty()
            }
            if (legacyStock) return defaults(defaultSize)
            val kept = list.filterNot {
                it.id.startsWith("aux_") || it.id == "keyboard" || it.kind == HudKind.KEYBOARD
            }
            if (kept.isEmpty()) return defaults(defaultSize)
            // Keyboard HP selalu tersedia lewat rail tetap; jangan menambahkan
            // salinan HUD bawaan yang dulu bertabrakan dengan rail kanan-bawah.
            return kept.map { key ->
                if (key.action in allowedActions(key.kind)) key
                else key.copy(action = HudAction.TAP)
            }
        }
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
    /** Label bahasa Inggris; label utama bahasa Indonesia. */
    val labelEn: String = label,
    val defaultAction: HudAction = HudAction.TAP,
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
        HudKeyOption("Aksi", "Klik kiri", HudKind.MOUSE_LEFT, labelEn = "Left click"),
        HudKeyOption("Aksi", "Klik kanan", HudKind.MOUSE_RIGHT, labelEn = "Right click"),
        HudKeyOption("Aksi", "Klik tengah", HudKind.MOUSE_MIDDLE, labelEn = "Middle click"),
        HudKeyOption("Aksi", "Scroll naik", HudKind.SCROLL_UP, labelEn = "Scroll up"),
        HudKeyOption("Aksi", "Scroll turun", HudKind.SCROLL_DOWN, labelEn = "Scroll down"),
        HudKeyOption("Aksi", "Geser scroll", HudKind.SCROLL_SLIDER, labelEn = "Swipe to scroll"),
        HudKeyOption("Aksi", "Ganti mode input", HudKind.INPUT_SWITCH, labelEn = "Switch input mode"),
        HudKeyOption(
            "Aksi",
            "Klik kiri (tahan = drag)",
            HudKind.MOUSE_LEFT,
            labelEn = "Left click (hold = drag)",
            defaultAction = HudAction.HOLD,
        ),
    )

    val modifiers = listOf(
        HudKeyOption("Modifier", "Ctrl", HudKind.KEY, CTRL, defaultAction = HudAction.TOGGLE),
        HudKeyOption("Modifier", "Shift", HudKind.KEY, SHIFT, defaultAction = HudAction.TOGGLE),
        HudKeyOption("Modifier", "Alt", HudKind.KEY, ALT, defaultAction = HudAction.TOGGLE),
        HudKeyOption("Modifier", "Win", HudKind.KEY, WIN, defaultAction = HudAction.TOGGLE),
        HudKeyOption("Modifier", "Ctrl (1x)", HudKind.KEY, CTRL, labelEn = "Ctrl (1x)", defaultAction = HudAction.ONE_SHOT),
        HudKeyOption("Modifier", "Shift (1x)", HudKind.KEY, SHIFT, labelEn = "Shift (1x)", defaultAction = HudAction.ONE_SHOT),
        HudKeyOption("Modifier", "Alt (1x)", HudKind.KEY, ALT, labelEn = "Alt (1x)", defaultAction = HudAction.ONE_SHOT),
        HudKeyOption("Modifier", "Win (1x)", HudKind.KEY, WIN, labelEn = "Win (1x)", defaultAction = HudAction.ONE_SHOT),
    )

    val general = listOf(
        HudKeyOption("Single Key", "Esc", HudKind.KEY, KeyEvent.KEYCODE_ESCAPE),
        HudKeyOption("Single Key", "Tab", HudKind.KEY, KeyEvent.KEYCODE_TAB),
        HudKeyOption("Single Key", "Enter", HudKind.KEY, KeyEvent.KEYCODE_ENTER),
        HudKeyOption("Single Key", "Backspace", HudKind.KEY, KeyEvent.KEYCODE_DEL),
        HudKeyOption("Single Key", "Del", HudKind.KEY, KeyEvent.KEYCODE_FORWARD_DEL),
        HudKeyOption("Single Key", "Space", HudKind.KEY, KeyEvent.KEYCODE_SPACE),
        HudKeyOption("Single Key", "Ins", HudKind.KEY, KeyEvent.KEYCODE_INSERT),
        HudKeyOption("Single Key", "Home", HudKind.KEY, KeyEvent.KEYCODE_MOVE_HOME),
        HudKeyOption("Single Key", "End", HudKind.KEY, KeyEvent.KEYCODE_MOVE_END),
        HudKeyOption("Single Key", "PgUp", HudKind.KEY, KeyEvent.KEYCODE_PAGE_UP),
        HudKeyOption("Single Key", "PgDn", HudKind.KEY, KeyEvent.KEYCODE_PAGE_DOWN),
        HudKeyOption("Single Key", "PrtSc", HudKind.KEY, KeyEvent.KEYCODE_SYSRQ),
        HudKeyOption("Single Key", "Pause", HudKind.KEY, KeyEvent.KEYCODE_BREAK),
        HudKeyOption("Single Key", "ScrLk", HudKind.KEY, KeyEvent.KEYCODE_SCROLL_LOCK),
        HudKeyOption("Single Key", "Menu", HudKind.KEY, KeyEvent.KEYCODE_MENU),
        HudKeyOption("Single Key", "Caps", HudKind.KEY, KeyEvent.KEYCODE_CAPS_LOCK),
    )

    val arrows = listOf(
        HudKeyOption("Panah", "\u25c0", HudKind.KEY, KeyEvent.KEYCODE_DPAD_LEFT),
        HudKeyOption("Panah", "\u25b2", HudKind.KEY, KeyEvent.KEYCODE_DPAD_UP),
        HudKeyOption("Panah", "\u25bc", HudKind.KEY, KeyEvent.KEYCODE_DPAD_DOWN),
        HudKeyOption("Panah", "\u25b6", HudKind.KEY, KeyEvent.KEYCODE_DPAD_RIGHT),
    )

    val topNumbers = (0..9).map { digit ->
        HudKeyOption("Angka", "$digit", HudKind.KEY, KeyEvent.KEYCODE_0 + digit)
    }

    val functionKeys = (1..12).map {
        HudKeyOption("F1 - F12", "F$it", HudKind.KEY, KeyEvent.KEYCODE_F1 + (it - 1))
    }

    val numpad = (0..9).map {
        HudKeyOption("Numpad", "Num $it", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_0 + it, labelEn = "Num $it")
    } + listOf(
        HudKeyOption("Numpad", "NumLk", HudKind.KEY, KeyEvent.KEYCODE_NUM_LOCK, labelEn = "NumLk"),
        HudKeyOption("Numpad", "Num /", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_DIVIDE, labelEn = "Num /"),
        HudKeyOption("Numpad", "Num *", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_MULTIPLY, labelEn = "Num *"),
        HudKeyOption("Numpad", "Num -", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, labelEn = "Num -"),
        HudKeyOption("Numpad", "Num +", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_ADD, labelEn = "Num +"),
        HudKeyOption("Numpad", "Num .", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_DOT, labelEn = "Num ."),
        HudKeyOption("Numpad", "Num \u21b5", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_ENTER, labelEn = "Num \u21b5"),
        HudKeyOption("Numpad", "Num =", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_EQUALS, labelEn = "Num ="),
        HudKeyOption("Numpad", "Num (", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN, labelEn = "Num ("),
        HudKeyOption("Numpad", "Num )", HudKind.KEY, KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN, labelEn = "Num )"),
    )

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
        HudKeyOption("Simbol", "*", HudKind.KEY, KeyEvent.KEYCODE_STAR),
        HudKeyOption("Simbol", "+", HudKind.KEY, KeyEvent.KEYCODE_PLUS),
        HudKeyOption("Simbol", "@", HudKind.KEY, KeyEvent.KEYCODE_AT),
        HudKeyOption("Simbol", "#", HudKind.KEY, KeyEvent.KEYCODE_POUND),
    )

    val singleKeys = general + arrows + topNumbers + symbols

    val combos = listOf(
        HudKeyOption("Kombinasi", "Ctrl+C", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_C)),
        HudKeyOption("Kombinasi", "Ctrl+V", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_V)),
        HudKeyOption("Kombinasi", "Ctrl+X", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_X)),
        HudKeyOption("Kombinasi", "Ctrl+Z", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_Z)),
        HudKeyOption("Kombinasi", "Ctrl+Y", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_Y)),
        HudKeyOption("Kombinasi", "Ctrl+A", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_A)),
        HudKeyOption("Kombinasi", "Ctrl+S", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_S)),
        HudKeyOption("Kombinasi", "Ctrl+F", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_F)),
        HudKeyOption("Kombinasi", "Ctrl+P", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_P)),
        HudKeyOption("Kombinasi", "Ctrl+W", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_W)),
        HudKeyOption("Kombinasi", "Ctrl+T", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_T)),
        HudKeyOption("Kombinasi", "Ctrl+N", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_N)),
        HudKeyOption("Kombinasi", "Ctrl+R", HudKind.COMBO, combo = listOf(CTRL, KeyEvent.KEYCODE_R)),
        HudKeyOption("Kombinasi", "Ctrl+Shift+T", HudKind.COMBO, combo = listOf(CTRL, SHIFT, KeyEvent.KEYCODE_T)),
        HudKeyOption("Kombinasi", "Ctrl+Shift+Esc", HudKind.COMBO, combo = listOf(CTRL, SHIFT, KeyEvent.KEYCODE_ESCAPE)),
        HudKeyOption("Kombinasi", "Ctrl+Alt+Del", HudKind.COMBO, combo = listOf(CTRL, ALT, KeyEvent.KEYCODE_FORWARD_DEL)),
        HudKeyOption("Kombinasi", "Alt+Tab", HudKind.COMBO, combo = listOf(ALT, KeyEvent.KEYCODE_TAB)),
        HudKeyOption("Kombinasi", "Alt+F4", HudKind.COMBO, combo = listOf(ALT, KeyEvent.KEYCODE_F4)),
        HudKeyOption("Kombinasi", "Alt+Enter", HudKind.COMBO, combo = listOf(ALT, KeyEvent.KEYCODE_ENTER)),
        HudKeyOption("Kombinasi", "Win+D", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_D)),
        HudKeyOption("Kombinasi", "Win+E", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_E)),
        HudKeyOption("Kombinasi", "Win+R", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_R)),
        HudKeyOption("Kombinasi", "Win+L", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_L)),
        HudKeyOption("Kombinasi", "Win+Tab", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_TAB)),
        HudKeyOption("Kombinasi", "Win+Shift+S", HudKind.COMBO, combo = listOf(WIN, SHIFT, KeyEvent.KEYCODE_S)),
        HudKeyOption("Kombinasi", "Win+I", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_I)),
        HudKeyOption("Kombinasi", "Win+X", HudKind.COMBO, combo = listOf(WIN, KeyEvent.KEYCODE_X)),
    )

    val groups: List<Triple<String, String, List<HudKeyOption>>> = listOf(
        Triple("Kombinasi", "Combos", combos),
        Triple("F1 - F12", "F1 - F12", functionKeys),
        Triple("Single Key", "Single Key", singleKeys),
        Triple("Numpad", "Numpad", numpad),
        Triple("Huruf A-Z", "Letters A-Z", letters),
        Triple("Modifier", "Modifiers", modifiers),
        Triple("Mouse & Scroll", "Mouse & Scroll", mouse),
    )

    /** Dipakai dialog "ubah tombol": label ringkas untuk aksi non-keyboard. */
    fun labelFor(kind: HudKind): String = when (kind) {
        HudKind.MOUSE_LEFT -> "Kiri"
        HudKind.MOUSE_RIGHT -> "Kanan"
        HudKind.MOUSE_MIDDLE -> "Tengah"
        HudKind.SCROLL_UP -> "Naik"
        HudKind.SCROLL_DOWN -> "Turun"
        HudKind.SCROLL_SLIDER -> "Geser"
        HudKind.INPUT_SWITCH -> "Mode"
        else -> "Tombol"
    }

    /** Versi bahasa Inggris dari [labelFor]. */
    fun labelForEn(kind: HudKind): String = when (kind) {
        HudKind.MOUSE_LEFT -> "L"
        HudKind.MOUSE_RIGHT -> "R"
        HudKind.MOUSE_MIDDLE -> "M"
        HudKind.SCROLL_UP -> "Up"
        HudKind.SCROLL_DOWN -> "Down"
        HudKind.SCROLL_SLIDER -> "Swipe"
        HudKind.INPUT_SWITCH -> "Mode"
        else -> "Key"
    }
}

/** Satu-satunya tempat yang tahu arti XyAudioMode buat label UI. */
internal fun XyAudioMode.shortTitle(): String = when (this) {
    XyAudioMode.DEVICE -> "Perangkat"
    XyAudioMode.REMOTE -> "Remote"
    XyAudioMode.OFF -> "Mati"
}

internal fun XyAudioMode.shortTitleEn(): String = when (this) {
    XyAudioMode.DEVICE -> "Device"
    XyAudioMode.REMOTE -> "Remote"
    XyAudioMode.OFF -> "Off"
}
