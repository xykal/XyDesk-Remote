package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.components.XySlider
import id.xydesk.remote.ui.theme.XyPill
import kotlin.math.roundToInt

/** Fase aksi tombol: layer cuma melaporkan fase, arti aksinya di screen. */
enum class HudPhase { DOWN, UP, TAP }

/**
 * Warna tombol kontrol di atas gambar remote. Sengaja tetap (tidak ikut tema
 * app) karena latarnya adalah gambar sesi, bukan permukaan app.
 *
 * Tiga rasa: transparan, gelap lembut, dan gelap tegas. Pelat terang sengaja
 * tidak dipakai agar ikon tidak mendapat cakram putih saat app bertema gelap.
 */
private val HudBorder = Color(0xD9FFFFFF)
private val HudInk = Color(0xFFF1F4F6)

/** Warna per rasa latar tombol. */
data class HudPalette(val border: Color, val ink: Color, val plate: Color)

fun hudPalette(plate: HudPlate): HudPalette = when (plate) {
    HudPlate.NONE -> HudPalette(HudBorder, HudInk, Color.Transparent)
    HudPlate.DARK -> HudPalette(Color(0xF2FFFFFF), Color(0xFFF7F9FA), Color(0xCC0B0D10))
    HudPlate.LIGHT -> HudPalette(Color(0xBFFFFFFF), Color(0xFFF1F4F6), Color(0x700B0D10))
}

/**
 * Lapisan tombol kontrol sesi.
 *
 * Tiap tombol berbentuk lingkaran, satu aksi, terkunci saat digunakan dan bisa
 * digeser dalam mode atur posisi. Ukuran dan aksinya dapat diubah. Di mode
 * atur posisi tombol diberi ring +
 * grid bantu supaya jelas bisa digeser.
 */
@Composable
fun HudKeyLayer(
    keys: List<HudKey>,
    mappingMode: Boolean,
    plate: HudPlate,
    latchedKeys: Set<String>,
    onMove: (id: String, x: Float, y: Float) -> Unit,
    onPhase: (HudKey, HudPhase) -> Unit,
    scrollSpeed: Float,
    onScrollUnits: (Int) -> Unit,
    onEdit: (HudKey) -> Unit,
    onStickAxis: (HudKey, Float, Float) -> Unit = { _, _, _ -> },
) {
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(12f)) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        keys.forEach { key ->
            androidx.compose.runtime.key(key.id) {
                val sizePx = with(density) { key.size.dp.toPx() }
                val maxX = (widthPx - sizePx).coerceAtLeast(1f)
                val maxY = (heightPx - sizePx).coerceAtLeast(1f)
                HudKeyButton(
                    key = key,
                    mappingMode = mappingMode,
                    plate = plate,
                    latched = (key.action == HudAction.TOGGLE || key.action == HudAction.ONE_SHOT) && key.id in latchedKeys,
                    maxX = maxX,
                    maxY = maxY,
                    onDrag = { x, y -> onMove(key.id, x, y) },
                    onPhase = { phase -> onPhase(key, phase) },
                    scrollSpeed = scrollSpeed,
                    onScrollUnits = onScrollUnits,
                    onEdit = { onEdit(key) },
                    onStickAxis = { x, y -> onStickAxis(key, x, y) },
                    modifier = Modifier
                        .offset {
                            IntOffset((key.x * maxX).roundToInt(), (key.y * maxY).roundToInt())
                        }
                        .zIndex(12f),
                )
            }
        }
    }
}

