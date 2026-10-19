package id.xydesk.remote.ui

import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PadPressTransactionTest {
    @Test fun normalPressReleasesInOrder() = runBlocking {
        val sent = mutableListOf<Boolean>()
        dispatchPadPress({ sent.add(it) }) { assertEquals(listOf(true), sent) }
        assertEquals(listOf(true, false), sent)
    }

    @Test fun disablingWhileHeldDoesNotSkipRelease() = runBlocking {
        val sent = mutableListOf<Boolean>()
        var enabled = true
        if (enabled) dispatchPadPress({ sent.add(it) }) { enabled = false }
        assertFalse(enabled)
        assertEquals(listOf(true, false), sent)
    }

    @Test fun cancellationStillReleasesAndPropagates() = runBlocking {
        val sent = mutableListOf<Boolean>()
        val cancellation = CancellationException("pointer cancelled")
        try {
            dispatchPadPress({ sent.add(it) }) { throw cancellation }
            fail("Cancellation must propagate")
        } catch (expected: CancellationException) {
            assertSame(cancellation, expected)
        }
        assertEquals(listOf(true, false), sent)
    }

    @Test fun failedDownStillAttemptsRelease() = runBlocking {
        val sent = mutableListOf<Boolean>()
        val failure = IllegalStateException("transport callback failed")
        try {
            dispatchPadPress({ down -> sent.add(down); if (down) throw failure }) {
                fail("Cannot wait after failed down")
            }
            fail("Failure must propagate")
        } catch (expected: IllegalStateException) {
            assertSame(failure, expected)
        }
        assertEquals(listOf(true, false), sent)
    }
}
