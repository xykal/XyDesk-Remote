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

    /**
     * Muat seluruh desktop setiap sesi dibuka / setelah resolusi berubah.
     * Default ON: kalau tidak, tepi bawah desktop (taskbar Windows) sering
     * tidak masuk layar dan kelihatan seperti "tenggelam".
     */
    var autoFit: Boolean
        get() = input.getBoolean(KEY_AUTO_FIT, true)
        set(v) = input.edit().putBoolean(KEY_AUTO_FIT, v).apply()

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

    /**
     * Tombol HUD sesi (bentuk bebas, bulat penuh): satu tombol = satu aksi.
     * Disimpan per perangkat karena posisi layer bergantung ukuran layar.
     */
    fun hudKeys(deviceId: String): List<HudKey> {
        HudKey.decode(device.getString("$deviceId.hudkeys", null))?.let { return it }
        // Belum pernah diubah: susun set bawaan, tapi hormati preferensi lama
        // (toggle cluster ronde 2) supaya tombol yang sengaja dimatikan user
        // tidak muncul lagi begitu model tombol baru dipakai.
        var keys = HudKey.defaults()
        if (!showLeft) keys = keys.filterNot { it.id == "kiri" }
        if (!showRight) keys = keys.filterNot { it.id == "kanan" }
        if (!showMiddle) keys = keys.filterNot { it.id == "tengah" }
        if (!showScroll) keys = keys.filterNot { it.id == "naik" || it.id == "turun" }
        if (!showSwitch) keys = keys.filterNot { it.id == "switch" }
        return keys
    }

    fun setHudKeys(deviceId: String, keys: List<HudKey>) {
        device.edit().putString("$deviceId.hudkeys", HudKey.encode(keys)).apply()
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
        private const val KEY_AUTO_FIT = "auto_fit"
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
    /** Preset pintar: resolusi 16:9 standar terbesar yang masih muat di layar HP. */
    const val SMART_16_9 = "smart169"

    /**
     * Pilihan resolusi remote, dikelompokkan per rasio. Rasio ditulis apa
     * adanya supaya user tahu 1920x1080 itu 16:9 dan tidak menebak-nebak.
     * "Otomatis" = persis ukuran layar HP (bisa 20:9, tampil penuh tanpa
     * bar hitam), "16:9 pas layar" = standar Windows yang paling dekat
     * dengan layar HP (paling aman untuk desktop Windows).
     */
    val resolutionGroups: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "Pintar" to listOf(
            AUTOMATIC to "Otomatis (layar HP)",
            SMART_16_9 to "16:9 pas layar",
        ),
        "16:9 — standar Windows" to listOf(
            "1280x720" to "1280\u00d7720 (HD)",
            "1600x900" to "1600\u00d7900",
            "1920x1080" to "1920\u00d71080 (FHD)",
            "2560x1440" to "2560\u00d71440 (QHD)",
            "3840x2160" to "3840\u00d72160 (4K)",
        ),
        "16:10" to listOf(
            "1280x800" to "1280\u00d7800",
            "1440x900" to "1440\u00d7900",
            "1680x1050" to "1680\u00d71050",
            "1920x1200" to "1920\u00d71200",
            "2560x1600" to "2560\u00d71600",
        ),
        "21:9 ultrawide" to listOf(
            "2560x1080" to "2560\u00d71080",
            "3440x1440" to "3440\u00d71440",
        ),
        "4:3" to listOf(
            "1024x768" to "1024\u00d7768",
            "1280x960" to "1280\u00d7960",
        ),
        "Potret (layar diputar)" to listOf(
            "720x1280" to "720\u00d71280",
            "1080x1920" to "1080\u00d71920",
            "1200x1920" to "1200\u00d71920",
            "1440x2560" to "1440\u00d72560",
        ),
    )

    /** Resolusi potret (dipakai saat layar HP diputar). */
    val portraitResolutions = listOf(
        "720x1280" to "720x1280",
        "1080x1920" to "1080x1920",
        "1200x1920" to "1200x1920",
        "1440x2560" to "1440x2560",
    )

    /** Daftar datar (dipakai layar perangkat / tempat lain yang butuh list). */
    val resolutions: List<Pair<String, String>> =
        resolutionGroups.flatMap { it.second } + portraitResolutions

    /**
     * Ubah preset jadi ukuran nyata. SMART_16_9 memilih resolusi 16:9 standar
     * terbesar yang sisi pendeknya tidak melebihi sisi pendek layar HP, jadi
     * desktop selalu 16:9 tapi tetap muat di layar.
     */
    fun resolvePreset(context: Context, preset: String?): String? {
        if (preset.isNullOrBlank() || preset == AUTOMATIC) return null
        if (preset != SMART_16_9) return preset
        val dm = context.resources.displayMetrics
        val minSide = minOf(dm.widthPixels, dm.heightPixels)
        val candidates = listOf(
            1280 to 720, 1600 to 900, 1920 to 1080, 2560 to 1440, 3840 to 2160,
        )
        val pick = candidates.lastOrNull { it.second <= minSide } ?: candidates.first()
        return "${pick.first}x${pick.second}"
    }

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
