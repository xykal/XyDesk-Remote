package id.xydesk.remote.ui

import android.app.Activity
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.compose.ui.geometry.Offset
import com.freerdp.freerdpcore.application.GlobalApp
import com.freerdp.freerdpcore.presentation.ScrollView2D
import com.freerdp.freerdpcore.presentation.SessionInputManager
import com.freerdp.freerdpcore.presentation.SessionView
import com.freerdp.freerdpcore.presentation.TouchPointerView
import com.freerdp.freerdpcore.services.LibFreeRDP
import id.xydesk.remote.core.GraphicsSink

/**
 * M2 — controller surface sesi XyDesk.
 *
 * Menjamu view render inti (SessionView + TouchPointerView + ScrollView2D —
 * susunan programatik dari `session.xml` milik core) dan mengimplementasikan [GraphicsSink]:
 *
 *  - OnGraphicsUpdate -> `LibFreeRDP.updateGraphics` (copy piksel ke
 *    bitmap permukaan) -> `SessionView.addInvalidRegion` + invalidate
 *    (main thread) — identik dengan jalur M0
 *    (`SessionActivity.OnGraphicsUpdate`).
 *  - OnGraphicsResize -> bitmap baru -> `SessionState.setSurface` ->
 *    `SessionView.onSurfaceChange` (main thread).
 *
 * Dipanggil dari thread RDP (sink) — semua modifikasi View di-post ke
 * main thread. View tree dibangun dari main thread (AndroidView factory).
 */
/**
 * Kursor yang dikirim server RDP.
 *
 * Server mengirim bitmap kursor SUNGGUHAN (panah, tangan, I-beam, resize,
 * dsb) lewat pointer update; kita cuma menggambarnya, jadi bentuknya sama
 * seperti di desktop remote. [visible] = false artinya server minta kursor
 * disembunyikan (mis. saat mengetik).
 */
data class RemoteCursor(
    val bitmap: android.graphics.Bitmap?,
    val hotX: Int,
    val hotY: Int,
    val visible: Boolean,
)

