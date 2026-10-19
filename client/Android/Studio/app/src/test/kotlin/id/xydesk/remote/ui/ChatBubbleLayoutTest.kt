package id.xydesk.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatBubbleLayoutTest {
    @Test fun narrowParentNeverForcesOldMediaMinimum() {
        assertEquals(106f, chatBubbleMaxWidth(150f, false), 0f)
        assertEquals(68f, chatBubbleMaxWidth(100f, true), 0f)
    }
    @Test fun normalAndTabletWidthsAreBounded() {
        assertEquals(276f, chatBubbleMaxWidth(320f, false), 0f)
        assertEquals(360f, chatBubbleMaxWidth(1000f, false), 0f)
        assertEquals(204f, chatBubbleMaxWidth(400f, true), 0f)
    }
    @Test fun tinyConstraintsNeverProduceNegativeWidths() {
        assertEquals(0f, chatBubbleMaxWidth(20f, false), 0f)
        assertEquals(0f, chatBubbleMaxWidth(0f, true), 0f)
    }
}