@Composable
private fun HudKeyButton(
    key: HudKey,
    mappingMode: Boolean,
    plate: HudPlate,
    latched: Boolean,
    maxX: Float,
    maxY: Float,
    onDrag: (Float, Float) -> Unit,
    onPhase: (HudPhase) -> Unit,
    scrollSpeed: Float,
    onScrollUnits: (Int) -> Unit,
    onEdit: () -> Unit,
    onStickAxis: (Float, Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var pressed by remember(key.id) { mutableStateOf(false) }

    // Posisi dan callback selama geser dibaca lewat rememberUpdatedState agar
    // blok pointerInput yang hidup lintas-rekomposisi tidak memakai daftar
    // tombol lama (yang sebelumnya membuat posisi tombol lain ikut ter-reset).
    val latest = rememberUpdatedState(key)
    val latestMaxX = rememberUpdatedState(maxX)
    val latestMaxY = rememberUpdatedState(maxY)
    val latestOnDrag = rememberUpdatedState(onDrag)
    val latestOnPhase = rememberUpdatedState(onPhase)
    val latestOnEdit = rememberUpdatedState(onEdit)
    val latestScrollUnits = rememberUpdatedState(onScrollUnits)
    val dragPos = remember(key.id) { mutableStateOf<Offset?>(null) }
    // Simpangan knob joystick dalam piksel; selalu balik ke (0,0) saat dilepas.
    val stickOffset = remember(key.id) { mutableStateOf(Offset.Zero) }

    val pal = hudPalette(plate)
    val density = LocalDensity.current
    val stickRadiusPx = with(density) { (key.size.dp * 0.31f).toPx() }
    val latestStickRadius = rememberUpdatedState(stickRadiusPx)
    val latestOnStick = rememberUpdatedState(onStickAxis)
    val ring = when {
        mappingMode -> Color(0xFF83D7FF)
        latched -> Color(0xFFB9F0D9)
        else -> pal.border
    }
    val circlePlate = when {
        pressed || latched -> Color(0xE62B624F)
        else -> pal.plate
    }

    Box(
        modifier
            .size(key.size.dp)
            .clip(CircleShape)
            .background(circlePlate)
            .border(
                if (pressed || latched || mappingMode) 2.dp else 1.2.dp,
                ring,
                CircleShape,
            )
            .pointerInput(key.id, mappingMode, key.action, key.kind, key.keyCode, key.shift, key.combo, key.size, plate, scrollSpeed) {
                awaitEachGesture {
                    val slop = viewConfiguration.touchSlop
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val scrollAccumulator = ScrollWheelAccumulator()
                    pressed = true
                    var travelled = 0f
                    var dragged = false
                    // Edit mode hanya diaktifkan lewat tombol panel, bukan tahan
                    // lama. TAP pada mouse/key tetap menahan aksi selama jari turun.
                    val tapCanHold = !mappingMode && key.action == HudAction.TAP &&
                        key.kind in setOf(
                            HudKind.MOUSE_LEFT, HudKind.MOUSE_RIGHT,
                            HudKind.MOUSE_MIDDLE, HudKind.KEY,
                        )
                    val holdActive = !mappingMode && key.action == HudAction.HOLD
                    val heldOnPress = tapCanHold || holdActive
                    if (heldOnPress) latestOnPhase.value(HudPhase.DOWN)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val delta = change.position - change.previousPosition
                        travelled += delta.getDistance()
                        if (shouldStartHudButtonGesture(travelled, slop, mappingMode, key.kind)) {
                            dragged = true
                            // Geser hanya berlaku di mode atur posisi.
                            if (mappingMode) {
                                val base = dragPos.value
                                    ?: Offset(latest.value.x, latest.value.y)
                                val nx = (base.x + delta.x / latestMaxX.value).coerceIn(0f, 1f)
                                val ny = (base.y + delta.y / latestMaxY.value).coerceIn(0f, 1f)
                                dragPos.value = Offset(nx, ny)
                                latestOnDrag.value(nx, ny)
                            } else if (key.kind == HudKind.SCROLL_SLIDER) {
                                scrollAccumulator.consume(delta.y, scrollSpeed)
                                    .takeIf { it != 0 }
                                    ?.let(latestScrollUnits.value)
                            } else if (key.kind == HudKind.PAD_STICK) {
                                val radius = latestStickRadius.value
                                val next = clampStickOffset(
                                    stickOffset.value.x + delta.x,
                                    stickOffset.value.y + delta.y,
                                    radius,
                                )
                                stickOffset.value = Offset(next[0], next[1])
                                val axis = stickAxis(next[0], next[1], radius)
                                latestOnStick.value(axis[0], axis[1])
                            }
                            change.consume()
                        }
                    }

                    pressed = false
                    dragPos.value = null
                    // Joystick harus benar-benar netral saat jari lepas, kalau
                    // tidak kursor remote terus melaju.
                    if (key.kind == HudKind.PAD_STICK && stickOffset.value != Offset.Zero) {
                        stickOffset.value = Offset.Zero
                        latestOnStick.value(0f, 0f)
                    }
                    if (heldOnPress) latestOnPhase.value(HudPhase.UP)

                    // Di mode atur posisi, ketuk = buka editor tombol itu.
                    if (mappingMode) {
                        if (!dragged) latestOnEdit.value()
                        return@awaitEachGesture
                    }
                    if (dragged) return@awaitEachGesture
                    when (key.action) {
                        HudAction.HOLD -> Unit
                        HudAction.TOGGLE, HudAction.ONE_SHOT -> latestOnPhase.value(HudPhase.TAP)
                        HudAction.TAP -> if (!tapCanHold) latestOnPhase.value(HudPhase.TAP)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (key.kind == HudKind.PAD_STICK) {
            Box(
                Modifier
                    .offset { IntOffset(stickOffset.value.x.roundToInt(), stickOffset.value.y.roundToInt()) }
                    .size(key.size.dp * 0.42f)
                    .clip(CircleShape)
                    .background(pal.ink.copy(alpha = 0.8f)),
            )
        } else {
            HudKeyGlyph(key, pal.ink)
        }
    }
}

/** Peta ikon vektor untuk setiap tombol HUD yang punya representasi ikon visual. */
internal fun hudIconFor(key: HudKey): ImageVector? =
    hudIconFor(key.kind, key.keyCode, key.combo, key.label)

internal fun hudIconFor(
    kind: HudKind,
    keyCode: Int = 0,
    combo: List<Int> = emptyList(),
    label: String = "",
): ImageVector? = when (kind) {
    HudKind.MOUSE_LEFT -> XyIcons.ClickLeft
    HudKind.MOUSE_RIGHT -> XyIcons.ClickRight
    HudKind.MOUSE_MIDDLE -> XyIcons.ClickMiddle
    HudKind.MOUSE_SWAP -> XyIcons.MouseSwap
    HudKind.SCROLL_UP -> XyIcons.ScrollUp
    HudKind.SCROLL_DOWN -> XyIcons.ScrollDown
    HudKind.SCROLL_SLIDER -> XyIcons.ScrollSlide
    HudKind.PAD_STICK -> XyIcons.Sliders
    HudKind.INPUT_SWITCH -> XyIcons.Swap
    HudKind.KEYBOARD -> XyIcons.Keyboard
    HudKind.KEY -> when (keyCode) {
        android.view.KeyEvent.KEYCODE_META_LEFT,
        android.view.KeyEvent.KEYCODE_META_RIGHT -> XyIcons.Windows
        android.view.KeyEvent.KEYCODE_DPAD_UP -> XyIcons.ArrowUp
        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> XyIcons.ArrowDown
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> XyIcons.ArrowLeft
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> XyIcons.ArrowRight
        android.view.KeyEvent.KEYCODE_ENTER,
        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> XyIcons.EnterKey
        android.view.KeyEvent.KEYCODE_DEL -> XyIcons.BackspaceKey
        android.view.KeyEvent.KEYCODE_TAB -> XyIcons.TabKey
        android.view.KeyEvent.KEYCODE_SPACE -> XyIcons.SpaceKey
        android.view.KeyEvent.KEYCODE_SHIFT_LEFT,
        android.view.KeyEvent.KEYCODE_SHIFT_RIGHT -> XyIcons.ShiftKey
        android.view.KeyEvent.KEYCODE_MENU -> XyIcons.Menu
        android.view.KeyEvent.KEYCODE_SYSRQ -> XyIcons.Shot
        else -> if (label.startsWith("Win", ignoreCase = true)) XyIcons.Windows else null
    }
    HudKind.COMBO -> when {
        combo == listOf(android.view.KeyEvent.KEYCODE_META_LEFT, android.view.KeyEvent.KEYCODE_L) -> XyIcons.Lock
        combo == listOf(android.view.KeyEvent.KEYCODE_META_LEFT, android.view.KeyEvent.KEYCODE_D) -> XyIcons.Monitor
        combo == listOf(android.view.KeyEvent.KEYCODE_META_LEFT, android.view.KeyEvent.KEYCODE_E) -> XyIcons.Folder
        combo == listOf(android.view.KeyEvent.KEYCODE_META_LEFT, android.view.KeyEvent.KEYCODE_I) -> XyIcons.Gear
        combo == listOf(android.view.KeyEvent.KEYCODE_META_LEFT, android.view.KeyEvent.KEYCODE_TAB) ||
            combo == listOf(android.view.KeyEvent.KEYCODE_ALT_LEFT, android.view.KeyEvent.KEYCODE_TAB) -> XyIcons.Grid
        combo == listOf(
            android.view.KeyEvent.KEYCODE_META_LEFT,
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT,
            android.view.KeyEvent.KEYCODE_S,
        ) -> XyIcons.Shot
        combo == listOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_C) -> XyIcons.Copy
        combo == listOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_V) -> XyIcons.Clip
        combo == listOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_Z) -> XyIcons.Undo
        combo == listOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_Y) -> XyIcons.Redo
        combo == listOf(android.view.KeyEvent.KEYCODE_CTRL_LEFT, android.view.KeyEvent.KEYCODE_F) -> XyIcons.Search
        combo == listOf(android.view.KeyEvent.KEYCODE_ALT_LEFT, android.view.KeyEvent.KEYCODE_F4) -> XyIcons.Close
        else -> null
    }
    HudKind.MACRO -> when {
        label.equals("CMD", ignoreCase = true) ||
            label.equals("PShell", ignoreCase = true) ||
            label.equals("Terminal", ignoreCase = true) -> XyIcons.Terminal
        else -> null
    }
}

