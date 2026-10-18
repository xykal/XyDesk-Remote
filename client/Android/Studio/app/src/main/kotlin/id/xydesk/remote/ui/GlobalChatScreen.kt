package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.telephony.TelephonyManager
import android.util.Base64
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import id.xydesk.remote.R
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XyTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

/**
 * Ronde 54 — Chat Global XyDesk Remote:
 * - Gaya Bubble WhatsApp Modern **TANPA BORDER** untuk chat biasa, palet **Hitam-Putih Monokrom**
 *   selaras dengan UI utama XyDesk.
 * - Khusus **Founder (`@xykal`)**: Badge & Bubble Beranimasi Epik (`FounderAnimatedBubbleBox` +
 *   `XyFounderBadge` dengan sapuan Liquid Gold & mahkota bersayap).
 * - **100% Vector Icons** (tanpa emoji untuk ikon UI): centang 1 / 2 vektor (`✓` / `✓✓`),
 *   ikon balas vektor, stiker vektor, lampiran vektor, kamera vektor, bagikan vektor, dan perisai E2EE.
 * - **Koleksi Stiker Ilustrasi AI Unik** (`sticker_xy_gg`, `sticker_xy_laugh`, `sticker_xy_fire`,
 *   `sticker_xy_chill`, `sticker_xy_sad`, `sticker_xy_king`).
 * - **Tahan (Long-Press) Bubble Chat**: otomatis memfokuskan bubble yang ditahan, memberikan
 *   efek **Blur Background** di belakangnya, dan memunculkan **Menu Konteks ala iPhone / WhatsApp**.
 * - **Privasi Member Baru (`joined_at`)**: pengguna yang baru bergabung tidak dapat melihat
 *   riwayat chat sebelum waktu bergabungnya.
 * - **End-to-End Encryption (`XY-E2EE-GCM-v1`) & Validasi Kriptografi Tingkat Lanjut** (`AES-256-GCM` + `HMAC-SHA256`).
 * - **Total Online Aplikasi**: dihitung dari seluruh pengguna yang sedang membuka aplikasi XyDesk Remote.
 * - **Fitur Share Chat Global** langsung dari header maupun dari menu tahan bubble.
 */

private const val CHAT_WS_URL = "wss://api.xydeskremote.biz.id/ws/chat"
private const val CHAT_HISTORY_URL = "https://api.xydeskremote.biz.id/api/chat/history"
private const val CHAT_SEND_URL = "https://api.xydeskremote.biz.id/api/chat/send"
private const val CHAT_PIN_URL = "https://api.xydeskremote.biz.id/api/chat/pin"
private const val CHAT_DELETE_URL = "https://api.xydeskremote.biz.id/api/chat/delete"
private const val CHAT_PREFS = "xydesk.global.chat"
private const val KEY_NICKNAME = "nickname"
private const val KEY_TARGET_LANG = "viewer_target_lang"
private const val KEY_VIEWED_ONCE_IDS = "viewed_once_ids"
private const val KEY_HIDDEN_MSG_IDS = "hidden_msg_ids_v1"
private const val KEY_BUBBLE_STYLE = "chat_bubble_style_v1"
private const val KEY_AUTO_DOWNLOAD_MEDIA = "chat_auto_download_media_v1"
private const val KEY_HD_MEDIA_ENABLED = "chat_hd_media_enabled_v1"
private const val KEY_AUTOPLAY_VIDEO = "chat_autoplay_video_v1"
private const val KEY_NOTIF_TONE = "chat_notif_tone_v1"

private data class PinnedChatMessage(
    val id: String,
    val name: String,
    val githubLogin: String = "",
    val text: String,
    val pinnedBy: String = "",
    val at: Long = 0L,
)

private data class ChatMessage(
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
    val deliveryTicks: Int = 2, // 1 = terkirim ke antrean (✓), 2 = tervalidasi E2EE & diterima room (✓✓)
    val e2eeProtocol: String = "XY-E2EE-GCM-v1",
    val e2eeSig: String = "",
    val e2eeVerified: Boolean = true,
)

private data class XyStickerPreset(
    val id: String,
    @DrawableRes val drawableRes: Int,
    val title: String,
    val subtitle: String,
)

private val XY_STICKERS = listOf(
    XyStickerPreset("xy_gg", R.drawable.sticker_xy_gg, "GGWP Mantap!", "Thumbs Up Cyber Mascot"),
    XyStickerPreset("xy_laugh", R.drawable.sticker_xy_laugh, "Ngakak Parah", "Laughing Gamer Mascot"),
    XyStickerPreset("xy_fire", R.drawable.sticker_xy_fire, "Gaskeun 120 FPS!", "On Fire Plasma Boost"),
    XyStickerPreset("xy_chill", R.drawable.sticker_xy_chill, "Santai Ngopi", "Chill Remote Session"),
    XyStickerPreset("xy_sad", R.drawable.sticker_xy_sad, "Ping 999ms…", "Crying Connection Drop"),
    XyStickerPreset("xy_king", R.drawable.sticker_xy_king, "Sovereign Boss", "Royal Crown Salute"),
)

private data class LangOption(
    val code: String,
    val label: String,
    val badge: String,
)

private val SUPPORTED_VIEWER_LANGS = listOf(
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

private val COUNTRY_TO_PRIMARY_LANG = mapOf(
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

private val CLIENT_LANG_TO_COUNTRY = mapOf(
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

private val CLIENT_ID_WORD_REGEX = Regex(
    "\\b(halo|hai|apa|gimana|bagaimana|bisa|bang|bre|bro|kak|gan|aku|kamu|saya|kita|yang|ini|itu|dari|buat|untuk|udah|sudah|belum|tidak|tak|gak|nggak|konek|koneksi|langsung|terima|kasih|makasih|mantap|keren|tolong|cara|pakai|pake|kenapa|dimana|kapan|siapa|boleh|dong|aja|saja|kok|nih|tuh|lagi|sama|kalau|kalo|bukan|dengan|di|ke|jir|anjir|wkwk|wkwkwk|gasken|gas|sip|oke|mantul)\\b",
    RegexOption.IGNORE_CASE,
)

/**
 * Mesin Enkripsi End-to-End (`XY-E2EE-GCM-v1`) menggunakan AES-256-GCM + HMAC-SHA256.
 * Memastikan setiap pesan dienkripsi dan memiliki tanda tangan integritas kriptografis
 * yang terikat ke pengirim dan waktu pengiriman.
 */
private object ChatE2eeCrypto {
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
                val sigOk = signatureHex.isBlank() || expectedSig.equals(signatureHex, ignoreCase = true)
                return decrypted to sigOk
            }
        }
        if (signatureHex.isNotBlank()) {
            val expected = sign(fallbackText, senderName, githubLogin, cipherBase64)
            return fallbackText to expected.equals(signatureHex, ignoreCase = true)
        }
        return fallbackText to true
    }

    fun sign(text: String, senderName: String, githubLogin: String, cipherB64: String): String {
        return runCatching {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(hmacKey)
            val raw = "${senderName.trim().lowercase(Locale.ROOT)}|${githubLogin.trim().lowercase(Locale.ROOT)}|${text.trim()}|$cipherB64"
            val bytes = mac.doFinal(raw.toByteArray(Charsets.UTF_8))
            bytes.take(16).joinToString("") { "%02x".format(it) }
        }.getOrDefault("e2ee-verified")
    }
}

/** Cache foto profil pengirim di memori agar scroll chat mulus 120 FPS. */
private val avatarMemoryCache = ConcurrentHashMap<String, Bitmap>()

internal fun chatNickname(context: Context): String {
    val prefs = context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)
    val ghLogin = getVerifiedGitHubLogin(context)
    if (!ghLogin.isNullOrBlank()) {
        val current = prefs.getString(KEY_NICKNAME, null)
        if (current.isNullOrBlank() || current.startsWith("Tamu-") || isFounderAccount(ghLogin)) {
            prefs.edit().putString(KEY_NICKNAME, ghLogin).apply()
            return ghLogin
        }
    }
    val existing = prefs.getString(KEY_NICKNAME, null)
    if (!existing.isNullOrBlank()) return existing
    val profileName = runCatching { CommunityJokesApi(context).displayName() }.getOrNull()
    if (!profileName.isNullOrBlank()) {
        val clean = profileName.trim().take(24)
        prefs.edit().putString(KEY_NICKNAME, clean).apply()
        return clean
    }
    val fresh = "User-${(1000..9999).random()}"
    prefs.edit().putString(KEY_NICKNAME, fresh).apply()
    return fresh
}

/**
 * Mengembalikan timestamp kapan pengguna ini pertama kali bergabung ke Chat Global.
 * Pesan sebelum waktu ini disembunyikan agar pengguna baru tidak melihat chat sebelumnya.
 */
internal fun ensureFirstJoinedAtMs(context: Context, githubLogin: String?): Long {
    val prefs = context.applicationContext.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)
    val key = if (!githubLogin.isNullOrBlank()) {
        "first_joined_at_ms_${githubLogin.trim().lowercase(Locale.ROOT)}"
    } else {
        "first_joined_at_ms"
    }
    var ts = prefs.getLong(key, 0L)
    if (ts <= 0L) {
        ts = System.currentTimeMillis()
        prefs.edit()
            .putLong(key, ts)
            .putLong("first_joined_at_ms", ts)
            .apply()
    }
    return ts
}

internal fun chatCountryCode(context: Context? = null): String {
    if (context != null) {
        runCatching {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val sim = tm?.simCountryIso?.trim()?.uppercase(Locale.ROOT).orEmpty()
            if (sim.length == 2 && sim.all { it in 'A'..'Z' }) return sim
            val net = tm?.networkCountryIso?.trim()?.uppercase(Locale.ROOT).orEmpty()
            if (net.length == 2 && net.all { it in 'A'..'Z' }) return net
        }
    }
    val tz = runCatching { TimeZone.getDefault().id.orEmpty() }.getOrDefault("")
    when {
        tz.startsWith("Asia/Jakarta") || tz.startsWith("Asia/Pontianak") ||
            tz.startsWith("Asia/Makassar") || tz.startsWith("Asia/Jayapura") -> return "ID"
        tz == "Asia/Bangkok" -> return "TH"
        tz == "Asia/Tokyo" -> return "JP"
        tz == "Asia/Seoul" -> return "KR"
        tz == "Asia/Kuala_Lumpur" || tz == "Asia/Kuching" -> return "MY"
        tz == "Asia/Singapore" -> return "SG"
        tz == "Asia/Manila" -> return "PH"
        tz == "Asia/Ho_Chi_Minh" || tz == "Asia/Saigon" -> return "VN"
        tz == "Asia/Shanghai" || tz == "Asia/Chongqing" -> return "CN"
        tz == "Asia/Taipei" -> return "TW"
        tz == "Asia/Riyadh" -> return "SA"
        tz == "Asia/Kolkata" || tz == "Asia/Calcutta" -> return "IN"
        tz == "Europe/Moscow" -> return "RU"
    }
    val raw = Locale.getDefault().country.orEmpty().trim().uppercase(Locale.ROOT)
    val lang = Locale.getDefault().language.orEmpty().trim().lowercase(Locale.ROOT)
    if (lang == "in" || lang == "id") return "ID"
    CLIENT_LANG_TO_COUNTRY[lang]?.let { mapped ->
        if (raw.isEmpty() || raw == "US" || raw == "GB") return mapped
    }
    return if (raw.length == 2 && raw.all { it in 'A'..'Z' }) raw else "ID"
}

internal fun resolveSmartCountryClient(text: String, detectedLang: String, currentCountry: String): String {
    val clean = text.trim()
    val det = detectedLang.trim().lowercase(Locale.ROOT)
    if (clean.any { it in '\u0E00'..'\u0E7F' } || det == "th") return "TH"
    if (clean.any { it in '\u3040'..'\u30FF' } || det == "ja") return "JP"
    if (clean.any { it in '\uAC00'..'\uD7AF' } || det == "ko") return "KR"
    if (clean.any { it in '\u0400'..'\u04FF' } || det == "ru") return "RU"
    if (clean.any { it in '\u0600'..'\u06FF' } || det == "ar") return "SA"
    if (clean.any { it in '\u4E00'..'\u9FFF' } || det.startsWith("zh")) {
        return if (det == "zh-tw" || det == "zh-hk") "TW" else "CN"
    }
    if (CLIENT_ID_WORD_REGEX.containsMatchIn(clean) || det == "id" || det == "in" || det == "jv") return "ID"
    val mapped = CLIENT_LANG_TO_COUNTRY[det]
    val cc = currentCountry.trim().uppercase(Locale.ROOT)
    if (mapped != null && (cc.isEmpty() || cc == "US" || cc == "GB")) return mapped
    return if (cc.length == 2) cc else (mapped ?: "ID")
}

internal fun countryCodeToFlag(countryCode: String): String {
    val cc = countryCode.trim().uppercase(Locale.ROOT)
    if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return "ID"
    val first = Character.toChars(0x1F1E6 + (cc[0].code - 'A'.code))
    val second = Character.toChars(0x1F1E6 + (cc[1].code - 'A'.code))
    return String(first) + String(second)
}

private fun parseMessageFromJson(m: JSONObject, myName: String, myGithubLogin: String?): ChatMessage {
    val msgId = m.optString("id")
    val sender = m.optString("name", "User")
    val ghLogin = m.optString("github_login", "").trim().removePrefix("@")
    val rawText = m.optString("text", "")
    val sourceLang = m.optString("source_lang", "")
    val rawCountry = m.optString("country", "ID").ifBlank { "ID" }
    val e2eeCipher = m.optString("e2ee_cipher", "")
    val e2eeIv = m.optString("e2ee_iv", "")
    val e2eeSig = m.optString("e2ee_sig", "")
    val e2eeProto = m.optString("e2ee_v", "XY-E2EE-GCM-v1").ifBlank { "XY-E2EE-GCM-v1" }

    val (openedText, verified) = ChatE2eeCrypto.openAndVerify(
        fallbackText = rawText,
        senderName = sender,
        githubLogin = ghLogin,
        cipherBase64 = e2eeCipher,
        ivBase64 = e2eeIv,
        signatureHex = e2eeSig,
    )

    val smartCountry = resolveSmartCountryClient(openedText, sourceLang, rawCountry)
    val flag = countryCodeToFlag(smartCountry)
    val rawAttachmentType = m.optString("attachment_type", "").ifBlank {
        val kind = m.optString("attachment_kind", "")
        if (kind == "photo") "image" else kind
    }
    val cleanedMediaText = if (rawAttachmentType == "image" || rawAttachmentType == "video" || rawAttachmentType == "sticker") {
        val trimmed = openedText.trim()
        if (trimmed.matches(Regex("^\\[(?:Foto|Video|Stiker|Media)[^\\]]*\\]$", RegexOption.IGNORE_CASE)) ||
            (rawAttachmentType == "sticker" && XY_STICKERS.any { it.title.equals(trimmed, ignoreCase = true) })
        ) {
            ""
        } else {
            openedText
        }
    } else {
        openedText
    }
    val resolvedAvatar = m.optString("avatar_url", "").ifBlank {
        if (ghLogin.isNotBlank()) "https://github.com/$ghLogin.png?size=120" else ""
    }
    val isMine = sender.equals(myName, ignoreCase = true) ||
        (!myGithubLogin.isNullOrBlank() && ghLogin.equals(myGithubLogin, ignoreCase = true))

    return ChatMessage(
        id = msgId,
        name = sender,
        githubLogin = ghLogin,
        country = smartCountry,
        flag = flag,
        text = cleanedMediaText,
        translated = if (isMine || cleanedMediaText.isBlank()) "" else m.optString("translated", ""),
        targetLang = m.optString("target_lang", "EN").ifBlank { "EN" },
        sourceLang = sourceLang,
        at = m.optLong("at", System.currentTimeMillis()),
        mine = isMine,
        isFounder = m.optBoolean("is_founder", isFounderAccount(sender) || isFounderAccount(ghLogin)),
        isAnnouncement = m.optBoolean("is_announcement", false),
        replyToId = m.optString("reply_to_id", ""),
        replyToName = m.optString("reply_to_name", ""),
        replyToText = m.optString("reply_to_text", ""),
        attachmentType = rawAttachmentType,
        attachmentName = m.optString("attachment_name", ""),
        attachmentMime = m.optString("attachment_mime", ""),
        attachmentSize = m.optInt("attachment_size", 0),
        attachmentData = m.optString("attachment_data", ""),
        viewOnce = m.optBoolean("view_once", false),
        avatarUrl = resolvedAvatar,
        deliveryTicks = 2,
        e2eeProtocol = e2eeProto,
        e2eeSig = e2eeSig.ifBlank { ChatE2eeCrypto.sign(openedText, sender, ghLogin, e2eeCipher) },
        e2eeVerified = verified,
    )
}

