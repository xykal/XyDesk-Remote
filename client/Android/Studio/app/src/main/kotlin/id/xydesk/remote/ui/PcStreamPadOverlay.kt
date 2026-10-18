package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.pcstream.XyGamepadState
import id.xydesk.remote.pcstream.XyPadButton
import id.xydesk.remote.pcstream.XyVirtualPadTracker
import id.xydesk.remote.ui.input.XyPadCluster
import id.xydesk.remote.ui.input.XyPadLayout
import id.xydesk.remote.ui.input.XyPadPos
import kotlin.math.roundToInt

/**
 * Gamepad virtual di layar untuk sesi PC Stream.
 *
 * Ini bagian wiring terakhir issue #12 tahap 3c: kontrol on-screen (stik,
 * D-pad, ABXY, bumper/trigger, start/back) → [XyVirtualPadTracker] →
 * `XyHostInputCodec.gamepad` (opcode `0x0E`, laporan XUSB 12 byte) →
 * data channel `input` → ViGEmBus di host.
 *
 * Konvensi arah: sumbu Y laporan XUSB **positif = ke atas** (stik didorong
 * maju), sedangkan koordinat layar Android positif = ke bawah — karena itu
 * sumbu Y di-flip di [PadStick] sebelum masuk tracker, dan tombol D-pad
 * memetakan arah layar langsung ke bit XInput.
 *
 * Tata letak: posisi tiap klaster (pecahan 0..1 ukuran layar) disimpan per
 * host lewat [SessionPrefs], matematikanya di [XyPadLayout]. Mode "atur"
 * (tombol ☰) mematikan input sesaat supaya tiap klaster bisa digeser dengan
 * drag; posisi tersimpan saat mode diakhiri.
 *
 * Sengaja minimal fungsional + monokrom: pemilik akan merevisi tampilannya
 * setelah mencoba (jangan over-polish dulu).
 */
