package id.xydesk.remote.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ChatOutboxPolicyTest {
    private val id="local-519f0398-4c72-49c2-ae83-912056627f83"
    private val e=OutboxEntry(id,"alice",ChatOutboxPolicy.fingerprint("old-token"),1,"{}",ownerId=42)
    private fun response(message: JSONObject? = JSONObject().put("id","server-1").put("author_id",42).put("client_msg_id",id)) =
        JSONObject().put("status","ok").put("send_protocol",1).put("receipt_only",message==null)
            .put("receipt",JSONObject().put("id","server-1").put("author_id",42).put("client_msg_id",id))
            .put("message",message ?: JSONObject.NULL)
    @Test fun unboundQueueRequiresExactOriginalCredentialNotReusedUsername() {
        val unbound=e.copy(ownerId=0)
        assertTrue(ChatOutboxPolicy.belongsTo(unbound,unbound.credentialFingerprint))
        assertFalse(ChatOutboxPolicy.belongsTo(unbound,ChatOutboxPolicy.fingerprint("new-token"),42))
    }
    @Test fun boundQueueResumesOnlyWithSameVerifiedProviderIdAfterTokenRotation() {
        assertTrue(ChatOutboxPolicy.belongsTo(e,ChatOutboxPolicy.fingerprint("new-token"),42))
        assertFalse(ChatOutboxPolicy.belongsTo(e,e.credentialFingerprint,99))
        assertFalse(ChatOutboxPolicy.belongsTo(e,ChatOutboxPolicy.fingerprint("new-token"),99))
    }
    @Test fun receiptsRequireProtocolNonceAndAuthorNotHttp200OrSocketQueueSuccess() {
        assertNotNull(ChatOutboxPolicy.receipt(response(),e,42))
        assertNull(ChatOutboxPolicy.receipt(JSONObject().put("status","ok"),e,42))
        assertNull(ChatOutboxPolicy.receipt(response().put("send_protocol",0),e,42))
        assertNull(ChatOutboxPolicy.receipt(response(),e,99))
        val forged=response(); forged.getJSONObject("receipt").put("client_msg_id","other")
        assertNull(ChatOutboxPolicy.receipt(forged,e,42))
    }
    @Test fun receiptOnlyAcknowledgesWithoutReconstructingQueuedTextOrMedia() {
        val receipt=ChatOutboxPolicy.receipt(response(null),e,42)
        assertNotNull(receipt); assertFalse(receipt!!.has("text")); assertFalse(receipt.has("attachment_data"))
        assertNull(ChatOutboxPolicy.receipt(response(null).put("receipt_only",false),e,42))
    }
    @Test fun unrelatedCanonicalMessageCannotAcknowledgeSend() {
        val r=response(); r.getJSONObject("message").put("id","other")
        assertNull(ChatOutboxPolicy.receipt(r,e,42))
    }
    @Test fun retryAfterIsMinimumAndExponentialDelayIsBounded() {
        assertEquals(300_000L,ChatOutboxPolicy.delayMs(0,300))
        assertEquals(30_000L,ChatOutboxPolicy.delayMs(0))
        assertEquals(60_000L,ChatOutboxPolicy.delayMs(1))
        assertEquals(3_600_000L,ChatOutboxPolicy.delayMs(1000))
        assertEquals(86_400_000L,ChatOutboxPolicy.delayMs(0,Long.MAX_VALUE))
    }
}
