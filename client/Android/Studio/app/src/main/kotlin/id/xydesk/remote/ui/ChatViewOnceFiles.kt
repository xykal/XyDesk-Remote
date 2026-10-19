package id.xydesk.remote.ui

import java.io.File
import java.io.IOException

/** Best-effort temporary-file hygiene, not secure erasure or access control. */
internal fun pruneExpiredChatViewOnceFiles(cacheDir: File, now: Long = System.currentTimeMillis()) {
    File(cacheDir, "chat_view_once").listFiles()?.filter { it.isFile && it.name.startsWith("once-") }
        ?.filter { now - it.lastModified() > 6L * 60 * 60 * 1000 }
        ?.forEach { it.delete() }
}

internal fun createChatViewOnceVideo(cacheDir: File, bytes: ByteArray): File {
    require(bytes.isNotEmpty() && bytes.size <= 2_100_000)
    pruneExpiredChatViewOnceFiles(cacheDir)
    val directory = File(cacheDir, "chat_view_once")
    if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create temporary media directory")
    val file = File.createTempFile("once-", ".mp4", directory)
    try { file.outputStream().use { it.write(bytes) }; return file }
    catch (error: Exception) { file.delete(); throw error }
}
