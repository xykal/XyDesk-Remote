package id.xydesk.remote.ui.components

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Kontrol milik XyDesk sendiri — bukan Slider/ProgressIndicator bawaan Material.
 *
 * Alasan: bahasa visual app ini hairlines + bentuk pil, sementara kontrol
 * bawaan Material punya gaya sendiri (warna, ketebalan, animasi) yang selalu
 * kelihatan "asing" di dalam panel kami.
 */

/**
 * Mode fokus slider (1.1.27).
 *
 * Saat pengguna menggeser slider apa pun — ukuran tombol, opasitas HUD,
 * sensitivitas pointer, zoom — seluruh UI lain diredupkan dan hanya slider
 * plus nilainya yang tersisa. Tujuannya: perubahan bisa dilihat LANGSUNG di
 * layar remote tanpa tertutup panel pengaturan. State dipegang global karena
 * slider tersebar di banyak layar (sesi, tambah perangkat, pengaturan).
 */
object SliderFocusState {
    // Backing field sengaja dipakai, bukan `var ... by ...; private set`:
    // Kotlin menolak visibility modifier pada setter delegated property.
    private var labelState = mutableStateOf<String?>(null)
    private var valueState = mutableStateOf(0f)
    private var startState = mutableStateOf(0f)
    private var endState = mutableStateOf(1f)
    private var stepsState = mutableStateOf(0)

    val activeLabel: String? get() = labelState.value
    val value: Float get() = valueState.value
    val start: Float get() = startState.value
    val end: Float get() = endState.value
    val steps: Int get() = stepsState.value
    val isActive: Boolean get() = labelState.value != null

    private var onChange: (Float) -> Unit = {}

    fun begin(
        label: String,
        initialValue: Float,
        range: ClosedFloatingPointRange<Float>,
        steps: Int,
        onChange: (Float) -> Unit,
    ) {
        startState.value = range.start
        endState.value = range.endInclusive
        stepsState.value = steps
        valueState.value = initialValue
        this.onChange = onChange
        labelState.value = label
    }

    fun update(v: Float) {
        valueState.value = v
    }

    fun applyChange(v: Float) {
        valueState.value = v
        onChange(v)
    }

    fun end() {
        labelState.value = null
        onChange = {}
    }
}

/** Teks nilai yang enak dibaca saat mode fokus aktif. */
private fun focusValueText(v: Float, start: Float, end: Float): String {
    val span = end - start
    return when {
        span <= 1.01f -> "${(v * 100).roundToInt()}%"
        span <= 4f -> String.format(Locale.US, "%.2f", v)
        else -> v.roundToInt().toString()
    }
}

/**
 * Bungkus seluruh pohon UI aktivitas dengan ini. Saat ada slider yang digeser,
 * konten diredupkan jadi hampir transparan dan [SliderFocusOverlay] muncul.
 */
@Composable
fun SliderFocusHost(content: @Composable () -> Unit) {
    val active = SliderFocusState.isActive
    val dim by animateFloatAsState(
        targetValue = if (active) 0.06f else 1f,
        animationSpec = tween(180),
        label = "sliderFocusDim",
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().alpha(dim)) { content() }
        if (active) SliderFocusOverlay()
    }
}

