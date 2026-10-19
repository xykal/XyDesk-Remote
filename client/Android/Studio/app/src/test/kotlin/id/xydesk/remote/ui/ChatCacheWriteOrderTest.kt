package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class ChatCacheWriteOrderTest {
    @Test fun confirmedMutationInvalidatesPreDeleteDebouncedSnapshot() {
        val order = ChatCacheWriteOrder()
        val before = order.advance("alice")
        val confirmedDelete = order.advance("alice")
        assertFalse(order.isCurrent("alice", before))
        assertTrue(order.isCurrent("alice", confirmedDelete))
    }
    @Test fun writeEpochsAreIsolatedByAccount() {
        val order = ChatCacheWriteOrder()
        val alice = order.advance("alice")
        order.advance("bob"); order.advance("bob")
        assertTrue(order.isCurrent("alice", alice))
        assertFalse(order.isCurrent("unknown", alice))
    }
}
