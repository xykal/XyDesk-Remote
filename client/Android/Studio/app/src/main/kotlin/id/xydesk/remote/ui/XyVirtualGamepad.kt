package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.pcstream.XyGamepadState
import id.xydesk.remote.pcstream.XyPadButton
import id.xydesk.remote.pcstream.XyVirtualPadTracker
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * On-screen gamepad overlay for XyDesk sessions.
 *
 * One control surface feeds both transports: the caller receives a normalized
 * [XyGamepadState] and decides where it goes (RDP pointer/keys via
 * `XyGamepadActionMapper`, and/or PC/Game Stream packets). This file therefore
 * contains no transport logic at all.
 *
 * Every control reports press *and* release so a lifted finger can never leave a
 * button stuck on the host.
 */
@Composable
fun XyVirtualGamepadOverlay(
    onState: (XyGamepadState) -> Unit,
    modifier: Modifier = Modifier,
    stickSize: Dp = 116.dp,
    buttonSize: Dp = 52.dp,
) {
    val tracker = remember { XyVirtualPadTracker() }
    val emit = { onState(tracker.state()) }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            XyPadPill("LB", buttonSize) { down ->
                tracker.setButton(XyPadButton.LEFT_SHOULDER, down); emit()
            }
            XyPadPill("BACK", buttonSize) { down ->
                tracker.setButton(XyPadButton.BACK, down); emit()
            }
            XyPadPill("START", buttonSize) { down ->
                tracker.setButton(XyPadButton.START, down); emit()
            }
            XyPadPill("RB", buttonSize) { down ->
                tracker.setButton(XyPadButton.RIGHT_SHOULDER, down); emit()
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            XyDpad(buttonSize) { button, down -> tracker.setButton(button, down); emit() }
            XyJoystickPad(
                size = stickSize,
                label = "L",
                onAxis = { x, y -> tracker.setLeftStick(x, y); emit() },
            )
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End,
        ) {
            XyAbxy(buttonSize) { button, down -> tracker.setButton(button, down); emit() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                XyPadPill("LT", buttonSize) { down ->
                    tracker.setTrigger(left = if (down) 1f else 0f); emit()
                }
                XyPadPill("RT", buttonSize) { down ->
                    tracker.setTrigger(right = if (down) 1f else 0f); emit()
                }
            }
            XyJoystickPad(
                size = stickSize,
                label = "R",
                onAxis = { x, y -> tracker.setRightStick(x, y); emit() },
            )
        }
    }
}

/**
 * Analog stick. Reports normalized -1..1 axes while dragging and a clean
 * (0, 0) on release/cancel, so the remote pointer never keeps drifting.
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
                        knob = clampToCircle(start - Offset(sizePx.width / 2f, sizePx.height / 2f), sizePx)
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
                .offsetPixels(knob)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

/** Round press-and-hold button; emits true on touch down and false on release. */
@Composable
fun XyPadRoundButton(
    label: String,
    size: Dp,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), CircleShape)
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
        Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Small rectangular control used for shoulders, triggers, START and BACK. */
@Composable
private fun XyPadPill(label: String, width: Dp, onPress: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = width, height = 34.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), MaterialTheme.shapes.medium)
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
private fun XyDpad(size: Dp, onButton: (XyPadButton, Boolean) -> Unit) {
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
private fun XyAbxy(size: Dp, onButton: (XyPadButton, Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        XyPadRoundButton("Y", size, { down -> onButton(XyPadButton.Y, down) })
        Row {
            XyPadRoundButton("X", size, { down -> onButton(XyPadButton.X, down) })
            Box(modifier = Modifier.size(size))
            XyPadRoundButton("B", size, { down -> onButton(XyPadButton.B, down) })
        }
        XyPadRoundButton("A", size, { down -> onButton(XyPadButton.A, down) })
    }
}

/** Keep a knob offset inside the pad circle so it cannot leave its base. */
private fun clampToCircle(offset: Offset, size: IntSize): Offset {
    val radius = min(size.width, size.height) / 2f
    val magnitude = sqrt(offset.x * offset.x + offset.y * offset.y)
    if (magnitude <= radius || magnitude == 0f) return offset
    val scale = radius / magnitude
    return Offset(offset.x * scale, offset.y * scale)
}

/** Translate a composable by a pixel offset without triggering relayout. */
private fun Modifier.offsetPixels(offset: Offset): Modifier = this.offset {
    IntOffset(offset.x.roundToInt(), offset.y.roundToInt())
}