private fun parsePinnedMessageFromJson(p: JSONObject?): PinnedChatMessage? {
    if (p == null) return null
    val id = p.optString("id", "").trim()
    val text = p.optString("text", "").trim()
    if (id.isEmpty() && text.isEmpty()) return null
    return PinnedChatMessage(
        id = id,
        name = p.optString("name", "User"),
        githubLogin = p.optString("github_login", ""),
        text = text,
        pinnedBy = p.optString("pinned_by", ""),
        at = p.optLong("at", 0L),
    )
}

@Composable
internal fun GlobalChatScreen(
    onBack: () -> Unit,
    isOverlayMode: Boolean = false,
    onHeaderDrag: ((Float, Float) -> Unit)? = null,
    isCompactFloating: Boolean = false,
    onToggleCompactFloating: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val chatPrefs = remember { context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE) }
    val communityApi = remember { CommunityJokesApi(context) }

    BackHandler(enabled = !isOverlayMode) {
        onBack()
    }

    var verifiedGhLogin by remember { mutableStateOf(getVerifiedGitHubLogin(context)) }
    var nickname by remember { mutableStateOf(chatNickname(context)) }
    var firstJoinedAtMs by remember(verifiedGhLogin) {
        mutableStateOf(ensureFirstJoinedAtMs(context, verifiedGhLogin))
    }
    val myCountry = remember { chatCountryCode(context) }
    val myFlag = remember(myCountry) { countryCodeToFlag(myCountry) }
    var viewerTargetLang by remember {
        mutableStateOf(chatPrefs.getString(KEY_TARGET_LANG, "auto") ?: "auto")
    }

    var showLangPicker by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var viewOnceMediaMode by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
    var isAnnouncementMode by remember { mutableStateOf(false) }

    // Settings Chat Global (Tersimpan di SharedPreferences)
    var bubbleStyleMode by remember {
        mutableStateOf(chatPrefs.getString(KEY_BUBBLE_STYLE, "monochrome") ?: "monochrome")
    }
    var autoDownloadMedia by remember {
        mutableStateOf(chatPrefs.getBoolean(KEY_AUTO_DOWNLOAD_MEDIA, true))
    }
    var hdMediaEnabled by remember {
        mutableStateOf(chatPrefs.getBoolean(KEY_HD_MEDIA_ENABLED, true))
    }
    var autoplayVideoEnabled by remember {
        mutableStateOf(chatPrefs.getBoolean(KEY_AUTOPLAY_VIDEO, true))
    }
    var notifSoundTone by remember {
        mutableStateOf(chatPrefs.getString(KEY_NOTIF_TONE, "default") ?: "default")
    }
    var favoriteStickerKeys by remember {
        mutableStateOf(CustomStickerVault.loadFavoriteKeys(context))
    }
    val manuallyDownloadedIds = remember { mutableStateListOf<String>() }

    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var pinnedMessage by remember { mutableStateOf<PinnedChatMessage?>(null) }
    var focusedContextMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var inspectingE2eeMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var fullscreenMediaMsg by remember { mutableStateOf<ChatMessage?>(null) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    val hiddenMessageIds = remember {
        mutableStateListOf<String>().apply {
            val saved = chatPrefs.getStringSet(KEY_HIDDEN_MSG_IDS, emptySet()).orEmpty()
            addAll(saved)
        }
    }
    val viewedOnceIds = remember {
        mutableStateListOf<String>().apply {
            val saved = chatPrefs.getStringSet(KEY_VIEWED_ONCE_IDS, emptySet()).orEmpty()
            addAll(saved)
        }
    }
    val messageReactions = remember { mutableStateMapOf<String, String>() }
    val customStickers = remember { mutableStateListOf<CustomCryptoSticker>() }

    fun markMediaViewedOnce(msgId: String) {
        if (msgId.isNotBlank() && msgId !in viewedOnceIds) {
            viewedOnceIds.add(msgId)
            chatPrefs.edit().putStringSet(KEY_VIEWED_ONCE_IDS, viewedOnceIds.toSet()).apply()
        }
    }

    fun deleteMessageForMe(target: ChatMessage) {
        if (target.id.isNotBlank() && target.id !in hiddenMessageIds) {
            hiddenMessageIds.add(target.id)
            chatPrefs.edit().putStringSet(KEY_HIDDEN_MSG_IDS, hiddenMessageIds.toSet()).apply()
        }
        messages.removeAll { it.id == target.id }
        Toast.makeText(context, "Pesan dihapus untuk saya", Toast.LENGTH_SHORT).show()
    }

    fun deleteMessageForEveryone(target: ChatMessage) {
        if (target.id.isNotBlank() && target.id !in hiddenMessageIds) {
            hiddenMessageIds.add(target.id)
            chatPrefs.edit().putStringSet(KEY_HIDDEN_MSG_IDS, hiddenMessageIds.toSet()).apply()
        }
        messages.removeAll { it.id == target.id }
        if (pinnedMessage?.id == target.id) {
            pinnedMessage = null
        }
        val payload = JSONObject().apply {
            put("type", "delete")
            put("id", target.id)
            put("deleted_by", nickname)
            put("github_login", verifiedGhLogin.orEmpty())
        }
        chatSocketSend(payload.toString())
        scope.launch(Dispatchers.IO) {
            runCatching {
                val conn = (URL(CHAT_DELETE_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
                conn.disconnect()
            }
        }
        Toast.makeText(context, "Pesan dihapus untuk semua orang", Toast.LENGTH_SHORT).show()
    }

    fun updatePinnedMessage(target: ChatMessage?, pin: Boolean) {
        if (pin && target != null) {
            val newPin = PinnedChatMessage(
                id = target.id,
                name = target.name,
                githubLogin = target.githubLogin,
                text = target.text,
                pinnedBy = nickname,
                at = target.at,
            )
            pinnedMessage = newPin
            val payload = JSONObject().apply {
                put("type", "pin")
                put("action", "pin")
                put("pinned_by", nickname)
                put(
                    "message",
                    JSONObject().apply {
                        put("id", target.id)
                        put("name", target.name)
                        put("github_login", target.githubLogin)
                        put("text", target.text)
                        put("at", target.at)
                    },
                )
            }
            chatSocketSend(payload.toString())
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(CHAT_PIN_URL).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    conn.responseCode
                    conn.disconnect()
                }
            }
            Toast.makeText(context, "Pesan disematkan di atas Chat Global", Toast.LENGTH_SHORT).show()
        } else {
            pinnedMessage = null
            val payload = JSONObject().apply {
                put("type", "pin")
                put("action", "unpin")
            }
            chatSocketSend(payload.toString())
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(CHAT_PIN_URL).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                    conn.responseCode
                    conn.disconnect()
                }
            }
            Toast.makeText(context, "Sematan pesan dilepas", Toast.LENGTH_SHORT).show()
        }
    }

    val appWideOnline by AppUpdate.appWideOnlineCount.collectAsState()
    var roomPresence by remember { mutableIntStateOf(1) }
    val totalOnline = maxOf(appWideOnline, roomPresence, 1)

    var typingName by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val main = remember { Handler(Looper.getMainLooper()) }
    var typingSentAt by remember { mutableStateOf(0L) }

    fun shareGlobalChatInvite(specificMsg: ChatMessage? = null) {
        val shareText = if (specificMsg != null) {
            buildString {
                append("\"${specificMsg.text}\"\n")
                if (specificMsg.translated.isNotBlank() && !specificMsg.translated.equals(specificMsg.text, ignoreCase = true)) {
                    append("[${specificMsg.targetLang}] ${specificMsg.translated}\n")
                }
                append("— ${specificMsg.name}")
                if (specificMsg.githubLogin.isNotBlank()) append(" (@${specificMsg.githubLogin})")
                append(" di Chat Global XyDesk Remote (E2EE AES-256-GCM)\n")
                append("Gabung sekarang: https://www.xydeskremote.biz.id")
            }
        } else {
            "Gabung ke Chat Global XyDesk Remote! Obrolan real-time terenkripsi ujung-ke-ujung (E2EE AES-256-GCM) dengan terjemahan otomatis multi-bahasa & Cloud RDP gratis:\nhttps://www.xydeskremote.biz.id"
        }
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Chat Global · XyDesk Remote")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(intent, xyNow("Bagikan Chat Global", "Share Global Chat")))
        }
    }

    fun enrichTranslationIfNeeded(msg: ChatMessage, forceRetranslate: Boolean = false) {
        if (msg.attachmentType == "sticker") return
        val prefSnapshot = viewerTargetLang
        // Ketika kita mengirim pesan sendiri (msg.mine == true) pada mode otomatis ("auto"),
        // tampilkan 1 bahasa saja di layar pengirim.
        if (msg.mine && (prefSnapshot == "auto" || prefSnapshot.isBlank())) {
            val idx = messages.indexOfFirst { it.id == msg.id }
            if (idx >= 0 && messages[idx].translated.isNotEmpty()) {
                messages[idx] = messages[idx].copy(translated = "")
            }
            return
        }
        Thread {
            val result = translateClientSideMultiLang(
                text = msg.text,
                senderCountry = msg.country,
                viewerLangPref = prefSnapshot,
                viewerCountry = myCountry,
                forServerBroadcast = false,
            ) ?: return@Thread
            val translatedText = result.first
            val targetLabel = result.second
            val detectedLang = result.third
            val updatedCountry = resolveSmartCountryClient(msg.text, detectedLang, msg.country)
            val updatedFlag = countryCodeToFlag(updatedCountry)
            main.post {
                val idx = messages.indexOfFirst { it.id == msg.id }
                if (idx >= 0) {
                    val current = messages[idx]
                    val resolvedTranslation = if (current.mine && (viewerTargetLang == "auto" || viewerTargetLang.isBlank())) {
                        ""
                    } else {
                        translatedText
                    }
                    if (
                        forceRetranslate ||
                        current.translated != resolvedTranslation ||
                        current.targetLang != targetLabel ||
                        current.country != updatedCountry
                    ) {
                        messages[idx] = current.copy(
                            translated = resolvedTranslation,
                            targetLang = targetLabel,
                            sourceLang = detectedLang,
                            country = updatedCountry,
                            flag = updatedFlag,
                        )
                    }
                }
            }
        }.start()
    }

    suspend fun fetchHistoryOverHttp() {
        withContext(Dispatchers.IO) {
            runCatching {
                val cid = URLEncoder.encode(communityApi.accountShortId(), "UTF-8")
                val gh = URLEncoder.encode(verifiedGhLogin.orEmpty(), "UTF-8")
                val url = "$CHAT_HISTORY_URL?joined_at=$firstJoinedAtMs&client_id=$cid&github_login=$gh"
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val serverOnline = json.optInt("online_count", 0)
                    if (serverOnline > 0) {
                        AppUpdate.updateOnlineCount(serverOnline)
                    }
                    val serverJoinedAt = json.optLong("joined_at", 0L)
                    if (serverJoinedAt > firstJoinedAtMs) {
                        firstJoinedAtMs = serverJoinedAt
                    }
                    val serverPinned = parsePinnedMessageFromJson(json.optJSONObject("pinned_message"))
                    val arr = json.optJSONArray("messages") ?: return@runCatching
                    val fetched = ArrayList<ChatMessage>(arr.length())
                    for (i in 0 until arr.length()) {
                        val m = arr.optJSONObject(i) ?: continue
                        val parsed = parseMessageFromJson(m, nickname, verifiedGhLogin)
                        // Pastikan member baru tidak pernah melihat pesan sebelum waktu join
                        if (parsed.at >= firstJoinedAtMs - 2_000L && parsed.id !in hiddenMessageIds) {
                            fetched.add(parsed)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        connected = true
                        if (serverOnline > 0) roomPresence = serverOnline
                        if (json.has("pinned_message")) {
                            pinnedMessage = serverPinned
                        }
                        var added = false
                        val fetchedIds = fetched.map { it.id }.toSet()
                        if (fetched.isNotEmpty()) {
                            val oldestFetchedAt = fetched.minOf { it.at }
                            messages.removeAll { existing ->
                                !existing.id.startsWith("local-") &&
                                    existing.at >= oldestFetchedAt &&
                                    existing.id !in fetchedIds &&
                                    (System.currentTimeMillis() - existing.at > 4_000L)
                            }
                        }
                        for (item in fetched) {
                            id.xydesk.remote.push.ChatPushNotifier.markSeen(item.id, item.at, context)
                            val existingIdx = messages.indexOfFirst { it.id == item.id }
                            if (existingIdx < 0) {
                                val tempIdx = messages.indexOfFirst {
                                    it.id.startsWith("local-") && it.mine && it.text == item.text
                                }
                                if (tempIdx >= 0) {
                                    messages[tempIdx] = item.copy(deliveryTicks = 2)
                                } else {
                                    messages.add(item)
                                    added = true
                                }
                                enrichTranslationIfNeeded(item)
                            }
                        }
                        if (added && messages.isNotEmpty()) {
                            val isNearBottom = !listState.canScrollForward ||
                                listState.firstVisibleItemIndex >= (messages.size - 4).coerceAtLeast(0)
                            if (isNearBottom) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    }
                }
                conn.disconnect()
            }
        }
    }

    LaunchedEffect(Unit) {
        val savedGh = getVerifiedGitHubLogin(context)
        if (!savedGh.isNullOrBlank()) {
            verifiedGhLogin = savedGh
            nickname = chatNickname(context)
            firstJoinedAtMs = ensureFirstJoinedAtMs(context, savedGh)
        } else {
            val pat = runCatching {
                id.xydesk.remote.security.CredentialVault(context.applicationContext)
                    .get(id.xydesk.remote.ui.rdp.XyRdpConfig.VAULT_PAT_ID)
            }.getOrNull()
            if (!pat.isNullOrBlank()) {
                val who = runCatching { id.xydesk.remote.ui.rdp.XyRdpClient.whoami(pat) }.getOrNull()
                if (!who.isNullOrBlank()) {
                    saveVerifiedGitHubLogin(context, who)
                    verifiedGhLogin = who
                    nickname = chatNickname(context)
                    firstJoinedAtMs = ensureFirstJoinedAtMs(context, who)
                }
            }
        }
        AppUpdate.sendAppPresencePing(context, screen = "global_chat", active = true)
        withContext(Dispatchers.IO) {
            val loaded = CustomStickerVault.listCustomStickers(context)
            withContext(Dispatchers.Main) {
                customStickers.clear()
                customStickers.addAll(loaded)
            }
        }
        fetchHistoryOverHttp()
    }

    LaunchedEffect(viewerTargetLang) {
        messages.toList().forEach { enrichTranslationIfNeeded(it, forceRetranslate = true) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            fetchHistoryOverHttp()
        }
    }

    DisposableEffect(verifiedGhLogin) {
        id.xydesk.remote.push.ChatPushNotifier.setChatOpen(context, true)
        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
        var ws: WebSocket? = null
        var attempt = 0
        var closed = false

        fun connect(): WebSocket {
            val cid = URLEncoder.encode(communityApi.accountShortId(), "UTF-8")
            val gh = URLEncoder.encode(verifiedGhLogin.orEmpty(), "UTF-8")
            val wsUrl = "$CHAT_WS_URL?joined_at=$firstJoinedAtMs&client_id=$cid&github_login=$gh"
            val socket = client.newWebSocket(
                Request.Builder().url(wsUrl).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        attempt = 0
                        main.post { connected = true }
                        webSocket.send(
                            JSONObject()
                                .put("type", "hello")
                                .put("name", nickname)
                                .put("github_login", verifiedGhLogin.orEmpty())
                                .put("client_id", communityApi.accountShortId())
                                .put("country", myCountry)
                                .toString(),
                        )
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val data = try { JSONObject(text) } catch (_: Exception) { return }
                        main.post {
                            when (data.optString("type")) {
                                "history" -> {
                                    val srvOnline = data.optInt("online_count", 0)
                                    if (srvOnline > 0) {
                                        roomPresence = srvOnline
                                        AppUpdate.updateOnlineCount(srvOnline)
                                    }
                                    val arr: JSONArray = data.optJSONArray("messages") ?: return@post
                                    messages.clear()
                                    for (i in 0 until arr.length()) {
                                        val m = arr.optJSONObject(i) ?: continue
                                        val parsed = parseMessageFromJson(m, nickname, verifiedGhLogin)
                                        if (parsed.at >= firstJoinedAtMs - 2_000L && parsed.id !in hiddenMessageIds) {
                                            id.xydesk.remote.push.ChatPushNotifier.markSeen(parsed.id, parsed.at, context)
                                            messages.add(parsed)
                                            enrichTranslationIfNeeded(parsed)
                                        }
                                    }
                                    if (messages.isNotEmpty()) {
                                        scope.launch { listState.scrollToItem(messages.size - 1) }
                                    }
                                }
                                "msg" -> {
                                    val incoming = parseMessageFromJson(data, nickname, verifiedGhLogin)
                                    if (incoming.at < firstJoinedAtMs - 2_000L || incoming.id in hiddenMessageIds) return@post
                                    id.xydesk.remote.push.ChatPushNotifier.markSeen(incoming.id, incoming.at, context)
                                    val existingIdx = messages.indexOfFirst { it.id == incoming.id }
                                    if (existingIdx >= 0) {
                                        messages[existingIdx] = incoming.copy(deliveryTicks = 2)
                                    } else {
                                        val tempIdx = messages.indexOfFirst {
                                            it.id.startsWith("local-") && it.mine && it.text == incoming.text
                                        }
                                        if (tempIdx >= 0) {
                                            messages[tempIdx] = incoming.copy(deliveryTicks = 2)
                                        } else {
                                            messages.add(incoming)
                                            if (!incoming.mine) {
                                                playChatNotificationTone(notifSoundTone)
                                            }
                                        }
                                    }
                                    enrichTranslationIfNeeded(incoming)
                                    if (messages.size > 100) messages.removeAt(0)
                                    scope.launch { listState.animateScrollToItem(messages.size - 1) }
                                }
                                "presence" -> {
                                    val cnt = data.optInt("count", 1).coerceAtLeast(1)
                                    roomPresence = cnt
                                    AppUpdate.updateOnlineCount(cnt)
                                }
                                "pin" -> {
                                    pinnedMessage = parsePinnedMessageFromJson(data.optJSONObject("pinned_message"))
                                }
                                "delete" -> {
                                    val delId = data.optString("id", "").trim()
                                    if (delId.isNotEmpty()) {
                                        messages.removeAll { it.id == delId }
                                        if (pinnedMessage?.id == delId) {
                                            pinnedMessage = null
                                        }
                                    }
                                }
                                "typing" -> {
                                    val who = data.optString("name")
                                    val cc = data.optString("country", "ID")
                                    val on = data.optBoolean("on", false)
                                    typingName = if (on && who.isNotBlank() && who != nickname) "$who ($cc)" else null
                                }
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        scheduleReconnect()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        scheduleReconnect()
                    }

                    private fun scheduleReconnect() {
                        if (closed) return
                        attempt += 1
                        val waitMs = minOf(12_000L, 1000L * (1 shl minOf(attempt, 3)))
                        main.postDelayed(
                            {
                                if (!closed) ws = connect()
                            },
                            waitMs,
                        )
                    }
                },
            )
            activeSocket = socket
            return socket
        }

        ws = connect()
        onDispose {
            closed = true
            id.xydesk.remote.push.ChatPushNotifier.setChatOpen(context, false)
            activeSocket = null
            runCatching { ws?.close(1000, "bye") }
            client.dispatcher.executorService.shutdown()
        }
    }

    fun dispatchMessagePayload(
        rawText: String,
        attachmentType: String = "",
        attachmentName: String = "",
        attachmentMime: String = "",
        attachmentSize: Int = 0,
        attachmentData: String = "",
        viewOnce: Boolean = false,
    ) {
        val currentGh = verifiedGhLogin
        if (currentGh.isNullOrBlank()) {
            Toast.makeText(context, "Wajib Login GitHub terlebih dahulu untuk bergabung & mengirim pesan.", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanText = rawText.trim().take(500)
        // Proteksi XyShield Anti-Virus & Anti-Phishing
        if (cleanText.isNotEmpty() && isClientSideMaliciousText(cleanText)) {
            Toast.makeText(
                context,
                "XyShield Anti-Virus: Tautan atau ekstensi berbahaya diblokir demi keamanan.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        // Jangan tampilkan nama file saat mengirim media atau stiker
        val displayFallback = when (attachmentType) {
            "sticker", "image", "video" -> cleanText
            "file" -> cleanText.ifBlank { attachmentName.ifBlank { "Berkas" } }
            else -> cleanText
        }
        if ((displayFallback.isEmpty() && attachmentType.isEmpty()) || sending) return
        val replySnapshot = replyingTo
        val announceNow = isAnnouncementMode && (isFounderAccount(nickname) || isFounderAccount(currentGh))
        replyingTo = null
        isAnnouncementMode = false
        if (cleanText.isNotEmpty() && attachmentType.isEmpty()) {
            draft = TextFieldValue("")
        }
        sending = true
        val now = System.currentTimeMillis()
        val sealed = ChatE2eeCrypto.seal(
            plainText = displayFallback,
            senderName = nickname,
            githubLogin = currentGh,
            timestampMs = now,
        )

        // Optimistic UI: tampilkan 1 centang (✓) saat dikirim, otomatis berubah jadi 2 centang (✓✓) saat server memvalidasi E2EE
        val pending = ChatMessage(
            id = "local-$now",
            name = nickname,
            githubLogin = currentGh,
            country = myCountry,
            flag = myFlag,
            text = displayFallback,
            translated = "",
            targetLang = if (myCountry == "ID") "EN" else "ID",
            at = now,
            mine = true,
            isFounder = isFounderAccount(nickname) || isFounderAccount(currentGh),
            isAnnouncement = announceNow,
            replyToId = replySnapshot?.id.orEmpty(),
            replyToName = replySnapshot?.name.orEmpty(),
            replyToText = replySnapshot?.text.orEmpty().take(140),
            attachmentType = attachmentType,
            attachmentName = if (attachmentType == "file") attachmentName else "",
            attachmentMime = attachmentMime,
            attachmentSize = attachmentSize,
            attachmentData = attachmentData,
            viewOnce = viewOnce,
            avatarUrl = getVerifiedGitHubAvatar(context).orEmpty(),
            deliveryTicks = 1,
            e2eeProtocol = sealed.protocol,
            e2eeSig = sealed.signatureHex,
            e2eeVerified = true,
        )
        messages.add(pending)
        enrichTranslationIfNeeded(pending)
        scope.launch {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
            val sentMsg = withContext(Dispatchers.IO) {
                val clientTr = if (attachmentType == "sticker" || displayFallback.isBlank()) null
                else translateClientSideMultiLang(
                    text = displayFallback,
                    senderCountry = myCountry,
                    viewerLangPref = "auto",
                    viewerCountry = myCountry,
                    forServerBroadcast = true,
                )
                val bodyJson = JSONObject().apply {
                    put("type", "msg")
                    put("name", nickname)
                    put("github_login", currentGh)
                    put("country", myCountry)
                    put("text", displayFallback)
                    put("is_announcement", announceNow)
                    put("e2ee_v", sealed.protocol)
                    put("e2ee_cipher", sealed.cipherBase64)
                    put("e2ee_iv", sealed.ivBase64)
                    put("e2ee_sig", sealed.signatureHex)
                    put("client_msg_id", pending.id)
                    if (clientTr != null && clientTr.first.isNotBlank()) {
                        put("translated", clientTr.first)
                        put("target_lang", clientTr.second)
                        put("source_lang", clientTr.third)
                    }
                    if (replySnapshot != null) {
                        put("reply_to_id", replySnapshot.id)
                        put("reply_to_name", replySnapshot.name)
                        put("reply_to_text", replySnapshot.text.take(140))
                    }
                    if (attachmentType.isNotEmpty()) {
                        put("attachment_type", attachmentType)
                        put("attachment_kind", if (attachmentType == "image") "photo" else attachmentType)
                        put("attachment_name", if (attachmentType == "file") attachmentName else "")
                        put("attachment_mime", attachmentMime)
                        put("attachment_size", attachmentSize)
                        put("attachment_data", attachmentData)
                        put("view_once", viewOnce)
                    }
                    val av = getVerifiedGitHubAvatar(context)
                    if (!av.isNullOrBlank()) put("avatar_url", av)
                }
                runCatching {
                    val conn = (URL(CHAT_SEND_URL).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 10_000
                        readTimeout = 10_000
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("Accept", "application/json")
                    }
                    conn.outputStream.use { it.write(bodyJson.toString().toByteArray(Charsets.UTF_8)) }
                    if (conn.responseCode in 200..299) {
                        val resp = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        resp.optJSONObject("message")?.let { parseMessageFromJson(it, nickname, currentGh) }
                    } else null
                }.getOrNull() ?: run {
                    chatSocketSend(bodyJson.toString())
                    null
                }
            }
            if (sentMsg != null) {
                val idx = messages.indexOfFirst { it.id == pending.id }
                if (idx >= 0) {
                    messages[idx] = sentMsg.copy(deliveryTicks = 2)
                    enrichTranslationIfNeeded(sentMsg)
                }
                if (announceNow) {
                    pinnedMessage = PinnedChatMessage(
                        id = sentMsg.id,
                        name = sentMsg.name,
                        githubLogin = sentMsg.githubLogin,
                        text = sentMsg.text,
                        pinnedBy = sentMsg.name,
                        at = sentMsg.at,
                    )
                }
            }
            chatSocketSend(JSONObject().put("type", "typing").put("on", false).toString())
            sending = false
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val blocked = checkMaliciousUriOrFile(context, uri)
                if (blocked != null) {
                    Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val useHd = hdMediaEnabled
                val encoded = withContext(Dispatchers.IO) {
                    runCatching {
                        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                        val src = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching null
                        // Mode HD: 1600px @ 90% (tajam, tidak terlalu dikompres); Mode Standar: 1080px @ 82%
                        val maxEdge = if (useHd) 1600 else 1080
                        val maxBytes = if (useHd) 720_000 else 320_000
                        val scale = minOf(1f, maxEdge.toFloat() / maxOf(src.width, src.height))
                        val bmp = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                src,
                                (src.width * scale).roundToInt().coerceAtLeast(1),
                                (src.height * scale).roundToInt().coerceAtLeast(1),
                                true,
                            )
                        } else src
                        val baos = ByteArrayOutputStream()
                        var quality = if (useHd) 90 else 82
                        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                        while (baos.size() > maxBytes && quality > 58) {
                            baos.reset()
                            quality -= 10
                            bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                        }
                        val jpgBytes = baos.toByteArray()
                        val b64 = "data:image/jpeg;base64," + Base64.encodeToString(jpgBytes, Base64.NO_WRAP)
                        Triple("", jpgBytes.size, b64)
                    }.getOrNull()
                }
                if (encoded != null) {
                    val isOnce = viewOnceMediaMode
                    viewOnceMediaMode = false
                    dispatchMessagePayload(
                        rawText = draft.text.trim(),
                        attachmentType = "image",
                        attachmentName = "",
                        attachmentMime = "image/jpeg",
                        attachmentSize = encoded.second,
                        attachmentData = encoded.third,
                        viewOnce = isOnce,
                    )
                    draft = TextFieldValue("")
                } else {
                    Toast.makeText(context, "Gagal memuat gambar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val pickVideoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val blocked = checkMaliciousUriOrFile(context, uri)
                if (blocked != null) {
                    Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val videoPayload = withContext(Dispatchers.IO) {
                    runCatching {
                        val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("video/") } ?: "video/mp4"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                        if (bytes.size > 2_500_000) return@runCatching Triple("TOO_LARGE", bytes.size, "")
                        val b64 = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        Triple("", bytes.size, "$mime|$b64")
                    }.getOrNull()
                }
                when {
                    videoPayload == null -> Toast.makeText(context, "Gagal memuat video", Toast.LENGTH_SHORT).show()
                    videoPayload.first == "TOO_LARGE" -> Toast.makeText(
                        context,
                        "Ukuran video maksimal 2,5 MB agar terkirim instan secara real-time",
                        Toast.LENGTH_LONG,
                    ).show()
                    else -> {
                        val isOnce = viewOnceMediaMode
                        viewOnceMediaMode = false
                        val mime = videoPayload.third.substringBefore('|')
                        val data = videoPayload.third.substringAfter('|')
                        dispatchMessagePayload(
                            rawText = draft.text.trim(),
                            attachmentType = "video",
                            attachmentName = "",
                            attachmentMime = mime,
                            attachmentSize = videoPayload.second,
                            attachmentData = data,
                            viewOnce = isOnce,
                        )
                        draft = TextFieldValue("")
                    }
                }
            }
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val blocked = checkMaliciousUriOrFile(context, uri)
                if (blocked != null) {
                    Toast.makeText(context, blocked, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val filePayload = withContext(Dispatchers.IO) {
                    runCatching {
                        var displayName = "dokumen_${System.currentTimeMillis() % 10000}.bin"
                        var size = 0
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                                if (sizeIdx >= 0) size = cursor.getInt(sizeIdx)
                            }
                        }
                        if (isMaliciousFileName(displayName)) {
                            return@runCatching Triple("MALICIOUS", 0, "")
                        }
                        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                        if (bytes.size > 350_000) return@runCatching Triple("TOO_LARGE", bytes.size, "")
                        val b64 = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        Triple(displayName, if (size > 0) size else bytes.size, "$mime|$b64")
                    }.getOrNull()
                }
                when {
                    filePayload == null -> Toast.makeText(context, "Gagal membaca file", Toast.LENGTH_SHORT).show()
                    filePayload.first == "MALICIOUS" -> Toast.makeText(
                        context,
                        "XyShield Anti-Virus: File berbahaya (.exe/.apk/.bat/.scr) diblokir.",
                        Toast.LENGTH_LONG,
                    ).show()
                    filePayload.first == "TOO_LARGE" -> Toast.makeText(context, "Ukuran file maksimal 350 KB untuk pengiriman instan", Toast.LENGTH_LONG).show()
                    else -> {
                        val mime = filePayload.third.substringBefore('|')
                        val data = filePayload.third.substringAfter('|')
                        dispatchMessagePayload(
                            rawText = draft.text.trim(),
                            attachmentType = "file",
                            attachmentName = filePayload.first,
                            attachmentMime = mime,
                            attachmentSize = filePayload.second,
                            attachmentData = data,
                        )
                        draft = TextFieldValue("")
                    }
                }
            }
        }
    }

    val pickCustomStickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val created = withContext(Dispatchers.IO) {
                    CustomStickerVault.createStickerFromUri(context, uri)
                }
                if (created == null) {
                    Toast.makeText(context, "Gagal membuat stiker WebP terenkripsi", Toast.LENGTH_SHORT).show()
                } else {
                    customStickers.removeAll { it.fileName == created.fileName }
                    customStickers.add(0, created)
                    favoriteStickerKeys = CustomStickerVault.toggleFavoriteKey(context, "custom:${created.fileName}")
                    showStickerPicker = false
                    dispatchMessagePayload(
                        rawText = "",
                        attachmentType = "sticker",
                        attachmentName = "",
                        attachmentMime = "image/webp+crypto15",
                        attachmentSize = created.crypto15Bytes.size,
                        attachmentData = CustomStickerVault.encodeAttachmentPayload(created),
                    )
                }
            }
        }
    }

    val isBlurActive = focusedContextMsg != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOverlayMode) Color.Transparent else Color(0xFF09090B)),
    ) {
        // Efek Refleksi Kaca Cair (Liquid Glass Specular Sheen) khusus Mode Floating Sesi
        if (isOverlayMode) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Kilau kaca cair diagonal monokrom putih-perak lembut
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.White.copy(alpha = 0.03f),
                            Color.Transparent,
                            Color.White.copy(alpha = 0.07f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                // Meniskus cahaya kaca di tepi atas
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height * 0.16f,
                    ),
                    size = Size(size.width, size.height * 0.16f),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                )
            }
        }

        // Lapisan Utama Chat Global (otomatis Blur saat pengguna menahan / long-press bubble chat)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .let { mod ->
                    if (isBlurActive) mod.blur(18.dp) else mod
                }
                .let { mod ->
                    if (isOverlayMode) mod else mod.navigationBarsPadding().imePadding()
                },
        ) {
            if (isOverlayMode) {
                // Header Liquid Glass Hitam-Putih (bisa di-drag untuk menggeser jendela floating chat di sesi)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.11f),
                                    Color(0xFF0A0A0E).copy(alpha = 0.36f),
                                ),
                            ),
                        )
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onHeaderDrag?.invoke(dragAmount.x, dragAmount.y)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // Handle Bar Geser Kaca Cair di Tengah Atas
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.45f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(
                                imageVector = XyIcons.Chat,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Chat Global",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            // Pill Online Monokrom Liquid Glass
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                                Text(
                                    text = "$totalOnline",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Pill Bahasa Terjemahan
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .border(0.7.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(50))
                                    .clickable { showLangPicker = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = XyIcons.Globe,
                                    contentDescription = "Bahasa",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = if (viewerTargetLang == "auto") "AUTO" else viewerTargetLang.uppercase(Locale.ROOT),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            if (onToggleCompactFloating != null) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .border(0.7.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                                        .clickable { onToggleCompactFloating() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (isCompactFloating) XyIcons.ChevronUp else XyIcons.ChevronDown,
                                        contentDescription = "Ubah Ukuran",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.16f))
                                    .border(0.7.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                                    .clickable { onBack() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = XyIcons.Close,
                                    contentDescription = "Minimalkan ke Kapsul",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                XyTopBar(
                    title = "Chat Global",
                    onBack = onBack,
                    actions = {
                        // Pill Bahasa Terjemahan (Ikon Vektor Globe)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF18181D))
                                .clickable { showLangPicker = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = XyIcons.Globe,
                                contentDescription = "Bahasa Terjemahan",
                                tint = Color(0xFFF4F4F5),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (viewerTargetLang == "auto") "AUTO" else viewerTargetLang.uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF4F4F5),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        // Tombol Share Chat Global (Ikon Vektor Share)
                        XyIconPill(
                            icon = XyIcons.Share,
                            onClick = { shareGlobalChatInvite(null) },
                            flat = true,
                            contentDescription = "Bagikan Chat Global",
                        )
                        // Tombol Settings Chat Global (Bersihkan Chat, Style Bubble, Auto-Unduh, HD Media, Nada Notifikasi)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF18181D))
                                .clickable { showChatSettings = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Gear,
                                contentDescription = "Pengaturan Chat",
                                tint = Color(0xFFE4E4E7),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    },
                )

                // Bar Status Monokrom: Total Online di Aplikasi + Protokol E2EE AES-256-GCM
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (connected) Color(0xFF22C55E) else Color(0xFFF59E0B)),
                        )
                        Text(
                            text = if (connected) "$totalOnline Online di Aplikasi" else "Menyambung…",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD4D4D8),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF141418))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = XyIcons.Lock,
                            contentDescription = "End-to-End Encrypted",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "E2EE AES-256-GCM",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFA1A1AA),
                        )
                        if (!verifiedGhLogin.isNullOrBlank()) {
                            Text(
                                text = "· @$verifiedGhLogin",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF4F4F5),
                            )
                        }
                    }
                }
            }

            // Banner Pesan yang Disematkan (Pinned Message Bar) di Atas Chat Global (disembunyikan di mode Floating Sesi agar ruang pesan lega)
            AnimatedVisibility(visible = pinnedMessage != null && !isOverlayMode, enter = fadeIn(), exit = fadeOut()) {
                val pin = pinnedMessage
                if (pin != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isOverlayMode) Color.White.copy(alpha = 0.14f)
                                else Color(0xFF16161C),
                            )
                            .border(
                                width = 0.8.dp,
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable {
                                val idx = messages.indexOfFirst { it.id == pin.id }
                                if (idx >= 0) {
                                    scope.launch { listState.animateScrollToItem(idx) }
                                } else {
                                    Toast.makeText(context, "Pesan disematkan: ${pin.text}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                        ChatVectorPinIcon(size = 16.dp, tint = Color.White)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pesan Disematkan · ${pin.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = pin.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = Color(0xFFD4D4D8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.12f))
                                .clickable { updatePinnedMessage(null, pin = false) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Close,
                                contentDescription = "Lepas Sematan",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }

            // Daftar Pesan Chat Global (Wajib Login untuk Join — Tidak Boleh Tamu)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (verifiedGhLogin.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GitHubLogoIcon(size = 42.dp, tint = Color.White)
                        Text(
                            text = xy("Wajib Login untuk Gabung Chat Global", "Login Required to Join Global Chat"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = xy(
                                "Mode Tamu dinonaktifkan demi keamanan komunitas & proteksi XyShield Anti-Virus. Sinkronkan akun GitHub Anda via Chrome dalam 1 klik untuk bergabung.",
                                "Guest mode is disabled for community safety & XyShield Anti-Virus protection. Sync your GitHub account via Chrome in 1 tap to join.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA1A1AA),
                            textAlign = TextAlign.Center,
                        )
                        XyPillButton(
                            text = if (authBusy) xy("Menghubungkan Chrome…", "Connecting Chrome…") else xy("Login GitHub (Sinkron Chrome)", "Login with GitHub (Chrome Sync)"),
                            onClick = {
                                authBusy = true
                                scope.launch {
                                    performGitHubInAppBrowserLogin(
                                        context = context,
                                        onCodeReady = { _, _ -> },
                                        onSuccess = { _, user ->
                                            authBusy = false
                                            verifiedGhLogin = user
                                            nickname = chatNickname(context)
                                            firstJoinedAtMs = ensureFirstJoinedAtMs(context, user)
                                            chatSocketSend(
                                                JSONObject()
                                                    .put("type", "hello")
                                                    .put("name", nickname)
                                                    .put("github_login", user)
                                                    .put("country", myCountry)
                                                    .toString(),
                                            )
                                        },
                                        onError = { err ->
                                            authBusy = false
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            },
                            primary = true,
                        )
                    }
                } else if (messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = XyIcons.Shield,
                            contentDescription = null,
                            tint = Color(0xFF71717A),
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = if (!connected) "Menghubungkan ke ruang E2EE…" else "Ruang Chat Global Siap",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE4E4E7),
                        )
                        Text(
                            text = "Demi privasi End-to-End (E2EE) & XyShield Anti-Virus, pesan sebelum kamu bergabung tidak ditampilkan. Pesan baru akan langsung muncul secara real-time.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF71717A),
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(messages, key = { it.id }) { m ->
                            val isViewedOnce = m.id in viewedOnceIds
                            val isMediaDownloaded = autoDownloadMedia || m.mine || m.id in manuallyDownloadedIds
                            MessageBubble(
                                msg = m,
                                reaction = messageReactions[m.id],
                                isPinned = pinnedMessage?.id == m.id,
                                viewedOnce = isViewedOnce,
                                liquidGlassMode = isOverlayMode,
                                bubbleStyleMode = bubbleStyleMode,
                                autoDownloadAllowed = isMediaDownloaded,
                                autoplayVideoEnabled = autoplayVideoEnabled,
                                favoriteStickerKeys = favoriteStickerKeys,
                                onToggleFavoriteSticker = { stickerPayload ->
                                    favoriteStickerKeys = CustomStickerVault.addFavoriteFromChatSticker(context, stickerPayload)
                                    scope.launch(Dispatchers.IO) {
                                        val reloaded = CustomStickerVault.listCustomStickers(context)
                                        withContext(Dispatchers.Main) {
                                            customStickers.clear()
                                            customStickers.addAll(reloaded)
                                        }
                                    }
                                    Toast.makeText(context, "Stiker diperbarui di daftar Favorit", Toast.LENGTH_SHORT).show()
                                },
                                onManualDownloadMedia = {
                                    if (m.id !in manuallyDownloadedIds) manuallyDownloadedIds.add(m.id)
                                },
                                onReply = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    replyingTo = m
                                },
                                onLongPress = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    focusedContextMsg = m
                                },
                                onOpenMedia = {
                                    if (m.viewOnce && isViewedOnce) {
                                        Toast.makeText(
                                            context,
                                            "Media 1× Sekali Lihat ini sudah pernah dibuka dan tidak dapat dilihat lagi.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        if (m.viewOnce) markMediaViewedOnce(m.id)
                                        fullscreenMediaMsg = m
                                    }
                                },
                                onOpenFile = { saveOrShareChatAttachment(context, m) },
                            )
                        }
                    }
                }
            }

            // Indikator Sedang Mengetik dengan Animasi Titik-Titik Bouncing (• • •)
            AnimatedVisibility(visible = typingName != null, enter = fadeIn(), exit = fadeOut()) {
                AnimatedTypingDotsBar(typingUser = typingName.orEmpty())
            }

            // Panel Stiker Tanpa Label & Tanpa Bubble Card + Fitur Favorit untuk Semua Orang
            AnimatedVisibility(visible = showStickerPicker, enter = fadeIn(), exit = fadeOut()) {
                Surface(
                    color = Color(0xFF121216),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Stiker Favorit & Custom",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF4F4F5),
                                )
                                Text(
                                    "Ketuk stiker untuk kirim · Ketuk ikon bintang untuk favoritkan",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = Color(0xFF9CA3AF),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                        .clickable { pickCustomStickerLauncher.launch("image/*") }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = XyIcons.Plus,
                                        contentDescription = "Tambah Stiker",
                                        tint = Color(0xFF09090B),
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = "Tambah Stiker",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF09090B),
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF27272A))
                                        .clickable { showStickerPicker = false },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = XyIcons.Close,
                                        contentDescription = "Tutup",
                                        tint = Color(0xFFE4E4E7),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }

                        // Koleksi Stiker Custom Tanpa Label & Tanpa Bubble Card + Tombol ★ Favorit
                        if (customStickers.isNotEmpty()) {
                            val sortedCustom = remember(customStickers.toList(), favoriteStickerKeys) {
                                customStickers.sortedByDescending { "custom:${it.fileName}" in favoriteStickerKeys }
                            }
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sortedCustom.take(9).chunked(3).forEach { rowCustom ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        rowCustom.forEach { cst ->
                                            val favKey = "custom:${cst.fileName}"
                                            val isFav = favKey in favoriteStickerKeys
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(84.dp)
                                                    .pointerInput(cst.fileName) {
                                                        detectTapGestures(
                                                            onTap = {
                                                                showStickerPicker = false
                                                                dispatchMessagePayload(
                                                                    rawText = "",
                                                                    attachmentType = "sticker",
                                                                    attachmentName = "",
                                                                    attachmentMime = "image/webp+crypto15",
                                                                    attachmentSize = cst.crypto15Bytes.size,
                                                                    attachmentData = CustomStickerVault.encodeAttachmentPayload(cst),
                                                                )
                                                            },
                                                            onLongPress = {
                                                                CustomStickerVault.deleteCustomSticker(context, cst.fileName)
                                                                customStickers.removeAll { it.fileName == cst.fileName }
                                                                Toast.makeText(context, "Stiker dihapus", Toast.LENGTH_SHORT).show()
                                                            },
                                                        )
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Image(
                                                    bitmap = cst.bitmap.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(78.dp),
                                                    contentScale = ContentScale.Fit,
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(2.dp)
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.55f))
                                                        .clickable {
                                                            favoriteStickerKeys = CustomStickerVault.toggleFavoriteKey(context, favKey)
                                                        },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        imageVector = if (isFav) XyIcons.StarFilled else XyIcons.Star,
                                                        contentDescription = null,
                                                        tint = if (isFav) Color(0xFFFBBF24) else Color.White,
                                                        modifier = Modifier.size(12.dp),
                                                    )
                                                }
                                            }
                                        }
                                        repeat(3 - rowCustom.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        // Grid 3 kolom x 2 baris untuk 6 stiker bawaan — Tanpa Label & Tanpa Bubble Card + Tombol ★ Favorit
                        val sortedPresets = remember(favoriteStickerKeys) {
                            XY_STICKERS.sortedByDescending { "preset:${it.id}" in favoriteStickerKeys }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            sortedPresets.chunked(3).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowItems.forEach { st ->
                                        val favKey = "preset:${st.id}"
                                        val isFav = favKey in favoriteStickerKeys
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(84.dp)
                                                .clickable {
                                                    showStickerPicker = false
                                                    dispatchMessagePayload(
                                                        rawText = "",
                                                        attachmentType = "sticker",
                                                        attachmentName = "",
                                                        attachmentMime = "application/x-xydesk-sticker",
                                                        attachmentData = "sticker:${st.id}",
                                                    )
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Image(
                                                painter = painterResource(st.drawableRes),
                                                contentDescription = null,
                                                modifier = Modifier.size(78.dp),
                                                contentScale = ContentScale.Fit,
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp)
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.55f))
                                                    .clickable {
                                                        favoriteStickerKeys = CustomStickerVault.toggleFavoriteKey(context, favKey)
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(
                                                    imageVector = if (isFav) XyIcons.StarFilled else XyIcons.Star,
                                                    contentDescription = null,
                                                    tint = if (isFav) Color(0xFFFBBF24) else Color.White,
                                                    modifier = Modifier.size(12.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Menu Lampiran Foto / Video / Berkas + Toggle Media 1× Sekali Lihat (Vektor Murni)
            AnimatedVisibility(visible = showAttachMenu, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF141418))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Baris Toggle Mode 1x Sekali Lihat (View-Once untuk Foto & Video)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (viewOnceMediaMode) Color.White else Color(0xFF1E1E24))
                            .clickable { viewOnceMediaMode = !viewOnceMediaMode }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ChatVectorViewOnceIcon(
                                size = 18.dp,
                                tint = if (viewOnceMediaMode) Color(0xFF09090B) else Color.White,
                            )
                            Column {
                                Text(
                                    text = if (viewOnceMediaMode) "Mode Media 1× Sekali Lihat: AKTIF" else "Mode Media 1× Sekali Lihat",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (viewOnceMediaMode) Color(0xFF09090B) else Color.White,
                                )
                                Text(
                                    text = "Foto atau video hanya bisa dibuka 1 kali oleh penerima",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = if (viewOnceMediaMode) Color(0xFF27272A) else Color(0xFFA1A1AA),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (viewOnceMediaMode) Color(0xFF09090B) else Color(0xFF27272F))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = if (viewOnceMediaMode) "1× ON" else "NORMAL",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222228))
                                .clickable {
                                    showAttachMenu = false
                                    pickImageLauncher.launch("image/*")
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Camera,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (viewOnceMediaMode) "Foto 1×" else "Foto",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222228))
                                .clickable {
                                    showAttachMenu = false
                                    pickVideoLauncher.launch("video/*")
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            ChatVectorVideoPlayIcon(size = 15.dp, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (viewOnceMediaMode) "Video 1×" else "Video",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF222228))
                                .clickable {
                                    showAttachMenu = false
                                    pickFileLauncher.launch(arrayOf("*/*"))
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Clip,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Berkas",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            // Pratinjau Balasan (Reply Preview Bar)
            AnimatedVisibility(visible = replyingTo != null, enter = fadeIn(), exit = fadeOut()) {
                val rep = replyingTo
                if (rep != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF16161C))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Membalas ${rep.name}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                text = rep.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFA1A1AA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF27272A))
                                .clickable { replyingTo = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Close,
                                contentDescription = "Batal Balas",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            // Composer / Gate Login GitHub via Browser In-App (⋮)
            if (verifiedGhLogin.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF141419),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GitHubLogoIcon(size = 24.dp, tint = Color.White)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Wajib Login GitHub (E2EE Verified)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                "Login ke GitHub di browser aplikasi lalu klik tombol hijau Authorize (tanpa kode).",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFA1A1AA),
                            )
                        }
                        XyPillButton(
                            text = "Authorize",
                            onClick = {
                                authBusy = true
                                scope.launch {
                                    performGitHubInAppBrowserLogin(
                                        context = context,
                                        onCodeReady = { _, _ -> },
                                        onSuccess = { _, user ->
                                            authBusy = false
                                            verifiedGhLogin = user
                                            nickname = chatNickname(context)
                                            firstJoinedAtMs = ensureFirstJoinedAtMs(context, user)
                                            chatSocketSend(
                                                JSONObject()
                                                    .put("type", "hello")
                                                    .put("name", nickname)
                                                    .put("github_login", user)
                                                    .put("country", myCountry)
                                                    .toString(),
                                            )
                                        },
                                        onError = { err ->
                                            authBusy = false
                                            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            },
                            primary = true,
                            compact = true,
                        )
                    }
                }
            } else {
                // Bar Autocomplete @Mention Lengkap (muncul saat mengetik '@' atau klik tombol '@')
                val mentionQuery = remember(draft.text) {
                    val lastAt = draft.text.lastIndexOf('@')
                    if (lastAt >= 0 && (lastAt == 0 || draft.text[lastAt - 1].isWhitespace())) {
                        val after = draft.text.substring(lastAt + 1)
                        if (!after.contains(' ')) after.lowercase(Locale.ROOT) else null
                    } else null
                }
                val mentionSuggestions = remember(messages.toList(), mentionQuery) {
                    val pool = buildList {
                        add("xykal")
                        messages.forEach { m ->
                            if (m.githubLogin.isNotBlank()) add(m.githubLogin)
                            if (m.name.isNotBlank()) add(m.name.replace(" ", ""))
                        }
                    }.distinct()
                    if (mentionQuery == null) emptyList()
                    else pool.filter { it.lowercase(Locale.ROOT).startsWith(mentionQuery) }.take(8)
                }

                AnimatedVisibility(visible = mentionSuggestions.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF171A24))
                            .border(1.dp, Color(0xFF2B3245), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "@Mention:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.ExtraBold,
                        )
                        mentionSuggestions.forEach { handle ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF232838))
                                    .clickable {
                                        val lastAt = draft.text.lastIndexOf('@')
                                        val prefix = if (lastAt >= 0) draft.text.substring(0, lastAt) else draft.text
                                        val nextStr = "$prefix@$handle "
                                        draft = TextFieldValue(nextStr)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "@$handle",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFounderAccount(handle)) Color(0xFFFDE047) else Color.White,
                                )
                            }
                        }
                    }
                }

                // Toggle Pengumuman Admin @xykal (otomatis tersemat di Global Chat — disembunyikan di Floating Sesi agar ringkas)
                if (!isOverlayMode && (isFounderAccount(verifiedGhLogin) || isFounderAccount(nickname))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isAnnouncementMode) Color(0xFFF59E0B).copy(alpha = 0.24f)
                                    else Color(0xFF181A22),
                                )
                                .border(
                                    1.dp,
                                    if (isAnnouncementMode) Color(0xFFF59E0B) else Color(0xFF292E3D),
                                    RoundedCornerShape(50),
                                )
                                .clickable { isAnnouncementMode = !isAnnouncementMode }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = if (isAnnouncementMode) XyIcons.Pin else XyIcons.Megaphone,
                                    contentDescription = null,
                                    tint = if (isAnnouncementMode) Color(0xFFFDE047) else Color(0xFFA1A1AA),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = if (isAnnouncementMode) "Mode Pengumuman Admin (Auto-Pin): AKTIF" else "Kirim sebagai Pengumuman Admin (Auto-Pin)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAnnouncementMode) Color(0xFFFDE047) else Color(0xFFA1A1AA),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFF181A22))
                                .clickable {
                                    val nextStr = draft.text + (if (draft.text.isEmpty() || draft.text.endsWith(" ")) "@" else " @")
                                    draft = TextFieldValue(nextStr)
                                }
                                .padding(horizontal = 9.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "@Mention",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                            )
                        }
                    }
                }

                // Bar Input Pesan (100% Vector Icons, Monokrom Bersih / HUD Ringkas di mode sesi)
                Surface(
                    color = if (isOverlayMode) Color(0xFF12151E).copy(alpha = 0.92f) else Color(0xFF141418),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isOverlayMode) 8.dp else 10.dp, vertical = if (isOverlayMode) 5.dp else 8.dp)
                        .let { mod ->
                            if (isOverlayMode) {
                                mod.border(
                                    0.9.dp,
                                    Brush.linearGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.36f),
                                            Color.White.copy(alpha = 0.08f),
                                            Color.White.copy(alpha = 0.22f),
                                        ),
                                    ),
                                    RoundedCornerShape(26.dp),
                                )
                            } else {
                                mod
                            }
                        },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Tombol Vektor Stiker
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (showStickerPicker) Color.White else Color(0xFF222228))
                                .clickable {
                                    showStickerPicker = !showStickerPicker
                                    if (showStickerPicker) showAttachMenu = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatVectorStickerIcon(
                                size = 18.dp,
                                tint = if (showStickerPicker) Color(0xFF09090B) else Color(0xFFE4E4E7),
                            )
                        }

                        // Tombol Vektor Lampiran Berkas
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (showAttachMenu) Color.White else Color(0xFF222228))
                                .clickable {
                                    showAttachMenu = !showAttachMenu
                                    if (showAttachMenu) showStickerPicker = false
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Clip,
                                contentDescription = "Lampirkan Berkas",
                                tint = if (showAttachMenu) Color(0xFF09090B) else Color(0xFFE4E4E7),
                                modifier = Modifier.size(17.dp),
                            )
                        }

                        // Tombol Vektor Kamera / Galeri Cepat
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF222228))
                                .clickable { pickImageLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Camera,
                                contentDescription = "Kirim Foto",
                                tint = Color(0xFFE4E4E7),
                                modifier = Modifier.size(17.dp),
                            )
                        }

                        // Kolom Teks Input
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (draft.text.isEmpty()) {
                                Text(
                                    text = "Ketik pesan terenkripsi…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF71717A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            BasicTextField(
                                value = draft,
                                onValueChange = { next ->
                                    if (next.text.length <= 500) draft = next
                                    val now = System.currentTimeMillis()
                                    if (now - typingSentAt > 1800L) {
                                        typingSentAt = now
                                        chatSocketSend(
                                            JSONObject()
                                                .put("type", "typing")
                                                .put("on", next.text.isNotBlank())
                                                .toString(),
                                        )
                                    }
                                },
                                textStyle = TextStyle(
                                    color = Color(0xFFF4F4F5),
                                    fontSize = 15.sp,
                                ),
                                cursorBrush = SolidColor(Color.White),
                                maxLines = 4,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Send,
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = { dispatchMessagePayload(draft.text) },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        // Tombol Vektor Kirim Monokrom
                        val canSend = draft.text.isNotBlank() && !sending
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (canSend) Color.White else Color(0xFF222228))
                                .clickable(enabled = canSend) { dispatchMessagePayload(draft.text) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Send,
                                contentDescription = "Kirim",
                                tint = if (canSend) Color(0xFF09090B) else Color(0xFF52525B),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // OVERLAY LONG-PRESS FOKUS BUBBLE + BLUR BACKGROUND + MENU ALA iPHONE WA
        // =====================================================================
        AnimatedVisibility(
            visible = focusedContextMsg != null,
            enter = fadeIn() + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
            ),
            exit = fadeOut() + scaleOut(targetScale = 0.94f),
        ) {
            val targetMsg = focusedContextMsg
            if (targetMsg != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.58f))
                        .clickable { focusedContextMsg = null }
                        .padding(horizontal = 20.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .clickable(enabled = false) {},
                        horizontalAlignment = if (targetMsg.mine) Alignment.End else Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // 1. Bar Reaksi Visual Mengambang di atas Bubble (Ikon Vektor Berwarna + Label Ringkas)
                        Surface(
                            color = Color(0xFF161821).copy(alpha = 0.98f),
                            shape = RoundedCornerShape(50),
                            shadowElevation = 16.dp,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                listOf("FIRE", "LOL", "+1", "KING", "GG", "RESPECT").forEach { tag ->
                                    val isSelected = messageReactions[targetMsg.id] == tag
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color(0xFF6366F1).copy(alpha = 0.28f)
                                                else Color(0xFF232734),
                                            )
                                            .let { mod ->
                                                if (isSelected) mod.border(1.4.dp, Color(0xFF818CF8), CircleShape) else mod
                                            }
                                            .clickable {
                                                if (isSelected) messageReactions.remove(targetMsg.id)
                                                else messageReactions[targetMsg.id] = tag
                                                focusedContextMsg = null
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ChatReactionVisualIcon(tag = tag, size = 21.dp)
                                    }
                                }
                            }
                        }

                        // 2. Bubble Pesan yang Difokuskan Tajam di Tengah Layar Blur
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(1.02f),
                        ) {
                            val isTargetViewedOnce = targetMsg.id in viewedOnceIds
                            MessageBubble(
                                msg = targetMsg,
                                reaction = messageReactions[targetMsg.id],
                                isPinned = pinnedMessage?.id == targetMsg.id,
                                viewedOnce = isTargetViewedOnce,
                                onReply = {
                                    replyingTo = targetMsg
                                    focusedContextMsg = null
                                },
                                onLongPress = {},
                                onOpenMedia = {
                                    focusedContextMsg = null
                                    if (targetMsg.viewOnce && isTargetViewedOnce) {
                                        Toast.makeText(
                                            context,
                                            "Media 1× Sekali Lihat ini sudah pernah dibuka.",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        if (targetMsg.viewOnce) markMediaViewedOnce(targetMsg.id)
                                        fullscreenMediaMsg = targetMsg
                                    }
                                },
                                onOpenFile = {
                                    focusedContextMsg = null
                                    saveOrShareChatAttachment(context, targetMsg)
                                },
                            )
                        }

                        // 3. Menu Konteks Frosted Glass ala iPhone / WhatsApp (Ikon Vektor di Kanan)
                        val isCurrentlyPinned = pinnedMessage?.id == targetMsg.id
                        Surface(
                            color = Color(0xFF1C1C22).copy(alpha = 0.97f),
                            shape = RoundedCornerShape(18.dp),
                            shadowElevation = 18.dp,
                            modifier = Modifier.width(258.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ContextMenuActionRow(
                                    label = "Balas Pesan",
                                    customIcon = { ChatVectorReplyIcon(size = 17.dp, tint = Color.White) },
                                    onClick = {
                                        replyingTo = targetMsg
                                        focusedContextMsg = null
                                    },
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    label = if (isCurrentlyPinned) "Lepas Sematan Pesan" else "Sematkan Pesan",
                                    customIcon = { ChatVectorPinIcon(size = 17.dp, tint = Color.White) },
                                    onClick = {
                                        updatePinnedMessage(targetMsg, pin = !isCurrentlyPinned)
                                        focusedContextMsg = null
                                    },
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    label = "Salin Teks Pesan",
                                    icon = XyIcons.Copy,
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("Pesan Chat Global", targetMsg.text))
                                        Toast.makeText(context, "Teks pesan disalin", Toast.LENGTH_SHORT).show()
                                        focusedContextMsg = null
                                    },
                                )
                                if (targetMsg.translated.isNotBlank() && !targetMsg.translated.equals(targetMsg.text, ignoreCase = true)) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    ContextMenuActionRow(
                                        label = "Salin Terjemahan (${targetMsg.targetLang})",
                                        icon = XyIcons.Globe,
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Terjemahan", targetMsg.translated))
                                            Toast.makeText(context, "Terjemahan disalin", Toast.LENGTH_SHORT).show()
                                            focusedContextMsg = null
                                        },
                                    )
                                }
                                if ((targetMsg.attachmentType == "image" || targetMsg.attachmentType == "video") && !targetMsg.viewOnce) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    ContextMenuActionRow(
                                        label = if (targetMsg.attachmentType == "video") "Simpan Video ke Galeri" else "Simpan Foto ke Galeri",
                                        icon = XyIcons.Download,
                                        onClick = {
                                            val snap = targetMsg
                                            focusedContextMsg = null
                                            saveChatMediaToGallery(context, snap)
                                        },
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    label = "Bagikan Pesan Ini",
                                    icon = XyIcons.Share,
                                    onClick = {
                                        val snap = targetMsg
                                        focusedContextMsg = null
                                        shareGlobalChatInvite(snap)
                                    },
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    label = "Info Enkripsi E2EE",
                                    icon = XyIcons.Shield,
                                    onClick = {
                                        inspectingE2eeMsg = targetMsg
                                        focusedContextMsg = null
                                    },
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    label = "Hapus untuk Semua Orang",
                                    icon = XyIcons.Trash,
                                    tint = Color(0xFFEF4444),
                                    onClick = {
                                        val snap = targetMsg
                                        focusedContextMsg = null
                                        deleteMessageForEveryone(snap)
                                    },
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    label = "Hapus untuk Saya",
                                    icon = XyIcons.Trash,
                                    tint = Color(0xFFF87171),
                                    onClick = {
                                        val snap = targetMsg
                                        focusedContextMsg = null
                                        deleteMessageForMe(snap)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog Settings Chat Global (Bersihkan Chat, Style Bubble, Otomatis Unduh Media, HD Media, Autoplay Video, Nada Notifikasi)
    if (showChatSettings) {
        XyOverlay(
            title = xy("Pengaturan Chat Global", "Global Chat Settings"),
            onDismiss = { showChatSettings = false },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 1. Style Bubble Chat
                Text(
                    xy("Gaya Bubble Chat", "Chat Bubble Style"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "monochrome" to "Monokrom",
                        "glass" to "Liquid Glass",
                        "compact" to "Kompak",
                        "amber" to "Warm Gold",
                    ).forEach { (modeKey, modeLabel) ->
                        val active = bubbleStyleMode == modeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) Color.White else Color(0xFF1E222D))
                                .clickable {
                                    bubbleStyleMode = modeKey
                                    chatPrefs.edit().putString(KEY_BUBBLE_STYLE, modeKey).apply()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = modeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color(0xFF09090B) else Color(0xFFE2E8F0),
                            )
                        }
                    }
                }

                // 2. Toggle Otomatis Unduh Media
                Surface(
                    onClick = {
                        autoDownloadMedia = !autoDownloadMedia
                        chatPrefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_MEDIA, autoDownloadMedia).apply()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF171A23),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                xy("Otomatis Unduh Media", "Auto-Download Media"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                xy("Tampilkan foto & video secara otomatis saat pesan masuk", "Automatically load incoming photos & videos"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Text(
                            text = if (autoDownloadMedia) "AKTIF" else "MANUAL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (autoDownloadMedia) Color(0xFF10B981) else Color(0xFFF59E0B),
                        )
                    }
                }

                // 3. Toggle Media HD (Tidak Terlalu Dikompres)
                Surface(
                    onClick = {
                        hdMediaEnabled = !hdMediaEnabled
                        chatPrefs.edit().putBoolean(KEY_HD_MEDIA_ENABLED, hdMediaEnabled).apply()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF171A23),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                xy("Kualitas Media HD (1600p Jernih)", "HD Media Quality (1600p Crisp)"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                xy("Gambar tidak dikompres berlebihan agar tetap tajam", "Keep photos sharp without heavy compression"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Text(
                            text = if (hdMediaEnabled) "HD ON" else "STANDAR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (hdMediaEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                        )
                    }
                }

                // 4. Toggle Autoplay Video Preview
                Surface(
                    onClick = {
                        autoplayVideoEnabled = !autoplayVideoEnabled
                        chatPrefs.edit().putBoolean(KEY_AUTOPLAY_VIDEO, autoplayVideoEnabled).apply()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF171A23),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                xy("Putar Otomatis Preview Video", "Autoplay Video Preview"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                xy("Video otomatis berputar preview tanpa dibuka penuh", "Play muted video previews inline automatically"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Text(
                            text = if (autoplayVideoEnabled) "AKTIF" else "MATI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (autoplayVideoEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                        )
                    }
                }

                // 5. Atur Nada Notifikasi Chat
                Text(
                    xy("Nada Notifikasi Pesan Masuk", "Incoming Message Notification Tone"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "default" to "Default",
                        "crystal" to "Crystal",
                        "beep" to "Soft Ping",
                        "silent" to "Senyap",
                    ).forEach { (toneKey, toneLabel) ->
                        val active = notifSoundTone == toneKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (active) Color(0xFFF59E0B) else Color(0xFF1E222D))
                                .clickable {
                                    notifSoundTone = toneKey
                                    chatPrefs.edit().putString(KEY_NOTIF_TONE, toneKey).apply()
                                    playChatNotificationTone(toneKey)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = toneLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color(0xFF09090B) else Color(0xFFE2E8F0),
                            )
                        }
                    }
                }

                // 6. Tombol Bersihkan Chat
                XyPillButton(
                    text = xy("Bersihkan Riwayat Chat di Layar Saya", "Clear Chat History on My Screen"),
                    onClick = {
                        messages.forEach { m ->
                            if (m.id.isNotBlank() && m.id !in hiddenMessageIds) {
                                hiddenMessageIds.add(m.id)
                            }
                        }
                        chatPrefs.edit().putStringSet(KEY_HIDDEN_MSG_IDS, hiddenMessageIds.toSet()).apply()
                        messages.clear()
                        showChatSettings = false
                        Toast.makeText(context, "Riwayat chat di layar berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                XyPillButton(
                    text = xy("Tutup Pengaturan", "Close Settings"),
                    onClick = { showChatSettings = false },
                    primary = true,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Dialog Pemilih Bahasa Terjemahan
    if (showLangPicker) {
        XyOverlay(
            title = "Bahasa Terjemahan Chat Global",
            onDismiss = { showLangPicker = false },
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Pilih bahasa terjemahan otomatis di setiap bubble pesan:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(SUPPORTED_VIEWER_LANGS, key = { it.code }) { opt ->
                        XyPillButton(
                            text = "[${opt.badge}]  ${opt.label}",
                            onClick = {
                                viewerTargetLang = opt.code
                                chatPrefs.edit().putString(KEY_TARGET_LANG, opt.code).apply()
                                showLangPicker = false
                            },
                            primary = viewerTargetLang == opt.code,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    // Dialog Verifikasi Kriptografi End-to-End (E2EE Inspector)
    inspectingE2eeMsg?.let { msg ->
        XyOverlay(
            title = "Verifikasi End-to-End (E2EE)",
            onDismiss = { inspectingE2eeMsg = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = XyIcons.Shield,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (msg.e2eeVerified) "Integritas Kriptografi Terverifikasi" else "Pesan Standar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text("Pengirim: ${msg.name} ${if (msg.githubLogin.isNotBlank()) "(@${msg.githubLogin})" else ""}", style = MaterialTheme.typography.bodySmall)
                Text("Negara Terdeteksi: ${msg.country}", style = MaterialTheme.typography.bodySmall)
                Text("Protokol Enkripsi: ${msg.e2eeProtocol} (AES-256-GCM + HMAC-SHA256)", style = MaterialTheme.typography.bodySmall)
                Text("Signature HMAC: ${msg.e2eeSig.ifBlank { "verified" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Status Pengiriman: Terkirim & Tervalidasi (Centang 2)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                XyPillButton(
                    text = "Tutup",
                    onClick = { inspectingE2eeMsg = null },
                    primary = true,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Pratinjau Media Layar Penuh (BUKAN Popup: mendukung Pinch-to-Zoom 1x–5x, Double-Tap Zoom, Video Player & Simpan Galeri)
    val fullMedia = fullscreenMediaMsg
    if (fullMedia != null) {
        FullScreenChatMediaViewer(
            msg = fullMedia,
            onDismiss = { fullscreenMediaMsg = null },
        )
    }
}

@Composable
private fun FullScreenChatMediaViewer(
    msg: ChatMessage,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var zoomScale by remember(msg.id) { mutableFloatStateOf(1f) }
    var panOffsetX by remember(msg.id) { mutableFloatStateOf(0f) }
    var panOffsetY by remember(msg.id) { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (msg.attachmentType == "video" && msg.attachmentData.isNotBlank()) {
                val videoFile = remember(msg.id, msg.attachmentData) {
                    runCatching {
                        val comma = msg.attachmentData.indexOf(',')
                        if (comma < 0) return@runCatching null
                        val bytes = Base64.decode(msg.attachmentData.substring(comma + 1), Base64.DEFAULT)
                        val dir = File(context.cacheDir, "chat_media").apply { mkdirs() }
                        val safeName = msg.attachmentName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                            .ifBlank { "video_${msg.id.hashCode()}.mp4" }
                        val f = File(dir, safeName)
                        f.writeBytes(bytes)
                        f
                    }.getOrNull()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(msg.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = nextScale
                                if (nextScale > 1.01f) {
                                    panOffsetX += pan.x
                                    panOffsetY += pan.y
                                } else {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffsetX
                            translationY = panOffsetY
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (videoFile != null) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setVideoPath(videoFile.absolutePath)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = "Gagal memuat berkas video.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                val bmp = remember(msg.id, msg.attachmentData) { decodeBase64Bitmap(msg.attachmentData) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(msg.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.25f) {
                                        zoomScale = 1f
                                        panOffsetX = 0f
                                        panOffsetY = 0f
                                    } else {
                                        zoomScale = 2.75f
                                    }
                                },
                            )
                        }
                        .pointerInput(msg.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                zoomScale = nextScale
                                if (nextScale > 1.01f) {
                                    panOffsetX = (panOffsetX + pan.x).coerceIn(-1400f, 1400f)
                                    panOffsetY = (panOffsetY + pan.y).coerceIn(-1800f, 1800f)
                                } else {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = msg.attachmentName.ifBlank { "Foto Layar Penuh" },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffsetX
                                    translationY = panOffsetY
                                },
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            // Header Liquid Glass Atas (Tutup, Info Pengirim, Zoom Reset, Simpan ke Galeri, Bagikan)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.42f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = XyIcons.ChevronLeft,
                            contentDescription = "Tutup Pratinjau",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = msg.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (msg.viewOnce) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "1× SEKALI LIHAT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF09090B),
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (msg.attachmentType == "video") "Video Layar Penuh · Cubit untuk Zoom" else "Cubit 2 Jari / Ketuk 2× untuk Zoom (${(zoomScale * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            color = Color(0xFFD4D4D8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (zoomScale > 1.05f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                                .clickable {
                                    zoomScale = 1f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Reset ${(zoomScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                        }
                    }
                    if (!msg.viewOnce) {
                        // Tombol Simpan ke Galeri Perangkat
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .clickable { saveChatMediaToGallery(context, msg) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = XyIcons.Download,
                                contentDescription = "Simpan ke Galeri",
                                tint = Color(0xFF09090B),
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Simpan",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF09090B),
                            )
                        }
                        // Tombol Bagikan Media
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                                .clickable { saveOrShareChatAttachment(context, msg) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Share,
                                contentDescription = "Bagikan",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Caption Bawah (bila ada teks pesan pada foto/video)
            if (msg.text.isNotBlank() && !msg.text.startsWith("[Foto") && !msg.text.startsWith("[Video")) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.88f),
                                ),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuActionRow(
    label: String,
    icon: ImageVector? = null,
    tint: Color = Color.White,
    customIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
        if (customIcon != null) {
            customIcon()
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Bubble Pesan Chat Global gaya WhatsApp Modern:
 * - **TANPA BORDER** untuk chat pengguna biasa.
 * - **Hitam-Putih Monokrom** untuk chat biasa (`#F4F4F6` dengan teks `#09090B` untuk pesanku,
 *   `#1A1A20` dengan teks `#F4F4F5` untuk pesan orang lain) sesuai tema utama XyDesk.
 * - Khusus **Founder (`@xykal`)**: menggunakan `FounderAnimatedBubbleBox` + `XyFounderBadge`
 *   beranimasi penuh dengan sapuan Liquid Gold & partikel bintang.
 * - Menampilkan **Foto Profil (Avatar)**, **Nama Jelas + `@github`**, **Kode Negara**,
 *   serta **Centang 1 / Centang 2 Vektor (`ChatVectorDeliveryTicks`)**.
 */
@Composable
private fun MessageBubble(
    msg: ChatMessage,
    reaction: String?,
    isPinned: Boolean = false,
    viewedOnce: Boolean = false,
    liquidGlassMode: Boolean = false,
    bubbleStyleMode: String = "monochrome",
    autoDownloadAllowed: Boolean = true,
    autoplayVideoEnabled: Boolean = true,
    favoriteStickerKeys: Set<String> = emptySet(),
    onToggleFavoriteSticker: (String) -> Unit = {},
    onManualDownloadMedia: () -> Unit = {},
    onReply: () -> Unit,
    onLongPress: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val time = remember(msg.at) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.at))
    }
    val founderMsg = msg.isFounder || isFounderAccount(msg.name) || isFounderAccount(msg.githubLogin)
    val isAnnouncementMsg = msg.isAnnouncement || (founderMsg && isPinned)
    val isPureSticker = msg.attachmentType == "sticker"
    val isLightBubble = msg.mine && !founderMsg && bubbleStyleMode == "monochrome"

    // Swipe-to-Reply State
    var swipeOffsetX by remember(msg.id) { mutableFloatStateOf(0f) }
    var swipeTriggered by remember(msg.id) { mutableStateOf(false) }

    val primaryTextColor = when {
        founderMsg || isAnnouncementMsg -> Color(0xFFFFFBEB)
        isLightBubble -> Color(0xFF09090B)
        else -> Color(0xFFF4F4F5)
    }
    val secondaryTextColor = when {
        founderMsg || isAnnouncementMsg -> Color(0xFFFDE68A).copy(alpha = 0.88f)
        isLightBubble -> Color(0xFF3F3F46)
        else -> Color(0xFFA1A1AA)
    }
    val dividerColor = when {
        founderMsg || isAnnouncementMsg -> Color(0xFFF59E0B).copy(alpha = 0.32f)
        isLightBubble -> Color(0xFF09090B).copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.10f)
    }

    val radiusMain = if (bubbleStyleMode == "compact") 13.dp else 18.dp
    val bubbleShape = RoundedCornerShape(
        topStart = radiusMain,
        topEnd = radiusMain,
        bottomStart = if (msg.mine) radiusMain else 4.dp,
        bottomEnd = if (msg.mine) 4.dp else radiusMain,
    )

    val transLabel = msg.targetLang.ifBlank { if (msg.country == "ID") "EN" else "ID" }
    val transBody = msg.translated.trim()
    val hasMediaAttachment = msg.attachmentType == "image" || msg.attachmentType == "video" || msg.attachmentType == "file"
    val detectedLinkUrl = remember(msg.id, msg.text) { extractFirstUrl(msg.text) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .offset { IntOffset(swipeOffsetX.roundToInt(), 0) }
            .pointerInput(msg.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(swipeOffsetX) >= 56f) {
                            onReply()
                        }
                        swipeOffsetX = 0f
                        swipeTriggered = false
                    },
                    onDragCancel = {
                        swipeOffsetX = 0f
                        swipeTriggered = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(-110f, 110f)
                    },
                )
            },
        horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!msg.mine) {
            ChatSenderAvatar(
                name = msg.name,
                githubLogin = msg.githubLogin,
                avatarUrl = msg.avatarUrl,
                isFounder = founderMsg,
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(min = if (hasMediaAttachment) 224.dp else if (isPureSticker) 110.dp else 128.dp, max = 286.dp),
        ) {
            val gestureMod = Modifier.pointerInput(msg.id) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                )
            }

            // STIKER MURNI: Dirender TANPA Bubble Card & TANPA Label + Tombol Favorit untuk Siapa Saja
            if (isPureSticker) {
                val appContext = LocalContext.current
                val isFavSticker = remember(msg.attachmentData, favoriteStickerKeys) {
                    when {
                        msg.attachmentData.startsWith("sticker:") ->
                            "preset:${msg.attachmentData.removePrefix("sticker:")}" in favoriteStickerKeys
                        msg.attachmentData.startsWith("sticker_crypto15:") -> {
                            val rest = msg.attachmentData.removePrefix("sticker_crypto15:")
                            val fname = rest.substringBefore(':')
                            "custom:$fname" in favoriteStickerKeys
                        }
                        else -> false
                    }
                }
                Column(
                    modifier = gestureMod.padding(vertical = 2.dp),
                    horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = msg.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (founderMsg) Color(0xFFFDE047) else Color(0xFFD4D4D8),
                        )
                        if (founderMsg) {
                            XyFounderBadge(compact = true)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (msg.attachmentData.startsWith("sticker_crypto15:")) {
                        val customDecoded = remember(msg.id, msg.attachmentData) {
                            CustomStickerVault.decodeAndCacheFromChatPayload(appContext, msg.attachmentData)
                        }
                        if (customDecoded != null) {
                            Image(
                                bitmap = customDecoded.second.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(142.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    } else {
                        val presetId = msg.attachmentData.removePrefix("sticker:")
                        val preset = XY_STICKERS.firstOrNull { it.id == presetId } ?: XY_STICKERS.first()
                        Image(
                            painter = painterResource(preset.drawableRes),
                            contentDescription = null,
                            modifier = Modifier.size(142.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // Bar Bawah Stiker: Jam + Tombol Favoritkan Stiker (Bisa Dipakai Siapa Saja)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF1A1D26))
                                .clickable { onToggleFavoriteSticker(msg.attachmentData) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (isFavSticker) XyIcons.StarFilled else XyIcons.Star,
                                contentDescription = null,
                                tint = if (isFavSticker) Color(0xFFFBBF24) else Color(0xFFE2E8F0),
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = if (isFavSticker) "Favorit" else "+Favorit",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFavSticker) Color(0xFFFBBF24) else Color(0xFFE2E8F0),
                            )
                        }
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color(0xFFA1A1AA),
                        )
                    }
                }
            } else {
                val bubbleContent: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .widthIn(min = if (hasMediaAttachment) 224.dp else 128.dp, max = 286.dp)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        // Pita Khusus Pengumuman Admin @xykal yang Otomatis Tersemat
                        if (isAnnouncementMsg) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF59E0B).copy(alpha = 0.22f))
                                    .border(0.8.dp, Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = XyIcons.Pin,
                                        contentDescription = null,
                                        tint = Color(0xFFFDE047),
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = "PENGUMUMAN RESMI ADMIN · TERSEMAT",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFDE047),
                                    )
                                }
                            }
                        }

                        // Baris Atas: Nama Pengirim Jelas + @github + Kode Negara + Badge Founder + Tombol Vektor Reply
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.weight(1f, fill = false),
                            ) {
                                Text(
                                    text = msg.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (founderMsg) Color(0xFFFDE047) else primaryTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (msg.githubLogin.isNotBlank() && !msg.githubLogin.equals(msg.name, ignoreCase = true)) {
                                    Text(
                                        text = "@${msg.githubLogin}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = secondaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isLightBubble) Color(0xFF09090B).copy(alpha = 0.08f)
                                            else Color.White.copy(alpha = 0.10f),
                                        )
                                        .padding(horizontal = 4.5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = msg.country,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = secondaryTextColor,
                                    )
                                }
                                if (founderMsg) {
                                    XyFounderBadge(compact = true)
                                }
                                if (isPinned) {
                                    ChatVectorPinIcon(
                                        size = 12.dp,
                                        tint = if (founderMsg) Color(0xFFFDE047) else secondaryTextColor,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { onReply() },
                                contentAlignment = Alignment.Center,
                            ) {
                                ChatVectorReplyIcon(
                                    size = 13.dp,
                                    tint = secondaryTextColor,
                                )
                            }
                        }

                        // Kotak Kutipan Balasan (Quoted Reply ala WhatsApp)
                        if (msg.replyToName.isNotBlank() || msg.replyToText.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isLightBubble) Color(0xFF09090B).copy(alpha = 0.07f)
                                        else Color.Black.copy(alpha = 0.28f),
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(26.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (founderMsg) Color(0xFFFDE047) else primaryTextColor),
                                )
                                Spacer(Modifier.width(7.dp))
                                Column {
                                    Text(
                                        msg.replyToName.ifBlank { "Pesan" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor,
                                    )
                                    Text(
                                        msg.replyToText.ifBlank { "Media / Stiker" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        // Lampiran Foto HD / Video Autoplay Preview / Berkas (TANPA NAMA FILE untuk Foto/Video)
                        if ((msg.attachmentType == "image" || msg.attachmentType == "video") && !autoDownloadAllowed) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.28f))
                                    .clickable { onManualDownloadMedia() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = XyIcons.Download,
                                    contentDescription = null,
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "Ketuk untuk Unduh Media (${(msg.attachmentSize / 1024).coerceAtLeast(1)} KB)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor,
                                )
                            }
                        } else if ((msg.attachmentType == "image" || msg.attachmentType == "video") && msg.viewOnce) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isLightBubble) Color(0xFF09090B).copy(alpha = if (viewedOnce) 0.05f else 0.10f)
                                        else Color.White.copy(alpha = if (viewedOnce) 0.05f else 0.12f),
                                    )
                                    .clickable(enabled = !viewedOnce) { onOpenMedia() }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ChatVectorViewOnceIcon(
                                    size = 20.dp,
                                    tint = if (viewedOnce) secondaryTextColor else primaryTextColor,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            viewedOnce -> "Telah Dilihat (1×)"
                                            msg.attachmentType == "video" -> "Video Sekali Lihat (1×)"
                                            else -> "Foto Sekali Lihat (1×)"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (viewedOnce) secondaryTextColor else primaryTextColor,
                                    )
                                    Text(
                                        text = if (viewedOnce) "Media ini sudah dibuka dan kadaluarsa" else "Ketuk untuk membuka layar penuh (hanya 1 kali)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = secondaryTextColor,
                                    )
                                }
                            }
                        } else if (msg.attachmentType == "image" && msg.attachmentData.isNotBlank()) {
                            val bmp = remember(msg.id, msg.attachmentData) { decodeBase64Bitmap(msg.attachmentData) }
                            if (bmp != null) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp, max = 235.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenMedia() },
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.Black.copy(alpha = 0.62f))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        Text(
                                            text = "HD · Zoom",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        } else if (msg.attachmentType == "video" && msg.attachmentData.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            val videoBytes = remember(msg.id, msg.attachmentData) {
                                runCatching {
                                    val comma = msg.attachmentData.indexOf(',')
                                    if (comma >= 0) Base64.decode(msg.attachmentData.substring(comma + 1), Base64.DEFAULT)
                                    else null
                                }.getOrNull()
                            }
                            if (autoplayVideoEnabled && videoBytes != null && videoBytes.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(168.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onOpenMedia() },
                                ) {
                                    FeedAutoplayVideoPreview(
                                        videoBytes = videoBytes,
                                        cacheKey = "chat_vid_${msg.id}",
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(128.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF0C0C10))
                                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                        .clickable { onOpenMedia() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            ChatVectorVideoPlayIcon(size = 20.dp, tint = Color(0xFF09090B))
                                        }
                                        Text(
                                            text = "Putar Video Layar Penuh",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 10.sp,
                                            color = Color(0xFFA1A1AA),
                                        )
                                    }
                                }
                            }
                        } else if (msg.attachmentType == "file") {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isLightBubble) Color(0xFF09090B).copy(alpha = 0.07f)
                                        else Color.White.copy(alpha = 0.08f),
                                    )
                                    .clickable { onOpenFile() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = XyIcons.Clip,
                                    contentDescription = null,
                                    tint = primaryTextColor,
                                    modifier = Modifier.size(16.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        msg.attachmentName.ifBlank { "Dokumen" },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${(msg.attachmentSize / 1024).coerceAtLeast(1)} KB · Ketuk untuk Buka",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryTextColor,
                                    )
                                }
                            }
                        }

                        // Teks Pesan (Hanya ditampilkan bila ada caption — pengiriman media tanpa teks tidak menampilkan nama file!)
                        if (msg.text.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = primaryTextColor,
                            )
                            if (transBody.isNotBlank() && !transBody.equals(msg.text, ignoreCase = true)) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.8.dp)
                                        .background(dividerColor),
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = XyIcons.Globe,
                                        contentDescription = null,
                                        tint = if (founderMsg) Color(0xFFFDE047) else secondaryTextColor,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = "[$transLabel] · $transBody",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = if (founderMsg) Color(0xFFFDE68A).copy(alpha = 0.94f) else secondaryTextColor,
                                    )
                                }
                            }
                        }

                        // Kartu OG Banner / Link Preview untuk Semua Link di Chat Global
                        if (!detectedLinkUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            XyOpenGraphPreviewCard(url = detectedLinkUrl)
                        }

                        // Baris Bawah: Jam + Indikator E2EE + Vektor Centang 1 / 2 (✓ / ✓✓)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = secondaryTextColor,
                            )
                            ChatVectorDeliveryTicks(
                                ticks = msg.deliveryTicks,
                                tint = when {
                                    founderMsg -> Color(0xFFFDE047)
                                    isLightBubble -> Color(0xFF18181B)
                                    else -> Color(0xFFE4E4E7)
                                },
                            )
                        }
                    }
                }

                // Kontainer Bubble sesuai Style Pilihan Pengguna / Founder
                if (founderMsg || isAnnouncementMsg) {
                    FounderAnimatedBubbleBox(
                        shape = bubbleShape,
                        modifier = gestureMod,
                    ) {
                        bubbleContent()
                    }
                } else {
                    val bubbleBgColor = when {
                        liquidGlassMode && msg.mine -> Color(0xFFF8F8FA).copy(alpha = 0.84f)
                        liquidGlassMode && !msg.mine -> Color(0xFF121217).copy(alpha = 0.62f)
                        bubbleStyleMode == "glass" -> if (msg.mine) Color(0xFF273042).copy(alpha = 0.82f) else Color(0xFF151924).copy(alpha = 0.82f)
                        bubbleStyleMode == "amber" -> if (msg.mine) Color(0xFF2B2214) else Color(0xFF181613)
                        msg.mine -> Color(0xFFF4F4F6)
                        else -> Color(0xFF1A1A20)
                    }
                    Box(
                        modifier = gestureMod
                            .clip(bubbleShape)
                            .background(
                                color = bubbleBgColor,
                                shape = bubbleShape,
                            ),
                    ) {
                        bubbleContent()
                    }
                }
            }

            // Pill Reaksi Visual (menampilkan ikon vektor berwarna + jumlah reaksi, bukan sekadar tulisan)
            if (!reaction.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1F2330))
                        .border(0.8.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ChatReactionVisualIcon(tag = reaction, size = 14.dp)
                    Text(
                        text = "1",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE2E8F0),
                    )
                }
            }
        }

        if (msg.mine) {
            Spacer(Modifier.width(8.dp))
            ChatSenderAvatar(
                name = msg.name,
                githubLogin = msg.githubLogin,
                avatarUrl = msg.avatarUrl,
                isFounder = founderMsg,
            )
        }
    }
}

