package id.xydesk.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FeedbackShareDraftTest {
    @Test
    fun shareDraftContainsOnlyBrandCategoryAndUserText() {
        val draft = feedbackShareDraft("Feature idea", "Please add a touch mode.")
        assertEquals(
            "XyDesk Remote — XyVerse Technology Global\n" +
                "Category: Feature idea\n\n" +
                "Please add a touch mode.",
            draft,
        )
        assertFalse(draft.contains("password"))
        assertFalse(draft.contains("clipboard"))
        assertFalse(draft.contains("host"))
    }
}
