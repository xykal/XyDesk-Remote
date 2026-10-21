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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import id.xydesk.remote.ui.components.XyEmptyState
import id.xydesk.remote.ui.components.XyLoadingSkeletonCard
import id.xydesk.remote.ui.components.XyLoadingSpinnerState
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
import androidx.compose.material3.TextButton
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
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
 *   ikon balas vektor, stiker vektor, lampiran vektor, kamera vektor, bagikan vektor, dan perisai keamanan pesan.
 * - **Koleksi Stiker Ilustrasi AI Unik** (`sticker_xy_gg`, `sticker_xy_laugh`, `sticker_xy_fire`,
 *   `sticker_xy_chill`, `sticker_xy_sad`, `sticker_xy_king`).
 * - **Tahan (Long-Press) Bubble Chat**: otomatis memfokuskan bubble yang ditahan, memberikan
 *   efek **Blur Background** di belakangnya, dan memunculkan **Menu Konteks ala iPhone / WhatsApp**.
 * - **Privasi Member Baru (`joined_at`)**: pengguna yang baru bergabung tidak dapat melihat
 *   riwayat chat sebelum waktu bergabungnya.
 * - **Proteksi Transit Pesan (`XY-E2EE-GCM-v1`: AES-256-GCM + HMAC-SHA256)** — enkripsi &
 *   integritas saat transit via WSS/TLS; BUKAN E2EE sejati (lihat catatan di `ChatE2eeCrypto`).
 * - **Total Online Aplikasi**: dihitung dari seluruh pengguna yang sedang membuka aplikasi XyDesk Remote.
 * - **Fitur Share Chat Global** langsung dari header maupun dari menu tahan bubble.
 */

private const val CHAT_WS_URL = "wss://api.xydeskremote.biz.id/ws/chat"
private const val CHAT_HISTORY_URL = "https://api.xydeskremote.biz.id/api/chat/history"
private const val CHAT_PREFS = "xydesk.global.chat"
private const val KEY_NICKNAME = "nickname"
private const val KEY_TARGET_LANG = "viewer_target_lang"
private const val KEY_VIEWED_ONCE_IDS = "viewed_once_ids"
private const val KEY_HIDDEN_MSG_IDS = "hidden_msg_ids_v1"
private const val KEY_BUBBLE_STYLE = "chat_bubble_style_v1"
private const val KEY_BUBBLE_SHAPE = "chat_bubble_shape_v1"
private const val KEY_BUBBLE_FLOATING = "chat_bubble_floating_v1"
private const val KEY_AUTO_DOWNLOAD_MEDIA = "chat_auto_download_media_v1"
private const val KEY_HD_MEDIA_ENABLED = "chat_hd_media_enabled_v1"
private const val KEY_AUTOPLAY_VIDEO = "chat_autoplay_video_v1"
private const val KEY_NOTIF_TONE = "chat_notif_tone_v1"

private val chatDecodedBitmapCache = object : android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(1)
}

private data class PendingStudioMedia(
    val attachmentType: String, // "image", "video", "file"
    val attachmentName: String,
    val attachmentMime: String,
    val attachmentSize: Int,
    val attachmentData: String,
    val previewBitmap: Bitmap? = null,
    val viewOnce: Boolean = false,
)


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
    return 0L
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

