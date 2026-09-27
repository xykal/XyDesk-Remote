package id.xydesk.remote.ui

import android.content.Context

/** Cara input di layar sesi. */
enum class InputMode(val title: String, val detail: String) {
    TRACKPAD("Trackpad", "Geser = gerakkan pointer, ketuk = klik kiri"),
    DIRECT("Sentuh langsung", "Sentuh langsung di titik yang dituju"),
}

enum class PointerStyle(val title: String) { DOT("Titik"), ARROW("Panah") }

enum class Corner(val title: String) { RIGHT("Kanan"), LEFT("Kiri") }

/**
 * Preferensi kontrol sesi (file `xydesk.input`) + preferensi per-perangkat
 * (file `xydesk.session`). Dipisah: yang global dibawa ke semua perangkat,
 * yang per-perangkat (posisi cluster, zoom, panel) disimpan per id.
 */
class SessionPrefs(context: Context) {

    private val input = context.applicationContext
        .getSharedPreferences(INPUT_FILE, Context.MODE_PRIVATE)
    private val device = context.applicationContext
        .getSharedPreferences(DEVICE_FILE, Context.MODE_PRIVATE)

    // ---- global ----

    var inputMode: InputMode
        get() = InputMode.entries.getOrElse(input.getInt(KEY_INPUT_MODE, 0)) { InputMode.TRACKPAD }
        set(v) = input.edit().putInt(KEY_INPUT_MODE, v.ordinal).apply()

    var pointerStyle: PointerStyle
        get() = PointerStyle.entries.getOrElse(input.getInt(KEY_POINTER_STYLE, 0)) { PointerStyle.DOT }
        set(v) = input.edit().putInt(KEY_POINTER_STYLE, v.ordinal).apply()

    /** Ukuran pointer di layar, dp. */
    var pointerSize: Float
        get() = input.getFloat(KEY_POINTER_SIZE, 22f).coerceIn(10f, 52f)
        set(v) = input.edit().putFloat(KEY_POINTER_SIZE, v.coerceIn(10f, 52f)).apply()

    var pointerFollows: Boolean
        get() = input.getBoolean(KEY_POINTER_FOLLOW, true)
        set(v) = input.edit().putBoolean(KEY_POINTER_FOLLOW, v).apply()

    var keyboardCorner: Corner
        get() = Corner.entries.getOrElse(input.getInt(KEY_KEYBOARD_CORNER, 0)) { Corner.RIGHT }
        set(v) = input.edit().putInt(KEY_KEYBOARD_CORNER, v.ordinal).apply()

    var haptics: Boolean
        get() = input.getBoolean(KEY_HAPTICS, true)
        set(v) = input.edit().putBoolean(KEY_HAPTICS, v).apply()

    /** Pengali kecepatan scroll (1.0 = satu notch per 40px geser). */
    var scrollSpeed: Float
        get() = input.getFloat(KEY_SCROLL_SPEED, 1f).coerceIn(0.4f, 2.5f)
        set(v) = input.edit().putFloat(KEY_SCROLL_SPEED, v.coerceIn(0.4f, 2.5f)).apply()

    /** Ukuran cluster tombol mouse (pengali). */
    var clusterScale: Float
        get() = input.getFloat(KEY_CLUSTER_SCALE, 1f).coerceIn(0.7f, 1.8f)
        set(v) = input.edit().putFloat(KEY_CLUSTER_SCALE, v.coerceIn(0.7f, 1.8f)).apply()

    var showLeft: Boolean
        get() = input.getBoolean(KEY_BTN_LEFT, true)
        set(v) = input.edit().putBoolean(KEY_BTN_LEFT, v).apply()

    var showRight: Boolean
        get() = input.getBoolean(KEY_BTN_RIGHT, true)
        set(v) = input.edit().putBoolean(KEY_BTN_RIGHT, v).apply()

    var showMiddle: Boolean
        get() = input.getBoolean(KEY_BTN_MIDDLE, false)
        set(v) = input.edit().putBoolean(KEY_BTN_MIDDLE, v).apply()

