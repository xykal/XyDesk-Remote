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
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Ronde 55 — Custom Sticker Vault (.webp.crypto15) untuk Chat Global & Floating Session Chat:
 * - Mengonversi gambar pilihan pengguna menjadi stiker WebP (256x256) secara otomatis.
 * - Mengenkripsi payload WebP dengan AES-256-GCM (`crypto15` envelope: magic `"XYC15\u0001"` + IV 12B + Ciphertext).
 * - Menyimpan file stiker terenkripsi langsung di:
 *   `Android/media/id.xydesk.remote/globwlchat/sticker/<Token>+2+$+$8.webp.crypto15`
 *   (dan mirror di `Android/media/id.xydesk.remote/globalchat/sticker/...`).
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

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    private val vaultKeySpec: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("XyDesk-GlobalChat-Sticker-Crypto15-v1|id.xydesk.remote".toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    /**
     * Direktori utama `Android/media/id.xydesk.remote/globwlchat/sticker`
     * beserta mirror `Android/media/id.xydesk.remote/globalchat/sticker`.
     */
    @Suppress("DEPRECATION")
    private fun resolveStickerDirs(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        val extRoot = runCatching {
            context.externalMediaDirs?.firstOrNull { it != null }
        }.getOrNull()

        if (extRoot != null) {
            val dirGlobwl = File(extRoot, "globwlchat/sticker")
            val dirGlobal = File(extRoot, "globalchat/sticker")
            runCatching { dirGlobwl.mkdirs() }
            runCatching { dirGlobal.mkdirs() }
            if (dirGlobwl.exists()) dirs.add(dirGlobwl)
            if (dirGlobal.exists()) dirs.add(dirGlobal)
        }

        val fallback = File(context.filesDir, "globwlchat/sticker")
        runCatching { fallback.mkdirs() }
        if (dirs.isEmpty()) {
            dirs.add(fallback)
        }
        return dirs
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
        // Center-crop & scale ke ukuran stiker 240x240 dengan sudut membulat halus bila gambar penuh
        val square = Bitmap.createBitmap(STICKER_DIM, STICKER_DIM, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(square)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val minSide = minOf(src.width, src.height).coerceAtLeast(1)
        val cropX = (src.width - minSide) / 2
        val cropY = (src.height - minSide) / 2
        val srcRect = Rect(cropX, cropY, cropX + minSide, cropY + minSide)
        val dstRect = RectF(0f, 0f, STICKER_DIM.toFloat(), STICKER_DIM.toFloat())

        if (!src.hasAlpha()) {
            canvas.drawRoundRect(dstRect, 36f, 36f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(src, srcRect, dstRect, paint)

        val baos = ByteArrayOutputStream()
        val fmt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        square.compress(fmt, 78, baos)
        return baos.toByteArray()
    }

    /**
     * Membuat stiker `.webp.crypto15` dari Uri galeri/file dan menyimpannya ke
     * `Android/media/id.xydesk.remote/globwlchat/sticker/<Token>.webp.crypto15`.
     */
    fun createStickerFromUri(context: Context, uri: Uri): CustomCryptoSticker? {
        return runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null
            val webpBytes = compressBitmapToWebp(decoded)
            val fileName = buildCrypto15FileName(webpBytes)
            val crypto15Bytes = encryptWebpToCrypto15(webpBytes)
            val dirs = resolveStickerDirs(context)
            var primaryFile: File? = null
            for (dir in dirs) {
                val target = File(dir, fileName)
                runCatching { target.writeBytes(crypto15Bytes) }
                if (primaryFile == null) primaryFile = target
            }
            val finalFile = primaryFile ?: return null
            val stickerBmp = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size) ?: decoded
            bitmapCache[fileName] = stickerBmp
            CustomCryptoSticker(
                fileName = fileName,
                displayPath = "Android/media/id.xydesk.remote/globwlchat/sticker/$fileName",
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
        val dirs = resolveStickerDirs(context)
        val seen = LinkedHashMap<String, CustomCryptoSticker>()
        for (dir in dirs) {
            val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".webp.crypto15", ignoreCase = true) }
                ?.sortedByDescending { it.lastModified() }
                ?: continue
            for (f in files) {
                if (seen.containsKey(f.name)) continue
                val cryptoBytes = runCatching { f.readBytes() }.getOrNull() ?: continue
                val webpBytes = decryptCrypto15ToWebp(cryptoBytes) ?: continue
                val bmp = bitmapCache[f.name] ?: BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size)?.also {
                    bitmapCache[f.name] = it
                } ?: continue
                seen[f.name] = CustomCryptoSticker(
                    fileName = f.name,
                    displayPath = "Android/media/id.xydesk.remote/globwlchat/sticker/${f.name}",
                    file = f,
                    crypto15Bytes = cryptoBytes,
                    bitmap = bmp,
                )
            }
        }
        return seen.values.toList()
    }

    fun deleteCustomSticker(context: Context, fileName: String) {
        bitmapCache.remove(fileName)
        for (dir in resolveStickerDirs(context)) {
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
     * `Android/media/id.xydesk.remote/globwlchat/sticker/<fileName>`.
     */
    fun decodeAndCacheFromChatPayload(context: Context, attachmentData: String): Pair<String, Bitmap>? {
        if (!attachmentData.startsWith("sticker_crypto15:")) return null
        val rest = attachmentData.removePrefix("sticker_crypto15:")
        val sep = rest.indexOf(':')
        if (sep <= 0) return null
        val rawFileName = rest.substring(0, sep).trim()
            .replace("/", "")
            .replace("\\", "")
            .ifBlank { "CustomSticker+1+$+$9.webp.crypto15" }
        val fileName = if (rawFileName.endsWith(".webp.crypto15")) rawFileName else "$rawFileName.webp.crypto15"
        bitmapCache[fileName]?.let { return fileName to it }

        val b64 = rest.substring(sep + 1).trim()
        val cryptoBytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return null
        val webpBytes = decryptCrypto15ToWebp(cryptoBytes) ?: return null
        val bmp = BitmapFactory.decodeByteArray(webpBytes, 0, webpBytes.size) ?: return null
        bitmapCache[fileName] = bmp

        // Simpan otomatis ke Android/media/id.xydesk.remote/globwlchat/sticker/<fileName>
        runCatching {
            for (dir in resolveStickerDirs(context)) {
                val f = File(dir, fileName)
                if (!f.exists()) {
                    f.writeBytes(cryptoBytes)
                }
            }
        }
        return fileName to bmp
    }
}
