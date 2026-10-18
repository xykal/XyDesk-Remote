package id.xydesk.remote.ui

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Disk I/O: call on Dispatchers.IO. Content keys avoid same-size filename collisions. */
@Synchronized
internal fun prepareCachedVideoFile(cacheDir: File, bytes: ByteArray, budgetBytes: Long = 64L * 1024 * 1024): File {
    require(budgetBytes > 0 && bytes.isNotEmpty() && bytes.size.toLong() <= budgetBytes)
    val folder = File(cacheDir, "xy_video_cache")
    if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Cannot create video cache")
    val key = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val target = File(folder, "$key.mp4")
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
    folder.listFiles()?.filter { it.isFile && it.extension == "mp4" }
        ?.sortedByDescending { if (it == target) Long.MAX_VALUE else it.lastModified() }
        ?.forEach { file ->
            total += file.length()
            if (total > budgetBytes && file != target) file.delete()
        }
    return target
}
