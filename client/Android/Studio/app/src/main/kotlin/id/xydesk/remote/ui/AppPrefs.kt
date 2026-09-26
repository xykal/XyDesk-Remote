package id.xydesk.remote.ui

import android.content.Context

/**
 * Preferensi global app (SP "xydesk.app"):
 *  - mode tema (0 = ikut sistem, 1 = gelap, 2 = terang)
 *  - auto-disconnect saat background (default ON, 15s)
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

    companion object {
        private const val NAME = "xydesk.app"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BG_DISCONNECT = "bg_disconnect"
    }
}