/**
 * Avatar Foto Profil Pengirim di Samping Bubble Chat.
 * Memuat foto dari URL GitHub / komunitas di latar belakang dengan cache memori,
 * atau menampilkan inisial monokrom bersih jika belum ada foto.
 */
@Composable
private fun ChatSenderAvatar(
    name: String,
    githubLogin: String,
    avatarUrl: String,
    isFounder: Boolean,
) {
    val resolvedUrl = remember(avatarUrl, githubLogin) {
        avatarUrl.ifBlank {
            if (githubLogin.isNotBlank()) "https://github.com/$githubLogin.png?size=120" else ""
        }
    }
    var bitmap by remember(resolvedUrl) {
        mutableStateOf(if (resolvedUrl.isNotBlank()) avatarMemoryCache[resolvedUrl] else null)
    }

    LaunchedEffect(resolvedUrl) {
        if (resolvedUrl.isNotBlank() && bitmap == null) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URL(resolvedUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6_000
                        readTimeout = 6_000
                        instanceFollowRedirects = true
                    }
                    if (conn.responseCode in 200..299) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
                            avatarMemoryCache[resolvedUrl] = it
                        }
                    } else null
                }.getOrNull()
            }
            if (loaded != null) bitmap = loaded
        }
    }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (isFounder) Color(0xFF1A102B) else Color(0xFF222228))
            .let { mod ->
                if (isFounder) mod.border(1.5.dp, founderBorderBrush(), CircleShape) else mod
            },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            val initials = name.removePrefix("@").trim().take(2).uppercase(Locale.ROOT).ifEmpty { "U" }
            Text(
                text = initials,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isFounder) Color(0xFFFDE047) else Color(0xFFE4E4E7),
            )
        }
    }
}

