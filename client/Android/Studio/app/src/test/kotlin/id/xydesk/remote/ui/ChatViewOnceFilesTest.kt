package id.xydesk.remote.ui

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ChatViewOnceFilesTest {
    @Test fun onceVideosNeverEnterReusableCacheOrReuseAPath() {
        val dir = Files.createTempDirectory("chat-once").toFile()
        try {
            val a = createChatViewOnceVideo(dir, byteArrayOf(1, 2))
            val b = createChatViewOnceVideo(dir, byteArrayOf(1, 2))
            assertNotEquals(a, b)
            assertEquals("chat_view_once", a.parentFile!!.name)
            assertFalse(java.io.File(dir, "xy_video_cache").exists())
            assertArrayEquals(byteArrayOf(1, 2), a.readBytes())
        } finally { dir.deleteRecursively() }
    }
    @Test fun staleCrashRemnantsArePrunedButRecentFilesAreKept() {
        val dir = Files.createTempDirectory("chat-once-prune").toFile()
        try {
            val a = createChatViewOnceVideo(dir, byteArrayOf(1))
            val now = System.currentTimeMillis()
            a.setLastModified(now - 7 * 60 * 60 * 1000)
            val b = createChatViewOnceVideo(dir, byteArrayOf(2))
            pruneExpiredChatViewOnceFiles(dir, now)
            assertFalse(a.exists())
            assertTrue(b.exists())
        } finally { dir.deleteRecursively() }
    }
}
