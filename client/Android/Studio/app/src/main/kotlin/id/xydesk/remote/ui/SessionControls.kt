package id.xydesk.remote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XyToggleRow
import id.xydesk.remote.ui.theme.XyPill
import kotlin.math.roundToInt

enum class XyMouseButton { LEFT, RIGHT, MIDDLE }

/**
 * Warna HUD sesi. Sengaja konstanta eksplisit, BUKAN MaterialTheme: HUD
 * selalu berada di atas gambar remote (gelap/terang apa pun isinya), jadi
 * skemanya harus tetap gelap walaupun app dipakai dalam mode terang.
 */
private val HudBorder = Color(0xD9FFFFFF)
private val HudBorderDim = Color(0x59FFFFFF)
private val HudInk = Color(0xFFEFF3F6)
private val HudShadow = Color(0x66000000)
private val HudPanelBg = Color(0xF20B0D10)

/**
 * Kontrol sesi XyDesk.
 *
 * Aturan bentuk (permintaan produk):
 *  - Tombol kontrol HUD: bulat penuh, HANYA border + ikon di dalamnya,
 *    tanpa isian warna, dengan shadow hitam tipis supaya tetap terlihat
 *    saat remote menampilkan halaman putih.
 *  - Tiap aksi berdiri sendiri: klik kiri / kanan / tengah, scroll naik,
 *    scroll turun, ganti mode input. Tidak ada tombol gabungan.
 *  - Ukuran tombol + posisi bebas diatur; nilainya disimpan per perangkat.
 *  - Panel pengaturan lebih ramping dari sebelumnya dan memakai skema gelap
 *    yang sama dengan HUD (teks tidak lagi tenggelam di latar panel).
 */
