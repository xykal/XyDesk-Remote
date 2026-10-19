package id.xydesk.remote.ui

import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable authenticated file encryption, not chat E2EE or sender authentication.
 * Fixed bounded format: magic[8], random salt[16], GCM IV[12], ciphertext+tag.
 * PBKDF2-HMAC-SHA1 is available on API 23; 1.4M iterations, 256-bit output.
 * No untrusted iteration/length fields, compression, or remote URLs to follow.
 */
internal object ChatBackupCrypto {
    const val MAX_PLAINTEXT = 1_000_000
    const val MAX_FILE = MAX_PLAINTEXT + 52
    private val magic = "XYCBK001".toByteArray(UTF_8)
    private const val ITERATIONS = 1_400_000

    fun encrypt(plain: ByteArray, password: CharArray, login: String): ByteArray {
        require(plain.size <= MAX_PLAINTEXT) { "Arsip terlalu besar." }
        require(password.size >= 12) { "Gunakan passphrase minimal 12 karakter." }
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        return magic + salt + iv + crypt(Cipher.ENCRYPT_MODE, plain, password, login, salt, iv)
    }

    fun decrypt(file: ByteArray, password: CharArray, login: String): ByteArray {
        require(file.size in 52..MAX_FILE && file.copyOfRange(0, 8).contentEquals(magic)) { "Format/ukuran backup tidak didukung." }
        return crypt(Cipher.DECRYPT_MODE, file.copyOfRange(36, file.size), password, login,
            file.copyOfRange(8, 24), file.copyOfRange(24, 36))
    }

    private fun crypt(mode: Int, bytes: ByteArray, password: CharArray, login: String, salt: ByteArray, iv: ByteArray): ByteArray {
        val account = login.trim().removePrefix("@").lowercase(Locale.ROOT)
        require(account.isNotBlank()) { "Login GitHub terverifikasi diperlukan." }
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val key = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(magic + account.toByteArray(UTF_8))
            cipher.doFinal(bytes)
        } finally { key.fill(0) }
    }
}
