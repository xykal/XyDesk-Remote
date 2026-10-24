package id.xydesk.remote.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.push.ChatPushNotifier
import id.xydesk.remote.ui.components.XyIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class XyInAppNotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val category: String = "info", // announcement, reply, like, reaction, comment, mention, chat, ota
    val actorName: String = "",
    val isFounder: Boolean = false,
    val targetRoute: String = "", // "chat", "feed"
    val createdAt: Long = System.currentTimeMillis(),
)

internal object XyInAppNotificationBus {
    private val _current = MutableStateFlow<XyInAppNotificationItem?>(null)
    val current: StateFlow<XyInAppNotificationItem?> = _current.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _history = MutableStateFlow<List<XyInAppNotificationItem>>(emptyList())
    val history: StateFlow<List<XyInAppNotificationItem>> = _history.asStateFlow()

    private val _inboxOpen = MutableStateFlow(false)
    val inboxOpen: StateFlow<Boolean> = _inboxOpen.asStateFlow()

    private val seenIds = LinkedHashSet<String>()

    fun openInbox() {
        _inboxOpen.value = true
        _unreadCount.value = 0
    }

    fun closeInbox() {
        _inboxOpen.value = false
    }

    fun seedHistoryOnly(item: XyInAppNotificationItem) {
        synchronized(seenIds) {
            if (item.id.isNotBlank() && !seenIds.add(item.id)) return
            while (seenIds.size > 250) {
                val first = seenIds.firstOrNull() ?: break
                seenIds.remove(first)
            }
        }
        _history.value = (_history.value + item)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .take(45)
    }

    fun push(item: XyInAppNotificationItem) {
        synchronized(seenIds) {
            if (item.id.isNotBlank() && !seenIds.add(item.id)) return
            while (seenIds.size > 250) {
                val first = seenIds.firstOrNull() ?: break
                seenIds.remove(first)
            }
        }
        _current.value = item
        _history.value = (listOf(item) + _history.value)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
            .take(45)
        _unreadCount.value = (_unreadCount.value + 1).coerceAtMost(99)
    }

    fun dismissCurrent() {
        _current.value = null
    }

    fun markAllRead() {
        _unreadCount.value = 0
    }

    fun clearAll() {
        _history.value = emptyList()
        _unreadCount.value = 0
        _current.value = null
    }
}

