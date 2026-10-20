package id.xydesk.remote.ui

import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface OutboxDisk {
    fun names(): List<String>
    fun read(name: String): ByteArray
    fun writeAtomic(name: String, bytes: ByteArray)
    fun delete(name: String)
    fun sizeBytes(): Long
}

/** Device-only authenticated encryption; injectable disk/key for real JVM round-trip/failure tests. */
internal class ChatOutboxStore(private val disk: OutboxDisk, private val key: (Boolean) -> SecretKey) {
    companion object {
        const val MAX_FILE_BYTES = 3_200_000
        const val MAX_TOTAL_BYTES = 24_000_000L
        const val MAX_PENDING = 32
        const val MAX_RECORDS = 160
    }

    private fun name(id: String): String { require(ChatOutboxPolicy.LOCAL_ID.matches(id)); return "$id.queue" }
    private fun encode(e: OutboxEntry) = JSONObject().put("v", 1).put("id", e.id).put("login", e.login)
        .put("credential_fingerprint", e.credentialFingerprint).put("created_at", e.createdAt).put("payload", e.payload)
        .put("owner_id", e.ownerId).put("state", e.state).put("attempts", e.attempts)
        .put("next_attempt_at", e.nextAttemptAt).put("issue", e.issue)
        .put("last_attempt_fingerprint", e.lastAttemptFingerprint).put("receipt", e.receipt).toString()

    private fun decode(text: String): OutboxEntry {
        val j = JSONObject(text); require(j.getInt("v") == 1)
        val e = OutboxEntry(j.getString("id"), j.getString("login"), j.getString("credential_fingerprint"),
            j.getLong("created_at"), j.getString("payload"), j.getLong("owner_id"), j.getString("state"),
            j.getInt("attempts"), j.getLong("next_attempt_at"), j.getString("issue"),
            j.getString("last_attempt_fingerprint"), j.getString("receipt"))
        validate(e); return e
    }

    private fun validate(e: OutboxEntry) {
        name(e.id); require(e.login.isNotBlank() && e.login.length <= 100)
        require(e.credentialFingerprint.matches(Regex("[0-9a-f]{64}")))
        require(e.createdAt > 0 && e.ownerId >= 0 && e.attempts >= 0 && e.nextAttemptAt >= 0)
        require(e.state in setOf("pending", "accepted", "cancelled") && e.issue.length <= 100)
        require(e.lastAttemptFingerprint.isEmpty() || e.lastAttemptFingerprint.matches(Regex("[0-9a-f]{64}")))
        if (e.state == "pending") {
            val body = JSONObject(e.payload)
            require(body.keys().asSequence().all { it in ChatOutboxPolicy.PAYLOAD_FIELDS })
            require(body.getString("client_msg_id") == e.id)
        } else require(e.payload.isEmpty())
        if (e.state == "accepted") {
            val receipt = JSONObject(e.receipt)
            require(receipt.optLong("author_id") == e.ownerId && e.ownerId > 0 && receipt.optString("client_msg_id") == e.id)
            require(receipt.keys().asSequence().all { it in setOf("id", "author_id", "client_msg_id", "at", "github_login") })
        }
    }

    private fun read(file: String): OutboxEntry {
        val bytes = disk.read(file); require(bytes.size in 29..MAX_FILE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD("xydesk-outbox-v1:$file".toByteArray(Charsets.UTF_8))
        val entry = decode(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
        require(name(entry.id) == file); return entry
    }

    @Synchronized fun entries(): List<OutboxEntry> {
        val names = disk.names(); require(names.size <= MAX_RECORDS) { "Antrean lokal perlu pemeriksaan; tidak ada data yang dihapus." }
        return names.map { read(it) }.sortedBy { it.createdAt }
    }

    private fun write(e: OutboxEntry) {
        validate(e)
        val file = name(e.id)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(disk.names().isEmpty()))
        cipher.updateAAD("xydesk-outbox-v1:$file".toByteArray(Charsets.UTF_8))
        val bytes = cipher.iv + cipher.doFinal(encode(e).toByteArray(Charsets.UTF_8))
        require(bytes.size <= MAX_FILE_BYTES) { "Pesan terlalu besar untuk antrean lokal." }
        val oldSize = if (file in disk.names()) disk.read(file).size else 0
        require(disk.sizeBytes() - oldSize + bytes.size <= MAX_TOTAL_BYTES) { "Antrean lokal penuh; pesan lama tidak dihapus." }
        disk.writeAtomic(file, bytes)
        check(read(file) == e) { "Antrean belum tersimpan utuh." }
    }

    @Synchronized fun enqueue(e: OutboxEntry) {
        require(e.state == "pending"); val previous = entries()
        require(previous.none { it.id == e.id })
        require(previous.count { it.state == "pending" } < MAX_PENDING) { "Antrean penuh; pesan lama tetap disimpan." }
        // Reclaim only receipt/cancel metadata, never unsent content.
        if (previous.size >= MAX_RECORDS) previous.firstOrNull { it.state != "pending" }?.let { disk.delete(name(it.id)) }
        write(e)
    }

    @Synchronized fun update(id: String, transform: (OutboxEntry) -> OutboxEntry): OutboxEntry? {
        val file = name(id); if (file !in disk.names()) return null
        val before = read(file); val after = transform(before)
        require(after.id == before.id && after.createdAt == before.createdAt && after.credentialFingerprint == before.credentialFingerprint)
        require(after.ownerId == before.ownerId || before.ownerId == 0L && after.ownerId > 0)
        require(after.state != "pending" || after.payload == before.payload)
        require(before.state == "pending" || after == before) // terminal states never resurrect
        if (before != after) write(after)
        return after
    }
}