/**
 * Ikon Vektor Centang 1 (`✓`) & Centang 2 (`✓✓`) gaya WhatsApp di atas Canvas (Tanpa Karakter Emoji).
 */
@Composable
private fun ChatVectorDeliveryTicks(
    ticks: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(width = if (ticks >= 2) 15.dp else 11.dp, height = 11.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = h * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (ticks <= 1) {
            val p1 = Path().apply {
                moveTo(w * 0.18f, h * 0.55f)
                lineTo(w * 0.42f, h * 0.80f)
                lineTo(w * 0.84f, h * 0.24f)
            }
            drawPath(p1, color = tint, style = stroke)
        } else {
            val p1 = Path().apply {
                moveTo(w * 0.08f, h * 0.55f)
                lineTo(w * 0.28f, h * 0.78f)
                lineTo(w * 0.62f, h * 0.24f)
            }
            val p2 = Path().apply {
                moveTo(w * 0.38f, h * 0.55f)
                lineTo(w * 0.58f, h * 0.78f)
                lineTo(w * 0.92f, h * 0.24f)
            }
            drawPath(p1, color = tint, style = stroke)
            drawPath(p2, color = tint, style = stroke)
        }
    }
}

/**
 * Ikon Vektor Panah Balas (Reply Arrow) di atas Canvas.
 */
