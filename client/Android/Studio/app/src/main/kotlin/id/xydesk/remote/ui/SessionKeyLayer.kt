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
import androidx.compose.ui.graphics.PathEffect
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
import id.xydesk.remote.ui.components.XySlider
import id.xydesk.remote.ui.theme.XyPill
import kotlin.math.roundToInt

/** Fase aksi tombol: layer cuma melaporkan fase, arti aksinya di screen. */
enum class HudPhase { DOWN, UP, TAP }

/**
 * Warna tombol kontrol di atas gambar remote. Sengaja tetap (tidak ikut tema
 * app) karena latarnya adalah gambar sesi, bukan permukaan app.
 *
 * Tiga rasa: transparan (cuma border), gelap tipis, dan terang tipis. Ini
 * yang bikin ikon tetap kelihatan waktu desktop remote-nya putih terang.
 */
private val HudBorder = Color(0xD9FFFFFF)
private val HudBorderDim = Color(0x7AFFFFFF)
private val HudInk = Color(0xFFF1F4F6)

/** Warna per rasa latar tombol. */
data class HudPalette(val border: Color, val ink: Color, val plate: Color)

fun hudPalette(plate: HudPlate): HudPalette = when (plate) {
    HudPlate.NONE -> HudPalette(HudBorder, HudInk, Color.Transparent)
    HudPlate.DARK -> HudPalette(Color(0xF2FFFFFF), Color(0xFFF7F9FA), Color(0x99000000))
    HudPlate.LIGHT -> HudPalette(Color(0x59000000), Color(0xFF14171B), Color(0xE6FFFFFF))
}

/**
 * Lapisan tombol kontrol sesi.
 *
 * Tiap tombol bulat penuh, satu aksi, bisa digeser bebas (kapan saja, tidak
 * harus masuk mode atur posisi), ukurannya diatur, dan aksinya dipilih
 * (sekali klik / tahan / toggle). Di mode atur posisi tombol diberi ring +
 * grid bantu supaya jelas bisa digeser.
 */
