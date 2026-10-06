package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.pcstream.XyGamepadState
import id.xydesk.remote.pcstream.XyPadButton
import id.xydesk.remote.pcstream.XyVirtualPadTracker
import id.xydesk.remote.ui.input.XyPadCluster
import id.xydesk.remote.ui.input.XyPadLayout
import id.xydesk.remote.ui.input.XyPadPos
import id.xydesk.remote.ui.input.XyStickMode
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val STICK_SIZE = 116.dp
private val BUTTON_SIZE = 52.dp
private val PILL_WIDTH = 52.dp
private val PILL_HEIGHT = 34.dp
private val CLUSTER_GAP = 10.dp

/** Ukuran tiap klaster dalam dp, dipakai menghitung penjepitan posisi. */
private fun clusterSize(cluster: XyPadCluster): Pair<Dp, Dp> = when (cluster) {
    XyPadCluster.TOP_BAR -> Pair(PILL_WIDTH * 4 + CLUSTER_GAP * 3, PILL_HEIGHT)
    XyPadCluster.DPAD -> Pair(BUTTON_SIZE * 3, BUTTON_SIZE * 3)
    XyPadCluster.ABXY -> Pair(BUTTON_SIZE * 3, BUTTON_SIZE * 3 + CLUSTER_GAP + PILL_HEIGHT)
    XyPadCluster.LEFT_STICK -> Pair(STICK_SIZE, STICK_SIZE)
    XyPadCluster.RIGHT_STICK -> Pair(STICK_SIZE, STICK_SIZE)
}

/**
 * Overlay gamepad virtual.
 *
 * Tata letak tidak lagi dipatok ke sudut layar: tiap klaster (stik kiri, D-pad,
 * ABXY, stik kanan, bar shoulder) punya posisi sendiri dalam pecahan ukuran
 * layar sehingga bisa digeser dan tidak menutupi overlay lain seperti pemutar
 * Spotify yang mengambang di tengah-atas. Posisi disimpan oleh pemanggil.
 *
 * Overlay ini tidak tahu apa-apa soal transport: ia hanya melaporkan
 * [XyGamepadState].
 */