@Composable
internal fun PcStreamPadOverlay(
    prefs: SessionPrefs,
    deviceId: String,
    onSend: (XyGamepadState) -> Unit,
    onClose: () -> Unit,
) {
    val tracker = remember { XyVirtualPadTracker() }
    var layout by remember { mutableStateOf(XyPadLayout.resolve(prefs.virtualPadLayout(deviceId))) }
    var editMode by remember { mutableStateOf(false) }

    fun push() {
        // Snapshot penuh tiap perubahan — host tinggal menerapkan frame
        // terakhir, aman terhadap paket hilang (lihat XyGamepadPacketV1).
        if (tracker.consumeDirty()) onSend(tracker.state())
    }

    /** Lepas semua tombol/sumbu supaya host tidak "menahan" input basi. */
    fun releaseAll() {
        tracker.reset()
        onSend(tracker.state())
    }
    DisposableEffect(Unit) {
        onDispose {
            prefs.setVirtualPadLayout(deviceId, layout)
            releaseAll()
        }
    }

    val padAlpha = remember(prefs, editMode) {
        if (editMode) 1f else prefs.hudOpacity.coerceIn(0.18f, 1f)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .then(Modifier.padding(0.dp)),
    ) {
        val screenW = constraints.maxWidth
        val screenH = constraints.maxHeight
        val density = LocalDensity.current

        fun place(cluster: XyPadCluster, wDp: Dp, hDp: Dp): IntOffset {
            val wPx = with(density) { wDp.toPx() }
            val hPx = with(density) { hDp.toPx() }
            val (x, y) = XyPadLayout.topLeftPx(
                layout[cluster] ?: XyPadPos(0.5f, 0.5f),
                screenW,
                screenH,
                wPx,
                hPx,
            )
            return IntOffset(x.roundToInt(), y.roundToInt())
        }

        fun dragCluster(cluster: XyPadCluster, wDp: Dp, hDp: Dp, dxPx: Float, dyPx: Float) {
            val wPx = with(density) { wDp.toPx() }
            val hPx = with(density) { hDp.toPx() }
            val next = XyPadLayout.drag(
                layout[cluster] ?: XyPadPos(0.5f, 0.5f),
                dxPx,
                dyPx,
                screenW,
                screenH,
                wPx,
                hPx,
            )
            val updated = layout.toMutableMap().apply { put(cluster, next) }
            layout = updated
            prefs.setVirtualPadLayout(deviceId, updated)
        }

        // ---- Klaster: pill atas (bumper, trigger, start/back, atur, tutup) ----
        ClusterBox(
            cluster = XyPadCluster.TOP_BAR,
            width = 280.dp,
            height = 38.dp,
            place = ::place,
            drag = ::dragCluster,
            editMode = editMode,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color(0x66000000))
                    .border(1.dp, Color(0x80FFFFFF), RoundedCornerShape(19.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PadKey("LT", mini = true, enabled = { !editMode }) {
                    tracker.setTrigger(left = if (it) 1f else 0f); push()
                }
                PadKey("LB", mini = true, enabled = { !editMode }) {
                    tracker.setButton(XyPadButton.LEFT_SHOULDER, it); push()
                }
                PadKey("ST", mini = true, enabled = { !editMode }) {
                    tracker.setButton(XyPadButton.START, it); push()
                }
                PadKey("BK", mini = true, enabled = { !editMode }) {
                    tracker.setButton(XyPadButton.BACK, it); push()
                }
                PadKey("RB", mini = true, enabled = { !editMode }) {
                    tracker.setButton(XyPadButton.RIGHT_SHOULDER, it); push()
                }
                PadKey("RT", mini = true, enabled = { !editMode }) {
                    tracker.setTrigger(right = if (it) 1f else 0f); push()
                }
                PadKey("EDIT", mini = true, enabled = { true }) { down ->
                    if (down) {
                        editMode = !editMode
                        // Stop held buttons AND analog axes when editing disables input.
                        if (editMode) releaseAll()
                        else prefs.setVirtualPadLayout(deviceId, layout)
                    }
                }
                PadKey("TUTUP", mini = true, enabled = { true }) { down ->
                    if (down) {
                        releaseAll()
                        onClose()
                    }
                }
            }
        }

        // ---- Klaster: D-pad ----
        ClusterBox(
            cluster = XyPadCluster.DPAD,
            width = 114.dp,
            height = 114.dp,
            place = ::place,
            drag = ::dragCluster,
            editMode = editMode,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PadKey("▲", enabled = { !editMode }) {
                    tracker.setButton(XyPadButton.DPAD_UP, it); push()
                }
                Row {
                    PadKey("◀", enabled = { !editMode }) {
                        tracker.setButton(XyPadButton.DPAD_LEFT, it); push()
                    }
                    Box(Modifier.size(38.dp))
                    PadKey("▶", enabled = { !editMode }) {
                        tracker.setButton(XyPadButton.DPAD_RIGHT, it); push()
                    }
                }
                PadKey("▼", enabled = { !editMode }) {
                    tracker.setButton(XyPadButton.DPAD_DOWN, it); push()
                }
            }
        }

        // ---- Klaster: stik kiri ----
        ClusterBox(
            cluster = XyPadCluster.LEFT_STICK,
            width = 124.dp,
            height = 124.dp,
            place = ::place,
            drag = ::dragCluster,
            editMode = editMode,
        ) {
            PadStick(
                label = "L",
                dragEnabled = { !editMode },
            ) { x, y ->
                tracker.setLeftStick(x, y); push()
            }
        }

        // ---- Klaster: stik kanan ----
        ClusterBox(
            cluster = XyPadCluster.RIGHT_STICK,
            width = 124.dp,
            height = 124.dp,
            place = ::place,
            drag = ::dragCluster,
            editMode = editMode,
        ) {
            PadStick(
                label = "R",
                dragEnabled = { !editMode },
            ) { x, y ->
                tracker.setRightStick(x, y); push()
            }
        }

        // ---- Klaster: ABXY ----
        ClusterBox(
            cluster = XyPadCluster.ABXY,
            width = 114.dp,
            height = 114.dp,
            place = ::place,
            drag = ::dragCluster,
            editMode = editMode,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PadKey("Y", enabled = { !editMode }) {
                    tracker.setButton(XyPadButton.Y, it); push()
                }
                Row {
                    PadKey("X", enabled = { !editMode }) {
                        tracker.setButton(XyPadButton.X, it); push()
                    }
                    Box(Modifier.size(38.dp))
                    PadKey("B", enabled = { !editMode }) {
                        tracker.setButton(XyPadButton.B, it); push()
                    }
                }
                PadKey("A", enabled = { !editMode }) {
                    tracker.setButton(XyPadButton.A, it); push()
                }
            }
        }
    }
}

