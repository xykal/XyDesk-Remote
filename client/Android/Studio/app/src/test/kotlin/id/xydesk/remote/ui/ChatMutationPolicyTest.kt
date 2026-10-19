package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class ChatMutationPolicyTest {
    private fun row() = ChatMessage(id = "m-1", name = "Alice", githubLogin = "alice", country = "ID", flag = "",
        text = "original", translated = "translated", targetLang = "EN", at = 1, mine = true, authorId = 42)

    @Test fun httpSuccessNeedsMatchingIdentityRevisionAndRequestedOutcome() {
        val original = row()
        assertFalse(isConfirmedChatMutation(original, original, "edit", "original"))
        assertFalse(isConfirmedChatMutation(original, original.copy(id = "other", revision = 1), "edit", "original"))
        assertFalse(isConfirmedChatMutation(original, original.copy(revision = 1), "delete", ""))
        assertTrue(isConfirmedChatMutation(original, original.copy(text = "new", revision = 1), "edit", "new"))
        assertTrue(isConfirmedChatMutation(original, original.copy(revision = 1).deletedPlaceholder(), "delete", ""))
    }
    @Test fun deletionScrubsTextMediaQuotesAndOldVerification() {
        val deleted = row().copy(attachmentType = "image", attachmentData = "secret", replyToText = "secret", e2eeSig = "sig", e2eeVerified = true).deletedPlaceholder(10)
        assertTrue(deleted.deleted)
        assertEquals("Pesan ini dihapus", deleted.text)
        assertEquals("", deleted.attachmentData)
        assertEquals("", deleted.attachmentType)
        assertEquals("", deleted.replyToText)
        assertEquals("", deleted.translated)
        assertFalse(deleted.e2eeVerified)
        assertEquals("", deleted.e2eeSig)
    }
    @Test fun nicknameOrLegacyRowsCannotGrantRemoteMutationPermission() {
        assertTrue(canMutateChatMessage(row(), "alice"))
        assertFalse(canMutateChatMessage(row(), "mallory"))
        assertFalse(canMutateChatMessage(row().copy(authorId = 0), "alice"))
        assertFalse(canMutateChatMessage(row().copy(id = "local-1"), "alice"))
        assertFalse(canMutateChatMessage(row().copy(mutationPending = true), "alice"))
        assertFalse(canMutateChatMessage(row().deletedPlaceholder(), "alice"))
    }
    @Test fun staleHistoryCannotOverwriteNewerEditOrTombstone() {
        val current = row().copy(text = "edited", revision = 2)
        assertEquals(current, reconcileChatMessage(current, row().copy(revision = 1)))
        val deleted = current.deletedPlaceholder()
        assertTrue(reconcileChatMessage(deleted, current).deleted)
    }
    @Test fun pendingUiIgnoresSameRevisionButAcceptsAuthoritativeAck() {
        val pending = row().copy(text = "new text", mutationPending = true, mutationId = "op")
        assertEquals(pending, reconcileChatMessage(pending, row()))
        val ack = row().copy(text = "new text", revision = 1)
        assertEquals(ack, reconcileChatMessage(pending, ack))
        assertFalse(reconcileChatMessage(pending, ack).mutationPending)
    }
    @Test fun deletionCannotRehydrateOldAttachmentFromCache() {
        val current = row().copy(attachmentType = "image", attachmentData = "secret")
        val incoming = row().copy(deleted = true, revision = 1, attachmentData = "old secret")
        assertEquals("", reconcileChatMessage(current, incoming).attachmentData)
    }
    @Test fun staleReplyPreviewCannotResurrectDeletedText() {
        val current = row().copy(replyToId = "deleted-id", replyToText = "Pesan ini dihapus")
        assertEquals("Pesan ini dihapus", reconcileChatMessage(current, current.copy(replyToText = "secret")).replyToText)
    }
}
