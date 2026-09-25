package id.xydesk.remote.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import id.xydesk.remote.MainActivity
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
            MaterialTheme {
                XyDeskHome(
                    onOpenCloudRdp = {
                        startActivity(Intent(this, CloudRdpActivity::class.java))
                    },
                    onOpenClassicForm = {
                        startActivity(Intent(this, MainActivity::class.java))
                    },
                )
            }
        }
    }
}
