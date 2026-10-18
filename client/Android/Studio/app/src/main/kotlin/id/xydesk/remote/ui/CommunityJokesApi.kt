package id.xydesk.remote.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Public, account-free community jokes API. Only a random install ID is sent for spam/reaction limits. */
/**
 * `JSONObject.optString` mengembalikan STRING "null" untuk nilai JSON null
 * (jebakan klasik Android) — laporan pemilik 2026-10-12: nama profil muncul
 * sebagai "null". Helper ini mengembalikan null sungguhan.
 */
private fun org.json.JSONObject.strOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal class CommunityJokesApi(context: Context) {
    private val appContext = context.applicationContext
    // Getter dinamis (bukan lazy val): setelah "Pulihkan dari Cloud" menulis
    // client ID lama ke prefs, instance API yang sudah ada langsung memakai
    // identitas lama tanpa perlu restart app.
    private val clientId: String get() = loadOrCreateClientId()

    suspend fun latest(): List<CommunityJoke> = withContext(Dispatchers.IO) {
        val gh = getVerifiedGitHubLogin(appContext)
        val hdrs = buildMap {
            put("X-XyDesk-Client-ID", clientId)
            if (!gh.isNullOrBlank()) put("X-XyDesk-GitHub-Login", gh)
        }
        val body = request(
            method = "GET",
            path = "/api/jokes",
            headers = hdrs,
        )
        val items = body.optJSONArray("items") ?: JSONArray()
        buildList(items.length()) {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.let { add(parseJoke(it)) }
            }
        }
    }

    /** Cloud sync setelan: unggah blob (lihat [CloudSync]). */
    suspend fun accountSettingsPut(blob: String): JSONObject = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("id", clientId).put("blob", blob)
        request(method = "PUT", path = "/api/account/settings", payload = payload)
    }

    /** Cloud sync setelan: unduh blob; melempar CommunityJokesException bila belum ada. */
    suspend fun accountSettingsGet(): JSONObject = withContext(Dispatchers.IO) {
        request(method = "GET", path = "/api/account/settings?id=$clientId")
    }

    /**
     * Resolve ID PC global (XY+8 hex) lewat direktori relay. Null bila PC
     * tidak terdaftar/offline (404) atau jaringan gagal.
     */
    suspend fun pcRelayResolve(globalId: String): JSONObject? = withContext(Dispatchers.IO) {
        runCatching { request(method = "GET", path = "/api/pc/relay?id=$globalId") }.getOrNull()
    }

    suspend fun submit(
        text: String,
        attachment: FeedAttachment? = null,
        isAnnouncement: Boolean = false,
        pinned: Boolean = false,
    ): CommunityJoke = withContext(Dispatchers.IO) {
        val attachmentJson = attachment?.let { att ->
            JSONObject()
                .put("mime", att.mime)
                .put("data", android.util.Base64.encodeToString(att.bytes, android.util.Base64.NO_WRAP))
        }
        val gh = getVerifiedGitHubLogin(appContext)
        val ghAvatar = getVerifiedGitHubAvatarUrl(appContext)
        val payload = JSONObject()
            .put("client_id", clientId)
            .put("text", text)
            .put("allow_links", true)
            .put("is_announcement", isAnnouncement)
            .put("pinned", pinned || isAnnouncement)
        attachmentJson?.let { payload.put("attachment", it) }
        (displayName() ?: gh)?.let { payload.put("display_name", it) }
        gh?.let { payload.put("github_login", it) }
        ghAvatar?.let { payload.put("avatar_url", it) }
        val body = request(method = "POST", path = "/api/jokes", payload = payload)
        parseJoke(body.optJSONObject("joke") ?: error("Missing submitted joke"))
    }

    suspend fun comment(
        jokeId: String,
        text: String,
        replyToId: String? = null,
        replyToName: String? = null,
    ): JokeComment = withContext(Dispatchers.IO) {
        val gh = getVerifiedGitHubLogin(appContext)
        val ghAvatar = getVerifiedGitHubAvatarUrl(appContext)
        val payload = JSONObject()
            .put("client_id", clientId)
            .put("text", text)
            .put("allow_links", true)
        (displayName() ?: gh)?.let { payload.put("display_name", it) }
        gh?.let { payload.put("github_login", it) }
        ghAvatar?.let { payload.put("avatar_url", it) }
        replyToId?.takeIf { it.isNotBlank() }?.let { payload.put("reply_to_id", it) }
        replyToName?.takeIf { it.isNotBlank() }?.let { payload.put("reply_to_name", it) }
        val body = request(method = "POST", path = "/api/jokes/$jokeId/comment", payload = payload)
        val c = body.optJSONObject("comment") ?: error("Missing comment")
        JokeComment(
            id = c.optString("id"),
            name = c.strOrNull("name"),
            githubLogin = c.strOrNull("github_login"),
            avatarUrl = c.strOrNull("avatar_url"),
            replyToId = c.strOrNull("reply_to_id"),
            replyToName = c.strOrNull("reply_to_name"),
            text = c.optString("text"),
            createdAtMillis = c.optLong("created_at", 0L),
            isOwn = true,
        )
    }

    /** Hapus komentar sendiri (butuh worker r37 dengan author_hash). */
    suspend fun deleteComment(jokeId: String, commentId: String): Int = withContext(Dispatchers.IO) {
        val gh = getVerifiedGitHubLogin(appContext)
        val payload = JSONObject().put("client_id", clientId).put("comment_id", commentId)
        (displayName() ?: gh)?.let { payload.put("display_name", it) }
        gh?.let { payload.put("github_login", it) }
        val body = request(method = "POST", path = "/api/jokes/$jokeId/comment-delete", payload = payload)
        body.optInt("comments_total", 0)
    }

    /** Nama tampilan server (dipulihkan setelah pasang ulang bila ID dipulihkan). */
    suspend fun fetchServerDisplayName(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = request(method = "GET", path = "/api/jokes/me", headers = mapOf("X-XyDesk-Client-ID" to clientId))
            body.strOrNull("display_name")
        }.getOrNull()
    }

    suspend fun like(jokeId: String): CommunityJoke = withContext(Dispatchers.IO) {
        val gh = getVerifiedGitHubLogin(appContext)
        val payload = JSONObject().put("client_id", clientId)
        (displayName() ?: gh)?.let { payload.put("display_name", it) }
        gh?.let { payload.put("github_login", it) }
        val body = request(
            method = "POST",
            path = "/api/jokes/$jokeId/like",
            payload = payload,
        )
        parseJoke(body.optJSONObject("joke") ?: error("Missing liked joke"))
    }

    suspend fun react(jokeId: String, emoji: String): CommunityJoke = withContext(Dispatchers.IO) {
        val gh = getVerifiedGitHubLogin(appContext)
        val payload = JSONObject().put("client_id", clientId).put("emoji", emoji)
        (displayName() ?: gh)?.let { payload.put("display_name", it) }
        gh?.let { payload.put("github_login", it) }
        val body = request(
            method = "POST",
            path = "/api/jokes/$jokeId/reaction",
            payload = payload,
        )
        parseJoke(body.optJSONObject("joke") ?: error("Missing reacted joke"))
    }

    suspend fun edit(
        jokeId: String,
        text: String,
        attachment: FeedAttachment? = null,
        removeAttachment: Boolean = false,
    ): CommunityJoke = withContext(Dispatchers.IO) {
        val gh = getVerifiedGitHubLogin(appContext)
        val payload = JSONObject()
            .put("client_id", clientId)
            .put("text", text)
            .put("allow_links", true)
        (displayName() ?: gh)?.let { payload.put("display_name", it) }
        gh?.let { payload.put("github_login", it) }
        if (removeAttachment) {
            payload.put("attachment", JSONObject.NULL)
        } else {
            attachment?.let { att ->
                payload.put(
                    "attachment",
                    JSONObject()
                        .put("mime", att.mime)
                        .put("data", android.util.Base64.encodeToString(att.bytes, android.util.Base64.NO_WRAP)),
                )
            }
        }
        val body = request(method = "POST", path = "/api/jokes/$jokeId/edit", payload = payload)
        parseJoke(body.optJSONObject("joke") ?: error("Missing edited joke"))
    }

    suspend fun delete(jokeId: String): Boolean = withContext(Dispatchers.IO) {
        val gh = getVerifiedGitHubLogin(appContext)
        val payload = JSONObject().put("client_id", clientId)
        (displayName() ?: gh)?.let { payload.put("display_name", it) }
        gh?.let { payload.put("github_login", it) }
        request(
            method = "POST",
            path = "/api/jokes/$jokeId/delete",
            payload = payload,
        )
        true
    }

    suspend fun fetchNotifications(): List<XyNotificationItem> = withContext(Dispatchers.IO) {
        runCatching {
            val gh = getVerifiedGitHubLogin(appContext).orEmpty()
            val disp = displayName().orEmpty()
            val hdrs = buildMap {
                put("X-XyDesk-Client-ID", clientId)
                if (gh.isNotBlank()) put("X-XyDesk-GitHub-Login", gh)
                if (disp.isNotBlank()) put("X-XyDesk-Display-Name", disp)
            }
            val body = request(method = "GET", path = "/api/jokes/notifications", headers = hdrs)
            val arr = body.optJSONArray("items") ?: JSONArray()
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        XyNotificationItem(
                            id = o.optString("id"),
                            category = o.optString("category", "inbox"),
                            title = o.optString("title"),
                            body = o.optString("body"),
                            actorName = o.optString("actor_name", "Komunitas"),
                            actorGithub = o.optString("actor_github"),
                            actorAvatar = o.optString("actor_avatar"),
                            jokeId = o.optString("joke_id"),
                            createdAtMillis = o.optLong("created_at", 0L),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchOnlineCount(): Int = withContext(Dispatchers.IO) {
        runCatching {
            val gh = getVerifiedGitHubLogin(appContext).orEmpty()
            val payload = JSONObject()
                .put("client_id", clientId)
                .put("github_login", gh)
                .put("name", displayName() ?: gh.ifBlank { "User" })
                .put("screen", "feed")
                .put("active", true)
            val body = request(method = "POST", path = "/api/chat/presence", payload = payload)
            body.optInt("online_count", 1).coerceAtLeast(1)
        }.getOrDefault(1)
    }

    suspend fun fetchLeaderboard(reportDeltaSec: Long = 0L): List<XyLeaderboardEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val gh = getVerifiedGitHubLogin(appContext).orEmpty()
            val name = (displayName() ?: gh).orEmpty()
            val totalLocalSec = recordAndGetLocalUsageSeconds(appContext, reportDeltaSec)
            val payload = JSONObject()
                .put("client_id", clientId)
                .put("github_login", gh)
                .put("name", name.ifBlank { gh.ifBlank { friendlyAccountId() } })
                .put("avatar_url", getVerifiedGitHubAvatarUrl(appContext).orEmpty())
                .put("country", java.util.Locale.getDefault().country.ifBlank { "ID" })
                .put("delta_seconds", reportDeltaSec)
                .put("total_seconds", totalLocalSec)
                .put("sessions_count", getLocalUsageSessionCount(appContext))
            val body = request(method = "POST", path = "/api/leaderboard", payload = payload)
            val arr = body.optJSONArray("items") ?: JSONArray()
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        XyLeaderboardEntry(
                            rank = o.optInt("rank", i + 1),
                            name = o.optString("name", "User"),
                            githubLogin = o.optString("github_login"),
                            avatarUrl = o.optString("avatar_url"),
                            country = o.optString("country", "ID"),
                            flag = o.optString("flag", "🇮🇩"),
                            isFounder = o.optBoolean("is_founder", false),
                            totalSeconds = o.optLong("total_seconds", 0L),
                            sessionsCount = o.optInt("sessions_count", 1),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchOpenGraph(rawUrl: String): XyOpenGraphData? = withContext(Dispatchers.IO) {
        val clean = rawUrl.trim()
        if (clean.isEmpty()) return@withContext null
        ogMemoryCache.get(clean)?.let { return@withContext it }
        runCatching {
            val encoded = java.net.URLEncoder.encode(clean, "UTF-8")
            val body = request(method = "GET", path = "/api/og?url=$encoded")
            val status = body.optString("status")
            if (status != "ok" && status != "blocked") return@runCatching null
            XyOpenGraphData(
                url = body.optString("url", clean),
                domain = body.optString("domain", ""),
                siteName = body.optString("site_name", ""),
                title = body.optString("title", clean),
                description = body.optString("description", ""),
                image = body.optString("image", ""),
                safe = body.optBoolean("safe", status != "blocked"),
                shieldBadge = body.optString("shield_badge", "XyShield Verified"),
            ).also { ogMemoryCache.put(clean, it) }
        }.getOrNull()
    }

    /** Unduh lampiran feed / avatar (byte mentah). */
    suspend fun fetchBinary(path: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = java.net.URL(ENDPOINT + path).openConnection() as javax.net.ssl.HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode !in 200..299) throw CommunityJokesException("http_${connection.responseCode}")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /** Unggah avatar profil komunitas (maks ~200KB setelah kompresi). */
    suspend fun uploadAvatar(bytes: ByteArray, mime: String): String? = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("client_id", clientId)
            .put("avatar", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
            .put("avatar_mime", mime)
        val body = request(method = "PUT", path = "/api/jokes/me", payload = payload)
        body.strOrNull("avatar_url")
    }

    suspend fun report(jokeId: String): Boolean = withContext(Dispatchers.IO) {
        val body = request(
            method = "POST",
            path = "/api/jokes/$jokeId/report",
            payload = JSONObject().put("client_id", clientId),
        )
        body.optBoolean("hidden", false)
    }

    private fun request(
        method: String,
        path: String,
        payload: JSONObject? = null,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val connection = try {
            (URL("$ENDPOINT$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
                if (payload != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
        } catch (_: Exception) {
            throw CommunityJokesException("network")
        }

        return try {
            if (payload != null) {
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val body = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (statusCode !in 200..299) {
                throw CommunityJokesException(body.optString("status").ifBlank { "http_$statusCode" })
            }
            body
        } catch (error: CommunityJokesException) {
            throw error
        } catch (_: Exception) {
            throw CommunityJokesException("network")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseJoke(value: JSONObject): CommunityJoke {
        val rawReactions = value.optJSONObject("reactions") ?: JSONObject()
        val reactions = buildMap {
            REACTIONS.forEach { emoji -> put(emoji, rawReactions.optInt(emoji, 0).coerceAtLeast(0)) }
            // Emoji di luar daftar (mis. dari klien masa depan) tetap ditampilkan.
            rawReactions.keys().forEach { emoji ->
                if (emoji !in this) put(emoji, rawReactions.optInt(emoji, 0).coerceAtLeast(0))
            }
        }
        val rawComments = value.optJSONArray("comments") ?: JSONArray()
        val comments = buildList(rawComments.length()) {
            for (i in 0 until rawComments.length()) {
                rawComments.optJSONObject(i)?.let { c ->
                    add(
                        JokeComment(
                            id = c.optString("id"),
                            name = c.strOrNull("name"),
                            githubLogin = c.strOrNull("github_login"),
                            avatarUrl = c.strOrNull("avatar_url"),
                            replyToId = c.strOrNull("reply_to_id"),
                            replyToName = c.strOrNull("reply_to_name"),
                            text = c.optString("text"),
                            createdAtMillis = c.optLong("created_at", 0L),
                            isOwn = c.optBoolean("is_own", false),
                        ),
                    )
                }
            }
        }
        val rawAvatar = value.strOrNull("author_avatar_url")
            ?.takeIf { it.startsWith("/api/jokes/avatar/") || it.startsWith("https://") }
        return CommunityJoke(
            id = value.optString("id"),
            text = value.optString("text"),
            createdAtMillis = value.optLong("created_at", 0L),
            likesCount = value.optInt("likes_count", rawReactions.optInt("❤️", 0)).coerceAtLeast(0),
            viewerLiked = value.optBoolean("viewer_liked", value.optString("viewer_reaction") == "❤️"),
            reactions = reactions,
            viewerReaction = value.optString("viewer_reaction").takeIf { it in REACTIONS && it != "❤️" },
            authorName = value.strOrNull("author_name"),
            githubLogin = value.strOrNull("github_login"),
            commentsTotal = value.optInt("comments_total", 0),
            comments = comments,
            editedAtMillis = value.optLong("edited_at", 0L),
            isOwn = value.optBoolean("is_own", false),
            isAnnouncement = value.optBoolean("is_announcement", false),
            pinned = value.optBoolean("pinned", false),
            sharedUrl = value.strOrNull("shared_url"),
            attachmentUrl = value.optJSONObject("attachment")?.optString("url")
                ?.takeIf { it.startsWith("/api/jokes/attachment/") },
            attachmentMime = value.optJSONObject("attachment")?.optString("mime")
                ?.takeIf { it.isNotBlank() },
            authorAvatarUrl = rawAvatar,
        )
    }

    // ---- akun komunitas ringan (guest) -------------------------------------
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** ID ramah pengguna, contoh: XY-7F3A9C. Turunan stabil dari client id. */
    fun friendlyAccountId(): String = "XY-" + clientId.replace("-", "").take(6).uppercase()

    fun accountShortId(): String = friendlyAccountId()

    fun displayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }

    fun setDisplayNameLocally(name: String?) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name?.trim()?.takeIf { it.isNotBlank() }).apply()
    }

    suspend fun setDisplayName(name: String): Boolean = withContext(Dispatchers.IO) {
        val clean = name.trim().take(24)
        if (clean.isEmpty()) return@withContext false
        val ok = runCatching {
            request(
                method = "PUT",
                path = "/api/jokes/me",
                payload = JSONObject().put("client_id", clientId).put("display_name", clean),
            )
        }.isSuccess
        if (ok) setDisplayNameLocally(clean)
        ok
    }

    /** Kode pemulihan = client id penuh; dipakai setelah pasang ulang. */
    fun restoreCode(): String = clientId

    fun restoreClientId(code: String): Boolean {
        val clean = code.trim()
        if (runCatching { UUID.fromString(clean) }.isFailure) return false
        prefs.edit().putString(KEY_CLIENT_ID, clean).apply()
        return true
    }

    // ---- cache feed: buka tab tanpa layar "Memuat…" ------------------------
    fun cachedFeed(): List<CommunityJoke> = runCatching {
        val raw = prefs.getString(KEY_FEED_CACHE, null) ?: return@runCatching emptyList()
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(parseJoke(it)) }
        }
    }.getOrDefault(emptyList())

    fun cacheFeed(items: List<CommunityJoke>) {
        val arr = JSONArray()
        items.take(20).forEach { j ->
            arr.put(
                JSONObject().apply {
                    put("id", j.id)
                    put("text", j.text)
                    put("created_at", j.createdAtMillis)
                    put("author_name", j.authorName)
                    put("comments_total", j.commentsTotal)
                    val reactions = JSONObject()
                    j.reactions.forEach { (e, n) -> reactions.put(e, n) }
                    put("reactions", reactions)
                    j.viewerReaction?.let { put("viewer_reaction", it) }
                    val comments = JSONArray()
                    j.comments.forEach { c ->
                        comments.put(JSONObject().apply {
                            put("id", c.id); put("name", c.name); put("text", c.text); put("created_at", c.createdAtMillis)
                        })
                    }
                    put("comments", comments)
                },
            )
        }
        prefs.edit().putString(KEY_FEED_CACHE, arr.toString()).apply()
    }

    private fun loadOrCreateClientId(): String {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_CLIENT_ID, null)?.let { existing ->
            if (runCatching { UUID.fromString(existing) }.isSuccess) return existing
        }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_CLIENT_ID, generated).apply()
        return generated
    }

    companion object {
        private const val ENDPOINT = "https://api.xydeskremote.biz.id"
        private const val PREFS_NAME = "xydesk.community.jokes"
        private const val KEY_CLIENT_ID = "anonymous_client_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_FEED_CACHE = "feed_cache_v1"
        // Harus sama dengan COMMUNITY_JOKE_REACTIONS di worker.
        val REACTIONS = listOf(
            "👍", "❤️", "😂", "😮", "😢", "🙏", "🔥", "🎉",
            "😭", "💀", "👎", "🤔", "😍", "🥳", "😎", "👏",
            "💯", "🤣", "😡", "😱", "🥶", "🤯", "😴", "🍿", "⚡", "🏆",
        )
        // Reaksi cepat gaya WhatsApp (terpisah dari Like Hati)
        val QUICK_REACTIONS = listOf("👍", "😂", "😮", "😢", "🙏", "🔥", "🎉", "💯")
    }
}

private val ogMemoryCache = object : android.util.LruCache<String, XyOpenGraphData>(80) {}

internal fun recordAndGetLocalUsageSeconds(context: Context, addDeltaSeconds: Long = 0L): Long {
    val prefs = context.getSharedPreferences("xydesk_usage_stats", Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val firstInstall = prefs.getLong("first_install_at", 0L).let {
        if (it <= 0L) {
            prefs.edit().putLong("first_install_at", now).putInt("sessions_count", 1).apply()
            now
        } else it
    }
    val prevSec = prefs.getLong("total_active_seconds", 900L).coerceAtLeast(300L)
    val updated = (prevSec + addDeltaSeconds.coerceIn(0L, 7200L)).coerceAtLeast(300L)
    if (addDeltaSeconds > 0L) {
        prefs.edit().putLong("total_active_seconds", updated).apply()
    }
    return updated
}

internal fun incrementLocalUsageSessionCount(context: Context) {
    val prefs = context.getSharedPreferences("xydesk_usage_stats", Context.MODE_PRIVATE)
    val c = prefs.getInt("sessions_count", 1).coerceAtLeast(1)
    prefs.edit().putInt("sessions_count", c + 1).apply()
}

internal fun getLocalUsageSessionCount(context: Context): Int {
    val prefs = context.getSharedPreferences("xydesk_usage_stats", Context.MODE_PRIVATE)
    return prefs.getInt("sessions_count", 1).coerceAtLeast(1)
}

internal data class XyOpenGraphData(
    val url: String,
    val domain: String,
    val siteName: String,
    val title: String,
    val description: String,
    val image: String,
    val safe: Boolean = true,
    val shieldBadge: String = "XyShield Verified",
)

internal data class XyNotificationItem(
    val id: String,
    val category: String, // "announcement" | "inbox" | "reply" | "mention"
    val title: String,
    val body: String,
    val actorName: String,
    val actorGithub: String = "",
    val actorAvatar: String = "",
    val jokeId: String = "",
    val createdAtMillis: Long = 0L,
)

internal data class XyLeaderboardEntry(
    val rank: Int,
    val name: String,
    val githubLogin: String = "",
    val avatarUrl: String = "",
    val country: String = "ID",
    val flag: String = "🇮🇩",
    val isFounder: Boolean = false,
    val totalSeconds: Long = 0L,
    val sessionsCount: Int = 1,
)

internal data class JokeComment(
    val id: String,
    val name: String?,
    val githubLogin: String? = null,
    val avatarUrl: String? = null,
    val replyToId: String? = null,
    val replyToName: String? = null,
    val text: String,
    val createdAtMillis: Long,
    /** Komentar milik pengguna ini (untuk tombol hapus di bottom sheet). */
    val isOwn: Boolean = false,
)

internal data class FeedAttachment(
    val mime: String,
    val bytes: ByteArray,
)

internal data class CommunityJoke(
    val id: String,
    val text: String,
    val createdAtMillis: Long,
    val likesCount: Int = 0,
    val viewerLiked: Boolean = false,
    val reactions: Map<String, Int>,
    val viewerReaction: String?,
    val authorName: String? = null,
    val githubLogin: String? = null,
    val commentsTotal: Int = 0,
    val comments: List<JokeComment> = emptyList(),
    val editedAtMillis: Long = 0L,
    val isOwn: Boolean = false,
    val isAnnouncement: Boolean = false,
    val pinned: Boolean = false,
    val sharedUrl: String? = null,
    val attachmentUrl: String? = null,
    val attachmentMime: String? = null,
    val authorAvatarUrl: String? = null,
)

internal class CommunityJokesException(val reason: String) : Exception(reason)