internal suspend fun warmUpGlobalChatAndFeedCache(context: Context) {
    val appCtx = context.applicationContext
    withContext(Dispatchers.IO) {
        runCatching {
            val api = CommunityJokesApi(appCtx)
            if (CommunityJokesApi.inMemoryFeedCache.isNullOrEmpty()) {
                api.cachedFeed()
            }
            val freshFeed = runCatching { api.fetchFeed() }.getOrNull()
            if (!freshFeed.isNullOrEmpty()) {
                api.cacheFeed(freshFeed)
            }
        }
        runCatching {
            val login = getVerifiedGitHubLogin(appCtx).orEmpty()
            val nick = chatNickname(appCtx)
            if (GlobalChatMemoryCache.messages.isEmpty()) {
                val disk = GlobalChatOfflineCache.read(appCtx, login, nick)
                if (disk.isNotEmpty()) {
                    GlobalChatMemoryCache.messages.clear()
                    GlobalChatMemoryCache.messages.addAll(disk.takeLast(80))
                }
            }
            val cid = URLEncoder.encode(CommunityJokesApi(appCtx).accountShortId(), "UTF-8")
            val gh = URLEncoder.encode(login, "UTF-8")
            val url = "$CHAT_HISTORY_URL?joined_at=0&client_id=$cid&github_login=$gh"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 7_000
                readTimeout = 7_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "keep-alive")
            }
            try {
                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val pinned = parsePinnedMessageFromJson(json.optJSONObject("pinned_message"))
                    if (json.has("pinned_message")) {
                        GlobalChatMemoryCache.pinnedMessage = pinned
                    }
                    val arr = json.optJSONArray("messages")
                    if (arr != null && arr.length() > 0) {
                        val parsedList = ArrayList<ChatMessage>(arr.length())
                        for (i in 0 until arr.length()) {
                            val m = arr.optJSONObject(i) ?: continue
                            parsedList.add(parseMessageFromJson(m, nick, login))
                        }
                        if (parsedList.isNotEmpty()) {
                            GlobalChatMemoryCache.messages.clear()
                            GlobalChatMemoryCache.messages.addAll(parsedList.takeLast(80))
                            val epoch = GlobalChatOfflineCache.nextWriteEpoch(login)
                            GlobalChatOfflineCache.write(appCtx, login, parsedList.takeLast(80), epoch)
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}

@Composable
internal fun GlobalChatScreen(
    onBack: () -> Unit,
    isOverlayMode: Boolean = false,
    onHeaderDrag: ((Float, Float) -> Unit)? = null,
    isCompactFloating: Boolean = false,
    onToggleCompactFloating: (() -> Unit)? = null,
) {
    val account = rememberVerifiedChatAccount(LocalContext.current)
    androidx.compose.runtime.key(normalizedChatAccount(account)) {
        GlobalChatAccountContent(onBack, isOverlayMode, onHeaderDrag, isCompactFloating, onToggleCompactFloating)
    }
}

@Composable
private fun GlobalChatAccountContent(
    onBack: () -> Unit,
    isOverlayMode: Boolean,
    onHeaderDrag: ((Float, Float) -> Unit)?,
    isCompactFloating: Boolean,
    onToggleCompactFloating: (() -> Unit)?,
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
    val oauthRev = GitHubDirectOAuthController.authRevision
    LaunchedEffect(oauthRev) {
        val currentUser = getVerifiedGitHubLogin(context)
        if (currentUser != verifiedGhLogin) {
            verifiedGhLogin = currentUser
            nickname = chatNickname(context)
            firstJoinedAtMs = ensureFirstJoinedAtMs(context, currentUser)
        }
    }
    val hiddenIdsKey = chatAccountPreferenceKey(KEY_HIDDEN_MSG_IDS, verifiedGhLogin)
    val viewedIdsKey = chatAccountPreferenceKey(KEY_VIEWED_ONCE_IDS, verifiedGhLogin)
    val myCountry = remember { chatCountryCode(context) }
    val myFlag = remember(myCountry) { countryCodeToFlag(myCountry) }
    var viewerTargetLang by remember {
        mutableStateOf(chatPrefs.getString(KEY_TARGET_LANG, "auto") ?: "auto")
    }

    var showLangPicker by remember { mutableStateOf(false) }
    var showChatSettings by remember { mutableStateOf(false) }
    var showOutbox by remember(verifiedGhLogin) { mutableStateOf(false) }
    var outboxIssue by remember(verifiedGhLogin) { mutableStateOf<String?>(null) }
    var outboxBinding by remember(verifiedGhLogin) { mutableStateOf("") }
    if (showOutbox && !verifiedGhLogin.isNullOrBlank() && outboxBinding.isNotBlank()) {
        androidx.compose.runtime.key(verifiedGhLogin, outboxBinding) {
            GlobalChatOutboxDialog(verifiedGhLogin.orEmpty(), outboxBinding, onClose = { showOutbox = false })
        }
    }
    var historyIssue by remember(verifiedGhLogin) { mutableStateOf<String?>(null) }
    var connectionIssue by remember(verifiedGhLogin) { mutableStateOf<String?>(null) }
    var historyRetryAt by remember(verifiedGhLogin) { mutableStateOf(0L) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var showAttachMenu by remember { mutableStateOf(false) }
    var viewOnceMediaMode by remember { mutableStateOf(false) }
    var pendingStudioMedia by remember { mutableStateOf<PendingStudioMedia?>(null) }
    var authBusy by remember { mutableStateOf(false) }
    var isAnnouncementMode by remember { mutableStateOf(false) }

    // Settings Chat Global (Tersimpan di SharedPreferences)
    var bubbleStyleMode by remember {
        mutableStateOf(chatPrefs.getString(KEY_BUBBLE_STYLE, "monochrome") ?: "monochrome")
    }
    var bubbleShapePreset by remember {
        mutableStateOf(chatPrefs.getString(KEY_BUBBLE_SHAPE, "TAIL_CLASSIC") ?: "TAIL_CLASSIC")
    }
    var bubbleFloatingElement by remember {
        mutableStateOf(chatPrefs.getString(KEY_BUBBLE_FLOATING, "NONE") ?: "NONE")
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
    var pinnedMessage by remember(verifiedGhLogin) {
        mutableStateOf<PinnedChatMessage?>(GlobalChatMemoryCache.pinnedMessage)
    }
    var focusedContextMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by remember { mutableStateOf("") }
    val failedMutations = remember { mutableStateMapOf<String, Pair<String, String>>() }

    var inspectingE2eeMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var fullscreenMediaMsg by remember { mutableStateOf<ChatMessage?>(null) }

    val hiddenMessageIds = remember(verifiedGhLogin) {
        mutableStateListOf<String>().apply {
            val saved = chatPrefs.getStringSet(hiddenIdsKey, chatPrefs.getStringSet(KEY_HIDDEN_MSG_IDS, emptySet())).orEmpty()
            addAll(saved)
        }
    }

    val messages = remember(verifiedGhLogin) {
        mutableStateListOf<ChatMessage>().apply {
            if (GlobalChatMemoryCache.messages.isNotEmpty()) {
                val hiddenSet = hiddenMessageIds.toSet()
                addAll(
                    GlobalChatMemoryCache.messages
                        .filter { it.id !in hiddenSet }
                        .distinctBy { it.id },
                )
            }
        }
    }
    var cacheWriteEpoch by remember { mutableStateOf(GlobalChatOfflineCache.nextWriteEpoch(verifiedGhLogin.orEmpty())) }

    LaunchedEffect(verifiedGhLogin) {
        val login = verifiedGhLogin.orEmpty()
        val cached = withContext(Dispatchers.IO) {
            GlobalChatOfflineCache.read(context.applicationContext, login, nickname)
        }
        // Network may have completed while disk was being read; don't replace fresher state.
        val hidden = chatPrefs.getStringSet(hiddenIdsKey, chatPrefs.getStringSet(KEY_HIDDEN_MSG_IDS, emptySet())).orEmpty()
        if (messages.isEmpty() && cached.isNotEmpty()) {
            val filtered = cached.filter { it.id !in hidden }.distinctBy { it.id }
            messages.addAll(filtered)
            GlobalChatMemoryCache.messages.clear()
            GlobalChatMemoryCache.messages.addAll(filtered.takeLast(80))
        }
        snapshotFlow { cacheWriteEpoch to messages.toList() }.collectLatest { (epoch, snapshot) ->
            delay(1200L)
            withContext(Dispatchers.IO) {
                GlobalChatOfflineCache.write(context.applicationContext, login, snapshot, epoch)
            }
        }
    }
    val viewedOnceIds = remember(verifiedGhLogin) {
        mutableStateListOf<String>().apply {
            val saved = chatPrefs.getStringSet(viewedIdsKey, chatPrefs.getStringSet(KEY_VIEWED_ONCE_IDS, emptySet())).orEmpty()
            addAll(saved)
        }
    }
    val messageReactions = remember { mutableStateMapOf<String, String>() }
    val customStickers = remember { mutableStateListOf<CustomCryptoSticker>() }

    fun markMediaViewedOnce(msgId: String) {
        if (msgId.isNotBlank() && msgId !in viewedOnceIds) {
            viewedOnceIds.add(msgId)
            chatPrefs.edit().putStringSet(viewedIdsKey, viewedOnceIds.toSet()).apply()
        }
    }

    fun deleteMessageForMe(target: ChatMessage) {
        fun hideLocal(ids: Set<String>) {
            ids.filter { it.isNotBlank() && it !in hiddenMessageIds }.forEach { hiddenMessageIds.add(it) }
            chatPrefs.edit().putStringSet(hiddenIdsKey, hiddenMessageIds.toSet()).apply()
            messages.removeAll { it.id in ids }
        }
        if (target.id.startsWith("local-")) {
            val account = verifiedGhLogin.orEmpty()
            val binding = outboxBinding
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) { GlobalChatOutbox.cancelPending(context, target.id, account, binding) }
                    if (binding != outboxBinding || !chatAccountSnapshotIsCurrent(account, getVerifiedGitHubLogin(context))) return@launch
                    val serverId = result?.takeIf { it.state == "accepted" }?.receipt?.let { JSONObject(it).optString("id") }.orEmpty()
                    hideLocal(setOf(target.id, serverId))
                    Toast.makeText(context, "Antrean lokal dihapus setelah status tersimpan. Pesan yang sudah masuk server tidak ditarik.", Toast.LENGTH_LONG).show()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { Toast.makeText(context, "Pembatalan belum tersimpan. Pesan tetap terlihat; buka Antrean untuk memeriksa akun/penyimpanan.", Toast.LENGTH_LONG).show() }
            }
            return
        }
        hideLocal(setOf(target.id))
        Toast.makeText(context, "Pesan dihapus untuk saya", Toast.LENGTH_SHORT).show()
    }

    fun applyIncomingMutation(incoming: ChatMessage) {
        val index = messages.indexOfFirst { it.id == incoming.id }
        if (index >= 0) {
            if (incoming.revision > messages[index].revision) failedMutations.remove(incoming.id)
            messages[index] = reconcileChatMessage(messages[index], incoming)
        } else {
            if (incoming.id in hiddenMessageIds || incoming.at < firstJoinedAtMs - 2_000L) return
            // A mutation can beat initial history parsing; keep its revision so old history cannot resurrect text.
            messages.add(incoming)
            messages.sortBy { it.at }
            if (messages.size > 100) messages.removeAt(0)
        }
        if (incoming.deleted) {
            id.xydesk.remote.push.ChatPushNotifier.retractMessage(context, incoming.id)
            messages.indices.forEach { i ->
                if (messages[i].replyToId == incoming.id) messages[i] = messages[i].copy(replyToText = "Pesan ini dihapus")
            }
            if (replyingTo?.id == incoming.id) replyingTo = null
            if (fullscreenMediaMsg?.id == incoming.id) fullscreenMediaMsg = null
        }
        if (pinnedMessage?.id == incoming.id) pinnedMessage = null
        // Persist ACK without the normal debounce; obsolete queued snapshots cannot restore old text.
        cacheWriteEpoch = GlobalChatOfflineCache.nextWriteEpoch(verifiedGhLogin.orEmpty())
        GlobalChatOfflineCache.persistConfirmed(context, verifiedGhLogin.orEmpty(), messages.toList(), cacheWriteEpoch)
    }

    fun mutateMessage(target: ChatMessage, action: String, text: String = "") {
        val index = messages.indexOfFirst { it.id == target.id }
        if (index < 0 || messages[index].revision != target.revision || !canMutateChatMessage(messages[index], verifiedGhLogin)) {
            Toast.makeText(context, "Pesan/status sudah berubah. Salin draf edit bila perlu, lalu buka ulang pesan terbaru.", Toast.LENGTH_LONG).show()
            return
        }
        editingMessage = null
        val original = messages[index]
        val operation = java.util.UUID.randomUUID().toString()
        val account = verifiedGhLogin
        val now = System.currentTimeMillis()
        val optimistic = if (action == "delete") original.deletedPlaceholder(now)
            else original.copy(text = text.trim(), translated = "", editedAt = now, e2eeSig = "", e2eeVerified = false)
        messages[index] = optimistic.copy(mutationPending = true, mutationId = operation, mutationError = "")
        failedMutations.remove(target.id)
        focusedContextMsg = null
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    check(normalizedChatAccount(getVerifiedGitHubLogin(context)) == normalizedChatAccount(account))
                    val token = GlobalChatRepository.token(context)?.takeIf { it.isNotBlank() } ?: error("Login kembali diperlukan")
                    val body = JSONObject().put("id", original.id).put("expected_revision", original.revision)
                        .put("operation_id", operation).put("text", text.trim())
                    val json = GlobalChatRepository.mutate(if (action == "delete") CHAT_DELETE_URL else CHAT_EDIT_URL, body, token)
                    val incoming = parseMessageFromJson(json, nickname, account)
                    check(json.optString("last_mutation_id") == operation && isConfirmedChatMutation(original, incoming, action, text))
                    incoming
                }
            }
            outcome.onSuccess { incoming ->
                applyIncomingMutation(incoming)
                failedMutations.remove(target.id)
            }.onFailure {
                val current = messages.indexOfFirst { it.id == original.id }
                // A newer WS/history revision wins over a late HTTP failure.
                if (current >= 0 && messages[current].mutationId == operation) {
                    messages[current] = original.copy(mutationError = "Perubahan belum terkonfirmasi · coba lagi")
                    failedMutations[original.id] = action to text
                    Toast.makeText(context, "Perubahan belum terkonfirmasi. Tampilan dipulihkan; tekan lama untuk coba lagi. Jika ditolak, sinkronkan ulang atau login kembali.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun deleteMessageForEveryone(target: ChatMessage) = mutateMessage(target, "delete")

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
                    GlobalChatRepository.postAction(CHAT_PIN_URL, payload)
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
                    GlobalChatRepository.postAction(CHAT_PIN_URL, payload)
                }
            }
            Toast.makeText(context, "Sematan pesan dilepas", Toast.LENGTH_SHORT).show()
        }
    }

    val appWideOnline by AppUpdate.appWideOnlineCount.collectAsState()
    var roomPresence by remember { mutableIntStateOf(1) }
    val totalOnline = maxOf(appWideOnline, roomPresence, 1)

    var typingName by remember { mutableStateOf<String?>(null) }
    var connected by remember(verifiedGhLogin) { mutableStateOf(false) }
    var socketConnected by remember(verifiedGhLogin) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val chatMotionEnabled by androidx.compose.runtime.rememberUpdatedState(id.xydesk.remote.ui.theme.LocalUiMotionEnabled.current)
    var didInitialScroll by remember(verifiedGhLogin) { mutableStateOf(false) }
    var newArrivals by remember(verifiedGhLogin) { mutableIntStateOf(0) }
    val sentSoundGate = remember(verifiedGhLogin) { ChatSentSoundGate() }
    val sentSound = remember(verifiedGhLogin) { ChatSendSoundPlayer(context) }
    val soundLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(sentSound) { onDispose { sentSound.close() } }
    fun confirmOwnSend(serverId: String) {
        if (sentSoundGate.accept(serverId)) sentSound.play(notifSoundTone != "silent" && soundLifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
    }
    LaunchedEffect(verifiedGhLogin, oauthRev) {
        GlobalChatOutbox.resume(context.applicationContext)
        GlobalChatOutbox.revision.collectLatest {
            val snapshot = withContext(Dispatchers.IO) { GlobalChatOutbox.snapshot(context.applicationContext) }
            if (!chatAccountSnapshotIsCurrent(verifiedGhLogin, getVerifiedGitHubLogin(context))) return@collectLatest
            outboxBinding = snapshot.binding
            outboxIssue = snapshot.issue
            snapshot.entries.forEach { entry ->
                if (entry.state != "pending") {
                    // Receipt-only acceptance/trim/cancel never reconstructs stale queued content.
                    messages.removeAll { it.id == entry.id }
                } else if (entry.id !in hiddenMessageIds) {
                    val index = messages.indexOfFirst { it.id == entry.id }
                    if (index >= 0) messages[index] = messages[index].copy(
                        deliveryTicks = if (entry.nextAttemptAt == Long.MAX_VALUE) -1 else 0)
                    else if (messages.none { it.clientMessageId == entry.id && it.deliveryTicks == 2 }) {
                        val restored = withContext(Dispatchers.IO) {
                            ChatOutboxPolicy.pendingMessage(entry, nickname, verifiedGhLogin.orEmpty())
                        }
                        if (chatAccountSnapshotIsCurrent(verifiedGhLogin, getVerifiedGitHubLogin(context)) &&
                            messages.none { it.id == entry.id || it.clientMessageId == entry.id && it.deliveryTicks == 2 }) {
                            messages.add(restored)
                            messages.sortBy { it.at }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(verifiedGhLogin) {
        GlobalChatOutbox.accepted.collect { acceptance ->
            if (!chatAccountSnapshotIsCurrent(acceptance.login, verifiedGhLogin) ||
                !chatAccountSnapshotIsCurrent(verifiedGhLogin, getVerifiedGitHubLogin(context)) ||
                acceptance.binding != outboxBinding) return@collect
            val incoming = acceptance.message?.let { raw ->
                withContext(Dispatchers.IO) { parseMessageFromJson(raw, nickname, verifiedGhLogin) }
            }
            if (acceptance.binding != outboxBinding ||
                !chatAccountSnapshotIsCurrent(verifiedGhLogin, getVerifiedGitHubLogin(context))) return@collect
            messages.removeAll { it.id == acceptance.localId }
            if (incoming != null && acceptance.localId !in hiddenMessageIds && incoming.id !in hiddenMessageIds) {
                val existing = messages.indexOfFirst { it.id == incoming.id }
                if (existing >= 0) messages[existing] = reconcileChatMessage(messages[existing], incoming)
                else { messages.add(incoming); messages.sortBy { it.at } }
            }
            confirmOwnSend(acceptance.receipt.getString("id"))
        }
    }
    LaunchedEffect(verifiedGhLogin, messages.isNotEmpty()) {
        if (!didInitialScroll && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
            didInitialScroll = true
        }
    }
    LaunchedEffect(verifiedGhLogin, listState) {
        snapshotFlow { !listState.canScrollForward }.collect { atBottom -> if (atBottom) newArrivals = 0 }
    }

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
                append(" di Chat Global XyDesk Remote\n")
                append("Gabung sekarang: https://www.xydeskremote.biz.id")
            }
        } else {
            "Gabung ke Chat Global XyDesk Remote! Obrolan real-time dengan terjemahan otomatis multi-bahasa & Cloud RDP gratis:\nhttps://www.xydeskremote.biz.id"
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

    // Bound network fan-out per screen; queued coroutines are cancelled on disposal.
    val translationSlots = remember { Semaphore(2) }
    val translatingKeys = remember { mutableSetOf<Triple<String, String, String>>() }
    val historyInFlight = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val wsHistoryReceived = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    fun enrichTranslationIfNeeded(msg: ChatMessage, forceRetranslate: Boolean = false) {
        if (msg.deleted || msg.mutationPending || msg.attachmentType == "sticker" || msg.text.isBlank()) return
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
        val translationKey = Triple(msg.id, prefSnapshot, msg.text)
        if (!translatingKeys.add(translationKey)) return
        scope.launch {
            try {
                val result = translationSlots.withPermit {
                    withContext(Dispatchers.IO) {
                        translateClientSideMultiLang(
                            text = msg.text,
                            senderCountry = msg.country,
                            viewerLangPref = prefSnapshot,
                            viewerCountry = myCountry,
                            forServerBroadcast = false,
                        )
                    }
                } ?: return@launch
                // Do not apply a response for an obsolete language selection.
                if (viewerTargetLang != prefSnapshot) return@launch
                val translatedText = result.first
                val targetLabel = result.second
                val detectedLang = result.third
                val updatedCountry = resolveSmartCountryClient(msg.text, detectedLang, msg.country)
                val updatedFlag = countryCodeToFlag(updatedCountry)
                run {
                    val idx = messages.indexOfFirst { it.id == msg.id }
                    if (idx >= 0) {
                        val current = messages[idx]
                        if (current.deleted || current.mutationPending || current.text != msg.text || current.revision != msg.revision) return@launch
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
            } finally {
                translatingKeys.remove(translationKey)
            }
        }
    }

    suspend fun fetchHistoryOverHttp() {
        if (System.currentTimeMillis() < historyRetryAt) return
        if (!historyInFlight.compareAndSet(false, true)) return
        val requestAccount = verifiedGhLogin
        val requestNickname = nickname
        val requestJoinedAt = firstJoinedAtMs
        try {
            withContext(Dispatchers.IO) {
                runCatching {
                    val snapshotStartedAt = System.currentTimeMillis()
                    val cid = URLEncoder.encode(communityApi.accountShortId(), "UTF-8")
                    val gh = URLEncoder.encode(requestAccount.orEmpty(), "UTF-8")
                    val url = "$CHAT_HISTORY_URL?joined_at=$requestJoinedAt&client_id=$cid&github_login=$gh"
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        requestMethod = "GET"
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        setRequestProperty("Accept", "application/json")
                    }
                    try {
                    val httpStatus = conn.responseCode
                    if (httpStatus in 200..299) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        val serverOnline = json.optInt("online_count", 0)
                        if (serverOnline > 0) {
                            AppUpdate.updateOnlineCount(serverOnline)
                        }
                        val serverPinned = parsePinnedMessageFromJson(json.optJSONObject("pinned_message"))
                        val arr = json.optJSONArray("messages") ?: return@runCatching
                        val fetched = ArrayList<ChatMessage>(arr.length())
                        for (i in 0 until arr.length()) {
                            val m = arr.optJSONObject(i) ?: continue
                            val parsed = parseMessageFromJson(m, requestNickname, requestAccount)
                            if (parsed.id !in hiddenMessageIds) {
                                fetched.add(parsed)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (!chatAccountSnapshotIsCurrent(requestAccount, verifiedGhLogin)) return@withContext
                            firstJoinedAtMs = 0L
                            historyIssue = null
                            connectionIssue = null
                            historyRetryAt = 0L
                            connected = true
                            if (serverOnline > 0) roomPresence = serverOnline
                            if (json.has("pinned_message")) {
                                pinnedMessage = serverPinned
                                GlobalChatMemoryCache.pinnedMessage = serverPinned
                            }
                            val followSnapshot = shouldFollowChat(!didInitialScroll || !listState.canScrollForward)
                            var newlyAdded = 0
                            var added = false
                            val fetchedIds = fetched.map { it.id }.toSet()
                            if (arr.length() == 0) {
                                messages.removeAll { !keepAfterEmptyChatSnapshot(it.id, it.at, snapshotStartedAt) }
                            }
                            if (fetched.isNotEmpty()) {
                                val oldestFetchedAt = fetched.minOf { it.at }
                                messages.removeAll { existing ->
                                    !existing.id.startsWith("local-") &&
                                        existing.at >= oldestFetchedAt &&
                                        existing.id !in fetchedIds &&
                                        (System.currentTimeMillis() - existing.at > 4_000L)
                                }
                            }
                            val newItemsToTranslate = ArrayList<ChatMessage>()
                            fetched.maxByOrNull { it.at }?.let { newest ->
                                id.xydesk.remote.push.ChatPushNotifier.markSeen(newest.id, newest.at, context)
                            }
                            for (item in fetched) {
                                val existingIdx = messages.indexOfFirst { it.id == item.id }
                                if (existingIdx >= 0) {
                                    if (item.revision > messages[existingIdx].revision) failedMutations.remove(item.id)
                                    val reconciled = reconcileChatMessage(messages[existingIdx], item)
                                    if (reconciled != messages[existingIdx]) {
                                        messages[existingIdx] = reconciled
                                    }
                                }
                                if (existingIdx < 0) {
                                    val tempIdx = findPendingChatIndex(messages, item)
                                    if (tempIdx >= 0) {
                                        messages[tempIdx] = item.copy(deliveryTicks = 2)
                                        if (item.clientMessageId.isNotBlank()) confirmOwnSend(item.id)
                                    } else {
                                        messages.add(item)
                                        added = true
                                        newlyAdded += 1
                                        newItemsToTranslate.add(item)
                                    }
                                }
                            }
                            newItemsToTranslate.takeLast(8).forEach { item ->
                                if (item.translated.isBlank() || viewerTargetLang != "auto") {
                                    enrichTranslationIfNeeded(item)
                                }
                            }
                            GlobalChatMemoryCache.messages.clear()
                            GlobalChatMemoryCache.messages.addAll(messages.takeLast(80))
                            if (added && messages.isNotEmpty()) {
                                if (!followSnapshot) newArrivals += newlyAdded
                                if (followSnapshot) {
                                    listState.scrollChatTo(messages.size - 1, chatMotionEnabled)
                                }
                            }
                        }
                    } else {
                        val errorText = conn.errorStream?.bufferedReader()?.use { reader ->
                            val buffer = CharArray(4096)
                            var count = 0
                            while (count < buffer.size) {
                                val n = reader.read(buffer, count, buffer.size - count)
                                if (n < 0) break
                                if (n > 0) count += n
                            }
                            String(buffer, 0, count)
                        }.orEmpty()
                        val code = runCatching { JSONObject(errorText).optString("code") }.getOrDefault("")
                        val failure = chatHistoryFailure(httpStatus, code, conn.getHeaderField("Retry-After"))
                        withContext(Dispatchers.Main) {
                            if (!chatAccountSnapshotIsCurrent(requestAccount, verifiedGhLogin)) return@withContext
                            historyIssue = failure.message
                            historyRetryAt = System.currentTimeMillis() + failure.retrySeconds * 1000L
                        }
                    }
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        } finally {
            historyInFlight.set(false)
        }
    }

    LaunchedEffect(Unit) {
        val savedGh = getVerifiedGitHubLogin(context)
        if (!savedGh.isNullOrBlank()) {
            verifiedGhLogin = savedGh
            nickname = chatNickname(context)
            firstJoinedAtMs = ensureFirstJoinedAtMs(context, savedGh)
        }
        launch { communityApi.fetchLeaderboard(reportDeltaSec = 0L) }
        launch(Dispatchers.IO) { runCatching { pruneExpiredChatViewOnceFiles(context.cacheDir) } }
        // WS-first, HTTP fallback; presence dan disk I/O stiker tetap paralel.
        launch {
            fetchHistoryOverHttp()
        }
        launch {
            if (savedGh.isNullOrBlank()) {
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
                        fetchHistoryOverHttp()
                    }
                }
            }
        }
        launch {
            val loaded = withContext(Dispatchers.IO) { CustomStickerVault.listCustomStickers(context) }
            customStickers.clear()
            customStickers.addAll(loaded)
        }
        launch {
            AppUpdate.sendAppPresencePing(context, screen = "global_chat", active = true)
        }
    }

    var lastAppliedLang by remember { mutableStateOf(viewerTargetLang) }
    LaunchedEffect(viewerTargetLang) {
        val langChanged = lastAppliedLang != viewerTargetLang
        lastAppliedLang = viewerTargetLang
        if (langChanged || viewerTargetLang != "auto") {
            messages.takeLast(18).forEach { enrichTranslationIfNeeded(it, forceRetranslate = true) }
        } else {
            messages.takeLast(10).filter { it.translated.isBlank() }.forEach {
                enrichTranslationIfNeeded(it, forceRetranslate = false)
            }
        }
    }

    // Quiet sockets remain live. Reschedule immediately when transport/account
    // changes; an HTTP success is not proof that the WebSocket is connected.
    LaunchedEffect(verifiedGhLogin, socketConnected) {
        while (true) {
            delay(chatHistoryPollDelayMs(socketConnected = socketConnected))
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
        // Preserve WS event order while history parsing suspends off-main.
        val socketEventMutex = Mutex()

        fun connect(): WebSocket {
            val cid = URLEncoder.encode(communityApi.accountShortId(), "UTF-8")
            val gh = URLEncoder.encode(verifiedGhLogin.orEmpty(), "UTF-8")
            val wsUrl = "$CHAT_WS_URL?joined_at=$firstJoinedAtMs&client_id=$cid&github_login=$gh"
            val socket = client.newWebSocket(
                Request.Builder().url(wsUrl).apply {
                    GlobalChatRepository.token(context)?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
                }.build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (closed) { webSocket.cancel(); return }
                        attempt = 0
                        main.post { if (!closed) { socketConnected = true; connected = true; connectionIssue = null } }
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
                        val snapshotReceivedAt = System.currentTimeMillis()
                        val data = try { JSONObject(text) } catch (_: Exception) { return }
                        scope.launch {
                            socketEventMutex.withLock {
                            if (closed) return@withLock
                            when (data.optString("type")) {
                                "history_unavailable" -> {
                                    val failure = chatHistoryFailure(data.optInt("http_status", 503), data.optString("code"), data.optString("retry_after"))
                                    historyIssue = failure.message
                                    historyRetryAt = System.currentTimeMillis() + failure.retrySeconds * 1000L
                                }
                                "history" -> {
                                    val srvOnline = data.optInt("online_count", 0)
                                    if (srvOnline > 0) {
                                        roomPresence = srvOnline
                                        AppUpdate.updateOnlineCount(srvOnline)
                                    }
                                    val arr: JSONArray = data.optJSONArray("messages") ?: return@launch
                                    val nameSnapshot = nickname
                                    val loginSnapshot = verifiedGhLogin
                                    val joinedSnapshot = firstJoinedAtMs
                                    val hiddenSnapshot = hiddenMessageIds.toSet()
                                    val batch = withContext(Dispatchers.Default) {
                                        buildList {
                                            for (i in 0 until arr.length()) {
                                                val m = arr.optJSONObject(i) ?: continue
                                                val parsed = parseMessageFromJson(m, nameSnapshot, loginSnapshot)
                                                if (parsed.id !in hiddenSnapshot) add(parsed)
                                            }
                                        }
                                    }
                                    wsHistoryReceived.set(true)
                                    batch.maxByOrNull { it.at }?.let { newest ->
                                        id.xydesk.remote.push.ChatPushNotifier.markSeen(newest.id, newest.at, context)
                                    }
                                    if (closed) return@withLock
                                    // Parsing suspends: preserve messages arriving meanwhile and pending sends.
                                    val newestAt = batch.maxOfOrNull { it.at } ?: 0L
                                    val batchIds = batch.mapTo(HashSet()) { it.id }
                                    batch.forEach { item ->
                                        if (findPendingChatIndex(messages, item) >= 0) confirmOwnSend(item.id)
                                    }
                                    val acknowledgedLocalIds = batch.mapNotNull { item ->
                                        findPendingChatIndex(messages, item).takeIf { it >= 0 }?.let { messages[it].id }
                                    }.toSet()
                                    val retained = messages.filter {
                                        it.id !in batchIds && it.id !in acknowledgedLocalIds && if (arr.length() == 0) {
                                            keepAfterEmptyChatSnapshot(it.id, it.at, snapshotReceivedAt)
                                        } else {
                                            it.id.startsWith("local-") || it.at > newestAt
                                        }
                                    }
                                    val followHistory = shouldFollowChat(!didInitialScroll || !listState.canScrollForward)
                                    val reconciledBatch = batch.map { incoming ->
                                        messages.firstOrNull { it.id == incoming.id }?.let {
                                            if (incoming.revision > it.revision) failedMutations.remove(incoming.id)
                                            reconcileChatMessage(it, incoming)
                                        } ?: incoming
                                    }
                                    val mergedList = (reconciledBatch + retained)
                                        .filter { it.id !in hiddenMessageIds }
                                        .distinctBy { it.id }
                                        .sortedBy { it.at }
                                    if (mergedList != messages.toList()) {
                                        messages.clear()
                                        messages.addAll(mergedList)
                                    }
                                    GlobalChatMemoryCache.messages.clear()
                                    GlobalChatMemoryCache.messages.addAll(messages.takeLast(80))
                                    messages.forEach { msgItem ->
                                        if (!msgItem.deleted) InAppLiveNotificationBus.updateLatestSnapshot(msgItem)
                                    }
                                    if (viewerTargetLang != "auto") {
                                        batch.takeLast(8).forEach { enrichTranslationIfNeeded(it) }
                                    }
                                    if (messages.isNotEmpty() && followHistory) {
                                        scope.launch { listState.scrollToItem(messages.size - 1) }
                                    }
                                }
                                "msg" -> {
                                    val followIncoming = shouldFollowChat(!didInitialScroll || !listState.canScrollForward)
                                    val incoming = parseMessageFromJson(data, nickname, verifiedGhLogin)
                                    if (incoming.id in hiddenMessageIds) return@launch
                                    id.xydesk.remote.push.ChatPushNotifier.markSeen(incoming.id, incoming.at, context)
                                    val existingIdx = messages.indexOfFirst { it.id == incoming.id }
                                    if (existingIdx >= 0) {
                                        if (incoming.revision > messages[existingIdx].revision) failedMutations.remove(incoming.id)
                                        messages[existingIdx] = reconcileChatMessage(messages[existingIdx], incoming.copy(deliveryTicks = 2))
                                    } else {
                                        val tempIdx = findPendingChatIndex(messages, incoming)
                                        if (tempIdx >= 0) {
                                            messages[tempIdx] = incoming.copy(deliveryTicks = 2)
                                            if (incoming.clientMessageId.isNotBlank()) confirmOwnSend(incoming.id)
                                        } else {
                                            messages.add(incoming)
                                            if (!followIncoming) newArrivals += 1
                                            if (!incoming.mine) {
                                                playChatNotificationTone(notifSoundTone)
                                                InAppLiveNotificationBus.pushIncomingChat(incoming)
                                            }
                                        }
                                    }
                                    enrichTranslationIfNeeded(incoming)
                                    if (messages.size > 100) messages.removeAt(0)
                                    if (followIncoming) scope.launch { listState.scrollChatTo(messages.size - 1, chatMotionEnabled) }
                                }
                                "presence" -> {
                                    val cnt = data.optInt("count", 1).coerceAtLeast(1)
                                    roomPresence = cnt
                                    AppUpdate.updateOnlineCount(cnt)
                                }
                                "pin" -> {
                                    pinnedMessage = parsePinnedMessageFromJson(data.optJSONObject("pinned_message"))
                                }
                                "mutation" -> {
                                    val payload = data.optJSONObject("message")
                                    if (payload != null) {
                                        val incoming = parseMessageFromJson(payload, nickname, verifiedGhLogin)
                                        if (incoming.id !in hiddenMessageIds) applyIncomingMutation(incoming)
                                    }
                                }
                                "delete" -> {
                                    val delId = data.optString("id", "").trim()
                                    if (delId.isNotEmpty()) {
                                        val index = messages.indexOfFirst { it.id == delId }
                                        if (index >= 0) messages[index] = messages[index].deletedPlaceholder()

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
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        val failure = chatSocketFailure(response?.code ?: 0, response?.header("Retry-After"))
                        main.post {
                            if (!closed) {
                                socketConnected = false
                                connected = false
                                connectionIssue = failure.message
                            }
                        }
                        scheduleReconnect(failure.retrySeconds * 1000L)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        main.post {
                            if (!closed) {
                                socketConnected = false
                                connected = false
                            }
                        }
                        scheduleReconnect()
                    }

                    private fun scheduleReconnect(minWaitMs: Long = 0L) {
                        if (closed) return
                        attempt += 1
                        val waitMs = maxOf(minWaitMs, minOf(12_000L, 1000L * (1 shl minOf(attempt, 3))))
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
        sending = true
        val now = System.currentTimeMillis()
        val sealed = ChatE2eeCrypto.seal(
            plainText = displayFallback,
            senderName = nickname,
            githubLogin = currentGh,
            timestampMs = now,
        )

        // Optimistic UI: tampilkan 1 centang (✓) saat dikirim, otomatis berubah jadi 2 centang (✓✓) saat server memvalidasi pesan
        val pending = ChatMessage(
            id = "local-${java.util.UUID.randomUUID()}",
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
        if (attachmentType.isEmpty()) draft = TextFieldValue("")
        replyingTo = null
        isAnnouncementMode = false
        sending = false
        messages.add(pending)
        enrichTranslationIfNeeded(pending)
        scope.launch { listState.scrollChatTo((messages.size - 1).coerceAtLeast(0), chatMotionEnabled) }
        val bindingSnapshot = outboxBinding
        scope.launch {
            try {
                val bodyJson = JSONObject().apply {
                    put("type", "msg")
                    put("name", pending.name)
                    put("github_login", currentGh)
                    put("country", pending.country)
                    put("text", displayFallback)
                    put("is_announcement", announceNow)
                    put("e2ee_v", sealed.protocol)
                    put("e2ee_cipher", sealed.cipherBase64)
                    put("e2ee_iv", sealed.ivBase64)
                    put("e2ee_sig", sealed.signatureHex)
                    put("client_msg_id", pending.id)
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
                    val av = pending.avatarUrl
                    if (!av.isNullOrBlank()) put("avatar_url", av)
                }
                val directResponse = withContext(Dispatchers.IO) {
                    runCatching {
                        GlobalChatRepository.send(bodyJson, GlobalChatRepository.token(context.applicationContext))
                    }.getOrNull()
                }
                val confirmedMsgJson = directResponse?.optJSONObject("message")
                if (confirmedMsgJson != null) {
                    val parsedConfirmed = withContext(Dispatchers.IO) {
                        parseMessageFromJson(confirmedMsgJson, nickname, currentGh).copy(deliveryTicks = 2)
                    }
                    val idx = messages.indexOfFirst { it.id == pending.id || it.id == parsedConfirmed.id }
                    if (idx >= 0) {
                        messages[idx] = parsedConfirmed
                    }
                    confirmOwnSend(parsedConfirmed.id)
                } else {
                    withContext(Dispatchers.IO) {
                        if (bindingSnapshot.isNotBlank()) {
                            GlobalChatOutbox.enqueue(context.applicationContext, currentGh, bindingSnapshot, bodyJson, now)
                        } else {
                            chatSocketSend(bodyJson.toString())
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val idx = messages.indexOfFirst { it.id == pending.id }
                if (idx >= 0) messages[idx] = messages[idx].copy(deliveryTicks = -1)
            } finally {
                chatSocketSend(JSONObject().put("type", "typing").put("on", false).toString())
            }
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
                    val decodedPreview = withContext(Dispatchers.IO) {
                        decodeBase64Bitmap(encoded.third)
                    }
                    pendingStudioMedia = PendingStudioMedia(
                        attachmentType = "image",
                        attachmentName = "foto.jpg",
                        attachmentMime = "image/jpeg",
                        attachmentSize = encoded.second,
                        attachmentData = encoded.third,
                        previewBitmap = decodedPreview,
                        viewOnce = isOnce,
                    )
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
                        if (bytes.size > 12_000_000) return@runCatching Triple("TOO_LARGE", bytes.size, "")
                        val b64 = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                        Triple("", bytes.size, "$mime|$b64")
                    }.getOrNull()
                }
                when {
                    videoPayload == null -> Toast.makeText(context, "Gagal memuat video", Toast.LENGTH_SHORT).show()
                    videoPayload.first == "TOO_LARGE" -> Toast.makeText(
                        context,
                        "Ukuran video maksimal 12 MB (gunakan fitur Potong/Cut di pratinjau sebelum kirim)",
                        Toast.LENGTH_LONG,
                    ).show()
                    else -> {
                        val isOnce = viewOnceMediaMode
                        viewOnceMediaMode = false
                        val mime = videoPayload.third.substringBefore('|')
                        val data = videoPayload.third.substringAfter('|')
                        val thumbBmp = withContext(Dispatchers.IO) {
                            runCatching {
                                val mmr = android.media.MediaMetadataRetriever()
                                mmr.setDataSource(context, uri)
                                val frame = mmr.getFrameAtTime(0)
                                mmr.release()
                                frame
                            }.getOrNull()
                        }
                        pendingStudioMedia = PendingStudioMedia(
                            attachmentType = "video",
                            attachmentName = "video.mp4",
                            attachmentMime = mime,
                            attachmentSize = videoPayload.second,
                            attachmentData = data,
                            previewBitmap = thumbBmp,
                            viewOnce = isOnce,
                        )
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
                        pendingStudioMedia = PendingStudioMedia(
                            attachmentType = "file",
                            attachmentName = filePayload.first,
                            attachmentMime = mime,
                            attachmentSize = filePayload.second,
                            attachmentData = data,
                            previewBitmap = null,
                            viewOnce = false,
                        )
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isOverlayMode) Color(0xFF0E1118) else Color(0xFF09090B)),
    ) {
        // Lapisan Utama Chat Global (Solid Matte tanpa blur/glassmorphism agar zero-lag)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .let { mod ->
                    if (isOverlayMode) mod else mod.navigationBarsPadding().imePadding()
                },
        ) {
            if (isOverlayMode) {
                // Header Solid Matte Dark (bisa di-drag untuk menggeser jendela floating chat di sesi)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141822))
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onHeaderDrag?.invoke(dragAmount.x, dragAmount.y)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    // Handle Bar Geser di Tengah Atas
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xFF3F475A)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.clickable {
                                if (messages.isNotEmpty()) {
                                    scope.launch { listState.scrollChatTo(0, chatMotionEnabled) }
                                }
                            },
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
                            // Pill Online Solid Matte
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF23293A))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981)),
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

                // Bar Status Monokrom (Mendukung Tap-to-Top: Ketuk bar ini untuk langsung ke pesan teratas)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (messages.isNotEmpty()) {
                                scope.launch { listState.scrollChatTo(0, chatMotionEnabled) }
                            }
                        }
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
                                .background(Color(0xFF22C55E)),
                        )
                        Text(
                            text = "$totalOnline Online · Ketuk ke Atas",
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
                            contentDescription = "TLS transport and encrypted local cache",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "TLS · Cache AES-GCM",
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

            if (!verifiedGhLogin.isNullOrBlank()) {
                XyDiscordVoiceStageBar(compact = isOverlayMode)
                TextButton(enabled = outboxBinding.isNotBlank(), onClick = { showOutbox = true },
                    modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(xyNow("Antrean kirim", "Send queue"))
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
                                    scope.launch { listState.scrollChatTo(idx, chatMotionEnabled) }
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

            if (!isOverlayMode) {
                OfficialChannelDirectory()
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
                } else if (!connected && messages.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        XyLoadingSkeletonCard(
                            count = if (isOverlayMode) 3 else 5,
                            itemHeight = if (isOverlayMode) 48.dp else 58.dp,
                        )
                    }
                } else if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        XyEmptyState(
                            title = xy("Ruang Chat Global Siap", "Global Chat Room Ready"),
                            subtitle = xy(
                                "Demi privasi & XyShield Anti-Virus, pesan sebelum kamu bergabung tidak ditampilkan. Pesan baru akan langsung muncul secara real-time.",
                                "For privacy & XyShield protection, messages before you joined are hidden. New messages will appear in real-time.",
                            ),
                            icon = XyIcons.Shield,
                        )
                    }
                } else {
                    val isScrollingChat by remember {
                        androidx.compose.runtime.derivedStateOf { listState.isScrollInProgress }
                    }
                    val showScrollToTop by remember {
                        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 2 }
                    }
                    val showScrollToBottom by remember(messages.size) {
                        androidx.compose.runtime.derivedStateOf { listState.canScrollForward && messages.isNotEmpty() }
                    }
                    val uniqueMessages by remember {
                        androidx.compose.runtime.derivedStateOf {
                            messages.distinctBy { it.id }
                        }
                    }
                    val itemDecorativeMotion = id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled.current && !isScrollingChat
                    androidx.compose.runtime.CompositionLocalProvider(
                        id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled provides itemDecorativeMotion,
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                horizontal = if (isOverlayMode) 7.dp else 10.dp,
                                vertical = if (isOverlayMode) 5.dp else 8.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(if (isOverlayMode) 4.dp else 6.dp),
                        ) {
                            items(
                                items = uniqueMessages,
                                key = { it.id },
                                contentType = { it.attachmentType.ifBlank { "text" } },
                            ) { m ->
                                val isViewedOnce = m.id in viewedOnceIds
                                val isMediaDownloaded = autoDownloadMedia || m.mine || m.id in manuallyDownloadedIds
                                MessageBubble(
                                    msg = m,
                                    reaction = messageReactions[m.id],
                                    isPinned = pinnedMessage?.id == m.id,
                                    viewedOnce = isViewedOnce,
                                    liquidGlassMode = isOverlayMode,
                                    isCompactOverlay = isOverlayMode,
                                    bubbleStyleMode = bubbleStyleMode,
                                    bubbleShapePreset = bubbleShapePreset,
                                    floatingElementPreset = if (isScrollingChat) "NONE" else bubbleFloatingElement,
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

                    // Tombol Mengambang Langsung ke Atas (Tap-to-Top) & Langsung ke Bawah (Jump-to-Bottom)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (newArrivals > 0 && showScrollToBottom) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFF18181F).copy(alpha = 0.95f),
                                border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.22f)),
                            ) {
                                Text(
                                    "$newArrivals pesan baru",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = showScrollToTop,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF141821).copy(alpha = 0.94f),
                                border = BorderStroke(0.75.dp, Color(0xFF2B3245)),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        scope.launch {
                                            listState.scrollChatTo(0, chatMotionEnabled)
                                        }
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = XyIcons.ChevronUp,
                                        contentDescription = "Ke Paling Atas",
                                        tint = Color(0xFFF8FAFC),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = showScrollToBottom,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF8FAFC),
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable {
                                        scope.launch {
                                            if (messages.isNotEmpty()) {
                                                listState.scrollChatTo(messages.size - 1, chatMotionEnabled)
                                            }
                                        }
                                    },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = XyIcons.ChevronDown,
                                        contentDescription = "Langsung ke Bawah",
                                        tint = Color(0xFF0A0C10),
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }
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
                        // Filter Paket Stiker: Semua / Matte (No-Neon) / XyCyber
                        var selectedStickerPack by remember { mutableStateOf("Semua") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf("Semua", "Matte", "XyCyber").forEach { packName ->
                                val active = selectedStickerPack == packName
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (active) Color.White else Color(0xFF1F1F26))
                                        .clickable { selectedStickerPack = packName }
                                        .padding(horizontal = 12.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        text = if (packName == "Matte") "Matte (No-Neon)" else packName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color(0xFF09090B) else Color(0xFFD4D4D8),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        // Grid 3 kolom stiker bawaan (Die-Cut Transparan Tanpa Kotak)
                        val sortedPresets = remember(favoriteStickerKeys, selectedStickerPack) {
                            XY_STICKERS
                                .filter { selectedStickerPack == "Semua" || it.pack.equals(selectedStickerPack, ignoreCase = true) }
                                .sortedByDescending { "preset:${it.id}" in favoriteStickerKeys }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
                                    repeat((3 - rowItems.size).coerceAtLeast(0)) {
                                        Spacer(Modifier.weight(1f))
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
                // Toggle Pengumuman Admin @xykal (otomatis tersemat di Global Chat — disembunyikan di Floating Sesi agar ringkas)
                if (!isOverlayMode && (isFounderAccount(verifiedGhLogin) || isFounderAccount(nickname))) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isAnnouncementMode) Color(0xFFA855F7).copy(alpha = 0.24f)
                                    else Color(0xFF181A22),
                                )
                                .border(
                                    1.dp,
                                    if (isAnnouncementMode) Color(0xFFA855F7) else Color(0xFF292E3D),
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
                                    tint = if (isAnnouncementMode) Color(0xFFE9D5FF) else Color(0xFFA1A1AA),
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = if (isAnnouncementMode) "Mode Pengumuman Admin (Auto-Pin): AKTIF" else "Kirim sebagai Pengumuman Admin (Auto-Pin)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAnnouncementMode) Color(0xFFE9D5FF) else Color(0xFFA1A1AA),
                                )
                            }
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
                                    text = "Ketik pesan…",
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
                                listOf("FIRE", "LOL", "+1", "WOW", "KING", "GG", "RESPECT").forEach { tag ->
                                    val isSelected = messageReactions[targetMsg.id] == tag
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Color(0xFFA855F7).copy(alpha = 0.30f)
                                                else Color(0xFF232734),
                                            )
                                            .let { mod ->
                                                if (isSelected) mod.border(1.4.dp, Color(0xFFC084FC), CircleShape) else mod
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
                                bubbleStyleMode = bubbleStyleMode,
                                bubbleShapePreset = bubbleShapePreset,
                                floatingElementPreset = bubbleFloatingElement,
                                isCompactOverlay = isOverlayMode,
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
                                if (canMutateChatMessage(targetMsg, verifiedGhLogin) && targetMsg.attachmentType.isBlank()) {
                                    ContextMenuActionRow(label = "Edit Pesan", icon = XyIcons.Edit, onClick = {
                                        editingMessage = targetMsg
                                        editText = targetMsg.text
                                        focusedContextMsg = null
                                    })
                                }
                                failedMutations[targetMsg.id]?.let { (action, text) ->
                                    ContextMenuActionRow(label = "Coba lagi perubahan", icon = XyIcons.Refresh, onClick = {
                                        mutateMessage(targetMsg, action, text)
                                    })
                                }
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
                                    label = "Kirim / Bagikan ke WhatsApp",
                                    icon = XyIcons.Share,
                                    onClick = {
                                        val snap = targetMsg
                                        focusedContextMsg = null
                                        val waText = "*${snap.name}* di XyDesk Global Chat:\n\"${snap.text}\"\n\nhttps://xydeskremote.biz.id"
                                        runCatching {
                                            val waIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                setPackage("com.whatsapp")
                                                putExtra(Intent.EXTRA_TEXT, waText)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(waIntent)
                                        }.onFailure {
                                            openInAppCustomTab(
                                                context,
                                                "https://wa.me/?text=" + URLEncoder.encode(waText, "UTF-8"),
                                            )
                                        }
                                    },
                                )
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
                                if (isFounderAccount(verifiedGhLogin) || isFounderAccount(nickname)) {
                                    val targetRoleKey = targetMsg.githubLogin.ifBlank { targetMsg.name }
                                    val isCurrentlyTeam = isXyTeamAccount(context, targetMsg.name, targetMsg.githubLogin)
                                    val isCurrentlyTester = isXyTesterAccount(context, targetMsg.name, targetMsg.githubLogin)
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    ContextMenuActionRow(
                                        label = if (isCurrentlyTeam) "Lepas Badge XYTEAM (@$targetRoleKey)" else "Beri Badge XYTEAM (@$targetRoleKey)",
                                        icon = XyIcons.Shield,
                                        onClick = {
                                            XySpecialRoleRegistry.setRole(
                                                context,
                                                targetRoleKey,
                                                if (isCurrentlyTeam) "" else "XYTEAM",
                                            )
                                            Toast.makeText(
                                                context,
                                                if (isCurrentlyTeam) "Badge XYTEAM dilepas" else "Badge XYTEAM diberikan ke $targetRoleKey",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            focusedContextMsg = null
                                        },
                                    )
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    ContextMenuActionRow(
                                        label = if (isCurrentlyTester) "Lepas Badge TESTER (@$targetRoleKey)" else "Beri Badge TESTER (@$targetRoleKey)",
                                        icon = XyIcons.Check,
                                        onClick = {
                                            XySpecialRoleRegistry.setRole(
                                                context,
                                                targetRoleKey,
                                                if (isCurrentlyTester) "" else "TESTER",
                                            )
                                            Toast.makeText(
                                                context,
                                                if (isCurrentlyTester) "Badge TESTER dilepas" else "Badge TESTER diberikan ke $targetRoleKey",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            focusedContextMsg = null
                                        },
                                    )
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    label = "Info Keamanan Pesan",
                                    icon = XyIcons.Shield,
                                    onClick = {
                                        inspectingE2eeMsg = targetMsg
                                        focusedContextMsg = null
                                    },
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                if (canMutateChatMessage(targetMsg, verifiedGhLogin)) ContextMenuActionRow(
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

    editingMessage?.let { target ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B).copy(alpha = 0.96f))
                .clickable(enabled = true, onClick = {})
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF12141D))
                    .border(1.dp, Color(0xFF272C3F), RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = xy("EDIT PESAN CHAT GLOBAL", "EDIT GLOBAL CHAT MESSAGE"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Icon(
                        imageVector = XyIcons.Close,
                        contentDescription = "Batal",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { editingMessage = null },
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it.take(500) },
                    label = { Text("Isi Pesan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                )
                Text(
                    "Perubahan tampil langsung di semua perangkat secara real-time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton(
                        text = "Batal",
                        onClick = { editingMessage = null },
                        primary = false,
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        text = "Simpan Perubahan",
                        enabled = editText.isNotBlank() && editText.trim() != target.text,
                        onClick = { mutateMessage(target, "edit", editText) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    // Halaman Pengaturan & Fitur Lengkap Chat Global Inline (Tanpa Popup XyOverlay)
    if (showChatSettings) {
        var sendSoundEnabled by remember { mutableStateOf(chatPrefs.getBoolean("send_sound_enabled", true)) }
        var inAppBannerEnabled by remember {
            mutableStateOf(chatPrefs.getBoolean("in_app_chat_banner_enabled", true))
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090B10))
                .clickable(enabled = true, onClick = {}),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Header Halaman Pengaturan Chat Global Inline
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF10131C))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2434))
                                .clickable { showChatSettings = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Close,
                                contentDescription = "Kembali",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Column {
                            Text(
                                text = xy("Pengaturan & Fitur Chat Global", "Global Chat Settings & Features"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                text = xy("Halaman penuh tanpa popup · Tersimpan otomatis", "Full inline page without popups · Auto-saved"),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                color = Color(0xFF10B981),
                            )
                        }
                    }
                    XyPillButton(
                        text = xy("Selesai", "Done"),
                        onClick = { showChatSettings = false },
                        compact = true,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Ringkasan Statistik Ruang Chat Global Live
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF131722))
                            .border(1.dp, Color(0xFF232A3D), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${messages.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF38BDF8),
                            )
                            Text("Pesan Dimuat", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalOnline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF10B981),
                            )
                            Text("Online Aktif", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (viewerTargetLang == "auto") "AUTO" else viewerTargetLang.uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBBF24),
                            )
                            Text("Terjemahan", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    // Toggle Suara Kirim & Banner Notifikasi Dalam Aplikasi (In-App Notification)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF161922))
                            .clickable {
                                sendSoundEnabled = !sendSoundEnabled
                                chatPrefs.edit().putBoolean("send_sound_enabled", sendSoundEnabled).apply()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                xy("Efek Suara Saat Kirim Pesan", "Send Message Sound Effect"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                xy("Bunyikan nada lembut saat pesan berhasil dikirim", "Play soft pop tone when sending a message"),
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Text(
                            text = if (sendSoundEnabled) "ON" else "OFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (sendSoundEnabled) Color(0xFF10B981) else Color(0xFF64748B),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF161922))
                            .clickable {
                                inAppBannerEnabled = !inAppBannerEnabled
                                chatPrefs.edit().putBoolean("in_app_chat_banner_enabled", inAppBannerEnabled).apply()
                                InAppLiveNotificationBus.inAppBannerEnabled = inAppBannerEnabled
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                xy("Banner Notifikasi Dalam Aplikasi & Live Bubble", "In-App Notification Banner & Live Bubble"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                xy("Tampilkan popup banner di atas layar & mini bubble di bawah floating sesi saat ada chat baru", "Show top banner & mini bubble under floating session pill when new chat arrives"),
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Text(
                            text = if (inAppBannerEnabled) "ON" else "OFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (inAppBannerEnabled) Color(0xFF10B981) else Color(0xFF64748B),
                        )
                    }

                    androidx.compose.runtime.key(verifiedGhLogin) {
                        ChatBackupPanel(verifiedGhLogin) { messages.toList() }
                    }

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
                        "glass" to "Matte Slate",
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

                // 1b. Gaya Shape Bubble, Elemen Melayang Beranimasi, & Generate with AI
                BubbleShapeAndAiSection(
                    selectedShape = bubbleShapePreset,
                    selectedFloating = bubbleFloatingElement,
                    onSelectShape = { nextShape ->
                        bubbleShapePreset = nextShape
                        chatPrefs.edit().putString(KEY_BUBBLE_SHAPE, nextShape).apply()
                    },
                    onSelectFloating = { nextFloating ->
                        bubbleFloatingElement = nextFloating
                        chatPrefs.edit().putString(KEY_BUBBLE_FLOATING, nextFloating).apply()
                    },
                    onApplyAiPreset = { aiShape, aiFloating, _ ->
                        bubbleShapePreset = aiShape
                        bubbleFloatingElement = aiFloating
                        chatPrefs.edit()
                            .putString(KEY_BUBBLE_SHAPE, aiShape)
                            .putString(KEY_BUBBLE_FLOATING, aiFloating)
                            .apply()
                        Toast.makeText(
                            context,
                            "AI Bubble diterapkan: $aiShape + $aiFloating",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )

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

                // 4c. Buka Pengaturan Push Notifikasi Bawaan HP
                Surface(
                    onClick = {
                        id.xydesk.remote.push.ChatPushNotifier.openSystemNotificationSettings(context)
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
                                xy("Setelan Push Notifikasi Bawaan HP", "Native System Push Notification Settings"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Text(
                                xy("Atur channel notif Mention, Balasan, Chat Global & suara HP", "Configure Mention, Reply, Global Chat channels & device sound"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Icon(
                            imageVector = XyIcons.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp),
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
                        chatPrefs.edit().putStringSet(hiddenIdsKey, hiddenMessageIds.toSet()).apply()
                        messages.clear()
                        showChatSettings = false
                        Toast.makeText(context, "Riwayat chat di layar berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                    },
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                XyPillButton(
                    text = xy("Selesai & Kembali ke Chat Global", "Done & Back to Global Chat"),
                    onClick = { showChatSettings = false },
                    primary = true,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Halaman Inline Pemilih Bahasa Terjemahan (Tanpa Popup XyOverlay)
    if (showLangPicker) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090B10))
                .clickable(enabled = true, onClick = {}),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF10131C))
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2434))
                                .clickable { showLangPicker = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Close,
                                contentDescription = "Kembali",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Column {
                            Text(
                                text = xy("Bahasa Auto-Translate Chat Global", "Global Chat Auto-Translate Language"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                text = xy("Pilih bahasa terjemahan otomatis untuk setiap pesan masuk", "Select target language for incoming messages"),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                    XyPillButton(
                        text = xy("Tutup", "Close"),
                        onClick = { showLangPicker = false },
                        compact = true,
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(SUPPORTED_VIEWER_LANGS.distinctBy { it.code }, key = { it.code }) { opt ->
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

    // Panel Inline Info Keamanan Pesan (Tanpa Popup XyOverlay)
    inspectingE2eeMsg?.let { msg ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090B).copy(alpha = 0.94f))
                .clickable { inspectingE2eeMsg = null }
                .padding(horizontal = 18.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF12141D))
                    .border(1.dp, Color(0xFF272C3F), RoundedCornerShape(20.dp))
                    .clickable(enabled = true, onClick = {})
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                        text = if (msg.e2eeVerified) "Integritas Terverifikasi (HMAC)" else "Pesan Standar",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Text("Pengirim: ${msg.name} ${if (msg.githubLogin.isNotBlank()) "(@${msg.githubLogin})" else ""}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
                Text("Negara Terdeteksi: ${msg.country}", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
                Text("Proteksi Transit: ${msg.e2eeProtocol} (AES-256-GCM + HMAC-SHA256 via WSS/TLS)", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE2E8F0))
                Text("Signature HMAC: ${msg.e2eeSig.ifBlank { "tidak ada" }}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                Text("Status Pengiriman: Terkirim & Tervalidasi (Centang 2)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981))
                Spacer(Modifier.height(6.dp))
                XyPillButton(
                    text = "Kembali ke Chat Global",
                    onClick = { inspectingE2eeMsg = null },
                    primary = true,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // Studio Pratinjau & Edit Media (Foto / Video Live Preview + Cut/Trim / Dokumen) Sebelum Dikirim
    pendingStudioMedia?.let { studio ->
        PreSendMediaStudioDialog(
            initialAttachmentData = studio.attachmentData,
            initialAttachmentName = studio.attachmentName,
            initialAttachmentType = if (studio.attachmentType == "image") "photo" else studio.attachmentType,
            initialCaption = draft.text,
            initialViewOnce = studio.viewOnce,
            onDismiss = { pendingStudioMedia = null },
            onConfirmSend = { finalData, _, _, finalCaption, finalViewOnce ->
                val targetStudio = studio
                pendingStudioMedia = null
                draft = TextFieldValue("")
                scope.launch {
                    val finalBytesSize = withContext(Dispatchers.IO) {
                        runCatching {
                            val comma = finalData.indexOf(',')
                            if (comma >= 0) Base64.decode(finalData.substring(comma + 1), Base64.DEFAULT).size
                            else targetStudio.attachmentSize
                        }.getOrDefault(targetStudio.attachmentSize)
                    }
                    if (targetStudio.attachmentType == "video" && finalBytesSize > 2_800_000) {
                        Toast.makeText(
                            context,
                            "Potongan video masih ${(finalBytesSize / 1024)} KB (maks 2,8 MB). Gunakan Potong/Cut untuk memperpendek durasi.",
                            Toast.LENGTH_LONG,
                        ).show()
                        return@launch
                    }
                    dispatchMessagePayload(
                        rawText = finalCaption,
                        attachmentType = targetStudio.attachmentType,
                        attachmentName = if (targetStudio.attachmentType == "file") targetStudio.attachmentName else "",
                        attachmentMime = if (targetStudio.attachmentType == "image") "image/jpeg" else targetStudio.attachmentMime,
                        attachmentSize = finalBytesSize,
                        attachmentData = finalData.ifBlank { targetStudio.attachmentData },
                        viewOnce = finalViewOnce,
                    )
                }
            },
        )
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

private var activeSocket: WebSocket? = null

private fun chatSocketSend(rawJson: String): Boolean =
    runCatching { activeSocket?.send(rawJson) == true }.getOrDefault(false)

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

internal fun decodeBase64Bitmap(dataUri: String, cacheResult: Boolean = true): Bitmap? = runCatching {
    if (dataUri.isBlank()) return@runCatching null
    if (dataUri.length > 2_800_000) return@runCatching null
    val key = MessageDigest.getInstance("SHA-256").digest(dataUri.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    if (cacheResult) chatDecodedBitmapCache.get(key)?.let { return@runCatching it }
    val comma = dataUri.indexOf(',')
    if (comma < 0) return@runCatching null
    val bytes = Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT)
    decodeBoundedChatBitmap(bytes, 2_048)?.also { bmp ->
        if (cacheResult) chatDecodedBitmapCache.put(key, bmp)
    }
}.getOrNull()

internal fun saveChatMediaToGallery(context: Context, msg: ChatMessage) {
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

internal fun saveOrShareChatAttachment(context: Context, msg: ChatMessage) {
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
internal fun ChatReactionVisualIcon(
    tag: String,
    size: Dp = 18.dp,
) {
    IosWhatsAppEmoji3D(tag = tag, size = size)
}

internal data class InAppLiveNotificationItem(
    val id: String,
    val senderName: String,
    val githubLogin: String,
    val avatarUrl: String,
    val text: String,
    val createdAt: Long,
    val category: String = "CHAT",
)

internal object InAppLiveNotificationBus {
    var inAppBannerEnabled by mutableStateOf(true)
    var activeBanner by mutableStateOf<InAppLiveNotificationItem?>(null)
        private set
    var latestFloatingChat by mutableStateOf<InAppLiveNotificationItem?>(null)
        private set
    var floatingChatSeq by mutableIntStateOf(0)
        private set
    var unreadFloatingCount by mutableIntStateOf(0)
        private set
    private val seenIds = LinkedHashSet<String>()
    private var initialSnapshotSeeded = false

    fun markInitialSeeded() {
        initialSnapshotSeeded = true
    }

    fun updateLatestSnapshot(msg: ChatMessage) {
        if (msg.id.isBlank() || msg.deleted) return
        seenIds.add(msg.id)
        initialSnapshotSeeded = true
    }

    fun pushIncomingChat(msg: ChatMessage) {
        if (msg.id.isBlank() || msg.deleted || msg.mine) return
        val isNew = seenIds.add(msg.id)
        if (!initialSnapshotSeeded) {
            initialSnapshotSeeded = true
            return
        }
        if (!isNew) return
        val item = InAppLiveNotificationItem(
            id = msg.id,
            senderName = msg.name.ifBlank { "Komunitas" },
            githubLogin = msg.githubLogin,
            avatarUrl = msg.avatarUrl,
            text = msg.text.ifBlank {
                when (msg.attachmentType) {
                    "sticker" -> "✨ Mengirim Stiker"
                    "image", "photo" -> "📷 Mengirim Foto"
                    "video" -> "🎬 Mengirim Video"
                    else -> "📎 Mengirim Lampiran"
                }
            },
            createdAt = msg.at,
            category = "CHAT",
        )
        latestFloatingChat = item
        floatingChatSeq += 1
        unreadFloatingCount = (unreadFloatingCount + 1).coerceAtMost(99)
        if (inAppBannerEnabled) {
            activeBanner = item
            XyInAppNotificationBus.push(
                XyInAppNotificationItem(
                    id = "livechat-${msg.id}",
                    title = "${item.senderName} · Chat Global",
                    body = item.text,
                    category = if (msg.isAnnouncement) "announcement" else "chat",
                    actorName = msg.githubLogin.ifBlank { item.senderName },
                    isFounder = isFounderAccount(msg.githubLogin) || isFounderAccount(item.senderName),
                    targetRoute = "chat",
                    createdAt = msg.at.takeIf { it > 0L } ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    fun pushCustomBanner(
        title: String,
        body: String,
        githubLogin: String = "xykal",
        category: String = "INFO",
    ) {
        if (!inAppBannerEnabled) return
        activeBanner = InAppLiveNotificationItem(
            id = "custom-${System.currentTimeMillis()}",
            senderName = title,
            githubLogin = githubLogin,
            avatarUrl = if (githubLogin.isNotBlank()) "https://github.com/$githubLogin.png?size=120" else "",
            text = body,
            createdAt = System.currentTimeMillis(),
            category = category,
        )
    }

    fun dismissBanner() {
        activeBanner = null
    }

    fun hideFloatingPreviewBubble() {
        latestFloatingChat = null
    }

    fun clearFloatingUnread() {
        unreadFloatingCount = 0
        latestFloatingChat = null
    }

    fun dismissFloatingBubblePreview() {
        latestFloatingChat = null
        unreadFloatingCount = 0
    }

    var floatingOverlayHidden by mutableStateOf(false)

    fun syncFloatingHiddenFromPrefs(context: Context) {
        val sp = context.getSharedPreferences("xydesk_floating_chat_prefs", Context.MODE_PRIVATE)
        floatingOverlayHidden = sp.getBoolean("hidden_in_session", false)
    }

    fun setFloatingHidden(context: Context, hidden: Boolean) {
        floatingOverlayHidden = hidden
        if (hidden) {
            latestFloatingChat = null
        }
        context.getSharedPreferences("xydesk_floating_chat_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("hidden_in_session", hidden)
            .apply()
    }
}

@Composable
internal fun FloatingSessionChatOverlay(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val floatPrefs = remember { context.getSharedPreferences("xydesk_floating_chat_prefs", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        InAppLiveNotificationBus.syncFloatingHiddenFromPrefs(context)
    }

    var chatWindowOpen by remember { mutableStateOf(false) }
    val isFloatingHidden = InAppLiveNotificationBus.floatingOverlayHidden
    var isCompactFloating by remember { mutableStateOf(floatPrefs.getBoolean("compact_mode", false)) }
    var isGhostOpacity by remember { mutableStateOf(floatPrefs.getBoolean("ghost_opacity", false)) }
    var bubbleOffsetX by remember { mutableFloatStateOf(floatPrefs.getFloat("bubble_x", 16f)) }
    var bubbleOffsetY by remember { mutableFloatStateOf(floatPrefs.getFloat("bubble_y", 86f)) }
    var panelOffsetX by remember { mutableFloatStateOf(floatPrefs.getFloat("panel_x", 0f)) }
    var panelOffsetY by remember { mutableFloatStateOf(floatPrefs.getFloat("panel_y", 0f)) }
    val onlineCount by AppUpdate.appWideOnlineCount.collectAsState()
    val latestLiveChat = InAppLiveNotificationBus.latestFloatingChat
    val floatingSeq = InAppLiveNotificationBus.floatingChatSeq
    val unreadCount = InAppLiveNotificationBus.unreadFloatingCount

    // Auto-hide: ketika ada chat baru muncul di bawah floating bubble, tampilkan sebentar (3,5 detik) lalu otomatis hilang!
    LaunchedEffect(latestLiveChat?.id, floatingSeq) {
        if (latestLiveChat != null) {
            delay(3_500L)
            InAppLiveNotificationBus.hideFloatingPreviewBubble()
        }
    }

    // Background poller ringan saat jendela chat floating tertutup & tidak di-hide
    LaunchedEffect(chatWindowOpen, isFloatingHidden) {
        if (chatWindowOpen) {
            InAppLiveNotificationBus.clearFloatingUnread()
            return@LaunchedEffect
        }
        if (isFloatingHidden) {
            return@LaunchedEffect
        }
        val myNick = chatNickname(context)
        val myGh = getVerifiedGitHubLogin(context)
        var firstFetch = true
        while (!chatWindowOpen && !InAppLiveNotificationBus.floatingOverlayHidden) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = (URL("https://api.xydeskremote.biz.id/api/chat/history?limit=15&joined_at=0").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        setRequestProperty("Accept", "application/json")
                    }
                    if (conn.responseCode in 200..299) {
                        val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        val arr = root.optJSONArray("messages")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val m = parseMessageFromJson(arr.getJSONObject(i), myNick, myGh)
                                if (firstFetch) {
                                    InAppLiveNotificationBus.updateLatestSnapshot(m)
                                } else {
                                    InAppLiveNotificationBus.pushIncomingChat(m)
                                }
                            }
                            InAppLiveNotificationBus.markInitialSeeded()
                            firstFetch = false
                        }
                    }
                }
            }
            delay(4_500L)
        }
    }

    val screenWidthDp = cfg.screenWidthDp.coerceAtLeast(320)
    val screenHeightDp = cfg.screenHeightDp.coerceAtLeast(320)
    val adaptivePanelWidth = if (isCompactFloating) {
        (screenWidthDp * 0.46f).coerceIn(270f, 330f).dp
    } else {
        (screenWidthDp * 0.58f).coerceIn(310f, 400f).dp
    }
    val adaptivePanelMaxHeight = if (isCompactFloating) {
        (screenHeightDp * 0.88f).coerceIn(330f, 480f).dp
    } else {
        (screenHeightDp * 0.96f).coerceIn(390f, 580f).dp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .displayCutoutPadding(),
    ) {
        if (!isFloatingHidden && !chatWindowOpen) {
            // Grup Floating Kapsul Sesi Solid Matte + Mini Bubble Chat Baru di Bawahnya!
            Column(
                modifier = Modifier
                    .offset { IntOffset(bubbleOffsetX.roundToInt(), bubbleOffsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                floatPrefs.edit()
                                    .putFloat("bubble_x", bubbleOffsetX)
                                    .putFloat("bubble_y", bubbleOffsetY)
                                    .apply()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                bubbleOffsetX = (bubbleOffsetX + dragAmount.x).coerceIn(6f, 1800f)
                                bubbleOffsetY = (bubbleOffsetY + dragAmount.y).coerceIn(12f, 1000f)
                            },
                        )
                    },
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                // 1. Kapsul Floating Utama (Solid Matte Dark + Tombol Hide 'X')
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF12151E))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF2A3246),
                            shape = RoundedCornerShape(50),
                        )
                        .padding(start = 11.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.clickable {
                                InAppLiveNotificationBus.clearFloatingUnread()
                                chatWindowOpen = true
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981)),
                            )
                            Icon(
                                imageVector = XyIcons.Chat,
                                contentDescription = "Buka Chat Global Sesi",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "LIVE CHAT",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF222838))
                                    .padding(horizontal = 5.5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = "${onlineCount.coerceAtLeast(1)} online",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFCBD5E1),
                                )
                            }
                            if (unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFFFE2C55))
                                        .padding(horizontal = 5.5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = "+$unreadCount",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                        // Tombol Hide ('X') untuk menyembunyikan Floating Chat dari layar sesi
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF23293A))
                                .clickable {
                                    InAppLiveNotificationBus.setFloatingHidden(context, true)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Close,
                                contentDescription = "Sembunyikan Floating Chat",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }

                // 2. Mini Bubble Chat Baru di Bawah Kapsul Floating ala Live TikTok (Muncul sebentar 3,5 detik lalu otomatis hilang!)
                AnimatedVisibility(
                    visible = latestLiveChat != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                ) {
                    val chatItem = latestLiveChat
                    if (chatItem != null) {
                        val isFounderSender = isFounderAccount(chatItem.senderName) || isFounderAccount(chatItem.githubLogin)
                        Row(
                            modifier = Modifier
                                .widthIn(min = 175.dp, max = 265.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF12151E))
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF2A3246),
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .clickable {
                                    InAppLiveNotificationBus.clearFloatingUnread()
                                    chatWindowOpen = true
                                }
                                .padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            FeedAvatarThumb(
                                avatarUrl = chatItem.avatarUrl,
                                name = chatItem.senderName,
                                githubLogin = chatItem.githubLogin,
                                isFounder = isFounderSender,
                                size = 22.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = chatItem.senderName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isFounderSender) Color(0xFFFDE047) else Color(0xFF38BDF8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    XyUserRoleBadges(
                                        name = chatItem.senderName,
                                        githubLogin = chatItem.githubLogin,
                                        compact = true,
                                    )
                                }
                                Text(
                                    text = chatItem.text,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFFF8FAFC),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .clickable {
                                        InAppLiveNotificationBus.hideFloatingPreviewBubble()
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = XyIcons.Close,
                                    contentDescription = "Tutup",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (chatWindowOpen) {
            val panelShape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // Panel Multitasking Sesi (Side-Docked PiP): Solid Matte Dark
                Box(
                    modifier = Modifier
                        .offset { IntOffset(panelOffsetX.roundToInt(), panelOffsetY.roundToInt()) }
                        .width(adaptivePanelWidth)
                        .heightIn(min = 320.dp, max = adaptivePanelMaxHeight)
                        .fillMaxHeight(if (isCompactFloating) 0.88f else 0.95f)
                        .clip(panelShape)
                        .background(Color(0xFF0E1118))
                        .border(
                            width = 1.dp,
                            color = Color(0xFF283044),
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
                            floatPrefs.edit()
                                .putFloat("panel_x", panelOffsetX)
                                .putFloat("panel_y", panelOffsetY)
                                .apply()
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
                            floatPrefs.edit()
                                .putBoolean("compact_mode", isCompactFloating)
                                .putBoolean("ghost_opacity", isGhostOpacity)
                                .apply()
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

