package id.xydesk.remote.ui

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import id.xydesk.remote.security.CrashLog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.xydesk.remote.core.ConnectionLog
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.SessionManager
import id.xydesk.remote.core.SessionState
import id.xydesk.remote.ui.theme.XyDeskTheme

/**
 * M2 — activity sesi XyDesk (Compose): surface RDP + HUD.
 *
 * Satu sesi = satu aktivitas. Lifecycle:
 *  - onCreate : SessionManager + SessionSurfaceController dibuat, sink
 *    di-set SEBELUM connect, layar immersive, connect(profile)
 *  - onStop   : policy background (M1.2b) — disconnect otomatis setelah
 *    [BACKGROUND_DISCONNECT_DELAY_MS] kalau masih Connected
 *  - onDestroy: controller.release() + manager.release()
 *
 * Back: ditangani layar Compose ([XyDeskSessionScreen] memasang
 * [backHandler]); default = finish.
 */
class XyDeskSessionActivity : ComponentActivity() {

    lateinit var manager: SessionManager
        private set
    lateinit var controller: SessionSurfaceController
        private set

    /**
     * Dipasang [XyDeskSessionScreen]: return `true` kalau back sudah
     * ditangani (dialog ditutup, panel disembunyikan, konfirmasi, dll).
     */
    var backHandler: (() -> Boolean)? = null
    var appPrefs: AppPrefs? = null

    private val bgHandler = Handler(Looper.getMainLooper())
    private val bgDisconnect = Runnable {
        if (manager.state.value is SessionState.Connected) {
            Log.i(TAG, "policy background: auto-disconnect (lewat ${BACKGROUND_DISCONNECT_DELAY_MS}ms)")
            manager.disconnect()
        }
    }
    private var clipListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            ConnectionLog.add("SES: activity created")
            manager = SessionManager(applicationContext)
            controller = SessionSurfaceController(this)
            // sink WAJIB sebelum connect — event grafik pertama tidak boleh hilang
            manager.setGraphicsSink(controller)
            ConnectionLog.add("SES: manager+controller ok, sink set")
        } catch (t: Throwable) {
            ConnectionLog.add("SES: FAIL inisialisasi: ${t.javaClass.name}: ${t.message}")
            Log.e(TAG, "gagal inisialisasi sesi", t)
            CrashLog.note(this, "inisialisasi sesi gagal: ${t.javaClass.name}: ${t.message}")
            Toast.makeText(this, "Gagal menyiapkan sesi: ${t.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val profile = profileFromIntent(getIntent())
        if (profile == null) {
            ConnectionLog.add("SES: intent tanpa profil valid — keluar")
            Log.w(TAG, "intent tanpa profil valid — keluar")
            finish()
            return
        }
        ConnectionLog.add("SES: profil ok -> ${profile.host}:${profile.port}")

        hideSystemBars()

        // clipboard lokal -> remote (teks; hanya saat Connected)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val listener = object : ClipboardManager.OnPrimaryClipChangedListener {
            override fun onPrimaryClipChanged() {
                if (manager.state.value is SessionState.Connected) {
                    val clip = cm.getPrimaryClip() ?: return
                    if (clip.itemCount > 0) {
                        val text = clip.getItemAt(0).coerceToText(this@XyDeskSessionActivity).toString()
                        if (text.isNotEmpty()) manager.sendClipboardData(text)
                    }
                }
            }
        }
        clipListener = listener
        cm.addPrimaryClipChangedListener(listener)

        val prefs = AppPrefs(this)
        this.appPrefs = prefs
        setContent {
            val systemDark = (getResources().configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val dark = when (prefs.themeMode) {
                1 -> true
                2 -> false
                else -> systemDark
            }
            XyDeskTheme(dark = dark) {
                XyDeskSessionScreen(
                    profile = profile,
                    manager = manager,
                    controller = controller,
                    onExit = { finish() },
                )
            }
        }

        // back = delegate ke layar Compose; default finish
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (backHandler?.invoke() != true) finish()
                }
            }
        )

        ConnectionLog.add("SES: memanggil manager.connect (native createSession + parse args)")
        try {
            manager.connect(profile)
            ConnectionLog.add("SES: manager.connect kembali (worker jalan async)")
        } catch (t: Throwable) {
            ConnectionLog.add("SES: connect() JVM-exception: ${t.javaClass.name}: ${t.message}")
            Log.e(TAG, "connect() gagal", t)
            CrashLog.note(this, "connect() gagal: ${t.javaClass.name}: ${t.message}")
        }
    }

    override fun onStart() {
        super.onStart()
        // balik dari background sebelum timer jalan = cancel auto-disconnect
        bgHandler.removeCallbacks(bgDisconnect)
    }

    override fun onStop() {
        super.onStop()
        if (manager.state.value is SessionState.Connected &&
            appPrefs?.autoDisconnect != false
        ) {
            bgHandler.postDelayed(bgDisconnect, BACKGROUND_DISCONNECT_DELAY_MS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bgHandler.removeCallbacks(bgDisconnect)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipListener?.let { cm.removePrimaryClipChangedListener(it) }
        clipListener = null
        controller.release()
        manager.release()
    }

    /** Immersive: bar transparan, hide sistem bar (swipe untuk transient). */
    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun profileFromIntent(intent: Intent): ConnectionProfile? {
        val host = intent.getStringExtra(EXTRA_HOST) ?: return null
        return try {
            ConnectionProfile(
                host = host,
                port = intent.getIntExtra(EXTRA_PORT, 3389),
                username = intent.getStringExtra(EXTRA_USER),
                password = intent.getStringExtra(EXTRA_PASS),
                domain = intent.getStringExtra(EXTRA_DOMAIN),
                label = intent.getStringExtra(EXTRA_LABEL),
            )
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "profil dari intent tidak valid: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "XyDeskSessionAct"

        const val EXTRA_HOST = "xydesk.host"
        const val EXTRA_PORT = "xydesk.port"
        const val EXTRA_USER = "xydesk.user"
        const val EXTRA_PASS = "xydesk.pass"
        const val EXTRA_DOMAIN = "xydesk.domain"
        const val EXTRA_LABEL = "xydesk.label"

        /** M1.2b — auto-disconnect kalau app di-background (default ON). */
        const val BACKGROUND_DISCONNECT_DELAY_MS = 15_000L

        fun connectIntent(profile: ConnectionProfile): Intent =
            Intent(null, XyDeskSessionActivity::class.java).apply {
                putExtra(EXTRA_HOST, profile.host)
                putExtra(EXTRA_PORT, profile.port)
                putExtra(EXTRA_USER, profile.username)
                putExtra(EXTRA_PASS, profile.password)
                putExtra(EXTRA_DOMAIN, profile.domain)
                putExtra(EXTRA_LABEL, profile.label)
            }
    }
}