@Composable
fun SessionControls(
    deviceId: String,
    hostLabel: String,
    statusText: String,
    remoteSize: String,
    zoomPercent: Int,
    pointerScreen: Offset,
    pointerVisible: Boolean,
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    keyboardShown: Boolean,
    onKeyboardShownChange: (Boolean) -> Unit,
    overlayShown: Boolean,
    onOverlayShownChange: (Boolean) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onZoomActual: () -> Unit,
    onScreenshot: () -> Unit,
    onDisconnect: () -> Unit,
    onPointerVisibilityChange: (Boolean) -> Unit,
    onResetCluster: () -> Unit,
    onRotationChange: (String) -> Unit,
    onResolutionChange: (String) -> Unit,
    onToggleTrackpad: () -> Unit,
    onMouse: (XyMouseButton, Boolean) -> Unit,
    onScroll: (Int) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { SessionPrefs(context) }
    val view = LocalView.current
    var panelOpen by remember { mutableStateOf(false) }
    var pointerSize by remember { mutableFloatStateOf(prefs.pointerSize) }
    var pointerStyle by remember { mutableStateOf(prefs.pointerStyle) }
    var hudSize by remember { mutableFloatStateOf(prefs.hudButtonSize) }
    var keyboardScale by remember { mutableFloatStateOf(prefs.keyboardScale) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var showLeft by remember { mutableStateOf(prefs.showLeft) }
    var showRight by remember { mutableStateOf(prefs.showRight) }
    var showMiddle by remember { mutableStateOf(prefs.showMiddle) }
    var showScroll by remember { mutableStateOf(prefs.showScroll) }
    var showSwitch by remember { mutableStateOf(prefs.showSwitch) }
    var clusterX by remember { mutableFloatStateOf(prefs.clusterX(deviceId)) }
    var clusterY by remember { mutableFloatStateOf(prefs.clusterY(deviceId)) }
    var resolution by remember { mutableStateOf(DisplayPrefs.resolution(context, deviceId)) }
    var rotation by remember { mutableStateOf(DisplayPrefs.rotation(context, deviceId)) }

    fun tap() {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    Box(Modifier.fillMaxSize().zIndex(10f)) {
        // ---- pointer tunggal, terpisah total dari tombol mouse ----
        if (pointerVisible) {
            XyPointer(position = pointerScreen, sizeDp = pointerSize, style = pointerStyle)
        }

        // ---- deretan tombol kontrol, tiap tombol berdiri sendiri ----
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val btn = hudSize
            val columnHeight = btn * 6.6f
            val maxX = (widthPx - btn * 1.6f).coerceAtLeast(1f)
            val maxY = (heightPx - columnHeight).coerceAtLeast(1f)

            Column(
                modifier = Modifier
                    .offset {
                        IntOffset((clusterX * maxX).roundToInt(), (clusterY * maxY).roundToInt())
                    }
                    .zIndex(11f),
                verticalArrangement = Arrangement.spacedBy((btn * 0.22f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HudChip(hudSize, onDrag = { dx, dy ->
                    clusterX = (clusterX + dx / maxX).coerceIn(0f, 1f)
                    clusterY = (clusterY + dy / maxY).coerceIn(0f, 1f)
                    prefs.setCluster(deviceId, clusterX, clusterY)
                })

                if (showScroll) {
                    HudButton("Scroll naik", XyIcons.ScrollUp, hudSize) { tap(); onScroll(1) }
                    HudButton("Scroll turun", XyIcons.ScrollDown, hudSize) { tap(); onScroll(-1) }
                }
                if (showLeft) {
                    HudButton("Klik kiri", XyIcons.Cursor, hudSize, onPress = { down ->
                        tap(); onMouse(XyMouseButton.LEFT, down)
                    })
                }
                if (showRight) {
                    HudButton("Klik kanan", XyIcons.ClickRight, hudSize, onPress = { down ->
                        tap(); onMouse(XyMouseButton.RIGHT, down)
                    })
                }
                if (showMiddle) {
                    HudButton("Klik tengah", XyIcons.ClickMiddle, hudSize, onPress = { down ->
                        tap(); onMouse(XyMouseButton.MIDDLE, down)
                    })
                }
                if (showSwitch) {
                    HudButton(
                        label = if (inputMode == InputMode.TRACKPAD) "Mode: trackpad" else "Mode: sentuh",
                        icon = XyIcons.Swap,
                        size = hudSize,
                        active = inputMode == InputMode.DIRECT,
                    ) { tap(); onToggleTrackpad() }
                }
            }
        }

        // ---- tombol keyboard overlay, pojok bawah ----
        val keyboardAlignment =
            if (prefs.keyboardCorner == Corner.RIGHT) Alignment.BottomEnd else Alignment.BottomStart
        Box(
            Modifier.align(keyboardAlignment).padding(16.dp).zIndex(12f),
        ) {
            HudButton(
                label = "Keyboard",
                icon = XyIcons.Keyboard,
                size = maxOf(hudSize, 52f),
                active = overlayShown,
                onPress = null,
                onClick = {
                    tap()
                    onOverlayShownChange(!overlayShown)
                },
            )
        }

        // ---- handle panel: panah kecil di tepi kanan ----
        if (!panelOpen) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { panelOpen = true }
                    .padding(vertical = 20.dp, horizontal = 5.dp)
                    .zIndex(12f),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(HudShadow)
                        .border(1.dp, HudBorderDim, RoundedCornerShape(50))
                        .padding(vertical = 16.dp, horizontal = 7.dp),
                ) {
                    Icon(
                        XyIcons.ChevronLeft,
                        contentDescription = "Buka panel sesi",
                        tint = HudInk,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        if (panelOpen) {
            SessionPanel(
                deviceId = deviceId,
                hostLabel = hostLabel,
                statusText = statusText,
                remoteSize = remoteSize,
                zoomPercent = zoomPercent,
                inputMode = inputMode,
                onInputModeChange = onInputModeChange,
                pointerVisible = pointerVisible,
                onPointerVisibilityChange = onPointerVisibilityChange,
                pointerStyle = pointerStyle,
                onPointerStyle = { pointerStyle = it; prefs.pointerStyle = it },
                pointerSize = pointerSize,
                onPointerSize = { pointerSize = it; prefs.pointerSize = it },
                hudSize = hudSize,
                onHudSize = { hudSize = it; prefs.hudButtonSize = it },
                keyboardScale = keyboardScale,
                onKeyboardScale = { keyboardScale = it; prefs.keyboardScale = it },
                overlayShown = overlayShown,
                onOverlayShownChange = onOverlayShownChange,
                haptics = haptics,
                onHaptics = { haptics = it; prefs.haptics = it },
                showLeft = showLeft,
                onShowLeft = { showLeft = it; prefs.showLeft = it },
                showRight = showRight,
                onShowRight = { showRight = it; prefs.showRight = it },
                showMiddle = showMiddle,
                onShowMiddle = { showMiddle = it; prefs.showMiddle = it },
                showScroll = showScroll,
                onShowScroll = { showScroll = it; prefs.showScroll = it },
                showSwitch = showSwitch,
                onShowSwitch = { showSwitch = it; prefs.showSwitch = it },
                onResetCluster = onResetCluster,
                resolution = resolution,
                onResolution = {
                    resolution = it
                    DisplayPrefs.setResolution(context, deviceId, it)
                    onResolutionChange(it)
                },
                rotation = rotation,
                onRotation = {
                    rotation = it
                    DisplayPrefs.setRotation(context, deviceId, it)
                    onRotationChange(it)
                },
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                onFit = onFit,
                onZoomActual = onZoomActual,
                onScreenshot = onScreenshot,
                onDisconnect = onDisconnect,
                keyboardShown = keyboardShown,
                onSystemKeyboard = { onKeyboardShownChange(!keyboardShown) },
                onClose = { panelOpen = false },
            )
        }
    }
}

// ---------------------------------------------------------------- tombol HUD

/**
 * Tombol kontrol: bulat penuh, hanya border + ikon, tanpa isian warna.
 * Shadow hitam tipis di bawahnya supaya tetap terbaca di atas area terang.
 */
@Composable
private fun HudButton(
    label: String,
    icon: ImageVector,
    size: Float,
    active: Boolean = false,
    onPress: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = CircleShape
    val pressModifier = if (onPress != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onPress(true)
                    tryAwaitRelease()
                    onPress(false)
                },
            )
        }
    } else {
        Modifier.clickable { onClick?.invoke() }
    }
    Box(
        Modifier
            .size(size.dp)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .border(1.4.dp, if (active) HudInk else HudBorder, shape)
            .then(pressModifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = HudInk,
            modifier = Modifier.size((size * 0.44f).dp),
        )
    }
}

/** Grip geser untuk memindahkan deretan tombol. */
@Composable
private fun HudChip(size: Float, onDrag: (Float, Float) -> Unit) {
    Box(
        Modifier
            .height((size * 0.42f).dp)
            .clip(XyPill)
            .background(Color(0x4D000000))
            .border(1.dp, HudBorderDim, XyPill)
            .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); onDrag(drag.x, drag.y) } }
            .padding(horizontal = (size * 0.22f).dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(Modifier.size(2.dp).clip(CircleShape).background(HudInk.copy(alpha = 0.7f)))
            }
        }
    }
}

