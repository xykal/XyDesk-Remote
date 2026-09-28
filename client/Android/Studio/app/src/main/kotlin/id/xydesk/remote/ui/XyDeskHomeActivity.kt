package id.xydesk.remote.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import id.xydesk.remote.ui.theme.XyDeskTheme
import id.xydesk.remote.ui.theme.XyThemeState
import id.xydesk.remote.ui.theme.xyDark

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
            XyThemeState.init(AppPrefs(this))
            XyDeskTheme(dark = xyDark()) {
                var boot by remember { mutableStateOf(true) }
                var ready by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    XyDeskHome(
                        onExit = { finish() },
                        onReady = { ready = true },
                    )
                    // Splash XyVerse menutup layar sampai data siap; home
                    // sudah tersusun di belakangnya, jadi begitu splash hilang
                    // tidak ada kedipan.
                    if (boot) {
                        XySplashScreen(
                            ready = ready,
                            dark = xyDark(),
                            onDone = { boot = false },
                        )
                    }
                }
            }
        }
    }
}
