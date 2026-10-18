package id.xydesk.remote.ui

import org.junit.Assert.*
import org.junit.Test

class ChatImageSizingTest {
    @Test fun smallImagesDoNotUpscaleOrDivideByZero() {
        assertEquals(1, chatImageSampleSize(120, 120, 128))
    }
    @Test fun largeImagesAreDownsampledWithinBound() {
        assertEquals(16, chatImageSampleSize(1600, 900, 128))
        assertEquals(4, chatImageSampleSize(8192, 4096, 2048))
        assertEquals(4, chatImageSampleSize(257, 1, 128))
    }
    @Test fun brokenOrExtremeDimensionsAreRejected() {
        assertNull(chatImageSampleSize(-1, 10, 128))
        assertNull(chatImageSampleSize(10, 0, 128))
        assertNull(chatImageSampleSize(Int.MAX_VALUE, 10, 128))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidTargetIsRejected() {
        chatImageSampleSize(10, 10, 0)
    }
}