/** Isi tombol: utamakan ikon vektor untuk semua tombol yang punya ikon (termasuk Windows, Mouse, Panah, dll). */
@Composable
internal fun HudKeyGlyph(key: HudKey, tint: Color, boxDp: Float = key.size) {
    val icon: ImageVector? = hudIconFor(key.kind, key.keyCode, key.combo, key.label)
    if (icon != null) {
        Icon(
            icon,
            contentDescription = key.label,
            tint = tint,
            modifier = Modifier.size((boxDp * 0.44f).coerceIn(15f, 24f).dp),
        )
        return
    }
    Text(
        text = key.label,
        color = tint,
        fontSize = (boxDp * 0.22f).coerceIn(10f, 16f).sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

internal fun Modifier.consumeBackgroundPointer(onTap: () -> Unit): Modifier =
    this
        .pointerInput(onTap) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                var moved = false
                val slop = viewConfiguration.touchSlop
                var total = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change ->
                        total += (change.position - change.previousPosition).getDistance()
                        if (total > slop) moved = true
                        change.consume()
                    }
                    if (event.changes.none { it.pressed }) break
                }
                if (!moved) onTap()
            }
        }

// ------------------------------------------------------------------ editor

/**
 * Dialog tambah tombol: satu katalog per grup (Kombinasi, F1-F12, Single Key,
 * Numpad, Huruf A-Z, Modifier, Mouse & Scroll) plus pencarian cepat.
 */
