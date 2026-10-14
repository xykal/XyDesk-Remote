package id.xydesk.remote.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import id.xydesk.remote.pcstream.XyHostInputCodec
import id.xydesk.remote.pcstream.XyHostMouseButton
import id.xydesk.remote.pcstream.XyHubSessionState
import id.xydesk.remote.pcstream.XyHubStreamViewer
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.theme.XyDeskTheme
import id.xydesk.remote.ui.theme.XyThemeState
import id.xydesk.remote.ui.theme.xyDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

/**
 * Sesi PC-stream produksi (host XyDesk via hub `signal.xydesk.my.id`):
 * layar pairing (ID host 9 digit + PIN 6 digit) → video WebRTC + overlay
 * input dasar (touchpad mouse, strip scroll, keyboard teks, keyboard fisik).
 *
 * Overlay: touchpad mouse, strip scroll, keyboard teks, keyboard fisik, dan
 * virtual pad on-screen (opcode 0x0E → ViGEmBus di host; lihat
 * [PcStreamPadOverlay]). Seluruh wiring media ada di [XyHubStreamViewer];
 * activity hanya memegang lifecycle + state Compose.
 */
class PcStreamSessionActivity : ComponentActivity() {
    private var viewer: XyHubStreamViewer? = null
    private var eglBase: EglBase? = null

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

    /** Sesi jalan sudah tidak bisa diulang lagi (gagal/ditutup) — teks dirender di Compose. */
    private val sessionEnded = mutableStateOf(false)

    /** Notifikasi sekali-lihat dari host (mis. status Virtual Display). */
    private val hostNotice = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        setContent {
            // Ikut setelan tema app seperti layar sesi RDP (XyDeskSessionActivity).
            XyThemeState.init(AppPrefs(this))
            XyDeskTheme(dark = xyDark()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    PcStreamScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewer?.close()
        viewer = null
        runCatching { eglBase?.release() }
        eglBase = null
    }

    private fun startSession(hostId: String, pin: String) {
        if (started.value) {
            // Sesi jalan tapi pairing ditolak (PIN salah / host offline) → ulangi
            // tanpa onboarding ulang; kalau sesi benar-benar mati, tutup layar.
            val current = sessionState.value
            val retryable = current == XyHubSessionState.REGISTERED || current == XyHubSessionState.PAIRING
            if (retryable) {
                runCatching { viewer?.retryPair(pin, deviceName()) }
                    .onFailure { failure.value = it.message }
            } else {
                sessionEnded.value = true
            }
            return
        }
        started.value = true
        activeHostId = hostId
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
        }

        override fun onInputChannelOpen(open: Boolean) {
            inputOpen.value = open
        }

        override fun onMediaConnected(connected: Boolean) {
            mediaConnected.value = connected
        }

        override fun onHostText(text: String) {
            // Balasan status host berupa teks JSON. Saat ini hanya
            // `virtualDisplay` yang ditangani; meta/cursor menyusul revisi
            // UI pemilik. Teks non-JSON diabaikan diam-diam.
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            if (json.optString("type") != "virtualDisplay") return
            hostNotice.value = when (json.optString("status")) {
                "applied" -> "VD " + json.optInt("width") + "×" + json.optInt("height") +
                    "@" + json.optInt("hz") + "Hz ✓"
                "notInstalled" -> "VD: adapter belum terpasang di host (pasang via panel host)"
                else -> "VD: " + json.optString("detail").ifBlank { "gagal" }
            }
        }

        override fun onViewerFailure(error: Throwable) {
            failure.value = error.message ?: error.javaClass.simpleName
        }

        override fun onSignalingClosed(code: Int, reason: String) {
            if (videoReady.value) finish()
        }
    }

