package id.xydesk.remote.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.launch

/**
 * Komponen Emoji Reaksi Asli (Native Color Emoji) dengan Animasi Interaktif
 * (Pop-In Elastic Bounce + Gerakan Ekspresif Hidup sesuai jenis emoji + Ring Burst).
 */
@Composable
fun IosWhatsAppEmoji3D(
    codeOrEmoji: String = "",
    tag: String = "",
    size: Dp = 18.dp,
    animated: Boolean = true,
    animateTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val activeInput = codeOrEmoji.ifBlank { tag }
    val norm = remember(activeInput) { normalizeReactionToIosType(activeInput) }
    val glyph = remember(norm, activeInput) { resolveNativeEmojiGlyph(norm, activeInput) }

    val popScale = remember(activeInput) { Animatable(if (animated) 0.45f else 1f) }
    val popTilt = remember(activeInput) { Animatable(0f) }
    val burstProgress = remember(activeInput) { Animatable(0f) }

    LaunchedEffect(activeInput, animateTrigger) {
        if (animated) {
            launch {
                popScale.snapTo(0.45f)
                popScale.animateTo(
                    targetValue = 1.24f,
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                )
                popScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                popTilt.snapTo(-11f)
                popTilt.animateTo(11f, tween(durationMillis = 140, easing = FastOutSlowInEasing))
                popTilt.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            launch {
                burstProgress.snapTo(0f)
                burstProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Box(
        modifier = modifier.requiredSize(size),
        contentAlignment = Alignment.Center
    ) {
        if (animated && burstProgress.value in 0.02f..0.95f) {
            val bp = burstProgress.value
            val burstAlpha = (1f - bp).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                val maxR = this.size.minDimension * 0.56f
                for (i in 0 until 6) {
                    val a = (i * 60f) * (PI.toFloat() / 180f)
                    val dist = maxR * (0.45f + 0.55f * bp)
                    drawCircle(
                        color = Color(0xFFFBBF24).copy(alpha = 0.65f * burstAlpha),
                        radius = (this.size.minDimension * 0.07f) * (1f - bp * 0.5f),
                        center = Offset(cx + cos(a) * dist, cy + sin(a) * dist)
                    )
                }
            }
        }
        Text(
            text = glyph,
            fontSize = (size.value * 0.78f).sp,
            modifier = Modifier
                .scale(popScale.value)
                .rotate(popTilt.value)
        )
    }
}

private fun resolveNativeEmojiGlyph(norm: String, raw: String): String {
    val trimmed = raw.trim()
    // Jika input sudah berupa karakter emoji Unicode (bukan tag ASCII), pertahankan karakter aslinya 1:1
    // supaya tidak ada 2 emoji berbeda yang dipaksa berubah menjadi 1 gambar yang sama.
    if (trimmed.isNotEmpty() && trimmed.any { it.code > 0x2000 || Character.isSurrogate(it) }) {
        return trimmed
    }
    return when (norm) {
        "heart" -> "\u2764\uFE0F"
        "fire" -> "\uD83D\uDD25"
        "like" -> "\uD83D\uDC4D"
        "dislike" -> "\uD83D\uDC4E"
        "laugh" -> "\uD83D\uDE02"
        "rofl" -> "\uD83E\uDD23"
        "sob" -> "\uD83D\uDE2D"
        "love_eyes" -> "\uD83D\uDE0D"
        "smiling_hearts" -> "\uD83E\uDD70"
        "cool" -> "\uD83D\uDE0E"
        "think" -> "\uD83E\uDD14"
        "party" -> "\uD83C\uDF89"
        "partying_face" -> "\uD83E\uDD73"
        "wow" -> "\uD83D\uDE2E"
        "scream" -> "\uD83D\uDE31"
        "angry" -> "\uD83D\uDE21"
        "cold" -> "\uD83E\uDD76"
        "mindblown" -> "\uD83E\uDD2F"
        "skull" -> "\uD83D\uDC80"
        "hundred" -> "\uD83D\uDCAF"
        "pray" -> "\uD83D\uDE4F"
        "respect" -> "\uD83E\uDEE1"
        "clap" -> "\uD83D\uDC4F"
        "bolt" -> "\u26A1"
        "rocket" -> "\uD83D\uDE80"
        "gem" -> "\uD83D\uDC8E"
        "star" -> "\u2B50"
        "sparkle" -> "\u2728"
        "crown" -> "\uD83D\uDC51"
        "trophy" -> "\uD83C\uDFC6"
        "check" -> "\u2705"
        "sleep" -> "\uD83D\uDE34"
        "popcorn" -> "\uD83C\uDF7F"
        else -> "\uD83D\uDD25"
    }
}

fun normalizeReactionToIosType(raw: String): String {
    return when (raw.trim().lowercase()) {
        "heart", "❤️", "❤", "💜", "💖", "💘", "💕" -> "heart"
        "fire", "🔥" -> "fire"
        "like", "+1", "👍", "👍🏻", "👍🏼" -> "like"
        "dislike", "-1", "👎" -> "dislike"
        "laugh", "lol", "😂" -> "laugh"
        "rofl", "🤣" -> "rofl"
        "sob", "😭", "😢" -> "sob"
        "love_eyes", "😍" -> "love_eyes"
        "smiling_hearts", "🥰" -> "smiling_hearts"
        "cool", "😎" -> "cool"
        "think", "🤔" -> "think"
        "party", "🎉" -> "party"
        "partying_face", "🥳" -> "partying_face"
        "wow", "😮" -> "wow"
        "scream", "😱" -> "scream"
        "angry", "😡" -> "angry"
        "cold", "🥶" -> "cold"
        "mindblown", "🤯" -> "mindblown"
        "skull", "💀", "☠️" -> "skull"
        "hundred", "100", "💯" -> "hundred"
        "pray", "🙏" -> "pray"
        "respect", "salute", "🫡" -> "respect"
        "clap", "👏" -> "clap"
        "bolt", "⚡" -> "bolt"
        "rocket", "🚀" -> "rocket"
        "gem", "💎" -> "gem"
        "star", "⭐" -> "star"
        "sparkle", "✨" -> "sparkle"
        "crown", "king", "👑" -> "crown"
        "trophy", "gg", "🏆" -> "trophy"
        "check", "✅" -> "check"
        "sleep", "😴" -> "sleep"
        "popcorn", "🍿" -> "popcorn"
        else -> "fire"
    }
}

private fun DrawScope.drawIosSphereBase(
    cx: Float,
    cy: Float,
    r: Float,
    colors: List<Color>
) {
    // Bayangan bawah halus khas iOS 3D
    drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = r * 0.96f,
        center = Offset(cx, cy + r * 0.08f)
    )
    // Bola 3D radial berlapis
    drawCircle(
        brush = Brush.radialGradient(
            colors = colors,
            center = Offset(cx - r * 0.28f, cy - r * 0.32f),
            radius = r * 1.38f
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    // Rim light bawah
    drawCircle(
        color = Color.White.copy(alpha = 0.22f),
        radius = r * 0.96f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.05f)
    )
    // Kilau spekular 3D khas Apple Color Emoji di bagian atas
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.72f),
                Color.White.copy(alpha = 0.04f)
            ),
            startY = cy - r * 0.88f,
            endY = cy - r * 0.18f
        ),
        topLeft = Offset(cx - r * 0.56f, cy - r * 0.86f),
        size = Size(r * 1.12f, r * 0.54f)
    )
}

