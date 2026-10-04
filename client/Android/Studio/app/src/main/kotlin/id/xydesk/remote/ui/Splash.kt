package id.xydesk.remote.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.R
import kotlinx.coroutines.delay

/**
 * Splash ringkas dengan wordmark XyDesk, footer brand, dan ucapan satu kali
 * setelah instalasi. Playback ditunggu agar teks tidak terpotong saat splash
 * ditutup ketika proses boot selesai.
 */
@Composable
fun XySplashScreen(
    ready: Boolean,
    dark: Boolean = true,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    var minTimeElapsed by remember { mutableStateOf(false) }
    var voiceComplete by remember { mutableStateOf(false) }
    val mediaPlayerRef = remember { arrayOfNulls<MediaPlayer>(1) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "splashAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "splashScale",
    )

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayerRef[0]?.let { player ->
                mediaPlayerRef[0] = null
                runCatching {
                    player.setOnCompletionListener(null)
                    player.setOnErrorListener(null)
                    player.release()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        visible = true
        val prefs = context.getSharedPreferences(SPLASH_PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SPLASH_VOICE_PLAYED, false)) {
            voiceComplete = true
        } else {
            val player = runCatching {
                MediaPlayer.create(context, R.raw.splash_xydesk_remote)
            }.getOrNull()
            if (player == null) {
                voiceComplete = true
            } else {
                mediaPlayerRef[0] = player
                player.setOnCompletionListener { finished ->
                    if (mediaPlayerRef[0] === finished) mediaPlayerRef[0] = null
                    runCatching { finished.release() }
                    prefs.edit().putBoolean(KEY_SPLASH_VOICE_PLAYED, true).apply()
                    voiceComplete = true
                }
                player.setOnErrorListener { failed, _, _ ->
                    if (mediaPlayerRef[0] === failed) mediaPlayerRef[0] = null
                    runCatching { failed.release() }
                    voiceComplete = true
                    true
                }
                runCatching { player.start() }.onFailure {
                    if (mediaPlayerRef[0] === player) mediaPlayerRef[0] = null
                    runCatching { player.release() }
                    voiceComplete = true
                }
            }
        }
        delay(650)
        minTimeElapsed = true
    }

    LaunchedEffect(ready, minTimeElapsed, voiceComplete) {
        if (ready && minTimeElapsed && voiceComplete) onDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF14161A) else Color(0xFFF6F7F9)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                if (dark) R.drawable.xy_logo_h_white else R.drawable.xy_logo_h_black,
            ),
            contentDescription = "XyDesk Remote",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(width = 252.dp, height = 86.dp)
                .alpha(alpha)
                .scale(scale),
        )
        Text(
            text = "XyVerse Technology Global",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            color = if (dark) Color.White.copy(alpha = 0.72f) else Color(0xFF14161A).copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private const val SPLASH_PREFS = "xydesk.splash"
private const val KEY_SPLASH_VOICE_PLAYED = "xydesk_remote_voice_played"
