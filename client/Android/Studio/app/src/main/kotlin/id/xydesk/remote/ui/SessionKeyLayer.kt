package id.xydesk.remote.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XySegmented
import id.xydesk.remote.ui.theme.XyPill
import kotlin.math.roundToInt

/** Fase aksi tombol: layer cuma melaporkan fase, arti aksinya di screen. */
enum class HudPhase { DOWN, UP, TAP }

private val HudBorder = Color(0xD9FFFFFF)
private val HudBorderDim = Color(0x59FFFFFF)
private val HudInk = Color(0xFFEFF3F6)
private val HudMuted = Color(0xFF9AA4AD)

/**
 * Layer tombol HUD: tiap tombol bulat penuh, satu aksi, bisa digeser bebas,
 * ukurannya diatur, dan aksinya dipilih (sekali klik / tahan / toggle).
 *
 * Di mode "atur posisi" (screen mapping) tombol diberi ring putus-putus +
 * grid bantu supaya jelas sedang bisa digeser; long-press membuka editor.
 */
@Composable
fun HudKeyLayer(
    keys: List<HudKey>,
    mappingMode: Boolean,
    onMove: (id: String, x: Float, y: Float) -> Unit,
    onPhase: (HudKey, HudPhase) -> Unit,
    onEdit: (HudKey) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        if (mappingMode) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 48.dp.toPx()
                val stroke = Stroke(
                    width = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f),
                )
                var x = step
                while (x < size.width) {
                    drawLine(Color(0x22FFFFFF), Offset(x, 0f), Offset(x, size.height), 1f, stroke)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(Color(0x22FFFFFF), Offset(0f, y), Offset(size.width, y), 1f, stroke)
                    y += step
                }
            }
        }

        keys.forEach { key ->
            val sizePx = with(density) { key.size.dp.toPx() }
            val maxX = (widthPx - sizePx).coerceAtLeast(1f)
            val maxY = (heightPx - sizePx).coerceAtLeast(1f)
            HudKeyButton(
                key = key,
                mappingMode = mappingMode,
                onMove = { dx, dy ->
                    onMove(
                        key.id,
                        (key.x + dx / maxX).coerceIn(0f, 1f),
                        (key.y + dy / maxY).coerceIn(0f, 1f),
                    )
                },
                onPhase = { phase -> onPhase(key, phase) },
                onEdit = { onEdit(key) },
                modifier = Modifier
                    .offset {
                        IntOffset((key.x * maxX).roundToInt(), (key.y * maxY).roundToInt())
                    }
                    .zIndex(12f),
            )
        }
    }
}

@Composable
private fun HudKeyButton(
    key: HudKey,
    mappingMode: Boolean,
    onMove: (Float, Float) -> Unit,
    onPhase: (HudPhase) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var latched by remember(key.id) { mutableStateOf(false) }
    var pressed by remember(key.id) { mutableStateOf(false) }

    val ring = when {
        latched -> Color(0xFFFFFFFF)
        mappingMode -> Color(0xFF8FD0FF)
        else -> HudBorder
    }

    Box(
        modifier
            .size(key.size.dp)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(CircleShape)
            .border(
                if (pressed || latched) 2.dp else 1.4.dp,
                ring,
                CircleShape,
            )
            .pointerInput(key.id, mappingMode, key.action) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    var travelled = 0f
                    var dragged = false
                    var longPressed = false
                    val holdActive = key.action == HudAction.HOLD
                    if (holdActive) onPhase(HudPhase.DOWN)

                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val delta = change.positionChange()
                        travelled += delta.getDistance()
                        if (travelled > 8f) {
                            dragged = true
                            if (holdActive && !mappingMode) {
                                // geser sambil tahan (mis. drag di remote)
                            } else {
                                onMove(delta.x, delta.y)
                                change.consume()
                            }
                        } else if (event.changes.all { it.uptimeMillis - down.uptimeMillis > longPressTimeout }) {
                            if (mappingMode && !longPressed) {
                                longPressed = true
                                onEdit()
                            }
                        }
                    }

                    pressed = false
                    if (holdActive) onPhase(HudPhase.UP)

                    when {
                        // Di mode atur posisi tombol TIDAK mengirim apa pun ke
                        // remote: tap/geser cuma mengatur letak, jadi tidak ada
                        // aksi nyasar (mis. kombinasi sensitif) saat menata.
                        mappingMode -> Unit
                        longPressed -> Unit
                        dragged && !holdActive -> Unit
                        key.action == HudAction.HOLD -> Unit
                        key.action == HudAction.TOGGLE -> {
                            latched = !latched
                            onPhase(HudPhase.TAP)
                        }

                        else -> onPhase(HudPhase.TAP)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        HudKeyGlyph(key, HudInk)
    }
}

