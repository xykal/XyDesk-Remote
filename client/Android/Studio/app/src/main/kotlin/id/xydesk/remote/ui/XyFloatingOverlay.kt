package id.xydesk.remote.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Jendela Mengambang (Floating Mini-Monitor) berbasis `SYSTEM_ALERT_WINDOW`
 * ("Display over other apps" / Tampilkan di atas aplikasi lain).
 *
 * Dipakai saat pengguna menekan tombol PiP tetapi ROM perangkat memblokir
 * `enterPictureInPictureMode` bawaan, atau ketika pengguna memilih mengaktifkan
 * izin "Display over other apps" (mis. lewat AppsPerms / Setelan Khusus).
 */
object XyFloatingOverlay {

    private var windowManager: WindowManager? = null
    private var rootContainer: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var expandedSize = false

    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    fun openOverlayPermissionSettings(context: Context): Boolean = runCatching {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun isShowing(): Boolean = rootContainer != null

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        context: Context,
        title: String,
        bitmapProvider: () -> Bitmap?,
        onRestoreSession: () -> Unit,
    ): Boolean {
        if (!canDrawOverlays(context)) return false
        dismiss()

        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val density = appCtx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initW = if (expandedSize) dp(312) else dp(236)
        val initH = if (expandedSize) dp(198) else dp(154)

        val params = WindowManager.LayoutParams(
            initW,
            initH,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(72)
        }

        val container = LinearLayout(appCtx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12141A"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#343946"))
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            elevation = dp(10).toFloat()
        }

        val header = LinearLayout(appCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(6), dp(4))
        }

        val titleView = TextView(appCtx).apply {
            text = title
            setTextColor(Color.parseColor("#F4F6F8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        fun makeBadgeButton(label: String, bgHex: String, onClick: () -> Unit): TextView =
            TextView(appCtx).apply {
                text = label
                setTextColor(Color.parseColor("#F4F6F8"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = dp(99).toFloat()
                    setStroke(1, Color.parseColor("#3E4452"))
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                lp.marginStart = dp(4)
                layoutParams = lp
                setOnClickListener { onClick() }
            }

        val sizeBtn = makeBadgeButton(if (expandedSize) "−" else "+", "#1C2029") {
            expandedSize = !expandedSize
            params.width = if (expandedSize) dp(312) else dp(236)
            params.height = if (expandedSize) dp(198) else dp(154)
            runCatching { wm.updateViewLayout(container, params) }
        }

        val openBtn = makeBadgeButton(xyNow("Buka", "Open"), "#262B38") {
            dismiss()
            onRestoreSession()
        }

        val closeBtn = makeBadgeButton("✕", "#1C2029") {
            dismiss()
        }

        header.addView(titleView)
        header.addView(sizeBtn)
        header.addView(openBtn)
        header.addView(closeBtn)

        val surfacePreview = object : View(appCtx) {
            private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            private val bgPaint = Paint().apply { color = Color.parseColor("#0A0B0E") }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#9DA5B4")
                textSize = dp(11).toFloat()
                textAlign = Paint.Align.CENTER
            }
            private val srcRect = Rect()
            private val dstRect = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                canvas.drawRect(0f, 0f, w, h, bgPaint)
                val bmp = bitmapProvider()
                if (bmp != null && !bmp.isRecycled && bmp.width > 0 && bmp.height > 0) {
                    runCatching {
                        synchronized(bmp) {
                            srcRect.set(0, 0, bmp.width, bmp.height)
                            val scale = minOf(w / bmp.width.toFloat(), h / bmp.height.toFloat())
                            val dw = bmp.width * scale
                            val dh = bmp.height * scale
                            val left = (w - dw) * 0.5f
                            val top = (h - dh) * 0.5f
                            dstRect.set(left, top, left + dw, top + dh)
                            canvas.drawBitmap(bmp, srcRect, dstRect, bitmapPaint)
                        }
                    }
                } else {
                    canvas.drawText(
                        xyNow("Sesi RDP Aktif (Ketuk untuk buka)", "Active RDP Session (Tap to open)"),
                        w * 0.5f,
                        h * 0.52f,
                        textPaint,
                    )
                }
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
        }

        var downRawX = 0f
        var downRawY = 0f
        var startWinX = 0
        var startWinY = 0
        var moved = false

        val dragListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startWinX = params.x
                    startWinY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) {
                        moved = true
                    }
                    if (moved) {
                        params.x = startWinX + dx
                        params.y = startWinY + dy
                        runCatching { wm.updateViewLayout(container, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        dismiss()
                        onRestoreSession()
                    }
                    true
                }
                else -> false
            }
        }

        header.setOnTouchListener(dragListener)
        surfacePreview.setOnTouchListener(dragListener)

        container.addView(header)
        container.addView(surfacePreview)

        val added = runCatching {
            wm.addView(container, params)
            true
        }.getOrDefault(false)

        if (!added) return false

        windowManager = wm
        rootContainer = container

        val ticker = object : Runnable {
            override fun run() {
                if (rootContainer == null) return
                surfacePreview.invalidate()
                mainHandler.postDelayed(this, 60L)
            }
        }
        refreshRunnable = ticker
        mainHandler.post(ticker)
        return true
    }

    fun dismiss() {
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        refreshRunnable = null
        val view = rootContainer
        val wm = windowManager
        rootContainer = null
        windowManager = null
        if (view != null && wm != null) {
            runCatching { wm.removeView(view) }
        }
    }
}
