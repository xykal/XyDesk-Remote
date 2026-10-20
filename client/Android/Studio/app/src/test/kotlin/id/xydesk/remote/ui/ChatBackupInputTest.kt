package id.xydesk.remote.ui

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class ChatBackupInputTest {
    @Test fun preservesBytesAndAllowsExactBound() {
        val bytes = ByteArray(ChatBackupCrypto.MAX_FILE) { (it % 251).toByte() }
        assertArrayEquals(bytes, ChatBackupInput.read(ByteArrayInputStream(bytes)))
        assertArrayEquals(byteArrayOf(), ChatBackupInput.read(ByteArrayInputStream(byteArrayOf())))
    }
    @Test fun rejectsOversizeWithoutReadingUnboundedInput() {
        var read = 0
        val input = object : InputStream() {
            override fun read(): Int { read++; return 1 }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(1, off, off + len); read += len; return len
            }
        }
        try { ChatBackupInput.read(input); fail("accepted oversized file") }
        catch (_: IllegalArgumentException) { }
        assertEquals(ChatBackupCrypto.MAX_FILE + 1, read)
    }
    @Test fun zeroProgressFailsInsteadOfSpinningAndWipesScratch() {
        var scratch: ByteArray? = null
        val input = object : InputStream() {
            override fun read(): Int = error("unexpected")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                scratch = b; b[0] = 42; return 0
            }
        }
        try { ChatBackupInput.read(input); fail("accepted stalled provider") }
        catch (_: IllegalStateException) { }
        assertTrue(scratch!!.all { it == 0.toByte() })
    }
    @Test fun providerFailureWipesScratchAndPropagates() {
        var scratch: ByteArray? = null
        val input = object : InputStream() {
            override fun read(): Int = error("unexpected")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                scratch = b; b[0] = 42; throw java.io.IOException("provider failed")
            }
        }
        try { ChatBackupInput.read(input); fail("ignored IO failure") }
        catch (_: java.io.IOException) { }
        assertTrue(scratch!!.all { it == 0.toByte() })
    }
}