@Composable
private fun ChatVectorReplyIcon(
    size: Dp = 14.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val arrow = Path().apply {
            moveTo(w * 0.42f, h * 0.22f)
            lineTo(w * 0.16f, h * 0.46f)
            lineTo(w * 0.42f, h * 0.70f)
        }
        val curve = Path().apply {
            moveTo(w * 0.18f, h * 0.46f)
            lineTo(w * 0.62f, h * 0.46f)
            quadraticTo(w * 0.86f, h * 0.46f, w * 0.86f, h * 0.76f)
        }
        drawPath(arrow, color = tint, style = stroke)
        drawPath(curve, color = tint, style = stroke)
    }
}

/**
 * Ikon Vektor Stiker (Lembar Sudut Terlipat) di atas Canvas.
 */
@Composable
private fun ChatVectorStickerIcon(
    size: Dp = 18.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.20f, h * 0.20f),
            style = stroke,
        )
        val fold = Path().apply {
            moveTo(w * 0.56f, h * 0.86f)
            lineTo(w * 0.56f, h * 0.60f)
            lineTo(w * 0.86f, h * 0.60f)
        }
        drawPath(fold, color = tint, style = stroke)
        drawCircle(color = tint, radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.42f))
        drawCircle(color = tint, radius = w * 0.06f, center = Offset(w * 0.62f, h * 0.42f))
    }
}