@Composable
internal fun XyInAppNotificationBannerHost(
    onOpenChat: () -> Unit = {},
    onOpenFeed: () -> Unit = {},
) {
    val active by XyInAppNotificationBus.current.collectAsState()
    val inboxOpen by XyInAppNotificationBus.inboxOpen.collectAsState()
    val history by XyInAppNotificationBus.history.collectAsState()

    LaunchedEffect(active?.id) {
        val item = active ?: return@LaunchedEffect
        delay(4400L)
        if (XyInAppNotificationBus.current.value?.id == item.id) {
            XyInAppNotificationBus.dismissCurrent()
        }
    }

    if (inboxOpen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090B10).copy(alpha = 0.97f))
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable(enabled = true, onClick = {}),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
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
                                .clickable { XyInAppNotificationBus.closeInbox() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Close,
                                contentDescription = "Tutup",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "Pusat Notifikasi Dalam Aplikasi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                text = "Chat Global, Mention, Balasan Komentar, Like & Info Sistem",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (history.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF1E2434))
                                    .clickable { XyInAppNotificationBus.clearAll() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "Bersihkan",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD5E1),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFA78BFA))
                                .clickable { XyInAppNotificationBus.closeInbox() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Tutup",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF09090B),
                            )
                        }
                    }
                }

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = XyIcons.Bell,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "Belum ada notifikasi baru",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                            )
                            Text(
                                text = "Notifikasi pesan Chat Global, komentar TikTok, like, dan mention akan langsung muncul di sini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        history.forEach { n ->
                            val accent = when (n.category.lowercase()) {
                                "announcement" -> Color(0xFFA855F7)
                                "like" -> Color(0xFFF43F5E)
                                "reaction" -> Color(0xFFF59E0B)
                                "comment" -> Color(0xFF10B981)
                                else -> Color(0xFFA78BFA)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF131722))
                                    .border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                    .clickable {
                                        XyInAppNotificationBus.closeInbox()
                                        when (n.targetRoute) {
                                            "feed" -> onOpenFeed()
                                            else -> onOpenChat()
                                        }
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                FeedAvatarThumb(
                                    avatarUrl = if (n.actorName.isNotBlank() && !n.actorName.contains(" ")) "https://github.com/${n.actorName.removePrefix("@")}.png?size=120" else null,
                                    name = n.actorName.ifBlank { n.title },
                                    githubLogin = n.actorName.takeIf { !it.contains(" ") },
                                    isFounder = n.isFounder || isFounderAccount(n.actorName),
                                    size = 36.dp,
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = n.title,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        XyUserRoleBadges(
                                            name = n.actorName,
                                            githubLogin = n.actorName.takeIf { !it.contains(" ") },
                                            compact = true,
                                        )
                                    }
                                    Text(
                                        text = n.body,
                                        fontSize = 12.sp,
                                        color = Color(0xFFCBD5E1),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = if (n.targetRoute == "feed") "Feed ▸" else "Chat ▸",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = active != null,
            enter = slideInVertically(
                initialOffsetY = { -it - 40 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it - 40 }) + fadeOut(),
        ) {
            val item = active ?: return@AnimatedVisibility
            val accentColor = when (item.category.lowercase()) {
                "announcement" -> Color(0xFFA855F7)
                "like" -> Color(0xFFF43F5E)
                "reaction" -> Color(0xFFF59E0B)
                "reply", "mention", "chat" -> Color(0xFFA78BFA)
                "ota" -> Color(0xFF10B981)
                else -> MaterialTheme.colorScheme.primary
            }
            val iconVec = when (item.category.lowercase()) {
                "announcement" -> XyIcons.Megaphone
                "like" -> XyIcons.HeartFilled
                "reaction" -> XyIcons.Emoji
                "reply", "mention", "chat", "comment" -> XyIcons.Chat
                "ota" -> XyIcons.Check
                else -> XyIcons.Bell
            }
            val categoryTag = when (item.category.lowercase()) {
                "announcement" -> "PENGUMUMAN"
                "like" -> "LIKE BARU"
                "reaction" -> "REAKSI"
                "reply" -> "BALASAN"
                "mention" -> "MENTION"
                "comment" -> "KOMENTAR"
                "chat" -> "CHAT GLOBAL"
                "ota" -> "DATA UPDATE"
                else -> "NOTIFIKASI"
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF111115).copy(alpha = 0.96f),
                tonalElevation = 8.dp,
                shadowElevation = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                    .clickable {
                        XyInAppNotificationBus.dismissCurrent()
                        when (item.targetRoute) {
                            "chat" -> onOpenChat()
                            "feed" -> onOpenFeed()
                        }
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = iconVec,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = categoryTag,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                letterSpacing = 0.6.sp,
                            )
                            Text(
                                text = "·",
                                fontSize = 10.sp,
                                color = Color(0xFF71717A),
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = item.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD4D4D8),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.07f))
                            .clickable { XyInAppNotificationBus.dismissCurrent() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = XyIcons.Close,
                            contentDescription = "Tutup",
                            tint = Color(0xFFA1A1AA),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Info versi terbaru & paket data OTA dari portal (`/api/update-check` & `/api/data-pack`).
 *
 * Ronde 54:
 * - Mendukung **2 jalur update langsung dari HP**:
 *   1. **Download Data (OTA Live Data Pack)** untuk update ringan (konfigurasi runner OS,
 *      koleksi stiker, protokol E2EE, preset RDP) tanpa perlu mengunduh/menginstal ulang APK.
 *   2. **Update APK Penuh** untuk perubahan engine native / besar, dapat diunduh langsung
 *      di dalam aplikasi dengan indikator progres `0% -> 100%`.
 * - Menyediakan **App-Wide Presence Heartbeat (`/api/chat/presence`)** agar jumlah **Total Online**
 *   dihitung dari seluruh pengguna yang sedang membuka aplikasi XyDesk Remote (bukan hanya yang
 *   sedang membuka layar Chat Global).
 */
internal data class AppUpdateInfo(
    val version: String,
    val url: String,
    val changelog: String,
    val dataPackVersion: String = "2026.10.17.56",
    val minApkVersion: String = version,
    val apk64Url: String = "https://api.xydeskremote.biz.id/XyDesk-Remote64bit.apk",
    val apk32Url: String = "https://api.xydeskremote.biz.id/XyDesk-Remote32bit.apk",
    val dataPackUrl: String = "https://api.xydeskremote.biz.id/api/data-pack",
)

internal object AppUpdate {
    private const val ENDPOINT = "https://api.xydeskremote.biz.id/api/update-check"
    private const val DATA_PACK_ENDPOINT = "https://api.xydeskremote.biz.id/api/data-pack"
    private const val PRESENCE_ENDPOINT = "https://api.xydeskremote.biz.id/api/chat/presence"
    private const val PREFS_DATA_PACK = "xydesk.ota.datapack"
    private const val KEY_INSTALLED_DATA_VER = "installed_data_version"
    private const val KEY_LAST_UPDATE_NOTIFIED = "last_notified_update_key"
    const val BUILTIN_DATA_VERSION = "2026.10.17.53"

    private val _appWideOnlineCount = MutableStateFlow(1)
    val appWideOnlineCount: StateFlow<Int> = _appWideOnlineCount.asStateFlow()

    fun updateOnlineCount(count: Int) {
        if (count >= 1) {
            _appWideOnlineCount.value = count
        }
    }

    fun installedDataVersion(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_DATA_PACK, Context.MODE_PRIVATE)
        return prefs.getString(KEY_INSTALLED_DATA_VER, BUILTIN_DATA_VERSION) ?: BUILTIN_DATA_VERSION
    }

    fun hasDataPackUpdate(context: Context, remoteDataVer: String): Boolean {
        val current = installedDataVersion(context)
        return remoteDataVer.isNotBlank() && remoteDataVer != current && isNewer(remoteDataVer, current)
    }

    suspend fun fetch(context: Context? = null): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode !in 200..299) return@runCatching null
            val body = JSONObject(conn.inputStream.bufferedReader().readText())
            val version = body.optString("version", "").trim()
            if (version.isEmpty()) return@runCatching null
            val info = AppUpdateInfo(
                version = version,
                url = body.optString("url", ""),
                changelog = body.optString("changelog", ""),
                dataPackVersion = body.optString("data_pack_version", "2026.10.17.54").trim(),
                minApkVersion = body.optString("min_apk_version", version).trim(),
                apk64Url = body.optString("apk_64bit_url", "https://api.xydeskremote.biz.id/XyDesk-Remote64bit.apk"),
                apk32Url = body.optString("apk_32bit_url", "https://api.xydeskremote.biz.id/XyDesk-Remote32bit.apk"),
                dataPackUrl = body.optString("data_pack_url", DATA_PACK_ENDPOINT),
            )
            if (context != null) {
                val appCtx = context.applicationContext
                // Otomatis unduh & pasang paket data OTA di latar belakang bila ada versi baru
                if (hasDataPackUpdate(appCtx, info.dataPackVersion)) {
                    val applied = downloadAndApplyDataPack(appCtx) { _, _ -> }.getOrNull()
                    if (!applied.isNullOrBlank()) {
                        XyInAppNotificationBus.push(
                            XyInAppNotificationItem(
                                id = "ota-${applied}",
                                title = "Data Otomatis Diperbarui",
                                body = "Paket data $applied telah diunduh & diaktifkan secara otomatis.",
                                category = "ota",
                            ),
                        )
                    }
                }
                maybeNotifyUpdatePush(appCtx, info)
            }
            info
        }.getOrNull()
    }

    private fun maybeNotifyUpdatePush(appContext: Context, info: AppUpdateInfo) {
        runCatching {
            val currentApk = runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.1.18"
            }.getOrDefault("1.1.18")
            val apkNewer = isNewer(info.version, currentApk)
            val dataNewer = hasDataPackUpdate(appContext, info.dataPackVersion)
            if (!apkNewer && !dataNewer) return@runCatching

            val prefs = appContext.getSharedPreferences(PREFS_DATA_PACK, Context.MODE_PRIVATE)
            val notifyKey = "${info.version}:${info.dataPackVersion}"
            if (prefs.getString(KEY_LAST_UPDATE_NOTIFIED, "") == notifyKey) return@runCatching
            prefs.edit().putString(KEY_LAST_UPDATE_NOTIFIED, notifyKey).apply()

            if (apkNewer) {
                ChatPushNotifier.notifyAppUpdateAvailable(
                    context = appContext,
                    title = xyNow("Update Aplikasi Tersedia · v${info.version}", "App Update Available · v${info.version}"),
                    summary = xyNow(
                        "Versi besar v${info.version} telah rilis! Ketuk untuk unduh & pasang langsung dari HP.",
                        "Major release v${info.version} is out! Tap to download & install directly on your phone.",
                    ),
                    isDataPack = false,
                )
            } else if (dataNewer) {
                ChatPushNotifier.notifyAppUpdateAvailable(
                    context = appContext,
                    title = xyNow("Update Data Ringan Tersedia · ${info.dataPackVersion}", "Live Data Update Available · ${info.dataPackVersion}"),
                    summary = xyNow(
                        "Paket data baru siap diunduh langsung di dalam aplikasi (Download Data) tanpa perlu instal ulang APK!",
                        "New data pack is ready to download inside the app without reinstalling the APK!",
                    ),
                    isDataPack = true,
                )
            }
        }
    }

    /**
     * Unduh & terapkan **Paket Data OTA (Download Data)** langsung di dalam HP tanpa instal ulang APK.
     */
    suspend fun downloadAndApplyDataPack(
        context: Context,
        onProgress: (percent: Int, label: String) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context.applicationContext
            withContext(Dispatchers.Main) {
                onProgress(12, xyNow("Menghubungi server paket data OTA…", "Connecting to OTA data pack server…"))
            }
            delay(220)
            val conn = (URL(DATA_PACK_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode !in 200..299) {
                error(xyNow("Server paket data mengembalikan HTTP ${conn.responseCode}", "Data server returned HTTP ${conn.responseCode}"))
            }
            withContext(Dispatchers.Main) {
                onProgress(42, xyNow("Mengunduh bundel konfigurasi, runner multi-OS & stiker…", "Downloading config bundle, multi-OS runners & stickers…"))
            }
            val rawJson = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()
            delay(260)

            val json = JSONObject(rawJson)
            val dataVer = json.optString("data_version", "2026.10.17.54").trim()
            val summary = json.optString(
                "changelog_summary",
                xyNow("Paket data berhasil diperbarui.", "Data pack updated successfully."),
            )

            withContext(Dispatchers.Main) {
                onProgress(76, xyNow("Memvalidasi integritas & menyinkronkan data lokal…", "Validating integrity & syncing local data…"))
            }
            val outFile = File(appCtx.filesDir, "xydesk_live_data_pack.json")
            outFile.writeText(rawJson, Charsets.UTF_8)

            // Sinkronkan juga konfigurasi animasi bubble & badge khusus dari paket data OTA
            runCatching {
                json.optJSONObject("bubble_anim_config")?.let { bcfg ->
                    val defaultPreset = bcfg.optString("default_preset", "SPARKLES").trim()
                    if (defaultPreset.isNotBlank()) {
                        appCtx.getSharedPreferences("xydesk_global_chat_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("bubble_floating_element", defaultPreset)
                            .apply()
                    }
                }
                json.optJSONObject("special_roles")?.let { rolesObj ->
                    val keys = rolesObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val roleVal = rolesObj.optString(k, "").trim()
                        if (roleVal.isNotBlank()) {
                            XySpecialRoleRegistry.setRole(appCtx, k, roleVal)
                        }
                    }
                }
            }

            appCtx.getSharedPreferences(PREFS_DATA_PACK, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTALLED_DATA_VER, dataVer)
                .putString("last_summary", summary)
                .putLong("updated_at_ms", System.currentTimeMillis())
                .apply()

            delay(240)
            withContext(Dispatchers.Main) {
                onProgress(100, xyNow("Selesai! Paket Data $dataVer telah aktif.", "Done! Data Pack $dataVer is now active."))
            }
            dataVer
        }
    }

    fun detectedDeviceAbi(): String {
        val abis = Build.SUPPORTED_ABIS.orEmpty()
        return when {
            abis.any { it.equals("arm64-v8a", ignoreCase = true) } -> "arm64-v8a"
            abis.any { it.equals("x86_64", ignoreCase = true) } -> "x86_64"
            abis.any { it.equals("armeabi-v7a", ignoreCase = true) } -> "armeabi-v7a"
            Build.SUPPORTED_64_BIT_ABIS.isNotEmpty() -> "arm64-v8a"
            else -> "armeabi-v7a"
        }
    }

    fun bestApkUrlForDevice(info: AppUpdateInfo): String {
        return when (detectedDeviceAbi()) {
            "x86_64" -> "https://github.com/xykal/XyDesk-Remote/releases/latest/download/XyDesk-Remote64bit-x86.apk"
            "arm64-v8a" -> info.apk64Url.ifBlank { "https://github.com/xykal/XyDesk-Remote/releases/latest/download/XyDesk-Remote64bit.apk" }
            else -> info.apk32Url.ifBlank { "https://github.com/xykal/XyDesk-Remote/releases/latest/download/XyDesk-Remote32bit.apk" }
        }
    }

    /**
     * Unduh file APK rilis terbaru langsung di dalam aplikasi dengan progres bar real-time,
     * deteksi otomatis ABI CPU (arm64-v8a / armeabi-v7a / x86_64), verifikasi SHA-256,
     * lalu kembalikan File APK untuk dipasang atau buka installer sistem.
     */
    suspend fun downloadApkInApp(
        context: Context,
        info: AppUpdateInfo,
        onProgress: (percent: Int, label: String) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context.applicationContext
            val abi = detectedDeviceAbi()
            val primaryUrl = bestApkUrlForDevice(info)
            val fallbackAsset = when (abi) {
                "x86_64" -> "XyDesk-Remote64bit-x86.apk"
                "arm64-v8a" -> "XyDesk-Remote64bit.apk"
                else -> "XyDesk-Remote32bit.apk"
            }
            val fallbackUrl = "https://github.com/xykal/XyDesk-Remote/releases/latest/download/$fallbackAsset"
            withContext(Dispatchers.Main) {
                onProgress(2, xyNow("Menyiapkan unduhan APK v${info.version} ($abi)…", "Preparing APK v${info.version} ($abi) download…"))
            }
            var conn = (URL(primaryUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            if (conn.responseCode !in 200..299 && primaryUrl != fallbackUrl) {
                conn.disconnect()
                conn = (URL(fallbackUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
            }
            if (conn.responseCode !in 200..299) {
                error("HTTP ${conn.responseCode}")
            }
            val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: 98_000_000L
            val outDir = File(appCtx.externalCacheDir ?: appCtx.cacheDir, "updates").apply { mkdirs() }
            val outFile = File(outDir, "XyDesk-Remote-v${info.version}-$abi.apk")
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(32 * 1024)
                    var readTotal = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        readTotal += n
                        val pct = ((readTotal * 100L) / totalBytes).toInt().coerceIn(1, 99)
                        if (pct != lastPct) {
                            lastPct = pct
                            val mbRead = "%.1f".format(readTotal / (1024.0 * 1024.0))
                            val mbTotal = "%.1f".format(totalBytes / (1024.0 * 1024.0))
                            withContext(Dispatchers.Main) {
                                onProgress(pct, xyNow("Mengunduh APK ($abi): $mbRead / $mbTotal MB ($pct%)", "Downloading APK ($abi): $mbRead / $mbTotal MB ($pct%)"))
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            val shaHex = digest.digest().joinToString("") { "%02x".format(it) }.take(12)
            withContext(Dispatchers.Main) {
                onProgress(100, xyNow("Unduhan selesai (SHA-256: $shaHex…)! Membuka pemasang…", "Download complete (SHA-256: $shaHex…)! Opening installer…"))
            }
            outFile
        }
    }

    /** Apakah aplikasi sudah diizinkan memasang APK (sumber tidak dikenal). */
    fun canRequestInstall(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    /** Buka halaman izin "Pasang aplikasi tidak dikenal" untuk aplikasi ini. */
    fun openInstallPermissionSettings(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Buka installer sistem untuk [file] APK hasil [downloadApkInApp] lewat
     * FileProvider. Mengembalikan false bila izin pasang belum diberikan —
     * panggil [openInstallPermissionSettings] lalu coba lagi.
     */
    fun installApk(context: Context, file: File): Boolean {
        if (!canRequestInstall(context)) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                )
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    // Super-cepat: cache 2 detik agar total online/unduh tidak delay
    private var lastPresenceAt = 0L
    private var lastStatsAt = 0L
    private var cachedDownloadCount: Int? = null

    suspend fun refreshOnlineCount(context: Context? = null): Int {
        // Alias cepat untuk XyDeskSession heartbeat
        return if (context != null) sendAppPresencePing(context, "session", true)
        else _appWideOnlineCount.value
    }

    /**
     * Kirim detak kehadiran (presence) tingkat aplikasi agar jumlah Total Online
     * menghitung seluruh orang yang sedang membuka aplikasi XyDesk Remote.
     * Super-cepat: burst 3x + cache 2 detik.
     */
    suspend fun sendAppPresencePing(
        context: Context,
        screen: String = "app",
        active: Boolean = true,
    ): Int = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            if (now - lastPresenceAt < 2_000L && _appWideOnlineCount.value > 1) return@runCatching _appWideOnlineCount.value
            lastPresenceAt = now
            val appCtx = context.applicationContext
            val api = CommunityJokesApi(appCtx)
            val ghLogin = getVerifiedGitHubLogin(appCtx).orEmpty()
            val name = ghLogin.ifBlank { api.displayName().orEmpty().ifBlank { chatNickname(appCtx) } }
            val clientId = api.accountShortId().ifBlank { name }
            val conn = (URL(PRESENCE_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 5_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject()
                .put("client_id", clientId)
                .put("github_login", ghLogin)
                .put("name", name)
                .put("screen", screen)
                .put("active", active)
                .toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(body)
                val count = root.optInt("online_count", 1).coerceAtLeast(1)
                _appWideOnlineCount.value = count
                // Cek notifikasi in-app terbaru dari server (pengumuman, balasan, like, react, mention)
                pollInAppNotifications(appCtx, clientId, ghLogin, name)
                conn.disconnect()
                return@runCatching count
            }
            conn.disconnect()
            _appWideOnlineCount.value
        }.getOrDefault(_appWideOnlineCount.value)
    }

    private fun pollInAppNotifications(
        appCtx: Context,
        clientId: String,
        ghLogin: String,
        displayName: String,
    ) {
        runCatching {
            val prefs = appCtx.getSharedPreferences(PREFS_DATA_PACK, Context.MODE_PRIVATE)
            val sinceMs = prefs.getLong("last_inapp_notif_ts", System.currentTimeMillis() - 120_000L)
            val urlStr = "https://api.xydeskremote.biz.id/api/notifications" +
                "?client_id=${URLEncoder.encode(clientId, "UTF-8")}" +
                "&github_login=${URLEncoder.encode(ghLogin, "UTF-8")}" +
                "&name=${URLEncoder.encode(displayName, "UTF-8")}" +
                "&since=$sinceMs"
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val obj = JSONObject(raw)
                val items = obj.optJSONArray("items")
                var maxTs = sinceMs
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val it = items.optJSONObject(i) ?: continue
                        val id = it.optString("id", "").trim()
                        val ts = it.optLong("created_at", 0L)
                        if (ts > maxTs) maxTs = ts
                        val actorHash = it.optString("actor_id", "").trim()
                        val actorName = it.optString("actor_name", "").trim()
                        val actorGh = it.optString("actor_github", "").trim()
                        val isSelfActor = ChatPushNotifier.isSelfUser(
                            context = appCtx,
                            actorName = actorName,
                            actorGithub = actorGh,
                            actorClientId = actorHash,
                        )
                        if (isSelfActor) continue
                        val cat = it.optString("category", "info")
                        val title = it.optString("title", "Notifikasi Baru")
                        val msg = it.optString("body", "")
                        val route = it.optString("target_route", if (cat == "like" || cat == "comment" || cat == "reaction") "feed" else "chat")
                        if (id.isNotBlank() && msg.isNotBlank()) {
                            XyInAppNotificationBus.push(
                                XyInAppNotificationItem(
                                    id = id,
                                    title = title,
                                    body = msg,
                                    category = cat,
                                    actorName = actorName,
                                    isFounder = it.optBoolean("is_founder", false),
                                    targetRoute = route,
                                    createdAt = ts.takeIf { t -> t > 0L } ?: System.currentTimeMillis(),
                                ),
                            )
                            ChatPushNotifier.unreadNotifCountState.intValue = (ChatPushNotifier.unreadNotifCountState.intValue + 1).coerceAtMost(99)
                            if (cat == "mention" || cat == "reply") {
                                ChatPushNotifier.unreadChatCountState.intValue = (ChatPushNotifier.unreadChatCountState.intValue + 1).coerceAtMost(99)
                            }
                            // Hanya tampilkan kartu notifikasi sosial terpisah untuk aktivitas Feed.
                            // Semua notifikasi Chat Global (route == "chat" / id "chat-*") sudah digabung
                            // ke dalam 1 kartu Grup WhatsApp (MessagingStyle) agar tidak double!
                            if (route != "chat" && !id.startsWith("chat-")) {
                                ChatPushNotifier.notifySocialEvent(
                                    context = appCtx,
                                    notifId = id,
                                    category = cat,
                                    title = title,
                                    body = msg,
                                    actorName = actorName,
                                    actorGithub = actorGh,
                                    actorClientId = actorHash,
                                )
                            }
                        }
                    }
                }
                if (maxTs > sinceMs) {
                    prefs.edit().putLong("last_inapp_notif_ts", maxTs).apply()
                }
            }
            conn.disconnect()
        }

        // Sinkronkan juga pesan terbaru Chat Global ke Pusat Notifikasi In-App & Banner Live
        runCatching {
            val prefs = appCtx.getSharedPreferences(PREFS_DATA_PACK, Context.MODE_PRIVATE)
            val lastChatTs = prefs.getLong("last_inapp_chat_ts", 0L)
            val cConn = (URL("https://api.xydeskremote.biz.id/api/chat/history?limit=12&joined_at=0").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 5_000
                setRequestProperty("Accept", "application/json")
            }
            if (cConn.responseCode in 200..299) {
                val raw = cConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(raw)
                val arr = root.optJSONArray("messages")
                var newestChatTs = lastChatTs
                if (arr != null && arr.length() > 0) {
                    for (i in 0 until arr.length()) {
                        val m = arr.optJSONObject(i) ?: continue
                        val id = m.optString("id", "").trim()
                        val at = m.optLong("at", 0L)
                        val sender = m.optString("name", "Komunitas").trim()
                        val gh = m.optString("github_login", "").trim()
                        val txt = m.optString("text", "").trim().ifBlank { "Mengirim media di Chat Global" }
                        if (id.isBlank() || m.optBoolean("deleted", false)) continue
                        if (at > newestChatTs) newestChatTs = at
                        val isSelf = ChatPushNotifier.isSelfUser(
                            context = appCtx,
                            actorName = sender,
                            actorGithub = gh,
                            actorClientId = "",
                        )
                        val notifItem = XyInAppNotificationItem(
                            id = "chat-$id",
                            title = "$sender di Chat Global",
                            body = txt,
                            category = if (m.optBoolean("is_announcement", false)) "announcement" else "chat",
                            actorName = gh.ifBlank { sender },
                            isFounder = isFounderAccount(gh) || isFounderAccount(sender),
                            targetRoute = "chat",
                            createdAt = at.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        )
                        if (lastChatTs == 0L || at <= lastChatTs || isSelf) {
                            XyInAppNotificationBus.seedHistoryOnly(notifItem)
                        } else {
                            XyInAppNotificationBus.push(notifItem)
                        }
                    }
                    if (newestChatTs > lastChatTs) {
                        prefs.edit().putLong("last_inapp_chat_ts", newestChatTs).apply()
                    }
                }
            }
            cConn.disconnect()
        }
    }

    /** True bila [remote] lebih baru dari [current] (per segmen numerik). */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String): List<Int> =
            value.trim().removePrefix("v").split(".").map { seg ->
                seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
