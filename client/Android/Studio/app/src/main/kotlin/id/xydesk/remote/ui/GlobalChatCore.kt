/*
 * XyDesk Remote — Chat Global: model data, tabel bahasa/negara, preset stiker,
 * cache memori, dan proteksi transit pesan.
 *
 * Hasil ekstraksi dari GlobalChatScreen.kt (fase 1.5 — pecah god file 6.100+
 * baris). Pemindahan 1:1 TANPA perubahan logika; hanya visibilitas private ->
 * internal karena pemakai (GlobalChatScreen.kt dkk) ada di file lain dalam
 * satu package. Langkah ekstraksi berikutnya: repository network/WS, lalu
 * komponen UI (bubble, composer, media viewer).
 */
package id.xydesk.remote.ui

import android.graphics.Bitmap
import android.util.Base64
import androidx.annotation.DrawableRes
import id.xydesk.remote.R
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class PinnedChatMessage(
    val id: String,
    val name: String,
    val githubLogin: String = "",
    val text: String,
    val pinnedBy: String = "",
    val at: Long = 0L,
)

internal data class ChatMessage(
    val id: String,
    val name: String,
    val githubLogin: String = "",
    val country: String,
    val flag: String,
    val text: String,
    val translated: String,
    val targetLang: String,
    val sourceLang: String = "",
    val at: Long,
    val mine: Boolean,
    val isFounder: Boolean = isFounderAccount(name) || isFounderAccount(githubLogin),
    val isAnnouncement: Boolean = false,
    val replyToId: String = "",
    val replyToName: String = "",
    val replyToText: String = "",
    val attachmentType: String = "", // "", "image", "video", "file", "sticker"
    val attachmentName: String = "",
    val attachmentMime: String = "",
    val attachmentSize: Int = 0,
    val attachmentData: String = "",
    val viewOnce: Boolean = false,
    val avatarUrl: String = "",
    val deliveryTicks: Int = 2, // -1 = unconfirmed, 0 = pending, 1 = legacy queued, 2 = room acknowledged (not read)
    val e2eeProtocol: String = "XY-E2EE-GCM-v1",
    val e2eeSig: String = "",
    // Default TIDAK terverifikasi; hanya HMAC valid dari server yang menyalakan ini.
    val e2eeVerified: Boolean = false,
)

internal data class XyStickerPreset(
    val id: String,
    @DrawableRes val drawableRes: Int,
    val title: String,
    val subtitle: String,
    val pack: String = "Matte",
)

internal val XY_STICKERS = listOf(
    // Seri Matte Flat Expressive (No-Neon, Die-Cut Transparan)
    XyStickerPreset("matte_cat_wave", R.drawable.sticker_matte_cat_wave, "Halo Santai", "Mochi Cat Wave", "Matte"),
    XyStickerPreset("matte_duck_ok", R.drawable.sticker_matte_duck_ok, "Siap OK!", "Cozy Duck Thumbs Up", "Matte"),
    XyStickerPreset("matte_shiba_love", R.drawable.sticker_matte_shiba_love, "Mantap Jiwa", "Shiba Heart Love", "Matte"),
    XyStickerPreset("matte_bear_sleep", R.drawable.sticker_matte_bear_sleep, "Ngantuk Berat", "Sleepy Bear Zzz", "Matte"),
    XyStickerPreset("matte_panda_shock", R.drawable.sticker_matte_panda_shock, "Waduh Kaget!", "Shocked Panda", "Matte"),
    XyStickerPreset("matte_bunny_hype", R.drawable.sticker_matte_bunny_hype, "Cool Abis", "Sunglasses Bunny", "Matte"),
    // Seri XyCyber Mascot
    XyStickerPreset("xy_gg", R.drawable.sticker_xy_gg, "GGWP Mantap!", "Thumbs Up Mascot", "XyCyber"),
    XyStickerPreset("xy_laugh", R.drawable.sticker_xy_laugh, "Ngakak Parah", "Laughing Mascot", "XyCyber"),
    XyStickerPreset("xy_fire", R.drawable.sticker_xy_fire, "Gaskeun 120 FPS!", "On Fire Boost", "XyCyber"),
    XyStickerPreset("xy_chill", R.drawable.sticker_xy_chill, "Santai Ngopi", "Chill Session", "XyCyber"),
    XyStickerPreset("xy_sad", R.drawable.sticker_xy_sad, "Ping 999ms…", "Connection Drop", "XyCyber"),
    XyStickerPreset("xy_king", R.drawable.sticker_xy_king, "Sovereign Boss", "Royal Crown Salute", "XyCyber"),
)

