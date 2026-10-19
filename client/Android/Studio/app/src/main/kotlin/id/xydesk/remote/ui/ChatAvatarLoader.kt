package id.xydesk.remote.ui

import android.graphics.Bitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Process-wide bound; stripes coalesce identical URLs without an unbounded lock map. */
internal object ChatAvatarLoader {
    private const val MAX_BYTES = 512 * 1024
    private val slots = Semaphore(3)
    private val locks = Array(32) { Mutex() }

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank() || !url.startsWith("https://", ignoreCase = true)) return@withContext null
        avatarMemoryCache.get(url)?.let { return@withContext it }
        locks[(url.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            avatarMemoryCache.get(url)?.let { return@withLock it }
            slots.withPermit {
                currentCoroutineContext().ensureActive()
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 6_000
                        connection.readTimeout = 6_000
                        connection.instanceFollowRedirects = true
                        if (connection.responseCode !in 200..299 || connection.contentLength > MAX_BYTES) return@withPermit null
                        val bytes = connection.inputStream.use { it.readChatBytesAtMost(MAX_BYTES) }
                        currentCoroutineContext().ensureActive()
                        decodeBoundedChatBitmap(bytes, 128)?.also { avatarMemoryCache.put(url, it) }
                    } finally {
                        connection.disconnect()
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null // initials remain visible if avatar cannot be loaded
                }
            }
        }
    }
}
