package id.xydesk.remote.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.telephony.TelephonyManager
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import id.xydesk.remote.security.CredentialVault
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.rdp.XyRdpClient
import id.xydesk.remote.ui.rdp.XyRdpConfig
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
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Ronde 47–52 — Chat Global XyDesk (Gaya WhatsApp Modern dengan Palet XyDesk).
 *
 * Fitur Utama:
 * 1. **Wajib Login GitHub**: hanya pengguna yang sudah login GitHub yang dapat
 *    mengirim pesan (dilengkapi tombol Login GitHub Instan tanpa ketik kode).
 * 2. **Dual-Transport (WebSocket + HTTP Fallback)**: riwayat & pengiriman pesan
 *    dijamin 100% berfungsi walau jaringan seluler memblokir WebSocket.
 * 3. **Deteksi Negara Akurat (SIM + TimeZone + Bahasa Teks)** & **Terjemahan Neural
 *    Semua Bahasa (`ID`, `EN`, `TH`, `JA`, `KO`, `ZH`, `VI`, `TL`, `MS`, `AR`, `RU`,
 *    `ES`, `DE`, `FR`, `PT`, `HI`, `TR`)** di dalam **1 bubble yang dipisah garis**.
 * 4. **Swipe-to-Reply (Geser Bubble ke Kanan untuk Balas)** + **Attach Foto/Gambar,
 *    File/Dokumen, & Stiker Ekspresif XyDesk**.
 * 5. **Floating Bubble Chat Global Saat Sesi RDP / PC Stream** (`FloatingSessionChatOverlay`).
 */

private const val CHAT_WS_URL = "wss://api.xydeskremote.biz.id/ws/chat"
private const val CHAT_HISTORY_URL = "https://api.xydeskremote.biz.id/api/chat/history"
private const val CHAT_SEND_URL = "https://api.xydeskremote.biz.id/api/chat/send"
private const val CHAT_PREFS = "xydesk.global.chat"
private const val KEY_NICKNAME = "nickname"
private const val KEY_TARGET_LANG = "viewer_target_lang"

internal data class ChatMessage(
    val id: String,
    val name: String,
    val country: String,
    val flag: String,
    val text: String,
    val translated: String,
    val targetLang: String,
    val sourceLang: String = "",
    val at: Long,
    val mine: Boolean,
    val isFounder: Boolean = isFounderAccount(name),
    val replyToId: String = "",
    val replyToName: String = "",
    val replyToText: String = "",
    val attachmentType: String = "", // "", "image", "file", "sticker"
    val attachmentName: String = "",
    val attachmentMime: String = "",
    val attachmentSize: Int = 0,
    val attachmentData: String = "",
    val avatarUrl: String = "",
)

internal data class XyStickerPreset(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val c1: Color,
    val c2: Color,
)

private val XY_STICKERS = listOf(
    XyStickerPreset("xy_ggwp", "🔥", "GGWP!", "Good Game Well Played", Color(0xFFEA580C), Color(0xFF9A3412)),
    XyStickerPreset("xy_gasken", "🚀", "GASKEUN!", "Langsung meluncur bosku", Color(0xFF2563EB), Color(0xFF1E3A8A)),
    XyStickerPreset("xy_120fps", "⚡", "120 FPS!", "Ultra Smooth No Lag", Color(0xFF0284C7), Color(0xFF0C4A6E)),
    XyStickerPreset("xy_founder", "👑", "KING VIBES", "XyVerse Technology", Color(0xFFD97706), Color(0xFF78350F)),
    XyStickerPreset("xy_wkwk", "😂", "WKWKWK", "Ngakak sampe guling", Color(0xFF059669), Color(0xFF064E3B)),
    XyStickerPreset("xy_mantap", "❤️", "MANTAP JIWA", "Keren parah bang!", Color(0xFFE11D48), Color(0xFF881337)),
    XyStickerPreset("xy_mabar", "🎮", "AYO MABAR!", "Login game sekarang", Color(0xFF7C3AED), Color(0xFF4C1D95)),
    XyStickerPreset("xy_thanks", "🙏", "MAKASIH!", "Sangat membantu bro", Color(0xFF0D9488), Color(0xFF134E4A)),
    XyStickerPreset("xy_santuy", "😎", "SANTUY BRO", "Aman terkendali", Color(0xFF4F46E5), Color(0xFF312E81)),
    XyStickerPreset("xy_rdp_on", "💻", "RDP ON!", "Server siap tempur", Color(0xFF0369A1), Color(0xFF082F49)),
    XyStickerPreset("xy_headshot", "🎯", "360° AIM!", "RawInput Auto Presisi", Color(0xFFDC2626), Color(0xFF7F1D1D)),
    XyStickerPreset("xy_halo", "👋", "HALO SEMUA!", "Salam kenal dari XyDesk", Color(0xFF2563EB), Color(0xFF1E40AF)),
)

internal data class ChatLangOption(
    val code: String,
    val label: String,
    val flag: String,
)

