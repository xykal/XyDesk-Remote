package id.xydesk.remote.ui

import android.content.Context

/**
 * M2.5 — preferensi sesi per koneksi (SharedPreferences).
 * Key = [id.xydesk.remote.core.ConnectionProfile.id] (`host:port`).
 */
class ConnectionPrefs(context: Context) {

    private val sp =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getZoom(id: String): Float = sp.getFloat("$id.zoom", 1f)

    fun setZoom(id: String, zoom: Float) {
        sp.edit().putFloat("$id.zoom", zoom).apply()
    }

    fun isPanelShown(id: String): Boolean = sp.getBoolean("$id.panel", false)

    fun setPanelShown(id: String, shown: Boolean) {
        sp.edit().putBoolean("$id.panel", shown).apply()
    }

    companion object {
        private const val NAME = "xydesk.session"
    }
}
