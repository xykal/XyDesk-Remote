package id.xydesk.remote.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COMMUNITY_JOKE_MAX_CHARS = 500
private const val INITIAL_JOKES_VISIBLE = 5

@Composable
internal fun FunHubCard(
    onHide: (() -> Unit)? = null,
    refreshSignal: Int = 0,
    onBusyChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }
    val scope = rememberCoroutineScope()

    var jokes by remember(api) { mutableStateOf(api.cachedFeed()) }
    var draft by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var fullscreenImage by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var authorFilter by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var activeReactionId by remember { mutableStateOf<String?>(null) }
    var commentingId by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var reportedIds by remember { mutableStateOf(emptySet<String>()) }
    var showAll by remember { mutableStateOf(true) }
    var composerOpen by remember { mutableStateOf(false) }
    var isAnnouncementDraft by remember { mutableStateOf(false) }
    var hdUploadEnabled by remember { mutableStateOf(true) }
    var formMessage by remember { mutableStateOf<String?>(null) }
    var formMessageIsError by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<FeedAttachment?>(null) }
    var editingJoke by remember { mutableStateOf<CommunityJoke?>(null) }
    var editRemoveAttachment by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var commentsTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var actionsTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var deletingCommentId by remember { mutableStateOf<String?>(null) }

    val myGithubLogin = remember(context, composerOpen, refreshKey) { getVerifiedGitHubLogin(context) }
    val isViewerFounder = remember(myGithubLogin) {
        isFounderAccount(myGithubLogin) || isFounderAccount(api.displayName())
    }

    val pickVisual = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val blockedReason = checkMaliciousUriOrFile(context, uri)
                if (blockedReason != null) {
                    formMessage = blockedReason
                    formMessageIsError = true
                    return@launch
                }
                val att = encodePickedFeedImage(context, uri, hdMode = hdUploadEnabled)
                if (att == null) {
                    formMessage = xyNow(
                        "Media tidak didukung atau melebihi batas ukuran HD (2,5 MB).",
                        "Media unsupported or exceeds HD size limit (2.5 MB).",
                    )
                    formMessageIsError = true
                } else {
                    pendingAttachment = att
                    editRemoveAttachment = false
                    formMessage = null
                }
            }
        }
    }
    var feedError by remember { mutableStateOf<String?>(null) }
    var feedMessage by remember { mutableStateOf<String?>(null) }
    var feedMessageIsError by remember { mutableStateOf(false) }

    fun showCommunityError(error: Throwable, loadingFeed: Boolean = false): String {
        val reason = (error as? CommunityJokesException)?.reason.orEmpty()
        return when {
            loadingFeed && (reason == "not_found" || reason == "http_404") ->
                xyNow("Feed server belum tersedia (404). Deploy Worker terbaru, lalu coba lagi.", "The feed endpoint isn't deployed yet (404). Deploy the latest Worker, then retry.")
            loadingFeed && reason == "unavailable" ->
                xyNow("Feed sementara tidak tersedia di server. Coba lagi sebentar.", "The feed server is temporarily unavailable. Please retry shortly.")
            reason == "rate_limited" -> xyNow("Tunggu sebentar sebelum mengirim lagi.", "Please wait a little before posting again.")
            reason == "malicious_blocked" -> xyNow("🛡️ XyShield: Tautan atau file berbahaya (virus/phishing) diblokir demi keamanan.", "🛡️ XyShield: Malicious link or virus file blocked for security.")
            reason == "forbidden" -> xyNow("Hanya pemilik postingan atau Admin @xykal yang dapat mengubah/menghapus ini.", "Only the post author or Admin @xykal can edit/delete this.")
            reason == "invalid_text" -> xyNow("Teks harus 3–500 karakter dan aman dari konten berbahaya.", "Text must be 3–500 characters and safe.")
            reason == "invalid_client_id" -> xyNow("Identitas tidak valid. Coba muat ulang aplikasi.", "Invalid ID. Try reopening the app.")
            !loadingFeed && reason == "not_found" -> xyNow("Postingan sudah tidak tersedia.", "This post is no longer available.")
            reason == "network" -> xyNow("Jaringan gagal menjangkau server. Periksa internet lalu coba lagi.", "Couldn't reach the server. Check your connection and retry.")
            loadingFeed -> xyNow("Feed gagal dimuat. Periksa jaringan lalu coba lagi.", "Couldn't load the feed. Check your connection and retry.")
            else -> xyNow("Aksi gagal. Coba lagi.", "That action failed. Please retry.")
        }
    }

    fun updateJoke(updated: CommunityJoke) {
        jokes = jokes.map { old ->
            if (old.id != updated.id) return@map old
            updated.copy(
                comments = updated.comments.ifEmpty { old.comments },
                commentsTotal = maxOf(updated.commentsTotal, old.commentsTotal),
            )
        }
    }

    LaunchedEffect(refreshing) { onBusyChange?.invoke(refreshing) }

    LaunchedEffect(api, refreshKey, refreshSignal) {
        feedImageCache.evictAll()
        refreshing = jokes.isEmpty() || refreshSignal > 0
        feedError = null
        try {
            val fresh = api.latest()
            jokes = fresh
            api.cacheFeed(fresh)
        } catch (error: Exception) {
            if (jokes.isEmpty()) feedError = showCommunityError(error, loadingFeed = true)
        } finally {
            refreshing = false
        }
    }

    val trimmedDraft = draft.trim()
    val characterCount = draft.codePointCount(0, draft.length)
    val trimmedCharacterCount = trimmedDraft.codePointCount(0, trimmedDraft.length)
    val maliciousLinkDetected = remember(draft) { isClientSideMaliciousText(draft) }
    val draftDetectedUrl = remember(draft) { extractFirstUrl(draft) }
    val hasAttachment = pendingAttachment != null || (editingJoke?.attachmentUrl != null && !editRemoveAttachment)
    val canSubmit = !maliciousLinkDetected && !submitting && (trimmedCharacterCount >= 1 || hasAttachment)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    xy("Feed Komunitas & Pengumuman", "Community Feed & Announcements"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    xy("Media HD · Autoplay Video · OG Link Preview · XyShield Aman", "HD Media · Video Autoplay · OG Link Previews · XyShield Protected"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (refreshing) xy("Memuat…", "Loading…") else xy("Muat ulang", "Refresh"),
                    modifier = Modifier
                        .clickable(enabled = !refreshing) { refreshKey += 1 }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (onHide != null) {
                    Text(
                        xy("Sembunyikan", "Hide"),
                        modifier = Modifier
                            .clickable(onClick = onHide)
                            .padding(start = 8.dp, top = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Kotak Tulis / Edit Postingan ditempatkan di atas supaya saat Edit diklik langsung terlihat jelas
        if (composerOpen) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131720),
                border = BorderStroke(
                    1.dp,
                    if (editingJoke != null || isAnnouncementDraft) Color(0xFFF59E0B) else Color(0xFF282E3D),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = when {
                                editingJoke != null -> xy("✏️ Edit Postingan", "✏️ Edit Post")
                                isAnnouncementDraft -> xy("📢 Buat Pengumuman Resmi Admin", "📢 Create Official Admin Announcement")
                                else -> xy("✍️ Buat Postingan Baru", "✍️ Create New Post")
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (editingJoke != null || isAnnouncementDraft) Color(0xFFFBBF24) else Color(0xFFF8FAFC),
                        )
                        Text(
                            xy("Batal", "Cancel"),
                            modifier = Modifier
                                .clickable(enabled = !submitting) {
                                    composerOpen = false
                                    editingJoke = null
                                    draft = ""
                                    pendingAttachment = null
                                    editRemoveAttachment = false
                                    isAnnouncementDraft = false
                                }
                                .padding(4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (isViewerFounder && editingJoke == null) {
                        Surface(
                            onClick = { isAnnouncementDraft = !isAnnouncementDraft },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAnnouncementDraft) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF1B202C),
                            border = BorderStroke(
                                1.dp,
                                if (isAnnouncementDraft) Color(0xFFF59E0B) else Color(0xFF2E3546),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = xy(
                                        "📌 Pin Otomatis sebagai Pengumuman di Feed & Global Chat",
                                        "📌 Auto-Pin as Official Announcement in Feed & Global Chat",
                                    ),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAnnouncementDraft) Color(0xFFFBBF24) else Color(0xFFCBD5E1),
                                )
                                Text(
                                    text = if (isAnnouncementDraft) "AKTIF" else "NONAKTIF",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isAnnouncementDraft) Color(0xFF10B981) else Color(0xFF64748B),
                                )
                            }
                        }
                    }

                    CommunityJokeInput(
                        value = draft,
                        onValueChange = { next ->
                            val nextCount = next.codePointCount(0, next.length)
                            if (nextCount <= COMMUNITY_JOKE_MAX_CHARS) {
                                draft = next
                                formMessage = null
                            }
                        },
                        placeholder = xy(
                            "Bagikan cerita, screenshot HD, video, atau tempel link (OG banner otomatis tampil)…",
                            "Share stories, HD screenshots, videos, or paste any link (auto OG banner)…",
                        ),
                    )

                    // Live OG Preview saat user menempelkan link di composer
                    if (!draftDetectedUrl.isNullOrBlank() && !maliciousLinkDetected) {
                        XyOpenGraphPreviewCard(url = draftDetectedUrl)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        XyPillButton(
                            text = xy("📷 Foto HD / GIF / Video", "📷 HD Photo / GIF / Video"),
                            onClick = { pickVisual.launch("*/*") },
                            primary = false,
                            compact = true,
                        )
                        Surface(
                            onClick = { hdUploadEnabled = !hdUploadEnabled },
                            shape = RoundedCornerShape(999.dp),
                            color = if (hdUploadEnabled) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFF1C202B),
                            border = BorderStroke(
                                1.dp,
                                if (hdUploadEnabled) Color(0xFF10B981) else Color(0xFF2D3343),
                            ),
                        ) {
                            Text(
                                text = if (hdUploadEnabled) "✨ HD Aktif (Jernih)" else "SD Standar",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hdUploadEnabled) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                            )
                        }
                        pendingAttachment?.let { att ->
                            if (att.mime.startsWith("video/")) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E293B),
                                ) {
                                    Text(
                                        text = "🎬 Video Siap (${att.bytes.size / 1024} KB)",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        fontSize = 11.sp,
                                        color = Color(0xFFF8FAFC),
                                    )
                                }
                            } else {
                                val bmp = remember(att) {
                                    android.graphics.BitmapFactory.decodeByteArray(att.bytes, 0, att.bytes.size)
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                            }
                            Text(
                                xy("Hapus Media", "Remove Media"),
                                modifier = Modifier
                                    .clickable {
                                        pendingAttachment = null
                                        if (editingJoke != null) editRemoveAttachment = true
                                    }
                                    .padding(4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val helper = when {
                            maliciousLinkDetected -> xy("🛡️ XyShield: Tautan/file berbahaya diblokir!", "🛡️ XyShield: Malicious link/file blocked!")
                            else -> xy("{0}/500 karakter · 🛡️ XyShield Aktif", "{0}/500 chars · 🛡️ XyShield Active", characterCount)
                        }
                        Text(
                            helper,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (maliciousLinkDetected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        XyPillButton(
                            text = when {
                                submitting -> xy("Mengirim…", "Posting…")
                                editingJoke != null -> xy("Simpan Perubahan", "Save Changes")
                                isAnnouncementDraft -> xy("Publikasikan & Pin", "Publish & Pin")
                                else -> xy("Publikasikan", "Publish")
                            },
                            onClick = {
                                if (canSubmit) {
                                    scope.launch {
                                        submitting = true
                                        formMessage = null
                                        try {
                                            val target = editingJoke
                                            val posted = if (target != null) {
                                                api.edit(
                                                    target.id,
                                                    trimmedDraft,
                                                    pendingAttachment,
                                                    removeAttachment = editRemoveAttachment && pendingAttachment == null,
                                                )
                                            } else {
                                                api.submit(
                                                    trimmedDraft,
                                                    pendingAttachment,
                                                    isAnnouncement = isAnnouncementDraft,
                                                    pinned = isAnnouncementDraft,
                                                )
                                            }
                                            val merged = if (target != null) {
                                                jokes.map { old -> if (old.id == posted.id) posted else old }
                                            } else {
                                                listOf(posted) + jokes.filterNot { it.id == posted.id }
                                            }.sortedWith(
                                                compareByDescending<CommunityJoke> { it.pinned || it.isAnnouncement }
                                                    .thenByDescending { it.createdAtMillis },
                                            ).take(25)
                                            jokes = merged
                                            api.cacheFeed(merged)
                                            draft = ""
                                            pendingAttachment = null
                                            editingJoke = null
                                            editRemoveAttachment = false
                                            isAnnouncementDraft = false
                                            composerOpen = false
                                            feedMessage = xyNow(
                                                if (target != null) "✅ Postingan berhasil diperbarui." else "✅ Postingan berhasil dipublikasikan.",
                                                if (target != null) "✅ Post updated." else "✅ Post published.",
                                            )
                                            feedMessageIsError = false
                                        } catch (error: Exception) {
                                            formMessage = showCommunityError(error)
                                            formMessageIsError = true
                                        } finally {
                                            submitting = false
                                        }
                                    }
                                }
                            },
                            enabled = canSubmit,
                            compact = true,
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                XyPillButton(
                    text = xy("✍️ Tulis Postingan / Share Link", "✍️ Write Post / Share Link"),
                    onClick = {
                        editingJoke = null
                        draft = ""
                        pendingAttachment = null
                        editRemoveAttachment = false
                        isAnnouncementDraft = false
                        composerOpen = true
                    },
                    primary = true,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                if (isViewerFounder) {
                    XyPillButton(
                        text = xy("📢 Buat Pengumuman", "📢 Announcement"),
                        onClick = {
                            editingJoke = null
                            draft = ""
                            pendingAttachment = null
                            editRemoveAttachment = false
                            isAnnouncementDraft = true
                            composerOpen = true
                        },
                        primary = false,
                        compact = true,
                    )
                }
            }
        }

        formMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (formMessageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }

        feedMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (feedMessageIsError) MaterialTheme.colorScheme.error else Color(0xFF10B981),
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (feedError != null && jokes.isNotEmpty()) {
            Text(feedError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        when {
            refreshing && jokes.isEmpty() -> Text(
                xy("Memuat feed komunitas…", "Loading community feed…"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            feedError != null && jokes.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(feedError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                XyPillButton(
                    text = xy("Coba lagi", "Try again"),
                    onClick = { refreshKey += 1 },
                    primary = false,
                    compact = true,
                )
            }
            jokes.isEmpty() -> Text(
                xy("Belum ada postingan. Kirim yang pertama.", "Nothing here yet. Post the first one."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                authorFilter?.let { who ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            xy("Postingan oleh ", "Posts by ") + who,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "✕",
                            modifier = Modifier
                                .clickable { authorFilter = null }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val filteredJokes = authorFilter
                    ?.let { who -> jokes.filter { (it.authorName ?: xyNow("Anonim", "Anonymous")) == who } }
                    ?: jokes
                val visibleJokes = if (showAll || authorFilter != null) {
                    filteredJokes
                } else {
                    filteredJokes.take(INITIAL_JOKES_VISIBLE)
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    visibleJokes.forEach { joke ->
                        val canManagePost = joke.isOwn || isViewerFounder ||
                            (!myGithubLogin.isNullOrBlank() && joke.githubLogin?.equals(myGithubLogin, ignoreCase = true) == true)
                        CommunityJokeItem(
                            joke = joke,
                            canManage = canManagePost,
                            onImageClick = { url, mime -> fullscreenImage = url to mime },
                            onAuthorClick = { name ->
                                authorFilter = name
                                showAll = true
                            },
                            isReactionBusy = activeReactionId == joke.id,
                            onReact = { emoji ->
                                if (activeReactionId == null) {
                                    scope.launch {
                                        activeReactionId = joke.id
                                        feedMessage = null
                                        try {
                                            updateJoke(api.react(joke.id, emoji))
                                        } catch (error: Exception) {
                                            feedMessage = showCommunityError(error)
                                            feedMessageIsError = true
                                        } finally {
                                            activeReactionId = null
                                        }
                                    }
                                }
                            },
                            onOpenComments = { commentsTarget = joke },
                            onOpenActions = { actionsTarget = joke },
                            onQuickEdit = {
                                editingJoke = joke
                                draft = joke.text
                                pendingAttachment = null
                                editRemoveAttachment = false
                                composerOpen = true
                            },
                            onQuickDelete = {
                                deleteTarget = joke
                            },
                            onShare = {
                                shareFeedPost(context, joke)
                            },
                        )
                    }
                    if (jokes.size > INITIAL_JOKES_VISIBLE) {
                        Text(
                            if (showAll) xy("Tampilkan lebih sedikit", "Show less")
                            else xy("Tampilkan {0} lainnya", "Show {0} more", jokes.size - INITIAL_JOKES_VISIBLE),
                            modifier = Modifier
                                .align(Alignment.End)
                                .clickable { showAll = !showAll }
                                .padding(vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        fullscreenImage?.let { (url, mime) ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { fullscreenImage = null },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                ),
            ) {
                FeedImageViewer(url = url, mime = mime, onDismiss = { fullscreenImage = null })
            }
        }
    }

    commentsTarget?.let { target ->
        val current = jokes.firstOrNull { it.id == target.id } ?: target
        CommentsBottomSheet(
            joke = current,
            isViewerFounder = isViewerFounder,
            isSending = commentingId == current.id,
            deletingCommentId = deletingCommentId,
            onSend = { text, replyToId, replyToName ->
                if (commentingId == null) {
                    scope.launch {
                        commentingId = current.id
                        feedMessage = null
                        try {
                            val posted = api.comment(current.id, text, replyToId = replyToId, replyToName = replyToName)
                            val updated = current.copy(
                                comments = listOf(posted) + current.comments,
                                commentsTotal = current.commentsTotal + 1,
                            )
                            jokes = jokes.map { old -> if (old.id == updated.id) updated else old }
                            commentsTarget = updated
                        } catch (error: Exception) {
                            feedMessage = showCommunityError(error)
                            feedMessageIsError = true
                        } finally {
                            commentingId = null
                        }
                    }
                }
            },
            onDeleteComment = { comment ->
                if (deletingCommentId == null) {
                    scope.launch {
                        deletingCommentId = comment.id
                        feedMessage = null
                        try {
                            api.deleteComment(current.id, comment.id)
                            val updated = current.copy(
                                comments = current.comments.filter { it.id != comment.id },
                                commentsTotal = maxOf(0, current.commentsTotal - 1),
                            )
                            jokes = jokes.map { old -> if (old.id == updated.id) updated else old }
                            commentsTarget = updated
                        } catch (error: Exception) {
                            feedMessage = showCommunityError(error)
                            feedMessageIsError = true
                        } finally {
                            deletingCommentId = null
                        }
                    }
                }
            },
            onDismiss = { commentsTarget = null },
        )
    }

    actionsTarget?.let { target ->
        val canManage = target.isOwn || isViewerFounder ||
            (!myGithubLogin.isNullOrBlank() && target.githubLogin?.equals(myGithubLogin, ignoreCase = true) == true)
        ActionsBottomSheet(
            joke = target,
            canManage = canManage,
            alreadyReported = target.id in reportedIds,
            onEdit = {
                actionsTarget = null
                editingJoke = target
                draft = target.text
                pendingAttachment = null
                editRemoveAttachment = false
                composerOpen = true
            },
            onDelete = {
                actionsTarget = null
                deleteTarget = target
            },
            onShare = {
                actionsTarget = null
                shareFeedPost(context, target)
            },
            onReport = {
                actionsTarget = null
                reportTarget = target
            },
            onDismiss = { actionsTarget = null },
        )
    }

    deleteTarget?.let { target ->
        XyOverlay(
            title = xy("Hapus postingan?", "Delete post?"),
            onDismiss = { if (!deleting) deleteTarget = null },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    xy(
                        "Postingan dan lampirannya akan dihapus permanen dari feed komunitas.",
                        "The post and its attachment will be permanently removed from the community feed.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    XyPillButton(
                        text = xy("Batal", "Cancel"),
                        onClick = { deleteTarget = null },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        text = if (deleting) xy("Menghapus…", "Deleting…") else xy("Hapus", "Delete"),
                        onClick = {
                            scope.launch {
                                deleting = true
                                try {
                                    api.delete(target.id)
                                    jokes = jokes.filterNot { it.id == target.id }
                                    api.cacheFeed(jokes)
                                    feedMessage = xyNow("🗑️ Postingan berhasil dihapus.", "🗑️ Post deleted.")
                                    feedMessageIsError = false
                                } catch (error: Exception) {
                                    feedMessage = showCommunityError(error)
                                    feedMessageIsError = true
                                } finally {
                                    deleting = false
                                    deleteTarget = null
                                }
                            }
                        },
                        enabled = !deleting,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    reportTarget?.let { joke ->
        XyOverlay(
            title = xy("Laporkan postingan", "Report this post"),
            onDismiss = { if (!reporting) reportTarget = null },
        ) {
            Text(
                xy(
                    "Laporan anonim membantu menjaga feed tetap aman dan bebas spam.",
                    "Anonymous reports help keep the feed safe and spam-free.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                XyPillButton(
                    text = xy("Batal", "Cancel"),
                    onClick = { reportTarget = null },
                    enabled = !reporting,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                XyPillButton(
                    text = if (reporting) xy("Mengirim…", "Sending…") else xy("Kirim laporan", "Submit report"),
                    onClick = {
                        if (!reporting) {
                            reporting = true
                            scope.launch {
                                try {
                                    val hidden = api.report(joke.id)
                                    reportedIds = reportedIds + joke.id
                                    if (hidden) jokes = jokes.filterNot { it.id == joke.id }
                                    feedMessage = xyNow("Laporan diterima.", "Report received.")
                                    feedMessageIsError = false
                                } catch (error: Exception) {
                                    feedMessage = showCommunityError(error)
                                    feedMessageIsError = true
                                } finally {
                                    reporting = false
                                    reportTarget = null
                                }
                            }
                        }
                    },
                    enabled = !reporting,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun shareFeedPost(context: android.content.Context, joke: CommunityJoke) {
    val author = joke.authorName ?: "Komunitas XyDesk"
    val shareText = buildString {
        if (joke.isAnnouncement) append("📢 [PENGUMUMAN RESMI XYDESK]\n")
        append(joke.text)
        if (!joke.sharedUrl.isNullOrBlank() && !joke.text.contains(joke.sharedUrl)) {
            append("\n").append(joke.sharedUrl)
        }
        append("\n\n— oleh $author di XyDesk Remote (https://www.xydeskremote.biz.id)")
    }
    runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Bagikan Postingan"))
    }
}

@Composable
private fun CommunityJokeInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0E1117))
            .border(1.dp, Color(0xFF232938), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        if (value.isEmpty()) {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp, max = 140.dp),
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Default,
            ),
            maxLines = 6,
        )
    }
}

@Composable
private fun CommunityJokeItem(
    joke: CommunityJoke,
    canManage: Boolean,
    onImageClick: (String, String?) -> Unit,
    onAuthorClick: (String) -> Unit,
    isReactionBusy: Boolean,
    onReact: (String) -> Unit,
    onOpenComments: () -> Unit,
    onOpenActions: () -> Unit,
    onQuickEdit: () -> Unit,
    onQuickDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var waReactBarOpen by remember(joke.id) { mutableStateOf(false) }
    var fullEmojiPickerOpen by remember(joke.id) { mutableStateOf(false) }
    val isPinnedAnnouncement = joke.isAnnouncement || joke.pinned
    val detectedUrl = remember(joke.id, joke.text, joke.sharedUrl) {
        joke.sharedUrl?.takeIf { it.isNotBlank() } ?: extractFirstUrl(joke.text)
    }
    val totalReactions = remember(joke.reactions) { joke.reactions.values.sum() }
    val activeReaction = joke.viewerReaction

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isPinnedAnnouncement) Color(0xFF19150E) else Color(0xFF13161E),
        border = BorderStroke(
            width = if (isPinnedAnnouncement) 1.5.dp else 1.dp,
            color = if (isPinnedAnnouncement) Color(0xFFF59E0B).copy(alpha = 0.78f) else Color(0xFF232834),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Pita Header Khusus Pengumuman Admin yang Otomatis Tersemat
            if (isPinnedAnnouncement) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("📌", fontSize = 12.sp)
                            Text(
                                text = xy("PENGUMUMAN RESMI · TERSEMAT OTOMATIS", "OFFICIAL ANNOUNCEMENT · AUTO-PINNED"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFFBBF24),
                                letterSpacing = 0.5.sp,
                            )
                        }
                        Text(
                            text = "FEED & GLOBAL CHAT",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE68A),
                        )
                    }
                }
            }

            // Baris Penulis + Avatar + Waktu + Tombol Edit/Hapus Cepat
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeedAvatarThumb(
                    avatarUrl = joke.authorAvatarUrl,
                    name = joke.authorName ?: "Anonim",
                    isFounder = isFounderAccount(joke.authorName) || isFounderAccount(joke.githubLogin),
                    size = 34.dp,
                )
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = joke.authorName ?: xy("Anonim", "Anonymous"),
                            modifier = Modifier.clickable {
                                onAuthorClick(joke.authorName ?: xyNow("Anonim", "Anonymous"))
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isPinnedAnnouncement) Color(0xFFFBBF24) else Color(0xFFF8FAFC),
                        )
                        if (isFounderAccount(joke.authorName) || isFounderAccount(joke.githubLogin)) {
                            XyFounderBadge(compact = true)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        feedTimeAgo(joke.createdAtMillis).takeIf { it.isNotEmpty() }?.let { ago ->
                            Text(
                                text = ago,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                            )
                        }
                        if (joke.editedAtMillis > 0) {
                            Text(
                                text = xy(" · diedit", " · edited"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                            )
                        }
                    }
                }

                if (canManage) {
                    Surface(
                        onClick = onQuickEdit,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1C212E),
                        border = BorderStroke(1.dp, Color(0xFF2D3548)),
                    ) {
                        Text(
                            text = xy("✏️ Edit", "✏️ Edit"),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        onClick = onQuickDelete,
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2A161B),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = "🗑️",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    "···",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenActions)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Isi Teks Postingan
            if (joke.text.isNotBlank()) {
                Text(
                    text = joke.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF1F5F9),
                    lineHeight = 20.sp,
                )
            }

            // Kartu OG Link Preview Otomatis (Sosmed / Website apa pun)
            if (!detectedUrl.isNullOrBlank()) {
                XyOpenGraphPreviewCard(url = detectedUrl)
            }

            // Lampiran Gambar HD / GIF / Autoplay Video Preview
            joke.attachmentUrl?.let { url ->
                FeedBinaryImage(
                    path = url,
                    mime = joke.attachmentMime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clickable { onImageClick(url, joke.attachmentMime) },
                )
            }

            // Pill Reaksi Gaya WhatsApp (👍 ❤️ 😂 😮 😢 🙏 🔥)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommunityJokesApi.QUICK_REACTIONS.forEach { emoji ->
                    val selected = joke.viewerReaction == emoji
                    val count = joke.reactions[emoji] ?: 0
                    Surface(
                        onClick = { if (!isReactionBusy) onReact(emoji) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) Color(0xFFF59E0B).copy(alpha = 0.22f) else Color(0xFF191D27),
                        border = BorderStroke(
                            1.dp,
                            if (selected) Color(0xFFF59E0B) else Color(0xFF262C3A),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(emoji, fontSize = 13.sp)
                            if (count > 0) {
                                Text(
                                    text = count.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) Color(0xFFFBBF24) else Color(0xFFCBD5E1),
                                )
                            }
                        }
                    }
                }
                Surface(
                    onClick = { if (!isReactionBusy) fullEmojiPickerOpen = !fullEmojiPickerOpen },
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF191D27),
                    border = BorderStroke(1.dp, Color(0xFF262C3A)),
                ) {
                    Text(
                        text = if (fullEmojiPickerOpen) "−" else "+",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFBBF24),
                    )
                }
            }

            if (fullEmojiPickerOpen) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF181C26),
                    border = BorderStroke(1.dp, Color(0xFF2B3244)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CommunityJokesApi.REACTIONS.chunked(8).forEach { rowEmojis ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                rowEmojis.forEach { emoji ->
                                    Text(
                                        emoji,
                                        modifier = Modifier
                                            .clickable(enabled = !isReactionBusy) {
                                                onReact(emoji)
                                                fullEmojiPickerOpen = false
                                            }
                                            .padding(vertical = 4.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Bar Utama: Like · Komentar · Share · Reaksi WA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tombol Like / React Utama
                Surface(
                    onClick = {
                        if (!isReactionBusy) {
                            onReact(activeReaction ?: "👍")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (activeReaction != null) Color(0xFFF59E0B).copy(alpha = 0.18f) else Color(0xFF181C26),
                    border = BorderStroke(
                        1.dp,
                        if (activeReaction != null) Color(0xFFF59E0B) else Color(0xFF252B39),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(activeReaction ?: "👍", fontSize = 13.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (totalReactions > 0) xy("Suka ($totalReactions)", "Like ($totalReactions)") else xy("Suka", "Like"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeReaction != null) Color(0xFFFBBF24) else Color(0xFFE2E8F0),
                        )
                    }
                }

                // Tombol Komentar
                Surface(
                    onClick = onOpenComments,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF181C26),
                    border = BorderStroke(1.dp, Color(0xFF252B39)),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("💬", fontSize = 13.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = xy("Komen (${joke.commentsTotal})", "Comment (${joke.commentsTotal})"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0),
                        )
                    }
                }

                // Tombol Share
                Surface(
                    onClick = onShare,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF181C26),
                    border = BorderStroke(1.dp, Color(0xFF252B39)),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("↗️", fontSize = 13.sp)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = xy("Bagikan", "Share"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kartu OpenGraph (OG Banner) Universal untuk Feed & Global Chat.
 * Menampilkan banner gambar OG, ikon sosmed/website, judul, deskripsi, dan status XyShield.
 */
@Composable
internal fun XyOpenGraphPreviewCard(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }
    var og by remember(url) { mutableStateOf<XyOpenGraphData?>(null) }
    var loading by remember(url) { mutableStateOf(true) }

    LaunchedEffect(url) {
        loading = true
        og = api.fetchOpenGraph(url)
        loading = false
    }

    val data = og
    val socialBrand = remember(url) { detectSocialBrandLabel(url) }

    Surface(
        onClick = {
            if (data?.safe != false) {
                openInAppCustomTab(context, data?.url ?: url)
            }
        },
        shape = RoundedCornerShape(14.dp),
        color = if (data?.safe == false) Color(0xFF2A1215) else Color(0xFF10131A),
        border = BorderStroke(
            1.dp,
            if (data?.safe == false) Color(0xFFEF4444) else Color(0xFF293040),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gambar OG Banner jika tersedia
            val imageUrl = data?.image.orEmpty()
            if (imageUrl.isNotBlank() && data?.safe != false) {
                FeedRemoteUrlBannerImage(
                    imageUrl = imageUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp),
                )
            } else if (data?.safe != false) {
                // Header Visual Brand Sosmed/Web jika situs tidak menyediakan og:image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1A202C), Color(0xFF131722)),
                            ),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(socialBrand.first, fontSize = 18.sp)
                        Column {
                            Text(
                                text = data?.siteName?.ifBlank { socialBrand.second } ?: socialBrand.second,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFF8FAFC),
                            )
                            Text(
                                text = data?.domain?.ifBlank { url } ?: url,
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${socialBrand.first} ${(data?.siteName?.ifBlank { socialBrand.second } ?: socialBrand.second).uppercase()}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF59E0B),
                    )
                    Text(
                        text = if (data?.safe == false) "🚨 DIBLOKIR XYSHIELD" else "🛡️ XyShield Aman",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (data?.safe == false) Color(0xFFEF4444) else Color(0xFF10B981),
                    )
                }
                Text(
                    text = when {
                        loading && data == null -> url
                        !data?.title.isNullOrBlank() -> data?.title.orEmpty()
                        else -> url
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!data?.description.isNullOrBlank()) {
                    Text(
                        text = data?.description.orEmpty(),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = data?.domain?.ifBlank { url } ?: url,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun detectSocialBrandLabel(url: String): Pair<String, String> {
    val u = url.lowercase()
    return when {
        "youtube.com" in u || "youtu.be" in u -> "▶️" to "YouTube"
        "tiktok.com" in u -> "🎵" to "TikTok"
        "instagram.com" in u -> "📸" to "Instagram"
        "twitter.com" in u || "x.com" in u -> "𝕏" to "X / Twitter"
        "github.com" in u -> "🐙" to "GitHub"
        "discord.gg" in u || "discord.com" in u -> "💬" to "Discord"
        "facebook.com" in u || "fb.watch" in u -> "📘" to "Facebook"
        "reddit.com" in u -> "👽" to "Reddit"
        "t.me" in u || "telegram.org" in u -> "✈️" to "Telegram"
        else -> "🌐" to "Web Link"
    }
}

private val remoteBannerCache = object : android.util.LruCache<String, ByteArray>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
}

@Composable
internal fun FeedRemoteUrlBannerImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    var bytes by remember(imageUrl) { mutableStateOf<ByteArray?>(remoteBannerCache.get(imageUrl)) }
    LaunchedEffect(imageUrl) {
        if (bytes == null && imageUrl.startsWith("https://")) {
            bytes = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = java.net.URL(imageUrl).openConnection() as javax.net.ssl.HttpsURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("User-Agent", "XyDesk-OGBot/1.1")
                    try {
                        if (conn.responseCode in 200..299) {
                            conn.inputStream.use { it.readBytes() }.takeIf { it.size <= 2_500_000 }
                        } else null
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()?.also { remoteBannerCache.put(imageUrl, it) }
            }
        }
    }
    val bmp = remember(bytes) {
        bytes?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
internal fun FeedAvatarThumb(
    avatarUrl: String?,
    name: String,
    isFounder: Boolean = false,
    size: Dp = 30.dp,
) {
    val cleanUrl = avatarUrl?.trim().orEmpty()
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isFounder) Color(0xFF3B2A10) else Color(0xFF222836))
            .border(
                width = if (isFounder) 1.5.dp else 1.dp,
                color = if (isFounder) Color(0xFFF59E0B) else Color(0xFF333B4D),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            cleanUrl.startsWith("/api/jokes/avatar/") -> {
                FeedBinaryImage(path = cleanUrl, modifier = Modifier.fillMaxSize(), round = true)
            }
            cleanUrl.startsWith("https://") -> {
                FeedRemoteUrlBannerImage(imageUrl = cleanUrl, modifier = Modifier.fillMaxSize())
            }
            else -> {
                Text(
                    text = name.trim().take(1).uppercase().ifEmpty { "U" },
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isFounder) Color(0xFFFBBF24) else Color(0xFFF8FAFC),
                )
            }
        }
    }
}

/**
 * Bottom sheet komentar dengan Foto Profil (Avatar), Balasan Komentar, `@mention`, & Notifikasi.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CommentsBottomSheet(
    joke: CommunityJoke,
    isViewerFounder: Boolean,
    isSending: Boolean,
    deletingCommentId: String?,
    onSend: (text: String, replyToId: String?, replyToName: String?) -> Unit,
    onDeleteComment: (JokeComment) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(joke.id) { mutableStateOf("") }
    var replyingTo by remember(joke.id) { mutableStateOf<JokeComment?>(null) }

    val mentionCandidates = remember(joke) {
        buildList {
            add("xykal")
            joke.authorName?.takeIf { it.isNotBlank() }?.let { add(it) }
            joke.comments.forEach { c ->
                c.name?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }.distinct().take(8)
    }

    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    xy("💬 {0} Komentar", "💬 {0} Comments", joke.commentsTotal),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    xy("Notifikasi Balasan & @Mention Aktif", "Reply & @Mention Notifications Active"),
                    fontSize = 10.sp,
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (joke.comments.isEmpty()) {
                    item {
                        Text(
                            xy("Belum ada komentar. Jadilah yang pertama berkomentar!", "No comments yet. Be the first to comment!"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(joke.comments, key = { it.id }) { c ->
                    val cAuthor = c.name ?: xy("Anonim", "Anonymous")
                    val cIsFounder = isFounderAccount(c.name) || isFounderAccount(c.githubLogin)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF141821),
                        border = BorderStroke(1.dp, Color(0xFF232938)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Foto Profil (Avatar) di setiap komentar
                            FeedAvatarThumb(
                                avatarUrl = c.avatarUrl,
                                name = cAuthor,
                                isFounder = cIsFounder,
                                size = 32.dp,
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = cAuthor,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (cIsFounder) Color(0xFFFBBF24) else MaterialTheme.colorScheme.primary,
                                    )
                                    if (cIsFounder) {
                                        Spacer(Modifier.width(5.dp))
                                        XyFounderBadge(compact = true)
                                    }
                                    feedTimeAgo(c.createdAtMillis).takeIf { it.isNotEmpty() }?.let { ago ->
                                        Text(
                                            " · $ago",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = xy("Balas", "Reply"),
                                        modifier = Modifier
                                            .clickable {
                                                replyingTo = c
                                                val tag = "@${cAuthor.replace(" ", "")} "
                                                if (!draft.startsWith(tag)) {
                                                    draft = (tag + draft).take(140)
                                                }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF59E0B),
                                    )
                                    if (c.isOwn || isViewerFounder) {
                                        Text(
                                            if (deletingCommentId == c.id) "…" else xy("Hapus", "Delete"),
                                            modifier = Modifier
                                                .clickable(enabled = deletingCommentId == null) { onDeleteComment(c) }
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                if (!c.replyToName.isNullOrBlank()) {
                                    Text(
                                        text = "↪ Membalas @${c.replyToName}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF94A3B8),
                                    )
                                }
                                Text(
                                    text = c.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFF1F5F9),
                                )
                            }
                        }
                    }
                }
            }

            // Indikator sedang membalas komentar tertentu
            replyingTo?.let { targetReply ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1D2230),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = xy("↪ Membalas komentar @${targetReply.name ?: "Anonim"}", "↪ Replying to @${targetReply.name ?: "Anonymous"}"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFBBF24),
                        )
                        Text(
                            text = "✕",
                            modifier = Modifier
                                .clickable { replyingTo = null }
                                .padding(horizontal = 6.dp),
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                        )
                    }
                }
            }

            // Bar Cepat @Mention
            if (mentionCandidates.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "@Mention:",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold,
                    )
                    mentionCandidates.forEach { handle ->
                        Surface(
                            onClick = {
                                val mentionToken = "@${handle.replace(" ", "")} "
                                if (!draft.contains(mentionToken.trim())) {
                                    draft = (draft + (if (draft.isEmpty() || draft.endsWith(" ")) "" else " ") + mentionToken).take(140)
                                }
                            },
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xFF1A1F2C),
                            border = BorderStroke(1.dp, Color(0xFF2B3244)),
                        ) {
                            Text(
                                text = "@$handle",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF11151E))
                        .border(1.dp, Color(0xFF262D3D), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.codePointCount(0, it.length) <= 140) draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 3,
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                Text(
                                    xy("Tulis komentar atau @mention…", "Write a comment or @mention…"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
                XyPillButton(
                    text = if (isSending) "…" else xy("Kirim", "Send"),
                    onClick = {
                        val clean = draft.trim()
                        if (clean.isNotEmpty() && !isSending) {
                            onSend(clean, replyingTo?.id, replyingTo?.name)
                            draft = ""
                            replyingTo = null
                        }
                    },
                    enabled = draft.trim().isNotEmpty() && !isSending,
                    compact = true,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ActionsBottomSheet(
    joke: CommunityJoke,
    canManage: Boolean,
    alreadyReported: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (canManage) {
                Text(
                    xy("✏️ Edit postingan", "✏️ Edit post"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    xy("🗑️ Hapus postingan", "🗑️ Delete post"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                xy("↗️ Bagikan postingan", "↗️ Share post"),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onShare)
                    .padding(horizontal = 12.dp, vertical = 13.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (alreadyReported) {
                Text(
                    xy("Sudah dilaporkan", "Already reported"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    xy("🚩 Laporkan", "🚩 Report"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onReport)
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val URL_EXTRACT_REGEX = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
private val MALICIOUS_URL_OR_EXT_REGEX = Regex(
    "(?:\\.(?:exe|msi|bat|cmd|scr|pif|vbs|vbe|js|jse|wsf|wsh|ps1|apk|dex|jar|hta|reg|dll|sys)(?:[?#\\s]|$)|javascript:|vbscript:|data:text/html|grabify\\.|iplogger\\.|bit\\.ly/virus|discord-nitro-free)",
    RegexOption.IGNORE_CASE,
)

internal fun extractFirstUrl(text: String): String? =
    URL_EXTRACT_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ']', ';')

internal fun isClientSideMaliciousText(text: String): Boolean =
    MALICIOUS_URL_OR_EXT_REGEX.containsMatchIn(text)

internal fun checkMaliciousUriOrFile(context: android.content.Context, uri: android.net.Uri): String? {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri)?.lowercase().orEmpty()
    val path = uri.toString().lowercase()
    val blockedExts = listOf(".exe", ".apk", ".bat", ".cmd", ".scr", ".msi", ".ps1", ".vbs", ".jar", ".dex", ".dll", ".sh")
    if (blockedExts.any { path.endsWith(it) } ||
        mime.contains("application/vnd.android.package-archive") ||
        mime.contains("application/x-msdownload") ||
        mime.contains("application/x-sh")
    ) {
        return xyNow(
            "🛡️ XyShield Anti-Virus: File eksekusi/APK/script berbahaya diblokir demi keamanan komunitas!",
            "🛡️ XyShield Anti-Virus: Executable/APK/script files are blocked for community safety!",
        )
    }
    return null
}

/**
 * Encode gambar HD (1920px @ 90% WebP/JPEG hingga 2,5 MB) atau Video MP4 (hingga 2,6 MB)
 * agar gambar tidak burik/terlalu dikompres dan video bisa diputar otomatis (autoplay preview).
 */
private fun encodePickedFeedImage(
    context: android.content.Context,
    uri: android.net.Uri,
    hdMode: Boolean = true,
): FeedAttachment? {
    val limit = 2_600_000
    return runCatching {
        val resolver = context.contentResolver
        val type = resolver.getType(uri).orEmpty().lowercase()
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (type == "image/gif" && raw.size <= limit) return FeedAttachment("image/gif", raw)
        if (type.startsWith("video/") && raw.size <= limit) return FeedAttachment("video/mp4", raw)
        val src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val maxSide = if (hdMode) 1920 else 1280
        val scale = minOf(1f, maxSide / maxOf(src.width, src.height).toFloat())
        var out = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                src,
                (src.width * scale).toInt().coerceAtLeast(1),
                (src.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            src
        }
        val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.graphics.Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            android.graphics.Bitmap.CompressFormat.WEBP
        }
        var bytes = ByteArray(0)
        var quality = if (hdMode) 92 else 84
        var guard = 0
        while (guard++ < 8) {
            val stream = java.io.ByteArrayOutputStream()
            out.compress(fmt, quality, stream)
            bytes = stream.toByteArray()
            if (bytes.size <= limit) break
            if (quality > 65) {
                quality -= 12
            } else {
                out = android.graphics.Bitmap.createScaledBitmap(
                    out,
                    (out.width * 0.78f).toInt().coerceAtLeast(1),
                    (out.height * 0.78f).toInt().coerceAtLeast(1),
                    true,
                )
            }
        }
        if (bytes.isEmpty() || bytes.size > limit) null else FeedAttachment("image/webp", bytes)
    }.getOrNull()
}

private val feedImageCache = object : android.util.LruCache<String, ByteArray>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
}

/**
 * Menampilkan lampiran Feed:
 * - Video (`video/mp4`, `video/webm`) otomatis berputar preview tanpa suara (Autoplay Muted Loop) walau tidak dibuka!
 * - GIF dianimasikan otomatis.
 * - Gambar HD dirender tajam.
 */
@Composable
internal fun FeedBinaryImage(
    path: String,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    mime: String? = null,
    fit: Boolean = false,
) {
    val context = LocalContext.current
    var bytes by remember(path) { mutableStateOf<ByteArray?>(feedImageCache.get(path)) }
    LaunchedEffect(path) {
        if (bytes == null) {
            bytes = withContext(Dispatchers.IO) {
                runCatching { CommunityJokesApi(context).fetchBinary(path) }.getOrNull()
                    ?.also { feedImageCache.put(path, it) }
            }
        }
    }
    val data = bytes
    val shape = if (round) CircleShape else RoundedCornerShape(12.dp)

    if (mime?.startsWith("video/") == true && data != null && data.isNotEmpty()) {
        FeedAutoplayVideoPreview(
            videoBytes = data,
            cacheKey = path,
            modifier = modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(shape),
        )
    } else if (mime == "image/gif" &&
        android.os.Build.VERSION.SDK_INT >= 28 &&
        data != null && data.isNotEmpty()
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.widget.ImageView(ctx).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
            },
            update = { view ->
                runCatching {
                    val source = android.graphics.ImageDecoder.createSource(
                        java.nio.ByteBuffer.wrap(data),
                    )
                    val drawable = android.graphics.ImageDecoder.decodeDrawable(source)
                    view.setImageDrawable(drawable)
                    (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
                }
            },
            modifier = modifier.clip(shape),
        )
    } else {
        val bmp = remember(data) {
            data?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        }
        bmp?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.clip(shape),
                contentScale = when {
                    round -> ContentScale.Crop
                    fit -> ContentScale.Fit
                    else -> ContentScale.FillWidth
                },
            )
        }
    }
}

/**
 * Video Autoplay Preview (berputar otomatis tanpa suara dalam loop walau belum dibuka penuh,
 * dengan tombol Mute/Unmute cepat di pojok).
 */
@Composable
internal fun FeedAutoplayVideoPreview(
    videoBytes: ByteArray,
    cacheKey: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var muted by remember(cacheKey) { mutableStateOf(true) }
    var mediaPlayerRef by remember(cacheKey) { mutableStateOf<android.media.MediaPlayer?>(null) }
    val tempFile = remember(cacheKey, videoBytes.size) {
        runCatching {
            val safeName = "xy_vid_" + cacheKey.hashCode().toUInt().toString(16) + ".mp4"
            val file = java.io.File(context.cacheDir, safeName)
            if (!file.exists() || file.length() != videoBytes.size.toLong()) {
                file.writeBytes(videoBytes)
            }
            file
        }.getOrNull()
    }

    DisposableEffect(cacheKey) {
        onDispose {
            runCatching { mediaPlayerRef?.stop() }
            runCatching { mediaPlayerRef?.release() }
            mediaPlayerRef = null
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        if (tempFile != null) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoPath(tempFile.absolutePath)
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            mp.isLooping = true
                            val vol = if (muted) 0f else 1f
                            mp.setVolume(vol, vol)
                            start()
                        }
                    }
                },
                update = { view ->
                    val vol = if (muted) 0f else 1f
                    runCatching { mediaPlayerRef?.setVolume(vol, vol) }
                    if (!view.isPlaying) {
                        runCatching { view.start() }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Surface(
            onClick = { muted = !muted },
            shape = RoundedCornerShape(999.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
        ) {
            Text(
                text = if (muted) "🔇 Autoplay · Ketuk Suara" else "🔊 Suara Aktif",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun FeedImageViewer(url: String, mime: String?, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else androidx.compose.ui.geometry.Offset.Zero
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onDismiss() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            },
    ) {
        FeedBinaryImage(
            path = url,
            mime = mime,
            fit = true,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .transformable(transformState),
        )
        Text(
            xy("Ketuk untuk tutup · ketuk dua kali / cubit untuk zoom", "Tap to close · double-tap / pinch to zoom"),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 26.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}

internal fun feedTimeAgo(millis: Long): String {
    if (millis <= 0L) return ""
    val diff = System.currentTimeMillis() - millis
    if (diff < 0L) return ""
    val id = java.util.Locale.getDefault().language.startsWith("id")
    return when {
        diff < 60_000L -> if (id) "baru saja" else "just now"
        diff < 3_600_000L -> {
            val m = diff / 60_000L
            if (id) "$m mnt lalu" else "${m}m ago"
        }
        diff < 86_400_000L -> {
            val h = diff / 3_600_000L
            if (id) "$h jam lalu" else "${h}h ago"
        }
        diff < 172_800_000L -> if (id) "kemarin" else "yesterday"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
            .format(java.util.Date(millis))
    }
}
