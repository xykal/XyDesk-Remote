package id.xydesk.remote.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Splash pembuka XyDesk Remote — Dual-Engine Architecture (Direct PC Stream +
 * Enterprise RDP Core) dengan animasi presisi tinggi, indikator tahapan muat
 * mesin native, dan identitas XyVerse Technology Global.
 */
@Composable
fun XySplashScreen(
    ready: Boolean,
    dark: Boolean = true,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val versionLabel = remember(context) {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "0.5.24"
        }.getOrDefault("0.5.24")
    }
    var visible by remember { mutableStateOf(false) }
    var bootStage by remember { mutableIntStateOf(0) }
    var minTimeElapsed by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "splashAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "splashScale",
    )
    val progress by animateFloatAsState(
        targetValue = when (bootStage) {
            0 -> 0.32f
            1 -> 0.72f
            else -> 1.0f
        },
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "splashProgress",
    )

    val infinite = rememberInfiniteTransition(label = "splashOrbit")
    val orbitAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitAngle",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(280)
        bootStage = 1
        delay(320)
        bootStage = 2
        delay(260)
        minTimeElapsed = true
    }

    LaunchedEffect(ready, minTimeElapsed) {
        if (ready && minTimeElapsed) {
            onDone()
        }
    }

    val stageLabel = when (bootStage) {
        0 -> xy("Memuat mesin native FreeRDP3 & dekoder H.264...", "Loading FreeRDP3 native engine & H.264 decoder...")
        1 -> xy("Menyiapkan pipeline Direct PC Stream & RawInput...", "Preparing Direct PC Stream & RawInput pipeline...")
        else -> xy("Mengaktifkan brankas kredensial AES-256-GCM...", "Activating AES-256-GCM credential vault...")
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF07090D),
                        Color(0xFF0B0F16),
                        Color(0xFF07090C),
                    ),
                ),
            )
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        // Latar kisi arsitektur presisi tipis
        Canvas(Modifier.fillMaxSize()) {
            val step = 36.dp.toPx()
            val gridColor = Color(0xFF1A2230).copy(alpha = 0.22f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1E293B).copy(alpha = 0.42f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, size.height * 0.40f),
                    radius = size.minDimension * 0.62f,
                ),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width / 2f, size.height * 0.40f),
            )
        }

        // Badge Versi di Atas
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(alpha)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF111620))
                .border(1.dp, Color(0xFF232C3D), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0)),
            )
            Text(
                text = "XYDESK REMOTE  ·  v$versionLabel",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.1.sp,
                color = Color(0xFFCBD5E1),
            )
        }

        // Inti Emblem + Identitas Dual-Engine di Tengah
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 360.dp)
                .alpha(alpha)
                .scale(scale),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(132.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Cincin orbit luar berputar halus
                Canvas(Modifier.fillMaxSize()) {
                    val rOuter = size.minDimension * 0.48f
                    val rMid = size.minDimension * 0.39f
                    drawCircle(
                        color = Color(0xFF232C3D),
                        radius = rOuter * pulse,
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                    rotate(orbitAngle) {
                        drawArc(
                            color = Color(0xFF94A3B8).copy(alpha = 0.55f),
                            startAngle = -35f,
                            sweepAngle = 70f,
                            useCenter = false,
                            topLeft = Offset(center.x - rMid, center.y - rMid),
                            size = Size(rMid * 2f, rMid * 2f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = Color(0xFF64748B).copy(alpha = 0.45f),
                            startAngle = 145f,
                            sweepAngle = 70f,
                            useCenter = false,
                            topLeft = Offset(center.x - rMid, center.y - rMid),
                            size = Size(rMid * 2f, rMid * 2f),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }

                // Kotak inti kristal gelap
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF171E2B),
                                    Color(0xFF0E131C),
                                ),
                            ),
                        )
                        .border(1.2.dp, Color(0xFF2D384D), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    val fg = Color(0xFFF1F5F9)
                    val accent = Color(0xFF94A3B8)
                    Canvas(Modifier.size(44.dp)) {
                        val s = size.minDimension
                        val stroke = s * 0.072f
                        // Layar Monitor Fisik PC
                        drawRoundRect(
                            color = fg,
                            topLeft = Offset(s * 0.08f, s * 0.12f),
                            size = Size(s * 0.84f, s * 0.56f),
                            cornerRadius = CornerRadius(s * 0.10f, s * 0.10f),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                        // Gelombang Sinyal Direct Stream di dalam layar
                        val wave = Path().apply {
                            moveTo(s * 0.24f, s * 0.42f)
                            lineTo(s * 0.38f, s * 0.42f)
                            lineTo(s * 0.45f, s * 0.27f)
                            lineTo(s * 0.55f, s * 0.53f)
                            lineTo(s * 0.62f, s * 0.38f)
                            lineTo(s * 0.76f, s * 0.38f)
                        }
                        drawPath(
                            path = wave,
                            color = accent,
                            style = Stroke(width = stroke * 0.82f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                        // Penyangga & Dudukan Monitor Presisi
                        drawLine(
                            color = fg,
                            start = Offset(s * 0.50f, s * 0.68f),
                            end = Offset(s * 0.50f, s * 0.84f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                        drawLine(
                            color = fg,
                            start = Offset(s * 0.30f, s * 0.84f),
                            end = Offset(s * 0.70f, s * 0.84f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "XyDesk Remote",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = Color(0xFFF8FAFC),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = xy(
                    "Mesin Ganda: Direct PC Stream 1:1  &  Windows RDP Desktop",
                    "Dual Engine: 1:1 Direct PC Stream  &  Windows RDP Desktop",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(22.dp))

            // Kartu Arsitektur Terpisah (Koneksi PC vs Koneksi RDP)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SplashEngineCard(
                    badge = "DIRECT PC",
                    title = xy("Koneksi PC", "PC Connect"),
                    subtitle = xy("ID 10-Digit · 60/120 FPS · RawInput", "10-Digit ID · 60/120 FPS · RawInput"),
                    modifier = Modifier.weight(1f),
                )
                SplashEngineCard(
                    badge = "RDP CORE",
                    title = xy("Koneksi RDP", "RDP Session"),
                    subtitle = xy("Multi-User · DISP · Drive RDPDR", "Multi-User · DISP · RDPDR Drive"),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(22.dp))

            // Progress Bar & Status Inisialisasi Live
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF10151F))
                    .border(1.dp, Color(0xFF1E2636), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stageLabel,
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1),
                        maxLines = 1,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF1E293B)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.05f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFE2E8F0)),
                    )
                }
            }
        }

        // Signature XyVerse Technology Global di Bawah
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "XyVerse Technology Global",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = Color(0xFFCBD5E1),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "rdp.xydesk.my.id  ·  rilisin.xyverse.my.id",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFF64748B),
            )
        }
    }
}

@Composable
private fun SplashEngineCard(
    badge: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF10151F))
            .border(1.dp, Color(0xFF222B3B), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = badge,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
            color = Color(0xFF94A3B8),
        )
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF1F5F9),
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = Color(0xFF64748B),
            lineHeight = 13.sp,
        )
    }
}
