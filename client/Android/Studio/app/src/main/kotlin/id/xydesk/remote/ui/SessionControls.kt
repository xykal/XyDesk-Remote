package id.xydesk.remote.ui

import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XyToggleRow
import id.xydesk.remote.ui.theme.XyPill
import kotlin.math.roundToInt

enum class XyMouseButton { LEFT, RIGHT, MIDDLE }

private val ClusterBg = Color(0xD912161A)
private val ClusterLine = Color(0x33FFFFFF)

/**
 * Kontrol sesi XyDesk.
 *
 * Prinsip yang dipegang di sini:
 *  - Layar sesi bersih: tidak ada bar atas berisi IP/host. Yang tersisa cuma
 *    handle kecil di tepi kanan untuk membuka panel pengaturan.
 *  - Pointer (satu) terpisah total dari tombol mouse. Ukuran pointer bisa
 *    dikecilkan/dibesarkan; cluster tombol bisa dipindah dan di-skala.
 *  - Tombol keyboard selalu di pojok bawah (kanan/kiri) supaya tidak
 *    bertumpuk dengan baris tombol fungsi keyboard.
 */
@Composable
fun SessionControls(
    deviceId: String,
    hostLabel: String,
    statusText: String,
    zoomPercent: Int,
    pointerScreen: Offset,
    pointerVisible: Boolean,
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    keyboardShown: Boolean,
    onKeyboardShownChange: (Boolean) -> Unit,
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
    var clusterScale by remember { mutableFloatStateOf(prefs.clusterScale) }
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
        // ---- pointer tunggal, posisinya mengikuti kursor remote ----
        if (pointerVisible) {
            XyPointer(
                position = pointerScreen,
                sizeDp = pointerSize,
                style = pointerStyle,
            )
        }

        // ---- cluster tombol mouse (bisa dipindah + di-skala) ----
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val clusterWidth = 150f * clusterScale
            val clusterHeight = 190f * clusterScale
            val maxX = (widthPx - clusterWidth * 3f).coerceAtLeast(1f)
            val maxY = (heightPx - clusterHeight * 3f).coerceAtLeast(1f)
            Column(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (clusterX * maxX).roundToInt(),
                            (clusterY * maxY).roundToInt(),
                        )
                    }
                    .zIndex(11f),
                verticalArrangement = Arrangement.spacedBy(6.dp * clusterScale),
                horizontalAlignment = Alignment.End,
            ) {
                // grip: seret untuk pindah posisi
                Box(
                    modifier = Modifier
                        .clip(XyPill)
                        .background(ClusterBg)
                        .border(1.dp, ClusterLine, XyPill)
                        .pointerInput(Unit) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                clusterX = (clusterX + drag.x / maxX).coerceIn(0f, 1f)
                                clusterY = (clusterY + drag.y / maxY).coerceIn(0f, 1f)
                                prefs.setCluster(deviceId, clusterX, clusterY)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        "GESER",
                        fontSize = 9.sp,
                        letterSpacing = 1.2.sp,
                        color = Color(0xFFB9C2CA),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (showScroll) {
                    ClusterButton("SCROLL", XyIcons.ScrollUp, clusterScale) {
                        tap(); onScroll(1)
                    }
                    ClusterButton("SCROLL", XyIcons.ScrollDown, clusterScale) {
                        tap(); onScroll(-1)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp * clusterScale)) {
                    if (showLeft) {
                        ClusterButton("L", null, clusterScale, onPress = { down ->
                            tap(); onMouse(XyMouseButton.LEFT, down)
                        })
                    }
                    if (showRight) {
                        ClusterButton("R", null, clusterScale, onPress = { down ->
                            tap(); onMouse(XyMouseButton.RIGHT, down)
                        })
                    }
                    if (showMiddle) {
                        ClusterButton("M", null, clusterScale, onPress = { down ->
                            tap(); onMouse(XyMouseButton.MIDDLE, down)
                        })
                    }
                }
                if (showSwitch) {
                    ClusterButton(
                        if (inputMode == InputMode.TRACKPAD) "TRACKPAD" else "SENTUH",
                        XyIcons.Swap,
                        clusterScale,
                    ) {
                        tap()
                        onToggleTrackpad()
                    }
                }
            }
        }

        // ---- tombol keyboard, pojok bawah ----
        val keyboardAlignment = if (prefs.keyboardCorner == Corner.RIGHT) Alignment.BottomEnd
        else Alignment.BottomStart
        Box(
            Modifier
                .align(keyboardAlignment)
                .padding(18.dp)
                .zIndex(12f),
        ) {
            KeyboardButton(active = keyboardShown) {
                tap()
                onKeyboardShownChange(!keyboardShown)
            }
        }

        // ---- handle panel (panah kecil di tepi kanan) ----
        if (!panelOpen) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 2.dp)
                    .clip(XyPill)
                    .background(ClusterBg)
                    .border(1.dp, ClusterLine, XyPill)
                    .clickable { panelOpen = true }
                    .padding(vertical = 18.dp, horizontal = 6.dp)
                    .zIndex(12f),
            ) {
                androidx.compose.material3.Icon(
                    XyIcons.ChevronLeft,
                    contentDescription = "Buka panel sesi",
                    tint = Color(0xFFD7DEE4),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (panelOpen) {
            SessionPanel(
                hostLabel = hostLabel,
                statusText = statusText,
                zoomPercent = zoomPercent,
                inputMode = inputMode,
                onInputModeChange = onInputModeChange,
                pointerVisible = pointerVisible,
                onPointerVisibilityChange = onPointerVisibilityChange,
                pointerStyle = pointerStyle,
                onPointerStyle = {
                    pointerStyle = it
                    prefs.pointerStyle = it
                },
                pointerSize = pointerSize,
                onPointerSize = {
                    pointerSize = it
                    prefs.pointerSize = it
                },
                clusterScale = clusterScale,
                onClusterScale = {
                    clusterScale = it
                    prefs.clusterScale = it
                },
                haptics = haptics,
                onHaptics = {
                    haptics = it
                    prefs.haptics = it
                },
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
                onClose = { panelOpen = false },
            )
        }
    }
}

