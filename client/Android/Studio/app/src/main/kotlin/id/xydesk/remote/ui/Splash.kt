package id.xydesk.remote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.R
import kotlin.math.min

/**
 * Splash XyVerse: layar tunggu sampai app siap.
 *
 * Satu tarikan animasi: HP potret melebar jadi monitor (dengan strip taskbar
 * dan jendela kecil), lalu menyempit jadi rak server dengan lampu yang
 * berkedip, terakhir garis koneksi putus-putus yang mengalir. Semua digambar
 * dari garis tipis pakai token tema — tanpa glow, tanpa gradien dekoratif.
 *
 * Kalau data app belum siap, splash berhenti di frame terakhir dan tetap
 * tampil sampai [ready] true.
 */
@Composable
fun XySplashScreen(
    ready: Boolean,
    dark: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    var animDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 2_000, easing = LinearEasing))
        animDone = true
    }

    LaunchedEffect(animDone, ready) {
        if (animDone && ready) {
            fade.animateTo(0f, tween(durationMillis = 340))
            onDone()
        }
    }

    val ink = MaterialTheme.colorScheme.onBackground
    val line = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.tertiary
    val surface = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .alpha(fade.value.coerceIn(0f, 1f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth(0.74f).height(210.dp)) {
                drawMorph(progress.value, ink, line, accent, surface)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "XyDesk Remote",
                color = ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            )
            Text(
                xy("RDP untuk Windows & Windows Server", "RDP for Windows & Windows Server"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Image(
            painter = painterResource(
                if (dark) R.drawable.xy_logo_h_white else R.drawable.xy_logo_h_black,
            ),
            contentDescription = "XyVerse",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.40f)
                .padding(bottom = 18.dp)
                .alpha(fadeIn(progress.value, 0.08f, 0.42f)),
        )
        if (animDone && !ready) {
            Canvas(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .fillMaxWidth(0.28f)
                    .height(2.dp),
            ) {
                drawLine(line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 2f)
            }
        }
    }
}

private fun fadeIn(p: Float, from: Float, to: Float): Float =
    ((p - from) / (to - from)).coerceIn(0f, 1f)

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

/**
 * Perangkat yang berubah bentuk. Fase:
 *  0.00–0.30 HP potret (home bar + kursor)
 *  0.30–0.62 melebar jadi monitor (taskbar + jendela)
 *  0.62–0.92 menyempit jadi rak server (slot + lampu)
 *  0.86–1.00 garis koneksi mengalir
 */
