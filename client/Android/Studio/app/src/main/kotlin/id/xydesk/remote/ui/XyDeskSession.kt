package id.xydesk.remote.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.input.ImeAction
import id.xydesk.remote.ui.components.XyDialog
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyOverlay
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.freerdp.freerdpcore.utils.Mouse
import id.xydesk.remote.core.CertificateInfo
import id.xydesk.remote.XySessionService
import id.xydesk.remote.core.ConnectionLog
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.core.SessionManager
import id.xydesk.remote.core.SessionState
import id.xydesk.remote.core.TelemetrySample
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyNoticeHost
import id.xydesk.remote.ui.components.rememberXyNotice
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySpinner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private data class CertPrompt(
    val info: CertificateInfo,
    val oldFingerprint: String?,
    val reply: (Int) -> Unit,
)

private data class NlaPrompt(
    val user: String?,
    val domain: String?,
    val reply: (String?, String?, String?) -> Unit,
)

/**
 * Layar sesi XyDesk.
 *
 * Susunan lapisan (bawah ke atas):
 *  1. Surface RDP (SessionView + keyboard inti) via [AndroidView]
 *  2. Lapisan gesture trackpad (hanya mode trackpad)
 *  3. Kontrol sesi: pointer tunggal, cluster tombol mouse, tombol keyboard,
 *     handle panel
 *
 * Tidak ada bar atas berisi host/IP: info itu pindah ke panel (handle panah
 * di tepi kanan). Selama connecting, layar menampilkan wallpaper perangkat
 * yang diburamkan + langkah koneksi, bukan layar hitam kosong.
 */
/**
 * Pecah preset resolusi "1920x1080" jadi pasangan lebar/tinggi.
 * Balikan null kalau bukan format itu atau di luar batas yang masuk akal.
 */
private fun parseSize(preset: String): Pair<Int, Int>? {
    val parts = preset.split('x')
    if (parts.size != 2) return null
    val w = parts[0].toIntOrNull() ?: return null
    val h = parts[1].toIntOrNull() ?: return null
    if (w !in 640..8192 || h !in 480..8192) return null
    return w to h
}

