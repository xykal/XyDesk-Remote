package id.xydesk.remote.ui

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class VideoFileCacheTest {
    @Test fun equalLengthDifferentVideosNeverOverwriteEachOther() {
        val dir = Files.createTempDirectory("chat-video-test").toFile()
        try {
            val a = prepareCachedVideoFile(dir, byteArrayOf(1, 2, 3))
            val b = prepareCachedVideoFile(dir, byteArrayOf(4, 5, 6))
            assertNotEquals(a, b)
            assertArrayEquals(byteArrayOf(1, 2, 3), a.readBytes())
            assertArrayEquals(byteArrayOf(4, 5, 6), b.readBytes())
            assertEquals(a, prepareCachedVideoFile(dir, byteArrayOf(1, 2, 3)))
            assertFalse(a.parentFile!!.listFiles()!!.any { it.extension == "part" })
        } finally { dir.deleteRecursively() }
    }
    @Test fun emptyInputIsRejected() {
        val dir = Files.createTempDirectory("chat-video-empty").toFile()
        try {
            try { prepareCachedVideoFile(dir, byteArrayOf()); fail("empty media accepted") }
            catch (_: IllegalArgumentException) { }
        } finally { dir.deleteRecursively() }
    }
    @Test fun oldEntriesAreEvictedWithinBudget() {
        val dir = Files.createTempDirectory("chat-video-budget").toFile()
        try {
            val first = prepareCachedVideoFile(dir, byteArrayOf(1, 2, 3), 5)
            val latest = prepareCachedVideoFile(dir, byteArrayOf(4, 5, 6), 5)
            assertFalse(first.exists())
            assertTrue(latest.exists())
            assertTrue(latest.parentFile!!.listFiles()!!.sumOf { it.length() } <= 5L)
        } finally { dir.deleteRecursively() }
    }

    @Test fun audioAndVideoCachesAreIsolated() {
        val dir = Files.createTempDirectory("chat-audio-cache").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3)
            val audio = prepareCachedAudioFile(dir, bytes)
            val video = prepareCachedVideoFile(dir, bytes)
            assertNotEquals(audio.parentFile, video.parentFile)
            assertEquals("audio", audio.extension)
            assertArrayEquals(bytes, audio.readBytes())
            assertEquals(audio, prepareCachedAudioFile(dir, bytes))
        } finally { dir.deleteRecursively() }
    }

}