private val SUPPORTED_VIEWER_LANGS = listOf(
    ChatLangOption("auto", "Otomatis (ID ⇄ EN)", "🌐"),
    ChatLangOption("id", "Indonesia (ID)", "🇮🇩"),
    ChatLangOption("en", "English (EN)", "🇺🇸"),
    ChatLangOption("th", "ไทย · Thailand (TH)", "🇹🇭"),
    ChatLangOption("ja", "日本語 · Jepang (JA)", "🇯🇵"),
    ChatLangOption("ko", "한국어 · Korea (KO)", "🇰🇷"),
    ChatLangOption("zh-CN", "中文 · Mandarin (ZH)", "🇨🇳"),
    ChatLangOption("vi", "Tiếng Việt (VI)", "🇻🇳"),
    ChatLangOption("tl", "Tagalog / Filipino (TL)", "🇵🇭"),
    ChatLangOption("ms", "Melayu (MS)", "🇲🇾"),
    ChatLangOption("ar", "العربية · Arab (AR)", "🇸🇦"),
    ChatLangOption("ru", "Русский · Rusia (RU)", "🇷🇺"),
    ChatLangOption("hi", "हिन्दी · India (HI)", "🇮🇳"),
    ChatLangOption("es", "Español (ES)", "🇪🇸"),
    ChatLangOption("pt", "Português (PT)", "🇧🇷"),
    ChatLangOption("de", "Deutsch (DE)", "🇩🇪"),
    ChatLangOption("fr", "Français (FR)", "🇫🇷"),
    ChatLangOption("tr", "Türkçe (TR)", "🇹🇷"),
)

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

internal fun chatNickname(context: Context): String {
    val prefs = context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)
    val ghLogin = getVerifiedGitHubLogin(context)
    var name = prefs.getString(KEY_NICKNAME, null)
    if (!ghLogin.isNullOrBlank()) {
        if (name.isNullOrBlank() || name.startsWith("Tamu-") || isFounderAccount(ghLogin)) {
            name = ghLogin
            prefs.edit().putString(KEY_NICKNAME, name).apply()
        }
    }
    if (name.isNullOrBlank()) {
        name = "Tamu-" + (1000 + (UUID.randomUUID().toString().hashCode() and 0x8fff))
        prefs.edit().putString(KEY_NICKNAME, name).apply()
    }
    return name
}

/**
 * Deteksi negara perangkat yang akurat:
 * 1) SIM / Network Country ISO dari TelephonyManager
 * 2) TimeZone perangkat (mis. Asia/Jakarta -> ID, Asia/Bangkok -> TH, Asia/Tokyo -> JP)
 * 3) Locale perangkat (hanya bila bukan en_US generik di zona waktu Asia)
 */
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
        tz in setOf("Asia/Jakarta", "Asia/Pontianak", "Asia/Makassar", "Asia/Jayapura", "Asia/Ujung_Pandang") -> return "ID"
        tz == "Asia/Bangkok" -> return "TH"
        tz == "Asia/Tokyo" -> return "JP"
        tz == "Asia/Seoul" -> return "KR"
        tz in setOf("Asia/Kuala_Lumpur", "Asia/Kuching") -> return "MY"
        tz == "Asia/Singapore" -> return "SG"
        tz == "Asia/Manila" -> return "PH"
        tz in setOf("Asia/Ho_Chi_Minh", "Asia/Saigon") -> return "VN"
        tz in setOf("Asia/Shanghai", "Asia/Chongqing", "Asia/Harbin", "Asia/Urumqi") -> return "CN"
        tz == "Asia/Taipei" -> return "TW"
        tz == "Asia/Hong_Kong" -> return "HK"
        tz in setOf("Asia/Kolkata", "Asia/Calcutta") -> return "IN"
        tz == "Asia/Riyadh" -> return "SA"
        tz == "Asia/Dubai" -> return "AE"
        tz == "Europe/Moscow" -> return "RU"
        tz == "Europe/Istanbul" -> return "TR"
    }
    val raw = Locale.getDefault().country.orEmpty().trim().uppercase(Locale.ROOT)
    val lang = Locale.getDefault().language.orEmpty().trim().lowercase(Locale.ROOT)
    if (lang == "in" || lang == "id") return "ID"
    if (lang == "th") return "TH"
    if (lang == "ja") return "JP"
    if (lang == "ko") return "KR"
    if (lang == "vi") return "VN"
    if (lang == "tl" || lang == "fil") return "PH"
    if (lang == "ms") return "MY"
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
    return if (cc.length == 2 && cc.all { it in 'A'..'Z' }) cc else (mapped ?: "ID")
}

internal fun countryCodeToFlag(countryCode: String): String {
    val cc = countryCode.trim().uppercase(Locale.ROOT)
    if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return "🌐"
    val first = Character.toChars(0x1F1E6 + (cc[0].code - 'A'.code))
    val second = Character.toChars(0x1F1E6 + (cc[1].code - 'A'.code))
    return String(first) + String(second)
}

private fun parseMessageFromJson(m: JSONObject, myName: String): ChatMessage {
    val msgId = m.optString("id")
    val msgAt = m.optLong("at")
    val itemName = m.optString("name", "Tamu")
    val text = m.optString("text")
    val srcLang = m.optString("source_lang", "")
    val rawCc = m.optString("country", "ID").ifBlank { "ID" }.uppercase(Locale.ROOT)
    val cc = resolveSmartCountryClient(text, srcLang, rawCc)
    val flag = countryCodeToFlag(cc)
    return ChatMessage(
        id = msgId,
        name = itemName,
        country = cc,
        flag = flag,
        text = text,
        translated = m.optString("translated", ""),
        targetLang = m.optString("target_lang", "EN").ifBlank { "EN" },
        sourceLang = srcLang,
        at = msgAt,
        mine = itemName.equals(myName, ignoreCase = true),
        isFounder = m.optBoolean("is_founder", isFounderAccount(itemName)),
        replyToId = m.optString("reply_to_id", ""),
        replyToName = m.optString("reply_to_name", ""),
        replyToText = m.optString("reply_to_text", ""),
        attachmentType = m.optString("attachment_type", ""),
        attachmentName = m.optString("attachment_name", ""),
        attachmentMime = m.optString("attachment_mime", ""),
        attachmentSize = m.optInt("attachment_size", 0),
        attachmentData = m.optString("attachment_data", ""),
        avatarUrl = m.optString("avatar_url", ""),
    )
}

