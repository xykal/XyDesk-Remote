package id.xydesk.remote.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class ChatOutboxControlPolicyTest {
    private val now = 1_800_000_000_000L
    private val binding = ChatOutboxPolicy.fingerprint("test-credential-only")
    private val id = "local-519f0398-4c72-49c2-ae83-912056627f83"
    private fun entry() = OutboxEntry(id,"alice",binding,now-1000,
        JSONObject().put("client_msg_id",id).put("text","draft").put("reply_to_id","original-reply")
            .put("e2ee_cipher","same-envelope").toString(),ownerId=42,attempts=3)
    private fun fails(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (expected: Exception) { } }
    @Test fun retryPreservesOriginalNonceTimePayloadAndAttemptHistory() {
        val original=entry()
        val retried=ChatOutboxControlPolicy.retry(original,binding,42,now)
        assertEquals(original.id,retried.id);assertEquals(original.createdAt,retried.createdAt)
        assertEquals(original.payload,retried.payload);assertEquals(3,retried.attempts)
        assertEquals(0L,retried.nextAttemptAt);assertEquals("pending",retried.state)
    }
    @Test fun manualRetryCannotSkipEvenOneMillisecondOfServerCooldown() {
        val waiting=entry().copy(nextAttemptAt=now+1)
        assertEquals(OutboxRetryState.WAIT,ChatOutboxControlPolicy.retryState(waiting,binding,42,now))
        fails { ChatOutboxControlPolicy.retry(waiting,binding,42,now) }
        assertEquals(OutboxRetryState.READY,ChatOutboxControlPolicy.retryState(waiting,binding,42,now+1))
    }
    @Test fun sevenDayDeadlineCannotBeResetByRetry() {
        val expired=entry().copy(createdAt=now-ChatOutboxPolicy.MAX_AGE_MS)
        assertEquals(OutboxRetryState.EXPIRED,ChatOutboxControlPolicy.retryState(expired,binding,42,now))
        fails { ChatOutboxControlPolicy.retry(expired,binding,42,now) }
    }
    @Test fun providerExpiredCodeRemainsBlockedAfterLocalClockMovesBack() {
        val expired=entry().copy(issue="outbox_expired",nextAttemptAt=Long.MAX_VALUE)
        assertEquals(OutboxRetryState.EXPIRED,ChatOutboxControlPolicy.retryState(expired,binding,42,now))
        fails { ChatOutboxControlPolicy.retry(expired,binding,42,now) }
    }
    @Test fun authPausedRetryRequiresFreshVerifiedIdentity() {
        val paused=entry().copy(issue="authentication_required",nextAttemptAt=Long.MAX_VALUE)
        assertEquals(OutboxRetryState.NEEDS_LOGIN,ChatOutboxControlPolicy.retryState(paused,binding,42,now))
        fails { ChatOutboxControlPolicy.retry(paused,binding,0,now) }
        val retried=ChatOutboxControlPolicy.retry(paused,binding,42,now)
        assertEquals("",retried.issue);assertEquals(0L,retried.nextAttemptAt)
    }
    @Test fun changedPayloadOrPermanentConflictCannotBeBypassedWithNewNonce() {
        val blocked=entry().copy(issue="idempotency_conflict",nextAttemptAt=Long.MAX_VALUE)
        assertEquals(OutboxRetryState.PERMANENT,ChatOutboxControlPolicy.retryState(blocked,binding,42,now))
        fails { ChatOutboxControlPolicy.retry(blocked,binding,42,now) }
        assertEquals(id,blocked.id)
    }
    @Test fun futureClockIsNotAutomaticallyRetimestamped() {
        val future=entry().copy(createdAt=now+300_001)
        assertEquals(OutboxRetryState.CLOCK,ChatOutboxControlPolicy.retryState(future,binding,42,now))
        fails { ChatOutboxControlPolicy.retry(future,binding,42,now) }
    }
    @Test fun wrongNumericAccountCannotRetryCancelOrCopyEvenWithSameCredentialClaim() {
        val e=entry()
        assertEquals(OutboxRetryState.WRONG_ACCOUNT,ChatOutboxControlPolicy.retryState(e,binding,99,now))
        fails { ChatOutboxControlPolicy.retry(e,binding,99,now) }
        fails { ChatOutboxControlPolicy.cancel(e,binding,99) }
        fails { ChatOutboxControlPolicy.copyText(e,binding,99) }
    }
    @Test fun unboundQueueNeverMigratesByUsernameAfterCredentialRotation() {
        val e=entry().copy(ownerId=0)
        val rotated=ChatOutboxPolicy.fingerprint("rotated-test-credential")
        assertEquals(OutboxRetryState.WRONG_ACCOUNT,ChatOutboxControlPolicy.retryState(e,rotated,42,now))
        fails { ChatOutboxControlPolicy.retry(e,rotated,42,now) }
        fails { ChatOutboxControlPolicy.copyText(e,rotated,42) }
    }
    @Test fun boundSameProviderCanRetryAfterVerifiedTokenRotationWithoutChangingPayload() {
        val e=entry();val rotated=ChatOutboxPolicy.fingerprint("rotated-test-credential")
        val retried=ChatOutboxControlPolicy.retry(e,rotated,42,now)
        assertEquals(e.id,retried.id);assertEquals(e.payload,retried.payload)
        assertEquals(e.credentialFingerprint,retried.credentialFingerprint)
    }
    @Test fun viewOnceCaptionCannotBeCopiedEvenByItsOwner() {
        val e=entry().copy(payload=JSONObject(entry().payload).put("view_once",true).put("attachment_data","private-media").toString())
        fails { ChatOutboxControlPolicy.copyText(e,binding,42) }
        assertTrue(ChatOutboxControlPolicy.preview(e).viewOnce)
        assertFalse(ChatOutboxControlPolicy.preview(e).toString().contains("private-media"))
    }
    @Test fun ordinaryTextCopyDoesNotExportAttachmentOrCiphertext() {
        val e=entry().copy(payload=JSONObject(entry().payload).put("attachment_type","image").put("attachment_data","private-media").toString())
        assertEquals("draft",ChatOutboxControlPolicy.copyText(e,binding,42))
        assertFalse(ChatOutboxControlPolicy.preview(e).toString().contains("same-envelope"))
    }
    @Test fun cancelledAndAcceptedEntriesNeverBecomePendingAgain() {
        val cancelled=ChatOutboxControlPolicy.cancel(entry(),binding,42)
        assertEquals("cancelled",cancelled.state);assertEquals("",cancelled.payload)
        fails { ChatOutboxControlPolicy.retry(cancelled,binding,42,now) }
        val accepted=entry().copy(state="accepted",payload="",receipt="server-receipt")
        assertEquals(accepted,ChatOutboxControlPolicy.cancel(accepted,binding,42))
        fails { ChatOutboxControlPolicy.copyText(accepted,binding,42) }
    }
    @Test fun failedDurableCancellationLeavesOriginalPendingPayloadRecoverable() {
        val files=mutableMapOf<String,ByteArray>();var fail=false
        val disk=object:OutboxDisk {
            override fun names()=files.keys.toList()
            override fun read(name:String)=files.getValue(name).clone()
            override fun writeAtomic(name:String,bytes:ByteArray) { check(!fail) { "disk full" }; files[name]=bytes.clone() }
            override fun delete(name:String) { files.remove(name) }
            override fun sizeBytes()=files.values.sumOf { it.size.toLong() }
        }
        val key=SecretKeySpec(ByteArray(32) { it.toByte() },"AES")
        val store=ChatOutboxStore(disk) { key };store.enqueue(entry());fail=true
        fails { store.update(id) { ChatOutboxControlPolicy.cancel(it,binding,42) } }
        val restored=ChatOutboxStore(disk) { key }.entries().single()
        assertEquals(entry(),restored)
    }
    @Test fun authPausedRetryAlsoHonorsPersistedRetryAfterFloor() {
        val e=entry().copy(issue="authentication_required",nextAttemptAt=Long.MAX_VALUE,retryNotBefore=now+300_000)
        assertEquals(OutboxRetryState.WAIT,ChatOutboxControlPolicy.retryState(e,binding,42,now))
        fails { ChatOutboxControlPolicy.retry(e,binding,42,now) }
        assertEquals(OutboxRetryState.NEEDS_LOGIN,ChatOutboxControlPolicy.retryState(e,binding,42,now+300_000))
    }
    @Test fun verifiedTokenRotationDoesNotEraseRetryAfter() {
        val e=entry().copy(issue="authentication_required",nextAttemptAt=Long.MAX_VALUE,retryNotBefore=now+300_000)
        val rotated=ChatOutboxPolicy.fingerprint("rotated-test-credential")
        fails { ChatOutboxControlPolicy.retry(e,rotated,42,now) }
        val retried=ChatOutboxControlPolicy.retry(e,rotated,42,now+300_000)
        assertEquals(e.retryNotBefore,retried.retryNotBefore)
    }
    @Test fun resettingNextAttemptAloneCannotBypassIndependentServerDeadline() {
        val e=entry().copy(nextAttemptAt=0,retryNotBefore=now+1)
        assertEquals(OutboxRetryState.WAIT,ChatOutboxControlPolicy.retryState(e,binding,42,now))
        fails { ChatOutboxControlPolicy.retry(e,binding,42,now) }
    }

}
