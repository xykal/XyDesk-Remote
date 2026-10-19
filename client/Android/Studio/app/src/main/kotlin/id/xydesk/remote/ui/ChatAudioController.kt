package id.xydesk.remote.ui

import android.content.Context
import android.media.MediaPlayer
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Base64
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Screen-owned audio player; blocking decoding/disk work is never done by the click handler. */
internal class ChatAudioController(private val context: Context, private val scope: CoroutineScope, private val data: String) {
    var playing by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var disposed = false
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= 0) Handler(Looper.getMainLooper()).post { interrupt() }
    }
    private var focusRequest: AudioFocusRequest? = null

    @Suppress("DEPRECATION")
    private fun requestFocus(): Boolean {
        val manager = audioManager ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
                .setAcceptsDelayedFocusGain(false).build()
            focusRequest = request
            manager.requestAudioFocus(request)
        } else manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else audioManager?.abandonAudioFocus(focusListener)
    }

    /** No automatic resume after focus loss/background: another tap is required. */
    fun interrupt() {
        job?.cancel()
        runCatching { player?.release() }
        player = null
        playing = false
        preparing = false
        abandonFocus()
    }

    fun toggle(onError: () -> Unit) {
        if (disposed || preparing) return
        val current = player
        if (current != null) {
            try {
                if (playing) {
                    current.pause()
                    playing = false
                    abandonFocus()
                } else if (requestFocus()) {
                    current.start()
                    playing = true
                } else fail(onError)
            } catch (_: Exception) { fail(onError) }
            return
        }
        preparing = true
        job = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    require(data.length <= 2_800_000)
                    val raw = data.substringAfter(',', data)
                    prepareCachedAudioFile(context.cacheDir, Base64.decode(raw, Base64.DEFAULT))
                }
                if (disposed) return@launch
                val fresh = MediaPlayer()
                player = fresh // assign before setup, so every failure can release it
                fresh.setOnPreparedListener { ready ->
                    if (disposed || player !== ready) return@setOnPreparedListener
                    try {
                        if (!requestFocus()) { fail(onError); return@setOnPreparedListener }
                        ready.start(); playing = true; preparing = false
                    }
                    catch (_: Exception) { fail(onError) }
                }
                fresh.setOnCompletionListener {
                    if (player === it) { playing = false; abandonFocus() }
                }
                fresh.setOnErrorListener { failed, _, _ ->
                    if (!disposed && player === failed) fail(onError)
                    true
                }
                fresh.setDataSource(file.absolutePath)
                fresh.prepareAsync()
                delay(15_000L)
                if (!disposed && player === fresh && preparing) fail(onError)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!disposed) fail(onError)
            }
        }
    }

    private fun fail(onError: () -> Unit) {
        runCatching { player?.release() }
        player = null
        playing = false
        preparing = false
        abandonFocus()
        onError()
    }

    fun dispose() {
        disposed = true
        interrupt()
    }
}

@Composable
internal fun rememberChatAudioController(context: Context, id: String, data: String): ChatAudioController {
    val scope = rememberCoroutineScope()
    val controller = remember(id, data) { ChatAudioController(context.applicationContext, scope, data) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) controller.interrupt()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.dispose() }
    }
    return controller
}
