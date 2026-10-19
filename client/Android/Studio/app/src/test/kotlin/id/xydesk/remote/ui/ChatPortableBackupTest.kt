package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class ChatPortableBackupTest {
    private fun row(id: String = "m1") = ChatMessage(id = id, name = "Alice", githubLogin = "alice", country = "ID", flag = "", text = "secret text", translated = "", targetLang = "EN", at = 1, mine = true, authorId = 42)
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: Exception) { }
    }
    @Test fun passwordAndAccountBoundRoundTripRejectsTampering() {
        val password = "a long independent passphrase".toCharArray()
        val plain = encodeChatArchive(listOf(row()))
        val file = ChatBackupCrypto.encrypt(plain, password, "Alice")
        assertArrayEquals(plain, ChatBackupCrypto.decrypt(file, password, "@alice"))
        rejects { ChatBackupCrypto.decrypt(file, "wrong passphrase".toCharArray(), "alice") }
        rejects { ChatBackupCrypto.decrypt(file, password, "mallory") }
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        rejects { ChatBackupCrypto.decrypt(file, password, "alice") }
    }
    @Test fun formatAndSizeBoundsRejectBeforeExpensiveKdf() {
        rejects { ChatBackupCrypto.decrypt(ByteArray(20), charArrayOf(), "alice") }
        rejects { ChatBackupCrypto.decrypt(ByteArray(ChatBackupCrypto.MAX_FILE + 1), charArrayOf(), "alice") }
        rejects { ChatBackupCrypto.encrypt(byteArrayOf(), "short".toCharArray(), "alice") }
        rejects { ChatBackupCrypto.encrypt(ByteArray(ChatBackupCrypto.MAX_PLAINTEXT + 1), "long enough password".toCharArray(), "alice") }
    }
    @Test fun excludesViewOncePendingAndLocalMessagesAndCapsHistory() {
        val messages = (1..85).map { row("m$it") } + listOf(row("local-1"), row("pending").copy(mutationPending = true), row("once").copy(viewOnce = true))
        val imported = decodeChatArchive(encodeChatArchive(messages))
        assertEquals(80, imported.size)
        assertEquals("m6", imported.first().id)
        assertEquals("m85", imported.last().id)
        assertTrue(imported.none { it.mine || it.authorId != 0L || it.e2eeVerified || it.mutationPending })
    }
    @Test fun importedIdsMustBePresentUniqueAndCanonical() {
        rejects { decodeChatArchive("""[{"id":""}]""".toByteArray()) }
        rejects { decodeChatArchive(encodeChatArchive(listOf(row(), row()))) }
        rejects { decodeChatArchive("""[{"id":"m1","revision":-1}]""".toByteArray()) }
    }
    @Test fun backupNeverSerializesMediaAndScrubsDeletedContent() {
        val original = row().copy(attachmentType = "image", attachmentData = "secret-media", e2eeSig = "signature")
        val encoded = encodeChatArchive(listOf(original, original.copy(id = "deleted", deleted = true, revision = 1)))
        val text = String(encoded, Charsets.UTF_8)
        assertFalse(text.contains("secret-media"))
        assertFalse(text.contains("signature"))
        val deleted = decodeChatArchive(encoded).last()
        assertEquals("Pesan ini dihapus", deleted.text)
        assertEquals("", deleted.attachmentType)
        assertEquals("", deleted.attachmentData)
    }
}