@Composable
fun HudKeyPicker(
    onPick: (HudKeyOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var group by remember { mutableStateOf(HudKeyCatalog.groups.first().first) }
    var search by remember { mutableStateOf("") }
    val query = search.trim()

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(40f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                .consumeBackgroundPointer(onDismiss),
        )
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(16.dp),
                )
                .pointerInput(Unit) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        xy("TAMBAH TOMBOL KONTROL", "ADD CONTROL BUTTON"),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        letterSpacing = 1.1.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        xy(
                            "Pilih dari kategori Kombinasi, F1-F12, Single Key, Numpad, Huruf, Modifier, atau Mouse.",
                            "Pick from Combos, F1-F12, Single Key, Numpad, Letters, Modifiers, or Mouse.",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.5.sp,
                    )
                }
            }
            XyField(
                value = search,
                onValueChange = { search = it },
                label = xy("Cari tombol", "Search button"),
                hint = xy("mis. F4, Ctrl+C, Num 5, Esc, Panah", "e.g. F4, Ctrl+C, Num 5, Esc, Arrow"),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HudKeyCatalog.groups.forEach { (name, nameEn, list) ->
                    val active = query.isEmpty() && name == group
                    Box(
                        Modifier
                            .clip(XyPill)
                            .background(
                                if (active) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            )
                            .border(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                XyPill,
                            )
                            .clickable {
                                search = ""
                                group = name
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "${xy(name, nameEn)} (${list.size})",
                            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
            val items = if (query.isNotEmpty()) {
                HudKeyCatalog.groups.flatMap { it.third }.filter { opt ->
                    opt.label.contains(query, ignoreCase = true) ||
                        opt.labelEn.contains(query, ignoreCase = true) ||
                        opt.group.contains(query, ignoreCase = true)
                }
            } else {
                HudKeyCatalog.groups.firstOrNull { it.first == group }?.third.orEmpty()
            }
            val subGroups = remember(items, group, query) {
                items.groupBy { it.group }
            }
            Column(
                Modifier
                    .height(260.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (items.isEmpty()) {
                    Text(
                        xy("Tidak ada tombol yang cocok dengan pencarian.", "No matching buttons found."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                subGroups.forEach { (subName, subItems) ->
                    if (subGroups.size > 1) {
                        Text(
                            subName.uppercase(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.5.sp,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    subItems.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEach { item ->
                                val itemIcon = hudIconFor(item.kind, item.keyCode, item.combo, item.label)
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(9.dp),
                                        )
                                        .clickable { onPick(item) }
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        if (itemIcon != null) {
                                            Icon(
                                                itemIcon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                        Text(
                                            xy(item.label, item.labelEn),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            XyPillButton(
                xy("Tutup", "Close"),
                onDismiss,
                primary = false,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Editor satu tombol: aksi (sekali/sekali pakai/tahan/toggle), ganti jenis aksi,
 * ukuran, hapus. Posisi diubah langsung dengan menggeser tombolnya di layar.
 */
@Composable
fun HudKeyEditor(
    key: HudKey,
    onChange: (HudKey) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var rePick by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zIndex(40f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                .consumeBackgroundPointer(onDismiss),
        )
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(180.dp))
                .padding(20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(14.dp),
                )
                .pointerInput(Unit) {}
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                xy("UBAH TOMBOL", "EDIT BUTTON"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                xy("Perubahan tersimpan otomatis. Selesai untuk kembali ke sesi.", "Changes save automatically. Finish to return to the session."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        key.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(key.size.coerceIn(56f, 120f).dp)
                            .clip(CircleShape)
                            .border(1.4.dp, MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        HudKeyGlyph(key, MaterialTheme.colorScheme.onSurface, key.size)
                    }
                }
                Text(
                    xy("Jenis: {0}", "Type: {0}", xy(key.kind.title, key.kind.titleEn)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                XyPillButton(
                    xy("Ganti jenis aksi", "Change action type"),
                    { rePick = true },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                XyField(
                    value = key.label,
                    onValueChange = { onChange(key.copy(label = it.take(10))) },
                    label = xy("Label tombol (maks 10 huruf)", "Button label (max 10 chars)"),
                )
                if (key.kind == HudKind.MACRO) {
                    XyField(
                        value = key.macroText,
                        onValueChange = { onChange(key.copy(macroText = it.take(160))) },
                        label = xy("Perintah / teks makro otomatis", "Auto-typed macro command / text"),
                        hint = "powershell",
                    )
                    id.xydesk.remote.ui.components.XyToggleRow(
                        title = xy("Tekan Enter otomatis di akhir", "Press Enter automatically at the end"),
                        checked = key.macroSendEnter,
                        onCheckedChange = { onChange(key.copy(macroSendEnter = it)) },
                    )
                }
                Text(xy("Cara pakai", "How it works"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                val supportedActions = HudKey.allowedActions(key.kind)
                val selectedAction = supportedActions.indexOf(key.action).coerceAtLeast(0)
                XySegmented(
                    options = supportedActions.map { xy(it.title, it.titleEn) },
                    selectedIndex = selectedAction,
                    onSelect = { onChange(key.copy(action = supportedActions[it])) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    xy(supportedActions[selectedAction].detail, supportedActions[selectedAction].detailEn),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                )
                Text(
                    xy("Ukuran tombol: {0} dp", "Button size: {0} dp", key.size.toInt()),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                )
                XySlider(
                    value = key.size,
                    onValueChange = { onChange(key.copy(size = it)) },
                    valueRange = HudKey.MIN_SIZE..HudKey.MAX_SIZE,
                )
                Text(
                    xy(
                        "Posisi: {0}% , {1}% — geser tombolnya langsung di layar untuk memindah.",
                        "Position: {0}% , {1}% — drag the button on screen to move it.",
                        (key.x * 100).toInt(),
                        (key.y * 100).toInt(),
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.5.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    xy("Hapus", "Delete"),
                    onDelete,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    xy("Simpan & selesai", "Save & finish"),
                    onDismiss,
                    primary = true,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (rePick) {
        HudKeyPicker(
            onPick = { option ->
                onChange(
                    key.copy(
                        kind = option.kind,
                        label = option.label,
                        keyCode = option.keyCode,
                        shift = option.shift,
                        combo = option.combo,
                        macroText = option.macroText,
                        macroSendEnter = option.macroSendEnter,
                        action = option.defaultAction.takeIf { it in HudKey.allowedActions(option.kind) }
                            ?: key.action.takeIf { it in HudKey.allowedActions(option.kind) }
                            ?: HudAction.TAP,
                    )
                )
                rePick = false
            },
            onDismiss = { rePick = false },
        )
    }
}

/** Banner mode atur posisi di bagian atas: tambah tombol, reset, atau selesai. */
@Composable
fun HudMappingBanner(
    keyCount: Int = 0,
    onAdd: () -> Unit,
    onReset: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    Row(
        Modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .consumeBackgroundPointer {}
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                xy(
                    "Atur posisi ({0})",
                    "Edit layout ({0})",
                    keyCount,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                xy(
                    "Geser tombol · Ketuk untuk ubah",
                    "Drag to move · Tap to edit",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .clip(XyPill)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary, XyPill)
                .clickable(onClick = onAdd)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                xy("+ Tambah", "+ Add"),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (onReset != null) {
            Box(
                Modifier
                    .clip(XyPill)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, XyPill)
                    .clickable(onClick = onReset)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    xy("Reset", "Reset"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                )
            }
        }
        Box(
            Modifier
                .clip(XyPill)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, XyPill)
                .clickable(onClick = onDone)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                xy("Selesai", "Done"),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Grid kecil pratinjau posisi tombol di dalam panel. */
@Composable
fun HudLayoutPreview(keys: List<HudKey>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            keys.forEach { key ->
                val sizeDp = (key.size / 4f).coerceIn(10f, 18f).dp
                val sizePx = with(density) { sizeDp.toPx() }
                val maxX = (constraints.maxWidth - sizePx).coerceAtLeast(1f)
                val maxY = (constraints.maxHeight - sizePx).coerceAtLeast(1f)
                Box(
                    Modifier
                        .offset {
                            IntOffset((key.x * maxX).roundToInt(), (key.y * maxY).roundToInt())
                        }
                        .size(sizeDp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
}
