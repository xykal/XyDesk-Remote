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
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.compose.ui.geometry.Offset
import com.freerdp.freerdpcore.application.GlobalApp
import com.freerdp.freerdpcore.presentation.ExtendedKeyboardView
import com.freerdp.freerdpcore.presentation.ScrollView2D
import com.freerdp.freerdpcore.presentation.SessionInputManager
import com.freerdp.freerdpcore.presentation.SessionView
import com.freerdp.freerdpcore.presentation.TouchPointerView
import com.freerdp.freerdpcore.services.LibFreeRDP
import id.xydesk.remote.core.GraphicsSink

/**
 * M2 — controller surface sesi XyDesk.
 *
 * Menjamu view render inti (SessionView + TouchPointerView +
 * ExtendedKeyboardView + ScrollView2D — susunan programatik dari
 * `session.xml` milik core) dan mengimplementasikan [GraphicsSink]:
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
class SessionSurfaceController(private val activity: Activity) : GraphicsSink {

    private val uiHandler = Handler(Looper.getMainLooper())

    private var rootView: RelativeLayout? = null
    private var scrollView: ScrollView2D? = null
    private var sessionView: SessionView? = null
    private var touchPointerView: TouchPointerView? = null
    private var keyboard: ExtendedKeyboardView? = null
    private var inputManager: SessionInputManager? = null

    @Volatile private var inst = 0L
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

        val kb = ExtendedKeyboardView(context)
        val kbId = View.generateViewId()
        kb.id = kbId
        val kbParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply { addRule(RelativeLayout.ALIGN_PARENT_BOTTOM) }
        kb.visibility = View.GONE
        root.addView(kb, kbParams)

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
            addRule(RelativeLayout.ABOVE, kbId)
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
        val im = SessionInputManager(activity, scroller, sv, tpv, kb)
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
        keyboard = kb
        inputManager = im
        viewReady = true

        installInsetsHandling(root, scroller, kb)
        root.requestApplyInsets()

        // Catch-up: kalau resize sudah terjadi sebelum view tree siap
        // (jarang — connect native butuh >100ms, factory jalan <100ms),
        // ambil surface yang sudah ada sekarang.
        catchUpSurface()

        root.post { sv.requestFocus() }
        return root
    }

    /**
     * Insets: keyboard extended harus NAIK di atas IME (bottom margin =
     * tinggi IME), bukan tenggelam di belakangnya. Ini jalur yang sama
     * dipakai SessionActivity inti; sebelumnya controller ini tidak
     * handle sama sekali sehingga baris tombol fungsi keyboard tertutup
     * keyboard sistem.
     */
    private fun installInsetsHandling(
        root: RelativeLayout,
        scroller: ScrollView2D,
        kb: ExtendedKeyboardView,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            inputManager?.onImeVisibilityChanged(imeBottom > 0)
            // IME sudah termasuk area nav bar: jangan padding dua kali
            kb.setInsets(nav.left, nav.right, if (imeBottom > 0) 0 else nav.bottom)
            val kbdVisible = kb.visibility == View.VISIBLE
            scroller.setPadding(nav.left, 0, nav.right, if (kbdVisible) 0 else nav.bottom)
            val lp = kb.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null && lp.bottomMargin != imeBottom) {
                lp.bottomMargin = imeBottom
                kb.layoutParams = lp
            }
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

    /** Sinkronkan visibilitas keyboard (idempotent). */
    fun setKeyboardVisible(shown: Boolean) {
        uiHandler.post {
            val kb = keyboard ?: return@post
            val visible = kb.visibility == View.VISIBLE
            if (shown != visible) {
                inputManager?.toggleKeyboard()
                rootView?.requestApplyInsets()
            }
        }
    }

    fun isKeyboardVisible(): Boolean = keyboard?.visibility == View.VISIBLE

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
        keyboard = null
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
        }
    }

    override fun onPointerSetNull() {
        uiHandler.post {
            sessionView?.setRemoteCursor(null, 0, 0, 0, 0)
            touchPointerView?.setRemoteCursor(null, 0, 0, 0, 0)
        }
    }

    override fun onPointerSetDefault() {
        uiHandler.post { sessionView?.setDefaultCursor() }
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
