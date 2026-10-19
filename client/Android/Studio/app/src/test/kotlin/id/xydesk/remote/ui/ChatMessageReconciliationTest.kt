package id.xydesk.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageReconciliationTest {
    private fun message(id: String, clientId: String = "", login: String = "alice", mine: Boolean = true) =
        ChatMessage(id = id, name = login, githubLogin = login, country = "ID", flag = "",
            text = "identical text", translated = "", targetLang = "EN", at = 100L, mine = mine,
            isFounder = false, clientMessageId = clientId)

    @Test fun sameTextAcknowledgesOnlyEchoedId() {
        val local = listOf(message("local-a"), message("local-b"))
        assertEquals(1, findPendingChatIndex(local, message("server", "local-b")))
    }
    @Test fun noEchoNeverGuessesUsingText() {
        assertEquals(-1, findPendingChatIndex(listOf(message("local-a")), message("server")))
    }
    @Test fun anotherSenderOrNonMineCannotAcknowledgeLocal() {
        val local = listOf(message("local-a"))
        assertEquals(-1, findPendingChatIndex(local, message("server", "local-a", "bob")))
        assertEquals(-1, findPendingChatIndex(local, message("server", "local-a", mine = false)))
    }
    @Test fun missingOrNonLocalIdsDoNotMatch() {
        assertEquals(-1, findPendingChatIndex(listOf(message("server-old")), message("server", "server-old")))
    }
}
