package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class ChatHistoryFailurePolicyTest {
    @Test fun quotaIsNotPresentedAsEmptyHistoryOrSuccessfulSync() {
        val failure = chatHistoryFailure(503, "chat_storage_quota_exceeded", "300")
        assertTrue(failure.message.contains("Kuota"))
        assertTrue(failure.message.contains("Riwayat lokal"))
        assertEquals(300L, failure.retrySeconds)
    }
    @Test fun retryHeadersAreBoundedAndUnknownErrorsStayUnconfirmed() {
        assertEquals(10L, chatHistoryFailure(503, "", "-1").retrySeconds)
        assertEquals(600L, chatHistoryFailure(503, "", "99999999").retrySeconds)
        assertEquals(300L, chatHistoryFailure(503, "", "bad").retrySeconds)
        assertTrue(chatHistoryFailure(500, "", null).message.contains("belum tersinkron"))
    }
}