@Composable
internal fun GlobalChatScreen(
    onBack: () -> Unit,
    isOverlayMode: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatPrefs = remember { context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE) }
    var verifiedGhLogin by remember { mutableStateOf(getVerifiedGitHubLogin(context)) }
    var nickname by remember { mutableStateOf(chatNickname(context)) }
    val myCountry = remember { chatCountryCode(context) }
    val myFlag = remember(myCountry) { countryCodeToFlag(myCountry) }
    var viewerTargetLang by remember {
        mutableStateOf(chatPrefs.getString(KEY_TARGET_LANG, "auto") ?: "auto")
    }
    var showLangPicker by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var loginModalOpen by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    var fullscreenImageUri by remember { mutableStateOf<String?>(null) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var presence by remember { mutableIntStateOf(1) }
    var typingName by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val main = remember { Handler(Looper.getMainLooper()) }
    var typingSentAt by remember { mutableStateOf(0L) }

    fun enrichTranslationIfNeeded(msg: ChatMessage, forceRetranslate: Boolean = false) {
        if (msg.attachmentType == "sticker" && msg.text.startsWith("🎨")) return
        Thread {
            val result = translateClientSideMultiLang(
                text = msg.text,
                senderCountry = msg.country,
                viewerLangPref = viewerTargetLang,
            ) ?: return@Thread
            val translatedText = result.first
            val targetLabel = result.second
            val detectedLang = result.third
            if (translatedText.isBlank()) return@Thread
            val updatedCountry = resolveSmartCountryClient(msg.text, detectedLang, msg.country)
            val updatedFlag = countryCodeToFlag(updatedCountry)
            main.post {
                val idx = messages.indexOfFirst { it.id == msg.id }
                if (idx >= 0) {
                    val current = messages[idx]
                    if (forceRetranslate || current.translated != translatedText || current.country != updatedCountry) {
                        messages[idx] = current.copy(
                            translated = translatedText,
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
        val list = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(CHAT_HISTORY_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    setRequestProperty("Accept", "application/json")
                }
                if (conn.responseCode !in 200..299) return@runCatching emptyList<ChatMessage>()
                val raw = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(raw)
                val arr = json.optJSONArray("messages") ?: return@runCatching emptyList<ChatMessage>()
                val out = ArrayList<ChatMessage>(arr.length())
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    out.add(parseMessageFromJson(m, nickname))
                }
                out
            }.getOrDefault(emptyList())
        }
        if (list.isNotEmpty()) {
            val existingIds = messages.map { it.id }.toSet()
            var changed = false
            for (item in list) {
                id.xydesk.remote.push.ChatPushNotifier.markSeen(item.id, item.at, context)
                val pendingIdx = messages.indexOfFirst {
                    it.id.startsWith("local-pending-") && it.text == item.text && it.mine
                }
                if (pendingIdx >= 0) {
                    messages[pendingIdx] = item
                    enrichTranslationIfNeeded(item)
                    changed = true
                } else if (item.id !in existingIds) {
                    messages.add(item)
                    enrichTranslationIfNeeded(item)
                    changed = true
                }
            }
            if (changed) {
                messages.sortBy { it.at }
            }
        }
    }

    // Verifikasi login GitHub saat layar dibuka
    LaunchedEffect(Unit) {
        fetchHistoryOverHttp()
        if (verifiedGhLogin == null) {
            val gh = withContext(Dispatchers.IO) {
                runCatching {
                    val pat = CredentialVault(context.applicationContext).get(XyRdpConfig.VAULT_PAT_ID)
                    if (pat != null) syncGitHubProfileAndAvatar(context, pat) ?: XyRdpClient.whoami(pat) else null
                }.getOrNull()
            }
            if (!gh.isNullOrBlank()) {
                saveVerifiedGitHubLogin(context, gh)
                verifiedGhLogin = gh
                val updated = chatNickname(context)
                nickname = updated
                chatSocketSend(
                    JSONObject()
                        .put("type", "hello")
                        .put("name", updated)
                        .put("country", myCountry)
                        .toString(),
                )
            }
        }
    }

    // Ketika penonton mengganti bahasa terjemahan di header, terjemahkan ulang daftar pesan
    LaunchedEffect(viewerTargetLang) {
        messages.toList().forEach { enrichTranslationIfNeeded(it, forceRetranslate = true) }
    }

    // Polling cadangan HTTP setiap 5 detik agar chat dijamin real-time walau WebSocket terhalang operator
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            fetchHistoryOverHttp()
        }
    }

    DisposableEffect(Unit) {
        id.xydesk.remote.push.ChatPushNotifier.setChatOpen(context, true)
        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
        var ws: WebSocket? = null
        var attempt = 0
        var closed = false

        fun connect(): WebSocket {
            val socket = client.newWebSocket(
                Request.Builder().url(CHAT_WS_URL).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        attempt = 0
                        main.post { connected = true }
                        webSocket.send(
                            JSONObject()
                                .put("type", "hello")
                                .put("name", nickname)
                                .put("country", myCountry)
                                .toString(),
                        )
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val data = try { JSONObject(text) } catch (e: Exception) { return }
                        main.post {
                            when (data.optString("type")) {
                                "history" -> {
                                    val arr: JSONArray = data.optJSONArray("messages") ?: return@post
                                    messages.clear()
                                    for (i in 0 until arr.length()) {
                                        val m = arr.optJSONObject(i) ?: continue
                                        val item = parseMessageFromJson(m, nickname)
                                        id.xydesk.remote.push.ChatPushNotifier.markSeen(item.id, item.at, context)
                                        messages.add(item)
                                        enrichTranslationIfNeeded(item)
                                    }
                                }
                                "msg" -> {
                                    val item = parseMessageFromJson(data, nickname)
                                    id.xydesk.remote.push.ChatPushNotifier.markSeen(item.id, item.at, context)
                                    messages.removeAll {
                                        it.id.startsWith("local-pending-") && it.text == item.text && it.mine
                                    }
                                    if (messages.none { it.id == item.id }) {
                                        messages.add(item)
                                        enrichTranslationIfNeeded(item)
                                    }
                                    typingName = null
                                }
                                "typing" -> {
                                    if (data.optBoolean("on")) {
                                        val tName = data.optString("name")
                                        val tFlag = data.optString("flag")
                                        typingName = if (tFlag.isNotBlank()) "$tFlag $tName" else tName
                                    } else {
                                        typingName = null
                                    }
                                }
                                "presence" -> presence = data.optInt("count", 1).coerceAtLeast(1)
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        main.post { connected = false }
                        if (closed) return
                        attempt += 1
                        val delayMs = (1000L shl (attempt - 1).coerceAtMost(4))
                        Thread {
                            try {
                                Thread.sleep(delayMs)
                            } catch (e: InterruptedException) {
                                return@Thread
                            }
                            if (!closed) ws = connect()
                        }.start()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        main.post { connected = false }
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
            try { ws?.close(1000, "bye") } catch (_: Exception) {}
        }
    }

    LaunchedEffect(messages.size, typingName) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    fun dispatchChatPayload(
        text: String,
        attachmentType: String = "",
        attachmentName: String = "",
        attachmentMime: String = "",
        attachmentSize: Int = 0,
        attachmentData: String = "",
    ) {
        val cleanText = text.trim().ifEmpty {
            when (attachmentType) {
                "sticker" -> "🎨 Stiker: $attachmentName"
                "image" -> "📷 Foto: ${attachmentName.ifBlank { "Gambar" }}"
                "file" -> "📎 File: ${attachmentName.ifBlank { "Dokumen" }}"
                else -> ""
            }
        }
        if (cleanText.isEmpty()) return
        val reply = replyingTo
        replyingTo = null
        showStickerPicker = false
        showAttachMenu = false

        val pending = ChatMessage(
            id = "local-pending-" + System.currentTimeMillis(),
            name = nickname,
            country = myCountry,
            flag = myFlag,
            text = cleanText,
            translated = if (attachmentType == "sticker") "" else "Menerjemahkan…",
            targetLang = if (myCountry == "ID") "EN" else "ID",
            at = System.currentTimeMillis(),
            mine = true,
            isFounder = isFounderAccount(nickname) || isFounderAccount(verifiedGhLogin),
            replyToId = reply?.id.orEmpty(),
            replyToName = reply?.name.orEmpty(),
            replyToText = reply?.text.orEmpty(),
            attachmentType = attachmentType,
            attachmentName = attachmentName,
            attachmentMime = attachmentMime,
            attachmentSize = attachmentSize,
            attachmentData = attachmentData,
            avatarUrl = getVerifiedGitHubAvatar(context).orEmpty(),
        )
        messages.add(pending)
        enrichTranslationIfNeeded(pending.copy(translated = ""))

        scope.launch {
            sending = true
            val sentMsg = withContext(Dispatchers.IO) {
                val clientTr = if (attachmentType == "sticker") null else {
                    translateClientSideMultiLang(cleanText, myCountry, "auto")
                }
                val smartCc = resolveSmartCountryClient(
                    cleanText,
                    clientTr?.third.orEmpty(),
                    myCountry,
                )
                val bodyJson = JSONObject().apply {
                    put("type", "msg")
                    put("name", nickname)
                    if (!verifiedGhLogin.isNullOrBlank()) put("github_login", verifiedGhLogin)
                    put("text", cleanText)
                    put("country", smartCc)
                    if (clientTr != null && clientTr.first.isNotBlank()) {
                        put("translated", clientTr.first)
                        put("target_lang", clientTr.second)
                        put("source_lang", clientTr.third)
                    }
                    if (reply != null) {
                        put("reply_to_id", reply.id)
                        put("reply_to_name", reply.name)
                        put("reply_to_text", reply.text.take(160))
                    }
                    if (attachmentType.isNotEmpty()) {
                        put("attachment_type", attachmentType)
                        put("attachment_kind", if (attachmentType == "image") "photo" else attachmentType)
                        put("attachment_name", attachmentName)
                        put("attachment_mime", attachmentMime)
                        put("attachment_size", attachmentSize)
                        put("attachment_data", attachmentData)
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
                        resp.optJSONObject("message")?.let { parseMessageFromJson(it, nickname) }
                    } else null
                }.getOrNull() ?: run {
                    chatSocketSend(bodyJson.toString())
                    null
                }
            }
            if (sentMsg != null) {
                val idx = messages.indexOfFirst { it.id == pending.id }
                if (idx >= 0) {
                    messages[idx] = sentMsg
                    enrichTranslationIfNeeded(sentMsg)
                }
            }
            chatSocketSend(JSONObject().put("type", "typing").put("on", false).toString())
            sending = false
        }
    }

    // Pemilih Foto / Gambar
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val encoded = withContext(Dispatchers.IO) {
                    runCatching {
                        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
                        val src = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return@runCatching null
                        val maxEdge = 640
                        val scale = minOf(1f, maxEdge.toFloat() / maxOf(src.width, src.height))
                        val bmp = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                src,
                                (src.width * scale).roundToInt().coerceAtLeast(1),
                                (src.height * scale).roundToInt().coerceAtLeast(1),
                                true,
                            )
                        } else src
                        val out = ByteArrayOutputStream()
                        val fmt = if (Build.VERSION.SDK_INT >= 30) {
                            Bitmap.CompressFormat.WEBP_LOSSY
                        } else {
                            @Suppress("DEPRECATION")
                            Bitmap.CompressFormat.WEBP
                        }
                        bmp.compress(fmt, 78, out)
                        val bytes = out.toByteArray()
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        Triple("data:image/webp;base64,$b64", bytes.size, "foto-${System.currentTimeMillis() % 10000}.webp")
                    }.getOrNull()
                }
                if (encoded != null && encoded.first.length <= 235_000) {
                    dispatchChatPayload(
                        text = draft.text.trim().ifEmpty { "📷 Mengirim foto" },
                        attachmentType = "image",
                        attachmentName = encoded.third,
                        attachmentMime = "image/webp",
                        attachmentSize = encoded.second,
                        attachmentData = encoded.first,
                    )
                    draft = TextFieldValue("")
                } else {
                    Toast.makeText(context, "Ukuran gambar terlalu besar, pilih gambar lain.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Pemilih File / Dokumen
    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val filePayload = withContext(Dispatchers.IO) {
                    runCatching {
                        var fileName = "dokumen.bin"
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIdx >= 0 && cursor.moveToFirst()) {
                                fileName = cursor.getString(nameIdx) ?: fileName
                            }
                        }
                        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                        val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                            val buf = ByteArray(140_000)
                            var total = 0
                            while (total < buf.size) {
                                val r = stream.read(buf, total, buf.size - total)
                                if (r <= 0) break
                                total += r
                            }
                            if (total in 1..135_000) buf.copyOf(total) else null
                        } ?: return@runCatching null
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        Triple(fileName, mime to bytes.size, "data:$mime;base64,$b64")
                    }.getOrNull()
                }
                if (filePayload != null) {
                    dispatchChatPayload(
                        text = draft.text.trim().ifEmpty { "📎 File: ${filePayload.first}" },
                        attachmentType = "file",
                        attachmentName = filePayload.first,
                        attachmentMime = filePayload.second.first,
                        attachmentSize = filePayload.second.second,
                        attachmentData = filePayload.third,
                    )
                    draft = TextFieldValue("")
                } else {
                    Toast.makeText(context, "File maksimal 135 KB untuk pengiriman instan di Chat Global.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isLoggedIn = !verifiedGhLogin.isNullOrBlank()
    val currentLangOption = SUPPORTED_VIEWER_LANGS.firstOrNull { it.code == viewerTargetLang }
        ?: SUPPORTED_VIEWER_LANGS.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1120),
                        Color(0xFF0F172A),
                        Color(0xFF111827),
                    ),
                ),
            )
            .imePadding(),
    ) {
        // =========================================================
        // HEADER ATAS BERGAYA WHATSAPP MODERN (TEMA XYDESK INDIGO)
        // =========================================================
        Surface(
            color = Color(0xFF0F172A),
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = onBack,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        if (isOverlayMode) "✕ Tutup" else "‹ Kembali",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Chat Global XyDesk",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isFounderAccount(nickname) || isFounderAccount(verifiedGhLogin)) {
                            XyFounderBadge(compact = true)
                        }
                    }
                    Text(
                        text = buildString {
                            append(if (connected) "● $presence online" else "● Sinkron aktif")
                            append(" · $myFlag $nickname ($myCountry)")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Tombol Pemilih Bahasa Terjemahan (Support All Languages!)
                Surface(
                    onClick = { showLangPicker = true },
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(50)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(currentLangOption.flag, fontSize = 12.sp)
                        Text(
                            currentLangOption.code.uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                        )
                    }
                }

                // Ikon Logo GitHub & Avatar Profil di Header Atas
                XyHeaderAccountActions(
                    onStateChanged = {
                        verifiedGhLogin = getVerifiedGitHubLogin(context)
                        nickname = chatNickname(context)
                    },
                )
            }
        }

        // =========================================================
        // DAFTAR PESAN (SWIPE TO REPLY + 2 BAHASA DALAM 1 BUBBLE)
        // =========================================================
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "🌐 Auto-Translate Semua Bahasa (ID · EN · TH · JA · KO · ZH · AR · RU) · Geser bubble ke kanan untuk Balas",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Belum ada pesan. Sapa pengguna dari berbagai negara sekarang 👋",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(messages, key = { it.id }) { msg ->
                ChatBubbleWhatsAppStyle(
                    msg = msg,
                    onReply = { replyingTo = msg },
                    onOpenImage = { dataUri -> fullscreenImageUri = dataUri },
                    onSaveFile = { item -> saveOrShareChatAttachment(context, item) },
                )
            }
            if (typingName != null) {
                item {
                    Text(
                        "$typingName sedang mengetik…",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        // =========================================================
        // PANEL STIKER EKSPRESIF XYDESK (SAAT TOMBOL STIKER DIKETUK)
        // =========================================================
        AnimatedVisibility(visible = showStickerPicker && isLoggedIn) {
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Pilih Stiker XyDesk (Ketuk untuk Kirim)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            "✕ Tutup",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.clickable { showStickerPicker = false }.padding(4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(XY_STICKERS, key = { it.id }) { st ->
                            Column(
                                modifier = Modifier
                                    .width(108.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(st.c1, st.c2)))
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                                    .clickable {
                                        dispatchChatPayload(
                                            text = "${st.emoji} ${st.title} — ${st.subtitle}",
                                            attachmentType = "sticker",
                                            attachmentName = st.title,
                                            attachmentData = "sticker:${st.id}",
                                        )
                                    }
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(st.emoji, fontSize = 26.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    st.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                                Text(
                                    st.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // =========================================================
        // BAR KUTIPAN BALASAN (SAAT SWIPE TO REPLY AKTIF)
        // =========================================================
        AnimatedVisibility(visible = replyingTo != null && isLoggedIn) {
            val target = replyingTo
            if (target != null) {
                Surface(color = Color(0xFF1E293B)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF38BDF8)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Membalas ${target.flag} ${target.name}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                            )
                            Text(
                                target.text,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            "✕",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { replyingTo = null }
                                .padding(6.dp),
                        )
                    }
                }
            }
        }

        // =========================================================
        // MENU LAMPIRAN CEPAT (FOTO / GAMBAR / FILE / STIKER)
        // =========================================================
        AnimatedVisibility(visible = showAttachMenu && isLoggedIn) {
            Surface(color = Color(0xFF1E293B)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    AttachOptionPill("📷 Foto / Gambar", Color(0xFF0284C7)) {
                        showAttachMenu = false
                        pickImageLauncher.launch("image/*")
                    }
                    AttachOptionPill("📎 File / Dokumen", Color(0xFF4F46E5)) {
                        showAttachMenu = false
                        pickFileLauncher.launch("*/*")
                    }
                    AttachOptionPill("🎨 Stiker XyDesk", Color(0xFFD97706)) {
                        showAttachMenu = false
                        showStickerPicker = true
                    }
                }
            }
        }

        // =========================================================
        // GATE WAJIB LOGIN GITHUB ATAU KOMPOSER PESAN WHATSAPP-STYLE
        // =========================================================
        if (!isLoggedIn) {
            Surface(
                color = Color(0xFF0F172A),
                tonalElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        GitHubLogoIcon(size = 20.dp, tint = Color(0xFF38BDF8))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Login GitHub Wajib untuk Chat Global",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Text(
                            "Masuk 1-klik dengan GitHub (tanpa ketik kode) untuk mulai ngobrol, kirim foto, file, & stiker.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8),
                        )
                    }
                    XyPillButton(
                        text = "Login GitHub",
                        onClick = { loginModalOpen = true },
                        primary = true,
                        compact = true,
                    )
                }
            }
        } else {
            Surface(
                color = Color(0xFF0F172A),
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Tombol Stiker
                    Surface(
                        onClick = {
                            showStickerPicker = !showStickerPicker
                            showAttachMenu = false
                        },
                        color = if (showStickerPicker) Color(0xFF2563EB) else Color(0xFF1E293B),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("😊", fontSize = 17.sp)
                        }
                    }

                    // Tombol Attach File / Foto
                    Surface(
                        onClick = {
                            showAttachMenu = !showAttachMenu
                            showStickerPicker = false
                        },
                        color = if (showAttachMenu) Color(0xFF2563EB) else Color(0xFF1E293B),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📎", fontSize = 17.sp)
                        }
                    }

                    OutlinedTextField(
                        value = draft,
                        onValueChange = { value ->
                            val wasEmpty = draft.text.isEmpty()
                            draft = value
                            val now = System.currentTimeMillis()
                            if (value.text.isNotEmpty() && now - typingSentAt > 2500) {
                                typingSentAt = now
                                chatSocketSend(JSONObject().put("type", "typing").put("on", true).toString())
                            } else if (value.text.isEmpty() && !wasEmpty) {
                                chatSocketSend(JSONObject().put("type", "typing").put("on", false).toString())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Ketik pesan (@$nickname)…",
                                color = Color(0xFF64748B),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                        ),
                    )

                    // Tombol Kirim Bulat (Selalu Aktif saat ada teks — pakai HTTP + WS)
                    val canSend = draft.text.isNotBlank() && !sending
                    Surface(
                        onClick = {
                            val text = draft.text.trim()
                            if (text.isNotEmpty()) {
                                draft = TextFieldValue("")
                                dispatchChatPayload(text = text)
                            }
                        },
                        enabled = canSend,
                        color = if (canSend) Color(0xFF2563EB) else Color(0xFF1E293B),
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "➤",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canSend) Color.White else Color(0xFF64748B),
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog Pemilih Bahasa Terjemahan (Support Semua Bahasa)
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
                    "Pilih bahasa terjemahan baris bawah di setiap bubble pesan (mendukung Thailand, Jepang, Korea, Mandarin, Arab, Rusia, Inggris, Indonesia, dll.):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(SUPPORTED_VIEWER_LANGS, key = { it.code }) { opt ->
                        XyPillButton(
                            text = "${opt.flag}  ${opt.label}",
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

    // Dialog Login GitHub Instan (Tanpa Ketik Kode)
    if (loginModalOpen) {
        XyOverlay(
            title = "Login GitHub Instan",
            onDismiss = { loginModalOpen = false },
        ) {
            GitHubDeviceFlowPanel(
                onCancel = { loginModalOpen = false },
                onSuccess = { tok, user ->
                    runCatching {
                        CredentialVault(context.applicationContext).put(XyRdpConfig.VAULT_PAT_ID, tok)
                    }
                    saveVerifiedGitHubLogin(context, user)
                    verifiedGhLogin = user
                    nickname = chatNickname(context)
                    loginModalOpen = false
                    chatSocketSend(
                        JSONObject()
                            .put("type", "hello")
                            .put("name", nickname)
                            .put("country", myCountry)
                            .toString(),
                    )
                },
                onError = {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    // Penampil Foto / Gambar Fullscreen
    fullscreenImageUri?.let { dataUri ->
        XyOverlay(
            title = "Pratinjau Gambar",
            onDismiss = { fullscreenImageUri = null },
        ) {
            val bmp = remember(dataUri) { decodeDataUriBitmap(dataUri) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 380.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
                XyPillButton(
                    text = "Tutup",
                    onClick = { fullscreenImageUri = null },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AttachOptionPill(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = color.copy(alpha = 0.22f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * Bubble Pesan Gaya WhatsApp Modern (Palet XyDesk Deep Indigo & Royal Blue)
 * dengan dukungan:
 * - **Swipe-to-Reply** (geser bubble ke kanan untuk langsung mengutip & membalas)
 * - **Kotak Kutipan Balasan (Quoted Reply)** di bagian atas bubble
 * - **Lampiran Foto/Gambar, File/Dokumen (unduh 1-klik), atau Stiker Ekspresif**
 * - **2 Bahasa di dalam 1 Bubble yang dipisah garis horizontal**
 */
@Composable
private fun ChatBubbleWhatsAppStyle(
    msg: ChatMessage,
    onReply: () -> Unit,
    onOpenImage: (String) -> Unit,
    onSaveFile: (ChatMessage) -> Unit,
) {
    val time = remember(msg.at) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.at))
    }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val founderMsg = msg.isFounder || isFounderAccount(msg.name)
    val bubbleShape = RoundedCornerShape(
        topStart = if (msg.mine) 18.dp else 4.dp,
        topEnd = if (msg.mine) 4.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp,
    )
    val bubbleBg = if (msg.mine) {
        Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)))
    } else if (founderMsg) {
        Brush.linearGradient(listOf(Color(0xFF1E1B4B), Color(0xFF172554)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
    }
    val dividerColor = Color.White.copy(alpha = 0.18f)
    val transLabel = msg.targetLang.ifBlank { "EN" }.uppercase(Locale.ROOT)
    val transBody = msg.translated.ifBlank { msg.text }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(msg.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffsetX > 85f) {
                            onReply()
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        val next = (dragOffsetX + dragAmount).coerceIn(0f, 150f)
                        dragOffsetX = next
                    },
                )
            },
    ) {
        // Ikon indikator Swipe-to-Reply di sebelah kiri saat digeser
        if (dragOffsetX > 12f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center,
            ) {
                Text("↩", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffsetX.roundToInt(), 0) },
            horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            // Avatar bulat di sebelah kiri untuk pesan orang lain
            if (!msg.mine) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (founderMsg) Color(0xFF1E1B4B) else Color(0xFF334155),
                        )
                        .let { m ->
                            if (founderMsg) m.border(1.2.dp, founderBorderBrush(), CircleShape) else m
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        msg.name.removePrefix("@").take(1).uppercase(Locale.ROOT),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (founderMsg) Color(0xFFFDE68A) else Color.White,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }

            Column(
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 315.dp)
                    .shadow(2.dp, bubbleShape)
                    .clip(bubbleShape)
                    .background(bubbleBg, bubbleShape)
                    .let { mod ->
                        if (founderMsg) mod.border(1.3.dp, founderBorderBrush(), bubbleShape)
                        else mod.border(0.8.dp, Color.White.copy(alpha = 0.08f), bubbleShape)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // Header Pengirim: Bendera + Nama + Negara + Badge Founder + Tombol Balas Cepat
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
                            text = "${msg.flag} ${msg.name} · ${msg.country}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (founderMsg) Color(0xFFFDE68A)
                            else if (msg.mine) Color(0xFFBAE6FD)
                            else Color(0xFF38BDF8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (founderMsg) {
                            XyFounderBadge(compact = true)
                        }
                    }
                    Text(
                        text = "↩",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReply() }
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }

                // Kotak Kutipan Balasan (Quoted Reply ala WhatsApp)
                if (msg.replyToName.isNotBlank() || msg.replyToText.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.24f))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(26.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF38BDF8)),
                        )
                        Spacer(Modifier.width(7.dp))
                        Column {
                            Text(
                                msg.replyToName.ifBlank { "Pesan" },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                            )
                            Text(
                                msg.replyToText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.80f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Render Lampiran: Stiker / Foto / File
                if (msg.attachmentType == "sticker") {
                    val presetId = msg.attachmentData.removePrefix("sticker:")
                    val preset = XY_STICKERS.firstOrNull { it.id == presetId }
                    Spacer(Modifier.height(6.dp))
                    if (preset != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.linearGradient(listOf(preset.c1, preset.c2)))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(preset.emoji, fontSize = 30.sp)
                            Column {
                                Text(
                                    preset.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                                Text(
                                    preset.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.88f),
                                )
                            }
                        }
                    }
                } else if (msg.attachmentType == "image" && msg.attachmentData.startsWith("data:image")) {
                    val bmp = remember(msg.id, msg.attachmentData) {
                        decodeDataUriBitmap(msg.attachmentData)
                    }
                    if (bmp != null) {
                        Spacer(Modifier.height(6.dp))
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = msg.attachmentName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 110.dp, max = 210.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenImage(msg.attachmentData) },
                        )
                    }
                } else if (msg.attachmentType == "file" && msg.attachmentData.startsWith("data:")) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable { onSaveFile(msg) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("📄", fontSize = 22.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                msg.attachmentName.ifBlank { "dokumen" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${(msg.attachmentSize / 1024).coerceAtLeast(1)} KB · Ketuk untuk Simpan",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Text(
                            "⬇",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8),
                        )
                    }
                }

                // Teks Asli + Garis Pemisah + Terjemahan Otomatis (2 chat di dalam 1 bubble)
                if (msg.attachmentType != "sticker") {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )

                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(dividerColor),
                    )
                    Spacer(Modifier.height(5.dp))

                    Text(
                        text = "🌐 $transLabel · $transBody",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFFE2E8F0).copy(alpha = 0.92f),
                    )
                }

                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (msg.mine) "$time ✓✓" else time,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.68f),
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

