package id.xydesk.remote.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.xydesk.remote.core.RefreshRatePolicy
import id.xydesk.remote.core.XyDirectConsoleStreamClient
import id.xydesk.remote.core.isGlobalPcId
import id.xydesk.remote.core.parsePcIdOrEndpoint
import id.xydesk.remote.pcstream.XyHostInputCodec
import id.xydesk.remote.pcstream.XyHostMouseButton
import id.xydesk.remote.pcstream.XyHubSessionState
import id.xydesk.remote.pcstream.XyHubStreamViewer
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.theme.XyDeskTheme
import id.xydesk.remote.ui.theme.XyThemeState
import id.xydesk.remote.ui.theme.xyDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sesi PC-stream produksi XyDesk dengan 2 engine terintegrasi:
 *  1) **Engine Konsol Mandiri (`XYV1` + `XYA1` + `XYI1` via UDP :4433)**:
 *     Terhubung langsung ke `XyDeskRemoteHost-Standalone.exe` di sesi konsol
 *     fisik Windows (`SM_REMOTESESSION == 0`, bebas deteksi RDP anti-cheat)
 *     dengan injeksi hardware `SendInput(MOUSEEVENTF_MOVE)` 120Hz untuk Game FPS.
 *  2) **Hub WebRTC (`signal.xydesk.my.id`)**:
 *     Pairing ID host 9 digit + PIN 6 digit → video WebRTC + DataChannel `input`.
 */
class PcStreamSessionActivity : ComponentActivity() {
    private var viewer: XyHubStreamViewer? = null
    private var eglBase: EglBase? = null
    private var directClient: XyDirectConsoleStreamClient? = null

    /** ID host sesi berjalan — kunci penyimpanan tata letak virtual pad. */
    private var activeHostId: String = ""
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Viewer memanggil listener-nya di thread utama — state Compose aman langsung.
    private val sessionState = mutableStateOf<XyHubSessionState?>(null)
    private val stateDetail = mutableStateOf<String?>(null)
    private val pairRejected = mutableStateOf<String?>(null)
    private val pairBlockedSeconds = mutableStateOf<Long?>(null)
    private val videoReady = mutableStateOf(false)
    private val inputOpen = mutableStateOf(false)
    private val mediaConnected = mutableStateOf(false)
    private val failure = mutableStateOf<String?>(null)
    private val started = mutableStateOf(false)

    // State khusus Engine Konsol Mandiri UDP :4433 (SM_REMOTESESSION == 0)
    private val directModeActive = mutableStateOf(false)
    private val directFrame = mutableStateOf<Bitmap?>(null)
    private val directFrameSeq = mutableIntStateOf(0)
    private val directStatus = mutableStateOf<String?>(null)
    private val directHostFps = mutableIntStateOf(0)
    private val directNonRdpVerified = mutableStateOf(false)
    private val discoveredHosts = mutableStateOf<List<XyDirectConsoleStreamClient.DiscoveredHost>>(emptyList())
    private val scanningLan = mutableStateOf(false)

    // Mode Game FPS (120Hz + Rotasi 360° RawInput + Tombol Tembak/Scope/WASD)
    private val fpsGameMode = mutableStateOf(true)

    /** Sesi jalan sudah tidak bisa diulang lagi (gagal/ditutup) — teks dirender di Compose. */
    private val sessionEnded = mutableStateOf(false)

    /** Notifikasi sekali-lihat dari host (mis. status Virtual Display). */
    private val hostNotice = mutableStateOf<String?>(null)

    /** Frame pertama dari host sudah sampai ke renderer. */
    @Volatile private var firstFrameSeen = false

    /** Watchdog: true = track siap tapi tidak ada frame dalam batas waktu. */
    private val noVideoSignal = mutableStateOf(false)

    /** Penyebab dari host (meta.capture.state / backend / blackFrames). */
    private val hostCaptureHint = mutableStateOf<String?>(null)

    private var firstFrameWatchdog: Job? = null

    /** Pairing tidak dijawab host dalam batas waktu (watchdog signaling). */
    private val pairingTimedOut = mutableStateOf(false)
    private var pairingWatchdog: Job? = null

