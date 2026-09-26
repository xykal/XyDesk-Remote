package id.xydesk.remote.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.res.Configuration
import id.xydesk.remote.ui.theme.XyDeskTheme
import id.xydesk.remote.cloud.CloudRdpActivity

/**
 * M1.2 — Home XyDesk (Jetpack Compose, Material 3).
 *
 * Menggantikan form M0 (MainActivity) sebagai launcher. Alur:
 *  - Daftar favorit (features-sessions, password dari vault terenkripsi)
 *  - Form koneksi baru (host/port/user/pass/domain + "ingat password")
 *  - Pintu ke Cloud RDP (GitHub) & form klasik M0 (fallback)
 *
 * Render sesi RDP masih lewat core SessionActivity (jalur teruji M0) —
 * XyDesk session surface native menyusul di M2.
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
                XyDeskHome(
                    onOpenCloudRdp = {
                        startActivity(Intent(this, CloudRdpActivity::class.java))
                    },
                    onExit = { finish() },
                )
            }
        }
    }
}
