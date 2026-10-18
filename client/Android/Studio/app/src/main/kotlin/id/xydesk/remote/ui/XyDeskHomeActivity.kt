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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.freerdp.freerdpcore.services.LibFreeRDP
import id.xydesk.remote.sessions.SessionsRepository
import id.xydesk.remote.ui.theme.XyDeskTheme
import id.xydesk.remote.ui.theme.XyThemeState
import id.xydesk.remote.ui.theme.xyDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Home XyDesk (Jetpack Compose).
 *
 * Isinya: daftar perangkat (favorit + kredensial terenkripsi), layar
 * tambah/ubah perangkat, seksi drawer (Tampilan, Kredensial, Umum,
 * Keamanan, Tentang), serta penerima Share-Sheet Android (ACTION_SEND)
 * yang menaruh file/gambar langsung ke folder drive 'XyDesk'.
 */
internal const val EXTRA_SKIP_AUTO_RESUME = "id.xydesk.remote.ui.extra.SKIP_AUTO_RESUME"

/** Extra dari widget quick-connect: id perangkat (ConnectionProfile.id) yang mau langsung disambung. */
internal const val EXTRA_CONNECT_PROFILE_ID = "id.xydesk.remote.ui.extra.CONNECT_PROFILE_ID"

/** Extra pendamping: appWidgetId asal (buat log/debug widget). */
internal const val EXTRA_CONNECT_WIDGET_ID = "id.xydesk.remote.ui.extra.CONNECT_WIDGET_ID"

/** Extra dari notifikasi Chat Global: buka langsung layar Chat Global. */
internal const val EXTRA_OPEN_CHAT_GLOBAL = "id.xydesk.remote.ui.extra.OPEN_CHAT_GLOBAL"

class XyDeskHomeActivity : ComponentActivity() {

    companion object {
        @Volatile
        private var splashShownInProcess: Boolean = false
    }

