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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyNoticeState
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XySlider
import id.xydesk.remote.ui.components.XyToggleRow
import id.xydesk.remote.ui.theme.XyPill
import kotlin.math.roundToInt

/**
 * Warna HUD sesi. Ini SATU-SATUNYA bagian yang sengaja tidak ikut tema:
 * tombol dan banner HUD duduk di atas gambar remote (gelap/terang apa pun
 * isinya), jadi kontrasnya harus tetap. Panel dan dialog ikut tema app.
 */
private val HudInk = Color(0xFFF1F4F6)
private val HudMuted = Color(0xFFC9D1D8)
private val HudEdge = Color(0xD9FFFFFF)
private val HudEdgeDim = Color(0x7AFFFFFF)

private enum class PanelSide { RIGHT, LEFT }

/**
 * Kontrol sesi.
 *
 * Model kontrol (ronde 5): TIDAK ADA bar toolbar dan TIDAK ADA keyboard
 * virtual. Kontrolnya satu-satu: tiap aksi = satu tombol bulat yang bisa
 * digeser ke mana saja, diubah ukurannya, diganti aksinya, ditambah, dan
 * dihapus. Titik masuknya tiga, semuanya jalur yang sama:
 *  - "Atur posisi" dari panel (atau tahan lama tombol mana pun) → editor tombol
 *  - chip "+" di banner atur posisi → tambah tombol baru
 *  - panel kanan bagian "Tombol" → daftar lengkap + tambah + kembalikan bawaan
 *
 * Panel (kiri/kanan) hanya setelan; tidak ada tombol aksi sesi di dalamnya
 * selain yang memang aksi (screenshot, putus, kirim teks).
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
    keys: List<HudKey>,
    onKeysChange: (List<HudKey>) -> Unit,
    mappingMode: Boolean,
    onMappingModeChange: (Boolean) -> Unit,
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
    onToggleKeyboard: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    lastClipboard: String? = null,
    onSendPhoneClipboard: () -> Unit = {},
    onPasteRemoteClipboard: () -> Unit = {},
    onSendText: (String) -> Unit,
    coreInfo: List<String> = emptyList(),
    notice: XyNoticeState,
) {
    val context = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    val prefs = remember { SessionPrefs(context) }
    var rightOpen by remember { mutableStateOf(false) }
    var leftOpen by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HudKey?>(null) }
    var pointerSize by remember { mutableFloatStateOf(prefs.pointerSize) }
    var pointerStyle by remember { mutableStateOf(prefs.pointerStyle) }
    var haptics by remember { mutableStateOf(prefs.haptics) }
    var autoFit by remember { mutableStateOf(prefs.autoFit) }
    var plate by remember { mutableStateOf(prefs.hudPlate) }
    var exitArmed by remember { mutableStateOf(false) }
    var exitArmedAt by remember { mutableLongStateOf(0L) }
    var resolution by remember { mutableStateOf(DisplayPrefs.resolution(context, deviceId)) }
    var rotation by remember { mutableStateOf(DisplayPrefs.rotation(context, deviceId)) }
    var custom by remember { mutableStateOf("") }
    var textOpen by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf("") }

    fun copyCoreInfo() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(
            ClipData.newPlainText("XyDesk Remote", coreInfo.joinToString("\n"))
        )
        notice.show(xyNow("Info teknis tersalin", "Technical info copied"))
    }

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
        )
        onKeysChange(keys + key)
        notice.show(
            xyNow(
                "Tombol \"{0}\" ditambahkan — geser ke posisi yang kamu mau",
                "Button \"{0}\" added — drag it where you want",
                key.label,
            ),
        )
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

        // Tombol kontrol: satu-satu, bebas digeser, ukuran & aksi per tombol.
        HudKeyLayer(
            keys = keys,
            mappingMode = mappingMode,
            plate = plate,
            onMove = { id, x, y ->
                onKeysChange(keys.map { if (it.id == id) it.copy(x = x, y = y) else it })
            },
            onPhase = { key, phase ->
                haptic()
                onPhase(key, phase)
            },
            onEdit = { editing = it },
            onRequestEditMode = {
                onMappingModeChange(true)
                notice.show(xyNow("Atur posisi menyala — geser tombol, lalu tekan Selesai", "Layout edit is on — drag the buttons, then press Done"))
            },
        )

        // Rail tetap: keyboard HP bisa dibuka/ditutup kapan saja, dan keluar
        // butuh dua kali ketuk supaya tidak kepencet waktu main.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 96.dp)
                .zIndex(24f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RailButton(
                icon = XyIcons.Keyboard,
                active = keyboardShown,
                description = if (keyboardShown) xy("Tutup keyboard HP", "Hide phone keyboard") else xy("Buka keyboard HP", "Show phone keyboard"),
                plate = plate,
            ) { onToggleKeyboard() }
            RailButton(
                icon = XyIcons.Monitor,
                active = false,
                description = xy("Sesi lain (buka home)", "Other sessions (open home)"),
                plate = plate,
            ) { onOpenHome() }
            RailButton(
                icon = XyIcons.Power,
                active = exitArmed,
                description = if (exitArmed) xy("Tekan sekali lagi untuk putus", "Press again to disconnect") else xy("Putuskan sesi (2x)", "Disconnect (2 taps)"),
                plate = plate,
            ) {
                val now = System.currentTimeMillis()
                if (exitArmed && now - exitArmedAt < 2_500L) {
                    exitArmed = false
                    onDisconnect()
                } else {
                    exitArmed = true
                    exitArmedAt = now
                    notice.show(xyNow("Tekan sekali lagi untuk memutus sesi", "Press again to disconnect the session"))
                }
            }
        }

        if (mappingMode) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)) {
                HudMappingBanner(
                    onAdd = { pickerOpen = true },
                    onDone = { onMappingModeChange(false) },
                )
            }
        }

        if (textOpen) {
            XyOverlay(title = xy("Kirim teks", "Send text"), onDismiss = { textOpen = false }) {
                Text(
                    xy(
                        "Teks dikirim sebagai unicode ke jendela remote yang sedang fokus.",
                        "Text is sent as unicode to the focused remote window.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                XyField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = xy("Teks", "Text"),
                    hint = xy("mis. password, alamat URL", "e.g. password, a URL"),
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton(
                        xy("Tempel", "Paste"),
                        {
                            val paste = clipboardText()
                            textValue = paste
                            if (paste.isEmpty()) notice.show(xyNow("Clipboard HP kosong", "Phone clipboard is empty"))
                        },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        xy("Kirim", "Send"),
                        {
                            val t = textValue
                            if (t.isEmpty()) {
                                notice.show(xyNow("Belum ada teks", "No text yet"))
                            } else {
                                onSendText(t)
                                notice.show(xyNow("Teks terkirim", "Text sent"))
                                textOpen = false
                            }
                        },
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                XyPillButton(
                    xy("Selesai", "Done"),
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
                autoFit = autoFit,
                onAutoFitChange = { autoFit = it; prefs.autoFit = it },
                onCustomChange = { custom = it },
                onCustomApply = {
                    val parsed = DisplayPrefs.parseCustom(custom)
                    if (parsed == null) {
                        notice.show(xyNow("Format resolusi harus WxH, mis. 1920x1080", "Resolution must be WxH, e.g. 1920x1080"))
                    } else {
                        resolution = parsed
                        DisplayPrefs.setResolution(context, deviceId, parsed)
                        onResolutionChange(parsed)
                    }
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
                coreInfo = coreInfo,
                onCopyCoreInfo = { copyCoreInfo() },
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
                onAddKey = { pickerOpen = true },
                onEditKey = { editing = it },
                onDeleteKey = { onKeysChange(keys.filterNot { k -> k.id == it.id }) },
                mappingMode = mappingMode,
                onMappingModeChange = onMappingModeChange,
                plate = plate,
                onPlate = { plate = it; prefs.hudPlate = it },
                haptics = haptics,
                onHaptics = { haptics = it; prefs.haptics = it },
                onResetCluster = onResetCluster,
                onToggleTrackpad = onToggleTrackpad,
                onSendTextClick = { textValue = ""; textOpen = true },
                keyboardShown = keyboardShown,
                onKeyboardShownChange = onKeyboardShownChange,
                lastClipboard = lastClipboard,
                onSendPhoneClipboard = onSendPhoneClipboard,
                onPasteRemoteClipboard = onPasteRemoteClipboard,
                onClose = { rightOpen = false },
            )
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
                notice.show(xyNow("Tombol dihapus", "Button deleted"))
            },
            onDismiss = { editing = null },
        )
    }
}

/** Tombol rail sesi: latar mengikuti rasa tombol HUD, ikon selalu terbaca. */
@Composable
private fun RailButton(
    icon: ImageVector,
    active: Boolean,
    description: String,
    plate: HudPlate,
    onClick: () -> Unit,
) {
    val pal = hudPalette(plate)
    Box(
        Modifier
            .size(44.dp)
            .shadow(8.dp, CircleShape, false, Color.Black, Color.Black)
            .clip(CircleShape)
            .background(pal.plate)
            .border(
                if (active) 2.dp else 1.2.dp,
                if (active) Color(0xFFFFFFFF) else pal.border,
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = pal.ink, modifier = Modifier.size(20.dp))
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
                .background(Color(0xD90B0D10))
                .border(1.dp, HudEdgeDim, XyPill)
                .padding(vertical = 16.dp, horizontal = 7.dp),
        ) {
            Icon(
                if (checksLeft) XyIcons.ChevronRight else XyIcons.ChevronLeft,
                contentDescription = if (checksLeft) xy("Buka panel kiri", "Open left panel")
                    else xy("Buka panel kanan", "Open right panel"),
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
    autoFit: Boolean = true,
    onAutoFitChange: (Boolean) -> Unit = {},
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
    coreInfo: List<String> = emptyList(),
    onCopyCoreInfo: () -> Unit = {},
    // kanan
    inputMode: InputMode = InputMode.TRACKPAD,
    onInputModeChange: (InputMode) -> Unit = {},
    pointerVisible: Boolean = true,
    onPointerVisibilityChange: (Boolean) -> Unit = {},
    pointerStyle: PointerStyle = PointerStyle.ARROW,
    onPointerStyle: (PointerStyle) -> Unit = {},
    pointerSize: Float = 22f,
    onPointerSize: (Float) -> Unit = {},
    keys: List<HudKey> = emptyList(),
    onAddKey: () -> Unit = {},
    onEditKey: (HudKey) -> Unit = {},
    onDeleteKey: (HudKey) -> Unit = {},
    mappingMode: Boolean = false,
    onMappingModeChange: (Boolean) -> Unit = {},
    plate: HudPlate = HudPlate.DARK,
    onPlate: (HudPlate) -> Unit = {},
    lastClipboard: String? = null,
    onSendPhoneClipboard: () -> Unit = {},
    onPasteRemoteClipboard: () -> Unit = {},
    haptics: Boolean = true,
    onHaptics: (Boolean) -> Unit = {},
    onResetCluster: () -> Unit = {},
    onToggleTrackpad: () -> Unit = {},
    onSendTextClick: () -> Unit = {},
    keyboardShown: Boolean = false,
    onKeyboardShownChange: (Boolean) -> Unit = {},
) {
    val align = if (side == PanelSide.RIGHT) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(onClick = onClose)
            .zIndex(30f),
        contentAlignment = align,
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.78f)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(0.dp))
                .clickable { }
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        hostLabel,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$statusText  ·  $zoomPercent%  ·  $remoteSize",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                PanelChip(xy("Tutup", "Close"), onClose)
            }

            if (side == PanelSide.RIGHT) {
                PanelSection(xy("Input", "Input")) {
                    XySegmented(
                        options = InputMode.entries.map { xy(it.title, it.titleEn) },
                        selectedIndex = inputMode.ordinal,
                        onSelect = { onInputModeChange(InputMode.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PanelHint(xy(InputMode.entries[inputMode.ordinal].detail, InputMode.entries[inputMode.ordinal].detailEn))
                    if (inputMode == InputMode.TRACKPAD) {
                        XyPillButton(
                            xy("Pindah ke sentuh langsung", "Switch to direct touch"),
                            onToggleTrackpad,
                            primary = false,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    XyToggleRow(
                        title = xy("Keyboard HP (IME)", "Phone keyboard (IME)"),
                        checked = keyboardShown,
                        onCheckedChange = onKeyboardShownChange,
                    )
                    PanelHint(
                        xy(
                            "Mengetik lewat keyboard HP. Untuk membuka cepat, pasang " +
                                "tombol HUD \"Buka keyboard\".",
                            "Typing uses the phone keyboard. For quick access, add a \"Show " +
                                "keyboard\" HUD button.",
                        ),
                    )
                    XyPillButton(
                        xy("Kirim teks ke remote", "Send text to remote"),
                        onSendTextClick,
                        primary = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PanelSection(xy("Clipboard", "Clipboard")) {
                    PanelHint(
                        xy(
                            "Copy di HP langsung terkirim ke remote; copy di remote " +
                                "langsung masuk clipboard HP.",
                            "Copying on the phone goes straight to the remote; copying " +
                                "on the remote lands in the phone clipboard.",
                        ),
                    )
                    XyPillButton(
                        xy("Kirim clipboard HP ke remote", "Send phone clipboard to remote"),
                        onSendPhoneClipboard,
                        primary = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PanelHint(
                        if (lastClipboard.isNullOrEmpty()) {
                            xy("Dari remote: belum ada", "From remote: nothing yet")
                        } else {
                            xy(
                                "Dari remote: {0}",
                                "From remote: {0}",
                                lastClipboard.take(60).replace("\n", " "),
                            )
                        },
                    )
                    XyPillButton(
                        xy("Salin teks remote ke HP", "Copy remote text to phone"),
                        onPasteRemoteClipboard,
                        primary = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                PanelSection(xy("Pointer", "Pointer")) {
                    XyToggleRow(
                        title = xy("Tampilkan pointer", "Show pointer"),
                        checked = pointerVisible,
                        onCheckedChange = onPointerVisibilityChange,
                    )
                    PanelHint(
                        xy(
                            "Bentuk pointer mengikuti kursor yang dikirim server (panah, tangan, I-beam).",
                            "Pointer shape follows the cursor sent by the server (arrow, hand, I-beam).",
                        ),
                    )
                    XySegmented(
                        options = PointerStyle.entries.map { xy(it.title, it.titleEn) },
                        selectedIndex = pointerStyle.ordinal,
                        onSelect = { onPointerStyle(PointerStyle.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PanelHint(
                        xy("Ukuran cadangan: {0} dp", "Fallback size: {0} dp", pointerSize.toInt()),
                    )
                    XySlider(value = pointerSize, onValueChange = onPointerSize, valueRange = 10f..52f)
                }

                // ---- kontrol: satu tombol satu aksi; geser cuma di mode atur ----
                PanelSection(xy("Tombol kontrol ({0})", "Control buttons ({0})", keys.size)) {
                    PanelHint(
                        if (mappingMode) {
                            xy(
                                "Mode atur posisi MENYALA: geser tombol ke tempat yang kamu mau, " +
                                    "ketuk tombol untuk ubah aksi/ukuran, lalu tekan Selesai.",
                                "Layout mode is ON: drag buttons where you want them, tap a " +
                                    "button to change its action/size, then press Done.",
                            )
                        } else {
                            xy(
                                "Tombol terkunci: tidak bisa kegeser waktu dipakai. " +
                                    "Tekan \"Atur posisi\" (atau tahan lama satu tombol) untuk memindahkan.",
                                "Buttons are locked: they cannot move while in use. Press " +
                                    "\"Edit layout\" (or long-press a button) to move them.",
                            )
                        },
                    )
                    XyPillButton(
                        if (mappingMode) xy("Selesai atur posisi", "Done editing layout") else xy("Atur posisi & ukuran", "Edit layout & size"),
                        { onMappingModeChange(!mappingMode) },
                        primary = mappingMode,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HudLayoutPreview(keys)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton(
                            xy("Tambah tombol", "Add button"),
                            onAddKey,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                        XyPillButton(
                            xy("Kembalikan bawaan", "Restore default"),
                            onResetCluster,
                            primary = false,
                            compact = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PanelHint(xy("Latar tombol — pakai gelap kalau desktop remote-nya putih", "Button plate — pick dark if the remote desktop is white"))
                    XySegmented(
                        options = HudPlate.entries.map { xy(it.title, it.titleEn) },
                        selectedIndex = plate.ordinal,
                        onSelect = { onPlate(HudPlate.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (keys.isEmpty()) {
                            PanelHint(
                                xy(
                                    "Belum ada tombol. Tekan \"Tambah tombol\".",
                                    "No buttons yet. Press \"Add button\".",
                                ),
                            )
                        }
                        keys.forEach { key -> KeyRow(key, onEditKey, onDeleteKey) }
                    }
                    XyToggleRow(
                        title = xy("Getaran saat tombol ditekan", "Haptic feedback on button press"),
                        checked = haptics,
                        onCheckedChange = onHaptics,
                    )
                }
            } else {
                PanelSection(xy("Ukuran tampilan", "Display size")) {
                    PanelHint(
                        xy(
                            "Zoom mengubah besar gambar di layar HP, resolusi di " +
                                "bawah mengubah ukuran desktop remote-nya.",
                            "Zoom changes how big the picture is on the phone; the " +
                                "resolution below changes the remote desktop size.",
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton(xy("Perkecil", "Zoom out"), onZoomOut, primary = false, compact = true, modifier = Modifier.weight(1f))
                        XyPillButton(xy("Perbesar", "Zoom in"), onZoomIn, primary = false, compact = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        XyPillButton(xy("Muat semua", "Fit all"), onFit, primary = false, compact = true, modifier = Modifier.weight(1f))
                        XyPillButton("100%", onZoomActual, primary = false, compact = true, modifier = Modifier.weight(1f))
                    }
                    XyToggleRow(
                        title = xy("Muat seluruh desktop", "Fit whole desktop"),
                        checked = autoFit,
                        onCheckedChange = onAutoFitChange,
                    )
                    PanelHint(
                        xy(
                            "Menyala = seluruh desktop (termasuk taskbar) selalu masuk " +
                                "layar setiap sesi dibuka atau resolusi berubah.",
                            "On = the whole desktop (taskbar included) always fits the " +
                                "screen whenever a session opens or the resolution changes.",
                        ),
                    )
                }

                PanelSection(xy("Resolusi desktop (16:9)", "Remote desktop resolution (16:9)")) {
                    PanelHint(xy("Desktop sekarang: {0}", "Desktop now: {0}", remoteSize))
                    DisplayPrefs.resolutionGroups.forEach { group ->
                        PanelHint(xy(group.id, group.en))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.items.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { option ->
                                        val value = option.value
                                        XyPillButton(
                                            text = xy(option.id, option.en),
                                            onClick = { onResolution(value) },
                                            primary = value == resolution,
                                            compact = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
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
                            label = xy("Kustom WxH", "Custom WxH"),
                            hint = xy("mis. 1920x1080", "e.g. 1920x1080"),
                            modifier = Modifier.weight(1f),
                        )
                        XyPillButton(xy("Pasang", "Apply"), onCustomApply, primary = false, compact = true)
                    }
                    XySegmented(
                        options = DisplayPrefs.rotations,
                        selectedIndex = DisplayPrefs.rotations.indexOf(rotation).coerceAtLeast(0),
                        onSelect = { onRotation(DisplayPrefs.rotations[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PanelHint(
                        xy(
                            "Resolusi dikirim ke server saat menyambung dan saat diubah " +
                                "(/size + dynamic resolution). Setelah berubah, tampilan " +
                                "langsung dimuat ulang supaya semuanya kelihatan.",
                            "The resolution is sent to the server on connect and whenever " +
                                "it changes (/size + dynamic resolution). After a change the " +
                                "view refits so nothing is cut off.",
                        ),
                    )
                }

                PanelSection(xy("Sesi & keluar", "Session & exit")) {
                    PanelHint(
                        xy(
                            "Tombol keluar di kanan bawah: dua kali ketuk untuk memutus sesi.",
                            "Exit button at the bottom right: double tap to disconnect.",
                        ),
                    )
                    XyPillButton(
                        xy("Ambil screenshot", "Take screenshot"),
                        onScreenshot,
                        primary = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    XyPillButton(
                        xy("Putuskan sesi", "Disconnect"),
                        onDisconnect,
                        primary = false,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (coreInfo.isNotEmpty()) {
                        coreInfo.forEach { line ->
                            Text(
                                line,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                            )
                        }
                        XyPillButton(
                            xy("Salin info teknis", "Copy technical info"),
                            onCopyCoreInfo,
                            primary = false,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
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
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, XyPill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
    }
}

@Composable
private fun PanelHint(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
}

@Composable
private fun PanelSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun KeyRow(
    key: HudKey,
    onEdit: (HudKey) -> Unit,
    onDelete: (HudKey) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(9.dp))
            .clickable { onEdit(key) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                key.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                xy(
                    "{0} · {1} · {2} dp · {3}%,{4}%",
                    "{0} · {1} · {2} dp · {3}%,{4}%",
                    xy(key.kind.title, key.kind.titleEn),
                    xy(key.action.title, key.action.titleEn),
                    key.size.toInt(),
                    (key.x * 100).toInt(),
                    (key.y * 100).toInt(),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .clip(XyPill)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, XyPill)
                .clickable { onDelete(key) }
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(xy("Hapus", "Delete"), color = MaterialTheme.colorScheme.onSurface, fontSize = 10.5.sp)
        }
    }
}