/** Pointer tunggal: titik atau panah, ukuran bebas, outline kontras. */
@Composable
private fun XyPointer(position: Offset, sizeDp: Float, style: PointerStyle) {
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { sizeDp.dp.toPx() }
    Box(
        Modifier
            .offset {
                IntOffset(
                    (position.x - sizePx / 2f).roundToInt(),
                    (position.y - sizePx / 2f).roundToInt(),
                )
            }
            .size(sizeDp.dp)
            .zIndex(11f),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (style) {
                PointerStyle.DOT -> {
                    val radius = w / 2f - 1.5f
                    drawCircle(Color.White, radius = radius, center = Offset(w / 2f, h / 2f))
                    drawCircle(
                        Color.Black.copy(alpha = 0.55f),
                        radius = radius,
                        center = Offset(w / 2f, h / 2f),
                        style = Stroke(width = 3f),
                    )
                }

                PointerStyle.ARROW -> {
                    val path = Path().apply {
                        val sx = w / 24f
                        val sy = h / 24f
                        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
                        val a = p(6f, 3f)
                        val b = p(6f, 18.5f)
                        val c = p(10.2f, 14.6f)
                        val d = p(13.1f, 20.8f)
                        val e = p(15.6f, 19.6f)
                        val f = p(12.8f, 13.6f)
                        val g = p(18.4f, 13.1f)
                        moveTo(a.x, a.y)
                        lineTo(b.x, b.y)
                        lineTo(c.x, c.y)
                        lineTo(d.x, d.y)
                        lineTo(e.x, e.y)
                        lineTo(f.x, f.y)
                        lineTo(g.x, g.y)
                        close()
                    }
                    drawPath(path, Color.White)
                    drawPath(path, Color.Black.copy(alpha = 0.6f), style = Stroke(width = 2.4f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- panel

@Composable
private fun SessionPanel(
    deviceId: String,
    hostLabel: String,
    statusText: String,
    remoteSize: String,
    zoomPercent: Int,
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    pointerVisible: Boolean,
    onPointerVisibilityChange: (Boolean) -> Unit,
    pointerStyle: PointerStyle,
    onPointerStyle: (PointerStyle) -> Unit,
    pointerSize: Float,
    onPointerSize: (Float) -> Unit,
    hudSize: Float,
    onHudSize: (Float) -> Unit,
    keyboardScale: Float,
    onKeyboardScale: (Float) -> Unit,
    overlayShown: Boolean,
    onOverlayShownChange: (Boolean) -> Unit,
    haptics: Boolean,
    onHaptics: (Boolean) -> Unit,
    showLeft: Boolean,
    onShowLeft: (Boolean) -> Unit,
    showRight: Boolean,
    onShowRight: (Boolean) -> Unit,
    showMiddle: Boolean,
    onShowMiddle: (Boolean) -> Unit,
    showScroll: Boolean,
    onShowScroll: (Boolean) -> Unit,
    showSwitch: Boolean,
    onShowSwitch: (Boolean) -> Unit,
    onResetCluster: () -> Unit,
    resolution: String,
    onResolution: (String) -> Unit,
    rotation: String,
    onRotation: (String) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFit: () -> Unit,
    onZoomActual: () -> Unit,
    onScreenshot: () -> Unit,
    onDisconnect: () -> Unit,
    keyboardShown: Boolean,
    onSystemKeyboard: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var custom by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClose)
            .zIndex(20f),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.78f)
                .background(HudPanelBg)
                .border(1.dp, HudBorderDim)
                .clickable { }
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        hostLabel,
                        color = HudInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("$statusText  ·  ${zoomPercent}%", color = Color(0xFF9AA4AD), fontSize = 10.5.sp)
                }
                PanelChip("Tutup", onClose)
            }

            PanelSection("Input") {
                XySegmented(
                    options = InputMode.entries.map { it.title },
                    selectedIndex = inputMode.ordinal,
                    onSelect = { onInputModeChange(InputMode.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PanelHint(InputMode.entries[inputMode.ordinal].detail)
            }

            PanelSection("Pointer") {
                XyToggleRow(
                    title = "Tampilkan pointer",
                    checked = pointerVisible,
                    onCheckedChange = onPointerVisibilityChange,
                )
                XySegmented(
                    options = PointerStyle.entries.map { it.title },
                    selectedIndex = pointerStyle.ordinal,
                    onSelect = { onPointerStyle(PointerStyle.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PanelHint("Ukuran: ${pointerSize.toInt()} dp")
                Slider(value = pointerSize, onValueChange = onPointerSize, valueRange = 10f..52f)
            }

            PanelSection("Tombol kontrol") {
                PanelHint("Ukuran tombol: ${hudSize.toInt()} dp (radius mengikuti, bulat penuh)")
                Slider(value = hudSize, onValueChange = onHudSize, valueRange = 40f..80f)
                XyToggleRow("Klik kiri", null, showLeft, onShowLeft)
                XyToggleRow("Klik kanan", null, showRight, onShowRight)
                XyToggleRow("Klik tengah", null, showMiddle, onShowMiddle)
                XyToggleRow("Scroll naik/turun", null, showScroll, onShowScroll)
                XyToggleRow("Ganti mode input", null, showSwitch, onShowSwitch)
                XyToggleRow("Getaran", null, haptics, onHaptics)
                XyPillButton("Pulihkan posisi", onResetCluster, primary = false, compact = true)
            }

            PanelSection("Keyboard") {
                XyToggleRow(
                    title = "Keyboard layar",
                    subtitle = "QWERTY + F1-F12 + numpad + kombinasi",
                    checked = overlayShown,
                    onCheckedChange = onOverlayShownChange,
                )
                PanelHint("Skala keyboard: ${(keyboardScale * 100).toInt()}%")
                Slider(value = keyboardScale, onValueChange = onKeyboardScale, valueRange = 0.7f..1.6f)
                XyPillButton(
                    text = if (keyboardShown) "Sembunyikan keyboard sistem" else "Keyboard sistem (IME)",
                    onClick = onSystemKeyboard,
                    primary = false,
                    compact = true,
                )
            }

            PanelSection("Layar") {
                PanelHint("Ukuran remote saat ini: $remoteSize")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton("Fit", onFit, primary = false, compact = true, modifier = Modifier.weight(1f))
                    XyPillButton("100%", onZoomActual, primary = false, compact = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton("Perkecil", onZoomOut, primary = false, compact = true, modifier = Modifier.weight(1f))
                    XyPillButton("Perbesar", onZoomIn, primary = false, compact = true, modifier = Modifier.weight(1f))
                }
                PanelHint("Resolusi remote")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisplayPrefs.resolutions.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (value, title) ->
                                XyPillButton(
                                    text = title,
                                    onClick = { onResolution(value) },
                                    primary = value == resolution,
                                    compact = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyField(
                        value = custom,
                        onValueChange = { custom = it },
                        label = "Kustom (mis. 2160x1350)",
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        text = "Pasang",
                        onClick = {
                            DisplayPrefs.parseCustom(custom)?.let { onResolution(it) }
                        },
                        primary = false,
                        compact = true,
                    )
                }
                XySegmented(
                    options = DisplayPrefs.rotations,
                    selectedIndex = DisplayPrefs.rotations.indexOf(rotation).coerceAtLeast(0),
                    onSelect = { onRotation(DisplayPrefs.rotations[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                PanelHint(
                    "Resolusi dikirim ke server saat menyambung (/size + dynamic " +
                        "resolution). Kalau layar remote tidak berubah, server-nya " +
                        "yang menolak ukuran itu.",
                )
            }

            PanelSection("Sesi") {
                XyPillButton(
                    "Ambil screenshot",
                    onScreenshot,
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                XyPillButton(
                    "Putuskan sesi",
                    onDisconnect,
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PanelChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(XyPill)
            .border(1.dp, HudBorderDim, XyPill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = HudInk, fontSize = 11.sp)
    }
}

@Composable
private fun PanelHint(text: String) {
    Text(text, color = Color(0xFF9AA4AD), fontSize = 10.5.sp)
}

@Composable
private fun PanelSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            color = Color(0xFF7E8790),
            fontSize = 10.sp,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}
