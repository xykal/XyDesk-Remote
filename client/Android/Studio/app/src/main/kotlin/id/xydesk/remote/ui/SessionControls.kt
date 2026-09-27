package id.xydesk.remote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XyToggleRow
import id.xydesk.remote.ui.theme.XyPill
import kotlin.math.roundToInt

/**
 * Warna HUD sesi. Sengaja konstanta eksplisit, BUKAN MaterialTheme: HUD
 * selalu berada di atas gambar remote (gelap/terang apa pun isinya), jadi
 * skemanya harus tetap gelap walaupun app dipakai dalam mode terang.
 */
private val HudBorder = Color(0xD9FFFFFF)
private val HudBorderDim = Color(0x7AFFFFFF)
private val HudInk = Color(0xFFEFF3F6)
private val HudMuted = Color(0xFFC9D1D8)
private val HudPanelBg = Color(0xF20B0D10)
private val HudPanelEdge = Color(0x33FFFFFF)

private enum class PanelSide { RIGHT, LEFT }

/**
 * Kontrol sesi XyDesk.
 *
 *  - Tombol : [HudKeyLayer] — tiap tombol bulat penuh, satu aksi, bebas
 *    digeser/diukur/diatur aksinya (termasuk F1..F12, numpad, kombinasi).
 *  - Toolbar: menempel persis di atas keyboard HP (IME); chip paling ujung
 *    `123` membuka board lengkap, `ABC` balik ke keyboard HP.
 *  - Panel  : kanan = input/pointer/tombol/keyboard, kiri = layar/sesi.
 *    Dua-duanya bisa terbuka sekaligus; kalau layar sempit tinggal pilih.
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
    remoteCursor: RemoteCursor? = null,
    zoom: Float = 1f,
    inputMode: InputMode,
    onInputModeChange: (InputMode) -> Unit,
    keyboardShown: Boolean,
    onKeyboardShownChange: (Boolean) -> Unit,
    overlayShown: Boolean,
    onOverlayShownChange: (Boolean) -> Unit,
    keys: List<HudKey>,
    onKeysChange: (List<HudKey>) -> Unit,
    mappingMode: Boolean,
    onMappingModeChange: (Boolean) -> Unit,
    imeHeightPx: Int,
    onPhase: (HudKey, HudPhase) -> Unit,
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
    onSendText: (String) -> Unit,
    coreInfo: List<String> = emptyList(),
) {
    val context = LocalContext.current
    val prefs = remember { SessionPrefs(context) }
    val view = LocalView.current
    var rightOpen by remember { mutableStateOf(false) }
    var leftOpen by remember { mutableStateOf(false) }
    var pickerAux by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HudKey?>(null) }
    var pointerSize by remember { mutableFloatStateOf(prefs.pointerSize) }
    var pointerStyle by remember { mutableStateOf(prefs.pointerStyle) }
    var keyboardScale by remember { mutableFloatStateOf(prefs.keyboardScale) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var resolution by remember { mutableStateOf(DisplayPrefs.resolution(context, deviceId)) }
    var rotation by remember { mutableStateOf(DisplayPrefs.rotation(context, deviceId)) }
    var custom by remember { mutableStateOf("") }
    var textOpen by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf("") }

    fun clipboardText(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = cm?.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context).toString()
    }

    fun haptic() {
        if (haptics) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun replace(updated: HudKey) {
        onKeysChange(keys.map { if (it.id == updated.id) updated else it })
    }

    fun add(option: HudKeyOption) {
        val id = "k${System.currentTimeMillis().toString(36)}"
        val slot = keys.size
        val key = HudKey(
            id = id,
            kind = option.kind,
            label = option.label,
            keyCode = option.keyCode,
            shift = option.shift,
            combo = option.combo,
            action = if (option.kind == HudKind.SCROLL_UP || option.kind == HudKind.SCROLL_DOWN) {
                HudAction.HOLD
            } else {
                HudAction.TAP
            },
            x = (0.60f + 0.08f * (slot % 3)).coerceIn(0f, 1f),
            y = (0.30f + 0.10f * (slot % 5)).coerceIn(0f, 1f),
            size = 48f,
            // Tombol baru masuk ke kelompok yang sedang dibuka: terapung
            // (bisa digeser bebas) atau baris atas keyboard kustom.
            inToolbar = pickerAux,
        )
        onKeysChange(keys + key)
    }

    Box(Modifier.fillMaxSize().zIndex(10f)) {
        if (pointerVisible) {
            XyPointer(
                position = pointerScreen,
                sizeDp = pointerSize,
                style = pointerStyle,
                remote = remoteCursor,
                zoom = zoom,
            )
        }

        HudKeyLayer(
            keys = keys.filter { !it.inToolbar },
            mappingMode = mappingMode,
            onMove = { id, x, y ->
                onKeysChange(keys.map { if (it.id == id) it.copy(x = x, y = y) else it })
            },
            onPhase = { key, phase ->
                haptic()
                onPhase(key, phase)
            },
            onEdit = { editing = it },
        )

        if (mappingMode) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                HudMappingBanner(onDone = { onMappingModeChange(false) })
            }
        }

        if (textOpen) {
            XyOverlay(title = "Kirim teks", onDismiss = { textOpen = false }) {
                Text(
                    "Teks dikirim sebagai unicode ke jendela remote yang sedang fokus.",
                    color = HudMuted,
                    fontSize = 10.5.sp,
                )
                XyField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = "Teks",
                    hint = "mis. password, alamat URL",
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                )
                val paste = clipboardText()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton(
                        "Tempel",
                        {
                            textValue = paste
                            if (paste.isEmpty()) {
                                Toast.makeText(context, "Clipboard kosong", Toast.LENGTH_SHORT).show()
                            }
                        },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        "Kirim",
                        {
                            val t = textValue
                            if (t.isNotEmpty()) onSendText(t)
                            textOpen = false
                        },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                XyPillButton(
                    "Selesai",
                    { textOpen = false },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- handle panel kiri & kanan ----
        if (!leftOpen) {
            PanelHandle(checksLeft = true, onClick = { leftOpen = true }, modifier = Modifier.align(Alignment.CenterStart))
        }
        if (!rightOpen) {
            PanelHandle(checksLeft = false, onClick = { rightOpen = true }, modifier = Modifier.align(Alignment.CenterEnd))
        }

        if (leftOpen) {
            SessionPanel(
                side = PanelSide.LEFT,
                hostLabel = hostLabel,
                statusText = statusText,
                remoteSize = remoteSize,
                zoomPercent = zoomPercent,
                resolution = resolution,
                rotation = rotation,
                custom = custom,
                keyboardShown = keyboardShown,
                overlayShown = overlayShown,
                keyboardScale = keyboardScale,
                onKeyboardScaleChange = { keyboardScale = it; prefs.keyboardScale = it },
                onOverlayShownChange = onOverlayShownChange,
                onCustomChange = { custom = it },
                onCustomApply = {
                    DisplayPrefs.parseCustom(custom)?.let { onResolutionChange(it) }
                },
                onResolution = {
                    resolution = it
                    DisplayPrefs.setResolution(context, deviceId, it)
                    onResolutionChange(it)
                },
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
                onClose = { leftOpen = false },
            )
        }

        if (rightOpen) {
            SessionPanel(
                side = PanelSide.RIGHT,
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
                keys = keys,
                onAddKey = { aux -> pickerAux = aux; pickerOpen = true },
                onEditKey = { editing = it },
                onDeleteKey = { onKeysChange(keys.filterNot { k -> k.id == it.id }) },
                mappingMode = mappingMode,
                onMappingModeChange = onMappingModeChange,
                haptics = haptics,
                onHaptics = { haptics = it; prefs.haptics = it },
                onResetCluster = onResetCluster,
                onToggleTrackpad = onToggleTrackpad,
                overlayShown = overlayShown,
                onOverlayShownChange = onOverlayShownChange,
                keyboardShown = keyboardShown,
                onKeyboardShownChange = onKeyboardShownChange,
                onClose = { rightOpen = false },
            )
        }

        // ---- tombol keyboard cadangan, pojok bawah ----
        // Cuma dipakai kalau user belum punya tombol HUD "Buka keyboard".
        // Tombol HUD bisa digeser; tombol cadangan ini menempel di layar dan
        // dulu menutupi taskbar remote, jadi sekarang jadi opsi terakhir.
        val hasKeyboardKey = keys.any { it.kind == HudKind.KEYBOARD }
        if (!hasKeyboardKey && !keyboardShown && !overlayShown && !mappingMode) {
            val alignment =
                if (prefs.keyboardCorner == Corner.RIGHT) Alignment.BottomEnd else Alignment.BottomStart
            Box(Modifier.align(alignment).padding(16.dp).zIndex(20f)) {
                HudRoundButton(
                    icon = XyIcons.Keyboard,
                    label = "Buka keyboard",
                    size = 52f,
                    active = false,
                ) { onKeyboardShownChange(true) }
            }
        }
    }

    if (pickerOpen) {
        HudKeyPicker(
            onPick = { add(it); pickerOpen = false },
            onDismiss = { pickerOpen = false },
        )
    }
    editing?.let { key ->
        HudKeyEditor(
            key = key,
            onChange = { replace(it) },
            onDelete = {
                onKeysChange(keys.filterNot { k -> k.id == key.id })
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** Chip kecil ikon di toolbar (tanpa background, border tipis). */
@Composable
private fun HudMiniChip(icon: ImageVector, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.2.dp, HudBorderDim, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = HudInk, modifier = Modifier.size(19.dp))
    }
}