    /** Kredensial sesi terakhir (in-memory saja) untuk tombol sambung ulang. */
    private var lastHostId: String = ""
    private var lastPin: String = ""
    private var lastDirectIp: String = ""
    private var lastDirectPort: Int = 4433
    private var lastDirectFps: Int = 120
    private val resolvingP2P = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyPreferredDisplayRefreshRate(120)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        val initialDirectHost = intent?.getStringExtra(EXTRA_DIRECT_HOST)?.trim().orEmpty()
        val initialDirectPort = intent?.getIntExtra(EXTRA_DIRECT_PORT, 4433) ?: 4433
        val initialDirectFps = intent?.getIntExtra(EXTRA_DIRECT_FPS, 120) ?: 120
        if (initialDirectHost.isNotEmpty()) {
            connectP2PTarget(initialDirectHost, initialDirectPort, initialDirectFps)
        }
        setContent {
            XyThemeState.init(AppPrefs(this))
            XyDeskTheme(dark = xyDark()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PcStreamScreen()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyPreferredDisplayRefreshRate(requestedHz: Int) {
        runCatching {
            val display = windowManager.defaultDisplay
            val currentMode = display.mode
            val sameResolution = display.supportedModes.filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            val modes = sameResolution.ifEmpty { display.supportedModes.toList() }
            val rates = modes.map { it.refreshRate }
            val preferred = RefreshRatePolicy.choose(requestedHz, rates)
                ?: display.refreshRate.takeIf { it.isFinite() && it > 0f }
                ?: return
            val bestMode = modes.minByOrNull { abs(it.refreshRate - preferred) }
            window.attributes = window.attributes.apply {
                preferredRefreshRate = preferred
                if (bestMode != null) {
                    preferredDisplayModeId = bestMode.modeId
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { directClient?.close() }
        directClient = null
        runCatching { viewer?.close() }
        viewer = null
        runCatching { eglBase?.release() }
        eglBase = null
    }

    private fun connectP2PTarget(rawTarget: String, defaultPort: Int = 4433, targetFps: Int = 120) {
        val trimmed = rawTarget.trim()
        if (trimmed.isEmpty()) return
        failure.value = null
        if (isGlobalPcId(trimmed)) {
            resolvingP2P.value = true
            scope.launch {
                val relay = runCatching {
                    CommunityJokesApi(this@PcStreamSessionActivity).pcRelayResolve(trimmed)
                }.getOrNull()
                val arr = relay?.optJSONArray("candidates")
                val candidates = buildList {
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val c = arr.optJSONObject(i) ?: continue
                            val h = c.optString("host").trim()
                            if (h.isNotEmpty()) add(h)
                        }
                    }
                }
                val bestHost = withContext(Dispatchers.IO) {
                    var responsive: String? = null
                    for (cand in candidates) {
                        val probed = XyDirectConsoleStreamClient.probeHost(cand, defaultPort, timeoutMs = 420)
                        if (probed != null) {
                            responsive = probed.ip
                            break
                        }
                    }
                    if (responsive == null) {
                        val lanFound = XyDirectConsoleStreamClient.discoverHosts(timeoutMs = 700, port = defaultPort)
                        responsive = lanFound.firstOrNull()?.ip
                    }
                    responsive ?: candidates.firstOrNull()
                }
                resolvingP2P.value = false
                if (bestHost.isNullOrBlank()) {
                    failure.value = xyNow(
                        "ID Global $trimmed tidak ditemukan di direktori P2P maupun LAN — pastikan XyDeskRemoteHost.exe berjalan di PC.",
                        "Global ID $trimmed not found in P2P directory or LAN — make sure XyDeskRemoteHost.exe is running on the PC.",
                    )
                } else {
                    startDirectConsoleSession(bestHost, defaultPort, targetFps)
                }
            }
            return
        }
        val ep = parsePcIdOrEndpoint(trimmed, defaultPort = defaultPort)
        val hostIp = ep?.host ?: trimmed.substringBefore(':')
        val hostPort = ep?.port?.let { if (it == 3389) defaultPort else it } ?: defaultPort
        startDirectConsoleSession(hostIp, hostPort, targetFps)
    }

    private fun startDirectConsoleSession(hostIp: String, port: Int = 4433, targetFps: Int = 120) {
        val cleanIp = hostIp.trim()
        if (cleanIp.isEmpty()) return
        directClient?.close()
        directClient = null
        lastDirectIp = cleanIp
        lastDirectPort = port
        lastDirectFps = targetFps
        activeHostId = cleanIp
        directModeActive.value = true
        videoReady.value = true
        inputOpen.value = true
        mediaConnected.value = false
        noVideoSignal.value = false
        firstFrameSeen = false
        applyPreferredDisplayRefreshRate(targetFps)

        val client = XyDirectConsoleStreamClient(
            host = cleanIp,
            port = port,
            targetFps = targetFps,
            jpegQuality = 88,
            scalePct = 100,
            enableAudio = true,
            onFrame = { bmp, _, _, hostFps, nonRdp ->
                if (!firstFrameSeen) {
                    firstFrameSeen = true
                    noVideoSignal.value = false
                    mediaConnected.value = true
                    firstFrameWatchdog?.cancel()
                }
                directFrame.value = bmp
                directFrameSeq.intValue++
                directHostFps.intValue = hostFps
                directNonRdpVerified.value = nonRdp
                SessionPreviewStore.saveDesktopSnapshotThrottled(
                    context = applicationContext,
                    hostKey = cleanIp,
                    bitmap = bmp,
                )
            },
            onState = { connected, detail ->
                directStatus.value = detail
                if (connected) mediaConnected.value = true
            },
        )
        directClient = client
        client.start()
        armFirstFrameWatchdog()
    }

    private fun scanLanConsoleHosts() {
        if (scanningLan.value) return
        scanningLan.value = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                XyDirectConsoleStreamClient.discoverHosts(timeoutMs = 950)
            }
            discoveredHosts.value = found
            scanningLan.value = false
        }
    }

    private fun startSession(hostId: String, pin: String) {
        directModeActive.value = false
        if (started.value) {
            val current = sessionState.value
            val retryable = current == XyHubSessionState.REGISTERED || current == XyHubSessionState.PAIRING
            if (retryable) {
                pairingTimedOut.value = false
                runCatching { viewer?.retryPair(pin, deviceName()) }
                    .onFailure { failure.value = it.message }
            } else {
                sessionEnded.value = true
            }
            return
        }
        started.value = true
        activeHostId = hostId
        lastHostId = hostId
        lastPin = pin
        val egl = EglBase.create()
        eglBase = egl
        val streamViewer = XyHubStreamViewer(this, egl.eglBaseContext, viewerListener)
        viewer = streamViewer
        scope.launch {
            runCatching { streamViewer.start(hostId, pin, deviceName()) }
                .onFailure { failure.value = it.message }
        }
    }

    private fun deviceName(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(32).ifBlank { "Android" }

    private val viewerListener = object : XyHubStreamViewer.Listener {
        override fun onViewerState(state: XyHubSessionState, detail: String?) {
            sessionState.value = state
            stateDetail.value = detail
            when (state) {
                XyHubSessionState.PAIRING -> armPairingWatchdog()
                XyHubSessionState.PAIRED,
                XyHubSessionState.FAILED,
                XyHubSessionState.CLOSED,
                XyHubSessionState.REGISTERED,
                -> disarmPairingWatchdog()
                else -> Unit
            }
        }

        override fun onPairAccepted(hostId: String) {
            pairRejected.value = null
            pairBlockedSeconds.value = null
        }

        override fun onPairRejected(reason: String?) {
            pairRejected.value = reason
        }

        override fun onPairBlocked(retryInSeconds: Long) {
            pairBlockedSeconds.value = retryInSeconds
        }

        override fun onVideoTrack(track: org.webrtc.VideoTrack?) {
            videoReady.value = track != null
            if (track != null) armFirstFrameWatchdog() else disarmFirstFrameWatchdog()
        }

        override fun onInputChannelOpen(open: Boolean) {
            inputOpen.value = open
            if (open) {
                // Minta host mengirim stream hingga 120 FPS begitu channel input terbuka.
                runCatching { viewer?.sendInputPacket(XyHostInputCodec.videoFps(120)) }
            }
        }

        override fun onMediaConnected(connected: Boolean) {
            mediaConnected.value = connected
        }

        override fun onHostText(text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (json.optString("type")) {
                "virtualDisplay" -> {
                    hostNotice.value = when (json.optString("status")) {
                        "applied" -> "VD " + json.optInt("width") + "x" + json.optInt("height") +
                            "@" + json.optInt("hz") + "Hz"
                        "notInstalled" -> "VD: adapter belum terpasang di host (pasang via panel host)"
                        else -> "VD: " + json.optString("detail").ifBlank { "gagal" }
                    }
                }
                "meta" -> updateCaptureHint(json.optJSONObject("capture"))
            }
        }

        override fun onViewerFailure(error: Throwable) {
            failure.value = error.message ?: error.javaClass.simpleName
        }

        override fun onSignalingClosed(code: Int, reason: String) {
            if (videoReady.value && !directModeActive.value) finish()
        }
    }

    // ── Pengiriman Input Terpadu (Hub WebRTC maupun Engine Konsol UDP :4433) ──

    private fun dispatchMouseRel(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        val dc = directClient
        if (directModeActive.value && dc != null) {
            dc.inputBridge.sendMouseRel(dx, dy)
        } else {
            viewer?.sendInputPacket(XyHostInputCodec.mouseMoveRel(dx, dy))
        }
    }

    private fun dispatchMouseButton(button: XyHostMouseButton, down: Boolean) {
        val dc = directClient
        if (directModeActive.value && dc != null) {
            dc.inputBridge.sendMouseButton(button.code, down)
        } else {
            viewer?.sendInputPacket(XyHostInputCodec.mouseButton(button.code, down))
        }
    }

    private fun dispatchScroll(wheelDelta: Int) {
        if (wheelDelta == 0) return
        val dc = directClient
        if (directModeActive.value && dc != null) {
            dc.inputBridge.sendMouseWheel(wheelDelta)
        } else {
            viewer?.sendInputPacket(XyHostInputCodec.scroll(0, wheelDelta))
        }
    }

    private fun dispatchKeyVk(vk: Int, down: Boolean) {
        if (vk <= 0) return
        val dc = directClient
        if (directModeActive.value && dc != null) {
            dc.inputBridge.sendKeyVk(vk, down)
        } else {
            viewer?.sendInputPacket(XyHostInputCodec.key(vk, down))
        }
    }

    // ── Anti-black-screen helpers ──────────────────────────────────────────

    private fun armFirstFrameWatchdog() {
        firstFrameSeen = false
        noVideoSignal.value = false
        firstFrameWatchdog?.cancel()
        firstFrameWatchdog = scope.launch {
            delay(FIRST_FRAME_TIMEOUT_MS)
            if (!firstFrameSeen) noVideoSignal.value = true
        }
    }

    private fun disarmFirstFrameWatchdog() {
        firstFrameWatchdog?.cancel()
        firstFrameWatchdog = null
        firstFrameSeen = false
        noVideoSignal.value = false
    }

    private fun armPairingWatchdog() {
        pairingTimedOut.value = false
        pairingWatchdog?.cancel()
        pairingWatchdog = scope.launch {
            delay(PAIRING_TIMEOUT_MS)
            if (sessionState.value == XyHubSessionState.PAIRING) {
                pairingTimedOut.value = true
            }
        }
    }

    private fun disarmPairingWatchdog() {
        pairingWatchdog?.cancel()
        pairingWatchdog = null
        pairingTimedOut.value = false
    }

    private fun updateCaptureHint(capture: JSONObject?) {
        if (capture == null) return
        val frames = capture.optInt("framesCaptured", -1)
        val state = capture.optString("state")
        when {
            frames > 0 -> {
                hostCaptureHint.value = null
                firstFrameSeen = true
                noVideoSignal.value = false
            }
            state == "backend-error" -> hostCaptureHint.value = xyNow(
                "Host: capture gagal (" + capture.optString("backend").ifBlank { "?" } + ")",
                "Host: capture failed (" + capture.optString("backend").ifBlank { "?" } + ")",
            )
            state == "armed-no-frame" -> hostCaptureHint.value = xyNow(
                "Host: siap menangkap tapi 0 frame — coba pasang virtual display (tombol VD)",
                "Host: armed but 0 frames — try the virtual display (VD button)",
            )
            state == "idle" && videoReady.value -> hostCaptureHint.value = xyNow(
                "Host: capture belum aktif",
                "Host: capture not active yet",
            )
        }
        if (capture.optBoolean("blackFrames", false)) {
            hostCaptureHint.value = xyNow(
                "Host: frame hitam terdeteksi (desktop mungkin kosong/terkunci)",
                "Host: black frames detected (desktop may be empty/locked)",
            )
        }
    }

    fun attachSessionSink(renderer: VideoSink) {
        viewer?.setVideoSink(object : VideoSink {
            override fun onFrame(frame: VideoFrame) {
                if (!firstFrameSeen) {
                    firstFrameSeen = true
                    noVideoSignal.value = false
                    firstFrameWatchdog?.cancel()
                }
                renderer.onFrame(frame)
            }
        })
    }

    private fun restartSession() {
        if (directModeActive.value) {
            val ip = lastDirectIp
            if (ip.isNotBlank()) {
                startDirectConsoleSession(ip, lastDirectPort, lastDirectFps)
            } else {
                directClient?.close()
                directClient = null
                directModeActive.value = false
                videoReady.value = false
            }
            return
        }
        viewer?.close()
        viewer = null
        runCatching { eglBase?.release() }
        eglBase = null
        disarmFirstFrameWatchdog()
        disarmPairingWatchdog()
        hostCaptureHint.value = null
        sessionState.value = null
        stateDetail.value = null
        pairRejected.value = null
        pairBlockedSeconds.value = null
        videoReady.value = false
        inputOpen.value = false
        mediaConnected.value = false
        failure.value = null
        sessionEnded.value = false
        started.value = false
        val hostId = lastHostId
        val pin = lastPin
        if (hostId.length == 9 && pin.length == 6) {
            startSession(hostId, pin)
        } else {
            sessionEnded.value = true
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (started.value && !sessionEnded.value) {
            val src = event.source
            val devSources = event.device?.sources ?: 0
            val isMouse = (src and android.view.InputDevice.SOURCE_MOUSE) == android.view.InputDevice.SOURCE_MOUSE ||
                (src and android.view.InputDevice.SOURCE_TOUCHPAD) == android.view.InputDevice.SOURCE_TOUCHPAD ||
                ((devSources and android.view.InputDevice.SOURCE_MOUSE) == android.view.InputDevice.SOURCE_MOUSE &&
                    event.device?.keyboardType != android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC)
            if (isMouse && (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_FORWARD)) {
                val down = event.action == KeyEvent.ACTION_DOWN
                if (event.keyCode == KeyEvent.KEYCODE_BACK && (down || event.action == KeyEvent.ACTION_UP)) {
                    dispatchMouseButton(XyHostMouseButton.RIGHT, down)
                }
                return true
            }
            val isPhysKbd = event.deviceId > 0 && event.device?.isVirtual == false &&
                event.device?.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC
            if (isPhysKbd && (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK)) {
                val down = event.action == KeyEvent.ACTION_DOWN
                if (down || event.action == KeyEvent.ACTION_UP) {
                    dispatchKeyVk(VK_ESCAPE, down)
                }
                return true
            }
        }
        if (!inputOpen.value) return super.dispatchKeyEvent(event)
        val vk = windowsVirtualKey(event.keyCode)
        if (vk == 0) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                dispatchKeyVk(vk, true)
            }

            KeyEvent.ACTION_UP -> dispatchKeyVk(vk, false)
        }
        return true
    }

    private fun windowsVirtualKey(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_A -> VK_A
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> VK_ENTER
        KeyEvent.KEYCODE_DEL -> VK_BACK
        KeyEvent.KEYCODE_SPACE -> VK_SPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> VK_DELETE
        KeyEvent.KEYCODE_ESCAPE -> VK_ESCAPE
        KeyEvent.KEYCODE_TAB -> VK_TAB
        KeyEvent.KEYCODE_DPAD_LEFT -> VK_LEFT
        KeyEvent.KEYCODE_DPAD_UP -> VK_UP
        KeyEvent.KEYCODE_DPAD_RIGHT -> VK_RIGHT
        KeyEvent.KEYCODE_DPAD_DOWN -> VK_DOWN
        else -> when {
            keyCode in KeyEvent.KEYCODE_B..KeyEvent.KEYCODE_Z ->
                VK_A + (keyCode - KeyEvent.KEYCODE_A)

            keyCode == KeyEvent.KEYCODE_0 -> VK_0
            keyCode in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 ->
                VK_1 + (keyCode - KeyEvent.KEYCODE_1)

            else -> 0
        }
    }

    @Composable
    private fun PcStreamScreen() {
        if (videoReady.value) {
            androidx.activity.compose.BackHandler {
                runCatching {
                    startActivity(
                        android.content.Intent(this@PcStreamSessionActivity, XyDeskHomeActivity::class.java)
                            .putExtra(EXTRA_SKIP_AUTO_RESUME, true)
                            .addFlags(
                                android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                    android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP,
                            ),
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                VideoStage()
            }
        } else {
            PairingStage()
        }
    }

    // ── Layar pairing & pemilihan engine ────────────────────────────────────

    @Composable
    private fun PairingStage() {
        var modeTab by remember { mutableIntStateOf(0) } // 0 = P2P Direct / Host PC, 1 = Hub 9-Digit + PIN
        var p2pInput by remember { mutableStateOf(lastDirectIp) }
        var p2pFpsIdx by remember { mutableIntStateOf(2) } // 0=60, 1=90, 2=120
        var hostId by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        val scroll = rememberScrollState()
        val state = sessionState.value
        val busy = resolvingP2P.value || ((state == XyHubSessionState.AUTHENTICATING ||
            state == XyHubSessionState.CONNECTING ||
            state == XyHubSessionState.PAIRING) && !pairingTimedOut.value)

        LaunchedEffect(Unit) {
            scanLanConsoleHosts()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .imePadding(),
        ) {
            Text(
                xy("PC Stream P2P & Game Console", "P2P PC Stream & Game Console"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                xy(
                    "Streaming konsol fisik Windows (SM_REMOTESESSION=0, anti-delay 60–120 FPS, RawInput 360° & audio/mic dua arah) via P2P Langsung atau Hub Pairing.",
                    "Direct Windows physical console streaming (SM_REMOTESESSION=0, zero-lag 60–120 FPS, 360° RawInput & 2-way audio/mic) via P2P Direct or Hub Pairing.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            XySegmented(
                options = listOf(
                    xy("P2P Direct / Host PC", "P2P Direct / Host PC"),
                    xy("Hub 9-Digit + PIN", "9-Digit Hub + PIN"),
                ),
                selectedIndex = modeTab,
                onSelect = {
                    modeTab = it
                    failure.value = null
                },
            )
            Spacer(Modifier.height(16.dp))
            if (modeTab == 0) {
                XyField(
                    value = p2pInput,
                    onValueChange = { p2pInput = it.trim() },
                    label = xy(
                        "ID Global (XY…) / ID PC 10-Digit / IP Tailscale / LAN",
                        "Global ID (XY…) / 10-Digit PC ID / Tailscale / LAN IP",
                    ),
                    hint = xy(
                        "Contoh: XYA1B2C3D4, 323-223-5826, 100.84.12.9, atau 192.168.1.50",
                        "Example: XYA1B2C3D4, 323-223-5826, 100.84.12.9, or 192.168.1.50",
                    ),
                    keyboardType = KeyboardType.Ascii,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    xy("Target Frame Rate (Zero-Lag Stream)", "Target Frame Rate (Zero-Lag Stream)"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                XySegmented(
                    options = listOf("60 FPS", "90 FPS", "120 FPS"),
                    selectedIndex = p2pFpsIdx,
                    onSelect = { p2pFpsIdx = it },
                )
                Spacer(Modifier.height(14.dp))
                val targetFps = when (p2pFpsIdx) {
                    0 -> 60
                    1 -> 90
                    else -> 120
                }
                XyPillButton(
                    text = if (resolvingP2P.value) {
                        xy("Menyambungkan Jalur P2P…", "Connecting P2P Path…")
                    } else {
                        xy("Sambungkan P2P Sekarang ($targetFps FPS)", "Connect P2P Now ($targetFps FPS)")
                    },
                    onClick = {
                        connectP2PTarget(p2pInput, defaultPort = 4433, targetFps = targetFps)
                    },
                    enabled = p2pInput.isNotBlank() && !busy,
                    icon = XyIcons.Bolt,
                    modifier = Modifier.fillMaxWidth(),
                )
                failure.value?.let { message ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        xy("Host P2P Terdeteksi di Jaringan / Tailscale", "P2P Hosts Detected on LAN / Tailscale"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(
                        onClick = { scanLanConsoleHosts() },
                        enabled = !scanningLan.value,
                    ) {
                        Text(
                            if (scanningLan.value) xy("Memindai…", "Scanning…")
                            else xy("Pindai Ulang", "Rescan"),
                        )
                    }
                }
                val hosts = discoveredHosts.value
                if (hosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF141821), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF252B3B), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            if (scanningLan.value) {
                                xy("Mencari XyDeskRemoteHost.exe di UDP :4433…", "Searching for XyDeskRemoteHost.exe on UDP :4433…")
                            } else {
                                xy(
                                    "Belum ada host terdeteksi via broadcast lokal. Jika beda jaringan/Wi-Fi, masukkan ID Global (XY…) atau IP Tailscale (100.x.y.z) di atas.",
                                    "No host detected via local broadcast yet. If on a different network, enter the Global ID (XY…) or Tailscale IP (100.x.y.z) above.",
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        hosts.forEach { h ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF141821))
                                    .border(1.dp, Color(0xFF2A3246), RoundedCornerShape(12.dp))
                                    .clickable {
                                        p2pInput = h.ip
                                        startDirectConsoleSession(h.ip, h.quicPort, targetFps)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        h.hostname + (if (h.pcId.isNotBlank()) " · ${h.pcId}" else ""),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(
                                        "${h.ip}:${h.quicPort} · ${h.gpu} · P2P Zero-Lag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF3DD68C),
                                    )
                                }
                                XyPillButton(
                                    text = xy("Konek", "Connect"),
                                    onClick = {
                                        p2pInput = h.ip
                                        startDirectConsoleSession(h.ip, h.quicPort, targetFps)
                                    },
                                    compact = true,
                                )
                            }
                        }
                    }
                }
            } else {
                XyField(
                    value = hostId,
                    onValueChange = { hostId = it.filter(Char::isDigit).take(9) },
                    label = xy("ID Host (9 Digit)", "Host ID (9 Digits)"),
                    hint = xy("Contoh: 123456789", "Example: 123456789"),
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(10.dp))
                XyField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                    label = xy("PIN Pairing (6 Digit)", "Pairing PIN (6 Digits)"),
                    hint = xy("PIN 6 digit dari host XyDesk", "6-digit PIN from the XyDesk host"),
                    isPassword = true,
                    keyboardType = KeyboardType.NumberPassword,
                )
                Spacer(Modifier.height(18.dp))
                XyPillButton(
                    text = xy(
                        if (pairRejected.value != null || pairBlockedSeconds.value != null) "Coba Lagi" else "Sambungkan",
                        if (pairRejected.value != null || pairBlockedSeconds.value != null) "Try Again" else "Connect",
                    ),
                    onClick = {
                        failure.value = null
                        pairRejected.value = null
                        pairBlockedSeconds.value = null
                        pairingTimedOut.value = false
                        startSession(hostId, pin)
                    },
                    enabled = hostId.length == 9 && pin.length == 6 && !busy,
                    icon = XyIcons.Monitor,
                    modifier = Modifier.fillMaxWidth(),
                )
                state?.let { current ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stateLabel(current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                pairRejected.value?.let { reason ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        xy("Pairing ditolak: ", "Pairing rejected: ") + (reason.ifBlank { "-" }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (pairingTimedOut.value) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        xy(
                            "Host tidak merespons pairing. Pastikan aplikasi host XyDesk aktif di PC, lalu coba lagi.",
                            "Host is not responding to pairing. Make sure the XyDesk host app is running on the PC, then try again.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                pairBlockedSeconds.value?.let { seconds ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        xy(
                            "Terlalu banyak percobaan PIN salah. Tunggu ±${seconds / 60} menit ${seconds % 60} detik.",
                            "Too many wrong PIN attempts. Wait about ${seconds / 60}m ${seconds % 60}s.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                failure.value?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        xy("Gagal: ", "Failed: ") + message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (sessionEnded.value) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        xy(
                            "Sesi sudah berakhir — tutup dan buka lagi.",
                            "Session already ended — close and reopen.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            XyPillButton(
                text = xy("Kembali", "Back"),
                onClick = { finish() },
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    private fun stateLabel(state: XyHubSessionState): String = when (state) {
        XyHubSessionState.AUTHENTICATING -> xy("Masuk sebagai tamu…", "Signing in as guest…")
        XyHubSessionState.CONNECTING -> xy("Menyambung ke hub signaling…", "Connecting to signaling hub…")
        XyHubSessionState.REGISTERED -> xy("Terdaftar di hub.", "Registered at hub.")
        XyHubSessionState.PAIRING -> xy("Meminta pairing ke host…", "Requesting pairing from host…")
        XyHubSessionState.PAIRED -> xy("Pairing diterima — menunggu media host…", "Pairing accepted — waiting for host media…")
        XyHubSessionState.CLOSED -> xy("Sesi ditutup.", "Session closed.")
        XyHubSessionState.FAILED -> xy("Sesi gagal.", "Session failed.")
    }

    // ── Layar video + overlay input + Mode Game FPS ─────────────────────────

    @Composable
    private fun VideoStage() {
        val activity = this
        var showTextDialog by remember { mutableStateOf(false) }
        var padOpen by remember { mutableStateOf(false) }
        var showVdDialog by remember { mutableStateOf(false) }
        val isFps = fpsGameMode.value
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            if (directModeActive.value) {
                val seq = directFrameSeq.intValue
                val bmp = directFrame.value
                if (bmp != null && !bmp.isRecycled && seq >= 0) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                val egl = eglBase
                if (egl != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            SurfaceViewRenderer(context).apply {
                                init(egl.eglBaseContext, null)
                                setMirror(false)
                                setEnableHardwareScaler(true)
                                activity.attachSessionSink(this)
                            }
                        },
                        onRelease = { renderer ->
                            activity.viewer?.setVideoSink(null)
                            runCatching { renderer.release() }
                        },
                    )
                }
            }

            // Area sentuh utama: geser gerak kamera/kursor (1.75x saat Mode Game FPS)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isFps) {
                        detectTapGestures(
                            onTap = { clickButton(XyHostMouseButton.LEFT) },
                            onDoubleTap = {
                                clickButton(XyHostMouseButton.LEFT)
                                clickButton(XyHostMouseButton.LEFT)
                            },
                            onLongPress = { clickButton(XyHostMouseButton.RIGHT) },
                        )
                    }
                    .pointerInput(isFps) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val mult = if (isFps) 1.75f else 1.0f
                            val dx = (drag.x * mult).roundToInt()
                            val dy = (drag.y * mult).roundToInt()
                            if (dx != 0 || dy != 0) {
                                dispatchMouseRel(dx, dy)
                            }
                        }
                    },
            )

            // Crosshair tengah presisi saat Mode Game FPS aktif
            if (isFps) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(12.dp).height(1.5.dp).background(Color(0xCC3DD68C)))
                    Box(Modifier.width(1.5.dp).height(12.dp).background(Color(0xCC3DD68C)))
                }

                // Kluster WASD + Sprint/Crouch di kiri bawah untuk Game FPS
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 54.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VkHoldButton("SHIFT", VK_SHIFT)
                        VkHoldButton("W", VK_W)
                        VkHoldButton("R", VK_R)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VkHoldButton("A", VK_A)
                        VkHoldButton("S", VK_S)
                        VkHoldButton("D", VK_D)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VkHoldButton("CTRL", VK_CONTROL)
                        VkHoldButton("SPACE", VK_SPACE)
                        VkHoldButton("ESC", VK_ESCAPE)
                    }
                }

                // Tombol Tembak (LMB) & Scope/ADS (RMB) di kanan bawah
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 58.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HoldButton(
                        label = xy("SCOPE (RMB)", "SCOPE (RMB)"),
                        button = XyHostMouseButton.RIGHT,
                    )
                    HoldButton(
                        label = xy("TEMBAK (LMB)", "FIRE (LMB)"),
                        button = XyHostMouseButton.LEFT,
                    )
                }
            }

