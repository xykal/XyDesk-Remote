package id.xydesk.remote.ui

import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.xydesk.remote.R

/**
 * Wallpaper desktop untuk preview perangkat + latar layar koneksi.
 *
 * Windows 11 memakai foto asli (drawable-nodpi/xy_win11_wall.jpg) supaya
 * preview perangkat Windows terasa seperti desktop yang benar-benar dilihat
 * user. Sisanya digambar prosedural (Canvas): bobot APK nol dan tidak ada
 * urusan lisensi gambar.
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
        l.contains("mac") || l.contains("osx") || l.contains("macbook") -> XyWall.MACOS
        l.contains("win 10") || l.contains("win10") || l.contains("windows 10") -> XyWall.WIN10
        l.contains("win") || l.contains("11") -> XyWall.WIN11
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
        if (wall == XyWall.WIN11) {
            Image(
                painter = painterResource(R.drawable.xy_win11_wall),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            when (wall) {
                XyWall.WIN11 -> {
                    // Fotonya digambar di luar Canvas (lihat Image di bawah) —
                    // di sini hanya penajam kontras supaya teks tetap terbaca
                    // di atas area terang wallpaper.
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x59000000), Color(0x26000000), Color(0x8C000000),
                            ),
                        )
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

/**
 * Art preview kartu perangkat.
 *
 * Ini BUKAN wallpaper RDP dan bukan wallpaper OS: gambar geometris milik
 * app sendiri, diturunkan dari nama perangkat (hash) sehingga tiap kartu
 * berbeda tapi tetap satu keluarga. Palet: grafit + baja + satu aksen amber.
 * Tidak ada glow neon dan tidak ada gradien ungu — sesuai aturan visual app.
 */