    var showScroll: Boolean
        get() = input.getBoolean(KEY_BTN_SCROLL, true)
        set(v) = input.edit().putBoolean(KEY_BTN_SCROLL, v).apply()

    var showSwitch: Boolean
        get() = input.getBoolean(KEY_BTN_SWITCH, true)
        set(v) = input.edit().putBoolean(KEY_BTN_SWITCH, v).apply()

    /**
     * Diameter tombol kontrol HUD (dp). Default 56; pengguna bisa geser ke
     * 40..80. Semua tombol kontrol HUD bulat penuh (radius = setengah
     * diameter), jadi satu angka ini menentukan radius efektifnya juga.
     */
    var hudButtonSize: Float
        get() = input.getFloat(KEY_HUD_SIZE, 56f).coerceIn(40f, 80f)
        set(v) = input.edit().putFloat(KEY_HUD_SIZE, v.coerceIn(40f, 80f)).apply()

    /** Skala keyboard overlay (pengali ukuran tombol). */
    var keyboardScale: Float
        get() = input.getFloat(KEY_KB_SCALE, 1f).coerceIn(0.7f, 1.6f)
        set(v) = input.edit().putFloat(KEY_KB_SCALE, v.coerceIn(0.7f, 1.6f)).apply()

    /** Keyboard overlay tampil otomatis saat sesi terhubung. */
    var keyboardAutoOpen: Boolean
        get() = input.getBoolean(KEY_KB_AUTO, false)
        set(v) = input.edit().putBoolean(KEY_KB_AUTO, v).apply()

    // ---- per perangkat ----

    fun zoom(id: String): Float = device.getFloat("$id.zoom", 1f)

    fun hasZoom(id: String): Boolean = device.contains("$id.zoom")

    fun setZoom(id: String, zoom: Float) = device.edit().putFloat("$id.zoom", zoom).apply()

    fun panelShown(id: String): Boolean = device.getBoolean("$id.panel", false)

    fun setPanelShown(id: String, shown: Boolean) {
        device.edit().putBoolean("$id.panel", shown).apply()
    }

    fun keyboardShown(id: String): Boolean = device.getBoolean("$id.keyboard", false)

    fun setKeyboardShown(id: String, shown: Boolean) {
        device.edit().putBoolean("$id.keyboard", shown).apply()
    }

    /** Posisi cluster tombol mouse, ternormalisasi 0..1 dari kiri-atas. */
    fun clusterX(id: String): Float = device.getFloat("$id.cluster.x", 0.72f).coerceIn(0f, 1f)

    fun clusterY(id: String): Float = device.getFloat("$id.cluster.y", 0.55f).coerceIn(0f, 1f)

    fun setCluster(id: String, x: Float, y: Float) {
        device.edit()
            .putFloat("$id.cluster.x", x.coerceIn(0f, 1f))
            .putFloat("$id.cluster.y", y.coerceIn(0f, 1f))
            .apply()
    }

    /** Keyboard overlay tampil (terpisah dari keyboard sistem/IME). */
    fun overlayShown(id: String): Boolean = device.getBoolean("$id.overlay", false)

    fun setOverlayShown(id: String, shown: Boolean) {
        device.edit().putBoolean("$id.overlay", shown).apply()
    }

    /** Posisi keyboard overlay, ternormalisasi 0..1 dari kiri-atas. */
    fun overlayX(id: String): Float = device.getFloat("$id.overlay.x", 0.06f).coerceIn(0f, 1f)

    fun overlayY(id: String): Float = device.getFloat("$id.overlay.y", 0.62f).coerceIn(0f, 1f)

    fun setOverlayPos(id: String, x: Float, y: Float) {
        device.edit()
            .putFloat("$id.overlay.x", x.coerceIn(0f, 1f))
            .putFloat("$id.overlay.y", y.coerceIn(0f, 1f))
            .apply()
    }

