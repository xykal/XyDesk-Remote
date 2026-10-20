package id.xydesk.remote.ui

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal class AndroidChatOutboxDisk(context: Context) : OutboxDisk {
    private val root = File(context.noBackupFilesDir, "chat-outbox-v1")
    private fun atomic(name: String): AtomicFile {
        require(name.endsWith(".queue") && ChatOutboxPolicy.LOCAL_ID.matches(name.removeSuffix(".queue")))
        return AtomicFile(File(root, name))
    }
    override fun names(): List<String> = root.listFiles().orEmpty().filter { it.isFile }
        .map { it.name.removeSuffix(".bak") }.filter { it.endsWith(".queue") }.distinct()
    override fun read(name: String): ByteArray = atomic(name).openRead().use { it.readChatBytesAtMost(ChatOutboxStore.MAX_FILE_BYTES) }
    override fun writeAtomic(name: String, bytes: ByteArray) {
        check(root.isDirectory || root.mkdirs()) { "Penyimpanan antrean tidak tersedia." }
        val file = atomic(name); val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
    }
    override fun delete(name: String) = atomic(name).delete()
    override fun sizeBytes(): Long = root.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }
}

internal fun chatOutboxKey(create: Boolean): SecretKey {
    val alias = "xydesk.chat.outbox.v1"
    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (store.getKey(alias, null) as? SecretKey)?.let { return it }
    check(create) { "Kunci antrean tidak tersedia; data lama tidak direset." }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
        init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
    }.generateKey()
}
