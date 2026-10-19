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
    fun write(context: Context, login: String, messages: List<ChatMessage>) {
        if (account(login).isBlank()) return
        runCatching {
            val rows = JSONArray()
            messages.filter { !it.viewOnce && !it.mutationPending && !it.id.startsWith("local-") }.takeLast(80).forEach { msg ->
                rows.put(JSONObject().apply {
                    put("id", msg.id); put("client_msg_id", msg.clientMessageId); put("name", msg.name); put("github_login", msg.githubLogin)
                    put("author_id", msg.authorId); put("revision", msg.revision); put("edited_at", msg.editedAt)
                    put("deleted", msg.deleted); put("deleted_at", msg.deletedAt)
                    put("text", msg.text.take(4_000)); put("at", msg.at); put("country", msg.country)
                    put("translated", msg.translated.take(4_000)); put("target_lang", msg.targetLang)
                    put("source_lang", msg.sourceLang); put("is_founder", msg.isFounder)
                    put("is_announcement", msg.isAnnouncement); put("reply_to_id", msg.replyToId)
                    put("reply_to_name", msg.replyToName); put("reply_to_text", msg.replyToText.take(500))
                    put("attachment_type", msg.attachmentType); put("attachment_name", msg.attachmentName.take(256))
                    put("attachment_mime", msg.attachmentMime); put("attachment_size", msg.attachmentSize)
                    put("avatar_url", msg.avatarUrl)
                })
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(account(login).toByteArray(Charsets.UTF_8))
            val encrypted = cipher.iv + cipher.doFinal(rows.toString().toByteArray(Charsets.UTF_8))
            if (encrypted.size > MAX_BYTES) return
            val cache = file(context, login)
            val stream = cache.startWrite()
            try { stream.write(encrypted); cache.finishWrite(stream) }
            catch (error: Exception) { cache.failWrite(stream); throw error }
        }
    }
}