private fun DrawScope.drawIos3DFace(cx: Float, cy: Float, r: Float, mode: String) {
    val baseColors = when (mode) {
        "angry" -> listOf(Color(0xFFFF9A76), Color(0xFFFF3B30), Color(0xFFB80D18))
        "cold" -> listOf(Color(0xFFB8F2FF), Color(0xFF32ADE6), Color(0xFF0062CC))
        else -> listOf(Color(0xFFFFF59D), Color(0xFFFFCC00), Color(0xFFF57C00))
    }
    drawIosSphereBase(cx, cy, r, baseColors)

    val featureDark = Color(0xFF4E2600)
    when (mode) {
        "laugh", "rofl" -> {
            // Mata melengkung bahagia ><
            val eyeStroke = Stroke(width = r * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val leftEye = Path().apply {
                moveTo(cx - r * 0.52f, cy - r * 0.24f)
                lineTo(cx - r * 0.22f, cy - r * 0.10f)
                lineTo(cx - r * 0.52f, cy + r * 0.02f)
            }
            val rightEye = Path().apply {
                moveTo(cx + r * 0.52f, cy - r * 0.24f)
                lineTo(cx + r * 0.22f, cy - r * 0.10f)
                lineTo(cx + r * 0.52f, cy + r * 0.02f)
            }
            drawPath(leftEye, color = featureDark, style = eyeStroke)
            drawPath(rightEye, color = featureDark, style = eyeStroke)

            // Mulut tawa lebar dengan gigi putih & lidah merah muda khas iOS 😂
            val mouth = Path().apply {
                moveTo(cx - r * 0.56f, cy + r * 0.14f)
                quadraticBezierTo(cx, cy + r * 0.88f, cx + r * 0.56f, cy + r * 0.14f)
                close()
            }
            drawPath(mouth, color = Color(0xFF5A1807))
            // Baris gigi putih di atas mulut
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - r * 0.44f, cy + r * 0.14f),
                size = Size(r * 0.88f, r * 0.16f),
                cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
            )
            // Air mata bahagia biru glossy di kiri & kanan (khusus 😂 / 🤣)
            val tearBrush = Brush.radialGradient(
                colors = listOf(Color(0xFFB3E5FC), Color(0xFF00B0FF), Color(0xFF0077C2))
            )
            drawOval(
                brush = tearBrush,
                topLeft = Offset(cx - r * 0.94f, cy - r * 0.02f),
                size = Size(r * 0.34f, r * 0.48f)
            )
            drawOval(
                brush = tearBrush,
                topLeft = Offset(cx + r * 0.60f, cy - r * 0.02f),
                size = Size(r * 0.34f, r * 0.48f)
            )
        }
        "sob" -> {
            // Alis sedih & aliran air mata deras biru glossy khas 😭 iOS
            drawOval(
                color = featureDark,
                topLeft = Offset(cx - r * 0.46f, cy - r * 0.26f),
                size = Size(r * 0.24f, r * 0.20f)
            )
            drawOval(
                color = featureDark,
                topLeft = Offset(cx + r * 0.22f, cy - r * 0.26f),
                size = Size(r * 0.24f, r * 0.20f)
            )
            val streamBrush = Brush.verticalGradient(
                colors = listOf(Color(0xFF80D8FF), Color(0xFF0091EA))
            )
            drawRoundRect(
                brush = streamBrush,
                topLeft = Offset(cx - r * 0.44f, cy - r * 0.08f),
                size = Size(r * 0.22f, r * 0.86f),
                cornerRadius = CornerRadius(r * 0.11f, r * 0.11f)
            )
            drawRoundRect(
                brush = streamBrush,
                topLeft = Offset(cx + r * 0.22f, cy - r * 0.08f),
                size = Size(r * 0.22f, r * 0.86f),
                cornerRadius = CornerRadius(r * 0.11f, r * 0.11f)
            )
            drawOval(
                color = Color(0xFF5A1807),
                topLeft = Offset(cx - r * 0.26f, cy + r * 0.18f),
                size = Size(r * 0.52f, r * 0.40f)
            )
        }
        "love_eyes" -> {
            // Dua mata hati 3D merah menyala khas 😍 iOS
            drawIos3DHeart(cx - r * 0.34f, cy - r * 0.12f, r * 0.34f)
            drawIos3DHeart(cx + r * 0.34f, cy - r * 0.12f, r * 0.34f)
            val smile = Path().apply {
                moveTo(cx - r * 0.46f, cy + r * 0.22f)
                quadraticBezierTo(cx, cy + r * 0.72f, cx + r * 0.46f, cy + r * 0.22f)
                close()
            }
            drawPath(smile, color = Color(0xFF5A1807))
        }
        "cool" -> {
            // Kacamata hitam glossy khas 😎 iOS
            val glasses = Brush.verticalGradient(
                colors = listOf(Color(0xFF3A3A3C), Color(0xFF111113))
            )
            drawRoundRect(
                brush = glasses,
                topLeft = Offset(cx - r * 0.76f, cy - r * 0.28f),
                size = Size(r * 0.68f, r * 0.42f),
                cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
            )
            drawRoundRect(
                brush = glasses,
                topLeft = Offset(cx + r * 0.08f, cy - r * 0.28f),
                size = Size(r * 0.68f, r * 0.42f),
                cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
            )
            drawLine(
                color = Color(0xFF1C1C1E),
                start = Offset(cx - r * 0.88f, cy - r * 0.18f),
                end = Offset(cx + r * 0.88f, cy - r * 0.18f),
                strokeWidth = r * 0.10f,
                cap = StrokeCap.Round
            )
            val smirk = Path().apply {
                moveTo(cx - r * 0.38f, cy + r * 0.34f)
                quadraticBezierTo(cx + r * 0.06f, cy + r * 0.56f, cx + r * 0.44f, cy + r * 0.24f)
            }
            drawPath(smirk, color = featureDark, style = Stroke(width = r * 0.12f, cap = StrokeCap.Round))
        }
        else -> {
            // Mata bulat 3D + senyum hangat khas iOS
            drawOval(
                color = featureDark,
                topLeft = Offset(cx - r * 0.44f, cy - r * 0.26f),
                size = Size(r * 0.22f, r * 0.30f)
            )
            drawOval(
                color = featureDark,
                topLeft = Offset(cx + r * 0.22f, cy - r * 0.26f),
                size = Size(r * 0.22f, r * 0.30f)
            )
            drawCircle(color = Color.White.copy(alpha = 0.8f), radius = r * 0.05f, center = Offset(cx - r * 0.36f, cy - r * 0.18f))
            drawCircle(color = Color.White.copy(alpha = 0.8f), radius = r * 0.05f, center = Offset(cx + r * 0.30f, cy - r * 0.18f))
            if (mode == "wow" || mode == "mindblown") {
                drawOval(
                    color = Color(0xFF5A1807),
                    topLeft = Offset(cx - r * 0.22f, cy + r * 0.16f),
                    size = Size(r * 0.44f, r * 0.48f)
                )
            } else {
                val smile = Path().apply {
                    moveTo(cx - r * 0.42f, cy + r * 0.22f)
                    quadraticBezierTo(cx, cy + r * 0.62f, cx + r * 0.42f, cy + r * 0.22f)
                }
                drawPath(smile, color = featureDark, style = Stroke(width = r * 0.13f, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.drawIos3DHeart(cx: Float, cy: Float, r: Float) {
    val path = Path().apply {
        moveTo(cx, cy + r * 0.84f)
        cubicTo(cx - r * 1.15f, cy + r * 0.18f, cx - r * 0.95f, cy - r * 0.78f, cx, cy - r * 0.26f)
        cubicTo(cx + r * 0.95f, cy - r * 0.78f, cx + r * 1.15f, cy + r * 0.18f, cx, cy + r * 0.84f)
        close()
    }
    drawPath(
        path = path,
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF8FA3),
                Color(0xFFFF2D55),
                Color(0xFFC9184A),
                Color(0xFF800F2F)
            ),
            center = Offset(cx - r * 0.28f, cy - r * 0.32f),
            radius = r * 1.45f
        )
    )
    // Kilau spekular 3D di kubah kiri & kanan hati khas ❤️ iOS
    drawOval(
        color = Color.White.copy(alpha = 0.62f),
        topLeft = Offset(cx - r * 0.58f, cy - r * 0.46f),
        size = Size(r * 0.34f, r * 0.20f)
    )
    drawOval(
        color = Color.White.copy(alpha = 0.38f),
        topLeft = Offset(cx + r * 0.18f, cy - r * 0.44f),
        size = Size(r * 0.24f, r * 0.14f)
    )
}

private fun DrawScope.drawIos3DFire(cx: Float, cy: Float, r: Float) {
    // Lidah api luar (Merah-Oranye 3D)
    val outer = Path().apply {
        moveTo(cx, cy - r * 0.96f)
        cubicTo(cx + r * 0.65f, cy - r * 0.38f, cx + r * 0.92f, cy + r * 0.25f, cx + r * 0.48f, cy + r * 0.82f)
        quadraticBezierTo(cx, cy + r * 1.04f, cx - r * 0.48f, cy + r * 0.82f)
        cubicTo(cx - r * 0.92f, cy + r * 0.25f, cx - r * 0.56f, cy - r * 0.28f, cx - r * 0.20f, cy - r * 0.56f)
        quadraticBezierTo(cx - r * 0.12f, cy - r * 0.22f, cx, cy - r * 0.96f)
        close()
    }
    drawPath(
        path = outer,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFF9500), Color(0xFFFF3B30), Color(0xFFC41E3A))
        )
    )
    // Lidah api tengah (Kuning-Emas 3D)
    val mid = Path().apply {
        moveTo(cx + r * 0.04f, cy - r * 0.38f)
        cubicTo(cx + r * 0.46f, cy + r * 0.04f, cx + r * 0.52f, cy + r * 0.52f, cx, cy + r * 0.84f)
        cubicTo(cx - r * 0.52f, cy + r * 0.52f, cx - r * 0.40f, cy + r * 0.04f, cx + r * 0.04f, cy - r * 0.38f)
        close()
    }
    drawPath(
        path = mid,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFF59D), Color(0xFFFFCC00), Color(0xFFFF9500))
        )
    )
    // Inti api putih-kuning terang
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFFFF176), Color.Transparent)
        ),
        radius = r * 0.34f,
        center = Offset(cx, cy + r * 0.52f)
    )
}