    /** Keyboard fisik → Windows Virtual-Key (subset penuh teks pakai dialog keyboard). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val streamViewer = viewer ?: return super.dispatchKeyEvent(event)
        if (!inputOpen.value) return super.dispatchKeyEvent(event)
        val vk = windowsVirtualKey(event.keyCode)
        if (vk == 0) return super.dispatchKeyEvent(event)
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                streamViewer.sendInputPacket(XyHostInputCodec.key(vk, true))
            }

            KeyEvent.ACTION_UP -> streamViewer.sendInputPacket(XyHostInputCodec.key(vk, false))
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
        if (videoReady.value) VideoStage() else PairingStage()
    }

    // ── Layar pairing ───────────────────────────────────────────────────────

    @Composable
    private fun PairingStage() {
        var hostId by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        val scroll = rememberScrollState()
        val state = sessionState.value
        val busy = state == XyHubSessionState.AUTHENTICATING ||
            state == XyHubSessionState.CONNECTING ||
            state == XyHubSessionState.PAIRING

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .imePadding(),
        ) {
            Text(
                xy("PC Stream — Host XyDesk", "PC Stream — XyDesk Host"),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                xy(
                    "Tancap ke host XyDesk produksi lewat hub signaling. ID host 9 digit + PIN 6 digit dari aplikasi host.",
                    "Plug into a production XyDesk host via the signaling hub. 9-digit host ID + 6-digit PIN from the host app.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
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

    // ── Layar video + overlay input ─────────────────────────────────────────

    @Composable
    private fun VideoStage() {
        val activity = this
        var showTextDialog by remember { mutableStateOf(false) }
        var padOpen by remember { mutableStateOf(false) }
        var showVdDialog by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val egl = eglBase
            if (egl != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        SurfaceViewRenderer(context).apply {
                            init(egl.eglBaseContext, null)
                            setMirror(false)
                            setEnableHardwareScaler(true)
                            activity.viewer?.setVideoSink(this)
                        }
                    },
                    onRelease = { renderer ->
                        activity.viewer?.setVideoSink(null)
                        runCatching { renderer.release() }
                    },
                )
            }

            // Area sentuh = touchpad mouse: geser gerak kursor, tap klik kiri,
            // double-tap klik ganda, tekan-tahan klik kanan.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { clickButton(XyHostMouseButton.LEFT) },
                            onDoubleTap = {
                                clickButton(XyHostMouseButton.LEFT)
                                clickButton(XyHostMouseButton.LEFT)
                            },
                            onLongPress = { clickButton(XyHostMouseButton.RIGHT) },
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val dx = drag.x.toInt()
                            val dy = drag.y.toInt()
                            if (dx != 0 || dy != 0) {
                                viewer?.sendInputPacket(XyHostInputCodec.mouseMoveRel(dx, dy))
                            }
                        }
                    },
            )

            // Strip scroll di kanan (ala touchpad laptop).
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxSize()
                    .align(Alignment.CenterEnd)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, amount ->
                            change.consume()
                            val steps = (amount / 24f).toInt()
                            if (steps != 0) {
                                viewer?.sendInputPacket(XyHostInputCodec.scroll(0, steps * SCROLL_UNIT))
                            }
                        }
                    },
            )

            // Bar status + tombol bawah.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildString {
                        append(
                            when {
                                mediaConnected.value -> xy("TERHUBUNG", "CONNECTED")
                                videoReady.value -> xy("MENGHUBUNGKAN…", "CONNECTING…")
                                else -> xy("MENUNGGU…", "WAITING…")
                            },
                        )
                        if (inputOpen.value) append(" · " + xy("INPUT AKTIF", "INPUT LIVE"))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoldButton(
                        label = xy("KIRI", "LEFT"),
                        button = XyHostMouseButton.LEFT,
                    )
                    HoldButton(
                        label = xy("KANAN", "RIGHT"),
                        button = XyHostMouseButton.RIGHT,
                    )
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
                    XyPillButton(
                        text = xy("TEKS", "TEXT"),
                        onClick = { showTextDialog = true },
                        primary = false,
                        compact = true,
                    )
                    XyPillButton(
                        text = xy("TUTUP", "EXIT"),
                        onClick = {
                            viewer?.stop()
                            finish()
                        },
                        primary = false,
                        compact = true,
                    )
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
                            viewer?.sendInputPacket(XyHostInputCodec.text(value))
                        }
                        showTextDialog = false
                    },
                    onCancel = { showTextDialog = false },
                )
            }
        }
    }

    /** Tombol tekan-tahan (drag = tahan klik) untuk tombol mouse. */
    @Composable
    private fun HoldButton(label: String, button: XyHostMouseButton) {
        Box(
            modifier = Modifier
                .background(Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                .pointerInput(button) {
                    detectTapGestures(
                        onPress = {
                            viewer?.sendInputPacket(XyHostInputCodec.mouseButton(button.code, true))
                            tryAwaitRelease()
                            viewer?.sendInputPacket(XyHostInputCodec.mouseButton(button.code, false))
                        },
                    )
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
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

    /**
     * Pilih mode Virtual Display Adapter host (opcode `0x10`) — untuk mesin
     * headless (cloud PC / VPS) yang tidak punya monitor fisik. Preset
     * sengaja sedikit dan mengikuti matriks resmi host; status balasan
     * host muncul sebagai notice di atas layar. Driver tidak dipasang dari
     * sesi remote — bila belum terpasang, host menjawab `notInstalled`.
     */
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
        viewer?.sendInputPacket(XyHostInputCodec.mouseButton(button.code, true))
        viewer?.sendInputPacket(XyHostInputCodec.mouseButton(button.code, false))
    }

    companion object {
        /** Satu langkah roda = 1 unit WHEEL_DELTA (120) di codec. */
        private const val SCROLL_UNIT = 120

        // Subset Windows Virtual-Key yang dipetakan dari keyCode Android.
        private const val VK_BACK = 0x08
        private const val VK_TAB = 0x09
        private const val VK_ENTER = 0x0D
        private const val VK_ESCAPE = 0x1B
        private const val VK_SPACE = 0x20
        private const val VK_LEFT = 0x25
        private const val VK_UP = 0x26
        private const val VK_RIGHT = 0x27
        private const val VK_DOWN = 0x28
        private const val VK_0 = 0x30
        private const val VK_1 = 0x31
        private const val VK_A = 0x41
        private const val VK_DELETE = 0x2E

        fun intent(context: Context): Intent = Intent(context, PcStreamSessionActivity::class.java)
    }
}