/**
 * Wadah klaster: diposisikan lewat [XyPadLayout] dan bisa digeser dengan drag
 * saat [editMode]. Anak-anaknya tetap menerima input saat mode normal karena
 * gesture anak dikonsumsi lebih dulu.
 */
@Composable
private fun BoxScope.ClusterBox(
    cluster: XyPadCluster,
    width: Dp,
    height: Dp,
    place: (XyPadCluster, Dp, Dp) -> IntOffset,
    drag: (XyPadCluster, Dp, Dp, Float, Float) -> Unit,
    editMode: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { place(cluster, width, height) }
            .size(width, height)
            .then(
                if (editMode) {
                    Modifier.border(2.dp, Color(0xCCFFFFFF), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                },
            )
            .pointerInput(cluster) {
                detectDragGestures { change, amount ->
                    if (!editMode) return@detectDragGestures
                    change.consume()
                    drag(cluster, width, height, amount.x, amount.y)
                }
            },
    ) {
        content()
    }
}

/**
 * Satu tombol pad: tekan → [action] dengan `true`, lepas → `false`.
 * [enabled] dicek tiap event (bukan saat komposisi) supaya pergantian mode
 * atur langsung berlaku tanpa menunggu recomposition.
 */
@Composable
private fun PadKey(
    label: String,
    enabled: () -> Boolean = { true },
    mini: Boolean = false,
    action: (Boolean) -> Unit,
) {
    val sizeDp = if (mini) 30.dp else 38.dp
    val enabledNow by rememberUpdatedState(enabled)
    val actionNow by rememberUpdatedState(action)
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(CircleShape)
            .background(Color(0x55000000))
            .border(1.dp, Color(0xAAFFFFFF), CircleShape)
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        if (enabledNow()) {
                            // Capture one callback for both edges of this press.
                            dispatchPadPress(actionNow) { tryAwaitRelease() }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (mini) 10.sp else 12.sp,
        )
    }
}

/**
 * Stik analog: knob digeser di dalam lingkaran dasar, nilai -1..1 per sumbu.
 * Lepat (atau drag batal) → knob kembali ke pusat dan sumbu menjadi (0,0).
 *
 * `onAxis` menerima **y positif = ke atas** (konvensi XUSB) — flip dari
 * koordinat layar dilakukan di sini, satu-satunya tempatnya.
 */
@Composable
private fun PadStick(
    label: String,
    dragEnabled: () -> Boolean,
    onAxis: (Float, Float) -> Unit,
) {
    val density = LocalDensity.current
    val knobRadiusPx = with(density) { 24.dp.toPx() }
    val maxRadiusPx = with(density) { 62.dp.toPx() } - knobRadiusPx
    var knob by remember { mutableStateOf(Offset.Zero) }

    fun applyAxis(offset: Offset) {
        val length = offset.getDistance()
        val clamped = if (length > maxRadiusPx && length > 0f) {
            Offset(offset.x / length * maxRadiusPx, offset.y / length * maxRadiusPx)
        } else {
            offset
        }
        knob = clamped
        onAxis(clamped.x / maxRadiusPx, -clamped.y / maxRadiusPx)
    }

    Box(
        modifier = Modifier
            .size(124.dp)
            .clip(CircleShape)
            .background(Color(0x42000000))
            .border(1.dp, Color(0x80FFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    translationX = knob.x
                    translationY = knob.y
                }
                .clip(CircleShape)
                .background(Color(0x99EEEEEE))
                .border(1.dp, Color(0xFFFFFFFF), CircleShape)
                .pointerInput(label) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            if (!dragEnabled()) return@detectDragGestures
                            change.consume()
                            applyAxis(knob + amount)
                        },
                        onDragEnd = {
                            knob = Offset.Zero
                            onAxis(0f, 0f)
                        },
                        onDragCancel = {
                            knob = Offset.Zero
                            onAxis(0f, 0f)
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color(0xCC000000),
                fontSize = 12.sp,
            )
        }
    }
}