private fun DrawScope.drawIos3DThumb(cx: Float, cy: Float, r: Float, up: Boolean) {
    val dir = if (up) 1f else -1f
    val goldBrush = Brush.radialGradient(
        colors = listOf(Color(0xFFFFF59D), Color(0xFFFFCC00), Color(0xFFE68A00)),
        center = Offset(cx - r * 0.2f, cy - r * 0.2f),
        radius = r * 1.3f
    )
    // Kepalan tangan 3D
    drawRoundRect(
        brush = goldBrush,
        topLeft = Offset(cx - r * 0.32f, cy - r * 0.18f),
        size = Size(r * 0.88f, r * 0.72f),
        cornerRadius = CornerRadius(r * 0.24f, r * 0.24f)
    )
    // Ibu jari 3D
    drawRoundRect(
        brush = goldBrush,
        topLeft = Offset(cx - r * 0.14f, if (up) cy - r * 0.82f else cy + r * 0.18f),
        size = Size(r * 0.38f, r * 0.72f),
        cornerRadius = CornerRadius(r * 0.19f, r * 0.19f)
    )
    // Pergelangan tangan biru glossy
    drawRoundRect(
        brush = Brush.verticalGradient(colors = listOf(Color(0xFF5AC8FA), Color(0xFF007AFF))),
        topLeft = Offset(cx - r * 0.74f, cy - r * 0.14f * dir - r * 0.30f),
        size = Size(r * 0.36f, r * 0.68f),
        cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
    )
}

private fun DrawScope.drawIos3DSkull(cx: Float, cy: Float, r: Float) {
    drawIosSphereBase(
        cx,
        cy - r * 0.08f,
        r * 0.84f,
        listOf(Color(0xFFFFFFFF), Color(0xFFE5E5EA), Color(0xFFAEAEB2))
    )
    drawRoundRect(
        color = Color(0xFFD1D1D6),
        topLeft = Offset(cx - r * 0.42f, cy + r * 0.32f),
        size = Size(r * 0.84f, r * 0.46f),
        cornerRadius = CornerRadius(r * 0.16f, r * 0.16f)
    )
    drawCircle(color = Color(0xFF1C1C1E), radius = r * 0.20f, center = Offset(cx - r * 0.30f, cy - r * 0.06f))
    drawCircle(color = Color(0xFF1C1C1E), radius = r * 0.20f, center = Offset(cx + r * 0.30f, cy - r * 0.06f))
}

private fun DrawScope.drawIos3DHundred(cx: Float, cy: Float, r: Float) {
    val redBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFF5E62), Color(0xFFFF2D55), Color(0xFFC8102E))
    )
    val stroke = Stroke(width = r * 0.20f, cap = StrokeCap.Round)
    drawLine(brush = redBrush, start = Offset(cx - r * 0.68f, cy - r * 0.42f), end = Offset(cx - r * 0.76f, cy + r * 0.26f), strokeWidth = r * 0.20f, cap = StrokeCap.Round)
    drawOval(brush = redBrush, topLeft = Offset(cx - r * 0.42f, cy - r * 0.42f), size = Size(r * 0.42f, r * 0.68f), style = stroke)
    drawOval(brush = redBrush, topLeft = Offset(cx + r * 0.16f, cy - r * 0.42f), size = Size(r * 0.42f, r * 0.68f), style = stroke)
    drawLine(brush = redBrush, start = Offset(cx - r * 0.78f, cy + r * 0.54f), end = Offset(cx + r * 0.72f, cy + r * 0.44f), strokeWidth = r * 0.16f, cap = StrokeCap.Round)
    drawLine(brush = redBrush, start = Offset(cx - r * 0.66f, cy + r * 0.76f), end = Offset(cx + r * 0.78f, cy + r * 0.66f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
}

private fun DrawScope.drawIos3DPray(cx: Float, cy: Float, r: Float) {
    val handBrush = Brush.verticalGradient(colors = listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFE65100)))
    val leftHand = Path().apply {
        moveTo(cx - r * 0.04f, cy - r * 0.82f)
        lineTo(cx - r * 0.44f, cy + r * 0.36f)
        lineTo(cx - r * 0.04f, cy + r * 0.48f)
        close()
    }
    val rightHand = Path().apply {
        moveTo(cx + r * 0.04f, cy - r * 0.82f)
        lineTo(cx + r * 0.44f, cy + r * 0.36f)
        lineTo(cx + r * 0.04f, cy + r * 0.48f)
        close()
    }
    drawPath(leftHand, brush = handBrush)
    drawPath(rightHand, brush = handBrush)
    drawRoundRect(
        color = Color(0xFF007AFF),
        topLeft = Offset(cx - r * 0.56f, cy + r * 0.42f),
        size = Size(r * 1.12f, r * 0.40f),
        cornerRadius = CornerRadius(r * 0.12f, r * 0.12f)
    )
}

private fun DrawScope.drawIos3DClap(cx: Float, cy: Float, r: Float) {
    drawIosSphereBase(cx, cy + r * 0.08f, r * 0.76f, listOf(Color(0xFFFFF59D), Color(0xFFFFCA28), Color(0xFFF57F17)))
    // Garis efek tepuk tangan di atas
    val rayColor = Color(0xFFFFD60A)
    drawLine(rayColor, Offset(cx, cy - r * 0.94f), Offset(cx, cy - r * 0.68f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
    drawLine(rayColor, Offset(cx - r * 0.56f, cy - r * 0.76f), Offset(cx - r * 0.36f, cy - r * 0.54f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
    drawLine(rayColor, Offset(cx + r * 0.56f, cy - r * 0.76f), Offset(cx + r * 0.36f, cy - r * 0.54f), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
}

private fun DrawScope.drawIos3DBolt(cx: Float, cy: Float, r: Float) {
    val bolt = Path().apply {
        moveTo(cx + r * 0.16f, cy - r * 0.92f)
        lineTo(cx - r * 0.62f, cy + r * 0.08f)
        lineTo(cx - r * 0.04f, cy + r * 0.08f)
        lineTo(cx - r * 0.22f, cy + r * 0.92f)
        lineTo(cx + r * 0.62f, cy - r * 0.08f)
        lineTo(cx + r * 0.04f, cy - r * 0.08f)
        close()
    }
    drawPath(
        path = bolt,
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFFFF9C4), Color(0xFFFFD60A), Color(0xFFFF9500)))
    )
}

private fun DrawScope.drawIos3DGem(cx: Float, cy: Float, r: Float) {
    val gem = Path().apply {
        moveTo(cx - r * 0.48f, cy - r * 0.68f)
        lineTo(cx + r * 0.48f, cy - r * 0.68f)
        lineTo(cx + r * 0.88f, cy - r * 0.18f)
        lineTo(cx, cy + r * 0.86f)
        lineTo(cx - r * 0.88f, cy - r * 0.18f)
        close()
    }
    drawPath(
        path = gem,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFE0F7FA), Color(0xFF32ADE6), Color(0xFF5E5CE6), Color(0xFFA855F7))
        )
    )
}

