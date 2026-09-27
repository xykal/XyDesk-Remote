package id.xydesk.remote.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wallpaper desktop untuk preview perangkat + latar layar koneksi.
 *
 * Digambar prosedural (Canvas) — bukan aset biner. Alasan: bobot APK nol,
 * tidak ada masalah lisensi gambar, dan hasilnya tetap ikut tema app.
 * Motifnya cuma pengingat jenis OS perangkat, bukan replika wallpaper asli.
 */
enum class XyWall(val label: String) {
    WIN11("Windows 11"),
    WIN10("Windows 10"),
    MACOS("macOS"),
    NEUTRAL("Netral"),
}

/** Tebak motif dari nama/label perangkat. */
fun XyWall.forDevice(label: String?): XyWall {
    val l = label?.lowercase().orEmpty()
    return when {
        l.contains("mac") || l.contains("osx") || l.contains("macbook") -> MACOS
        l.contains("win 10") || l.contains("win10") || l.contains("windows 10") -> WIN10
        l.contains("win") || l.contains("11") -> WIN11
        else -> this
    }
}

@Composable
fun XyWallpaper(
    wall: XyWall,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 0.dp,
    dim: Float = 0f,
) {
    val blurModifier = if (blurRadius > 0.dp && Build.VERSION.SDK_INT >= 31) {
        Modifier.blur(blurRadius)
    } else {
        Modifier
    }
    Box(modifier.fillMaxSize().then(blurModifier)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (wall) {
                XyWall.WIN11 -> {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0B2038), Color(0xFF071427), Color(0xFF040C18)),
                            start = Offset(0f, 0f),
                            end = Offset(w, h),
                        )
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2C6FB5), Color(0x000E2A46)),
                            center = Offset(w * 0.5f, h * 0.46f),
                            radius = h * 0.68f,
                        ),
                        topLeft = Offset(w * 0.06f, h * 0.02f),
                        size = Size(w * 0.88f, h * 0.88f),
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF7C5BD6), Color(0x00000000)),
                            center = Offset(w * 0.74f, h * 0.30f),
                            radius = h * 0.42f,
                        ),
                        topLeft = Offset(w * 0.24f, h * 0.02f),
                        size = Size(w * 0.8f, h * 0.7f),
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF38C6D9), Color(0x00000000)),
                            center = Offset(w * 0.26f, h * 0.74f),
                            radius = h * 0.4f,
                        ),
                        topLeft = Offset(-w * 0.2f, h * 0.34f),
                        size = Size(w * 0.9f, h * 0.8f),
                    )
                }

                XyWall.WIN10 -> {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0A1622), Color(0xFF071019), Color(0xFF02070C)),
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.6f, h),
                        )
                    )
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1D4F7A), Color(0x00090F16)),
                            start = Offset(0f, 0f),
                            end = Offset(w * 0.75f, h * 0.9f),
                        )
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2F7FB8), Color(0x00000000)),
                            center = Offset(w * 0.3f, h * 0.3f),
                            radius = h * 0.55f,
                        ),
                        topLeft = Offset(-w * 0.1f, -h * 0.05f),
                        size = Size(w, h),
                    )
                }

                XyWall.MACOS -> {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2A1E5C), Color(0xFF6A3E9E),
                                Color(0xFFB85C88), Color(0xFFE08A5A),
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(w, h),
                        )
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFC48A), Color(0x00000000)),
                            center = Offset(w * 0.62f, h * 0.72f),
                            radius = h * 0.5f,
                        ),
                        topLeft = Offset(w * 0.16f, h * 0.36f),
                        size = Size(w * 0.9f, h * 0.8f),
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF4A6BE0), Color(0x00000000)),
                            center = Offset(w * 0.24f, h * 0.26f),
                            radius = h * 0.44f,
                        ),
                        topLeft = Offset(-w * 0.2f, -h * 0.1f),
                        size = Size(w * 0.8f, h * 0.8f),
                    )
                }

                XyWall.NEUTRAL -> {
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF15181C), Color(0xFF0A0C0F)),
                            start = Offset(0f, 0f),
                            end = Offset(w, h),
                        )
                    )
                    drawOval(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF2A3038), Color(0x00000000)),
                            center = Offset(w * 0.4f, h * 0.3f),
                            radius = h * 0.6f,
                        ),
                        topLeft = Offset(-w * 0.2f, -h * 0.2f),
                        size = Size(w * 1.2f, h),
                    )
                }
            }
            if (dim > 0f) {
                drawRect(Color.Black.copy(alpha = dim.coerceIn(0f, 0.9f)))
            }
        }
    }
}

/** Latar gelap polos untuk layar app (bukan wallpaper). */
@Composable
fun XyBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color(0xFF07080A)))
}