    companion object {
        private const val INPUT_FILE = "xydesk.input"
        private const val DEVICE_FILE = "xydesk.session"
        private const val KEY_INPUT_MODE = "input_mode"
        private const val KEY_POINTER_STYLE = "pointer_style"
        private const val KEY_POINTER_SIZE = "pointer_size"
        private const val KEY_POINTER_FOLLOW = "pointer_follow"
        private const val KEY_KEYBOARD_CORNER = "keyboard_corner"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_SCROLL_SPEED = "scroll_speed"
        private const val KEY_CLUSTER_SCALE = "cluster_scale"
        private const val KEY_BTN_LEFT = "btn_left"
        private const val KEY_BTN_RIGHT = "btn_right"
        private const val KEY_BTN_MIDDLE = "btn_middle"
        private const val KEY_BTN_SCROLL = "btn_scroll"
        private const val KEY_BTN_SWITCH = "btn_switch"
        private const val KEY_HUD_SIZE = "hud_size"
        private const val KEY_KB_SCALE = "kb_scale"
        private const val KEY_KB_AUTO = "kb_auto"
    }
}

/**
 * Preferensi tampilan per perangkat (file `xydesk.remote.display`) —
 * dibaca juga oleh [id.xydesk.remote.core.SessionManager] saat menyusun
 * argumen `/size:` untuk sesi.
 */
object DisplayPrefs {

    const val AUTOMATIC = "automatic"

    /**
     * Pilihan resolusi remote. "Otomatis" = ikut ukuran layar HP (fit).
     * Ukuran lain dikirim ke server sebagai `/size:WxH`; server yang
     * mendukung dynamic resolution langsung menyesuaikan desktop-nya.
     */
    val resolutions = listOf(
        AUTOMATIC to "Otomatis",
        "1024x768" to "1024 x 768",
        "1280x720" to "1280 x 720",
        "1280x800" to "1280 x 800",
        "1366x768" to "1366 x 768",
        "1440x900" to "1440 x 900",
        "1600x900" to "1600 x 900",
        "1680x1050" to "1680 x 1050",
        "1920x1080" to "1920 x 1080",
        "1920x1200" to "1920 x 1200",
        "2560x1440" to "2560 x 1440",
        "2560x1600" to "2560 x 1600",
        "3840x2160" to "3840 x 2160",
    )

    /** Resolusi portrait (layar diputar). */
    val portraitResolutions = listOf(
        "720x1280" to "720 x 1280",
        "1080x1920" to "1080 x 1920",
        "1200x1920" to "1200 x 1920",
        "1440x2560" to "1440 x 2560",
    )

    val rotations = listOf("Auto", "Portrait", "Landscape")

    /** Validasi "WxH" manual (batas sama dengan SessionManager). */
    fun parseCustom(value: String): String? {
        val parts = value.trim().lowercase().split('x')
        if (parts.size != 2) return null
        val w = parts[0].trim().toIntOrNull() ?: return null
        val h = parts[1].trim().toIntOrNull() ?: return null
        if (w !in 640..8192 || h !in 480..8192) return null
        return "${w}x$h"
    }

    private fun sp(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun resolution(context: Context, id: String): String =
        sp(context).getString("$id.resolution", AUTOMATIC) ?: AUTOMATIC

    fun setResolution(context: Context, id: String, value: String) =
        sp(context).edit().putString("$id.resolution", value).apply()

    fun rotation(context: Context, id: String): String =
        sp(context).getString("$id.rotation", "Auto") ?: "Auto"

    fun setRotation(context: Context, id: String, value: String) =
        sp(context).edit().putString("$id.rotation", value).apply()

    /** Skala tampilan awal saat sesi dibuka, 100..200 (%). */
    fun dpi(context: Context, id: String): Int =
        sp(context).getInt("$id.dpi", 100).coerceIn(80, 200)

    fun setDpi(context: Context, id: String, value: Int) =
        sp(context).edit().putInt("$id.dpi", value.coerceIn(80, 200)).apply()

    fun clear(context: Context, id: String) {
        sp(context).edit()
            .remove("$id.resolution")
            .remove("$id.rotation")
            .remove("$id.dpi")
            .apply()
    }

    private const val FILE = "xydesk.remote.display"
}