private fun DrawScope.drawIos3DCrown(cx: Float, cy: Float, r: Float) {
    val crown = Path().apply {
        moveTo(cx - r * 0.72f, cy + r * 0.56f)
        lineTo(cx - r * 0.82f, cy - r * 0.36f)
        lineTo(cx - r * 0.32f, cy + r * 0.06f)
        lineTo(cx, cy - r * 0.64f)
        lineTo(cx + r * 0.32f, cy + r * 0.06f)
        lineTo(cx + r * 0.82f, cy - r * 0.36f)
        lineTo(cx + r * 0.72f, cy + r * 0.56f)
        close()
    }
    drawPath(
        path = crown,
        brush = Brush.verticalGradient(colors = listOf(Color(0xFFFAF5FF), Color(0xFFC084FC), Color(0xFF7E22CE)))
    )
}

private fun DrawScope.drawIos3DCheck(cx: Float, cy: Float, r: Float) {
    drawIosSphereBase(cx, cy, r, listOf(Color(0xFF69F0AE), Color(0xFF34C759), Color(0xFF1B8A3D)))
    val check = Path().apply {
        moveTo(cx - r * 0.44f, cy + r * 0.04f)
        lineTo(cx - r * 0.12f, cy + r * 0.36f)
        lineTo(cx + r * 0.46f, cy - r * 0.28f)
    }
    drawPath(check, color = Color.White, style = Stroke(width = r * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Data coretan garis (stroke) dan stiker emoji pada Pre-Send Media Studio.
 */
data class MediaDrawStroke(
    val points: List<Offset>, // normalized 0..1 coordinates
    val color: Color,
    val widthRatio: Float
)

data class MediaStickerItem(
    val code: String,
    val emojiChar: String,
    val normX: Float,
    val normY: Float,
    val scale: Float = 1f
)

/**
 * Studio Preview & Edit Media (Foto / Video / Berkas) sesuai UI Utama XyDesk Remote
 * (Matte Monokrom/Slate Bersih, Ikon Vektor `XyIcons`, Tanpa Emoji):
 * - Foto: Coretan Garis (multi-warna + ketebalan + Undo), Teks Overlay, Putar 90°, & Sekali Lihat (1×)
 * - Video: Live Video Player sesuai Aspect Ratio Asli (9:16 portrait / 16:9 landscape tanpa bar hitam),
 *   Play/Pause, Atur / Cut (Trim Durasi Awal & Akhir), Toggle Suara (Mute/Unmute), & Sekali Lihat (1×)
 * - Berkas/Dokumen: Pratinjau informasi dokumen bersih dengan ikon vektor
 */
@Composable
fun PreSendMediaStudioDialog(
    initialAttachmentData: String,
    initialAttachmentName: String,
    initialAttachmentType: String,
    initialCaption: String = "",
    initialViewOnce: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmSend: (finalData: String, finalName: String, finalType: String, caption: String, viewOnce: Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var caption by remember(initialCaption) { mutableStateOf(initialCaption) }
    var viewOnce by remember(initialViewOnce) { mutableStateOf(initialViewOnce) }
    var activeTool by remember { mutableStateOf("none") } // "none", "draw", "text", "trim"
    var selectedColor by remember { mutableStateOf(Color(0xFFA78BFA)) }
    var strokeRatio by remember { mutableFloatStateOf(0.014f) }
    var overlayText by remember { mutableStateOf("") }
    var rotationQuarter by remember { mutableIntStateOf(0) }

    val strokes = remember { mutableStateListOf<MediaDrawStroke>() }
    val currentPoints = remember { mutableStateListOf<Offset>() }
    val stickers = remember { mutableStateListOf<MediaStickerItem>() }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    val isVideo = initialAttachmentType == "video" || initialAttachmentData.startsWith("data:video/")
    val isPhoto = !isVideo && (initialAttachmentType == "photo" || initialAttachmentType == "image" || initialAttachmentData.startsWith("data:image/"))

    val sourceBitmap = remember(initialAttachmentData, isPhoto) {
        if (!isPhoto) null
        else runCatching {
            val raw = initialAttachmentData.substringAfter("base64,", initialAttachmentData)
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    val rotatedPreviewBitmap = remember(sourceBitmap, rotationQuarter) {
        if (sourceBitmap == null) null
        else if (rotationQuarter % 4 == 0) sourceBitmap
        else {
            val m = Matrix().apply { postRotate((rotationQuarter % 4) * 90f) }
            runCatching {
                Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, m, true)
            }.getOrDefault(sourceBitmap)
        }
    }

    // Ekstraksi berkas video sementara + metadata rasio asli (9:16 vs 16:9) & durasi untuk Live Preview + Cut/Trim
    val tempVideoFile = remember(initialAttachmentData, isVideo) {
        if (!isVideo || initialAttachmentData.isBlank()) null
        else runCatching {
            val raw = initialAttachmentData.substringAfter("base64,", initialAttachmentData)
            val bytes = Base64.decode(raw, Base64.DEFAULT)
            val f = java.io.File(context.cacheDir, "studio_preview_${System.currentTimeMillis()}.mp4")
            f.writeBytes(bytes)
            f
        }.getOrNull()
    }

    var videoDurationMs by remember(tempVideoFile) { mutableIntStateOf(0) }
    var videoAspectRatio by remember(tempVideoFile) { mutableFloatStateOf(9f / 16f) }
    var trimStartMs by remember(tempVideoFile) { mutableIntStateOf(0) }
    var trimEndMs by remember(tempVideoFile) { mutableIntStateOf(0) }
    var videoMuted by remember(tempVideoFile) { mutableStateOf(false) }
    var videoPlaying by remember(tempVideoFile) { mutableStateOf(true) }
    var currentVideoPosMs by remember(tempVideoFile) { mutableIntStateOf(0) }
    var videoViewRef by remember(tempVideoFile) { mutableStateOf<android.widget.VideoView?>(null) }
    var mediaPlayerRef by remember(tempVideoFile) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isProcessingSend by remember { mutableStateOf(false) }

    LaunchedEffect(tempVideoFile) {
        val f = tempVideoFile ?: return@LaunchedEffect
        runCatching {
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(f.absolutePath)
                val dur = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
                val w = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val h = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rot = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                val effW = if (rot == 90 || rot == 270) h else w
                val effH = if (rot == 90 || rot == 270) w else h
                if (effW > 0 && effH > 0) {
                    videoAspectRatio = (effW.toFloat() / effH.toFloat()).coerceIn(0.45f, 2.2f)
                }
                if (dur > 0) {
                    videoDurationMs = dur
                    trimStartMs = 0
                    trimEndMs = dur
                }
            } finally {
                runCatching { mmr.release() }
            }
        }
    }

    // Loop pemutaran video mengikuti batas Cut/Trim (trimStartMs .. trimEndMs)
    LaunchedEffect(tempVideoFile, videoPlaying, trimStartMs, trimEndMs, videoMuted) {
        val mp = mediaPlayerRef
        val vol = if (videoMuted) 0f else 1f
        runCatching { mp?.setVolume(vol, vol) }
        while (isVideo && tempVideoFile != null) {
            val vv = videoViewRef
            if (vv != null && videoPlaying) {
                val pos = runCatching { vv.currentPosition }.getOrDefault(0)
                currentVideoPosMs = pos
                val endBound = if (trimEndMs > trimStartMs + 300) trimEndMs else videoDurationMs
                if (endBound > 0 && pos >= endBound) {
                    runCatching {
                        vv.seekTo(trimStartMs)
                        if (!vv.isPlaying) vv.start()
                    }
                } else if (pos < trimStartMs) {
                    runCatching { vv.seekTo(trimStartMs) }
                }
            }
            kotlinx.coroutines.delay(120L)
        }
    }

    androidx.compose.runtime.DisposableEffect(tempVideoFile) {
        XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue += 1
        onDispose {
            XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue =
                (XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue - 1).coerceAtLeast(0)
            runCatching { videoViewRef?.stopPlayback() }
            runCatching { mediaPlayerRef?.stop() }
            runCatching { mediaPlayerRef?.release() }
            videoViewRef = null
            mediaPlayerRef = null
        }
    }

    val drawColors = listOf(
        Color(0xFFA78BFA), // Sky Blue (UI Accent)
        Color(0xFFD946EF), // Red
        Color(0xFFFBBF24), // Amber
        Color(0xFF10B981), // Emerald
        Color(0xFFFFFFFF), // White
        Color(0xFF09090B)  // Black
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0C10)),
            color = Color(0xFF0A0C10)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar Studio (Sesuai UI Utama XyDesk Remote, Ikon Vektor Murni Tanpa Emoji)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF141821),
                            border = BorderStroke(0.75.dp, Color(0xFF232938)),
                            modifier = Modifier.clickable { onDismiss() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = id.xydesk.remote.ui.components.XyIcons.Close,
                                    contentDescription = "Batal",
                                    tint = Color(0xFFF8FAFC),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Batal",
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPhoto) {
                                StudioToolChip(
                                    icon = id.xydesk.remote.ui.components.XyIcons.Edit,
                                    label = "Coret",
                                    active = activeTool == "draw",
                                    onClick = { activeTool = if (activeTool == "draw") "none" else "draw" }
                                )
                                StudioToolChip(
                                    icon = id.xydesk.remote.ui.components.XyIcons.Keyboard,
                                    label = "Teks",
                                    active = activeTool == "text",
                                    onClick = { activeTool = if (activeTool == "text") "none" else "text" }
                                )
                                StudioToolChip(
                                    icon = id.xydesk.remote.ui.components.XyIcons.Refresh,
                                    label = "Putar",
                                    active = false,
                                    onClick = { rotationQuarter = (rotationQuarter + 1) % 4 }
                                )
                                if (strokes.isNotEmpty()) {
                                    StudioToolChip(
                                        icon = id.xydesk.remote.ui.components.XyIcons.ChevronLeft,
                                        label = "Undo",
                                        active = false,
                                        onClick = {
                                            if (strokes.isNotEmpty()) {
                                                strokes.removeAt(strokes.lastIndex)
                                            }
                                        }
                                    )
                                }
                            } else if (isVideo) {
                                StudioToolChip(
                                    icon = id.xydesk.remote.ui.components.XyIcons.Tune,
                                    label = "Potong / Cut",
                                    active = activeTool == "trim" || activeTool == "none",
                                    onClick = { activeTool = if (activeTool == "trim") "none" else "trim" }
                                )
                                StudioToolChip(
                                    icon = if (videoMuted) id.xydesk.remote.ui.components.XyIcons.VolumeOff else id.xydesk.remote.ui.components.XyIcons.VolumeOn,
                                    label = if (videoMuted) "Tanpa Suara" else "Suara ON",
                                    active = !videoMuted,
                                    onClick = {
                                        videoMuted = !videoMuted
                                        val vol = if (videoMuted) 0f else 1f
                                        runCatching { mediaPlayerRef?.setVolume(vol, vol) }
                                    }
                                )
                            }
                        }
                    }

                    // Sub-bar untuk alat Drawing Garis pada Foto
                    AnimatedVisibility(visible = isPhoto && activeTool == "draw") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF12151D))
                                .border(0.75.dp, Color(0xFF1E222C), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                drawColors.forEach { c ->
                                    val selected = c == selectedColor
                                    Box(
                                        modifier = Modifier
                                            .requiredSize(if (selected) 22.dp else 18.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) Color.White else Color.White.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColor = c }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Tipis" to 0.008f, "Sedang" to 0.015f, "Tebal" to 0.026f).forEach { (lbl, ratio) ->
                                    val sel = kotlin.math.abs(strokeRatio - ratio) < 0.002f
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (sel) Color(0xFFF8FAFC) else Color(0xFF1B202C),
                                        modifier = Modifier.clickable { strokeRatio = ratio }
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = if (sel) Color(0xFF0A0C10) else Color(0xFFCBD5E1),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sub-bar untuk Teks Overlay di atas foto
                    AnimatedVisibility(visible = isPhoto && activeTool == "text") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF12151D))
                                .border(0.75.dp, Color(0xFF1E222C), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Teks:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Box(modifier = Modifier.weight(1f)) {
                                if (overlayText.isEmpty()) {
                                    Text(
                                        text = "Ketik teks di atas gambar...",
                                        color = Color(0xFF64748B),
                                        fontSize = 12.sp
                                    )
                                }
                                BasicTextField(
                                    value = overlayText,
                                    onValueChange = { if (it.length <= 60) overlayText = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color(0xFFF8FAFC), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                                    cursorBrush = SolidColor(Color(0xFFF8FAFC)),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (overlayText.isNotEmpty()) {
                                Text(
                                    text = "Hapus",
                                    color = Color(0xFFD946EF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { overlayText = "" }
                                )
                            }
                        }
                    }

                    // Sub-bar Cut / Trim Video (Atur Durasi Awal & Akhir sebelum dikirim)
                    AnimatedVisibility(visible = isVideo && videoDurationMs > 0 && (activeTool == "trim" || activeTool == "none")) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF12151D))
                                .border(0.75.dp, Color(0xFF1E222C), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val selectedDurSec = ((trimEndMs - trimStartMs).coerceAtLeast(0)) / 1000f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Potong / Cut Durasi Video",
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${formatVideoMs(trimStartMs)} – ${formatVideoMs(trimEndMs)} (${"%.1f".format(selectedDurSec)} dtk)",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            // Slider Batas Awal (Start)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Awal",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.width(34.dp)
                                )
                                androidx.compose.material3.Slider(
                                    value = trimStartMs.toFloat(),
                                    onValueChange = { v ->
                                        val nextStart = v.toInt().coerceIn(0, (trimEndMs - 500).coerceAtLeast(0))
                                        trimStartMs = nextStart
                                        runCatching { videoViewRef?.seekTo(nextStart) }
                                    },
                                    valueRange = 0f..videoDurationMs.coerceAtLeast(1000).toFloat(),
                                    modifier = Modifier.weight(1f).height(24.dp)
                                )
                            }
                            // Slider Batas Akhir (End)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Akhir",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.width(34.dp)
                                )
                                androidx.compose.material3.Slider(
                                    value = trimEndMs.toFloat(),
                                    onValueChange = { v ->
                                        val nextEnd = v.toInt().coerceIn((trimStartMs + 500).coerceAtMost(videoDurationMs), videoDurationMs)
                                        trimEndMs = nextEnd
                                    },
                                    valueRange = 0f..videoDurationMs.coerceAtLeast(1000).toFloat(),
                                    modifier = Modifier.weight(1f).height(24.dp)
                                )
                            }
                            // Preset cepat Cut 5 dtk / 10 dtk / 15 dtk / Penuh
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "5 dtk" to 5_000,
                                    "10 dtk" to 10_000,
                                    "15 dtk" to 15_000,
                                    "Penuh" to videoDurationMs
                                ).forEach { (lbl, durMs) ->
                                    val targetEnd = (trimStartMs + durMs).coerceAtMost(videoDurationMs)
                                    val activePreset = kotlin.math.abs(trimEndMs - targetEnd) < 300
                                    Surface(
                                        shape = RoundedCornerShape(7.dp),
                                        color = if (activePreset) Color(0xFFF8FAFC) else Color(0xFF1B202C),
                                        modifier = Modifier.clickable {
                                            if (durMs >= videoDurationMs) {
                                                trimStartMs = 0
                                                trimEndMs = videoDurationMs
                                            } else {
                                                trimEndMs = (trimStartMs + durMs).coerceAtMost(videoDurationMs)
                                            }
                                            runCatching { videoViewRef?.seekTo(trimStartMs) }
                                        }
                                    ) {
                                        Text(
                                            text = lbl,
                                            color = if (activePreset) Color(0xFF0A0C10) else Color(0xFFCBD5E1),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Kanvas Tengah: Preview Foto Interaktif, Live Video Player sesuai Rasio Asli (9:16 / 16:9), atau Info Berkas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0E1117))
                        .border(0.75.dp, Color(0xFF1E222C), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPhoto && rotatedPreviewBitmap != null) {
                        val imgRatio = remember(rotatedPreviewBitmap) {
                            (rotatedPreviewBitmap.width.toFloat() / rotatedPreviewBitmap.height.toFloat().coerceAtLeast(1f))
                                .coerceIn(0.45f, 2.2f)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .aspectRatio(imgRatio, matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(12.dp))
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(activeTool, selectedColor, strokeRatio, canvasSize) {
                                    if (activeTool == "draw") {
                                        detectDragGestures(
                                            onDragStart = { start ->
                                                currentPoints.clear()
                                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                                currentPoints.add(
                                                    Offset(
                                                        (start.x / w).coerceIn(0f, 1f),
                                                        (start.y / h).coerceIn(0f, 1f)
                                                    )
                                                )
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                                currentPoints.add(
                                                    Offset(
                                                        (change.position.x / w).coerceIn(0f, 1f),
                                                        (change.position.y / h).coerceIn(0f, 1f)
                                                    )
                                                )
                                            },
                                            onDragEnd = {
                                                if (currentPoints.size >= 2) {
                                                    strokes.add(
                                                        MediaDrawStroke(
                                                            points = currentPoints.toList(),
                                                            color = selectedColor,
                                                            widthRatio = strokeRatio
                                                        )
                                                    )
                                                }
                                                currentPoints.clear()
                                            }
                                        )
                                    }
                                }
                        ) {
                            Image(
                                bitmap = rotatedPreviewBitmap.asImageBitmap(),
                                contentDescription = initialAttachmentName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Layer Garis Coretan (Freehand Drawing)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val minDim = min(size.width, size.height)
                                fun drawStrokeList(pts: List<Offset>, col: Color, ratio: Float) {
                                    if (pts.size < 2) return
                                    val path = Path().apply {
                                        moveTo(pts.first().x * size.width, pts.first().y * size.height)
                                        for (i in 1 until pts.size) {
                                            lineTo(pts[i].x * size.width, pts[i].y * size.height)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = col,
                                        style = Stroke(
                                            width = (minDim * ratio).coerceAtLeast(3f),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                                strokes.forEach { s -> drawStrokeList(s.points, s.color, s.widthRatio) }
                                if (currentPoints.size >= 2) {
                                    drawStrokeList(currentPoints, selectedColor, strokeRatio)
                                }
                            }

                            // Layer Teks Overlay
                            if (overlayText.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .border(0.75.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = overlayText,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else if (isVideo && tempVideoFile != null) {
                        // Live Video Preview sesuai Rasio Asli (9:16 portrait atau 16:9 landscape tanpa bar hitam)
                        Box(
                            modifier = Modifier
                                .aspectRatio(videoAspectRatio, matchHeightConstraintsFirst = videoAspectRatio < 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .clickable {
                                    val vv = videoViewRef
                                    if (vv != null) {
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            videoPlaying = false
                                        } else {
                                            vv.start()
                                            videoPlaying = true
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { ctx ->
                                    android.widget.VideoView(ctx).apply {
                                        videoViewRef = this
                                        setVideoPath(tempVideoFile.absolutePath)
                                        setOnPreparedListener { mp ->
                                            mediaPlayerRef = mp
                                            mp.isLooping = true
                                            val vw = mp.videoWidth
                                            val vh = mp.videoHeight
                                            if (vw > 0 && vh > 0) {
                                                videoAspectRatio = (vw.toFloat() / vh.toFloat()).coerceIn(0.45f, 2.2f)
                                            }
                                            if (videoDurationMs <= 0 && duration > 0) {
                                                videoDurationMs = duration
                                                trimEndMs = duration
                                            }
                                            val vol = if (videoMuted) 0f else 1f
                                            mp.setVolume(vol, vol)
                                            seekTo(trimStartMs)
                                            if (videoPlaying) start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Indikator Play/Pause di tengah saat dijeda
                            if (!videoPlaying) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.68f))
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = id.xydesk.remote.ui.components.XyIcons.Play,
                                        contentDescription = "Putar Video",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Pill posisi waktu di sudut kiri bawah
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.68f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${formatVideoMs(currentVideoPosMs)} / ${formatVideoMs(if (trimEndMs > 0) trimEndMs else videoDurationMs)}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        // Pratinjau Berkas / Dokumen (Sesuai Desain UI Utama, Ikon Vektor Murni Tanpa Emoji)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .requiredSize(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF151923))
                                    .border(0.75.dp, Color(0xFF252B3B), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = if (isVideo) id.xydesk.remote.ui.components.XyIcons.Video else id.xydesk.remote.ui.components.XyIcons.File,
                                    contentDescription = null,
                                    tint = Color(0xFFF8FAFC),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = initialAttachmentName.ifBlank { if (isVideo) "video.mp4" else "dokumen" },
                                color = Color(0xFFF8FAFC),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Berkas siap dikirim · Tambahkan keterangan di bawah bila perlu.",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Bottom Bar: Input Pesan/Caption + Toggle 1× + Tombol Kirim Vektor (Sesuai UI Utama)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF12151D))
                            .border(0.75.dp, Color(0xFF232938), RoundedCornerShape(14.dp))
                            .padding(horizontal = 13.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (caption.isEmpty()) {
                                Text(
                                    text = "Tambahkan pesan atau keterangan...",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = caption,
                                onValueChange = { if (it.length <= 500) caption = it },
                                textStyle = TextStyle(color = Color(0xFFF8FAFC), fontSize = 13.sp),
                                cursorBrush = SolidColor(Color(0xFFF8FAFC)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (isPhoto || isVideo) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (viewOnce) Color(0xFFF8FAFC) else Color(0xFF1B202C),
                                border = BorderStroke(
                                    0.75.dp,
                                    if (viewOnce) Color(0xFFF8FAFC) else Color(0xFF2E3646)
                                ),
                                modifier = Modifier.clickable { viewOnce = !viewOnce }
                            ) {
                                Text(
                                    text = "1×",
                                    color = if (viewOnce) Color(0xFF0A0C10) else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Tombol Kirim dengan Ikon Vektor XyIcons.Send (Merender foto atau memotong video jika di-trim/mute)
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        modifier = Modifier
                            .requiredSize(44.dp)
                            .clickable(enabled = !isProcessingSend) {
                                if (isVideo && tempVideoFile != null &&
                                    (trimStartMs > 250 || (videoDurationMs > 0 && trimEndMs < videoDurationMs - 250) || videoMuted)
                                ) {
                                    isProcessingSend = true
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val trimmedData = trimAndMuxVideoToDataUri(
                                            context = context,
                                            sourceFile = tempVideoFile,
                                            startMs = trimStartMs,
                                            endMs = if (trimEndMs > trimStartMs) trimEndMs else videoDurationMs,
                                            muteAudio = videoMuted
                                        ) ?: initialAttachmentData
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            isProcessingSend = false
                                            onConfirmSend(
                                                trimmedData,
                                                initialAttachmentName,
                                                initialAttachmentType,
                                                caption.trim(),
                                                viewOnce
                                            )
                                        }
                                    }
                                } else {
                                    val finalData = if (isPhoto && rotatedPreviewBitmap != null &&
                                        (strokes.isNotEmpty() || stickers.isNotEmpty() || overlayText.isNotBlank() || rotationQuarter % 4 != 0)
                                    ) {
                                        bakeEditedBitmapToDataUri(
                                            base = rotatedPreviewBitmap,
                                            strokes = strokes,
                                            stickers = stickers,
                                            overlayText = overlayText
                                        ) ?: initialAttachmentData
                                    } else {
                                        initialAttachmentData
                                    }
                                    onConfirmSend(
                                        finalData,
                                        initialAttachmentName,
                                        initialAttachmentType,
                                        caption.trim(),
                                        viewOnce
                                    )
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            androidx.compose.material3.Icon(
                                imageVector = id.xydesk.remote.ui.components.XyIcons.Send,
                                contentDescription = "Kirim",
                                tint = Color(0xFF0A0C10),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatVideoMs(ms: Int): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}

/**
 * Memotong durasi video (`startMs`..`endMs`) dan/atau membisukan track audio (`muteAudio`)
 * secara cepat menggunakan `MediaExtractor` + `MediaMuxer` bawaan Android tanpa re-encode lambat.
 */
private fun trimAndMuxVideoToDataUri(
    context: android.content.Context,
    sourceFile: java.io.File,
    startMs: Int,
    endMs: Int,
    muteAudio: Boolean
): String? = runCatching {
    val outFile = java.io.File(context.cacheDir, "trimmed_${System.currentTimeMillis()}.mp4")
    val extractor = android.media.MediaExtractor()
    extractor.setDataSource(sourceFile.absolutePath)
    val muxer = android.media.MediaMuxer(outFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    val trackMap = HashMap<Int, Int>()
    var maxBufferSize = 256 * 1024
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(android.media.MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("video/") || (!muteAudio && mime.startsWith("audio/"))) {
            extractor.selectTrack(i)
            val dstIndex = muxer.addTrack(format)
            trackMap[i] = dstIndex
            if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                val sz = format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                if (sz > maxBufferSize) maxBufferSize = sz
            }
        }
    }
    if (trackMap.isEmpty()) {
        extractor.release()
        muxer.release()
        return@runCatching null
    }

    // Pertahankan orientasi rotasi video asli (9:16 portrait vs 16:9 landscape)
    runCatching {
        val mmr = android.media.MediaMetadataRetriever()
        mmr.setDataSource(sourceFile.absolutePath)
        val rot = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        mmr.release()
        if (rot == 90 || rot == 180 || rot == 270) {
            muxer.setOrientationHint(rot)
        }
    }

    val startUs = startMs.coerceAtLeast(0).toLong() * 1000L
    val endUs = if (endMs > startMs) endMs.toLong() * 1000L else Long.MAX_VALUE
    if (startUs > 0L) {
        extractor.seekTo(startUs, android.media.MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
    }

    val buffer = java.nio.ByteBuffer.allocate(maxBufferSize)
    val bufferInfo = android.media.MediaCodec.BufferInfo()
    muxer.start()
    while (true) {
        bufferInfo.offset = 0
        bufferInfo.size = extractor.readSampleData(buffer, 0)
        if (bufferInfo.size < 0) break
        val sampleTimeUs = extractor.sampleTime
        if (sampleTimeUs > endUs) break
        val trackIndex = extractor.sampleTrackIndex
        val dstTrack = trackMap[trackIndex]
        if (dstTrack != null && sampleTimeUs >= startUs) {
            bufferInfo.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0L)
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(dstTrack, buffer, bufferInfo)
        }
        extractor.advance()
    }
    runCatching { muxer.stop() }
    runCatching { muxer.release() }
    runCatching { extractor.release() }

    val bytes = outFile.readBytes()
    outFile.delete()
    if (bytes.isEmpty()) null
    else "data:video/mp4;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}.getOrNull()

@Composable
private fun StudioToolChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (active) Color(0xFFF8FAFC) else Color(0xFF141821),
        border = BorderStroke(
            0.75.dp,
            if (active) Color(0xFFF8FAFC) else Color(0xFF232938)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) Color(0xFF0A0C10) else Color(0xFFF8FAFC),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                color = if (active) Color(0xFF0A0C10) else Color(0xFFF8FAFC),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun bakeEditedBitmapToDataUri(
    base: Bitmap,
    strokes: List<MediaDrawStroke>,
    stickers: List<MediaStickerItem>,
    overlayText: String
): String? = runCatching {
    val w = base.width.coerceAtLeast(1)
    val h = base.height.coerceAtLeast(1)
    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = AndroidCanvas(out)
    val minDim = min(w, h).toFloat()

    // 1. Gambar semua garis coretan (strokes)
    strokes.forEach { stroke ->
        if (stroke.points.size >= 2) {
            val p = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color.toArgb()
                style = AndroidPaint.Style.STROKE
                strokeWidth = (minDim * stroke.widthRatio).coerceAtLeast(4f)
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
            }
            val path = AndroidPath().apply {
                moveTo(stroke.points.first().x * w, stroke.points.first().y * h)
                for (i in 1 until stroke.points.size) {
                    lineTo(stroke.points[i].x * w, stroke.points[i].y * h)
                }
            }
            canvas.drawPath(path, p)
        }
    }

    // 2. Gambar semua stiker emoji
    stickers.forEach { st ->
        val tp = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textSize = (minDim * 0.14f * st.scale).coerceAtLeast(28f)
            textAlign = AndroidPaint.Align.CENTER
        }
        canvas.drawText(st.emojiChar, st.normX * w, st.normY * h + tp.textSize * 0.35f, tp)
    }

    // 3. Gambar teks overlay bila ada
    if (overlayText.isNotBlank()) {
        val textPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = (minDim * 0.052f).coerceIn(22f, 56f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = AndroidPaint.Align.CENTER
        }
        val textW = textPaint.measureText(overlayText)
        val padH = minDim * 0.035f
        val padV = minDim * 0.020f
        val cy = h - minDim * 0.08f
        val bgRect = RectF(
            (w / 2f - textW / 2f - padH).coerceAtLeast(8f),
            cy - textPaint.textSize - padV,
            (w / 2f + textW / 2f + padH).coerceAtMost(w - 8f),
            cy + padV
        )
        val bgPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(190, 20, 10, 38)
            style = AndroidPaint.Style.FILL
        }
        canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)
        canvas.drawText(overlayText, w / 2f, cy - padV * 0.3f, textPaint)
    }

    val baos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 86, baos)
    "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}.getOrNull()

/**
 * Panel kustomisasi Shape Bubble Chat, Elemen Melayang Beranimasi, dan
 * Generator "Generate with AI" di dalam BubbleCustomizerModal.
 */
@Composable
fun BubbleShapeAndAiSection(
    currentShape: String,
    currentFloating: String,
    onSelectShape: (String) -> Unit,
    onSelectFloating: (String) -> Unit,
    onApplyAiPreset: (bgStart: String, bgEnd: String, accent: String, shape: String, floating: String, styleMode: String) -> Unit
) {
    var aiPrompt by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Pilihan Gaya Bentuk (Shape) Bubble
        Text(
            text = "Gaya Bentuk (Shape) Bubble",
            color = Color(0xFFD4D4D8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "rounded" to "Rounded",
                "sharp" to "Ujung Tajam",
                "morph" to "Liquid Morph",
                "abstract" to "Abstrak HUD",
                "cloud" to "Cloud Soft"
            ).forEach { (id, label) ->
                val sel = currentShape == id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) Color(0xFFF4F4F5) else Color(0xFF18181B),
                    border = BorderStroke(
                        1.dp,
                        if (sel) Color.White else Color.White.copy(alpha = 0.10f)
                    ),
                    modifier = Modifier.clickable { onSelectShape(id) }
                ) {
                    Text(
                        text = label,
                        color = if (sel) Color(0xFF09090B) else Color(0xFFA1A1AA),
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 2. Pilihan Elemen Melayang Beranimasi di dalam Bubble (Tanpa Emoji di Kontrol UI)
        Text(
            text = "Elemen Melayang Beranimasi",
            color = Color(0xFFD4D4D8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "none" to "Tanpa Efek",
                "crystals" to "Kristal",
                "orbs" to "Bola Plasma",
                "stars" to "Bintang",
                "cyber" to "Cyber HUD",
                "sakura" to "Kelopak",
                "fireflies" to "Kunang-Kunang"
            ).forEach { (id, label) ->
                val sel = currentFloating == id
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) Color(0xFFF4F4F5) else Color(0xFF18181B),
                    border = BorderStroke(
                        1.dp,
                        if (sel) Color.White else Color.White.copy(alpha = 0.10f)
                    ),
                    modifier = Modifier.clickable { onSelectFloating(id) }
                ) {
                    Text(
                        text = label,
                        color = if (sel) Color(0xFF09090B) else Color(0xFFA1A1AA),
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 3. Generate with AI (Selaras Palet Utama Monokrom Matte)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141418))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generate Bubble with AI",
                    color = Color(0xFFF4F4F5),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "AI Style Engine",
                    color = Color(0xFFA1A1AA),
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0C0C0F))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (aiPrompt.isEmpty()) {
                        Text(
                            text = "Misal: Kristal Tajam / Cyber Morph / Bintang...",
                            color = Color(0xFF71717A),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = aiPrompt,
                        onValueChange = { if (it.length <= 80) aiPrompt = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 11.5.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF4F4F5),
                    modifier = Modifier.clickable {
                        val generated = synthesizeAiBubbleStyle(aiPrompt)
                        onApplyAiPreset(
                            generated.bgStart,
                            generated.bgEnd,
                            generated.accent,
                            generated.shape,
                            generated.floating,
                            generated.styleMode
                        )
                    }
                ) {
                    Text(
                        text = "Generate",
                        color = Color(0xFF09090B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private data class AiBubbleSynthesis(
    val bgStart: String,
    val bgEnd: String,
    val accent: String,
    val shape: String,
    val floating: String,
    val styleMode: String
)

private fun synthesizeAiBubbleStyle(promptRaw: String): AiBubbleSynthesis {
    val q = promptRaw.trim().lowercase()
    val presets = listOf(
        AiBubbleSynthesis("#2E1065", "#4C1D95", "#C084FC", "morph", "crystals", "aurora"),
        AiBubbleSynthesis("#1E0938", "#581C87", "#E879F9", "sharp", "cyber", "neon"),
        AiBubbleSynthesis("#0F172A", "#1E3A8A", "#38BDF8", "abstract", "stars", "aurora"),
        AiBubbleSynthesis("#064E3B", "#0F766E", "#34D399", "sharp", "cyber", "neon"),
        AiBubbleSynthesis("#3B0764", "#701A75", "#F472B6", "cloud", "sakura", "aurora"),
        AiBubbleSynthesis("#1F1235", "#311042", "#FACC15", "morph", "fireflies", "aurora")
    )
    if (q.isBlank()) {
        return presets[Random.nextInt(presets.size)]
    }
    val shape = when {
        "tajam" in q || "sharp" in q || "lancip" in q || "blade" in q -> "sharp"
        "morph" in q || "liquid" in q || "cair" in q || "organik" in q -> "morph"
        "abstrak" in q || "abstract" in q || "hud" in q || "mecha" in q -> "abstract"
        "cloud" in q || "awan" in q || "soft" in q -> "cloud"
        else -> listOf("morph", "sharp", "abstract", "cloud")[kotlin.math.abs(q.hashCode()) % 4]
    }
    val floating = when {
        "kristal" in q || "crystal" in q || "ungu" in q || "amethyst" in q || "diamond" in q -> "crystals"
        "bintang" in q || "star" in q || "galaxy" in q || "angkasa" in q -> "stars"
        "cyber" in q || "matrix" in q || "hacker" in q || "hud" in q -> "cyber"
        "sakura" in q || "bunga" in q || "pink" in q -> "sakura"
        "kunang" in q || "firefl" in q || "emas" in q || "gold" in q -> "fireflies"
        "orb" in q || "plasma" in q || "neon" in q -> "orbs"
        else -> listOf("crystals", "orbs", "stars", "cyber", "sakura", "fireflies")[kotlin.math.abs(q.hashCode() / 3) % 6]
    }
    return when {
        "ungu" in q || "purple" in q || "amethyst" in q || "founder" in q ->
            AiBubbleSynthesis("#230B42", "#4C1D95", "#C084FC", shape, floating, "aurora")
        "merah" in q || "red" in q || "crimson" in q || "fire" in q ->
            AiBubbleSynthesis("#3B0A14", "#7F1D1D", "#F87171", shape, floating, "neon")
        "hijau" in q || "green" in q || "emerald" in q || "matrix" in q ->
            AiBubbleSynthesis("#052E16", "#065F46", "#34D399", shape, floating, "neon")
        "biru" in q || "blue" in q || "cyber" in q || "ocean" in q ->
            AiBubbleSynthesis("#082F49", "#1E3A8A", "#38BDF8", shape, floating, "aurora")
        "pink" in q || "sakura" in q || "rose" in q ->
            AiBubbleSynthesis("#3B0736", "#831843", "#F472B6", shape, floating, "aurora")
        else -> presets[kotlin.math.abs(q.hashCode()) % presets.size].copy(shape = shape, floating = floating)
    }
}

/**
 * Overload BubbleShapeAndAiSection untuk GlobalChatScreen (3-parameter AI preset callback).
 */
@Composable
fun BubbleShapeAndAiSection(
    selectedShape: String,
    selectedFloating: String,
    onSelectShape: (String) -> Unit,
    onSelectFloating: (String) -> Unit,
    onApplyAiPreset: (styleMode: String, shape: String, floating: String) -> Unit,
) {
    BubbleShapeAndAiSection(
        currentShape = selectedShape.lowercase(),
        currentFloating = selectedFloating.lowercase(),
        onSelectShape = onSelectShape,
        onSelectFloating = onSelectFloating,
        onApplyAiPreset = { _, _, _, shape, floating, styleMode ->
            val mappedStyle = when (styleMode.lowercase()) {
                "aurora" -> "glass"
                "neon" -> "amber"
                else -> "glass"
            }
            onApplyAiPreset(mappedStyle, shape, floating)
        },
    )
}

/**
 * Overload PreSendMediaStudioDialog berbasis Bitmap untuk GlobalChatScreen.
 */
@Composable
fun PreSendMediaStudioDialog(
    mediaKind: String,
    mediaName: String,
    initialBitmap: Bitmap?,
    initialCaption: String = "",
    initialViewOnce: Boolean = false,
    onDismiss: () -> Unit,
    onConfirmSend: (caption: String, editedBitmap: Bitmap?) -> Unit,
) {
    val initialData = remember(initialBitmap, mediaKind) {
        if (initialBitmap != null) {
            val out = ByteArrayOutputStream()
            initialBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } else {
            ""
        }
    }
    val mappedType = when (mediaKind.lowercase()) {
        "image", "photo" -> "photo"
        "video" -> "video"
        else -> "file"
    }
    PreSendMediaStudioDialog(
        initialAttachmentData = initialData,
        initialAttachmentName = mediaName,
        initialAttachmentType = mappedType,
        initialCaption = initialCaption,
        initialViewOnce = initialViewOnce,
        onDismiss = onDismiss,
        onConfirmSend = { finalData, _, _, caption, _ ->
            val decoded = if (finalData != initialData && finalData.startsWith("data:image/")) {
                runCatching {
                    val raw = finalData.substringAfter("base64,", "")
                    val bytes = Base64.decode(raw, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            } else {
                null
            }
            onConfirmSend(caption, decoded)
        },
    )
}