@Composable
fun XyDeskSessionScreen(
    profile: ConnectionProfile,
    manager: SessionManager,
    controller: SessionSurfaceController,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val state by manager.state.collectAsState(initial = SessionState.Idle)
    val stage by manager.stage.collectAsState(initial = SessionManager.Stage.IDLE)
    val telemetry by manager.telemetry.collectAsState(initial = TelemetrySample.EMPTY)
    val prefs = remember { SessionPrefs(context) }
    val trustStore = remember { CertificateTrustStore(context) }
    val wall = remember(profile.id) {
        XyWall.NEUTRAL.forDevice(profile.label ?: profile.host)
    }
    var certPrompt by remember { mutableStateOf<CertPrompt?>(null) }
    var nlaPrompt by remember { mutableStateOf<NlaPrompt?>(null) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(prefs.zoom(profile.id)) }
    var bound by remember { mutableStateOf(false) }
    var inputMode by remember { mutableIntStateOf(prefs.inputMode.ordinal) }
    var pointerVisible by remember { mutableStateOf(true) }
    // Keyboard HP (IME) — satu-satunya keyboard. Board keyboard virtual dan
    // toolbar di atas keyboard sudah dihapus dari produk (ronde 5).
    var keyboardShown by remember(profile.id) { mutableStateOf(prefs.keyboardShown(profile.id)) }
    var hudKeys by remember(profile.id) {
        // Layout lama dimigrasi tanpa mengubah posisi/ukuran yang sudah diatur.
        mutableStateOf(HudKey.migrate(prefs.hudKeys(profile.id)))
    }
    var mappingMode by remember { mutableStateOf(false) }
    var remoteCursor by remember { mutableStateOf<RemoteCursor?>(null) }
    val notice = rememberXyNotice()
    var autoFit by remember { mutableStateOf(prefs.autoFit) }
    /** Ukuran area gambar (tanpa kontrol), dipakai untuk resize yang akurat. */
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    /** Resize yang harus dikirim ulang setelah reconnect (ganti resolusi). */
    var pendingResize by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // Info teknis untuk laporan bug: versi FreeRDP + ringkasan build JNI.
    val coreInfo = remember {
        runCatching {
            listOf(
                "Inti RDP ${manager.freeRdpVersion()}",
                manager.buildInfo().lineSequence().firstOrNull()?.take(120).orEmpty(),
            ).filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }
    val latchedKeys = remember(profile.id) { mutableStateMapOf<String, Boolean>() }

    fun setHudKeys(list: List<HudKey>) {
        hudKeys = list
        prefs.setHudKeys(profile.id, list)
    }
    var cursorX by remember { mutableFloatStateOf(0f) }
    var cursorY by remember { mutableFloatStateOf(0f) }
    var cursorInit by remember { mutableStateOf(false) }
    var applyingResolution by remember { mutableStateOf(false) }

    val remoteWidth = if (telemetry.width > 0) telemetry.width else 1920
    val remoteHeight = if (telemetry.height > 0) telemetry.height else 1080

    LaunchedEffect(profile.id) {
        rotationFor(context, profile.id).let { mode ->
            (context as? Activity)?.requestedOrientation = when (mode) {
                "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
        }
    }

    LaunchedEffect(Unit) {
        manager.setListener(object : SessionManager.Listener {
            override fun onCertificatePrompt(info: CertificateInfo, reply: (Int) -> Unit) {
                val stored = trustStore.fingerprint(info.host, info.port)
                if (stored != null && stored == info.fingerprint) {
                    reply(CertificateInfo.VERIFY_ACCEPT)
                    return
                }
                certPrompt = CertPrompt(info, stored, reply)
            }

            override fun onCredentialsPrompt(
                username: String?,
                domain: String?,
                reply: (String?, String?, String?) -> Unit,
            ) {
                nlaPrompt = NlaPrompt(username, domain, reply)
            }

            override fun onRemoteClipboardText(text: String) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("rdp", text))
            }
        })
    }

    LaunchedEffect(Unit) {
        // Bentuk kursor dari server (panah/tangan/I-beam/...) dipakai apa adanya.
        controller.onRemoteCursor = { cursor -> remoteCursor = cursor }
    }

    LaunchedEffect(Unit) {
        controller.onZoomChanged = { z ->
            zoom = z
            prefs.setZoom(profile.id, z)
        }
        controller.setInstance(manager.instance())
    }

    // Foreground service: sesi tetap hidup saat app ditinggal ke latar, dan
    // notifikasinya memberi jalan pulang + tombol putus.
    LaunchedEffect(state) {
        val label = profile.label ?: "${profile.host}:${profile.port}"
        if (state is SessionState.Connected) {
            XySessionService.start(context, label)
        } else if (state is SessionState.Disconnected) {
            XySessionService.stop(context)
        }
    }

    LaunchedEffect(state) {
        if (state is SessionState.Connected && !bound) {
            bound = true
            controller.bind(manager.instance())
            controller.setImeVisible(keyboardShown)
            when {
                // Zoom sendiri menang; kalau belum pernah diatur dan auto-fit
                // menyala, seluruh desktop dimuat (taskbar ikut kelihatan).
                prefs.hasZoom(profile.id) -> controller.applyZoom(zoom)
                prefs.autoFit -> controller.fitToScreen()
                else -> {
                    val dpi = DisplayPrefs.dpi(context, profile.id) / 100f
                    if (dpi != 1f) controller.applyZoom(dpi) else controller.fitToScreen()
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    LaunchedEffect(state, configuration.screenWidthDp, configuration.screenHeightDp, viewport) {
        if (state is SessionState.Connected && !applyingResolution &&
            DisplayPrefs.resolution(context, profile.id) == DisplayPrefs.AUTOMATIC &&
            viewport.width > 0 && viewport.height > 0
        ) {
            // Ukuran yang dikirim = area gambar yang benar-benar terlihat.
            // Dengan begitu tidak ada bagian desktop yang jatuh di luar layar.
            manager.resizeRemote(viewport.width, viewport.height)
            if (autoFit) controller.fitToScreen()
        }
    }

    // Kalau ukuran layar berubah (rotasi), desktop remote ikut menyesuaikan.
    LaunchedEffect(viewport) {
        if (state is SessionState.Connected && autoFit && viewport.width > 0) {
            controller.fitToScreen()
        }
    }

    LaunchedEffect(state, applyingResolution) {
        if (applyingResolution && state is SessionState.Disconnected) {
            applyingResolution = false
            bound = false
            manager.connect(profile)
        }
    }

    // Setelah reconnect karena ganti resolusi: kirim ukuran yang diminta
    // sekali lagi lewat kanal DISP, lalu muat seluruh desktop.
    LaunchedEffect(state) {
        val want = pendingResize ?: return@LaunchedEffect
        if (state is SessionState.Connected) {
            manager.resizeRemote(want.first, want.second)
            controller.fitToScreen()
            pendingResize = null
        }
    }

    LaunchedEffect(state) {
        val act = context as? Activity ?: return@LaunchedEffect
        if (state is SessionState.Connected) {
            act.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            act.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(state, certPrompt != null, nlaPrompt != null, confirmDisconnect) {
        (context as? XyDeskSessionActivity)?.backHandler = {
            when {
                certPrompt != null || nlaPrompt != null -> true
                confirmDisconnect -> {
                    confirmDisconnect = false
                    true
                }
                state is SessionState.Connected -> {
                    confirmDisconnect = true
                    true
                }
                state is SessionState.Connecting || state is SessionState.Authenticating -> {
                    manager.cancelConnection()
                    true
                }
                else -> false
            }
        }
    }

    fun cursor(): Offset {
        if (!cursorInit) {
            cursorInit = true
            cursorX = remoteWidth / 2f
            cursorY = remoteHeight / 2f
        }
        return Offset(cursorX, cursorY)
    }

    fun movePointer(dxScreen: Float, dyScreen: Float) {
        val scale = if (zoom > 0.05f) zoom else 1f
        cursorX = (cursorX + dxScreen / scale).coerceIn(0f, remoteWidth.toFloat())
        cursorY = (cursorY + dyScreen / scale).coerceIn(0f, remoteHeight.toFloat())
        val c = cursor()
        manager.sendCursorEvent(c.x.roundToInt(), c.y.roundToInt(), Mouse.getMoveEvent())
    }

    fun sendButton(button: XyMouseButton, down: Boolean) {
        val c = cursor()
        val flags = when (button) {
            XyMouseButton.LEFT -> Mouse.getLeftButtonEvent(context, down)
            XyMouseButton.RIGHT -> Mouse.getRightButtonEvent(context, down)
            XyMouseButton.MIDDLE -> Mouse.getMiddleButtonEvent(down)
        }
        manager.sendCursorEvent(c.x.roundToInt(), c.y.roundToInt(), flags)
    }

    fun sendScroll(notches: Int) {
        val c = cursor()
        val amount = Mouse.WHEEL_DELTA * notches
        manager.sendCursorEvent(
            c.x.roundToInt(),
            c.y.roundToInt(),
            Mouse.getScrollEvent(context, amount),
        )
    }

    /**
     * Buka/tutup keyboard HP. Sumber kebenarannya state IME yang sebenarnya,
     * bukan state lokal: kalau user menutup keyboard lewat tombol back
     * Android, tombol rail tetap tahu keadaan aslinya.
     */
    fun toggleKeyboard() {
        val next = !controller.isImeVisible()
        keyboardShown = next
        prefs.setKeyboardShown(profile.id, next)
        controller.setImeVisible(next)
        notice.show(
            if (next) xyNow("Keyboard HP dibuka", "Phone keyboard shown")
            else xyNow("Keyboard HP ditutup", "Phone keyboard hidden"),
        )
    }

    /** Kirim aksi satu tombol HUD (down=true tekan, false lepas). */
    fun runHudKey(key: HudKey, down: Boolean) {
        when (key.kind) {
            HudKind.MOUSE_LEFT -> sendButton(XyMouseButton.LEFT, down)
            HudKind.MOUSE_RIGHT -> sendButton(XyMouseButton.RIGHT, down)
            HudKind.MOUSE_MIDDLE -> sendButton(XyMouseButton.MIDDLE, down)
            HudKind.SCROLL_UP -> if (down) sendScroll(1)
            HudKind.SCROLL_DOWN -> if (down) sendScroll(-1)
            HudKind.KEYBOARD -> if (down) toggleKeyboard()

            HudKind.INPUT_SWITCH -> if (down) {
                val next = if (InputMode.entries[inputMode] == InputMode.TRACKPAD) {
                    InputMode.DIRECT
                } else {
                    InputMode.TRACKPAD
                }
                inputMode = next.ordinal
                prefs.inputMode = next
                if (next == InputMode.DIRECT) controller.setImeVisible(keyboardShown)
            }

            HudKind.KEY -> {
                if (key.shift && down) controller.sendVirtualKey(KeyEvent.KEYCODE_SHIFT_LEFT, true)
                controller.sendVirtualKey(key.keyCode, down)
                if (key.shift && !down) controller.sendVirtualKey(KeyEvent.KEYCODE_SHIFT_LEFT, false)
            }

            HudKind.COMBO -> if (down) controller.sendCombo(key.combo)
        }
    }

    /**
     * Fase dari layer tombol -> aksi. TAP = tekan+lepas, HOLD = aktif selama
     * ditahan, TOGGLE = nyala/mati dengan latch terpisah per tombol.
     */
    fun handleHudPhase(key: HudKey, phase: HudPhase) {
        when (key.action) {
            HudAction.HOLD -> runHudKey(key, phase == HudPhase.DOWN)

            HudAction.TOGGLE -> {
                if (phase != HudPhase.TAP) return
                val on = latchedKeys[key.id] == true
                latchedKeys[key.id] = !on
                runHudKey(key, !on)
            }

            HudAction.TAP -> {
                if (phase != HudPhase.TAP) return
                runHudKey(key, true)
                runHudKey(key, false)
            }
        }
    }

    val connected = state is SessionState.Connected

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewport = it },
    ) {
        AndroidView(
            factory = { ctx -> controller.buildViewTree(ctx) },
            modifier = Modifier.fillMaxSize(),
        )


        // Lapisan gesture trackpad: menutup surface supaya sentuhan tidak
        // diteruskan langsung sebagai klik di posisi jari.
        if (connected && InputMode.entries[inputMode] == InputMode.TRACKPAD) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(zoom, remoteWidth, remoteHeight) {
                        trackpadGestures(
                            scrollSpeed = prefs.scrollSpeed,
                            onMove = { dx, dy -> movePointer(dx, dy) },
                            onTap = {
                                sendButton(XyMouseButton.LEFT, true)
                                sendButton(XyMouseButton.LEFT, false)
                            },
                            onTwoFingerTap = {
                                sendButton(XyMouseButton.RIGHT, true)
                                sendButton(XyMouseButton.RIGHT, false)
                            },
                            onScroll = { sendScroll(it) },
                        )
                    },
            )
        }

        if (connected) {
            val pointerScreen = controller.remoteToScreen(cursor().x, cursor().y)
                ?: Offset(-1000f, -1000f)
            SessionControls(
                deviceId = profile.id,
                hostLabel = profile.label ?: "${profile.host}:${profile.port}",
                statusText = "Terhubung",
                remoteSize = if (telemetry.width > 0) "${telemetry.width} x ${telemetry.height}" else "menunggu server",
                zoomPercent = (zoom * 100).roundToInt(),
                pointerScreen = pointerScreen,
                pointerVisible = pointerVisible && InputMode.entries[inputMode] == InputMode.TRACKPAD,
                remoteCursor = remoteCursor,
                zoom = zoom,
                inputMode = InputMode.entries[inputMode],
                onInputModeChange = { mode ->
                    inputMode = mode.ordinal
                    prefs.inputMode = mode
                    if (mode == InputMode.DIRECT) controller.setImeVisible(keyboardShown)
                },
                keyboardShown = keyboardShown,
                onKeyboardShownChange = { shown ->
                    keyboardShown = shown
                    prefs.setKeyboardShown(profile.id, shown)
                    controller.setImeVisible(shown)
                },
                keys = hudKeys,
                onKeysChange = { setHudKeys(it) },
                mappingMode = mappingMode,
                onMappingModeChange = {
                    mappingMode = it
                    if (it) notice.show(xyNow("Geser tombol ke posisi yang kal mau", "Drag the buttons where you want them"))
                },
                onPhase = { key, phase -> handleHudPhase(key, phase) },
                onZoomIn = { controller.zoomIn() },
                onZoomOut = { controller.zoomOut() },
                onFit = { controller.fitToScreen() },
                onZoomActual = { controller.applyZoom(1f) },
                onScreenshot = {
                    val act = context as? Activity ?: return@SessionControls
                    val uri: Uri = controller.captureScreenshot(act) ?: return@SessionControls
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    act.startActivity(Intent.createChooser(send, "Bagikan screenshot"))
                },
                onDisconnect = { confirmDisconnect = true },
                onPointerVisibilityChange = { pointerVisible = it },
                onResetCluster = {
                    // Kembalikan tombol HUD ke set bawaan (klik kiri/kanan/
                    // tengah, scroll naik/turun, ganti mode input).
                    latchedKeys.clear()
                    setHudKeys(HudKey.defaults())
                },
                onRotationChange = { mode ->
                    (context as? Activity)?.requestedOrientation = when (mode) {
                        "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                    }
                    // Rotasi mengubah bentuk layar: ukuran dikirim ulang oleh
                    // efek yang memantau ukuran area gambar.
                    notice.show(xyNow("Orientasi diubah ke {0}", "Orientation set to {0}", mode))
                },
                onResolutionChange = { preset ->
                    // Jalur utama: ubah desktop remote saat sesi hidup lewat
                    // kanal DISP (/dynamic-resolution) — tidak perlu reconnect.
                    // Kalau server menolak, baru jatuh ke jalur reconnect.
                    val size = when (preset) {
                        DisplayPrefs.AUTOMATIC -> viewport.width to viewport.height
                        else -> parseSize(DisplayPrefs.resolvePreset(context, preset).orEmpty())
                    }
                    val live = size != null && size.first > 0 && size.second > 0 &&
                        manager.resizeRemote(size.first, size.second)
                    if (live) {
                        controller.fitToScreen()
                        notice.show(xyNow("Resolusi remote: {0} x {1}", "Remote resolution: {0} x {1}", size.first, size.second))
                    } else {
                        // Server menolak ukuran live -> reconnect dengan /size baru.
                        pendingResize = size
                        applyingResolution = true
                        manager.disconnect()
                        notice.show(xyNow("Menyambung ulang dengan resolusi baru", "Reconnecting with the new resolution"))
                    }
                },
                onToggleKeyboard = { toggleKeyboard() },
                onToggleTrackpad = {
                    val next = if (InputMode.entries[inputMode] == InputMode.TRACKPAD) {
                        InputMode.DIRECT
                    } else {
                        InputMode.TRACKPAD
                    }
                    inputMode = next.ordinal
                    prefs.inputMode = next
                    if (next == InputMode.DIRECT) controller.setImeVisible(keyboardShown)
                },
                // Teks bebas (unicode) — untuk password/URL/karakter yang
                // tidak ada di pemetaan tombol HUD.
                onSendText = { manager.sendText(it) },
                coreInfo = coreInfo,
                notice = notice,
            )

            // Pesan app sendiri (bukan Toast bawaan Android) — satu bahasa
            // visual dengan panel, ikut tema.
            XyNoticeHost(
                state = notice,
                modifier = Modifier.align(Alignment.TopCenter),
            )

        }

        if (state is SessionState.Connecting || state is SessionState.Authenticating) {
            ConnectingScreen(
                wall = wall,
                deviceLabel = profile.label ?: "${profile.host}:${profile.port}",
                stage = stage,
                onCancel = { manager.cancelConnection() },
            )
        }

        if (state is SessionState.Disconnected && !applyingResolution) {
            DisconnectedScreen(
                wall = wall,
                deviceLabel = profile.label ?: profile.host,
                onReconnect = {
                    bound = false
                    manager.connect(profile)
                },
                onExit = onExit,
            )
        }

        if (state is SessionState.Disconnected && applyingResolution) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    xy("Menerapkan resolusi baru...", "Applying new resolution..."),
                    color = Color(0xFFE7EDF2),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }

    val err = state as? SessionState.Error
    val active = certPrompt != null || nlaPrompt != null

    certPrompt?.let { p ->
        CertificateDialog(
            info = p.info,
            oldFingerprint = p.oldFingerprint,
            onReply = { code ->
                p.reply(code)
                certPrompt = null
            },
            onTrustRemember = {
                trustStore.trust(p.info.host, p.info.port, p.info.fingerprint)
                p.reply(CertificateInfo.VERIFY_ACCEPT)
                certPrompt = null
            },
        )
    }
    if (!active) nlaPrompt?.let { p ->
        NlaDialog(p) { u, d, pw ->
            p.reply(u, d, pw)
            nlaPrompt = null
        }
    }
    if (!active) err?.let { e ->
        XyOverlay(
            title = "Koneksi gagal",
            onDismiss = { onExit() },
        ) {
            Text(e.message, style = MaterialTheme.typography.bodyMedium)
            if (e.code == SessionManager.ERROR_UNREACHABLE) {
                Text(
                    "Host tidak menjawab di port RDP. Cek: RDP aktif " +
                        "(Windows Pro/Server), firewall, dan alamat/tailnet benar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    text = "Detail",
                    onClick = { showLog = true },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = "Reset resolusi",
                    onClick = {
                        DisplayPrefs.setResolution(context, profile.id, DisplayPrefs.AUTOMATIC)
                        manager.connect(profile)
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            XyPillButton(
                text = "Tutup",
                onClick = { onExit() },
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (!active && err != null && showLog) {
        XyOverlay(title = "Log koneksi", onDismiss = { showLog = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                ConnectionLog.last(60).forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            XyPillButton(
                text = "Tutup",
                onClick = { showLog = false },
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (!active && err == null && confirmDisconnect) {
        XyDialog(
            title = "Sesi masih aktif",
            body = "Putuskan sesi sekarang?",
            confirmLabel = "Putuskan",
            onConfirm = {
                confirmDisconnect = false
                manager.disconnect()
            },
            dismissLabel = "Batal",
            onDismiss = { confirmDisconnect = false },
        )
    }
}

// =============================================================
// Overlay koneksi
// =============================================================

private fun stepIndex(stage: SessionManager.Stage): Int = when (stage) {
    SessionManager.Stage.IDLE -> 0
    SessionManager.Stage.PROBE -> 1
    SessionManager.Stage.HANDSHAKE -> 2
    SessionManager.Stage.AUTH -> 3
    SessionManager.Stage.READY -> 4
}

private val connectSteps = listOf(
    "Menyiapkan sesi",
    "Memeriksa jaringan",
    "Security connect",
    "Autentikasi (NLA)",
    "Menyiapkan desktop",
)

@Composable
private fun ConnectingScreen(
    wall: XyWall,
    deviceLabel: String,
    stage: SessionManager.Stage,
    onCancel: () -> Unit,
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            elapsed++
        }
    }
    val active = stepIndex(stage)

    Box(Modifier.fillMaxSize()) {
        XyWallpaper(wall, blurRadius = 34.dp, dim = 0.62f)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                deviceLabel,
                color = Color.White.copy(alpha = 0.96f),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${elapsed}s",
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(26.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                connectSteps.forEachIndexed { index, title ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                            when {
                                index < active -> androidx.compose.material3.Icon(
                                    XyIcons.Check,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.size(14.dp),
                                )
                                index == active -> XySpinner(
                                    modifier = Modifier.size(14.dp),
                                    size = 14.dp,
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                                else -> Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.28f)),
                                )
                            }
                        }
                        Text(
                            title,
                            color = Color.White.copy(alpha = if (index <= active) 0.96f else 0.72f),
                            fontSize = 14.sp,
                            fontWeight = if (index == active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
            XyPillButton("Batalkan", onCancel, primary = false, compact = true)
        }
    }
}

@Composable
private fun DisconnectedScreen(
    wall: XyWall,
    deviceLabel: String,
    onReconnect: () -> Unit,
    onExit: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        XyWallpaper(wall, blurRadius = 40.dp, dim = 0.7f)
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Sesi terputus",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(deviceLabel, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))
            XyPillButton("Sambungkan lagi", onReconnect, compact = true)
            Spacer(Modifier.height(8.dp))
            XyPillButton("Kembali ke home", onExit, primary = false, compact = true)
        }
    }
}

// =============================================================
// Gesture trackpad
// =============================================================

/**
 * Gesture trackpad satu jari untuk gerak + ketuk, dua jari untuk scroll dan
 * klik kanan. Dipasang di lapisan atas surface supaya surface tidak
 * menerjemahkan sentuhan sebagai klik di titik jari.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.trackpadGestures(
    scrollSpeed: Float,
    onMove: (Float, Float) -> Unit,
    onTap: () -> Unit,
    onTwoFingerTap: () -> Unit,
    onScroll: (Int) -> Unit,
) {
    awaitEachGesture {
        val slop = viewConfiguration.touchSlop
        var maxPointers = 1
        var travelled = 0f
        var scrollAccum = 0f
        var sawMulti = false
        val first = awaitFirstDown(requireUnconsumed = false)
        var lastCentroid = first.position
        var released = false
        while (!released) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                released = true
                break
            }
            maxPointers = maxOf(maxPointers, pressed.size)
            val centroid = pressed.fold(Offset.Zero) { acc, change -> acc + change.position } /
                pressed.size.toFloat()
            val delta = centroid - lastCentroid
            lastCentroid = centroid
            val distance = delta.getDistance()
            if (distance > 0.15f) {
                travelled += distance
                if (pressed.size >= 2) {
                    sawMulti = true
                    scrollAccum += delta.y
                    val step = (26f / scrollSpeed).coerceAtLeast(6f)
                    while (scrollAccum <= -step) {
                        onScroll(1)
                        scrollAccum += step
                    }
                    while (scrollAccum >= step) {
                        onScroll(-1)
                        scrollAccum -= step
                    }
                } else if (!sawMulti) {
                    onMove(delta.x, delta.y)
                }
            }
            event.changes.forEach { change -> if (change.pressed) change.consume() }
        }
        if (travelled <= slop && maxPointers < 3) {
            if (maxPointers >= 2) onTwoFingerTap() else onTap()
        }
    }
}

private fun rotationFor(context: Context, deviceId: String): String =
    DisplayPrefs.rotation(context, deviceId)

// =============================================================
// Dialog sertifikat & NLA (sama seperti sebelumnya, tanpa jalur cloud)
// =============================================================

@Composable
private fun CertificateDialog(
    info: CertificateInfo,
    oldFingerprint: String?,
    onReply: (Int) -> Unit,
    onTrustRemember: () -> Unit,
) {
    XyOverlay(
        title = if (info.isChanged) "Sertifikat server berubah" else "Percaya sertifikat server?",
        onDismiss = null,
    ) {
        Text("${info.host}:${info.port}", style = MaterialTheme.typography.bodyMedium)
        if (info.isGateway) {
            Text(xy("Jenis: RDP Gateway", "Type: RDP Gateway"), style = MaterialTheme.typography.bodySmall)
        }
        if (info.isMismatch) {
            Text(
                "PERINGATAN: nama sertifikat tidak cocok dengan host",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (info.isChanged) {
            Text(
                "PERINGATAN: sertifikat berubah dari yang pernah diterima.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (oldFingerprint != null && oldFingerprint != info.fingerprint) {
            Text(
                "PERINGATAN: sertifikat BERUBAH dari yang pernah kamu percaya.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Tersimpan : $oldFingerprint",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text("Subject: ${info.subject}", style = MaterialTheme.typography.bodySmall)
        Text("Issuer: ${info.issuer}", style = MaterialTheme.typography.bodySmall)
        Text(xy("Fingerprint SHA-256:", "Fingerprint SHA-256:"), style = MaterialTheme.typography.bodySmall)
        Text(
            info.fingerprint,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        XyPillButton(
            text = "Percaya & ingat",
            onClick = onTrustRemember,
            compact = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XyPillButton(
                text = "Percaya (sekali)",
                onClick = { onReply(CertificateInfo.VERIFY_ACCEPT) },
                primary = false,
                compact = true,
                modifier = Modifier.weight(1f),
            )
            XyPillButton(
                text = "Tolak",
                onClick = { onReply(CertificateInfo.VERIFY_DENY) },
                primary = false,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NlaDialog(
    p: NlaPrompt,
    onReply: (String?, String?, String?) -> Unit,
) {
    var user by remember { mutableStateOf(p.user ?: "") }
    var domain by remember { mutableStateOf(p.domain ?: "") }
    var pass by remember { mutableStateOf("") }
    XyOverlay(title = "Masuk ke server", onDismiss = null) {
        Text(
            "Server meminta kredensial (NLA/CredSSP).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        XyField(value = user, onValueChange = { user = it }, label = "Username")
        XyField(value = domain, onValueChange = { domain = it }, label = "Domain (opsional)")
        XyField(
            value = pass,
            onValueChange = { pass = it },
            label = "Password",
            isPassword = true,
            imeAction = ImeAction.Done,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XyPillButton(
                text = "Masuk",
                onClick = {
                    onReply(
                        user.ifBlank { null },
                        domain.ifBlank { null },
                        pass.ifBlank { null },
                    )
                },
                enabled = user.isNotEmpty() || pass.isNotEmpty(),
                compact = true,
                modifier = Modifier.weight(1f),
            )
            XyPillButton(
                text = "Batal",
                onClick = { onReply(null, null, null) },
                primary = false,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