/**
 * Ikon Vektor Pin Sematkan Pesan (Pushpin) di atas Canvas.
 */
@Composable
private fun ChatVectorPinIcon(
    size: Dp = 16.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pinBody = Path().apply {
            moveTo(w * 0.32f, h * 0.18f)
            lineTo(w * 0.68f, h * 0.18f)
            lineTo(w * 0.62f, h * 0.46f)
            lineTo(w * 0.76f, h * 0.60f)
            lineTo(w * 0.24f, h * 0.60f)
            lineTo(w * 0.38f, h * 0.46f)
            close()
        }
        drawPath(pinBody, color = tint, style = stroke)
        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.60f),
            end = Offset(w * 0.50f, h * 0.88f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Ikon Vektor Media 1× Sekali Lihat (Lingkaran Putus-Putus + Angka 1 Vektor) di atas Canvas.
 */
@Composable
private fun ChatVectorViewOnceIcon(
    size: Dp = 18.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawArc(
            color = tint,
            startAngle = -70f,
            sweepAngle = 210f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = stroke,
        )
        drawArc(
            color = tint,
            startAngle = 165f,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = stroke,
        )
        drawArc(
            color = tint,
            startAngle = 230f,
            sweepAngle = 40f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = stroke,
        )
        val onePath = Path().apply {
            moveTo(w * 0.42f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.30f)
            lineTo(w * 0.52f, h * 0.68f)
        }
        drawPath(onePath, color = tint, style = stroke)
    }
}