private var activeSocket: WebSocket? = null

private fun chatSocketSend(rawJson: String) {
    runCatching { activeSocket?.send(rawJson) }
}

/**
 * Mesin Terjemahan Multi-Bahasa (`Support All Languages`):
 * Mendukung deteksi & penerjemahan otomatis antara Bahasa Indonesia, Inggris,
 * Thailand (`th`), Jepang (`ja`), Korea (`ko`), Mandarin (`zh-CN`), Vietnam (`vi`),
 * Tagalog (`tl`), Melayu (`ms`), Arab (`ar`), Rusia (`ru`), Hindi (`hi`), Spanyol (`es`),
 * Portugis (`pt`), Jerman (`de`), Prancis (`fr`), dan Turki (`tr`).
 *
 * Mengembalikan `Triple(translatedText, targetLangLabel, detectedSourceLang)`.
 */
internal fun translateClientSideMultiLang(
    text: String,
    senderCountry: String,
    viewerLangPref: String = "auto",
): Triple<String, String, String>? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    return runCatching {
        val preferredTarget = if (viewerLangPref == "auto" || viewerLangPref.isBlank()) "id" else viewerLangPref
        val firstPass = callGoogleNeuralTranslate(clean, preferredTarget) ?: return null
        val detected = firstPass.second.lowercase(Locale.ROOT)
        val isSameAsTarget = detected == preferredTarget.lowercase(Locale.ROOT) ||
            (preferredTarget == "id" && (detected == "id" || detected == "in" || CLIENT_ID_WORD_REGEX.containsMatchIn(clean)))
        if (isSameAsTarget && viewerLangPref == "auto") {
            val secondTarget = if (preferredTarget.startsWith("en")) "id" else "en"
            val secondPass = callGoogleNeuralTranslate(clean, secondTarget) ?: firstPass
            Triple(secondPass.first, secondTarget.uppercase(Locale.ROOT), detected)
        } else {
            Triple(firstPass.first, preferredTarget.uppercase(Locale.ROOT), detected)
        }
    }.getOrNull()
}

