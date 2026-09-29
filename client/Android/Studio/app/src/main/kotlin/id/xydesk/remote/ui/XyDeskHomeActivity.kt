package id.xydesk.remote.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.freerdp.freerdpcore.services.LibFreeRDP
import id.xydesk.remote.ui.theme.XyDeskTheme
import id.xydesk.remote.ui.theme.XyThemeState
import id.xydesk.remote.ui.theme.xyDark
import java.io.File

/**
 * Home XyDesk (Jetpack Compose).
 *
 * Isinya: daftar perangkat (favorit + kredensial terenkripsi), layar
 * tambah/ubah perangkat, seksi drawer (Tampilan, Kredensial, Umum,
 * Keamanan, Tentang), serta penerima Share-Sheet Android (ACTION_SEND)
 * yang menaruh file/gambar langsung ke folder drive 'XyDesk'.
 */
class XyDeskHomeActivity : ComponentActivity() {

    private var pendingAuthAction: (() -> Unit)? = null
    private val sharedFileBanner = mutableStateOf<String?>(null)

    private val deviceCredentialLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val action = pendingAuthAction
        pendingAuthAction = null
        if (result.resultCode == Activity.RESULT_OK) {
            action?.invoke()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyScreenSecurityFlags()
        handleIncomingShareIntent(intent)
        setContent {
            XyThemeState.init(AppPrefs(this))
            XyDeskTheme(dark = xyDark()) {
                var boot by remember { mutableStateOf(true) }
                var ready by remember { mutableStateOf(false) }
                val sharedBanner by sharedFileBanner
                Box(Modifier.fillMaxSize()) {
                    XyDeskHome(
                        onExit = { finish() },
                        onReady = { ready = true },
                        sharedFileNotice = sharedBanner,
                        onDismissSharedNotice = { sharedFileBanner.value = null },
                        onAuthenticateConnect = { action -> authenticateBeforeConnect(action) },
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

    override fun onResume() {
        super.onResume()
        applyScreenSecurityFlags()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun applyScreenSecurityFlags() {
        val secure = runCatching { AppPrefs(this).screenCaptureProtection }.getOrDefault(false)
        if (secure) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    @Suppress("DEPRECATION")
    private fun authenticateBeforeConnect(onVerified: () -> Unit) {
        val prefs = AppPrefs(this)
        if (!prefs.requireDeviceLock) {
            onVerified()
            return
        }
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null || !km.isDeviceSecure) {
            onVerified()
            return
        }
        val confirmIntent = km.createConfirmDeviceCredentialIntent(
            xyNow("Verifikasi Keamanan XyDesk", "XyDesk Security Verification"),
            xyNow(
                "Buka kunci perangkat Anda untuk memulai sesi remote desktop.",
                "Unlock your device to launch the remote desktop session.",
            ),
        )
        if (confirmIntent == null) {
            onVerified()
            return
        }
        pendingAuthAction = onVerified
        runCatching { deviceCredentialLauncher.launch(confirmIntent) }
            .onFailure {
                pendingAuthAction = null
                onVerified()
            }
    }

    @Suppress("DEPRECATION")
    private fun handleIncomingShareIntent(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND) return
        val streamUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            incoming.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
        incoming.action = Intent.ACTION_MAIN

        Thread {
            runCatching {
                val driveRoot = File(LibFreeRDP.appDrivePath(applicationContext))
                if (!driveRoot.exists()) driveRoot.mkdirs()

                if (streamUri != null) {
                    val rawName = resolveDisplayName(streamUri)
                        ?: "shared-${System.currentTimeMillis()}"
                    val safeName = rawName.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank {
                        "shared-${System.currentTimeMillis()}"
                    }
                    val targetFile = File(driveRoot, safeName)
                    contentResolver.openInputStream(streamUri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    runOnUiThread {
                        sharedFileBanner.value = xyNow(
                            "File '${targetFile.name}' siap di Drive XyDesk PC remote. Pilih perangkat untuk terhubung.",
                            "File '${targetFile.name}' is ready in the remote PC 'XyDesk' drive. Select a device to connect.",
                        )
                    }
                } else if (!sharedText.isNullOrBlank()) {
                    val noteFile = File(driveRoot, "shared-text-${System.currentTimeMillis()}.txt")
                    noteFile.writeText(sharedText)
                    runOnUiThread {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("XyDesk Share", sharedText))
                        sharedFileBanner.value = xyNow(
                            "Teks disalin ke clipboard & disimpan sebagai '${noteFile.name}' di Drive XyDesk.",
                            "Text copied to clipboard & saved as '${noteFile.name}' in the XyDesk drive.",
                        )
                    }
                }
            }
        }.start()
    }

    private fun resolveDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
