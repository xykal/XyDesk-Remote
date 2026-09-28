package id.xydesk.remote.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.os.Handler
import android.os.Looper
import android.util.Log
import id.xydesk.remote.security.CrashLog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import android.content.res.Configuration
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Build
import id.xydesk.remote.XySessionBridge
import id.xydesk.remote.XySessionService
import id.xydesk.remote.core.ConnectionLog
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.RdpOptions
import id.xydesk.remote.core.SessionManager
import id.xydesk.remote.core.SessionState
import id.xydesk.remote.ui.components.XyNoticeBus
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
            ConnectionLog.addThrowable("SES: FAIL inisialisasi", t)
            Log.e(TAG, "gagal inisialisasi sesi", t)
            CrashLog.note(this, "inisialisasi sesi gagal: ${t.stackTraceToString().take(12_000)}")
            XyNoticeBus.post("Gagal menyiapkan sesi: ${t.message}")
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
        requestRuntimePermissions(profile)
        setContent {
            // Layar sesi SELALU gelap, apa pun mode tema app. Alasannya dua:
            // (1) HUD berada di atas gambar remote, jadi kontrasnya tidak boleh
            //     bergantung tema sistem; (2) dulu di mode terang panel HUD
            //     memakai komponen skema terang di atas panel gelap — teks jadi
            //     tidak terbaca ("ketabrak").
            XyDeskTheme(dark = true) {
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
            ConnectionLog.addThrowable("SES: connect() JVM-exception", t)
            Log.e(TAG, "connect() gagal", t)
            CrashLog.note(this, "connect() gagal: ${t.stackTraceToString().take(12_000)}")
        }
    }

    override fun onStart() {
        super.onStart()
        // balik dari background sebelum timer jalan = cancel auto-disconnect
        bgHandler.removeCallbacks(bgDisconnect)
        // Notifikasi punya tombol "Putuskan" — sambungkan ke sesi yang hidup.
        XySessionBridge.onStopRequested = {
            runOnUiThread {
                if (manager.state.value is SessionState.Connected) manager.disconnect()
                XySessionService.stop(this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (manager.state.value !is SessionState.Connected) return
        when {
            // Default: sesi dibiarkan hidup di latar lewat foreground service.
            appPrefs?.keepAlive != false -> {
                Log.i(TAG, "keep-alive: sesi jalan di latar (foreground service)")
                XySessionService.start(this, null, ping = true)
            }

            appPrefs?.autoDisconnect != false -> {
                bgHandler.postDelayed(bgDisconnect, BACKGROUND_DISCONNECT_DELAY_MS)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bgHandler.removeCallbacks(bgDisconnect)
        XySessionBridge.onStopRequested = null
        XySessionService.stop(this)
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipListener?.let { cm.removePrimaryClipChangedListener(it) }
        clipListener = null
        controller.release()
        manager.release()
    }

    /** Immersive: bar transparan, hide sistem bar (swipe untuk transient). */
    private fun hideSystemBars() {
        // Bar transparan tidak lagi di-set lewat window.statusBarColor /
        // navigationBarColor (no-op dan deprecated sejak API 35); cukup
        // edge-to-edge + hide() di bawah.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Keyboard HP mengetik ke remote.
     *
     * Inti FreeRDP (SessionView) memakai BaseInputConnection kosong, jadi
     * karakter dari keyboard HP tidak pernah sampai ke KeyboardMapper.
     * Di sini koneksi input diganti versi yang mengubah tiap karakter jadi
     * KeyEvent Android, sehingga jalur key sudah terbukti milik inti
     * (scancode, modifier, kombinasi) yang dipakai.
     */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN
        val target: View = window.decorView
        return ForwardingInputConnection(target, true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        controller.onKeyEvent(event) || super.dispatchKeyEvent(event)

    private inner class ForwardingInputConnection(
        target: View,
        mutable: Boolean,
    ) : BaseInputConnection(target, mutable) {

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            text?.forEach { ch -> sendChar(ch) }
            return true
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean = controller.onKeyEvent(event)

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            repeat(beforeLength.coerceAtLeast(0)) {
                controller.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                controller.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            }
            return true
        }

        private fun sendChar(ch: Char) {
            val code = keyCodeOf(ch)
            if (code != 0) {
                controller.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                controller.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            } else {
                // Karakter yang tidak punya keycode (mis. emoji, aksen):
                // dikirim sebagai unicode supaya tetap masuk.
                controller.sendUnicode(ch.code)
            }
        }
    }

    /**
     * Izin runtime yang diminta mengikuti kanal yang benar-benar ada di
     * biner: KAMERA (rdpecam) dan MIKROFON (audin, capture lewat OpenSLES).
     * Keduanya terverifikasi ada di libfreerdp-client3.so; capture OpenSLES
     * di Android wajib punya izin RECORD_AUDIO yang diberikan user, jadi
     * izinnya diminta tepat sebelum menyambung saat opsi mikrofon menyala.
     */
    private fun requestRuntimePermissions(profile: ConnectionProfile) {
        val options = runCatching { RdpOptions.of(this, profile.id) }.getOrNull()
            ?: return
        val wanted = buildList {
            if (options.camera) add(Manifest.permission.CAMERA)
            if (options.microphone) add(Manifest.permission.RECORD_AUDIO)
            // Notifikasi sesi (foreground service). Tanpa izin ini service tetap
            // jalan, tapi notifikasinya tidak terlihat user.
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (wanted.isEmpty()) return
        ConnectionLog.add("SES: minta izin runtime ${wanted.joinToString()}")
        permissionLauncher.launch(wanted.toTypedArray())
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            ConnectionLog.add("SES: hasil izin ${result.entries.joinToString { "${it.key}=${it.value}" }}")
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

        /** Karakter -> keycode Android untuk KeyboardMapper inti. */
        fun keyCodeOf(ch: Char): Int = when {
            ch in 'a'..'z' -> KeyEvent.KEYCODE_A + (ch - 'a')
            ch in 'A'..'Z' -> KeyEvent.KEYCODE_A + (ch - 'A')
            ch in '0'..'9' -> KeyEvent.KEYCODE_0 + (ch - '0')
            else -> when (ch) {
                ' ' -> KeyEvent.KEYCODE_SPACE
                '\n' -> KeyEvent.KEYCODE_ENTER
                '\t' -> KeyEvent.KEYCODE_TAB
                '.' -> KeyEvent.KEYCODE_PERIOD
                ',' -> KeyEvent.KEYCODE_COMMA
                '-' -> KeyEvent.KEYCODE_MINUS
                '=' -> KeyEvent.KEYCODE_EQUALS
                '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
                ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
                ';' -> KeyEvent.KEYCODE_SEMICOLON
                '\'' -> KeyEvent.KEYCODE_APOSTROPHE
                '/' -> KeyEvent.KEYCODE_SLASH
                '\\' -> KeyEvent.KEYCODE_BACKSLASH
                '`' -> KeyEvent.KEYCODE_GRAVE
                '@' -> KeyEvent.KEYCODE_AT
                '*' -> KeyEvent.KEYCODE_STAR
                '#' -> KeyEvent.KEYCODE_POUND
                '+' -> KeyEvent.KEYCODE_PLUS
                else -> 0
            }
        }

        /** M1.2b — auto-disconnect kalau app di-background (default ON). */
        const val BACKGROUND_DISCONNECT_DELAY_MS = 15_000L

        fun connectIntent(context: Context, profile: ConnectionProfile): Intent =
            Intent(context, XyDeskSessionActivity::class.java).apply {
                putExtra(EXTRA_HOST, profile.host)
                putExtra(EXTRA_PORT, profile.port)
                putExtra(EXTRA_USER, profile.username)
                putExtra(EXTRA_PASS, profile.password)
                putExtra(EXTRA_DOMAIN, profile.domain)
                putExtra(EXTRA_LABEL, profile.label)
            }
    }
}
