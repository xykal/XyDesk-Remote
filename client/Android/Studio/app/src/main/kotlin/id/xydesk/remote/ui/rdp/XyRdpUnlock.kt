package id.xydesk.remote.ui.rdp

import android.content.Context

/**
 * Syarat buka fitur RDP Gratis: nonton satu iklan rewarded + ketuk gabung
 * saluran. Keduanya flag lokal; iklan diverifikasi AdMob (reward callback),
 * gabung saluran berbasis kepercayaan (tidak ada bot/token komunitas).
 */
internal class XyRdpUnlock(context: Context) {
    private val sp =
        context.applicationContext.getSharedPreferences("xydesk.rdpfree", Context.MODE_PRIVATE)

    var adWatched: Boolean
        get() = sp.getBoolean(KEY_AD, false)
        set(v) = sp.edit().putBoolean(KEY_AD, v).apply()

    var joined: Boolean
        get() = sp.getBoolean(KEY_JOIN, false)
        set(v) = sp.edit().putBoolean(KEY_JOIN, v).apply()

    val unlocked: Boolean get() = adWatched && joined

    companion object {
        private const val KEY_AD = "unlock_ad_watched"
        private const val KEY_JOIN = "unlock_joined"
    }
}
