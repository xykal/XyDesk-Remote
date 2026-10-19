package id.xydesk.remote.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Bounded, account-scoped encrypted text snapshots. No view-once or media payloads.
 * noBackupFilesDir prevents restoration without the device Keystore key.
 * Local encryption is not sender authentication or end-to-end encryption.
 */
internal object GlobalChatOfflineCache {
    private const val ALIAS = "xydesk.chat.offline.v1"
    private const val MAX_BYTES = 1_000_000
    private val writeOrder = ChatCacheWriteOrder()
    private val writer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    fun nextWriteEpoch(login: String): Long = writeOrder.advance(account(login))

    fun persistConfirmed(context: Context, login: String, messages: List<ChatMessage>, epoch: Long) {
        val app = context.applicationContext
        writer.launch { write(app, login, messages, epoch) }
    }

    // Imported history is never inserted into the live timeline or sent to the server.
    fun readArchive(context: Context, login: String): List<ChatMessage> =
        read(context, "archive:$login", "").map { it.copy(mine = false, authorId = 0, e2eeVerified = false) }

    @Synchronized
    fun deleteArchive(context: Context, login: String) {
        require(login.isNotBlank())
        val isolated = "archive:$login"
        nextWriteEpoch(isolated)
        val cache = file(context, isolated)
        cache.delete()
        check(!cache.baseFile.exists()) { "Arsip lokal gagal dihapus." }
    }

    fun storeArchive(context: Context, login: String, rows: List<ChatMessage>) {
        require(login.isNotBlank())
        val isolated = "archive:$login"
        write(context, isolated, rows, nextWriteEpoch(isolated))
        // write() historically swallows storage failures: verify before reporting success.
        val stored = readArchive(context, login).associateBy { it.id }
        check(rows.all { stored[it.id]?.let { saved -> saved.revision > it.revision || saved.revision == it.revision && saved.text == it.text && saved.deleted == it.deleted } == true }) {
            "Arsip belum tersimpan. Periksa ruang penyimpanan lalu coba lagi."
        }
    }

    private fun account(login: String) = login.trim().removePrefix("@").lowercase(Locale.ROOT)
    private fun file(context: Context, login: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(account(login).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(context.noBackupFilesDir, "chat-$hash.cache"))
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized
    fun read(context: Context, login: String, nickname: String): List<ChatMessage> {
        if (account(login).isBlank()) return emptyList()
        return runCatching {
            val cache = file(context, login)
            if (cache.baseFile.length() !in 29L..MAX_BYTES.toLong()) return emptyList()
            val bytes = cache.readFully()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(account(login).toByteArray(Charsets.UTF_8))
            val rows = JSONArray(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            buildList {
                for (i in 0 until minOf(rows.length(), 80)) {
                    val row = rows.optJSONObject(i) ?: continue
                    // Verification defaults false: local cache is not a new server signature.
                    add(parseMessageFromJson(row, nickname, login))
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun write(context: Context, login: String, messages: List<ChatMessage>, epoch: Long) {
        if (account(login).isBlank() || !writeOrder.isCurrent(account(login), epoch)) return
        runCatching {
            // Multiple windows must reconcile revisions before writing an account snapshot.
            val previous = read(context, login, login).associateBy { it.id }
            val reconciled = messages.map { incoming -> previous[incoming.id]?.let { reconcileChatMessage(it, incoming) } ?: incoming }
            val plain = encodeChatArchive(reconciled)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(account(login).toByteArray(Charsets.UTF_8))
            val encrypted = cipher.iv + cipher.doFinal(plain)
            if (encrypted.size > MAX_BYTES) return
            if (!writeOrder.isCurrent(account(login), epoch)) return
            val cache = file(context, login)
            val stream = cache.startWrite()
            try { stream.write(encrypted); cache.finishWrite(stream) }
            catch (error: Exception) { cache.failWrite(stream); throw error }
        }
    }
}