/** Karakter dari IME -> keycode Android (0 = tidak ada padanannya). */
internal fun xyKeyCodeOf(ch: Char): Int = when {
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

class SessionSurfaceController(private val activity: Activity) : GraphicsSink {

    private val uiHandler = Handler(Looper.getMainLooper())

    private var rootView: RelativeLayout? = null
    private var scrollView: ScrollView2D? = null
    private var sessionView: SessionView? = null
    private var touchPointerView: TouchPointerView? = null
    private var inputManager: SessionInputManager? = null

    @Volatile private var inst = 0L
    /** Tinggi IME terakhir (px). 0 = keyboard HP tidak tampil. */
    @Volatile var imeHeightPx: Int = 0
        private set
    private var lastImeBottom = -1
    /**
     * Dipanggil tiap tinggi IME berubah. UI memakai ini untuk menaruh
     * toolbar persis di atas keyboard HP dan menyembunyikannya saat
     * keyboard HP ditutup.
     */
    var onImeChanged: ((Int) -> Unit)? = null
    /** Bentuk kursor dari server untuk digambar Compose (null = panah bawaan). */
    var onRemoteCursor: ((RemoteCursor) -> Unit)? = null
    @Volatile private var bitmap: Bitmap? = null
    @Volatile private var viewReady = false

    /** Perubahan zoom (pinch/programatik) — dipanggil di main thread. */
    var onZoomChanged: ((Float) -> Unit)? = null

    val isViewReady: Boolean
        get() = viewReady

    /** Dipanggil (idempotent) sesegera mungkin setelah view tree siap. */
    fun setInstance(inst: Long) {
        if (inst == 0L) return
        this.inst = inst
        if (viewReady) catchUpSurface()
    }

    /**
     * Build view tree (dipanggil dari Compose `AndroidView` factory).
     * Wiring input identik dengan `SessionActivity.onCreate`.
     */
    fun buildViewTree(context: Context): View {
        if (viewReady) return rootView!!
        val root = RelativeLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        val scroller = ScrollView2D(context)
        val rail = FrameLayout(context)
        val sv = SessionView(context)
        rail.addView(
            sv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        scroller.addView(rail)
        val scrollerParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
        }
        root.addView(scroller, scrollerParams)

        val tpv = TouchPointerView(context)
        val tpvParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        tpv.visibility = View.INVISIBLE
        root.addView(tpv, tpvParams)

        // Wiring input — cermin SessionActivity.onCreate
        // Board keyboard bawaan FreeRDP tidak dipakai (ronde 5): semua
        // pengetikan lewat keyboard HP, jadi view-nya tidak pernah dibuat.
        val im = SessionInputManager(activity, scroller, sv, tpv, null)

        // Keyboard HP -> mapper tombol inti. SessionView menanyakan koneksi
        // input ke sini; karakter diubah jadi KeyEvent supaya scancode,
        // modifier, dan kombinasi ditangani jalur yang sudah terbukti.
        sv.setInputSink(object : SessionView.InputSink {
            override fun onChar(ch: Char) {
                val code = xyKeyCodeOf(ch)
                if (code != 0) {
                    im.onAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    im.onAndroidKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                } else {
                    im.processUnicodeKey(ch.code)
                }
            }

            override fun onKeyEvent(event: KeyEvent): Boolean = im.onAndroidKeyEvent(event)
        })
        sv.setSessionViewListener(im)
        tpv.setTouchPointerListener(im)
        sv.setScaleGestureDetector(
            ScaleGestureDetector(activity, im.getPinchZoomListener())
        )
        sv.setOnZoomChangedListener { z -> onZoomChanged?.invoke(z) }

        rootView = root
        scrollView = scroller
        sessionView = sv
        touchPointerView = tpv
        inputManager = im
        viewReady = true

        installInsetsHandling(root, scroller)
        root.requestApplyInsets()

        // Catch-up: kalau resize sudah terjadi sebelum view tree siap
        // (jarang — connect native butuh >100ms, factory jalan <100ms),
        // ambil surface yang sudah ada sekarang.
        catchUpSurface()

        root.post { sv.requestFocus() }
        return root
    }

    /**
     * Insets: satu-satunya hal yang perlu dilaporkan adalah status keyboard
     * HP (IME) ke input manager inti. Tidak ada lagi view keyboard yang perlu
     * digeser, jadi scroll view tidak diberi padding — inilah yang dulu bikin
     * strip gelap di tepi bawah dan menutupi taskbar remote.
     */
    private fun installInsetsHandling(
        root: RelativeLayout,
        scroller: ScrollView2D,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            val bottomInset = maxOf(nav.bottom, gestures)
            inputManager?.onImeVisibilityChanged(imeBottom > 0)
            if (imeBottom != lastImeBottom) {
                lastImeBottom = imeBottom
                imeHeightPx = imeBottom
                onImeChanged?.invoke(imeBottom)
            }
            scroller.setPadding(nav.left, 0, nav.right, 0)
            WindowInsetsCompat.CONSUMED
        }
    }

    /** Dipanggil saat state = Connected. */
    fun bind(inst: Long) {
        if (inst == 0L) return
        this.inst = inst
        uiHandler.post {
            val session = GlobalApp.getSession(inst) ?: return@post
            val surface = session.getSurface() ?: return@post
            bitmap = surface.bitmap
            sessionView?.onSurfaceChange(session)
            scrollView?.requestLayout()
            inputManager?.attachSession(inst, surface.bitmap)
            val root = rootView
            if (root != null && root.width > 0 && root.height > 0) {
                inputManager?.setScreenSize(root.width, root.height)
            }
        }
    }

    // ------------------------------------------------------------------
    // Controls (dipakai panel HUD — main thread)
    // ------------------------------------------------------------------

    fun currentZoom(): Float = sessionView?.getZoom() ?: 1f

    fun zoomIn(): Boolean = sessionView?.zoomIn(ZOOM_STEP) ?: false

    fun zoomOut(): Boolean = sessionView?.zoomOut(ZOOM_STEP) ?: false

    /** Scale the full remote desktop into the current device viewport. */
    fun fitToScreen() {
        val view = sessionView ?: return
        val frame = bitmap ?: return
        val width = (scrollView?.width ?: rootView?.width ?: 0).toFloat()
        val height = (scrollView?.height ?: rootView?.height ?: 0).toFloat()
        if (width <= 0f || height <= 0f || frame.width <= 0 || frame.height <= 0) return
        val factor = minOf(width / frame.width, height / frame.height)
        view.setZoom(factor.coerceIn(SessionView.MIN_SCALE_FACTOR, SessionView.MAX_SCALE_FACTOR))
    }

    fun toggleTouchPointer() {
        inputManager?.toggleTouchPointer()
    }

    fun toggleKeyboard() {
        inputManager?.toggleKeyboard()
    }

    /**
     * Kirim keycode Android (KeyEvent.KEYCODE_*) lewat input manager inti.
     * Pencocokan ke scancode RDP dilakukan KeyboardMapper, jadi overlay
     * keyboard XyDesk tidak perlu tahu tabel scancode.
     */
    fun sendVirtualKey(keyCode: Int, down: Boolean) {
        uiHandler.post { inputManager?.processVirtualKey(keyCode, down) }
    }

    /** Tekan beberapa tombol sekaligus (mis. Ctrl+Alt+Del) lalu lepas terbalik. */
    fun sendCombo(keyCodes: List<Int>) {
        if (keyCodes.isEmpty()) return
        keyCodes.forEach { sendVirtualKey(it, true) }
        keyCodes.reversed().forEach { sendVirtualKey(it, false) }
    }

    /** Kirim karakter unicode (dipakai kalau server tidak paham scancode-nya). */
    fun sendUnicode(ch: Int) {
        uiHandler.post { inputManager?.processUnicodeKey(ch) }
    }

    /**
     * Nyala/matikan keyboard HP (IME). Board keyboard bawaan sudah dibuang,
     * jadi ini satu-satunya jalur keyboard: tombol HUD "Keyboard" dan chip
     * di panel hanya memanggil ini.
     */
    fun setImeVisible(shown: Boolean) {
        uiHandler.post {
            val im = inputManager ?: return@post
            if (shown != im.isSoftInputActive()) {
                im.setSoftKeyboard(shown)
                rootView?.requestApplyInsets()
            }
        }
    }

    fun isImeVisible(): Boolean = inputManager?.isSoftInputActive() ?: false

    /**
     * Teruskan event tombol Android (dari keyboard HP / keyboard fisik) ke
     * mapper inti. Activity wajib memanggil ini dari dispatchKeyEvent —
     * tanpa jalur ini keyboard HP tidak bisa mengetik ke remote.
     */
    fun onKeyEvent(event: KeyEvent): Boolean = inputManager?.onAndroidKeyEvent(event) ?: false

    fun onKeyLongPress(keyCode: Int): Boolean = inputManager?.onAndroidKeyLongPress(keyCode) ?: false

    /**
     * Konversi koordinat desktop remote -> koordinat layar (px), dipakai
     * lapisan Compose untuk menggambar pointer sendiri. Return null kalau
     * surface belum siap.
     */
    fun remoteToScreen(x: Float, y: Float): Offset? {
        val sv = sessionView ?: return null
        val zoom = sv.getZoom().takeIf { it > 0.01f } ?: return null
        val container = if (scrollView?.childCount ?: 0 > 0) scrollView!!.getChildAt(0) else sv
        val screenX = x * zoom - (scrollView?.scrollX ?: 0) + container.left
        val screenY = y * zoom - (scrollView?.scrollY ?: 0) + container.top
        return Offset(screenX, screenY)
    }

    /** Terapkan zoom tersimpan (dipanggil setelah bind, main thread). */
    fun applyZoom(zoom: Float) {
        sessionView?.setZoom(zoom)
    }

    /**
     * M2.5 — screenshot surface: copy bitmap -> PNG di
     * `getExternalFilesDir/screenshots/` -> content URI (FileProvider)
     * untuk dibagikan. Return null kalau belum ada frame.
     */
    fun captureScreenshot(activity: Activity): Uri? {
        val src = bitmap ?: return null
        val shot = src.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            val dir = File(activity.getExternalFilesDir(null), "screenshots")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "xydesk-${System.currentTimeMillis()}.png")
            FileOutputStream(f).use { out ->
                shot.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            FileProvider.getUriForFile(activity, "${activity.packageName}.files", f)
        } finally {
            shot.recycle()
        }
    }

    fun release() {
        uiHandler.removeCallbacksAndMessages(null)
        inputManager?.cancelPendingEvents()
        inputManager?.hideKeyboards()
        inputManager = null
        sessionView = null
        touchPointerView = null
        scrollView = null
        rootView = null
        bitmap = null
        inst = 0L
        viewReady = false
    }

    // ------------------------------------------------------------------
    // GraphicsSink (dipanggil dari thread RDP)
    // ------------------------------------------------------------------

    override fun onGraphicsUpdate(x: Int, y: Int, width: Int, height: Int) {
        val i = inst
        val bm = bitmap
        if (i == 0L || bm == null) return
        // copy piksel remote ke bitmap permukaan (thread RDP — aman,
        // bitmap ditulis native & dibaca Canvas di main; sama dengan M0)
        LibFreeRDP.updateGraphics(i, bm, x, y, width, height)
        uiHandler.post {
            val sv = sessionView ?: return@post
            sv.addInvalidRegion(Rect(x, y, x + width, y + height))
            sv.invalidateRegion()
        }
    }

    override fun onGraphicsResize(width: Int, height: Int, bpp: Int) {
        val i = inst
        if (i == 0L) return
        val newBitmap = if (bpp > 16) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        }
        bitmap = newBitmap
        val session = GlobalApp.getSession(i) ?: return
        session.setSurface(BitmapDrawable(activity.getResources(), newBitmap))
        uiHandler.post {
            if (!viewReady) return@post
            val sv = sessionView ?: return@post
            sv.onSurfaceChange(session)
            scrollView?.requestLayout()
            inputManager?.setBitmap(newBitmap)
            inputManager?.attachSession(i, newBitmap)
        }
    }

    override fun onPointerSet(
        pixels: IntArray?,
        width: Int,
        height: Int,
        hotX: Int,
        hotY: Int,
    ) {
        uiHandler.post {
            sessionView?.setRemoteCursor(pixels, width, height, hotX, hotY)
            touchPointerView?.setRemoteCursor(pixels, width, height, hotX, hotY)
            if (pixels != null && width > 0 && height > 0) {
                val bmp = runCatching {
                    android.graphics.Bitmap.createBitmap(
                        pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888,
                    )
                }.getOrNull()
                if (bmp != null) onRemoteCursor?.invoke(RemoteCursor(bmp, hotX, hotY, true))
            }
        }
    }

    override fun onPointerSetNull() {
        uiHandler.post {
            sessionView?.setRemoteCursor(null, 0, 0, 0, 0)
            touchPointerView?.setRemoteCursor(null, 0, 0, 0, 0)
            onRemoteCursor?.invoke(RemoteCursor(null, 0, 0, false))
        }
    }

    override fun onPointerSetDefault() {
        uiHandler.post {
            sessionView?.setDefaultCursor()
            onRemoteCursor?.invoke(RemoteCursor(null, 0, 0, true))
        }
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    private fun catchUpSurface() {
        val i = inst
        if (i == 0L) return
        val session = GlobalApp.getSession(i) ?: return
        val surface = session.getSurface() ?: return
        bitmap = surface.bitmap
        uiHandler.post {
            if (!viewReady) return@post
            sessionView?.onSurfaceChange(session)
            scrollView?.requestLayout()
            inputManager?.setBitmap(surface.bitmap)
            inputManager?.attachSession(i, surface.bitmap)
        }
    }

    companion object {
        private const val ZOOM_STEP = 0.1f
    }
}
