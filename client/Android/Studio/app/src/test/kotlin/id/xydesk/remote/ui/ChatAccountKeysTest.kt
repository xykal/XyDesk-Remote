package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class ChatAccountKeysTest {
    @Test fun casingWhitespaceAndPrefixAreNormalized() {
        assertEquals(chatAccountPreferenceKey("seen", "alice"), chatAccountPreferenceKey("seen", " @Alice "))
    }
    @Test fun usersAndGuestNeverShareKeys() {
        val guest = chatAccountPreferenceKey("seen", null)
        assertNotEquals(guest, chatAccountPreferenceKey("seen", "guest"))
        assertNotEquals(chatAccountPreferenceKey("seen", "alice"), chatAccountPreferenceKey("seen", "bob"))
        assertEquals(guest, chatAccountPreferenceKey("seen", " "))
    }
    @Test fun preferenceKindsStayDistinctAndRawLoginIsNotInSuffix() {
        assertNotEquals(chatAccountPreferenceKey("seen", "alice"), chatAccountPreferenceKey("hidden", "alice"))
        assertFalse(chatAccountPreferenceKey("seen", "alice").contains("alice"))
    }
}
