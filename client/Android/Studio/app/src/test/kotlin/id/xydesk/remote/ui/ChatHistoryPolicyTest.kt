package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class ChatHistoryPolicyTest {
    @Test fun staleConfirmedCacheIsRemoved() {
        assertFalse(keepAfterEmptyChatSnapshot("server-1", 1_000L, 20_000L))
    }
    @Test fun localPendingOrUnconfirmedIsPreserved() {
        assertTrue(keepAfterEmptyChatSnapshot("local-1", 1_000L, 20_000L))
    }
    @Test fun concurrentAndRecentArrivalsArePreserved() {
        assertTrue(keepAfterEmptyChatSnapshot("server-new", 21_000L, 20_000L))
        assertTrue(keepAfterEmptyChatSnapshot("server-race", 16_000L, 20_000L))
        assertFalse(keepAfterEmptyChatSnapshot("server-old", 15_999L, 20_000L))
    }
}