@Composable
private fun SliderFocusOverlay() {
    val shape = RoundedCornerShape(20.dp)
    val state = SliderFocusState
    // Sengaja TANPA clickable/pointerInput penuh layar: kalau ada, event
    // ketuk akan ditelan overlay dan gestur pada slider asli terputus,
    // sehingga `finally` langsung memanggil end() dan overlay menghilang
    // seketika. Biarkan event turun ke slider asli; overlay hanya menampilkan
    // salinan visual yang nilainya mengikuti state yang sama.
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                state.activeLabel.orEmpty(),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            Text(
                focusValueText(state.value, state.start, state.end),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            XySlider(
                value = state.value,
                onValueChange = { state.applyChange(it) },
                valueRange = state.start..state.end,
                steps = state.steps,
                focusEnabled = false,
            )
            Text(
                "Lepaskan untuk menyimpan",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
            Text(
                "Selesai",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { SliderFocusState.end() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

internal fun sliderValueAtFraction(
    fraction: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    val start = valueRange.start
    val end = valueRange.endInclusive
    if (!start.isFinite() || !end.isFinite() || end <= start) return start

    val safeFraction = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    val raw = start + safeFraction * (end - start)
    if (steps <= 0) return raw

    val stepSize = (end - start) / (steps + 1)
    val index = ((raw - start) / stepSize).roundToInt().coerceIn(0, steps + 1)
    return (start + index * stepSize).coerceIn(start, end)
}

/** Slider: track tipis ber-border, isi aksen, pegangan bulat dan dapat diakses. */
@Composable
fun XySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Jumlah langkah antara (0 = bebas). Nilai dibulatkan ke langkah terdekat. */
    steps: Int = 0,
    /** Label yang tampil di mode fokus. Null = pakai "Pengaturan". */
    focusLabel: String? = null,
    /** Matikan pada slider overlay supaya tidak memicu fokus berulang. */
    focusEnabled: Boolean = true,
) {
    val latest = rememberUpdatedState(onValueChange)
    val latestValue = rememberUpdatedState(value)
    val latestLabel = rememberUpdatedState(focusLabel)
    val range = valueRange.endInclusive - valueRange.start

    Box(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(valueRange, enabled, steps, focusEnabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (focusEnabled) {
                        SliderFocusState.begin(
                            label = latestLabel.value ?: "Pengaturan",
                            initialValue = latestValue.value,
                            range = valueRange,
                            steps = steps,
                            onChange = { latest.value(it) },
                        )
                    }
                    fun apply(x: Float) {
                        val fraction = if (size.width > 0) x / size.width.toFloat() else 0f
                        val next = sliderValueAtFraction(fraction, valueRange, steps)
                        latest.value(next)
                        if (focusEnabled) SliderFocusState.update(next)
                    }
                    try {
                        apply(down.position.x)
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            apply(change.position.x)
                            change.consume()
                        }
                    } finally {
                        // gesture dibatalkan (rotasi / panel ditutup) tetap
                        // kembalikan UI, jangan biarkan layar menggantung redup.
                        if (focusEnabled) SliderFocusState.end()
                    }
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    value.coerceIn(valueRange.start, valueRange.endInclusive),
                    valueRange,
                    steps.coerceAtLeast(0),
                )
                setProgress { target ->
                    if (!enabled || range <= 0f) {
                        false
                    } else {
                        val fraction = (target - valueRange.start) / range
                        latest.value(sliderValueAtFraction(fraction, valueRange, steps))
                        true
                    }
                }
            },
    ) {
        val track = MaterialTheme.colorScheme.outline
        val fill = MaterialTheme.colorScheme.primary
        val knob = MaterialTheme.colorScheme.surface
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val h = size.height
            val w = size.width
            val trackH = 6.dp.toPx()
            val radius = CornerRadius(trackH / 2f, trackH / 2f)
            val cy = h / 2f
            drawRoundRect(
                color = track,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(w, trackH),
                cornerRadius = radius,
                style = Stroke(width = 1.2f),
            )
            val frac = if (range <= 0f) 0f
            else ((value - valueRange.start) / range).coerceIn(0f, 1f)
            if (frac > 0f) {
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(w * frac, trackH),
                    cornerRadius = radius,
                )
            }
            val knobR = 8.dp.toPx()
            val kx = (w * frac).coerceIn(knobR, (w - knobR).coerceAtLeast(knobR))
            drawCircle(color = fill, radius = knobR, center = Offset(kx, cy))
            drawCircle(
                color = knob,
                radius = knobR - 2.5f,
                center = Offset(kx, cy),
            )
        }
    }
}

/**
 * Spinner: satu busur yang berputar. Pengganti `CircularProgressIndicator`
 * bawaan supaya tidak ada dua bahasa animasi di satu layar.
 */
@Composable
fun XySpinner(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp,
) {
    val transition = rememberInfiniteTransition(label = "xy-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "xy-spinner-angle",
    )
    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 260f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(this.size.width - stroke, this.size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Garis pemisah tipis milik app (bukan HorizontalDivider). */
@Composable
fun XyDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

/** Titik kecil sebagai pengganti indikator akhir daftar. */
@Composable
fun XyDot(color: Color, sizeDp: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(sizeDp)
            .clip(CircleShape)
            .border(1.dp, color, CircleShape),
    )
}

/** Teks kecil ber-huruf renggang untuk label bagian. */
@Composable
fun XyTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
        ),
        maxLines = 1,
    )
}

/** Latar panel kecil dengan radius konsisten (dipakai di beberapa layar). */
@Composable
fun XyInset(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
    ) {
        content()
    }
}
