package id.xydesk.remote.ui
import org.junit.Assert.*
import org.junit.Test

class OfficialChannelPolicyTest {
    @Test fun knownTagsHaveStableCanonicalDestinations() {
        assertEquals("panduan-pengguna", officialChannelId("#PANDUANPengguna"))
        assertEquals("announcement", officialChannelId("#Pengumuman"))
        assertEquals("informasi", officialChannelId("#Informasi"))
        assertNull(officialChannelId("#unknown"))
    }
    @Test fun tagsHaveBoundariesAndDoNotMatchUrlsOrPrefixOfAnotherTag() {
        val text = "Baca #PanduanPengguna, #Informasi! #InformasiBaru https://example.com/#Announcement"
        val hits = channelTags(text)
        assertEquals(listOf("panduan-pengguna", "informasi"), hits.map { it.channelId })
        assertEquals("#PanduanPengguna", text.substring(hits[0].start, hits[0].end))
        assertTrue(channelTags("abc#Informasi ##Announcement").isEmpty())
    }
    @Test fun backgroundArrivalsDoNotStealTheReaderPosition() {
        assertFalse(shouldFollowChat(false))
        assertTrue(shouldFollowChat(true))
        assertTrue(shouldFollowChat(false, explicitOwnSend = true))
    }
    @Test fun confirmedSoundDeduplicatesHttpWsAndHistoryAndRejectsOptimisticIds() {
        val gate=ChatSentSoundGate(2)
        assertFalse(gate.accept("local-123")); assertFalse(gate.accept(""))
        assertTrue(gate.accept("server-a")); assertFalse(gate.accept("server-a"))
        assertTrue(gate.accept("server-b")); assertTrue(gate.accept("server-c"))
        assertFalse(gate.accept("server-c")); assertTrue(gate.accept("server-a"))
    }
}
