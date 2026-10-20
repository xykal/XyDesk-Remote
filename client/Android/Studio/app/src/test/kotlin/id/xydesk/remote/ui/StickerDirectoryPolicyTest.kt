package id.xydesk.remote.ui

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class StickerDirectoryPolicyTest {
    @Test fun writesOnlyCanonicalWithInternalFallbackNotMirror() {
        assertEquals(listOf(File("/media/app/globalchat/sticker"), File("/private/globalchat/sticker")),
            StickerDirectoryPolicy.writeCandidates(listOf(File("/media/app"), File("/sdcard/app")), File("/private")))
    }

    @Test fun missingExternalStorageStillUsesCanonicalInternalPath() {
        assertEquals(listOf(File("/private/globalchat/sticker")),
            StickerDirectoryPolicy.writeCandidates(emptyList(), File("/private")))
    }

    @Test fun readsLegacyAndInternalEvenWhenExternalStorageReturns() {
        val dirs = StickerDirectoryPolicy.readDirectories(listOf(File("/media/app")), File("/private"))
        assertEquals(listOf("/media/app/globalchat/sticker", "/media/app/globwlchat/sticker",
            "/private/globalchat/sticker", "/private/globwlchat/sticker"), dirs.map { it.path })
    }

    @Test fun listingAndPlanningWritesNeverCreateOrDeleteDirectories() {
        val root = Files.createTempDirectory("sticker-policy").toFile()
        try {
            val media = File(root, "media")
            val legacy = File(media, "globwlchat/sticker").apply { mkdirs() }
            val saved = File(legacy, "old.webp.crypto15").apply { writeText("preserve") }
            val internal = File(root, "private")
            StickerDirectoryPolicy.readDirectories(listOf(media), internal)
            StickerDirectoryPolicy.writeCandidates(listOf(media), internal)
            assertFalse(File(media, "globalchat").exists())
            assertFalse(internal.exists())
            assertEquals("preserve", saved.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun duplicateMediaRootsDoNotDoubleListOrWrite() {
        val roots = listOf(File("/media/app"), File("/media/app"))
        assertEquals(4, StickerDirectoryPolicy.readDirectories(roots, File("/private")).size)
        assertEquals(2, StickerDirectoryPolicy.writeCandidates(roots, File("/private")).size)
    }

    @Test fun invalidFileNamesCannotEscapeStickerDirectory() {
        for (name in listOf("../bad.webp.crypto15", "a/b.webp.crypto15", "a\\b.webp.crypto15", "", "bad.webp", "\n.webp.crypto15"))
            assertFalse(name, StickerDirectoryPolicy.isSafeFileName(name))
        assertTrue(StickerDirectoryPolicy.isSafeFileName("Example+1+$+$9.webp.crypto15"))
    }
    @Test fun successfulExternalWriteStopsBeforeFallbackNoMirror() {
        val attempted = mutableListOf<File>()
        val result = StickerDirectoryPolicy.writeFirstAvailable(listOf(File("/media/app")), File("/private")) {
            attempted.add(it); "saved"
        }
        assertEquals("saved", result)
        assertEquals(listOf(File("/media/app/globalchat/sticker")), attempted)
    }

    @Test fun failedExternalWriteFallsBackOnceWithoutLegacyWrite() {
        val attempted = mutableListOf<File>()
        val result = StickerDirectoryPolicy.writeFirstAvailable(listOf(File("/media/app")), File("/private")) {
            attempted.add(it); if (attempted.size == 1) null else "saved"
        }
        assertEquals("saved", result)
        assertEquals(listOf(File("/media/app/globalchat/sticker"), File("/private/globalchat/sticker")), attempted)
    }

    @Test fun allWritesFailNeverReturnFalseSuccess() {
        val attempted = mutableListOf<File>()
        val result = StickerDirectoryPolicy.writeFirstAvailable<String>(listOf(File("/media/app")), File("/private")) {
            attempted.add(it); null
        }
        assertNull(result)
        assertEquals(2, attempted.size)
    }

}
