package id.xydesk.remote.ui

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Disk I/O: call on Dispatchers.IO. Content keys avoid same-size filename collisions. */
internal fun prepareCachedVideoFile(cacheDir: File, bytes: ByteArray, budgetBytes: Long = 64L * 1024 * 1024): File =
    prepareCachedMediaFile(cacheDir, bytes, budgetBytes, "xy_video_cache", "mp4")

internal fun prepareCachedAudioFile(cacheDir: File, bytes: ByteArray): File =
    prepareCachedMediaFile(cacheDir, bytes, 16L * 1024 * 1024, "xy_audio_cache", "audio")

@Synchronized
private fun prepareCachedMediaFile(cacheDir: File, bytes: ByteArray, budgetBytes: Long, folderName: String, extension: String): File {
    require(budgetBytes > 0 && bytes.isNotEmpty() && bytes.size.toLong() <= budgetBytes)
    val folder = File(cacheDir, folderName)
    if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Cannot create video cache")
    val key = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val target = File(folder, "$key.$extension")
    if (!target.isFile || target.length() != bytes.size.toLong()) {
        val temporary = File.createTempFile("video-", ".part", folder)
        try {
            temporary.outputStream().use { it.write(bytes) }
            if (!temporary.renameTo(target)) throw IOException("Cannot commit video cache")
        } finally {
            temporary.delete()
        }
    }
    target.setLastModified(System.currentTimeMillis())
    var total = 0L
    folder.listFiles()?.filter { it.isFile && it.extension == extension }
        ?.sortedByDescending { if (it == target) Long.MAX_VALUE else it.lastModified() }
        ?.forEach { file ->
            total += file.length()
            if (total > budgetBytes && file != target) file.delete()
        }
    return target
}