@Composable
private fun KeyboardButton(active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFFEFF3F6) else ClusterBg)
            .border(1.dp, if (active) Color.Transparent else ClusterLine, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            XyIcons.Keyboard,
            contentDescription = "Keyboard",
            tint = if (active) Color(0xFF0A0C0E) else Color(0xFFE7EDF2),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ClusterButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    scale: Float,
    onPress: ((Boolean) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
) {
    val size = (56f * scale).dp
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
        Modifier.clickable { onTap?.invoke() }
    }
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(ClusterBg)
            .border(1.dp, ClusterLine, MaterialTheme.shapes.medium)
            .then(pressModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = label,
                tint = Color(0xFFE7EDF2),
                modifier = Modifier.size(size * 0.42f),
            )
        } else {
            Text(
                label,
                color = Color(0xFFE7EDF2),
                fontSize = (15f * scale).sp,
                fontWeight = FontWeight.Bold,
            )
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
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
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

@Composable
private fun SessionPanel(
    hostLabel: String,
    statusText: String,
    zoomPercent: Int,
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    pointerVisible: Boolean,
    onPointerVisibilityChange: (Boolean) -> Unit,
    pointerStyle: PointerStyle,
    onPointerStyle: (PointerStyle) -> Unit,
    pointerSize: Float,
    onPointerSize: (Float) -> Unit,
    clusterScale: Float,
    onClusterScale: (Float) -> Unit,
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
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClose)
            .zIndex(20f),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.86f)
                .background(Color(0xF20B0D10))
                .border(1.dp, ClusterLine)
                .clickable { }
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        hostLabel,
                        color = Color(0xFFF2F6F9),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$statusText  ·  ${zoomPercent}%",
                        color = Color(0xFF9AA4AD),
                        fontSize = 11.sp,
                    )
                }
                Box(
                    Modifier
                        .clip(XyPill)
                        .border(1.dp, ClusterLine, XyPill)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text("Tutup", color = Color(0xFFE7EDF2), fontSize = 11.sp)
                }
            }

            PanelSection("Input") {
                XySegmented(
                    options = InputMode.entries.map { it.title },
                    selectedIndex = inputMode.ordinal,
                    onSelect = { onInputModeChange(InputMode.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    InputMode.entries[inputMode.ordinal].detail,
                    color = Color(0xFF9AA4AD),
                    fontSize = 11.sp,
                )
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
                Text("Ukuran: ${pointerSize.toInt()} dp", color = Color(0xFF9AA4AD), fontSize = 11.sp)
                Slider(
                    value = pointerSize,
                    onValueChange = onPointerSize,
                    valueRange = 10f..52f,
                )
            }

            PanelSection("Tombol mouse") {
                Text("Ukuran cluster: ${(clusterScale * 100).toInt()}%", color = Color(0xFF9AA4AD), fontSize = 11.sp)
                Slider(
                    value = clusterScale,
                    onValueChange = onClusterScale,
                    valueRange = 0.7f..1.8f,
                )
                XyToggleRow("Tombol kiri", null, showLeft, onShowLeft)
                XyToggleRow("Tombol kanan", null, showRight, onShowRight)
                XyToggleRow("Tombol tengah", null, showMiddle, onShowMiddle)
                XyToggleRow("Scroll atas/bawah", null, showScroll, onShowScroll)
                XyToggleRow("Ganti mode input", null, showSwitch, onShowSwitch)
                XyToggleRow("Getaran", null, haptics, onHaptics)
                XyPillButton("Pulihkan posisi cluster", onResetCluster, primary = false, compact = true)
            }

            PanelSection("Layar") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton("Fit", onFit, primary = false, compact = true, modifier = Modifier.weight(1f))
                    XyPillButton("100%", onZoomActual, primary = false, compact = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton("Perkecil", onZoomOut, primary = false, compact = true, modifier = Modifier.weight(1f))
                    XyPillButton("Perbesar", onZoomIn, primary = false, compact = true, modifier = Modifier.weight(1f))
                }
                Text("Resolusi remote", color = Color(0xFF9AA4AD), fontSize = 11.sp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DisplayPrefs.resolutions.chunked(2).forEach { row ->
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
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Text(
                    "Ganti resolusi = sesi putus lalu sambung ulang dengan ukuran baru.",
                    color = Color(0xFF9AA4AD),
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(2.dp))
                XySegmented(
                    options = DisplayPrefs.rotations,
                    selectedIndex = DisplayPrefs.rotations.indexOf(rotation).coerceAtLeast(0),
                    onSelect = { onRotation(DisplayPrefs.rotations[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PanelSection("Sesi") {
                XyPillButton("Ambil screenshot", onScreenshot, primary = false, compact = true, modifier = Modifier.fillMaxWidth())
                XyPillButton("Putuskan sesi", onDisconnect, primary = false, compact = true, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(24.dp))
        }
    }
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