/** Isi tombol: ikon untuk aksi mouse/scroll/mode, teks untuk tombol keyboard. */
@Composable
private fun HudKeyGlyph(key: HudKey, tint: Color) {
    val icon: ImageVector? = when (key.kind) {
        HudKind.MOUSE_LEFT -> XyIcons.ClickLeft
        HudKind.MOUSE_RIGHT -> XyIcons.ClickRight
        HudKind.MOUSE_MIDDLE -> XyIcons.ClickMiddle
        HudKind.SCROLL_UP -> XyIcons.ScrollUp
        HudKind.SCROLL_DOWN -> XyIcons.ScrollDown
        HudKind.INPUT_SWITCH -> XyIcons.Swap
        else -> null
    }
    if (icon != null) {
        Icon(
            icon,
            contentDescription = key.label,
            tint = tint,
            modifier = Modifier.size((key.size * 0.46f).dp),
        )
        return
    }
    Text(
        text = key.label,
        color = tint,
        fontSize = (key.size * 0.28f).sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

/**
 * Toolbar yang menempel di atas keyboard HP. Isinya tombol yang ditandai
 * "tampil di toolbar" (horizontal, bisa di-scroll) plus chip paling ujung:
 * `123` untuk membuka board lengkap, `ABC` untuk balik ke keyboard HP.
 */
@Composable
fun HudKeyToolbar(
    keys: List<HudKey>,
    boardOpen: Boolean,
    onPhase: (HudKey, HudPhase) -> Unit,
    onToggleBoard: () -> Unit,
    onOpenPanel: () -> Unit,
    chips: @Composable () -> Unit = {},
) {
    val row = keys.filter { it.inToolbar }
    Row(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(XyPill)
            .background(Color(0xCC0B0D10))
            .border(1.dp, HudBorderDim, XyPill)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .weight(1f, fill = false)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips()
            row.forEach { key ->
                ToolbarChip(
                    key = key,
                    onPhase = { phase -> onPhase(key, phase) },
                )
            }
        }
        // Paling ujung: 123 / ABC.
        Box(
            Modifier
                .clip(XyPill)
                .border(1.2.dp, HudBorder, XyPill)
                .clickable(onClick = onToggleBoard)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                if (boardOpen) "ABC" else "123",
                color = HudInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ToolbarChip(key: HudKey, onPhase: (HudPhase) -> Unit) {
    val size = 40f
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .border(1.2.dp, HudBorderDim, CircleShape)
            .pointerInput(key.id, key.action) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val hold = key.action == HudAction.HOLD
                    if (hold) onPhase(HudPhase.DOWN)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                    }
                    if (hold) onPhase(HudPhase.UP) else onPhase(HudPhase.TAP)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        HudKeyGlyph(key, HudInk)
    }
}

// ------------------------------------------------------------------ editor

/**
 * Dialog tambah tombol: daftar katalog per grup. Bukan bottom sheet bawaan
 * Android — panel penuh milik app sendiri supaya gayanya konsisten.
 */
@Composable
fun HudKeyPicker(
    onPick: (HudKeyOption) -> Unit,
    onDismiss: () -> Unit,
) {
    var group by remember { mutableStateOf(HudKeyCatalog.groups.first().first) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss)
            .zIndex(40f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .padding(20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF20B0D10))
                .border(1.dp, HudBorderDim, RoundedCornerShape(16.dp))
                .clickable { }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "TAMBAH TOMBOL",
                color = HudMuted,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HudKeyCatalog.groups.forEach { (name, _) ->
                    Box(
                        Modifier
                            .clip(XyPill)
                            .border(
                                1.dp,
                                if (name == group) HudInk else HudBorderDim,
                                XyPill,
                            )
                            .clickable { group = name }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            name,
                            color = if (name == group) HudInk else HudMuted,
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }
            val items = HudKeyCatalog.groups.firstOrNull { it.first == group }?.second.orEmpty()
            Column(
                Modifier
                    .height(240.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.chunked(4).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { item ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .border(1.dp, HudBorderDim, RoundedCornerShape(9.dp))
                                    .clickable { onPick(item) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    item.label,
                                    color = HudInk,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    "Tutup",
                    onDismiss,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Editor satu tombol: label, aksi, ukuran, ikut toolbar, hapus. */
@Composable
fun HudKeyEditor(
    key: HudKey,
    onChange: (HudKey) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onDismiss)
            .zIndex(40f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .padding(20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF20B0D10))
                .border(1.dp, HudBorderDim, RoundedCornerShape(16.dp))
                .clickable { }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "UBAH TOMBOL",
                color = HudMuted,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(key.label, color = HudInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Aksi",
                color = HudMuted,
                fontSize = 10.5.sp,
            )
            XySegmented(
                options = HudAction.entries.map { it.title },
                selectedIndex = key.action.ordinal,
                onSelect = { onChange(key.copy(action = HudAction.entries[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                HudAction.entries[key.action.ordinal].detail,
                color = HudMuted,
                fontSize = 10.sp,
            )
            if (key.kind != HudKind.COMBO) {
                Text("Ukuran: ${key.size.toInt()} dp", color = HudInk, fontSize = 11.sp)
                Slider(
                    value = key.size,
                    onValueChange = { onChange(key.copy(size = it)) },
                    valueRange = 32f..96f,
                )
            }
            Text(
                "Posisi: ${(key.x * 100).toInt()}% , ${(key.y * 100).toInt()}%  ·  " +
                    "geser tombolnya langsung untuk memindah",
                color = HudMuted,
                fontSize = 10.sp,
            )
            XySegmented(
                options = listOf("Di toolbar", "Hanya di layar"),
                selectedIndex = if (key.inToolbar) 0 else 1,
                onSelect = { onChange(key.copy(inToolbar = it == 0)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    "Simpan",
                    onDismiss,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    "Hapus",
                    onDelete,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Baris status mode atur posisi. */
@Composable
fun HudMappingBanner(onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(XyPill)
            .background(Color(0xD90B0D10))
            .border(1.dp, HudBorderDim, XyPill)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Mode atur posisi: geser tombol bebas, tahan tombol untuk ubah aksi/ukuran",
            color = HudInk,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .clip(XyPill)
                .border(1.dp, HudBorder, XyPill)
                .clickable(onClick = onDone)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("Selesai", color = HudInk, fontSize = 11.sp)
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
            .background(Color(0xFF0E1114))
            .border(1.dp, HudBorderDim, RoundedCornerShape(10.dp)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            keys.forEach { key ->
                val sizeDp = 12.dp
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
                        .background(
                            if (key.inToolbar) Color(0xFFEFF3F6) else Color(0x66EFF3F6),
                        ),
                )
            }
        }
    }
}
