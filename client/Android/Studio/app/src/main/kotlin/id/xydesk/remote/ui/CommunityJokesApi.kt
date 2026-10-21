package id.xydesk.remote.ui

import android.content.Context
import id.xydesk.remote.push.ChatPushNotifier
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
            val myGhLower = gh.trim().removePrefix("@").lowercase()
            val myDispLower = disp.trim().removePrefix("@").lowercase()
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val cat = o.optString("category", "inbox")
                    val actName = o.optString("actor_name", "Komunitas")
                    val actGh = o.optString("actor_github", "")
                    val actId = o.optString("actor_id", "")
                    val isSelf = ChatPushNotifier.isSelfUser(
                        context = appContext,
                        actorName = actName,
                        actorGithub = actGh,
                        actorClientId = actId,
                    ) || (myGhLower.isNotBlank() && (actGh.trim().removePrefix("@").lowercase() == myGhLower || actName.trim().removePrefix("@").lowercase() == myGhLower)) ||
                        (myDispLower.isNotBlank() && (actName.trim().removePrefix("@").lowercase() == myDispLower || actGh.trim().removePrefix("@").lowercase() == myDispLower))
                    if (isSelf) continue
                    val item = XyNotificationItem(
                        id = o.optString("id"),
                        category = cat,
                        title = o.optString("title"),
                        body = o.optString("body"),
                        actorName = actName,
                        actorGithub = actGh,
                        actorAvatar = o.optString("actor_avatar"),
                        jokeId = o.optString("joke_id"),
                        createdAtMillis = o.optLong("created_at", 0L),
                    )
                    add(item)
                    XyInAppNotificationBus.seedHistoryOnly(
                        XyInAppNotificationItem(
                            id = item.id,
                            title = item.title,
                            body = item.body,
                            category = item.category,
                            actorName = item.actorGithub.ifBlank { item.actorName },
                            isFounder = isFounderAccount(item.actorGithub) || isFounderAccount(item.actorName),
                            targetRoute = "feed",
                            createdAt = item.createdAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        ),
                    )
                }
                // Gabungkan juga riwayat notifikasi in-app (Chat Global, Mention, Balasan)
                XyInAppNotificationBus.history.value.forEach { live ->
                    if (none { it.id == live.id }) {
                        add(
                            XyNotificationItem(
                                id = live.id,
                                category = if (live.category == "chat") "reply" else live.category,
                                title = live.title,
                                body = live.body,
                                actorName = live.actorName,
                                actorGithub = live.actorName.takeIf { !it.contains(" ") }.orEmpty(),
                                actorAvatar = if (live.actorName.isNotBlank() && !live.actorName.contains(" ")) {
                                    "https://github.com/${live.actorName.removePrefix("@")}.png?size=120"
                                } else "",
                                jokeId = "",
                                createdAtMillis = live.createdAt,
                            ),
                        )
                    }
                }
            }.distinctBy { it.id }.sortedByDescending { it.createdAtMillis }
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
            val body = if (gh.isNotBlank()) {
                val payload = JSONObject()
                    .put("client_id", clientId)
                    .put("github_login", gh)
                    .put("name", name.ifBlank { gh })
                    .put("avatar_url", getVerifiedGitHubAvatarUrl(appContext).orEmpty())
                    .put("country", java.util.Locale.getDefault().country.ifBlank { "ID" })
                    .put("delta_seconds", reportDeltaSec)
                    .put("total_seconds", totalLocalSec)
                    .put("sessions_count", getLocalUsageSessionCount(appContext))
                request(method = "POST", path = "/api/leaderboard", payload = payload)
            } else {
                request(method = "GET", path = "/api/leaderboard")
            }
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
            }.also { LeaderboardPodium.publish(it) }
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

    /** Unduh lampiran feed / avatar (byte mentah) dengan in-memory LRU cache agar tidak reload saat scroll/pindah tab. */
    suspend fun fetchBinary(path: String): ByteArray = withContext(Dispatchers.IO) {
        binaryMemoryCache.get(path)?.let { return@withContext it }
        val connection = java.net.URL(ENDPOINT + path).openConnection() as javax.net.ssl.HttpsURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Connection", "keep-alive")
        try {
            if (connection.responseCode !in 200..299) throw CommunityJokesException("http_${connection.responseCode}")
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isNotEmpty() && bytes.size <= 4 * 1024 * 1024) {
                binaryMemoryCache.put(path, bytes)
            }
            bytes
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
        inMemoryFeedCache?.takeIf { it.isNotEmpty() }?.let { return@runCatching it }
        val raw = prefs.getString(KEY_FEED_CACHE, null) ?: return@runCatching emptyList()
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { add(parseJoke(it)) }
        }.also { if (it.isNotEmpty()) inMemoryFeedCache = it }
    }.getOrDefault(emptyList())

    fun cacheFeed(items: List<CommunityJoke>) {
        inMemoryFeedCache = items
        val arr = JSONArray()
        items.take(25).forEach { j ->
            arr.put(
                JSONObject().apply {
                    put("id", j.id)
                    put("text", j.text)
                    put("created_at", j.createdAtMillis)
                    put("edited_at", j.editedAtMillis)
                    put("likes_count", j.likesCount)
                    put("viewer_liked", j.viewerLiked)
                    put("author_name", j.authorName)
                    j.githubLogin?.let { put("github_login", it) }
                    j.authorAvatarUrl?.let { put("author_avatar_url", it) }
                    j.attachmentUrl?.let { put("attachment_url", it) }
                    j.attachmentMime?.let { put("attachment_mime", it) }
                    j.sharedUrl?.let { put("shared_url", it) }
                    put("is_own", j.isOwn)
                    put("is_announcement", j.isAnnouncement)
                    put("pinned", j.pinned)
                    put("comments_total", j.commentsTotal)
                    val reactions = JSONObject()
                    j.reactions.forEach { (e, n) -> reactions.put(e, n) }
                    put("reactions", reactions)
                    j.viewerReaction?.let { put("viewer_reaction", it) }
                    val comments = JSONArray()
                    j.comments.forEach { c ->
                        comments.put(JSONObject().apply {
                            put("id", c.id)
                            put("name", c.name)
                            c.githubLogin?.let { put("github_login", it) }
                            c.avatarUrl?.let { put("avatar_url", it) }
                            c.replyToId?.let { put("reply_to_id", it) }
                            c.replyToName?.let { put("reply_to_name", it) }
                            put("text", c.text)
                            put("created_at", c.createdAtMillis)
                            put("is_own", c.isOwn)
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

        @Volatile
        internal var inMemoryFeedCache: List<CommunityJoke>? = null

        internal val binaryMemoryCache = object : android.util.LruCache<String, ByteArray>(16 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ByteArray): Int = value.size.coerceAtLeast(1)
        }
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