private fun callGoogleNeuralTranslate(text: String, targetLang: String): Pair<String, String>? {
    val q = URLEncoder.encode(text, "UTF-8")
    // Jalur 1: clients5.google.com (client=dict-chrome-ex, bebas limit 429 & presisi semua bahasa)
    runCatching {
        val url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=auto&tl=$targetLang&q=$q"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            )
        }
        if (conn.responseCode in 200..299) {
            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()
            val arr = JSONArray(raw)
            val first = arr.optJSONArray(0)
            if (first != null && first.length() >= 1) {
                val tr = first.optString(0, "").trim()
                val det = first.optString(1, "auto").trim()
                if (tr.isNotEmpty()) return Pair(tr, det)
            }
        } else {
            conn.disconnect()
        }
    }

    // Jalur 2: translate.googleapis.com dengan client=dict-chrome-ex
    runCatching {
        val url = "https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=auto&tl=$targetLang&dt=t&q=$q"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) Chrome/131.0.0.0 Mobile")
        }
        if (conn.responseCode in 200..299) {
            val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            conn.disconnect()
            val root = JSONArray(raw)
            val parts = root.optJSONArray(0)
            if (parts != null) {
                val sb = StringBuilder()
                for (i in 0 until parts.length()) {
                    val seg = parts.optJSONArray(i) ?: continue
                    val chunk = seg.optString(0, "")
                    if (chunk.isNotEmpty()) sb.append(chunk)
                }
                val det = root.optString(2, "auto")
                val out = sb.toString().trim()
                if (out.isNotEmpty()) return Pair(out, det)
            }
        } else {
            conn.disconnect()
        }
    }
    return null
}

