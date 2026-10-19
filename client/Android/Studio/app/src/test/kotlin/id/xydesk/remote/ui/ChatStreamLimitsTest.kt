package id.xydesk.remote.ui

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class ChatStreamLimitsTest {
    @Test fun exactLimitAndEmptyAreAccepted() {
        assertArrayEquals(byteArrayOf(1, 2, 3), ByteArrayInputStream(byteArrayOf(1, 2, 3)).readChatBytesAtMost(3))
        assertArrayEquals(byteArrayOf(), ByteArrayInputStream(byteArrayOf()).readChatBytesAtMost(1))
    }
    @Test(expected = IOException::class) fun oversizedChunkIsRejected() {
        ByteArrayInputStream(ByteArray(10_000)).readChatBytesAtMost(100)
    }
    @Test(expected = IllegalArgumentException::class) fun zeroLimitIsRejected() {
        ByteArrayInputStream(byteArrayOf()).readChatBytesAtMost(0)
    }
    @Test fun zeroByteReadsCannotCauseInfiniteLoop() {
        val stream = object : InputStream() {
            var done = false
            override fun read(bytes: ByteArray, off: Int, len: Int): Int = 0
            override fun read(): Int = if (done) -1 else { done = true; 7 }
        }
        assertArrayEquals(byteArrayOf(7), stream.readChatBytesAtMost(1))
    }
}