            // Strip scroll di kanan
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxSize()
                    .align(Alignment.CenterEnd)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, amount ->
                            change.consume()
                            val steps = (amount / 24f).toInt()
                            if (steps != 0) {
                                dispatchScroll(steps * SCROLL_UNIT)
                            }
                        }
                    },
            )

            // Bar status + tombol bawah.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x88000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        if (directModeActive.value) {
                            append(
                                if (mediaConnected.value) {
                                    if (directNonRdpVerified.value) "CONSOLE SM_REMOTESESSION=0"
                                    else "UDP :4433 LIVE"
                                } else {
                                    directStatus.value ?: xy("MENUNGGU HOST :4433…", "WAITING FOR HOST :4433…")
                                },
                            )
                            if (directHostFps.intValue > 0) append(" · ${directHostFps.intValue} FPS")
                        } else {
                            append(
                                when {
                                    noVideoSignal.value -> xy("TANPA GAMBAR", "NO VIDEO")
                                    mediaConnected.value -> xy("TERHUBUNG", "CONNECTED")
                                    videoReady.value -> xy("MENGHUBUNGKAN…", "CONNECTING…")
                                    else -> xy("MENUNGGU…", "WAITING…")
                                },
                            )
                            if (inputOpen.value) append(" · " + xy("INPUT 120Hz", "120Hz INPUT"))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    var voiceOn by remember { mutableStateOf(false) }
                    XyPillButton(
                        text = if (voiceOn) "MIC ON" else "MIC OFF",
                        onClick = {
                            val next = !voiceOn
                            if (next && ContextCompat.checkSelfPermission(
                                    this@PcStreamSessionActivity,
                                    android.Manifest.permission.RECORD_AUDIO,
                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                ActivityCompat.requestPermissions(
                                    this@PcStreamSessionActivity,
                                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                                    9402,
                                )
                            }
                            voiceOn = next
                            directClient?.setVoiceMicEnabled(next)
                        },
                        primary = voiceOn,
                        compact = true,
                    )
                    XyPillButton(
                        text = if (isFps) "FPS 360° ON" else "FPS 360°",
                        onClick = { fpsGameMode.value = !isFps },
                        primary = isFps,
                        compact = true,
                    )
                    HoldButton(
                        label = xy("KIRI", "LEFT"),
                        button = XyHostMouseButton.LEFT,
                    )
                    HoldButton(
                        label = xy("KANAN", "RIGHT"),
                        button = XyHostMouseButton.RIGHT,
                    )
                    if (!directModeActive.value) {
                        XyPillButton(
                            text = xy("PAD", "PAD"),
                            onClick = { padOpen = !padOpen },
                            primary = false,
                            compact = true,
                        )
                        XyPillButton(
                            text = "VD",
                            onClick = { showVdDialog = true },
                            primary = false,
                            compact = true,
                        )
                    }
                    XyPillButton(
                        text = xy("TEKS", "TEXT"),
                        onClick = { showTextDialog = true },
                        primary = false,
                        compact = true,
                    )
                    XyPillButton(
                        text = xy("BERANDA", "HOME"),
                        onClick = {
                            runCatching {
                                startActivity(
                                    android.content.Intent(this@PcStreamSessionActivity, XyDeskHomeActivity::class.java)
                                        .putExtra(EXTRA_SKIP_AUTO_RESUME, true)
                                        .addFlags(
                                            android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                                android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP,
                                        ),
                                )
                            }
                        },
                        primary = false,
                        compact = true,
                    )
                    XyPillButton(
                        text = xy("PUTUS", "DISCONNECT"),
                        onClick = {
                            runCatching { directClient?.close() }
                            directClient = null
                            runCatching { viewer?.stop() }
                            finish()
                        },
                        primary = false,
                        compact = true,
                    )
                }
            }

            if (noVideoSignal.value) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xCC000000), RoundedCornerShape(14.dp))
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        xy("Tidak ada gambar dari host", "No video from host"),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (directModeActive.value) {
                            xy(
                                "Pastikan XyDeskRemoteHost-Standalone.exe berjalan di sesi konsol PC dan port UDP 4433 terbuka di jaringan/Tailscale.",
                                "Make sure XyDeskRemoteHost-Standalone.exe is running on the PC console session and UDP port 4433 is reachable on LAN/Tailscale.",
                            )
                        } else {
                            hostCaptureHint.value ?: xy(
                                "Koneksi tersambung tetapi frame tidak pernah sampai. Biasanya encoder host gagal atau PC headless tanpa virtual display.",
                                "Connection is up but frames never arrive. Usually the host encoder failed, or a headless PC without virtual display.",
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFBDBDBD),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton(
                            text = xy("SAMBUNG ULANG", "RECONNECT"),
                            onClick = { restartSession() },
                            primary = false,
                            compact = true,
                        )
                        XyPillButton(
                            text = xy("KEMBALI", "BACK"),
                            onClick = {
                                directClient?.close()
                                directClient = null
                                directModeActive.value = false
                                videoReady.value = false
                                disarmFirstFrameWatchdog()
                            },
                            primary = false,
                            compact = true,
                        )
                    }
                }
            }

            hostNotice.value?.let { notice ->
                LaunchedEffect(notice) {
                    delay(6000)
                    if (hostNotice.value == notice) hostNotice.value = null
                }
                Text(
                    text = notice,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .background(Color(0x99000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            if (showVdDialog) {
                VirtualDisplayDialog(
                    onPick = { w, h, hz ->
                        showVdDialog = false
                        viewer?.sendInputPacket(XyHostInputCodec.virtualDisplay(w, h, hz, 1))
                    },
                    onDismiss = { showVdDialog = false },
                )
            }

            if (padOpen) {
                PcStreamPadOverlay(
                    prefs = remember { SessionPrefs(activity) },
                    deviceId = activeHostId,
                    onSend = { state ->
                        if (inputOpen.value) {
                            viewer?.sendInputPacket(XyHostInputCodec.gamepad(state))
                        }
                    },
                    onClose = { padOpen = false },
                )
            }

            if (showTextDialog) {
                TextInputDialog(
                    onSend = { value ->
                        if (value.isNotEmpty()) {
                            if (directModeActive.value) {
                                value.forEach { ch ->
                                    val code = ch.uppercaseChar().code
                                    dispatchKeyVk(code, true)
                                    dispatchKeyVk(code, false)
                                }
                            } else {
                                viewer?.sendInputPacket(XyHostInputCodec.text(value))
                            }
                        }
                        showTextDialog = false
                    },
                    onCancel = { showTextDialog = false },
                )
            }
        }
    }

    /** Tombol tekan-tahan untuk tombol mouse (LMB Tembak / RMB Scope). */
    @Composable
    private fun HoldButton(label: String, button: XyHostMouseButton) {
        Box(
            modifier = Modifier
                .background(Color(0x44FFFFFF), RoundedCornerShape(16.dp))
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(16.dp))
                .pointerInput(button) {
                    detectTapGestures(
                        onPress = {
                            dispatchMouseButton(button, true)
                            tryAwaitRelease()
                            dispatchMouseButton(button, false)
                        },
                    )
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }

    /** Tombol tekan-tahan Virtual-Key untuk WASD / Space / Shift / Ctrl / Reload di Mode Game FPS. */
    @Composable
    private fun VkHoldButton(label: String, vk: Int) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 40.dp)
                .clip(CircleShape)
                .background(Color(0x40000000))
                .border(1.dp, Color(0x777C9EFF), CircleShape)
                .pointerInput(vk) {
                    detectTapGestures(
                        onPress = {
                            dispatchKeyVk(vk, true)
                            tryAwaitRelease()
                            dispatchKeyVk(vk, false)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White,
            )
        }
    }

    @Composable
    private fun TextInputDialog(onSend: (String) -> Unit, onCancel: () -> Unit) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(xy("Kirim Teks ke Host", "Send Text to Host")) },
            text = {
                XyField(
                    value = value,
                    onValueChange = { value = it.take(512) },
                    label = xy("Teks", "Text"),
                    keyboardType = KeyboardType.Text,
                )
            },
            confirmButton = {
                TextButton(onClick = { onSend(value) }) { Text(xy("Kirim", "Send")) }
            },
            dismissButton = {
                TextButton(onClick = onCancel) { Text(xy("Batal", "Cancel")) }
            },
        )
    }

    @Composable
    private fun VirtualDisplayDialog(
        onPick: (width: Int, height: Int, hz: Int) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val presets = remember {
            listOf(
                Triple(1280, 720, 60),
                Triple(1920, 1080, 60),
                Triple(1920, 1080, 120),
                Triple(2560, 1440, 60),
                Triple(2560, 1440, 144),
                Triple(3840, 2160, 60),
            )
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(xy("Virtual Display", "Virtual Display")) },
            text = {
                Column {
                    presets.forEach { (w, h, hz) ->
                        TextButton(onClick = { onPick(w, h, hz) }) {
                            Text("${w}×${h} · ${hz}Hz")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(xy("Tutup", "Close")) }
            },
        )
    }

    private fun clickButton(button: XyHostMouseButton) {
        dispatchMouseButton(button, true)
        dispatchMouseButton(button, false)
    }

    companion object {
        private const val SCROLL_UNIT = 120
        private const val FIRST_FRAME_TIMEOUT_MS = 15_000L
        private const val PAIRING_TIMEOUT_MS = 25_000L

        private const val VK_BACK = 0x08
        private const val VK_TAB = 0x09
        private const val VK_ENTER = 0x0D
        private const val VK_SHIFT = 0x10
        private const val VK_CONTROL = 0x11
        private const val VK_ESCAPE = 0x1B
        private const val VK_SPACE = 0x20
        private const val VK_LEFT = 0x25
        private const val VK_UP = 0x26
        private const val VK_RIGHT = 0x27
        private const val VK_DOWN = 0x28
        private const val VK_0 = 0x30
        private const val VK_1 = 0x31
        private const val VK_A = 0x41
        private const val VK_D = 0x44
        private const val VK_R = 0x52
        private const val VK_S = 0x53
        private const val VK_W = 0x57
        private const val VK_DELETE = 0x2E

        const val EXTRA_DIRECT_HOST = "xydesk.pcstream.direct_host"
        const val EXTRA_DIRECT_PORT = "xydesk.pcstream.direct_port"
        const val EXTRA_DIRECT_FPS = "xydesk.pcstream.direct_fps"

        fun intent(
            context: Context,
            directHost: String? = null,
            directPort: Int = 4433,
            directFps: Int = 120,
        ): Intent = Intent(context, PcStreamSessionActivity::class.java).apply {
            if (!directHost.isNullOrBlank()) {
                putExtra(EXTRA_DIRECT_HOST, directHost.trim())
                putExtra(EXTRA_DIRECT_PORT, directPort)
                putExtra(EXTRA_DIRECT_FPS, directFps)
            }
        }
    }
}
