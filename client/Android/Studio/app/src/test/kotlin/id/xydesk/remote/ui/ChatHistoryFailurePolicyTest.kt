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
    @Test fun socketAuthFailureIsVisibleAndNotFastRetried() {
        val failure = chatSocketFailure(401, null)
        assertTrue(failure.message.contains("Autentikasi"))
        assertEquals(60L, failure.retrySeconds)
    }
    @Test fun socketServiceFailureHonorsBoundedRetryAfter() {
        assertEquals(300L, chatSocketFailure(503, null).retrySeconds)
        assertEquals(600L, chatSocketFailure(429, "9999999").retrySeconds)
        assertEquals(10L, chatSocketFailure(0, "-1").retrySeconds)
    }
    @Test fun quietConnectedSocketDoesNotFallBackToFiveSecondHistoryPolling() {
        assertEquals(300_000L, chatHistoryPollDelayMs(true))
        assertEquals(10_000L, chatHistoryPollDelayMs(false))
    }
}