@Composable
fun XyVirtualGamepadOverlay(
    onState: (XyGamepadState) -> Unit,
    layout: Map<XyPadCluster, XyPadPos>,
    onLayoutChange: (Map<XyPadCluster, XyPadPos>) -> Unit,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    editMode: Boolean,
    onExitEdit: () -> Unit,
    stickMode: XyStickMode = XyStickMode.POINTER,
    onStickModeChange: (XyStickMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tracker = remember { XyVirtualPadTracker() }
    val emit = { onState(tracker.state()) }
    val currentLayout by rememberUpdatedState(layout)
    val currentOnChange by rememberUpdatedState(onLayoutChange)
    var screen by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize().onSizeChanged { screen = it }) {
        XyPadCluster.entries.forEach { cluster ->
            val pos = currentLayout[cluster] ?: XyPadLayout.defaults().getValue(cluster)
            val (widthDp, heightDp) = clusterSize(cluster)
            val clusterW = with(density) { (widthDp * scale).toPx() }
            val clusterH = with(density) { (heightDp * scale).toPx() }
            val (left, top) = XyPadLayout.topLeftPx(pos, screen.width, screen.height, clusterW, clusterH)

            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .then(
                        if (editMode) {
                            Modifier
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp),
                                )
                                .pointerInput(cluster, editMode, screen, clusterW, clusterH) {
                                    detectDragGestures { _, drag ->
                                        val moved = XyPadLayout.drag(
                                            pos = currentLayout[cluster]
                                                ?: XyPadLayout.defaults().getValue(cluster),
                                            dxPx = drag.x,
                                            dyPx = drag.y,
                                            screenW = screen.width,
                                            screenH = screen.height,
                                            clusterW = clusterW,
                                            clusterH = clusterH,
                                        )
                                        currentOnChange(currentLayout + (cluster to moved))
                                    }
                                }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                when (cluster) {
                    XyPadCluster.TOP_BAR -> XyTopBar(scale) { button, down ->
                        tracker.setButton(button, down); emit()
                    }

                    XyPadCluster.DPAD -> XyDpad(scale) { button, down ->
                        tracker.setButton(button, down); emit()
                    }

                    XyPadCluster.ABXY -> XyAbxy(
                        scale = scale,
                        onButton = { button, down ->
                            tracker.setButton(button, down); emit()
                        },
                        onTrigger = { left, right ->
                            tracker.setTrigger(
                                left = if (left) 1f else 0f,
                                right = if (right) 1f else 0f,
                            )
                            emit()
                        },
                    )

                    XyPadCluster.LEFT_STICK -> XyJoystickPad(
                        size = STICK_SIZE * scale,
                        label = "L",
                        onAxis = { x, y -> tracker.setLeftStick(x, y); emit() },
                    )

                    XyPadCluster.RIGHT_STICK -> XyJoystickPad(
                        size = STICK_SIZE * scale,
                        label = "R",
                        onAxis = { x, y -> tracker.setRightStick(x, y); emit() },
                    )
                }
            }
        }

        if (editMode) {
            XyPadEditBar(
                scale = scale,
                onScaleChange = onScaleChange,
                onReset = { onLayoutChange(XyPadLayout.defaults()) },
                onDone = onExitEdit,
                stickMode = stickMode,
                onCycleStickMode = {
                    val all = XyStickMode.entries
                    onStickModeChange(all[(stickMode.ordinal + 1) % all.size])
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun XyTopBar(scale: Float, onButton: (XyPadButton, Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CLUSTER_GAP * scale)) {
        XyPadPill("LB", scale) { down -> onButton(XyPadButton.LEFT_SHOULDER, down) }
        XyPadPill("BACK", scale) { down -> onButton(XyPadButton.BACK, down) }
        XyPadPill("START", scale) { down -> onButton(XyPadButton.START, down) }
        XyPadPill("RB", scale) { down -> onButton(XyPadButton.RIGHT_SHOULDER, down) }
    }
}

@Composable
private fun XyPadEditBar(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    stickMode: XyStickMode,
    onCycleStickMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Geser klaster", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        XyEditChip(stickModeLabel(stickMode), onCycleStickMode)
        XyEditChip("−") { onScaleChange(XyPadLayout.clampScale(scale - 0.1f)) }
        Text("${(scale * 100).roundToInt()}%", fontSize = 11.sp)
        XyEditChip("+") { onScaleChange(XyPadLayout.clampScale(scale + 0.1f)) }
        XyEditChip("Reset", onReset)
        XyEditChip("Selesai", onDone)
    }
}

/** Label ringkas mode stik untuk chip di bar edit. */
private fun stickModeLabel(mode: XyStickMode): String = when (mode) {
    XyStickMode.POINTER -> "Stik: Kursor"
    XyStickMode.WASD -> "Stik: WASD"
    XyStickMode.ARROWS -> "Stik: Panah"
}

@Composable
private fun XyEditChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Stik analog. Melaporkan sumbu -1..1 saat digeser dan (0,0) bersih saat
 * dilepas/dibatalkan, supaya kursor remote tidak terus melaju.
 */
@Composable
fun XyJoystickPad(
    size: Dp,
    label: String,
    onAxis: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sizePx by remember { mutableStateOf(IntSize.Zero) }
    var knob by remember { mutableStateOf(Offset.Zero) }

    val report = {
        val radius = (min(sizePx.width, sizePx.height) / 2f).coerceAtLeast(1f)
        onAxis((knob.x / radius).coerceIn(-1f, 1f), (knob.y / radius).coerceIn(-1f, 1f))
    }

    Box(
        modifier = modifier
            .size(size)
            .onSizeChanged { sizePx = it }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.42f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
            .pointerInput(sizePx) {
                detectDragGestures(
                    onDragStart = { start ->
                        knob = clampToCircle(
                            start - Offset(sizePx.width / 2f, sizePx.height / 2f),
                            sizePx,
                        )
                        report()
                    },
                    onDrag = { _, drag ->
                        knob = clampToCircle(knob + drag, sizePx)
                        report()
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
        Box(
            modifier = Modifier
                .size(size / 2.4f)
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Tombol bulat tekan-tahan; memancarkan true saat sentuh dan false saat lepas. */
@Composable
fun XyPadRoundButton(
    label: String,
    size: Dp,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), CircleShape)
            .pointerInput(onLongPress) {
                detectTapGestures(
                    onLongPress = { onLongPress?.invoke() },
                    onPress = {
                        onPress(true)
                        val released = tryAwaitRelease()
                        onPress(!released)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Kontrol persegi kecil untuk shoulder, trigger, START, dan BACK. */
@Composable
private fun XyPadPill(label: String, scale: Float, onPress: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = PILL_WIDTH * scale, height = PILL_HEIGHT * scale)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                MaterialTheme.shapes.medium,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress(true)
                        val released = tryAwaitRelease()
                        onPress(!released)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun XyDpad(scale: Float, onButton: (XyPadButton, Boolean) -> Unit) {
    val size = BUTTON_SIZE * scale
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        XyPadRoundButton("▲", size, { down -> onButton(XyPadButton.DPAD_UP, down) })
        Row {
            XyPadRoundButton("◀", size, { down -> onButton(XyPadButton.DPAD_LEFT, down) })
            XyPadRoundButton("●", size, { down -> onButton(XyPadButton.LEFT_THUMB, down) })
            XyPadRoundButton("▶", size, { down -> onButton(XyPadButton.DPAD_RIGHT, down) })
        }
        XyPadRoundButton("▼", size, { down -> onButton(XyPadButton.DPAD_DOWN, down) })
    }
}

@Composable
private fun XyAbxy(
    scale: Float,
    onButton: (XyPadButton, Boolean) -> Unit,
    onTrigger: (left: Boolean, right: Boolean) -> Unit,
) {
    val size = BUTTON_SIZE * scale
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        XyPadRoundButton("Y", size, { down -> onButton(XyPadButton.Y, down) })
        Row {
            XyPadRoundButton("X", size, { down -> onButton(XyPadButton.X, down) })
            Box(modifier = Modifier.size(size))
            XyPadRoundButton("B", size, { down -> onButton(XyPadButton.B, down) })
        }
        XyPadRoundButton("A", size, { down -> onButton(XyPadButton.A, down) })
        Row(
            modifier = Modifier.padding(top = CLUSTER_GAP * scale),
            horizontalArrangement = Arrangement.spacedBy(CLUSTER_GAP * scale),
        ) {
            XyPadPill("LT", scale) { down -> onTrigger(down, false) }
            XyPadPill("RT", scale) { down -> onTrigger(false, down) }
        }
    }
}

/** Jaga offset knob tetap di dalam lingkaran alasnya. */
private fun clampToCircle(offset: Offset, size: IntSize): Offset {
    val radius = min(size.width, size.height) / 2f
    val magnitude = sqrt(offset.x * offset.x + offset.y * offset.y)
    if (magnitude <= radius || magnitude == 0f) return offset
    val scale = radius / magnitude
    return Offset(offset.x * scale, offset.y * scale)
}
