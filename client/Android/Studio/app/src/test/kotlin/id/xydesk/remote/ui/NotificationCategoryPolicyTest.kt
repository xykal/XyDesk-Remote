package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class NotificationCategoryPolicyTest {
    @Test fun mentionsAndRepliesHaveDistinctFilters() {
        assertTrue(notificationCategoryMatches("reply", "reply"))
        assertFalse(notificationCategoryMatches("reply", "mention"))
        assertTrue(notificationCategoryMatches("mention", "mention"))
        assertFalse(notificationCategoryMatches("mention", "reply"))
    }
    @Test fun likesAndReactionsHaveDistinctFilters() {
        assertFalse(notificationCategoryMatches("like", "reaction"))
        assertFalse(notificationCategoryMatches("reaction", "like"))
        assertTrue(notificationCategoryMatches("reaction", "reaction"))
    }
    @Test fun commentsKeepLegacyInboxMappingAndAllIncludesFutureCategories() {
        assertTrue(notificationCategoryMatches("inbox", "comment"))
        assertTrue(notificationCategoryMatches("inbox", "inbox"))
        assertTrue(notificationCategoryMatches("all", "future-category"))
        assertEquals("Sebutan @", notificationCategoryLabel("mention").first)
    }
}
