package id.xydesk.remote.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDeliveryStateTest {
    @Test fun onlyAcknowledgementProducesDeliveredTicks() {
        assertEquals(2, deliveryTicksAfterAttempt(true, true))
        assertEquals(2, deliveryTicksAfterAttempt(true, false))
    }
    @Test fun socketQueueAcceptanceIsStillPending() {
        assertEquals(0, deliveryTicksAfterAttempt(false, true))
    }
    @Test fun failedTransportsDoNotShowSentTicks() {
        assertEquals(-1, deliveryTicksAfterAttempt(false, false))
    }
}