    private val autoResumeSignal = mutableIntStateOf(0)
    private val skipAutoResumeSignal = mutableIntStateOf(-1)
    private val openChatSignal = mutableIntStateOf(0)
    private var skipAutoResumeOnNextResume = false
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
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyScreenSecurityFlags()
        val isOAuthReturn = intent?.data?.scheme == "xydesk"
        val shouldShowSplash = !splashShownInProcess && savedInstanceState == null && !isOAuthReturn
        splashShownInProcess = true
        markSkipAutoResumeIfNeeded(intent)
        handleIncomingShareIntent(intent)
        handleWidgetConnectIntent(intent)
        handleOpenChatIntent(intent)
        handleGitHubOAuthDeepLink(intent)
        setContent {
            XyThemeState.init(AppPrefs(this))
            val isDark = xyDark()
            SideEffect {
                runCatching {
                    val insetsCtrl = WindowInsetsControllerCompat(window, window.decorView)
                    insetsCtrl.isAppearanceLightStatusBars = !isDark
                    insetsCtrl.isAppearanceLightNavigationBars = !isDark
                }
            }
            val baseDensity = LocalDensity.current
            val normalizedDensity = remember(baseDensity.density, baseDensity.fontScale) {
                Density(
                    density = baseDensity.density,
                    fontScale = baseDensity.fontScale.coerceIn(0.85f, 1.10f),
                )
            }
            CompositionLocalProvider(LocalDensity provides normalizedDensity) {
                XyDeskTheme(dark = isDark) {
                    var boot by remember { mutableStateOf(shouldShowSplash) }
                    var ready by remember { mutableStateOf(!shouldShowSplash) }
                    val sharedBanner by sharedFileBanner
                    Box(Modifier.fillMaxSize()) {
                        XyDeskHome(
                            onExit = { finish() },
                            onReady = { ready = true },
                            sharedFileNotice = sharedBanner,
                            autoResumeSignal = autoResumeSignal.intValue,
                            skipAutoResume = autoResumeSignal.intValue > 0 &&
                                skipAutoResumeSignal.intValue == autoResumeSignal.intValue,
                            openChatSignal = openChatSignal.intValue,
                            onDismissSharedNotice = { sharedFileBanner.value = null },
                            onAuthenticateConnect = { action -> authenticateBeforeConnect(action) },
                        )
                        // Splash hanya tampil sekali saat cold-start awal; saat kembali
                        // dari OAuth GitHub di Chrome atau rotasi layar, tidak pernah
                        // menampilkan splash ulang.
                        if (boot) {
                            XySplashScreen(
                                ready = ready,
                                dark = isDark,
                                onDone = { boot = false },
                            )
                        }
                        AppChannelGate(onExit = { finish() })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyScreenSecurityFlags()
        autoResumeSignal.intValue += 1
        if (skipAutoResumeOnNextResume) {
            skipAutoResumeSignal.intValue = autoResumeSignal.intValue
            skipAutoResumeOnNextResume = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        markSkipAutoResumeIfNeeded(intent)
        handleIncomingShareIntent(intent)
        handleWidgetConnectIntent(intent)
        handleOpenChatIntent(intent)
        handleGitHubOAuthDeepLink(intent)
    }

    private fun handleGitHubOAuthDeepLink(incoming: Intent?) {
        val data = incoming?.data ?: return
        if (data.scheme == "xydesk" && data.host == "github-auth") {
            val token = data.getQueryParameter("token").orEmpty().trim()
            val login = data.getQueryParameter("login").orEmpty().trim()
            val name = data.getQueryParameter("name").orEmpty().trim()
            val avatar = data.getQueryParameter("avatar").orEmpty().trim()
            val code = data.getQueryParameter("code").orEmpty().trim()
            val state = data.getQueryParameter("state").orEmpty().trim()
            incoming.data = null
            if (token.isNotEmpty() && login.isNotEmpty()) {
                lifecycleScope.launch {
                    GitHubDirectOAuthController.onDeepLinkReceived(
                        context = applicationContext,
                        token = token,
                        login = login,
                        displayName = name,
                        avatarUrl = avatar,
                    )
                }
            } else if (code.isNotEmpty()) {
                lifecycleScope.launch {
                    GitHubDirectOAuthController.onCodeReceived(
                        context = applicationContext,
                        code = code,
                        state = state,
                    )
                }
            }
        }
    }

    private fun handleOpenChatIntent(incoming: Intent?) {
        if (incoming?.getBooleanExtra(EXTRA_OPEN_CHAT_GLOBAL, false) != true) return
        incoming.removeExtra(EXTRA_OPEN_CHAT_GLOBAL)
        openChatSignal.intValue += 1
    }

    /**
     * Tap widget quick-connect: buka home sambil langsung menyambung ke
     * perangkat pilihan. Jalur autentikasi (kunci perangkat) tetap sama
     * seperti connect dari daftar perangkat.
     */
    private fun handleWidgetConnectIntent(incoming: Intent?) {
        val profileId = incoming?.getStringExtra(EXTRA_CONNECT_PROFILE_ID) ?: return
        incoming.removeExtra(EXTRA_CONNECT_PROFILE_ID)
        incoming.action = Intent.ACTION_MAIN
        lifecycleScope.launch {
            val profile = runCatching {
                SessionsRepository(applicationContext).favorites().first()
            }.getOrDefault(emptyList()).firstOrNull { it.id == profileId }
            if (profile == null) {
                sharedFileBanner.value = xyNow(
                    "Perangkat widget tidak ditemukan — sudah dihapus?",
                    "Widget device not found — was it deleted?",
                )
                return@launch
            }
            authenticateBeforeConnect {
                startActivity(XyDeskSessionActivity.connectIntent(this@XyDeskHomeActivity, profile))
            }
        }
    }

    private fun markSkipAutoResumeIfNeeded(incoming: Intent) {
        val isShare = incoming.action == Intent.ACTION_SEND ||
            incoming.action == Intent.ACTION_SEND_MULTIPLE
        val shouldSkip = incoming.getBooleanExtra(EXTRA_SKIP_AUTO_RESUME, false) || isShare
        if (!shouldSkip) return
        incoming.removeExtra(EXTRA_SKIP_AUTO_RESUME)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            autoResumeSignal.intValue += 1
            skipAutoResumeSignal.intValue = autoResumeSignal.intValue
        } else {
            skipAutoResumeOnNextResume = true
        }
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