internal object GlobalChatMemoryCache {
    val messages = ArrayList<ChatMessage>()
    var pinnedMessage: PinnedChatMessage? = null
}

internal data class LangOption(
    val code: String,
    val label: String,
    val badge: String,
)

internal val SUPPORTED_VIEWER_LANGS = listOf(
    LangOption("auto", "Otomatis Sesuai Region Negara Penonton (All Negara)", "AUTO"),
    LangOption("id", "Bahasa Indonesia", "ID"),
    LangOption("en", "English (Inggris)", "EN"),
    LangOption("ms", "Bahasa Melayu (Malaysia / Brunei)", "MS"),
    LangOption("th", "ภาษาไทย (Thailand)", "TH"),
    LangOption("ja", "日本語 (Jepang)", "JA"),
    LangOption("ko", "한국어 (Korea)", "KO"),
    LangOption("zh-CN", "中文简体 (Mandarin / Tiongkok)", "ZH"),
    LangOption("zh-TW", "中文繁體 (Taiwan / Hong Kong)", "TW"),
    LangOption("vi", "Tiếng Việt (Vietnam)", "VI"),
    LangOption("tl", "Filipino / Tagalog (Filipina)", "TL"),
    LangOption("ar", "العربية (Timur Tengah / Arab)", "AR"),
    LangOption("ru", "Русский (Rusia / CIS)", "RU"),
    LangOption("hi", "हिन्दी (India)", "HI"),
    LangOption("ur", "اردو (Pakistan)", "UR"),
    LangOption("bn", "বাংলা (Bangladesh)", "BN"),
    LangOption("es", "Español (Spanyol / Amerika Latin)", "ES"),
    LangOption("pt", "Português (Brasil / Portugal)", "PT"),
    LangOption("de", "Deutsch (Jerman / Austria / Swiss)", "DE"),
    LangOption("fr", "Français (Prancis / Kanada / Afrika)", "FR"),
    LangOption("it", "Italiano (Italia)", "IT"),
    LangOption("nl", "Nederlands (Belanda / Belgia)", "NL"),
    LangOption("tr", "Türkçe (Turki)", "TR"),
    LangOption("pl", "Polski (Polandia)", "PL"),
    LangOption("uk", "Українська (Ukraina)", "UK"),
)

internal val COUNTRY_TO_PRIMARY_LANG = mapOf(
    // Asia Tenggara & Pasifik
    "ID" to "id", "TL" to "pt",
    "MY" to "ms", "BN" to "ms",
    "SG" to "en", "PH" to "tl",
    "TH" to "th", "VN" to "vi",
    "KH" to "km", "LA" to "lo", "MM" to "my",
    "AU" to "en", "NZ" to "en", "FJ" to "en", "PG" to "en",
    // Asia Timur
    "JP" to "ja", "KR" to "ko", "KP" to "ko",
    "CN" to "zh-CN", "TW" to "zh-TW", "HK" to "zh-TW", "MO" to "zh-TW", "MN" to "mn",
    // Asia Selatan & Tengah
    "IN" to "hi", "PK" to "ur", "BD" to "bn", "LK" to "si", "NP" to "ne", "MV" to "en",
    "KZ" to "ru", "UZ" to "uz", "KG" to "ru", "TJ" to "tg", "TM" to "tk",
    // Timur Tengah & Afrika Utara
    "SA" to "ar", "AE" to "ar", "QA" to "ar", "KW" to "ar", "BH" to "ar", "OM" to "ar",
    "YE" to "ar", "IQ" to "ar", "SY" to "ar", "JO" to "ar", "LB" to "ar", "PS" to "ar",
    "EG" to "ar", "LY" to "ar", "TN" to "ar", "DZ" to "ar", "MA" to "ar", "SD" to "ar",
    "IR" to "fa", "AF" to "fa", "IL" to "iw", "TR" to "tr", "AZ" to "az", "GE" to "ka", "AM" to "hy",
    // Eropa
    "GB" to "en", "IE" to "en", "MT" to "en",
    "DE" to "de", "AT" to "de", "CH" to "de", "LI" to "de", "LU" to "fr",
    "FR" to "fr", "MC" to "fr", "BE" to "nl", "NL" to "nl",
    "ES" to "es", "AD" to "ca", "PT" to "pt",
    "IT" to "it", "SM" to "it", "VA" to "it",
    "RU" to "ru", "BY" to "ru", "UA" to "uk", "MD" to "ro", "RO" to "ro",
    "PL" to "pl", "CZ" to "cs", "SK" to "sk", "HU" to "hu", "BG" to "bg",
    "RS" to "sr", "HR" to "hr", "BA" to "bs", "SI" to "sl", "ME" to "sr", "MK" to "mk", "AL" to "sq",
    "GR" to "el", "CY" to "el",
    "SE" to "sv", "NO" to "no", "DK" to "da", "FI" to "fi", "IS" to "is",
    "EE" to "et", "LV" to "lv", "LT" to "lt",
    // Amerika Utara & Latin
    "US" to "en", "CA" to "en", "JM" to "en", "TT" to "en", "BS" to "en", "BB" to "en", "BZ" to "en", "GY" to "en",
    "MX" to "es", "GT" to "es", "SV" to "es", "HN" to "es", "NI" to "es", "CR" to "es", "PA" to "es",
    "CU" to "es", "DO" to "es", "PR" to "es",
    "CO" to "es", "VE" to "es", "EC" to "es", "PE" to "es", "BO" to "es", "PY" to "es", "CL" to "es", "AR" to "es", "UY" to "es",
    "BR" to "pt", "SR" to "nl", "HT" to "fr",
    // Afrika Sub-Sahara
    "ZA" to "en", "NG" to "en", "KE" to "sw", "TZ" to "sw", "UG" to "en", "GH" to "en",
    "ET" to "am", "SO" to "so", "SN" to "fr", "CI" to "fr", "CM" to "fr", "CD" to "fr",
    "AO" to "pt", "MZ" to "pt", "ZW" to "en", "ZM" to "en", "RW" to "en",
)