/** Tombol bulat besar (dipakai tombol keyboard pojok bawah). */
@Composable
private fun HudRoundButton(
    icon: ImageVector,
    label: String,
    size: Float,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .shadow(8.dp, CircleShape, false, Color.Black, Color.Black)
            .clip(CircleShape)
            .border(1.4.dp, if (active) HudInk else HudBorder, CircleShape)
            .clickable(onClick = onClick),
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

@Composable
private fun PanelHandle(
    checksLeft: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp, horizontal = 5.dp)
            .zIndex(20f),
    ) {
        Box(
            Modifier
                .clip(XyPill)
                .shadow(6.dp, XyPill, false, Color.Black, Color.Black)
                .border(1.dp, HudBorderDim, XyPill)
                .padding(vertical = 16.dp, horizontal = 7.dp),
        ) {
            Icon(
                if (checksLeft) XyIcons.ChevronRight else XyIcons.ChevronLeft,
                contentDescription = if (checksLeft) "Buka panel kiri" else "Buka panel kanan",
                tint = HudInk,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/**
 * Pointer tunggal.
 *
 * Urutan prioritas:
 *  1. Bentuk kursor yang DIKIRIM SERVER (panah, tangan, I-beam, resize, ...)
 *     — digambar apa adanya, jadi bentuknya berubah selayaknya pointer desktop.
 *  2. Kalau server tidak mengirim apa-apa: panah/titik bawaan app.
 */
@Composable
private fun XyPointer(
    position: Offset,
    sizeDp: Float,
    style: PointerStyle,
    remote: RemoteCursor?,
    zoom: Float,
) {
    val remoteBitmap = remote?.bitmap
    if (remote != null && remote.visible && remoteBitmap != null) {
        val image = remember(remoteBitmap) { remoteBitmap.asImageBitmap() }
        val scale = zoom.coerceIn(0.35f, 3f)
        Canvas(Modifier.fillMaxSize().zIndex(11f)) {
            val w = (remoteBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val h = (remoteBitmap.height * scale).roundToInt().coerceAtLeast(1)
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(remoteBitmap.width, remoteBitmap.height),
                dstOffset = IntOffset(
                    (position.x - remote.hotX * scale).roundToInt(),
                    (position.y - remote.hotY * scale).roundToInt(),
                ),
                dstSize = IntSize(w, h),
            )
        }
        return
    }

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

// ------------------------------------------------------------------ panel

@Composable
private fun SessionPanel(
    side: PanelSide,
    hostLabel: String,
    statusText: String,
    remoteSize: String,
    zoomPercent: Int,
    onClose: () -> Unit,
    // kiri
    resolution: String = DisplayPrefs.AUTOMATIC,
    rotation: String = "Auto",
    custom: String = "",
    keyboardScale: Float = 1f,
    onKeyboardScaleChange: (Float) -> Unit = {},
    onOverlayShownChange: (Boolean) -> Unit = {},
    onCustomChange: (String) -> Unit = {},
    onCustomApply: () -> Unit = {},
    onResolution: (String) -> Unit = {},
    onRotation: (String) -> Unit = {},
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onFit: () -> Unit = {},
    onZoomActual: () -> Unit = {},
    onScreenshot: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    // kanan
    inputMode: InputMode = InputMode.TRACKPAD,
    onInputModeChange: (InputMode) -> Unit = {},
    pointerVisible: Boolean = true,
    onPointerVisibilityChange: (Boolean) -> Unit = {},
    pointerStyle: PointerStyle = PointerStyle.DOT,
    onPointerStyle: (PointerStyle) -> Unit = {},
    pointerSize: Float = 22f,
    onPointerSize: (Float) -> Unit = {},
    keys: List<HudKey> = emptyList(),
    onAddKey: (Boolean) -> Unit = {},
    onEditKey: (HudKey) -> Unit = {},
    onDeleteKey: (HudKey) -> Unit = {},
    mappingMode: Boolean = false,
    onMappingModeChange: (Boolean) -> Unit = {},
    haptics: Boolean = true,
    onHaptics: (Boolean) -> Unit = {},
    onResetCluster: () -> Unit = {},
    onToggleTrackpad: () -> Unit = {},
    overlayShown: Boolean = false,
    keyboardShown: Boolean = false,
    onKeyboardShownChange: (Boolean) -> Unit = {},
) {
    val align = if (side == PanelSide.RIGHT) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClose)
            .zIndex(30f),
        contentAlignment = align,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.78f)
                .background(HudPanelBg)
                .border(1.dp, HudPanelEdge, RoundedCornerShape(0.dp))
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
                    Text(
                        "$statusText  ·  $zoomPercent%  ·  $remoteSize",
                        color = HudMuted,
                        fontSize = 10.5.sp,
                    )
                }
                PanelChip("Tutup", onClose)
            }

            if (side == PanelSide.RIGHT) {
                PanelSection("Input") {
                    XySegmented(
                        options = InputMode.entries.map { it.title },
                        selectedIndex = inputMode.ordinal,
                        onSelect = { onInputModeChange(InputMode.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PanelHint(InputMode.entries[inputMode.ordinal].detail)
                    if (inputMode == InputMode.TRACKPAD) {
                        XyPillButton(
                            "Pindah ke sentuh langsung",
                            onToggleTrackpad,
                            primary = false,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    // Jalur untuk teks yang tidak enak diketik lewat pemetaan
                    // tombol: tempel dari clipboard HP lalu kirim sebagai unicode.
                    XyPillButton(
                        "Kirim teks ke remote",
                        { textValue = ""; textOpen = true },
                        primary = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
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
                    PanelHint("Ukuran: ${pointerSize.toInt()} dp")
                    Slider(value = pointerSize, onValueChange = onPointerSize, valueRange = 10f..52f)
                }

                PanelSection("Tombol (${keys.size})") {
                    PanelHint(
                        "Terapung: bulat penuh, digeser bebas di layar. Baris atas: " +
                            "nempel di atas keyboard kustom, tidak menutupi taskbar.",
                    )
                    HudLayoutPreview(keys.filter { !it.inToolbar })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton(
                            "Tambah terapung",
                            { onAddKey(false) },
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                        XyPillButton(
                            "Tambah baris atas",
                            { onAddKey(true) },
                            primary = false,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton(
                            if (mappingMode) "Selesai atur" else "Atur posisi",
                            { onMappingModeChange(!mappingMode) },
                            primary = mappingMode,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                        XyPillButton(
                            "Kembalikan bawaan",
                            onResetCluster,
                            primary = false,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val floating = keys.filter { !it.inToolbar }
                    val aux = keys.filter { it.inToolbar }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeyListHeader("Terapung (${floating.size})")
                        if (floating.isEmpty()) {
                            PanelHint("Belum ada tombol terapung.")
                        }
                        floating.forEach { key ->
                            KeyRow(key, onEditKey, onDeleteKey)
                        }
                        KeyListHeader("Baris atas keyboard (${aux.size})")
                        if (aux.isEmpty()) {
                            PanelHint("Belum ada tombol baris atas.")
                        }
                        aux.forEach { key ->
                            KeyRow(key, onEditKey, onDeleteKey)
                        }
                    }
                }

                PanelSection("Keyboard") {
                    PanelHint(
                        "Keyboard HP dipakai untuk mengetik. Toolbar di atasnya " +
                            "punya chip 123 untuk board lengkap; ABC balik ke keyboard HP.",
                    )
                    XyToggleRow(
                        title = "Keyboard HP (IME)",
                        checked = keyboardShown,
                        onCheckedChange = onKeyboardShownChange,
                    )
                    XyToggleRow(
                        title = "Board lengkap (QWERTY + F1.. + numpad)",
                        checked = overlayShown,
                        onCheckedChange = onOverlayShownChange,
                    )
                    PanelHint("Skala board: ${(keyboardScale * 100).toInt()}%")
                    Slider(
                        value = keyboardScale,
                        onValueChange = onKeyboardScaleChange,
                        valueRange = 0.7f..1.6f,
                    )
                }
            } else {
                PanelSection("Layar") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton("Fit", onFit, primary = false, compact = true, modifier = Modifier.weight(1f))
                        XyPillButton("100%", onZoomActual, primary = false, compact = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton("Perkecil", onZoomOut, primary = false, compact = true, modifier = Modifier.weight(1f))
                        XyPillButton("Perbesar", onZoomIn, primary = false, compact = true, modifier = Modifier.weight(1f))
                    }
                    PanelHint("Resolusi remote (kanal DISP): $remoteSize")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        XyField(
                            value = custom,
                            onValueChange = onCustomChange,
                            label = "Kustom (mis. 2160x1350)",
                            modifier = Modifier.weight(1f),
                        )
                        XyPillButton("Pasang", onCustomApply, primary = false, compact = true)
                    }
                    XySegmented(
                        options = DisplayPrefs.rotations,
                        selectedIndex = DisplayPrefs.rotations.indexOf(rotation).coerceAtLeast(0),
                        onSelect = { onRotation(DisplayPrefs.rotations[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PanelHint(
                        "Resolusi dikirim ke server saat menyambung dan saat diubah " +
                            "(/size + dynamic resolution). Server yang menentukan " +
                            "apakah ukurannya diterima.",
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
                    if (coreInfo.isNotEmpty()) {
                        coreInfo.forEach { line ->
                            Text(line, color = HudMuted, fontSize = 10.5.sp)
                        }
                        XyPillButton(
                            "Salin info teknis",
                            {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as? ClipboardManager
                                cm?.setPrimaryClip(
                                    android.content.ClipData.newPlainText(
                                        "XyDesk Remote",
                                        coreInfo.joinToString("\n"),
                                    )
                                )
                                Toast.makeText(context, "Info tersalin", Toast.LENGTH_SHORT).show()
                            },
                            primary = false,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                PanelSection("Tips") {
                    PanelHint(
                        "Toolbar di atas keyboard HP hanya muncul saat keyboard " +
                            "tampil. Tombol yang tidak mau ikut di toolbar: ubah " +
                            "jadi 'Hanya di layar' di daftar tombol.",
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun PanelRowShape() = RoundedCornerShape(9.dp)

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
    Text(text, color = HudMuted, fontSize = 10.5.sp)
}

@Composable
private fun PanelSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            color = Color(0xFFAAB3BB),
            fontSize = 10.sp,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun KeyListHeader(text: String) {
    Text(
        text,
        color = HudMuted,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun KeyRow(
    key: HudKey,
    onEditKey: (HudKey) -> Unit,
    onDeleteKey: (HudKey) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(XyPill)
            .clickable { onEditKey(key) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            key.label,
            color = HudInk,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${key.action.title} · ${key.size.toInt()} dp",
            color = HudMuted,
            fontSize = 9.5.sp,
        )
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(1.dp, HudBorderDim, CircleShape)
                .clickable { onDeleteKey(key) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                XyIcons.Close,
                contentDescription = "Hapus tombol",
                tint = HudMuted,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
