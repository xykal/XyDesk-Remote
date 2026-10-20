package id.xydesk.remote.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ronde 55 — Custom Sticker Vault (.webp.crypto15) untuk Chat Global & Floating Session Chat:
 * - Mengonversi gambar pilihan pengguna menjadi stiker WebP (256x256) secara otomatis.
 * - Mengenkripsi payload WebP dengan AES-256-GCM (`crypto15` envelope: magic `"XYC15\u0001"` + IV 12B + Ciphertext).
 * - Menyimpan file stiker terenkripsi langsung di:
 *   `Android/media/id.xydesk.remote/globalchat/sticker/<Token>+2+$+$8.webp.crypto15`.
 * - Folder typo lama tetap dibaca, tetapi tidak dibuat atau dijadikan mirror lagi.
 */
internal data class CustomCryptoSticker(
    val fileName: String,
    val displayPath: String,
    val file: File,
    val crypto15Bytes: ByteArray,
    val bitmap: Bitmap,
)

internal object CustomStickerVault {
    private val MAGIC_HEADER = byteArrayOf(
        'X'.code.toByte(),
        'Y'.code.toByte(),
        'C'.code.toByte(),
        '1'.code.toByte(),
        '5'.code.toByte(),
        0x01,
    )
    private const val IV_SIZE = 12
    private const val GCM_TAG_BITS = 128
    private const val STICKER_DIM = 240
    private const val ALPHA_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    private val bitmapCache = object : android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
    }

    private val vaultKeySpec: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("XyDesk-GlobalChat-Sticker-Crypto15-v1|id.xydesk.remote".toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    @Suppress("DEPRECATION")
    private fun mediaRoots(context: Context): List<File> =
        runCatching { context.externalMediaDirs.filterNotNull() }.getOrDefault(emptyList())

    private fun readStickerDirs(context: Context): List<File> =
        StickerDirectoryPolicy.readDirectories(mediaRoots(context), context.filesDir)

    /** One successful write, never a mirror. Failure may fall back to internal storage. */
    @Synchronized
    private fun persistSticker(context: Context, fileName: String, bytes: ByteArray, overwrite: Boolean = false): File? {
        if (!StickerDirectoryPolicy.isSafeFileName(fileName) || bytes.size !in 1..2_100_000) return null
        return StickerDirectoryPolicy.writeFirstAvailable(mediaRoots(context), context.filesDir) { dir ->
            runCatching {
                check(dir.isDirectory || dir.mkdirs()) { "Sticker directory unavailable" }
                val target = File(dir, fileName)
                val atomic = AtomicFile(target)
                if (!overwrite && target.isFile) return@runCatching target
                val output = atomic.startWrite()
                try {
                    output.write(bytes)
                    atomic.finishWrite(output)
                } catch (error: Exception) {
                    runCatching { atomic.failWrite(output) }
                    throw error
                }
                val stored = atomic.openRead().use { it.readChatBytesAtMost(2_100_000) }
                check(stored.contentEquals(bytes)) { "Sticker write incomplete" }
                target
            }.getOrNull()
        }
    }

    /**
     * Menghasilkan nama file deterministik bergaya:
     * `Jwkwhdixhdbehejeudjdo+2+$+$8.webp.crypto15`
     */
    fun buildCrypto15FileName(webpBytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(webpBytes)
        val sb = StringBuilder(36)
        // Huruf pertama kapital seperti contoh "Jwkwhdixhdbehejeudjdo+2+$+$8.webp.crypto15"
        val firstUpper = 'A' + ((hash[0].toInt() and 0xFF) % 26)
        sb.append(firstUpper)
        for (i in 1..20) {
            val b = hash[i].toInt() and 0xFF
            val ch = if (i % 5 == 0) {
                ALPHA_POOL[b % ALPHA_POOL.length]
            } else {
                'a' + (b % 26)
            }
            sb.append(ch)
        }
        val d1 = (hash[21].toInt() and 0xFF) % 10
        val d2 = (hash[22].toInt() and 0xFF) % 10
        sb.append('+').append(d1).append("+$+$").append(d2).append(".webp.crypto15")
        return sb.toString()
    }

    fun encryptWebpToCrypto15(webpBytes: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, vaultKeySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(webpBytes)
        val out = ByteArray(MAGIC_HEADER.size + IV_SIZE + encrypted.size)
        System.arraycopy(MAGIC_HEADER, 0, out, 0, MAGIC_HEADER.size)
        System.arraycopy(iv, 0, out, MAGIC_HEADER.size, IV_SIZE)
        System.arraycopy(encrypted, 0, out, MAGIC_HEADER.size + IV_SIZE, encrypted.size)
        return out
    }

    fun decryptCrypto15ToWebp(crypto15Bytes: ByteArray): ByteArray? {
        if (crypto15Bytes.size <= MAGIC_HEADER.size + IV_SIZE + 16) return null
        for (i in MAGIC_HEADER.indices) {
            if (crypto15Bytes[i] != MAGIC_HEADER[i]) return null
        }
        return runCatching {
            val iv = crypto15Bytes.copyOfRange(MAGIC_HEADER.size, MAGIC_HEADER.size + IV_SIZE)
            val cipherPayload = crypto15Bytes.copyOfRange(MAGIC_HEADER.size + IV_SIZE, crypto15Bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, vaultKeySpec, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherPayload)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun compressBitmapToWebp(src: Bitmap): ByteArray {
        // Pertahankan shape & rasio asli gambar stiker tanpa rounded mask / tanpa crop kotak
        val srcW = src.width.coerceAtLeast(1)
        val srcH = src.height.coerceAtLeast(1)
        val maxSide = maxOf(srcW, srcH)
        val scale = if (maxSide > STICKER_DIM) STICKER_DIM.toFloat() / maxSide.toFloat() else 1f
        val dstW = (srcW * scale).toInt().coerceAtLeast(1)
        val dstH = (srcH * scale).toInt().coerceAtLeast(1)

        val outBmp = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val srcRect = Rect(0, 0, srcW, srcH)
        val dstRect = RectF(0f, 0f, dstW.toFloat(), dstH.toFloat())
        canvas.drawBitmap(src, srcRect, dstRect, paint)

        val baos = ByteArrayOutputStream()
        val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        outBmp.compress(fmt, 82, baos)
        return baos.toByteArray()
    }

    /**
     * Membuat stiker `.webp.crypto15` dari Uri galeri/file dan menyimpannya ke
     * `Android/media/id.xydesk.remote/globalchat/sticker/<Token>.webp.crypto15`.
     */
    fun createStickerFromUri(context: Context, uri: Uri): CustomCryptoSticker? {
        return runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
            val webpBytes = compressBitmapToWebp(decoded)
            val fileName = buildCrypto15FileName(webpBytes)
            val crypto15Bytes = encryptWebpToCrypto15(webpBytes)
            val finalFile = persistSticker(context, fileName, crypto15Bytes, overwrite = true) ?: return null
            val stickerBmp = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size) ?: decoded
            bitmapCache.put(fileName, stickerBmp)
            CustomCryptoSticker(
                fileName = fileName,
                displayPath = finalFile.absolutePath,
                file = finalFile,
                crypto15Bytes = crypto15Bytes,
                bitmap = stickerBmp,
            )
        }.getOrNull()
    }

    /**
     * Memuat semua stiker `.webp.crypto15` yang tersimpan di perangkat.
     */
    fun listCustomStickers(context: Context): List<CustomCryptoSticker> {
        val dirs = readStickerDirs(context)
        val seen = LinkedHashMap<String, CustomCryptoSticker>()
        for (dir in dirs) {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".webp.crypto15", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: continue
            for (f in files) {
                if (seen.containsKey(f.name)) continue
                if (f.length() !in 1L..2_100_000L) continue
                val cryptoBytes = runCatching { f.inputStream().use { it.readChatBytesAtMost(2_100_000) } }.getOrNull() ?: continue
                val webpBytes = decryptCrypto15ToWebp(cryptoBytes) ?: continue
                val bmp = bitmapCache.get(f.name) ?: decodeBoundedChatBitmap(webpBytes, STICKER_DIM)?.also {
                    bitmapCache.put(f.name, it)
                } ?: continue
                seen[f.name] = CustomCryptoSticker(
                    fileName = f.name,
                    displayPath = f.absolutePath,
                    file = f,
                    crypto15Bytes = cryptoBytes,
                    bitmap = bmp,
                )
            }
        }
        return seen.values.toList()
    }

    fun deleteCustomSticker(context: Context, fileName: String) {
        if (!StickerDirectoryPolicy.isSafeFileName(fileName)) return
        bitmapCache.remove(fileName)
        for (dir in readStickerDirs(context)) {
            runCatching { File(dir, fileName).delete() }
        }
    }

    fun encodeAttachmentPayload(sticker: CustomCryptoSticker): String {
        val b64 = Base64.encodeToString(sticker.crypto15Bytes, Base64.NO_WRAP)
        return "sticker_crypto15:${sticker.fileName}:$b64"
    }

    /**
     * Mendekode payload `"sticker_crypto15:<fileName>:<b64>"` dari pesan Chat Global
     * dan otomatis menyimpannya sebagai `.webp.crypto15` di folder
     * `Android/media/id.xydesk.remote/globalchat/sticker/<fileName>`.
     */
    fun decodeAndCacheFromChatPayload(context: Context, attachmentData: String): Pair<String, Bitmap>? {
        if (attachmentData.length > 2_800_000 || !attachmentData.startsWith("sticker_crypto15:")) return null
        val rest = attachmentData.removePrefix("sticker_crypto15:")
        val sep = rest.indexOf(':')
        if (sep <= 0) return null
        val rawFileName = rest.substring(0, sep).trim()
            .replace("/", "")
            .replace("\\", "")
            .ifBlank { "CustomSticker+1+$+$9.webp.crypto15" }
        val fileName = if (rawFileName.endsWith(".webp.crypto15")) rawFileName else "$rawFileName.webp.crypto15"
        val b64 = rest.substring(sep + 1).trim()
        val cryptoBytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
        val payloadKey = "payload:" + MessageDigest.getInstance("SHA-256").digest(cryptoBytes)
            .joinToString("") { "%02x".format(it) }
        bitmapCache.get(payloadKey)?.let { return fileName to it }
        val webpBytes = decryptCrypto15ToWebp(cryptoBytes) ?: return null
        val bmp = decodeBoundedChatBitmap(webpBytes, STICKER_DIM) ?: return null
        bitmapCache.put(payloadKey, bmp)

        // Save once to the canonical folder, never mirror into the typo directory.
        persistSticker(context, fileName, cryptoBytes)
        return fileName to bmp
    }

    private const val PREF_STICKER_FAVS = "xydesk_sticker_favorites_v2"
    private const val KEY_FAV_SET = "fav_keys"

    fun loadFavoriteKeys(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_STICKER_FAVS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAV_SET, emptySet())?.toSet() ?: emptySet()
    }

    fun isFavorite(context: Context, key: String): Boolean {
        return key.trim() in loadFavoriteKeys(context)
    }

    fun toggleFavoriteKey(context: Context, key: String): Set<String> {
        val clean = key.trim()
        val prefs = context.getSharedPreferences(PREF_STICKER_FAVS, Context.MODE_PRIVATE)
        val next = loadFavoriteKeys(context).toMutableSet()
        if (clean in next) next.remove(clean) else next.add(clean)
        prefs.edit().putStringSet(KEY_FAV_SET, next).apply()
        return next
    }

    fun addFavoriteFromChatSticker(context: Context, attachmentData: String): Set<String> {
        if (attachmentData.startsWith("sticker_crypto15:")) {
            val decoded = decodeAndCacheFromChatPayload(context, attachmentData)
            if (decoded != null) {
                return toggleFavoriteIfNotPresent(context, "custom:${decoded.first}")
            }
        } else if (attachmentData.startsWith("sticker:")) {
            val id = attachmentData.removePrefix("sticker:").trim()
            return toggleFavoriteIfNotPresent(context, "preset:$id")
        }
        return loadFavoriteKeys(context)
    }

    private fun toggleFavoriteIfNotPresent(context: Context, key: String): Set<String> {
        val clean = key.trim()
        val prefs = context.getSharedPreferences(PREF_STICKER_FAVS, Context.MODE_PRIVATE)
        val next = loadFavoriteKeys(context).toMutableSet()
        if (clean in next) next.remove(clean) else next.add(clean)
        prefs.edit().putStringSet(KEY_FAV_SET, next).apply()
        return next
    }
}
