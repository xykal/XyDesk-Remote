package id.xydesk.remote.ui

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class ChatOutboxStoreTest {
    private class MemoryDisk : OutboxDisk {
        val files = linkedMapOf<String, ByteArray>()
        var fail = false
        override fun names() = files.keys.toList()
        override fun read(name: String) = files.getValue(name).clone()
        override fun writeAtomic(name: String, bytes: ByteArray) { check(!fail) { "disk full" }; files[name] = bytes.clone() }
        override fun delete(name: String) { files.remove(name) }
        override fun sizeBytes() = files.values.sumOf { it.size.toLong() }
    }
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
    private fun store(disk: MemoryDisk) = ChatOutboxStore(disk) { key }
    private fun entry(i: Int = 0, content: String = "PRIVATE-TEXT") : OutboxEntry {
        val id = "local-519f0398-4c72-49c2-ae83-${i.toString().padStart(12, '0')}"
        return OutboxEntry(id,"alice",ChatOutboxPolicy.fingerprint("EXAMPLE-CREDENTIAL-NOT-SAVED"),1_800_000_000_000,
            JSONObject().put("client_msg_id",id).put("text",content).put("github_login","alice").toString())
    }
    private fun fails(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (expected: Exception) { } }

    @Test fun encryptedRoundTripSurvivesStoreRecreationWithoutPlaintextCredentials() {
        val d = MemoryDisk(); val original = entry(); store(d).enqueue(original)
        assertEquals(original, store(d).entries().single())
        val raw = String(d.files.values.single(), Charsets.ISO_8859_1)
        assertFalse(raw.contains("PRIVATE-TEXT")); assertFalse(raw.contains("EXAMPLE-CREDENTIAL-NOT-SAVED"))
        assertFalse(raw.contains("alice"))
    }
    @Test fun mediaAndViewOnceStayOnlyInPrivatePendingEnvelopeThenAreRemovedOnAck() {
        val d = MemoryDisk(); val s = store(d); val e = entry().let {
            it.copy(payload = JSONObject(it.payload).put("attachment_data","PRIVATE-MEDIA").put("view_once",true).toString())
        }
        s.enqueue(e); s.update(e.id) { it.copy(ownerId=42) }
        s.update(e.id) { it.copy(state="accepted",payload="",receipt=JSONObject().put("id","server-1")
            .put("author_id",42).put("client_msg_id",e.id).toString()) }
        val accepted = store(d).entries().single(); assertEquals("accepted",accepted.state)
        assertEquals("",accepted.payload); assertFalse(accepted.receipt.contains("PRIVATE"))
    }
    @Test fun nonceFilenameIsAuthenticatedAndCannotBeRebound() {
        val d = MemoryDisk(); val s = store(d); s.enqueue(entry(1)); s.enqueue(entry(2))
        val names=d.names(); d.files[names[0]]=d.files.getValue(names[1]); fails { s.entries() }
    }
    @Test fun ciphertextTamperAndWrongKeyFailClosedWithoutResetOrDeletion() {
        val d = MemoryDisk(); val s = store(d); s.enqueue(entry()); val saved = d.files.values.single().clone()
        d.files.values.single()[20] = (saved[20].toInt() xor 1).toByte(); fails { s.entries() }
        assertEquals(1,d.files.size)
        d.files[d.names().single()] = saved
        fails { ChatOutboxStore(d) { SecretKeySpec(ByteArray(32),"AES") }.entries() }
        assertEquals(1,d.files.size)
    }
    @Test fun storageFailureNeverClaimsEnqueueOrAcknowledgement() {
        val d = MemoryDisk(); val s = store(d); d.fail=true; fails { s.enqueue(entry()) }; assertTrue(d.files.isEmpty())
        d.fail=false; s.enqueue(entry()); d.fail=true
        fails { s.update(entry().id) { it.copy(ownerId=42) } }
        assertEquals(0L,store(d).entries().single().ownerId)
        assertEquals("pending",store(d).entries().single().state)
    }
    @Test fun fullQueueNeverEvictsUnsentContent() {
        val d = MemoryDisk(); val s = store(d)
        repeat(ChatOutboxStore.MAX_PENDING) { s.enqueue(entry(it)) }
        fails { s.enqueue(entry(99)) }; assertEquals(ChatOutboxStore.MAX_PENDING,s.entries().size)
    }
    @Test fun unexpectedPayloadKeysAndTraversalIdsCannotBeSerialized() {
        val d = MemoryDisk(); val s=store(d); val e=entry()
        for (field in listOf("authorization","token","access_token"))
            fails { s.enqueue(e.copy(payload=JSONObject(e.payload).put(field,"SECRET").toString())) }
        fails { s.enqueue(e.copy(id="../outside")) }; assertTrue(d.files.isEmpty())
    }
    @Test fun immutablePayloadAndBoundPrincipalCannotBeChangedDuringRetry() {
        val d = MemoryDisk(); val s=store(d); val e=entry(); s.enqueue(e)
        fails { s.update(e.id) { it.copy(payload=JSONObject(it.payload).put("text","changed").toString()) } }
        s.update(e.id) { it.copy(ownerId=42) }; fails { s.update(e.id) { it.copy(ownerId=99) } }
        assertEquals(e.payload,s.entries().single().payload)
    }
    @Test fun cancelledEntryDropsPayloadAndCannotBeResurrectedByLateNetworkResult() {
        val d=MemoryDisk(); val s=store(d); val e=entry(); s.enqueue(e)
        s.update(e.id) { it.copy(state="cancelled",payload="") }
        fails { s.update(e.id) { it.copy(state="pending",payload=e.payload) } }
        assertEquals("cancelled",store(d).entries().single().state)
    }
    @Test fun corruptedExistingQueuePreventsSilentReplacement() {
        val d=MemoryDisk(); val s=store(d); s.enqueue(entry()); d.files[d.names().single()]=ByteArray(35)
        fails { s.enqueue(entry(1)) }; assertEquals(1,d.files.size)
    }
    @Test fun oversizedPayloadIsRejectedWithoutEvictingExistingQueue() {
        val d=MemoryDisk(); val s=store(d); s.enqueue(entry())
        fails { s.enqueue(entry(1,"x".repeat(ChatOutboxStore.MAX_FILE_BYTES))) }
        assertEquals(1,s.entries().size)
    }
    @Test fun missingKeystoreKeyDoesNotRegenerateOrDeleteExistingData() {
        val d=MemoryDisk(); store(d).enqueue(entry()); val before=d.files.values.single().clone()
        val broken=ChatOutboxStore(d) { create -> check(!create) { "must not regenerate" }; error("key unavailable") }
        fails { broken.entries() }; assertTrue(before.contentEquals(d.files.values.single()))
    }

}
