package id.xydesk.remote.ui

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import id.xydesk.remote.ui.theme.XyDeskTheme

/**
 * Home XyDesk (Jetpack Compose).
 *
 * Isinya: daftar perangkat (favorit + kredensial terenkripsi), layar
 * tambah/ubah perangkat, dan seksi drawer (Tampilan, Kredensial, Umum,
 * Keamanan, Tentang). Sesi RDP dibuka di [XyDeskSessionActivity].
 */
class XyDeskHomeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = AppPrefs(this)
            val systemDark = (getResources().configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (prefs.themeMode) {
                1 -> true
                2 -> false
                else -> systemDark
            }
            XyDeskTheme(dark = dark) {
                XyDeskHome(onExit = { finish() })
            }
        }
    }
}