private fun DrawScope.drawMorph(
    progress: Float,
    ink: Color,
    line: Color,
    accent: Color,
    surface: Color,
) {
    val cx = size.width / 2f
    val cy = size.height * 0.48f
    val unit = min(size.width, size.height)

    val appear = smooth(fadeIn(progress, 0f, 0.18f))
    if (appear <= 0.01f) return
    val toDesktop = smooth(fadeIn(progress, 0.30f, 0.62f))
    val toServer = smooth(fadeIn(progress, 0.62f, 0.92f))
    // Jendela tiap fase dibuat tidak tumpang tindih: detail HP hilang dulu,
    // baru detail desktop muncul, lalu detail desktop hilang dulu sebelum
    // rak server muncul. Tanpa ini strip taskbar ikut kelihatan di fase
    // server dan gambarnya jadi seperti dua perangkat menumpuk.
    val phoneAlpha = appear * (1f - smooth(fadeIn(progress, 0.24f, 0.40f)))
    val deskAlpha = smooth(fadeIn(progress, 0.26f, 0.42f)) *
        (1f - smooth(fadeIn(progress, 0.60f, 0.74f)))
    val srvAlpha = smooth(fadeIn(progress, 0.62f, 0.78f))

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    val w = lerp(lerp(unit * 0.26f, unit * 0.86f, toDesktop), unit * 0.34f, toServer)
    val h = lerp(lerp(unit * 0.52f, unit * 0.50f, toDesktop), unit * 0.66f, toServer)
    val r = lerp(unit * 0.045f, unit * 0.028f, maxOf(toDesktop, toServer))
    val left = cx - w / 2f
    val top = cy - h / 2f

    drawRoundRect(
        color = surface.copy(alpha = 0.30f * appear),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
    )
    drawRoundRect(
        color = line.copy(alpha = 0.95f * appear),
        topLeft = Offset(left, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.6f),
    )
    // Kilau tipis di tepi atas: bikin bentuknya terbaca sebagai layar, bukan
    // kotak kosong.
    drawLine(
        color = ink.copy(alpha = 0.20f * appear),
        start = Offset(left + r, top + 1.2f),
        end = Offset(left + w - r, top + 1.2f),
        strokeWidth = 1.4f,
        cap = StrokeCap.Round,
    )

    val pad = unit * 0.026f
    val sx = left + pad
    val sy = top + pad
    val sw = w - pad * 2f
    val sh = h - pad * 2f

    // --- HP: home bar + kursor
    if (phoneAlpha > 0.02f) {
        val barW = sw * 0.34f
        drawLine(
            ink.copy(alpha = 0.45f * phoneAlpha),
            Offset(cx - barW / 2f, sy + sh - pad * 0.7f),
            Offset(cx + barW / 2f, sy + sh - pad * 0.7f),
            2.4f,
            StrokeCap.Round,
        )
        drawCircle(
            ink.copy(alpha = 0.75f * phoneAlpha),
            radius = unit * 0.011f,
            center = Offset(cx + sw * 0.16f, sy + sh * 0.32f),
        )
    }

    // --- Desktop: strip taskbar + jendela + kursor
    if (deskAlpha > 0.02f) {
        val barH = sh * 0.10f
        drawRect(
            ink.copy(alpha = 0.12f * deskAlpha),
            topLeft = Offset(sx, sy + sh - barH),
            size = Size(sw, barH),
        )
        drawLine(
            ink.copy(alpha = 0.42f * deskAlpha),
            Offset(sx, sy + sh - barH),
            Offset(sx + sw, sy + sh - barH),
            1.4f,
        )
        val winW = sw * 0.46f
        val winH = sh * 0.46f
        val winX = sx + sw * 0.06f
        val winY = sy + sh * 0.14f
        drawRoundRect(
            color = ink.copy(alpha = 0.18f * deskAlpha),
            topLeft = Offset(winX, winY),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(unit * 0.012f, unit * 0.012f),
        )
        drawRoundRect(
            color = ink.copy(alpha = 0.55f * deskAlpha),
            topLeft = Offset(winX, winY),
            size = Size(winW, winH),
            cornerRadius = CornerRadius(unit * 0.012f, unit * 0.012f),
            style = Stroke(width = 1.3f),
        )
        drawCircle(
            ink.copy(alpha = 0.8f * deskAlpha),
            radius = unit * 0.011f,
            center = Offset(sx + sw * (0.30f + 0.40f * toDesktop), sy + sh * (0.70f - 0.20f * toDesktop)),
        )
    }

    // --- Server: slot rak + lampu
    if (srvAlpha > 0.02f) {
        val gap = sh * 0.16f
        for (i in 0 until 4) {
            val y = sy + sh * 0.13f + gap * i
            drawRoundRect(
                color = ink.copy(alpha = 0.20f * srvAlpha),
                topLeft = Offset(sx + sw * 0.12f, y),
                size = Size(sw * 0.76f, gap * 0.52f),
                cornerRadius = CornerRadius(unit * 0.008f, unit * 0.008f),
            )
            drawRoundRect(
                color = ink.copy(alpha = 0.48f * srvAlpha),
                topLeft = Offset(sx + sw * 0.12f, y),
                size = Size(sw * 0.76f, gap * 0.52f),
                cornerRadius = CornerRadius(unit * 0.008f, unit * 0.008f),
                style = Stroke(width = 1.2f),
            )
            val on = ((progress * 5f) + i * 0.7f) % 1f < 0.5f
            drawCircle(
                color = if (on) accent.copy(alpha = 0.95f * srvAlpha)
                else ink.copy(alpha = 0.28f * srvAlpha),
                radius = unit * 0.0085f,
                center = Offset(sx + sw * 0.80f, y + gap * 0.26f),
            )
        }
    }

    // --- garis koneksi yang mengalir
    val linkAlpha = smooth(fadeIn(progress, 0.86f, 1f))
    if (linkAlpha > 0.02f) {
        val y = cy + h / 2f + unit * 0.09f
        val from = cx - w * 1.5f
        val to = cx + w * 1.5f
        val dash = unit * 0.036f
        var x = from
        while (x < to) {
            drawLine(
                line.copy(alpha = 0.9f * linkAlpha),
                Offset(x, y),
                Offset(min(x + dash * 0.6f, to), y),
                2f,
                StrokeCap.Round,
            )
            x += dash
        }
        val dotX = from + (to - from) * ((progress - 0.86f) / 0.14f)
        if (dotX in from..to) {
            drawCircle(accent.copy(alpha = 0.95f * linkAlpha), radius = unit * 0.011f, center = Offset(dotX, y))
        }
    }
}
