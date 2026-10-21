package id.xydesk.remote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Process-wide bound; stripes coalesce identical URLs without an unbounded lock map. */
internal object ChatAvatarLoader {
    private const val MAX_BYTES = 512 * 1024
    private val slots = Semaphore(4)
    private val locks = Array(32) { Mutex() }

    fun normalizeAvatarUrl(
        rawAvatarUrl: String?,
        githubLogin: String? = null,
        fallbackName: String? = null,
    ): String {
        val raw = rawAvatarUrl?.trim().orEmpty()
        val gh = githubLogin?.trim()?.removePrefix("@")
            ?.substringBefore(" ")
            ?.takeIf { isValidHandle(it) }
            .orEmpty()
        val nameHandle = fallbackName?.trim()
            ?.substringBefore(" [")
            ?.substringBefore(" (")
            ?.removePrefix("@")
            ?.takeIf { isValidHandle(it) }
            .orEmpty()
        val handle = gh.ifBlank { nameHandle }

        if (raw.startsWith("/api/", ignoreCase = true)) {
            return "https://api.xydeskremote.biz.id$raw"
        }
        if (raw.startsWith("https://github.com/", ignoreCase = true) && raw.contains(".png")) {
            val userPart = raw.substringAfter("https://github.com/", "")
                .substringBefore(".png")
                .substringBefore("/")
                .trim()
            if (isValidHandle(userPart)) {
                return "https://avatars.githubusercontent.com/$userPart?size=120"
            }
        }
        if (raw.startsWith("https://", ignoreCase = true) || raw.startsWith("http://", ignoreCase = true)) {
            return raw
        }
        if (handle.isNotBlank()) {
            return "https://avatars.githubusercontent.com/$handle?size=120"
        }
        return ""
    }

    private fun isValidHandle(candidate: String): Boolean {
        if (candidate.length !in 2..39) return false
        if (candidate.contains(" ")) return false
        val low = candidate.lowercase()
        if (low.startsWith("tamu") || low.startsWith("guest") || low.startsWith("anon") || low == "user" || low == "anda" || low == "you") {
            return false
        }
        return candidate.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
    }

    private fun diskCacheFile(context: Context?, url: String): File? {
        if (context == null || url.isBlank()) return null
        return runCatching {
            val dir = File(context.applicationContext.cacheDir, "chat_avatars")
            if (!dir.exists()) dir.mkdirs()
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(url.toByteArray(Charsets.UTF_8))
                .take(12)
                .joinToString("") { "%02x".format(it) }
            File(dir, "$hash.png")
        }.getOrNull()
    }

    private fun fetchBitmapBytesSync(url: String, timeoutMs: Int = 5_500): Bitmap? {
        if (url.isBlank()) return null
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; XyDeskRemote/1.1.23)")
                setRequestProperty("Accept", "image/webp,image/png,image/*,*/*;q=0.8")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299 || connection.contentLength > MAX_BYTES) return null
                val bytes = connection.inputStream.use { it.readChatBytesAtMost(MAX_BYTES) }
                decodeBoundedChatBitmap(bytes, 128)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    fun circleCrop(source: Bitmap, targetSize: Int = 128): Bitmap {
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val radius = targetSize / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val srcRect = Rect(0, 0, source.width, source.height)
        val dstRect = Rect(0, 0, targetSize, targetSize)
        canvas.drawBitmap(source, srcRect, dstRect, paint)
        return out
    }

    fun loadCachedOrSyncForNotification(
        context: Context,
        rawAvatarUrl: String?,
        githubLogin: String?,
        fallbackName: String?,
        isMe: Boolean = false,
    ): Bitmap? {
        val appCtx = context.applicationContext
        if (isMe) {
            for (fname in listOf("xy_avatar.webp", "profile_avatar.webp")) {
                val f = File(appCtx.filesDir, fname)
                if (f.exists() && f.length() > 256L) {
                    val localBmp = runCatching { BitmapFactory.decodeFile(f.absolutePath) }.getOrNull()
                    if (localBmp != null) return circleCrop(localBmp, 128)
                }
            }
        }
        val normalized = normalizeAvatarUrl(rawAvatarUrl, githubLogin, fallbackName)
        if (normalized.isBlank()) return null
        avatarMemoryCache.get(normalized)?.let { return circleCrop(it, 128) }
        val diskFile = diskCacheFile(appCtx, normalized)
        if (diskFile != null && diskFile.exists() && diskFile.length() > 128L) {
            val diskBmp = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
            if (diskBmp != null) {
                avatarMemoryCache.put(normalized, diskBmp)
                return circleCrop(diskBmp, 128)
            }
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val netBmp = fetchBitmapBytesSync(normalized, timeoutMs = 3_200)
                ?: run {
                    val gh = githubLogin?.trim()?.removePrefix("@").orEmpty()
                    if (gh.isNotBlank() && !normalized.contains("avatars.githubusercontent.com")) {
                        fetchBitmapBytesSync("https://avatars.githubusercontent.com/$gh?size=120", timeoutMs = 3_000)
                    } else null
                }
            if (netBmp != null) {
                avatarMemoryCache.put(normalized, netBmp)
                if (diskFile != null) {
                    runCatching { FileOutputStream(diskFile).use { out -> netBmp.compress(Bitmap.CompressFormat.PNG, 90, out) } }
                }
                return circleCrop(netBmp, 128)
            }
        }
        return null
    }

    suspend fun load(
        url: String,
        context: Context? = null,
        githubLogin: String? = null,
        fallbackName: String? = null,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val normalized = normalizeAvatarUrl(url, githubLogin, fallbackName)
        if (normalized.isBlank()) return@withContext null
        avatarMemoryCache.get(normalized)?.let { return@withContext it }
        locks[(normalized.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            avatarMemoryCache.get(normalized)?.let { return@withLock it }
            val diskFile = diskCacheFile(context, normalized)
            if (diskFile != null && diskFile.exists() && diskFile.length() > 128L) {
                val diskBmp = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath) }.getOrNull()
                if (diskBmp != null) {
                    avatarMemoryCache.put(normalized, diskBmp)
                    return@withLock diskBmp
                }
            }
            slots.withPermit {
                currentCoroutineContext().ensureActive()
                try {
                    var decoded = fetchBitmapBytesSync(normalized, timeoutMs = 6_000)
                    if (decoded == null) {
                        val fallbackGh = githubLogin?.trim()?.removePrefix("@").orEmpty().ifBlank {
                            fallbackName?.trim()?.substringBefore(" ")?.removePrefix("@").orEmpty()
                        }
                        if (isValidHandle(fallbackGh)) {
                            val ghUrl = "https://avatars.githubusercontent.com/$fallbackGh?size=120"
                            if (ghUrl != normalized) {
                                decoded = fetchBitmapBytesSync(ghUrl, timeoutMs = 5_000)
                            }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                    decoded?.also { bmp ->
                        avatarMemoryCache.put(normalized, bmp)
                        if (diskFile != null) {
                            runCatching {
                                FileOutputStream(diskFile).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 90, out) }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
