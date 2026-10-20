package id.xydesk.remote.ui

import java.io.File

/** Pure path policy: listing never creates folders; legacy locations are read-only. */
internal object StickerDirectoryPolicy {
    private const val CANONICAL = "globalchat/sticker"
    private const val LEGACY = "globwlchat/sticker"

    fun writeCandidates(mediaRoots: List<File>, filesRoot: File): List<File> =
        (mediaRoots.firstOrNull()?.let { listOf(File(it, CANONICAL)) }.orEmpty() +
            File(filesRoot, CANONICAL)).distinctBy { it.absolutePath }

    fun <T : Any> writeFirstAvailable(mediaRoots: List<File>, filesRoot: File, write: (File) -> T?): T? {
        for (directory in writeCandidates(mediaRoots, filesRoot)) {
            val saved = write(directory)
            if (saved != null) return saved
        }
        return null
    }

    fun readDirectories(mediaRoots: List<File>, filesRoot: File): List<File> =
        (mediaRoots.flatMap { listOf(File(it, CANONICAL), File(it, LEGACY)) } +
            listOf(File(filesRoot, CANONICAL), File(filesRoot, LEGACY)))
            .distinctBy { it.absolutePath }

    fun isSafeFileName(name: String): Boolean = name.length in 1..180 &&
        '/' !in name && '\\' !in name && name.none { it.isISOControl() } &&
        name.endsWith(".webp.crypto15", ignoreCase = true)
}