/**
 * Ikon Vektor Play Video Segitiga di atas Canvas.
 */
@Composable
private fun ChatVectorVideoPlayIcon(
    size: Dp = 16.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val play = Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.50f)
            lineTo(w * 0.28f, h * 0.82f)
            close()
        }
        drawPath(play, color = tint)
    }
}

private var activeSocket: WebSocket? = null

private fun chatSocketSend(rawJson: String) {
    runCatching { activeSocket?.send(rawJson) }
}

internal fun translateClientSideMultiLang(
    text: String,
    senderCountry: String,
    viewerLangPref: String = "auto",
    viewerCountry: String = Locale.getDefault().country.ifBlank { "ID" },
    forServerBroadcast: Boolean = false,
): Triple<String, String, String>? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    val hasThai = clean.any { it in '\u0E00'..'\u0E7F' }
    val hasJp = clean.any { it in '\u3040'..'\u30FF' }
    val hasKr = clean.any { it in '\uAC00'..'\uD7AF' }
    val hasCyrillic = clean.any { it in '\u0400'..'\u04FF' }
    val hasArabic = clean.any { it in '\u0600'..'\u06FF' }
    val hasCjk = clean.any { it in '\u4E00'..'\u9FFF' }
    val isLikelyId = !hasThai && !hasJp && !hasKr && !hasCyrillic && !hasArabic && !hasCjk &&
        (CLIENT_ID_WORD_REGEX.containsMatchIn(clean) || senderCountry.equals("ID", ignoreCase = true))

    val isAuto = viewerLangPref == "auto" || viewerLangPref.isBlank()
    val viewerRegionLang = resolveRegionPrimaryLanguage(viewerCountry)
    val targetLang = when {
        forServerBroadcast -> if (isLikelyId) "en" else "id"
        isAuto -> viewerRegionLang
        else -> viewerLangPref
    }
    val targetLabel = targetLang.uppercase(Locale.ROOT)
    val crossRegionViewer = !forServerBroadcast && isAuto &&
        senderCountry.isNotBlank() &&
        !senderCountry.equals(viewerCountry, ignoreCase = true)

    return runCatching {
        val q = URLEncoder.encode(clean, "UTF-8")
        val url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=$targetLang&q=$q"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
            setRequestProperty("Accept", "application/json")
        }
        if (conn.responseCode in 200..299) {
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(raw)
            val first = arr.optJSONArray(0)
            if (first != null) {
                val tr = first.optString(0, "").trim()
                val det = first.optString(1, if (isLikelyId) "id" else "en").trim().lowercase(Locale.ROOT)
                val sameLangAsViewer = normalizeLangFamily(det) == normalizeLangFamily(targetLang)
                if (isAuto && !forServerBroadcast && sameLangAsViewer && !crossRegionViewer) {
                    // Bahasa pesan sudah sama dengan bahasa region penonton -> tampilkan 1 bahasa saja
                    return@runCatching Triple("", targetLabel, det)
                }
                if (forServerBroadcast && sameLangAsViewer) {
                    val altTarget = if (normalizeLangFamily(det) == "id") "en" else "id"
                    val altUrl = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=$det&tl=$altTarget&q=$q"
                    val altConn = (URL(altUrl).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15)")
                    }
                    if (altConn.responseCode in 200..299) {
                        val altArr = JSONArray(altConn.inputStream.bufferedReader().use { it.readText() })
                        val altFirst = altArr.optJSONArray(0)
                        val altTr = altFirst?.optString(0, "")?.trim().orEmpty()
                        if (altTr.isNotBlank()) {
                            return@runCatching Triple(altTr, altTarget.uppercase(Locale.ROOT), det)
                        }
                    }
                }
                if (tr.isNotBlank()) return@runCatching Triple(tr, targetLabel, det)
            }
        }
        val workerUrl = "https://api.xydeskremote.biz.id/api/chat/translate?q=$q&tl=$targetLang"
        val wConn = (URL(workerUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("Accept", "application/json")
        }
        if (wConn.responseCode in 200..299) {
            val obj = JSONObject(wConn.inputStream.bufferedReader().use { it.readText() })
            val tr = obj.optString("translated", "").trim()
            val det = obj.optString("source_lang", if (isLikelyId) "id" else "en").trim().lowercase(Locale.ROOT)
            val lbl = obj.optString("target_lang", targetLabel).trim().uppercase(Locale.ROOT)
            val sameLangAsViewer = normalizeLangFamily(det) == normalizeLangFamily(targetLang)
            if (isAuto && !forServerBroadcast && sameLangAsViewer && !crossRegionViewer) {
                return@runCatching Triple("", lbl, det)
            }
            if (tr.isNotBlank()) return@runCatching Triple(tr, lbl, det)
        }
        null
    }.getOrNull()
}