@Composable
fun HudKeyLayer(
    keys: List<HudKey>,
    mappingMode: Boolean,
    plate: HudPlate = HudPlate.DARK,
    onMove: (id: String, x: Float, y: Float) -> Unit,
    onPhase: (HudKey, HudPhase) -> Unit,
    onEdit: (HudKey) -> Unit,
    onRequestEditMode: () -> Unit = {},
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        if (mappingMode) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 48.dp.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
                var x = step
                while (x < size.width) {
                    drawLine(
                        color = Color(0x33FFFFFF),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                        pathEffect = dash,
                    )
                    x += step
                }
                var y = step
                while (y < size.height) {
                    drawLine(
                        color = Color(0x33FFFFFF),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                        pathEffect = dash,
                    )
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
                plate = plate,
                maxX = maxX,
                maxY = maxY,
                onDrag = { x, y -> onMove(key.id, x, y) },
                onPhase = { phase -> onPhase(key, phase) },
                onEdit = { onEdit(key) },
                onRequestEditMode = onRequestEditMode,
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
    plate: HudPlate,
    maxX: Float,
    maxY: Float,
    onDrag: (Float, Float) -> Unit,
    onPhase: (HudPhase) -> Unit,
    onEdit: () -> Unit,
    onRequestEditMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var latched by remember(key.id) { mutableStateOf(false) }
    var pressed by remember(key.id) { mutableStateOf(false) }

    // Posisi selama geser diakumulasi lokal: blok pointerInput tidak restart
    // saat posisi berubah, jadi membaca key.x/key.y langsung akan memakai
    // nilai basi dan tombol kelihatan tidak mau digeser.
    val latest = rememberUpdatedState(key)
    val dragPos = remember(key.id) { mutableStateOf<Offset?>(null) }

    val pal = hudPalette(plate)
    val ring = when {
        mappingMode -> Color(0xFF8FD0FF)
        latched -> Color(0xFFFFFFFF)
        else -> pal.border
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
            .background(pal.plate)
            .border(
                if (pressed || latched) 2.dp else 1.4.dp,
                ring,
                CircleShape,
            )
            .pointerInput(key.id, mappingMode, key.action, plate) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    var travelled = 0f
                    var dragged = false
                    var longPressed = false
                    val holdActive = key.action == HudAction.HOLD
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    if (holdActive) onPhase(HudPhase.DOWN)

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
                        if (travelled > 8f) {
                            dragged = true
                            // Geser cuma berlaku di mode atur posisi. Di luar itu
                            // tombol diam di tempat (dulu bisa kegeser tanpa
                            // sengaja). Tombol ber-aksi "tahan" tetap dipakai
                            // untuk drag di remote.
                            if (!holdActive && mappingMode) {
                                val base = dragPos.value
                                    ?: Offset(latest.value.x, latest.value.y)
                                val nx = (base.x + delta.x / maxX).coerceIn(0f, 1f)
                                val ny = (base.y + delta.y / maxY).coerceIn(0f, 1f)
                                dragPos.value = Offset(nx, ny)
                                onDrag(nx, ny)
                            }
                            change.consume()
                        } else if (event.changes.all { it.uptimeMillis - down.uptimeMillis > longPressTimeout }) {
                            if (!holdActive && !longPressed && !dragged) {
                                longPressed = true
                                if (mappingMode) onEdit() else onRequestEditMode()
                            }
                        }
                    }

                    pressed = false
                    dragPos.value = null
                    if (holdActive) onPhase(HudPhase.UP)

                    // Di mode atur posisi, ketuk = buka editor tombol itu.
                    if (mappingMode) {
                        if (!dragged && !longPressed) onEdit()
                        return@awaitEachGesture
                    }
                    if (longPressed) return@awaitEachGesture
                    if (dragged) return@awaitEachGesture
                    when (key.action) {
                        HudAction.HOLD -> Unit
                        HudAction.TOGGLE -> {
                            latched = !latched
                            onPhase(HudPhase.TAP)
                        }

                        HudAction.TAP -> onPhase(HudPhase.TAP)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        HudKeyGlyph(key, pal.ink)
    }
}

/** Isi tombol: ikon untuk aksi mouse/scroll/mode, teks untuk tombol keyboard. */
@Composable
private fun HudKeyGlyph(key: HudKey, tint: Color, boxDp: Float = key.size) {
    val icon: ImageVector? = when (key.kind) {
        HudKind.MOUSE_LEFT -> XyIcons.ClickLeft
        HudKind.MOUSE_RIGHT -> XyIcons.ClickRight
        HudKind.MOUSE_MIDDLE -> XyIcons.ClickMiddle
        HudKind.SCROLL_UP -> XyIcons.ScrollUp
        HudKind.SCROLL_DOWN -> XyIcons.ScrollDown
        HudKind.INPUT_SWITCH -> XyIcons.Swap
        HudKind.KEYBOARD -> XyIcons.Keyboard
        else -> null
    }
    if (icon != null) {
        Icon(
            icon,
            contentDescription = key.label,
            tint = tint,
            modifier = Modifier.size((boxDp * 0.46f).dp),
        )
        return
    }
    Text(
        text = key.label,
        color = tint,
        fontSize = (boxDp * 0.30f).sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}

// ------------------------------------------------------------------ editor

/**
 * Dialog tambah tombol: satu katalog per grup. Bukan bottom sheet/dialog
 * bawaan Android — pakai overlay app sendiri supaya gayanya satu bahasa
 * dengan panel dan tema.
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
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss)
            .zIndex(40f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 440.dp)
                .padding(20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(14.dp),
                )
                .clickable { }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                xy("TAMBAH TOMBOL", "ADD BUTTON"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                HudKeyCatalog.groups.forEach { (name, nameEn, _) ->
                    val active = name == group
                    Box(
                        Modifier
                            .clip(XyPill)
                            .border(
                                1.dp,
                                if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                XyPill,
                            )
                            .clickable { group = name }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            xy(name, nameEn),
                            color = if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            val items = HudKeyCatalog.groups.firstOrNull { it.first == group }?.third.orEmpty()
            Column(
                Modifier
                    .height(250.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowItems.forEach { item ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(9.dp),
                                    )
                                    .clickable { onPick(item) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    xy(item.label, item.labelEn),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
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
 * Editor satu tombol: aksi (sekali/tahan/toggle), ganti jenis aksi, ukuran,
 * hapus. Posisi diubah langsung dengan menggeser tombolnya di layar.
 */
@Composable
fun HudKeyEditor(
    key: HudKey,
    onChange: (HudKey) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var rePick by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
            .clickable(onClick = onDismiss)
            .zIndex(40f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 400.dp)
                .padding(20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(14.dp),
                )
                .clickable { }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                xy("UBAH TOMBOL", "EDIT BUTTON"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                letterSpacing = 1.3.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
                        .size((key.size.coerceAtMost(64f)).dp)
                        .clip(CircleShape)
                        .border(1.4.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    HudKeyGlyph(key, MaterialTheme.colorScheme.onSurface, maxOf(key.size, 40f))
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
            Text(xy("Cara pakai", "How it works"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            XySegmented(
                options = HudAction.entries.map { xy(it.title, it.titleEn) },
                selectedIndex = key.action.ordinal,
                onSelect = { onChange(key.copy(action = HudAction.entries[it])) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                xy(HudAction.entries[key.action.ordinal].detail, HudAction.entries[key.action.ordinal].detailEn),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
            )
            Text(
                xy("Ukuran: {0} dp", "Size: {0} dp", key.size.toInt()),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
            )
            XySlider(
                value = key.size,
                onValueChange = { onChange(key.copy(size = it)) },
                valueRange = 28f..120f,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    xy("Simpan", "Save"),
                    onDismiss,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    xy("Hapus", "Delete"),
                    onDelete,
                    primary = false,
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
                    )
                )
                rePick = false
            },
            onDismiss = { rePick = false },
        )
    }
}

/** Banner mode atur posisi: tambah tombol, atau selesai. */
@Composable
fun HudMappingBanner(
    onAdd: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE60B0D10))
            .border(1.dp, HudBorderDim, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            xy(
                "Atur posisi: geser tombol, tahan lama untuk ubah aksi/ukuran",
                "Edit layout: drag a button, long-press to change action/size",
            ),
            color = HudInk,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .clip(XyPill)
                .border(1.dp, HudBorder, XyPill)
                .clickable(onClick = onAdd)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(xy("+ Tombol", "+ Button"), color = HudInk, fontSize = 11.sp)
        }
        Box(
            Modifier
                .clip(XyPill)
                .background(Color(0xFFF1F4F6))
                .clickable(onClick = onDone)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(xy("Selesai", "Done"), color = Color(0xFF0A0B0D), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                val sizeDp = (key.size / 4f).coerceIn(8f, 18f).dp
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
