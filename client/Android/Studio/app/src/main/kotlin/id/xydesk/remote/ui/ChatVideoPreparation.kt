package id.xydesk.remote.ui

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

internal data class PreparedChatVideo(val file: File, val bytes: ByteArray, val ratio: Float)
internal data class ChatVideoPreparation(val loading: Boolean = true, val video: PreparedChatVideo? = null)
private val chatVideoSlots = Semaphore(2)

@Composable
internal fun rememberPreparedChatVideo(context: Context, id: String, data: String): ChatVideoPreparation = key(id, data) {
    produceState(ChatVideoPreparation(), id, data) {
        val video = withContext(Dispatchers.IO) {
            chatVideoSlots.withPermit {
                try {
                    if (data.length > 2_800_000) return@withPermit null
                    val comma = data.indexOf(',')
                    if (comma < 0) return@withPermit null
                    val bytes = Base64.decode(data.substring(comma + 1), Base64.DEFAULT)
                    if (bytes.isEmpty()) return@withPermit null
                    val file = prepareCachedVideoFile(context.cacheDir, bytes)
                    val ratio = extractCachedVideoAspectRatio(context, id, bytes)
                    PreparedChatVideo(file, bytes, ratio)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { null }
            }
        }
        value = ChatVideoPreparation(loading = false, video = video)
    }.value
}