private fun decodeDataUriBitmap(dataUri: String): Bitmap? = runCatching {
    val comma = dataUri.indexOf(',')
    if (comma < 0) return@runCatching null
    val bytes = Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

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
 * Ronde 52 — Floating Bubble Chat Global Saat Sesi RDP / PC Stream Berlangsung.
 *
 * Menampilkan gelembung mengambang (bisa digeser bebas di layar sesi) dengan
 * indikator pesan baru; ketika diketuk langsung membuka jendela `GlobalChatScreen`
 * di atas sesi tanpa memutus koneksi RDP.
 */
@Composable
internal fun FloatingSessionChatOverlay(
    modifier: Modifier = Modifier,
) {
    var chatWindowOpen by remember { mutableStateOf(false) }
    var bubbleOffsetX by remember { mutableFloatStateOf(24f) }
    var bubbleOffsetY by remember { mutableFloatStateOf(220f) }

    Box(modifier = modifier.fillMaxSize()) {
        // Tombol Bubble Mengambang (Draggable)
        if (!chatWindowOpen) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()) }
                    .size(46.dp)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF2563EB), Color(0xFF0284C7)),
                        ),
                    )
                    .border(1.5.dp, Color(0xFFBAE6FD).copy(alpha = 0.75f), CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            bubbleOffsetX = (bubbleOffsetX + dragAmount.x).coerceIn(8f, 900f)
                            bubbleOffsetY = (bubbleOffsetY + dragAmount.y).coerceIn(48f, 1800f)
                        }
                    }
                    .clickable { chatWindowOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("💬", fontSize = 20.sp)
            }
        }

        // Jendela Overlay Chat Global di atas Sesi RDP
        if (chatWindowOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { chatWindowOpen = false }
                    .padding(horizontal = 16.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 460.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .shadow(16.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                        .clickable(enabled = false) {},
                ) {
                    GlobalChatScreen(
                        onBack = { chatWindowOpen = false },
                        isOverlayMode = true,
                    )
                }
            }
        }
    }
}
