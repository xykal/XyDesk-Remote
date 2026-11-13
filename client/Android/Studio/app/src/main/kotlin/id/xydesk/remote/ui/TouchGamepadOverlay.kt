package id.xydesk.remote.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Kontrol gamepad sentuh ala AetherSX2 (Dual Analog Pad) — permintaan pemilik
 * 13 Nov 2026. Layout: L2/L1 kiri-atas, R2/R1 kanan-atas, D-pad + stick kiri,
 * face buttons (△○✕□) + stick kanan, Start/Select di tengah-bawah.
 *
 * Semua masukan disuntik sebagai KeyEvent Android ke [SessionSurfaceController]
 * (jalur yang sama dengan keyboard HP), jadi tidak ada protokol baru:
 *  - Stick kiri  : W/A/S/D (tahan)
 *  - Stick kanan : panah (tahan)
 *  - D-pad       : panah (tahan)
 *  - ✕ = Spasi, ○ = Shift, □ = Ctrl, △ = E
 *  - L1 = Q, L2 = 1, R1 = R, R2 = 3
 *  - Start = Enter, Select = Tab
 */
@Composable
fun TouchGamepadOverlay(controller: SessionSurfaceController, enabled: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    var padAlpha by rememberSaveable { mutableFloatStateOf(0.62f) }

    val send = remember(controller) {
        { action: Int, code: Int -> controller.onKeyEvent(KeyEvent(action, code)); Unit }
    }
    val keys = remember(controller) { PadKeys(send) }

    DisposableEffect(enabled) {
        onDispose { keys.upAll() }
    }
    if (!enabled) {
        keys.upAll()
    }

    Box(Modifier.fillMaxSize().zIndex(3f)) {
        if (!enabled) return@Box

        if (!open) {
            // Tombol pemicu — pojok kanan-atas, di atas surface tapi tidak
            // menghalangi area sentuh utama.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0x66000000))
                    .pointerInput(Unit) { detectTapGestures { open = true } }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("🎮 Kontrol", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            return@Box
        }

        // ===== Panel gamepad terbuka =====
        // Semua klaster punya pointerInput sendiri (menahan tombol), jadi tidak
        // ada gesture layar penuh yang memblokir surface di luar widget.

        // --- L2/L1 kiri-atas ---
        Column(
            Modifier.align(Alignment.TopStart).padding(10.dp).alpha(padAlpha),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ShoulderButton("L2", keys, KeyEvent.KEYCODE_1)
            ShoulderButton("L1", keys, KeyEvent.KEYCODE_Q)
        }
        // --- R2/R1 kanan-atas ---
        Column(
            Modifier.align(Alignment.TopEnd).padding(10.dp).alpha(padAlpha),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ShoulderButton("R2", keys, KeyEvent.KEYCODE_3)
            ShoulderButton("R1", keys, KeyEvent.KEYCODE_R)
        }

        // --- D-pad (tengah-kiri) ---
        DPad(
            keys = keys,
            up = KeyEvent.KEYCODE_DPAD_UP,
            down = KeyEvent.KEYCODE_DPAD_DOWN,
            left = KeyEvent.KEYCODE_DPAD_LEFT,
            right = KeyEvent.KEYCODE_DPAD_RIGHT,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 18.dp)
                .alpha(padAlpha),
        )

        // --- Face buttons (tengah-kanan): △ ○ ✕ □ ---
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).alpha(padAlpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FaceButton("△", keys, KeyEvent.KEYCODE_E)
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                FaceButton("□", keys, KeyEvent.KEYCODE_CTRL_LEFT)
                FaceButton("○", keys, KeyEvent.KEYCODE_SHIFT_LEFT)
            }
            FaceButton("✕", keys, KeyEvent.KEYCODE_SPACE)
        }

        // --- Stick kiri (WASD) ---
        AnalogStick(
            label = "WASD",
            keys = keys,
            map = intArrayOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .alpha(padAlpha),
        )
        // --- Stick kanan (panah) ---
        AnalogStick(
            label = "PANAH",
            keys = keys,
            map = intArrayOf(
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            ),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .alpha(padAlpha),
        )

        // --- Start / Select + pengaturan di tengah-bawah ---
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallPill("SELECT", keys, KeyEvent.KEYCODE_TAB)
                SmallPill("START", keys, KeyEvent.KEYCODE_ENTER)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Opasitas", color = Color.White, fontSize = 11.sp)
                Slider(
                    value = padAlpha,
                    onValueChange = { padAlpha = it },
                    valueRange = 0.25f..1f,
                    modifier = Modifier.width(120.dp),
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0x66000000))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                keys.upAll()
                                open = false
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("Tutup", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Antrean tombol tahan (down/up) — tombol yang sama tidak ditekan dobel. */
private class PadKeys(private val send: (Int, Int) -> Unit) {
    private val held = mutableSetOf<Int>()
    fun down(code: Int) {
        if (code != 0 && held.add(code)) send(KeyEvent.ACTION_DOWN, code)
    }

    fun up(code: Int) {
        if (held.remove(code)) send(KeyEvent.ACTION_UP, code)
    }

    fun tap(code: Int) {
        down(code)
        up(code)
    }

    fun upAll() {
        held.toList().forEach { up(it) }
    }
}

/** Tombol bahu ala AetherSX2 — kotak rounded dengan label. */
@Composable
private fun ShoulderButton(text: String, keys: PadKeys, code: Int) {
    Box(
        Modifier
            .width(58.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x33FFFFFF))
            .pointerInput(code) {
                detectTapGestures(
                    onPress = {
                        keys.down(code)
                        try {
                            tryAwaitRelease()
                        } finally {
                            keys.up(code)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Face button bundar (△○✕□). */
@Composable
private fun FaceButton(symbol: String, keys: PadKeys, code: Int) {
    Box(
        Modifier
            .padding(5.dp)
            .size(52.dp)
            .clip(CircleShape)
            .background(Color(0x2EFFFFFF))
            .pointerInput(code) {
                detectTapGestures(
                    onPress = {
                        keys.down(code)
                        try {
                            tryAwaitRelease()
                        } finally {
                            keys.up(code)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

/** Pil kecil Start/Select — ketuk sekali. */
@Composable
private fun SmallPill(text: String, keys: PadKeys, code: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x40FFFFFF))
            .pointerInput(code) { detectTapGestures { keys.tap(code) } }
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * D-pad 4 arah (tahan). Dibuat dari kolom/baris sel dengan area tekan masing-masing.
 */
@Composable
private fun DPad(keys: PadKeys, up: Int, down: Int, left: Int, right: Int, modifier: Modifier = Modifier) {
    val cell = 46.dp
    Column(modifier) {
        Row {
            Spacer(Modifier.size(cell))
            DPadCell("▲", up, keys, Modifier.size(cell))
            Spacer(Modifier.size(cell))
        }
        Row {
            DPadCell("◀", left, keys, Modifier.size(cell))
            Spacer(Modifier.size(cell))
            DPadCell("▶", right, keys, Modifier.size(cell))
        }
        Row {
            Spacer(Modifier.size(cell))
            DPadCell("▼", down, keys, Modifier.size(cell))
            Spacer(Modifier.size(cell))
        }
    }
}

@Composable
private fun DPadCell(symbol: String, code: Int, keys: PadKeys, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x2EFFFFFF))
            .pointerInput(code) {
                detectTapGestures(
                    onPress = {
                        keys.down(code)
                        try {
                            tryAwaitRelease()
                        } finally {
                            keys.up(code)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Color.White, fontSize = 15.sp)
    }
}

/**
 * Stick analog bundar: drag knob -> 4 arah (atas/bawah/kiri/kanan) ditahan,
 * kombinasi diagonal memegang dua kunci. `map` = [atas, bawah, kiri, kanan].
 */
@Composable
private fun AnalogStick(label: String, keys: PadKeys, map: IntArray, modifier: Modifier = Modifier) {
    val radius = 52.dp
    var knob by remember { mutableStateOf(Offset.Zero) }
    val maxPx = with(androidx.compose.ui.platform.LocalDensity.current) { 34.dp.toPx() }

    Box(modifier.size(radius * 2), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color(0x24FFFFFF)),
        )
        Box(
            Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(58.dp)
                .clip(CircleShape)
                .background(Color(0x66FFFFFF))
                .pointerInput(map) {
                    detectDragGestures(
                        onDragEnd = {
                            knob = Offset.Zero
                            keys.upAll()
                        },
                        onDragCancel = {
                            knob = Offset.Zero
                            keys.upAll()
                        },
                    ) { change, drag ->
                        change.consume()
                        val next = (knob + drag)
                        val dist = next.getDistance()
                        val clamped = if (dist > maxPx) next * (maxPx / dist) else next
                        knob = clamped
                        // Threshold 35% radius -> arah aktif
                        val nx = clamped.x / maxPx
                        val ny = clamped.y / maxPx
                        val t = 0.35f
                        if (ny < -t) keys.down(map[0]) else keys.up(map[0])
                        if (ny > t) keys.down(map[1]) else keys.up(map[1])
                        if (nx < -t) keys.down(map[2]) else keys.up(map[2])
                        if (nx > t) keys.down(map[3]) else keys.up(map[3])
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}
