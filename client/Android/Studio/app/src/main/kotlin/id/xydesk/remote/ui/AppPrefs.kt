package id.xydesk.remote.ui

import android.content.Context

/**
 * Preferensi global app (SP "xydesk.app"):
 *  - mode tema (0 = ikut sistem, 1 = gelap, 2 = terang)
 *  - auto-disconnect saat background (dipakai kalau keep-alive dimatikan)
 *  - keep-alive: sesi jalan terus di latar lewat foreground service
 */
class AppPrefs(context: Context) {
    private val sp =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var themeMode: Int
        get() = sp.getInt(KEY_THEME, 0)
        set(v) = sp.edit().putInt(KEY_THEME, v).apply()

    var autoDisconnect: Boolean
        get() = sp.getBoolean(KEY_BG_DISCONNECT, true)
        set(v) = sp.edit().putBoolean(KEY_BG_DISCONNECT, v).apply()

    /**
     * Sesi tetap jalan saat app ditinggal ke latar (foreground service +
     * notifikasi). Default ON: RDP itu pekerjaan yang sedang dilihat user,
     * bukan proses yang boleh diputus Android begitu layar dialihkan.
     */
    var keepAlive: Boolean
        get() = sp.getBoolean(KEY_KEEP_ALIVE, true)
        set(v) = sp.edit().putBoolean(KEY_KEEP_ALIVE, v).apply()

    // ---- default fitur untuk perangkat BARU (diatur di General) ----
    // Perangkat yang sudah tersimpan tidak ikut berubah: nilainya tetap
    // diatur per perangkat di layar Ubah perangkat.

    var defaultUdp: Boolean
        get() = sp.getBoolean("default_udp", true)
        set(v) = sp.edit().putBoolean("default_udp", v).apply()

    var defaultNetAuto: Boolean
        get() = sp.getBoolean("default_netauto", true)
        set(v) = sp.edit().putBoolean("default_netauto", v).apply()

    var defaultH264: Boolean
        get() = sp.getBoolean("default_h264", true)
        set(v) = sp.edit().putBoolean("default_h264", v).apply()

    var defaultDynamicResolution: Boolean
        get() = sp.getBoolean("default_dynres", true)
        set(v) = sp.edit().putBoolean("default_dynres", v).apply()

    var defaultClipboard: Boolean
        get() = sp.getBoolean("default_clipboard", true)
        set(v) = sp.edit().putBoolean("default_clipboard", v).apply()

    var defaultLocalDrive: Boolean
        get() = sp.getBoolean("default_drive", false)
        set(v) = sp.edit().putBoolean("default_drive", v).apply()

    companion object {
        private const val NAME = "xydesk.app"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BG_DISCONNECT = "bg_disconnect"
        private const val KEY_KEEP_ALIVE = "keep_alive"
    }
}
