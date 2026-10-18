package id.xydesk.remote.ui

import android.content.Context
import android.media.MediaPlayer
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

    fun toggle(onError: () -> Unit) {
        if (disposed || preparing) return
        val current = player
        if (current != null) {
            try {
                if (playing) current.pause() else current.start()
                playing = !playing
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
                    try { ready.start(); playing = true; preparing = false }
                    catch (_: Exception) { fail(onError) }
                }
                fresh.setOnCompletionListener { if (player === it) playing = false }
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
        onError()
    }

    fun dispose() {
        disposed = true
        job?.cancel()
        runCatching { player?.release() }
        player = null
        playing = false
        preparing = false
    }
}

@Composable
internal fun rememberChatAudioController(context: Context, id: String, data: String): ChatAudioController {
    val scope = rememberCoroutineScope()
    val controller = remember(id, data) { ChatAudioController(context.applicationContext, scope, data) }
    DisposableEffect(controller) { onDispose { controller.dispose() } }
    return controller
}