internal fun resolveRegionPrimaryLanguage(countryCode: String): String {
    val cc = countryCode.trim().uppercase(Locale.ROOT)
    COUNTRY_TO_PRIMARY_LANG[cc]?.let { return it }
    val devLang = Locale.getDefault().language.trim().lowercase(Locale.ROOT)
    return when {
        devLang == "in" || devLang == "id" -> "id"
        devLang == "zh" -> if (cc in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
        devLang.isNotBlank() -> devLang
        else -> "en"
    }
}

internal fun normalizeLangFamily(code: String): String {
    val c = code.trim().lowercase(Locale.ROOT)
    return when {
        c == "in" || c == "id" || c == "jv" || c == "su" -> "id"
        c == "fil" || c == "tl" -> "tl"
        c == "he" || c == "iw" -> "iw"
        c.startsWith("zh-tw") || c.startsWith("zh-hk") -> "zh-tw"
        c.startsWith("zh") -> "zh-cn"
        c.contains("-") -> c.substringBefore("-")
        else -> c
    }
}

internal val CLIENT_LANG_TO_COUNTRY = mapOf(
    "id" to "ID", "in" to "ID", "jv" to "ID", "su" to "ID",
    "ms" to "MY",
    "th" to "TH",
    "ja" to "JP",
    "ko" to "KR",
    "vi" to "VN",
    "tl" to "PH", "fil" to "PH",
    "zh" to "CN", "zh-cn" to "CN", "zh-tw" to "TW",
    "ru" to "RU",
    "uk" to "UA",
    "ar" to "SA",
    "hi" to "IN",
    "tr" to "TR",
    "de" to "DE",
    "fr" to "FR",
    "es" to "ES",
    "pt" to "BR",
    "it" to "IT",
    "nl" to "NL",
)

internal val CLIENT_ID_WORD_REGEX = Regex(
    "\\b(halo|hai|apa|gimana|bagaimana|bisa|bang|bre|bro|kak|gan|aku|kamu|saya|kita|yang|ini|itu|dari|buat|untuk|udah|sudah|belum|tidak|tak|gak|nggak|konek|koneksi|langsung|terima|kasih|makasih|mantap|keren|tolong|cara|pakai|pake|kenapa|dimana|kapan|siapa|boleh|dong|aja|saja|kok|nih|tuh|lagi|sama|kalau|kalo|bukan|dengan|di|ke|jir|anjir|wkwk|wkwkwk|gasken|gas|sip|oke|mantul)\\b",
    RegexOption.IGNORE_CASE,
)

/**
 * Proteksi pesan Chat Global (`XY-E2EE-GCM-v1`): AES-256-GCM + HMAC-SHA256.
 *
 * JUJUR SOAL MODEL ANCAMAN: kunci room diturunkan dari [ROOM_SEED] yang
 * ter-compile ke APK publik, dan server juga menyimpan plaintext di field
 * `text`. Jadi ini obfuscation + cek integritas transit — BUKAN end-to-end
 * encryption sejati. Jangan pernah mengklaim "E2EE" ke user atas modul ini.
 * TODO(fase-1.5): ganti ke sender-key nyata (X25519 per-member, ratchet saat
 * member keluar) di app + worker; nama protokol wire dipertahankan supaya
 * client lama tetap kompatibel sampai migrasi selesai.
 */
internal object ChatE2eeCrypto {
    private const val PROTOCOL = "XY-E2EE-GCM-v1"
    private const val ROOM_SEED = "XyDesk-GlobalChat-E2EE-Sovereign-v1-2026"

    private val aesKey: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(ROOM_SEED.toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "AES")
    }

    private val hmacKey: SecretKeySpec by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$ROOM_SEED:HMAC-AUTH".toByteArray(Charsets.UTF_8))
        SecretKeySpec(digest, "HmacSHA256")
    }

    data class SealedPayload(
        val protocol: String,
        val cipherBase64: String,
        val ivBase64: String,
        val signatureHex: String,
    )

    fun seal(plainText: String, senderName: String, githubLogin: String, timestampMs: Long): SealedPayload {
        return runCatching {
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            val aad = "${senderName.lowercase(Locale.ROOT)}|${githubLogin.lowercase(Locale.ROOT)}"
                .toByteArray(Charsets.UTF_8)
            cipher.updateAAD(aad)
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val cipherB64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
            val sig = sign(plainText, senderName, githubLogin, cipherB64)
            SealedPayload(PROTOCOL, cipherB64, ivB64, sig)
        }.getOrElse {
            SealedPayload(PROTOCOL, "", "", sign(plainText, senderName, githubLogin, ""))
        }
    }

    fun openAndVerify(
        fallbackText: String,
        senderName: String,
        githubLogin: String,
        cipherBase64: String,
        ivBase64: String,
        signatureHex: String,
    ): Pair<String, Boolean> {
        if (cipherBase64.isNotBlank() && ivBase64.isNotBlank()) {
            runCatching {
                val iv = Base64.decode(ivBase64, Base64.DEFAULT)
                val encrypted = Base64.decode(cipherBase64, Base64.DEFAULT)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
                val aad = "${senderName.lowercase(Locale.ROOT)}|${githubLogin.lowercase(Locale.ROOT)}"
                    .toByteArray(Charsets.UTF_8)
                cipher.updateAAD(aad)
                val decrypted = String(cipher.doFinal(encrypted), Charsets.UTF_8)
                val expectedSig = sign(decrypted, senderName, githubLogin, cipherBase64)
                // Signature kosong TIDAK lagi dihitung valid; HMAC gagal juga tidak.
                val sigOk = expectedSig.isNotBlank() && expectedSig.equals(signatureHex, ignoreCase = true)
                return decrypted to sigOk
            }
        }
        if (signatureHex.isNotBlank()) {
            val expected = sign(fallbackText, senderName, githubLogin, cipherBase64)
            return fallbackText to (expected.isNotBlank() && expected.equals(signatureHex, ignoreCase = true))
        }
        // Tanpa cipher dan tanpa signature: pesan plaintext biasa (mis. dari
        // server/versi lama) — tampilkan apa adanya, tandai TIDAK terverifikasi.
        return fallbackText to false
    }

    fun sign(text: String, senderName: String, githubLogin: String, cipherB64: String): String {
        return runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(hmacKey)
            val raw = "${senderName.trim().lowercase(Locale.ROOT)}|${githubLogin.trim().lowercase(Locale.ROOT)}|${text.trim()}|$cipherB64"
            val bytes = mac.doFinal(raw.toByteArray(Charsets.UTF_8))
            bytes.take(16).joinToString("") { "%02x".format(it) }
        }.getOrDefault("") // gagal sign = "" (jangan pernah mengaku "verified")
    }
}

/**
 * Cache foto profil pengirim di memori agar scroll chat mulus.
 * LruCache dengan anggaran byte (±1/8 max heap, floor 4 MB) — sebelumnya
 * ConcurrentHashMap tanpa batas: sesi chat panjang menumpuk Bitmap sampai OOM.
 */
internal val avatarMemoryCache = object : android.util.LruCache<String, Bitmap>(
    maxOf(4L * 1024L * 1024L, Runtime.getRuntime().maxMemory() / 8L).toInt(),
) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}
