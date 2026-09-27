package id.xydesk.remote.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import id.xydesk.remote.R
import kotlin.math.roundToInt

private data class HudAction(
    val id: String,
    val label: String,
    val shortLabel: String,
    val x: Float,
    val y: Float,
    val action: () -> Unit,
)

private data class HudPlacement(
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val visible: Boolean = true,
)

/** Full-screen RDP overlay. Controls are separate, movable and individually resizable. */
@Composable
fun SessionHud(
    profileId: String,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    hostLabel: String,
    statusText: String,
    zoom: Float,
    onTogglePointer: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomActual: () -> Unit,
    onFit: () -> Unit,
    onResolutionSelected: (String) -> Unit,
    onScreenshot: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember(profileId) {
        context.getSharedPreferences("xydesk-hud-$profileId", Context.MODE_PRIVATE)
    }
    val displayStore = remember(profileId) {
        context.getSharedPreferences("xydesk.remote.display", Context.MODE_PRIVATE)
    }
    var selectedResolution by remember(profileId) {
        mutableStateOf(displayStore.getString("$profileId.resolution", "automatic") ?: "automatic")
    }
    val actionList = listOf(
        HudAction("pointer", "Mouse / touch pointer", "Mouse", .88f, .34f, onTogglePointer),
        HudAction("keyboard", "Keyboard", "Keys", .88f, .44f, onToggleKeyboard),
        HudAction("zoom_in", "Zoom in", "Zoom+", .88f, .54f, onZoomIn),
        HudAction("zoom_out", "Zoom out", "Zoom−", .88f, .64f, onZoomOut),
        HudAction("fit", "Fit desktop to screen", "Fit", .88f, .74f, onFit),
        HudAction("screenshot", "Take screenshot", "Shot", .88f, .84f, onScreenshot),
        HudAction("rotate", "Rotate device", "Rotate", .74f, .84f, {}),
        HudAction("disconnect", "Disconnect session", "Exit", .74f, .74f, onDisconnect),
    )
    fun defaultPlacement(a: HudAction) = HudPlacement(a.x, a.y)
    fun loadPlacement(a: HudAction): HudPlacement {
        val prefix = a.id + "."
        return HudPlacement(
            x = store.getFloat(prefix + "x", a.x).coerceIn(0f, 1f),
            y = store.getFloat(prefix + "y", a.y).coerceIn(0f, 1f),
            scale = store.getFloat(prefix + "scale", 1f).coerceIn(.65f, 1_000_000f),
            visible = store.getBoolean(prefix + "visible", true),
        )
    }
    var placements by remember(profileId) {
        mutableStateOf(actionList.associate { it.id to loadPlacement(it) })
    }
    var arrangeMode by remember { mutableStateOf(false) }
    var paletteOpen by remember { mutableStateOf(false) }
    var displayOpen by remember { mutableStateOf(false) }
    var orientation by remember(profileId) { mutableStateOf(store.getString("orientation", "Auto") ?: "Auto") }

    LaunchedEffect(orientation) {
        val activity = context as? Activity
        activity?.requestedOrientation = when (orientation) {
            "Portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            "Landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    fun update(id: String, placement: HudPlacement) {
        val normalized = placement.copy(
            x = placement.x.coerceIn(0f, 1f),
            y = placement.y.coerceIn(0f, 1f),
            scale = placement.scale.coerceIn(.65f, 1_000_000f),
        )
        placements = placements + (id to normalized)
        val prefix = "$id."
        store.edit()
            .putFloat(prefix + "x", normalized.x)
            .putFloat(prefix + "y", normalized.y)
            .putFloat(prefix + "scale", normalized.scale)
            .putBoolean(prefix + "visible", normalized.visible)
            .apply()
    }

    Box(Modifier.fillMaxSize().zIndex(10f)) {
        if (open) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val widthPx = constraints.maxWidth.toFloat()
                val heightPx = constraints.maxHeight.toFloat()
                val currentPlacements by rememberUpdatedState(placements)
                actionList.forEach { item ->
                    val placement = placements[item.id] ?: defaultPlacement(item)
                    if (placement.visible) {
                        val controlSize = minOf(54.dp * placement.scale, maxWidth, maxHeight)
                        val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            controlSize.toPx()
                        }
                        val maxX = (widthPx - sizePx).coerceAtLeast(0f)
                        val maxY = (heightPx - sizePx).coerceAtLeast(0f)
                        Surface(
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (placement.x * maxX).roundToInt(),
                                        (placement.y * maxY).roundToInt(),
                                    )
                                }
                                .size(controlSize)
                                .shadow(12.dp, RoundedCornerShape(18.dp))
                                .border(
                                    1.dp,
                                    if (arrangeMode) Color(0xFF58E1C1) else Color.White.copy(alpha = .16f),
                                    RoundedCornerShape(18.dp),
                                )
                                .pointerInput(arrangeMode, item.id, widthPx, heightPx, sizePx) {
                                    if (arrangeMode) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            val current = currentPlacements[item.id] ?: placement
                                            update(
                                                item.id,
                                                current.copy(
                                                    x = current.x + drag.x / maxX.coerceAtLeast(1f),
                                                    y = current.y + drag.y / maxY.coerceAtLeast(1f),
                                                ),
                                            )
                                        }
                                    }
                                }
                                .clickable(enabled = !arrangeMode) {
                                    if (item.id == "rotate") displayOpen = true else item.action()
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xE91A2632),
                            contentColor = Color(0xFFF4FAFC),
                        ) {
                            Box(Modifier.fillMaxSize().padding(3.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    item.shortLabel,
                                    fontSize = minOf(11f * placement.scale, 30f).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                    .fillMaxWidth(.94f)
                    .border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(22.dp))
                    .shadow(14.dp, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF21B2631),
                contentColor = Color(0xFFF4FAFC),
            ) {
                Row(
                    modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.xydesk_app_mark),
                        contentDescription = "XyDesk",
                        modifier = Modifier.size(28.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(hostLabel, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("$statusText  ·  ${(zoom * 100).roundToInt()}%",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = Color(0xFFB9C8D2), fontSize = 10.sp)
                    }
                    HudTopButton(if (arrangeMode) "Done" else "Move", arrangeMode) { arrangeMode = !arrangeMode }
                    HudTopButton("Items", paletteOpen) { paletteOpen = true }
                    HudTopButton("Display", false) { displayOpen = true }
                    HudTopButton("×", false) { onOpenChange(false) }
                }
            }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .border(1.dp, Color.White.copy(alpha = .16f), RoundedCornerShape(18.dp))
                    .clickable { onOpenChange(true) },
                shape = RoundedCornerShape(18.dp),
                color = Color(0xE91A2632),
                contentColor = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(R.drawable.xydesk_app_mark),
                        contentDescription = "XyDesk",
                        modifier = Modifier.size(22.dp),
                    )
                    Text("Controls", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (paletteOpen) {
            HudOverlayCard(title = "Atur kontrol satu per satu", onClose = { paletteOpen = false }) {
                Text("Seret tiap tombol langsung di layar. Atur ukuran atau sembunyikan item secara mandiri.",
                    color = Color(0xFFB9C8D2), fontSize = 12.sp)
                actionList.forEach { item ->
                    val value = placements[item.id] ?: defaultPlacement(item)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp).clickable {
                                update(item.id, value.copy(visible = !value.visible))
                            },
                            shape = RoundedCornerShape(11.dp),
                            color = if (value.visible) Color(0xFF183F3E) else Color(0xFF303944),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(if (value.visible) "ON" else "OFF", fontSize = 9.sp,
                                    color = Color(0xFF7AE7C9), fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(item.label, modifier = Modifier.weight(1f), fontSize = 12.sp,
                            color = Color(0xFFF1F5F8))
                        TextButton(onClick = {
                            update(item.id, value.copy(scale = value.scale / 1.15f))
                        }) { Text("−") }
                        Text(if (value.scale >= 5f) "Layar" else "${(value.scale * 100).roundToInt()}%", fontSize = 10.sp,
                            color = Color(0xFFB9C8D2))
                        TextButton(onClick = {
                            update(item.id, value.copy(scale = value.scale * 1.15f))
                        }) { Text("+") }
                    }
                }
                Text("Ukuran dapat dinaikkan atau diturunkan per item. Posisi tersimpan untuk koneksi ini.",
                    color = Color(0xFFB9C8D2), fontSize = 11.sp)
                HudTopButton("Pulihkan layout", false) {
                    actionList.forEach { item -> update(item.id, defaultPlacement(item)) }
                }
            }
        }

        if (displayOpen) {
            HudOverlayCard(title = "Layar & orientasi", onClose = { displayOpen = false }) {
                Text("Skala viewer", color = Color(0xFFB9C8D2), fontSize = 12.sp)
                HudChoice("Fit seluruh desktop", "Skalakan desktop agar muat di layar", onClick = {
                    onFit()
                    displayOpen = false
                })
                HudChoice("Ukuran 100%", "Tampilkan piksel remote pada skala asli", onClick = {
                    onZoomActual()
                    displayOpen = false
                })
                Spacer(Modifier.height(6.dp))
                Text("Resolusi desktop remote", color = Color(0xFFB9C8D2), fontSize = 12.sp)
                listOf(
                    "automatic" to "Otomatis",
                    "1280x720" to "1280 × 720",
                    "1366x768" to "1366 × 768",
                    "1600x900" to "1600 × 900",
                    "1920x1080" to "1920 × 1080",
                    "2560x1440" to "2560 × 1440",
                ).forEach { (value, label) ->
                    HudChoice(
                        if (selectedResolution == value) "✓  $label" else label,
                        "Terapkan saat sesi tersambung ulang",
                        onClick = {
                            val changed = selectedResolution != value
                            selectedResolution = value
                            displayStore.edit().putString("$profileId.resolution", value).apply()
                            displayOpen = false
                            if (changed) onResolutionSelected(value)
                        },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Orientasi perangkat", color = Color(0xFFB9C8D2), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Auto", "Portrait", "Landscape").forEach { mode ->
                        HudTopButton(mode, orientation == mode) {
                            orientation = mode
                            store.edit().putString("orientation", mode).apply()
                        }
                    }
                }
                Text("Perubahan resolusi memutus lalu menyambungkan ulang sesi memakai ukuran baru. Fit/100% hanya mengubah skala viewer.",
                    color = Color(0xFFB9C8D2), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun HudTopButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Color(0xFF1B685B) else Color(0xFF293744),
        contentColor = Color.White,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
            fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HudOverlayCard(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .58f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(.92f).fillMaxHeight(.88f).clickable { }
                .shadow(24.dp, RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF17232D),
            contentColor = Color(0xFFF2F7F9),
        ) {
            Column(
                modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, modifier = Modifier.weight(1f), fontSize = 17.sp,
                        fontWeight = FontWeight.Bold)
                    HudTopButton("Tutup", false, onClose)
                }
                Spacer(Modifier.height(5.dp))
                content()
            }
        }
    }
}

@Composable
private fun HudChoice(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF263642),
        contentColor = Color(0xFFF2F7F9),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 10.sp, color = Color(0xFFB9C8D2))
        }
    }
}