@Composable
fun DevicePreviewArt(
    seed: String,
    os: XyWall,
    modifier: Modifier = Modifier,
) {
    val h = seed.hashCode()
    val corners = listOf(
        Offset(0f, 0f),
        Offset(1f, 0f),
        Offset(1f, 1f),
        Offset(0f, 1f),
    )
    val origin = corners[((h ushr 3) and 0x7fffffff) % corners.size]
    val accentX = 0.22f + ((h ushr 7) and 0x7fffffff) % 45 / 100f
    val accentY = 0.30f + ((h ushr 13) and 0x7fffffff) % 40 / 100f
    val hairShift = ((h ushr 17) and 0x7fffffff) % 4

    Box(
        modifier.fillMaxSize(),
    ) {
        if (os == XyWall.WIN11) {
            // Preview perangkat Windows 11 memakai wallpaper aslinya, lalu
            // ditutup scrim gelap: kartu tetap bagian UI app, bukan pameran foto.
            Image(
                painter = painterResource(R.drawable.xy_win11_wall),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x4D000000), Color(0x40000000), Color(0x99000000)),
                        )
                    ),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF15191E), Color(0xFF0C0F12), Color(0xFF070909)),
                            start = Offset(0f, 0f),
                            end = Offset(720f, 420f),
                        ),
                    ),
            )
        }
        // Garis rambut lebih tipis di atas foto supaya tidak terlihat kotor.
        val artAlpha = if (os == XyWall.WIN11) 0.42f else 1f
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val hh = size.height
            val ox = origin.x * w
            val oy = origin.y * hh

            // Cincin besar: pusat di sudut terpilih per nama perangkat.
            val radius = hh * (1.05f + (hairShift * 0.06f))
            drawCircle(
                color = Color(0xFF3A5468).copy(alpha = 0.55f * artAlpha),
                radius = radius,
                center = Offset(ox, oy),
                style = Stroke(width = 1.6f),
            )
            drawCircle(
                color = Color(0xFF26313A).copy(alpha = artAlpha),
                radius = radius * 0.78f,
                center = Offset(ox, oy),
                style = Stroke(width = 1.2f),
            )

            // Garis diagonal tipis (arah berbeda per perangkat).
            val angle = (22f + hairShift * 14f) * (if ((h and 1) == 0) 1f else -1f)
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val dx = kotlin.math.cos(rad)
            val dy = kotlin.math.sin(rad)
            for (i in 0..3) {
                val off = (i + 1) * w * 0.19f
                drawLine(
                    color = Color(0xFF2A333B).copy(alpha = artAlpha),
                    start = Offset(-w * 0.2f + off * dx, -hh * 0.2f + off * dy),
                    end = Offset(w * 1.2f * dx + off * dx, hh * 1.2f * dy + off * dy),
                    strokeWidth = 1f,
                )
            }

            // Titik identitas: satu aksen saja, ukuran kecil.
            drawCircle(
                color = Color(0xFFD9A45B),
                radius = hh * 0.035f,
                center = Offset(w * accentX, hh * accentY),
            )
            drawCircle(
                color = Color(0xFFD9A45B).copy(alpha = 0.30f),
                radius = hh * 0.085f,
                center = Offset(w * accentX, hh * accentY),
                style = Stroke(width = 1f),
            )

            // Grid titik halus di sudut berlawanan.
            val step = hh * 0.09f
            var gy = hh * 0.62f
            while (gy < hh) {
                var gx = w * 0.06f
                while (gx < w * 0.5f) {
                    drawCircle(
                        color = Color(0xFF6C7A85).copy(alpha = 0.28f),
                        radius = 0.9f,
                        center = Offset(gx, gy),
                    )
                    gx += step
                }
                gy += step
            }
        }

        // Penanda jenis OS: siluet jendela kecil, bukan logo resmi.
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(26.dp),
        ) {
            val stroke = Stroke(width = 1.4f)
            when (os) {
                XyWall.MACOS -> {
                    drawRoundRect(
                        color = Color(0xFFB9C4CC),
                        topLeft = Offset(2f, 2f),
                        size = Size(size.width - 4f, size.height - 6f),
                        cornerRadius = CornerRadius(3f, 3f),
                        style = stroke,
                    )
                    drawLine(
                        color = Color(0xFFB9C4CC),
                        start = Offset(size.width * 0.42f, size.height - 4f),
                        end = Offset(size.width * 0.58f, size.height - 4f),
                        strokeWidth = 1.4f,
                    )
                }

                XyWall.WIN10 -> {
                    drawRect(
                        color = Color(0xFFB9C4CC),
                        topLeft = Offset(2f, 4f),
                        size = Size(size.width - 4f, size.height - 9f),
                        style = stroke,
                    )
                    drawLine(
                        color = Color(0xFFB9C4CC),
                        start = Offset(size.width * 0.35f, 4f),
                        end = Offset(size.width * 0.35f, size.height - 5f),
                        strokeWidth = 1.4f,
                    )
                }

                XyWall.WIN11 -> {
                    drawRoundRect(
                        color = Color(0xFFB9C4CC),
                        topLeft = Offset(2f, 4f),
                        size = Size(size.width - 4f, size.height - 9f),
                        cornerRadius = CornerRadius(2f, 2f),
                        style = stroke,
                    )
                    drawLine(
                        color = Color(0xFFB9C4CC),
                        start = Offset(size.width * 0.66f, 4f),
                        end = Offset(size.width * 0.66f, size.height - 5f),
                        strokeWidth = 1.4f,
                    )
                }

                XyWall.NEUTRAL -> {
                    drawCircle(
                        color = Color(0xFFB9C4CC),
                        radius = size.minDimension * 0.36f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = stroke,
                    )
                    drawLine(
                        color = Color(0xFFB9C4CC),
                        start = Offset(size.width / 2f, size.height * 0.14f),
                        end = Offset(size.width / 2f, size.height * 0.86f),
                        strokeWidth = 1.2f,
                    )
                }
            }
        }
    }
}