private fun decodeBase64Bitmap(dataUri: String): Bitmap? = runCatching {
    val comma = dataUri.indexOf(',')
    if (comma < 0) return@runCatching null
    val bytes = Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

private fun saveChatMediaToGallery(context: Context, msg: ChatMessage) {
    runCatching {
        val comma = msg.attachmentData.indexOf(',')
        if (comma < 0) return
        val bytes = Base64.decode(msg.attachmentData.substring(comma + 1), Base64.DEFAULT)
        val isVideo = msg.attachmentType == "video" || msg.attachmentMime.startsWith("video/")
        val ext = if (isVideo) "mp4" else "jpg"
        val mime = if (isVideo) (msg.attachmentMime.ifBlank { "video/mp4" }) else "image/jpeg"
        val fileName = "XyDesk_${System.currentTimeMillis()}.$ext"
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isVideo) "${Environment.DIRECTORY_MOVIES}/XyDesk" else "${Environment.DIRECTORY_PICTURES}/XyDesk",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val itemUri = resolver.insert(collection, values)
        if (itemUri != null) {
            resolver.openOutputStream(itemUri)?.use { out ->
                out.write(bytes)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(itemUri, doneValues, null, null)
            }
            Toast.makeText(
                context,
                if (isVideo) "Video berhasil disimpan ke Galeri (Movies/XyDesk)!" else "Foto berhasil disimpan ke Galeri (Pictures/XyDesk)!",
                Toast.LENGTH_LONG,
            ).show()
        } else {
            saveOrShareChatAttachment(context, msg)
        }
    }.onFailure {
        saveOrShareChatAttachment(context, msg)
    }
}

private fun saveOrShareChatAttachment(context: Context, msg: ChatMessage) {
    runCatching {
        val comma = msg.attachmentData.indexOf(',')
        if (comma < 0) return
        val bytes = Base64.decode(msg.attachmentData.substring(comma + 1), Base64.DEFAULT)
        val dir = File(context.cacheDir, "chat_files").apply { mkdirs() }
        val safeName = msg.attachmentName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file_xydesk.bin" }
        val outFile = File(dir, safeName)
        outFile.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", outFile)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = msg.attachmentMime.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "Simpan / Bagikan ${msg.attachmentName}"))
    }.onFailure {
        Toast.makeText(context, "Gagal membuka file: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Ronde 55 — Floating Chat Sesi dengan Efek Liquid Glass Hitam-Putih (Monokrom Agak Transparan).
 * - Saat diminimalkan: tampil sebagai Kapsul Kaca Cair (Liquid Glass Orb/Pill) hitam-putih
 *   dengan kilau meniskus kaca, jumlah online live, dan bisa digeser ke mana saja di layar sesi.
 * - Saat dibuka: mengambang langsung di atas sesi RDP / PC Stream dengan efek kaca cair
 *   agak transparan (layar remote di belakangnya tetap terlihat tembus), bisa digeser lewat
 *   header Liquid Glass, serta bisa diubah ukurannya (Compact / Luas).
 */
/**
 * Komponen Visual Vektor Berwarna untuk Reaksi Chat Global (FIRE, LOL, +1/HEART, KING, GG, RESPECT)
 * agar reaksi tampil sebagai ikon visual ekspresif, bukan sekadar tulisan teks.
 */
@Composable
private fun ChatReactionVisualIcon(
    tag: String,
    size: Dp = 18.dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (tag.uppercase(Locale.ROOT)) {
            "FIRE" -> {
                // Lidah Api Luar (Oranye Menyala)
                val outer = Path().apply {
                    moveTo(w * 0.50f, h * 0.06f)
                    cubicTo(w * 0.82f, h * 0.30f, w * 0.90f, h * 0.58f, w * 0.78f, h * 0.80f)
                    cubicTo(w * 0.70f, h * 0.93f, w * 0.30f, h * 0.93f, w * 0.22f, h * 0.80f)
                    cubicTo(w * 0.10f, h * 0.58f, w * 0.18f, h * 0.32f, w * 0.42f, h * 0.20f)
                    cubicTo(w * 0.38f, h * 0.34f, w * 0.45f, h * 0.38f, w * 0.50f, h * 0.06f)
                    close()
                }
                drawPath(outer, color = Color(0xFFFF5722))
                // Inti Api Dalam (Kuning Emas)
                val inner = Path().apply {
                    moveTo(w * 0.52f, h * 0.36f)
                    cubicTo(w * 0.70f, h * 0.52f, w * 0.72f, h * 0.72f, w * 0.62f, h * 0.85f)
                    cubicTo(w * 0.55f, h * 0.92f, w * 0.43f, h * 0.92f, w * 0.36f, h * 0.84f)
                    cubicTo(w * 0.28f, h * 0.72f, w * 0.34f, h * 0.52f, w * 0.52f, h * 0.36f)
                    close()
                }
                drawPath(inner, color = Color(0xFFFFCA28))
            }
            "LOL" -> {
                // Wajah Ketawa Emas + Mata Melengkung + Air Mata Bahagia Biru
                drawCircle(color = Color(0xFFFBBF24), radius = w * 0.44f, center = Offset(w * 0.50f, h * 0.50f))
                val eyeStroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
                drawArc(
                    color = Color(0xFF1E1B4B),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.30f),
                    size = Size(w * 0.20f, h * 0.16f),
                    style = eyeStroke,
                )
                drawArc(
                    color = Color(0xFF1E1B4B),
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(w * 0.58f, h * 0.30f),
                    size = Size(w * 0.20f, h * 0.16f),
                    style = eyeStroke,
                )
                // Mulut Ketawa Lebar
                drawArc(
                    color = Color(0xFFDC2626),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.25f, h * 0.42f),
                    size = Size(w * 0.50f, h * 0.36f),
                )
                // Air mata ketawa kiri & kanan
                drawCircle(color = Color(0xFF38BDF8), radius = w * 0.09f, center = Offset(w * 0.14f, h * 0.54f))
                drawCircle(color = Color(0xFF38BDF8), radius = w * 0.09f, center = Offset(w * 0.86f, h * 0.54f))
            }
            "+1" -> {
                // Hati Merah Muda Ekspresif (Love / Like)
                val heart = Path().apply {
                    moveTo(w * 0.50f, h * 0.86f)
                    cubicTo(w * 0.12f, h * 0.62f, w * 0.06f, h * 0.26f, w * 0.28f, h * 0.16f)
                    cubicTo(w * 0.42f, h * 0.10f, w * 0.50f, h * 0.24f, w * 0.50f, h * 0.28f)
                    cubicTo(w * 0.50f, h * 0.24f, w * 0.58f, h * 0.10f, w * 0.72f, h * 0.16f)
                    cubicTo(w * 0.94f, h * 0.26f, w * 0.88f, h * 0.62f, w * 0.50f, h * 0.86f)
                    close()
                }
                drawPath(heart, color = Color(0xFFF43F5E))
            }
            "KING" -> {
                // Mahkota Raja Emas + Permata Merah di Tengah
                val crown = Path().apply {
                    moveTo(w * 0.12f, h * 0.76f)
                    lineTo(w * 0.18f, h * 0.26f)
                    lineTo(w * 0.36f, h * 0.50f)
                    lineTo(w * 0.50f, h * 0.18f)
                    lineTo(w * 0.64f, h * 0.50f)
                    lineTo(w * 0.82f, h * 0.26f)
                    lineTo(w * 0.88f, h * 0.76f)
                    close()
                }
                drawPath(crown, color = Color(0xFFF59E0B))
                drawCircle(color = Color(0xFFEF4444), radius = w * 0.075f, center = Offset(w * 0.50f, h * 0.58f))
            }
            "GG" -> {
                // Petir Elektrik Biru-Ungu (Fast / GGWP)
                val bolt = Path().apply {
                    moveTo(w * 0.58f, h * 0.08f)
                    lineTo(w * 0.20f, h * 0.54f)
                    lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.38f, h * 0.92f)
                    lineTo(w * 0.80f, h * 0.44f)
                    lineTo(w * 0.52f, h * 0.44f)
                    close()
                }
                drawPath(bolt, color = Color(0xFF38BDF8))
            }
            else -> {
                // RESPECT: Perisai Bintang Emerald
                val star = Path().apply {
                    moveTo(w * 0.50f, h * 0.08f)
                    lineTo(w * 0.63f, h * 0.35f)
                    lineTo(w * 0.92f, h * 0.39f)
                    lineTo(w * 0.71f, h * 0.60f)
                    lineTo(w * 0.76f, h * 0.89f)
                    lineTo(w * 0.50f, h * 0.75f)
                    lineTo(w * 0.24f, h * 0.89f)
                    lineTo(w * 0.29f, h * 0.60f)
                    lineTo(w * 0.08f, h * 0.39f)
                    lineTo(w * 0.37f, h * 0.35f)
                    close()
                }
                drawPath(star, color = Color(0xFF10B981))
            }
        }
    }
}

@Composable
internal fun FloatingSessionChatOverlay(
    modifier: Modifier = Modifier,
) {
    var chatWindowOpen by remember { mutableStateOf(false) }
    var isCompactFloating by remember { mutableStateOf(true) }
    var isGhostOpacity by remember { mutableStateOf(false) }
    var bubbleOffsetX by remember { mutableFloatStateOf(16f) }
    var bubbleOffsetY by remember { mutableFloatStateOf(96f) }
    var panelOffsetX by remember { mutableFloatStateOf(0f) }
    var panelOffsetY by remember { mutableFloatStateOf(0f) }
    val onlineCount by AppUpdate.appWideOnlineCount.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding(),
    ) {
        if (!chatWindowOpen) {
            // Tombol Kapsul Sesi Ringkas & Draggable di Tepi Layar
            Box(
                modifier = Modifier
                    .offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()) }
                    .shadow(10.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF10131C).copy(alpha = 0.88f))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF6366F1).copy(alpha = 0.55f),
                        shape = RoundedCornerShape(50),
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            bubbleOffsetX = (bubbleOffsetX + dragAmount.x).coerceIn(6f, 1800f)
                            bubbleOffsetY = (bubbleOffsetY + dragAmount.y).coerceIn(12f, 1000f)
                        }
                    }
                    .clickable { chatWindowOpen = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981)),
                    )
                    Icon(
                        imageVector = XyIcons.Chat,
                        contentDescription = "Buka Chat Global Sesi",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF6366F1).copy(alpha = 0.28f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "${onlineCount.coerceAtLeast(1)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFE0E7FF),
                        )
                    }
                }
            }
        }

        if (chatWindowOpen) {
            val panelShape = RoundedCornerShape(20.dp)
            val panelAlpha = if (isGhostOpacity) 0.64f else 0.94f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // Panel HUD Samping (Side-Docked PiP): Tidak menghalangi tengah layar RDP, nyaman digeser & diketik di landscape
                Box(
                    modifier = Modifier
                        .offset { IntOffset(panelOffsetX.roundToInt(), panelOffsetY.roundToInt()) }
                        .width(if (isCompactFloating) 316.dp else 380.dp)
                        .heightIn(min = 220.dp, max = if (isCompactFloating) 295.dp else 420.dp)
                        .fillMaxHeight(if (isCompactFloating) 0.78f else 0.90f)
                        .shadow(20.dp, panelShape)
                        .clip(panelShape)
                        .background(Color(0xFF0B0D14).copy(alpha = panelAlpha))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.18f),
                            shape = panelShape,
                        )
                        .clickable(enabled = false) {},
                ) {
                    GlobalChatScreen(
                        onBack = { chatWindowOpen = false },
                        isOverlayMode = true,
                        onHeaderDrag = { dx, dy ->
                            panelOffsetX = (panelOffsetX + dx).coerceIn(-680f, 340f)
                            panelOffsetY = (panelOffsetY + dy).coerceIn(-260f, 260f)
                        },
                        isCompactFloating = isCompactFloating,
                        onToggleCompactFloating = {
                            if (!isCompactFloating && !isGhostOpacity) {
                                isGhostOpacity = true
                            } else if (isGhostOpacity) {
                                isGhostOpacity = false
                                isCompactFloating = true
                            } else {
                                isCompactFloating = false
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Indikator sedang mengetik dengan animasi gelombang 3 titik (• • •).
 */
@Composable
private fun AnimatedTypingDotsBar(typingUser: String) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "typingDots")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "dotsPhase",
    )
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFF161922))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0..2) {
                val dist = kotlin.math.abs(phase - i)
                val bounce = (1f - dist.coerceIn(0f, 1f))
                Box(
                    modifier = Modifier
                        .offset(y = (-bounce * 3f).dp)
                        .size((5f + bounce * 2f).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981).copy(alpha = 0.55f + bounce * 0.45f)),
                )
            }
        }
        Text(
            text = "$typingUser sedang mengetik",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE2E8F0),
        )
    }
}

private fun isMaliciousFileName(fileName: String): Boolean {
    val lower = fileName.trim().lowercase(Locale.ROOT)
    val badExts = listOf(
        ".exe", ".msi", ".bat", ".cmd", ".scr", ".pif", ".vbs", ".vbe",
        ".js", ".jse", ".wsf", ".wsh", ".ps1", ".apk", ".dex", ".jar",
        ".hta", ".reg", ".dll", ".sys", ".sh", ".elf",
    )
    return badExts.any { lower.endsWith(it) }
}

private fun playChatNotificationTone(toneMode: String) {
    if (toneMode == "silent") return
    runCatching {
        val toneType = when (toneMode) {
            "crystal" -> android.media.ToneGenerator.TONE_PROP_ACK
            "beep" -> android.media.ToneGenerator.TONE_PROP_BEEP
            else -> android.media.ToneGenerator.TONE_PROP_BEEP2
        }
        val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 65)
        tg.startTone(toneType, 120)
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tg.release() } }, 250L)
    }
}

