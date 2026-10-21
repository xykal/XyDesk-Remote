package id.xydesk.remote.ui

import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import id.xydesk.remote.ui.components.XyEmptyState
import id.xydesk.remote.ui.components.XyErrorState
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyLoadingSkeletonCard
import id.xydesk.remote.ui.components.XySuccessStateBanner
import java.util.Locale
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    var studioAttachment by remember { mutableStateOf<FeedAttachment?>(null) }

    val authRev = GitHubDirectOAuthController.authRevision
    val myGithubLogin = remember(context, composerOpen, refreshKey, authRev) { getVerifiedGitHubLogin(context) }
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
                    studioAttachment = att
                }
            }
        }
    }
    var feedError by remember { mutableStateOf<String?>(null) }
    var feedMessage by remember { mutableStateOf<String?>(null) }
    var feedMessageIsError by remember { mutableStateOf(false) }

    studioAttachment?.let { activeAtt ->
        val initialDataUri = remember(activeAtt) {
            "data:${activeAtt.mime};base64," + Base64.encodeToString(activeAtt.bytes, Base64.NO_WRAP)
        }
        val kind = remember(activeAtt.mime) {
            if (activeAtt.mime.startsWith("video/")) "video" else "photo"
        }
        PreSendMediaStudioDialog(
            initialAttachmentData = initialDataUri,
            initialAttachmentName = "Feed Media",
            initialAttachmentType = kind,
            initialCaption = draft,
            initialViewOnce = false,
            onDismiss = { studioAttachment = null },
            onConfirmSend = { finalData, _, _, captionText, _ ->
                studioAttachment = null
                if (captionText.isNotBlank()) {
                    draft = captionText
                }
                val updatedAtt = if (finalData != initialDataUri && finalData.startsWith("data:image/")) {
                    runCatching {
                        val rawB64 = finalData.substringAfter("base64,", "")
                        val decoded = Base64.decode(rawB64, Base64.DEFAULT)
                        FeedAttachment(mime = "image/jpeg", bytes = decoded)
                    }.getOrNull() ?: activeAtt
                } else if (finalData != initialDataUri && finalData.startsWith("data:video/")) {
                    runCatching {
                        val rawB64 = finalData.substringAfter("base64,", "")
                        val decoded = Base64.decode(rawB64, Base64.DEFAULT)
                        FeedAttachment(mime = "video/mp4", bytes = decoded)
                    }.getOrNull() ?: activeAtt
                } else {
                    activeAtt
                }
                pendingAttachment = updatedAtt
                editRemoveAttachment = false
                formMessage = null
            },
        )
    }

    fun showCommunityError(error: Throwable, loadingFeed: Boolean = false): String {
        val reason = (error as? CommunityJokesException)?.reason.orEmpty()
        return when {
            loadingFeed && (reason == "not_found" || reason == "http_404") ->
                xyNow("Feed server belum tersedia (404). Deploy Worker terbaru, lalu coba lagi.", "The feed endpoint isn't deployed yet (404). Deploy the latest Worker, then retry.")
            loadingFeed && reason == "unavailable" ->
                xyNow("Feed sementara tidak tersedia di server. Coba lagi sebentar.", "The feed server is temporarily unavailable. Please retry shortly.")
            reason == "rate_limited" -> xyNow("Tunggu sebentar sebelum mengirim lagi.", "Please wait a little before posting again.")
            reason == "malicious_blocked" -> xyNow("XyShield: Tautan atau file berbahaya (virus/phishing) diblokir demi keamanan.", "XyShield: Malicious link or virus file blocked for security.")
            reason == "forbidden" -> xyNow("Hanya pemilik postingan yang dapat mengubah atau menghapus postingan ini.", "Only the post author can edit or delete this post.")
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
        if (refreshKey > 0 || refreshSignal > 0) {
            feedImageCache.evictAll()
            feedDecodedBitmapCache.evictAll()
            remoteBannerBitmapCache.evictAll()
        }
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
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onHide != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    xy("Feed Komunitas", "Community Feed"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
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
                                editingJoke != null -> xy("Edit Postingan", "Edit Post")
                                isAnnouncementDraft -> xy("Buat Pengumuman Resmi Admin", "Create Official Admin Announcement")
                                else -> xy("Buat Postingan Baru", "Create New Post")
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC),
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
                                        "Sematkan Otomatis sebagai Pengumuman di Feed & Global Chat",
                                        "Auto-Pin as Official Announcement in Feed & Global Chat",
                                    ),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAnnouncementDraft) Color(0xFFF4F4F5) else Color(0xFFCBD5E1),
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
                            text = xy("Foto HD / GIF / Video", "HD Photo / GIF / Video"),
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
                                text = if (hdUploadEnabled) "HD Aktif" else "SD Standar",
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
                                        text = "Video Siap (${att.bytes.size / 1024} KB)",
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
                            maliciousLinkDetected -> xy("XyShield: Tautan/file berbahaya diblokir", "XyShield: Malicious link/file blocked")
                            else -> xy("{0}/500 karakter · XyShield Aktif", "{0}/500 chars · XyShield Active", characterCount)
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
                                                if (target != null) "Postingan berhasil diperbarui." else "Postingan berhasil dipublikasikan.",
                                                if (target != null) "Post updated." else "Post published.",
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
            val myDispName = remember(myGithubLogin) {
                api.displayName() ?: myGithubLogin?.takeIf { it.isNotBlank() } ?: "U"
            }
            val myAvatarUrl = remember(myGithubLogin) {
                getVerifiedGitHubAvatarUrl(context)
                    ?: myGithubLogin?.takeIf { it.isNotBlank() }?.let { "https://github.com/$it.png?size=160" }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingJoke = null
                            draft = ""
                            pendingAttachment = null
                            editRemoveAttachment = false
                            isAnnouncementDraft = false
                            composerOpen = true
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FeedAvatarThumb(
                        avatarUrl = myAvatarUrl,
                        name = myDispName,
                        isFounder = isViewerFounder,
                        size = 36.dp,
                    )
                    Text(
                        text = xy("Apa yang ingin kamu bagikan?", "What's on your mind?"),
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.weight(1f),
                    )
                    if (isViewerFounder) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    editingJoke = null
                                    draft = ""
                                    pendingAttachment = null
                                    editRemoveAttachment = false
                                    isAnnouncementDraft = true
                                    composerOpen = true
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = id.xydesk.remote.ui.components.XyIcons.Megaphone,
                                contentDescription = "Pengumuman",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = id.xydesk.remote.ui.components.XyIcons.Edit,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.75.dp)
                        .background(Color(0xFF1D222D)),
                )
            }
        }

        formMessage?.let { msg ->
            if (formMessageIsError) {
                XyErrorState(
                    message = msg,
                    onRetry = { formMessage = null },
                )
            } else {
                XySuccessStateBanner(
                    message = msg,
                    onDismiss = { formMessage = null },
                )
            }
        }

        feedMessage?.let { msg ->
            if (feedMessageIsError) {
                XyErrorState(
                    message = msg,
                    onRetry = { feedMessage = null },
                )
            } else {
                XySuccessStateBanner(
                    message = msg,
                    onDismiss = { feedMessage = null },
                )
            }
        }
        if (feedError != null && jokes.isNotEmpty()) {
            XyErrorState(
                message = feedError.orEmpty(),
                onRetry = { refreshKey += 1 },
            )
        }

        when {
            refreshing && jokes.isEmpty() -> {
                XyLoadingSkeletonCard(
                    count = 3,
                    itemHeight = 84.dp,
                )
            }
            feedError != null && jokes.isEmpty() -> {
                XyErrorState(
                    message = feedError.orEmpty(),
                    onRetry = { refreshKey += 1 },
                )
            }
            jokes.isEmpty() -> {
                XyEmptyState(
                    title = xy("Belum Ada Postingan", "No Posts Yet"),
                    subtitle = xy("Jadilah yang pertama membagikan informasi, tips RDP, atau media ke komunitas XyDesk.", "Be the first to share news, RDP tips, or media with the XyDesk community."),
                    icon = id.xydesk.remote.ui.components.XyIcons.Globe,
                    actionText = xy("Tulis Postingan Pertama", "Write First Post"),
                    onAction = { composerOpen = true },
                )
            }
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
                        androidx.compose.material3.Icon(
                            imageVector = XyIcons.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable { authorFilter = null }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .size(14.dp),
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
                        val canManagePost = joke.isOwn
                        CommunityJokeItem(
                            joke = joke,
                            canManage = canManagePost,
                            onImageClick = { url, mime -> fullscreenImage = url to mime },
                            onAuthorClick = { name ->
                                authorFilter = name
                                showAll = true
                            },
                            isReactionBusy = activeReactionId == joke.id,
                            onLike = {
                                if (activeReactionId == null) {
                                    val prevJoke = joke
                                    val nextLiked = !joke.viewerLiked
                                    val nextCount = (joke.likesCount + if (nextLiked) 1 else -1).coerceAtLeast(0)
                                    updateJoke(joke.copy(viewerLiked = nextLiked, likesCount = nextCount))
                                    scope.launch {
                                        activeReactionId = joke.id
                                        feedMessage = null
                                        try {
                                            updateJoke(api.like(joke.id))
                                        } catch (error: Exception) {
                                            updateJoke(prevJoke)
                                            feedMessage = showCommunityError(error)
                                            feedMessageIsError = true
                                        } finally {
                                            activeReactionId = null
                                        }
                                    }
                                }
                            },
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
        val canManage = target.isOwn
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
                                    feedMessage = xyNow("Postingan berhasil dihapus.", "Post deleted.")
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
        if (joke.isAnnouncement) append("[PENGUMUMAN RESMI XYDESK]\n")
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
    onLike: () -> Unit,
    onReact: (String) -> Unit,
    onOpenComments: () -> Unit,
    onOpenActions: () -> Unit,
    onQuickEdit: () -> Unit,
    onQuickDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var fullEmojiPickerOpen by remember(joke.id) { mutableStateOf(false) }
    var reactAnimTrigger by remember(joke.id) { mutableIntStateOf(0) }
    var likeAnimTrigger by remember(joke.id) { mutableIntStateOf(0) }
    val likePopAnim = remember(joke.id) { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(likeAnimTrigger) {
        if (likeAnimTrigger > 0) {
            likePopAnim.snapTo(0.65f)
            likePopAnim.animateTo(
                targetValue = 1.32f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
                ),
            )
            likePopAnim.animateTo(1f)
        }
    }
    val isPinnedAnnouncement = joke.isAnnouncement || joke.pinned
    val detectedUrl = remember(joke.id, joke.text, joke.sharedUrl) {
        joke.sharedUrl?.takeIf { it.isNotBlank() } ?: extractFirstUrl(joke.text)
    }
    val totalEmojiReactions = remember(joke.reactions) {
        joke.reactions.entries.filter { it.key != "❤️" }.sumOf { it.value }
    }
    val activeReaction = joke.viewerReaction
    val isFounderAuthor = isFounderAccount(joke.authorName) || isFounderAccount(joke.githubLogin)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        if (isPinnedAnnouncement) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = id.xydesk.remote.ui.components.XyIcons.Pin,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text = xy("Pengumuman Tersemat", "Pinned Announcement"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFBBF24),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            FeedAvatarThumb(
                avatarUrl = joke.authorAvatarUrl,
                name = joke.authorName ?: "Anonim",
                githubLogin = joke.githubLogin,
                isFounder = isFounderAuthor,
                size = 38.dp,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header: Nama + Badge + Waktu + Aksi Flat (tanpa kotak/border)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = joke.authorName ?: xy("Anonim", "Anonymous"),
                            modifier = Modifier.clickable {
                                onAuthorClick(joke.authorName ?: xyNow("Anonim", "Anonymous"))
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFounderAuthor) Color(0xFFE9D5FF) else Color(0xFFF8FAFC),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        XyUserRoleBadges(
                            name = joke.authorName,
                            githubLogin = joke.githubLogin,
                            compact = true,
                        )
                        feedTimeAgo(joke.createdAtMillis).takeIf { it.isNotEmpty() }?.let { ago ->
                            Text(
                                text = "· $ago",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                            )
                        }
                        if (joke.editedAtMillis > 0) {
                            Text(
                                text = xy("(diedit)", "(edited)"),
                                fontSize = 10.sp,
                                color = Color(0xFF475569),
                            )
                        }
                    }

                    if (canManage) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onQuickEdit),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = id.xydesk.remote.ui.components.XyIcons.Edit,
                                contentDescription = "Edit",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onQuickDelete),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = id.xydesk.remote.ui.components.XyIcons.Trash,
                                contentDescription = "Hapus",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onOpenActions),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = id.xydesk.remote.ui.components.XyIcons.MoreHorizontal,
                            contentDescription = "Lainnya",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // Isi Teks Postingan (Semua Link/URL Berwarna Biru Cerah & Bisa Diklik!)
                if (joke.text.isNotBlank()) {
                    XyClickableLinkText(
                        text = joke.text,
                        color = Color(0xFFF1F5F9),
                        linkColor = Color(0xFF38BDF8),
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                    )
                }

                // OG Link Preview
                if (!detectedUrl.isNullOrBlank()) {
                    XyOpenGraphPreviewCard(url = detectedUrl)
                }

                // Lampiran Gambar HD / GIF / Video
                joke.attachmentUrl?.let { url ->
                    FeedBinaryImage(
                        path = url,
                        mime = joke.attachmentMime,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onImageClick(url, joke.attachmentMime) },
                    )
                }

                // Pemilih Reaksi Emoji (Terpisah dari Like Hati — Beranimasi saat Di-React)
                if (fullEmojiPickerOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF151923))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CommunityJokesApi.QUICK_REACTIONS.forEach { emoji ->
                            val isSelectedEmoji = activeReaction == emoji
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelectedEmoji) Color(0xFF2E364F) else Color(0xFF1E2433))
                                    .clickable(enabled = !isReactionBusy) {
                                        reactAnimTrigger += 1
                                        onReact(emoji)
                                        fullEmojiPickerOpen = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                IosWhatsAppEmoji3D(
                                    tag = emoji,
                                    size = 21.dp,
                                    animateTrigger = reactAnimTrigger,
                                )
                            }
                        }
                    }
                }

                // Action Bar Modern Flat (Like Hati Terpisah dari Reaksi Emoji + Komentar + Bagikan)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // 1. Tombol LIKE (Murni Ikon Hati Vektor + Counter Likes)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = !isReactionBusy) {
                                    likeAnimTrigger += 1
                                    onLike()
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = if (joke.viewerLiked) {
                                    id.xydesk.remote.ui.components.XyIcons.HeartFilled
                                } else {
                                    id.xydesk.remote.ui.components.XyIcons.Heart
                                },
                                contentDescription = "Suka",
                                tint = if (joke.viewerLiked) Color(0xFFF43F5E) else Color(0xFF94A3B8),
                                modifier = Modifier
                                    .size(17.dp)
                                    .scale(likePopAnim.value),
                            )
                            if (joke.likesCount > 0) {
                                Text(
                                    text = joke.likesCount.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (joke.viewerLiked) FontWeight.Bold else FontWeight.Medium,
                                    color = if (joke.viewerLiked) Color(0xFFF43F5E) else Color(0xFF94A3B8),
                                )
                            }
                        }

                        // 2. Tombol REACT EMOJI (Terpisah dari Like!)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = !isReactionBusy) {
                                    fullEmojiPickerOpen = !fullEmojiPickerOpen
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            if (activeReaction != null) {
                                IosWhatsAppEmoji3D(
                                    tag = activeReaction,
                                    size = 17.dp,
                                    animateTrigger = reactAnimTrigger,
                                )
                            } else {
                                androidx.compose.material3.Icon(
                                    imageVector = id.xydesk.remote.ui.components.XyIcons.Emoji,
                                    contentDescription = "Reaksi Emoji",
                                    tint = if (fullEmojiPickerOpen) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            if (totalEmojiReactions > 0) {
                                Text(
                                    text = totalEmojiReactions.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (activeReaction != null) FontWeight.Bold else FontWeight.Medium,
                                    color = if (activeReaction != null) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                                )
                            }
                        }

                        // 3. Tombol KOMENTAR (Flat)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenComments)
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = id.xydesk.remote.ui.components.XyIcons.Chat,
                                contentDescription = "Komentar",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(17.dp),
                            )
                            if (joke.commentsTotal > 0) {
                                Text(
                                    text = joke.commentsTotal.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                        }

                        // 4. Tombol BAGIKAN (Flat)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(onClick = onShare)
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = id.xydesk.remote.ui.components.XyIcons.Share,
                                contentDescription = "Bagikan",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Ringkasan Top Emoji Reaksi di kanan (Tanpa Hati Like)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val topEmojis = remember(joke.reactions) {
                            joke.reactions.entries
                                .filter { it.value > 0 && it.key != "❤️" }
                                .sortedByDescending { it.value }
                                .take(3)
                        }
                        topEmojis.forEach { (emoji, count) ->
                            Row(
                                modifier = Modifier.clickable(enabled = !isReactionBusy) {
                                    reactAnimTrigger += 1
                                    onReact(emoji)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                IosWhatsAppEmoji3D(
                                    tag = emoji,
                                    size = 15.dp,
                                    animateTrigger = reactAnimTrigger,
                                )
                                Text(
                                    text = count.toString(),
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                )
                            }
                        }
                    }
                }

                // Pratinjau Komentar Inline ala TikTok (PP Bulat + Nama + Badge + Isi Komentar)
                if (joke.comments.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF121620))
                            .border(0.75.dp, Color(0xFF1E2536), RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenComments)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        joke.comments.distinctBy { it.id }.takeLast(2).forEach { c ->
                            val cName = c.name?.takeIf { it.isNotBlank() } ?: xy("Anonim", "Anonymous")
                            val cFounder = isFounderAccount(c.name) || isFounderAccount(c.githubLogin)
                            val isCreator = !joke.authorName.isNullOrBlank() &&
                                (c.name.equals(joke.authorName, ignoreCase = true) ||
                                    (!joke.githubLogin.isNullOrBlank() && c.githubLogin.equals(joke.githubLogin, ignoreCase = true)))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                FeedAvatarThumb(
                                    avatarUrl = c.avatarUrl,
                                    name = cName,
                                    githubLogin = c.githubLogin,
                                    isFounder = cFounder,
                                    size = 20.dp,
                                )
                                Text(
                                    text = cName,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cFounder) Color(0xFFE9D5FF) else Color(0xFFE2E8F0),
                                    maxLines = 1,
                                )
                                if (isCreator) {
                                    XyCreatorBadge()
                                }
                                XyUserRoleBadges(
                                    name = c.name,
                                    githubLogin = c.githubLogin,
                                    compact = true,
                                )
                                Text(
                                    text = c.text,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        if (joke.commentsTotal > 2) {
                            Text(
                                text = xy("Lihat semua {0} komentar…", "View all {0} comments…", joke.commentsTotal),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .background(Color(0xFF1D222D)),
        )
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
                        androidx.compose.material3.Icon(
                            imageVector = id.xydesk.remote.ui.components.XyIcons.Globe,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp),
                        )
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
                        text = (data?.siteName?.ifBlank { socialBrand.second } ?: socialBrand.second).uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF59E0B),
                    )
                    Text(
                        text = if (data?.safe == false) "DIBLOKIR XYSHIELD" else "XyShield Aman",
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
        "youtube.com" in u || "youtu.be" in u -> "YT" to "YouTube"
        "tiktok.com" in u -> "TT" to "TikTok"
        "instagram.com" in u -> "IG" to "Instagram"
        "twitter.com" in u || "x.com" in u -> "X" to "X / Twitter"
        "github.com" in u -> "GH" to "GitHub"
        "discord.gg" in u || "discord.com" in u -> "DC" to "Discord"
        "facebook.com" in u || "fb.watch" in u -> "FB" to "Facebook"
        "reddit.com" in u -> "RD" to "Reddit"
        "t.me" in u || "telegram.org" in u -> "TG" to "Telegram"
        else -> "WEB" to "Web Link"
    }
}

private val remoteBannerCache = object : android.util.LruCache<String, ByteArray>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
}

private val remoteBannerBitmapCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(48)

@Composable
internal fun FeedRemoteUrlBannerImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    var bmp by remember(imageUrl) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(remoteBannerBitmapCache.get(imageUrl))
    }
    LaunchedEffect(imageUrl) {
        if (bmp == null && imageUrl.startsWith("https://")) {
            bmp = withContext(Dispatchers.IO) {
                val raw = remoteBannerCache.get(imageUrl) ?: runCatching {
                    val conn = java.net.URL(imageUrl).openConnection() as javax.net.ssl.HttpsURLConnection
                    conn.connectTimeout = 5000
                    conn.readTimeout = 6000
                    conn.setRequestProperty("User-Agent", "XyDesk-OGBot/1.1")
                    try {
                        if (conn.responseCode in 200..299) {
                            conn.inputStream.use { it.readBytes() }.takeIf { it.size <= 2_500_000 }
                        } else null
                    } finally {
                        conn.disconnect()
                    }
                }.getOrNull()?.also { remoteBannerCache.put(imageUrl, it) }
                raw?.let {
                    runCatching {
                        android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
                    }.getOrNull()
                }?.also { remoteBannerBitmapCache.put(imageUrl, it) }
            }
        }
    }
    val readyBmp = bmp
    if (readyBmp != null) {
        Image(
            bitmap = readyBmp,
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
    githubLogin: String? = null,
    isFounder: Boolean = false,
    size: Dp = 30.dp,
    durationRank: Int? = null,
) {
    val context = LocalContext.current
    val resolvedUrl = remember(avatarUrl, githubLogin, name) {
        ChatAvatarLoader.normalizeAvatarUrl(avatarUrl, githubLogin, name)
    }
    var avatarBmp by remember(resolvedUrl) {
        mutableStateOf(if (resolvedUrl.isNotBlank()) avatarMemoryCache.get(resolvedUrl) else null)
    }
    LaunchedEffect(resolvedUrl, githubLogin, name) {
        if (avatarBmp == null && (resolvedUrl.isNotBlank() || !githubLogin.isNullOrBlank() || name.isNotBlank())) {
            ChatAvatarLoader.load(
                url = resolvedUrl,
                context = context,
                githubLogin = githubLogin,
                fallbackName = name,
            )?.let { avatarBmp = it }
        }
    }
    val topTen by LeaderboardPodium.topTen.collectAsState()
    val effectiveRank: Int? = durationRank
        ?: topTen[normalizedChatAccount(githubLogin)]
        ?: topTen[normalizedChatAccount(name)]
    val paletteColors = remember(name) {
        val hash = kotlin.math.abs(name.hashCode())
        when (hash % 6) {
            0 -> listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
            1 -> listOf(Color(0xFF0284C7), Color(0xFF2563EB))
            2 -> listOf(Color(0xFF059669), Color(0xFF0D9488))
            3 -> listOf(Color(0xFFE11D48), Color(0xFFDB2777))
            4 -> listOf(Color(0xFFD97706), Color(0xFFEA580C))
            else -> listOf(Color(0xFF6366F1), Color(0xFFEC4899))
        }
    }
    Box(
        modifier = Modifier
            .requiredSize(size)
            .clip(CircleShape)
            .background(
                if (isFounder) {
                    Brush.linearGradient(listOf(Color(0xFF581C87), Color(0xFF9333EA)))
                } else {
                    Brush.linearGradient(paletteColors)
                },
            )
            .then(
                if (isFounder) {
                    Modifier.border(1.3.dp, Color(0xFFFBBF24), CircleShape)
                } else {
                    Modifier.border(0.8.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                },
            )
            .podiumFrame(effectiveRank?.takeIf { it in 1..3 }),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = avatarBmp
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = name.trim().removePrefix("@").take(1).uppercase().ifEmpty { "U" },
                fontSize = (size.value * 0.40f).sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isFounder) Color(0xFFFEF08A) else Color.White,
            )
            when {
                resolvedUrl.startsWith("/api/jokes/avatar/") -> {
                    FeedBinaryImage(path = resolvedUrl, modifier = Modifier.fillMaxSize(), round = true)
                }
                resolvedUrl.startsWith("https://") -> {
                    FeedRemoteUrlBannerImage(imageUrl = resolvedUrl, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

/**
 * Bottom Sheet Komentar Bergaya TikTok Penuh:
 * - Foto Profil Bulat (PP) dengan fallback avatar GitHub & cincin Founder/Podium
 * - Nama Tampilan Tebal + `@username` + tag `Pembuat` (#FE2C55) + Badge (`FOUNDER`, `XYTEAM`, `TESTER`, `TOP #1..#10`)
 * - Sub-komentar / Balasan berjenjang (menjorok ke kanan seperti TikTok)
 * - Tombol Like Hati (`♥`) di sisi kanan setiap komentar dengan counter
 * - Baris Emoji Cepat TikTok + Bilah Input dengan PP pengguna sendiri
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
    val context = LocalContext.current
    val viewerLogin = rememberVerifiedChatAccount(context)
    val viewerAvatar = remember(viewerLogin) { getVerifiedGitHubAvatarUrl(context) }
    val viewerDisplayName = remember(viewerLogin) {
        viewerLogin ?: context.getSharedPreferences("xydesk.global.chat", android.content.Context.MODE_PRIVATE)
            .getString("nickname", "Saya") ?: "Saya"
    }
    val commentLikePrefs = remember(context) {
        context.getSharedPreferences("xydesk.tiktok.comment.likes", android.content.Context.MODE_PRIVATE)
    }
    var likeRevision by remember { mutableIntStateOf(0) }
    var draft by remember(joke.id) { mutableStateOf("") }
    var replyingTo by remember(joke.id) { mutableStateOf<JokeComment?>(null) }

    val uniqueComments = remember(joke.comments) {
        joke.comments.distinctBy { it.id }
    }

    val quickEmojis = remember { listOf("😂", "🔥", "❤️", "👏", "🤣", "😍", "😭", "🙏") }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12141C),
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF475569)),
                )
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header Tengah ala TikTok: "X komentar" + tombol X di kanan
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Text(
                    text = xy("{0} komentar", "{0} comments", joke.commentsTotal.coerceAtLeast(uniqueComments.size)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFF8FAFC),
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2433))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = XyIcons.Close,
                        contentDescription = xy("Tutup", "Close"),
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.6.dp)
                    .background(Color(0xFF1F2637)),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 420.dp)
                    .padding(horizontal = 14.dp),
            ) {
                if (uniqueComments.isEmpty()) {
                    item(key = "empty_comments") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = xy("Belum ada komentar", "No comments yet"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                            )
                            Text(
                                text = xy("Mulai percakapan dengan menulis komentar pertama!", "Start the conversation by leaving the first comment!"),
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                    }
                }
                items(uniqueComments, key = { it.id }) { c ->
                    val cAuthor = c.name?.takeIf { it.isNotBlank() } ?: xy("Anonim", "Anonymous")
                    val cHandle = c.githubLogin?.takeIf { it.isNotBlank() && !it.equals(cAuthor, ignoreCase = true) }
                    val cIsFounder = isFounderAccount(c.name) || isFounderAccount(c.githubLogin)
                    val isCreator = !joke.authorName.isNullOrBlank() &&
                        (c.name.equals(joke.authorName, ignoreCase = true) ||
                            (!joke.githubLogin.isNullOrBlank() && c.githubLogin.equals(joke.githubLogin, ignoreCase = true)))
                    val isReply = !c.replyToId.isNullOrBlank() || !c.replyToName.isNullOrBlank() || c.text.trim().startsWith("@")
                    val likedKey = "liked_${joke.id}_${c.id}"
                    val isCommentLiked = remember(c.id, likeRevision) {
                        commentLikePrefs.getBoolean(likedKey, false)
                    }
                    val baseCommentLikes = remember(c.id) {
                        if (isFounderAccount(c.name) || isFounderAccount(c.githubLogin)) 3 else 0
                    }
                    val displayCommentLikes = baseCommentLikes + (if (isCommentLiked) 1 else 0)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (isReply) 34.dp else 0.dp,
                                top = if (isReply) 6.dp else 11.dp,
                                bottom = 9.dp,
                            ),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Foto Profil Bulat (PP) Komentator
                        FeedAvatarThumb(
                            avatarUrl = c.avatarUrl,
                            name = cAuthor,
                            githubLogin = c.githubLogin,
                            isFounder = cIsFounder,
                            size = if (isReply) 28.dp else 36.dp,
                        )

                        // Kolom Tengah: Nama + Badge + Isi Komentar + Waktu & Balas
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.5.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Text(
                                    text = cAuthor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (cIsFounder) Color(0xFFE9D5FF) else Color(0xFFCBD5E1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (cHandle != null) {
                                    Text(
                                        text = "@$cHandle",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                    )
                                }
                                if (isCreator) {
                                    XyCreatorBadge()
                                }
                                XyUserRoleBadges(
                                    name = c.name,
                                    githubLogin = c.githubLogin,
                                    compact = true,
                                )
                            }

                            if (!c.replyToName.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "▸",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                    )
                                    Text(
                                        text = xy("Membalas @{0}", "Replying to @{0}", c.replyToName),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF38BDF8),
                                    )
                                }
                            }

                            // Isi Teks Komentar
                            XyClickableLinkText(
                                text = c.text,
                                color = Color(0xFFF8FAFC),
                                linkColor = Color(0xFF38BDF8),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp,
                                ),
                            )

                            // Baris Aksi Bawah Komentar ala TikTok: Waktu  ·  Balas  ·  Hapus
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(top = 1.dp),
                            ) {
                                val ago = feedTimeAgo(c.createdAtMillis).ifEmpty { xy("Baru saja", "Just now") }
                                Text(
                                    text = ago,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                )
                                Text(
                                    text = xy("Balas", "Reply"),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            replyingTo = c
                                            val tag = "@${cAuthor.replace(" ", "")} "
                                            if (!draft.startsWith(tag)) {
                                                draft = (tag + draft).take(140)
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                )
                                if (c.isOwn || isViewerFounder) {
                                    Text(
                                        text = if (deletingCommentId == c.id) "…" else xy("Hapus", "Delete"),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable(enabled = deletingCommentId == null) { onDeleteComment(c) }
                                            .padding(vertical = 2.dp),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF87171),
                                    )
                                }
                            }
                        }

                        // Kolom Kanan: Tombol Like Hati Vertikal ala TikTok
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    commentLikePrefs.edit().putBoolean(likedKey, !isCommentLiked).apply()
                                    likeRevision += 1
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = if (isCommentLiked) XyIcons.HeartFilled else XyIcons.Heart,
                                contentDescription = "Suka Komentar",
                                tint = if (isCommentLiked) Color(0xFFFE2C55) else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp),
                            )
                            if (displayCommentLikes > 0) {
                                Text(
                                    text = displayCommentLikes.toString(),
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isCommentLiked) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCommentLiked) Color(0xFFFE2C55) else Color(0xFF64748B),
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.6.dp)
                    .background(Color(0xFF1F2637)),
            )

            // Baris Quick Emoji ala TikTok
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                quickEmojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable {
                                if ((draft + emoji).codePointCount(0, (draft + emoji).length) <= 140) {
                                    draft += emoji
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 19.sp)
                    }
                }
            }

            // Indikator sedang membalas komentar tertentu
            replyingTo?.let { targetReply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1B2232))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = xy("Membalas @{0}", "Replying to @{0}", targetReply.name ?: "Anonim"),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8),
                    )
                    androidx.compose.material3.Icon(
                        imageVector = XyIcons.Close,
                        contentDescription = "Batal Balas",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .clickable { replyingTo = null }
                            .padding(horizontal = 6.dp)
                            .size(14.dp),
                    )
                }
            }

            // Bilah Input Bawah dengan PP Bulat Pengguna + Kapsul Input + Tombol Kirim TikTok
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FeedAvatarThumb(
                    avatarUrl = viewerAvatar,
                    name = viewerDisplayName,
                    githubLogin = viewerLogin,
                    isFounder = isViewerFounder,
                    size = 34.dp,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFF1E2433))
                        .border(0.8.dp, Color(0xFF2D364B), RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.codePointCount(0, it.length) <= 140) draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            color = Color(0xFFF8FAFC),
                            fontSize = 13.sp,
                        ),
                        cursorBrush = SolidColor(Color(0xFFFE2C55)),
                        maxLines = 3,
                        decorationBox = { inner ->
                            if (draft.isEmpty()) {
                                Text(
                                    xy("Tambahkan komentar…", "Add comment…"),
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                )
                            }
                            inner()
                        },
                    )
                }
                val canSendNow = draft.trim().isNotEmpty() && !isSending
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSendNow) Color(0xFFFE2C55) else Color(0xFF262D3F),
                        )
                        .clickable(enabled = canSendNow) {
                            val clean = draft.trim()
                            if (clean.isNotEmpty() && !isSending) {
                                onSend(clean, replyingTo?.id, replyingTo?.name)
                                draft = ""
                                replyingTo = null
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = XyIcons.Send,
                        contentDescription = xy("Kirim", "Send"),
                        tint = if (canSendNow) Color.White else Color(0xFF64748B),
                        modifier = Modifier.size(16.dp),
                    )
                }
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
                    xy("Edit postingan", "Edit post"),
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
                    xy("Hapus postingan", "Delete post"),
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
                xy("Bagikan postingan", "Share post"),
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
                    xy("Laporkan", "Report"),
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
            "XyShield Anti-Virus: File eksekusi/APK/script berbahaya diblokir demi keamanan komunitas!",
            "XyShield Anti-Virus: Executable/APK/script files are blocked for community safety!",
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

private val feedDecodedBitmapCache = android.util.LruCache<String, androidx.compose.ui.graphics.ImageBitmap>(48)

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
    var decodedBmp by remember(path) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(feedDecodedBitmapCache.get(path))
    }
    LaunchedEffect(path, mime) {
        val raw = bytes ?: withContext(Dispatchers.IO) {
            runCatching { CommunityJokesApi(context).fetchBinary(path) }.getOrNull()
                ?.also { feedImageCache.put(path, it) }
        }
        if (raw != null && bytes == null) {
            bytes = raw
        }
        if (raw != null && decodedBmp == null && mime?.startsWith("video/") != true && mime != "image/gif") {
            decodedBmp = withContext(Dispatchers.IO) {
                runCatching {
                    android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)?.asImageBitmap()
                }.getOrNull()?.also { feedDecodedBitmapCache.put(path, it) }
            }
        }
    }
    val data = bytes
    val shape = if (round) CircleShape else RoundedCornerShape(12.dp)

    if (mime?.startsWith("video/") == true && data != null && data.isNotEmpty()) {
        val vidRatio by androidx.compose.runtime.produceState(9f / 16f, path, data.size) {
            value = withContext(Dispatchers.IO) { extractCachedVideoAspectRatio(context, path, data) }
        }
        FeedAutoplayVideoPreview(
            videoBytes = data,
            cacheKey = path,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(vidRatio)
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
                if (view.tag != path) {
                    view.tag = path
                    runCatching {
                        val source = android.graphics.ImageDecoder.createSource(
                            java.nio.ByteBuffer.wrap(data),
                        )
                        val drawable = android.graphics.ImageDecoder.decodeDrawable(source)
                        view.setImageDrawable(drawable)
                        (drawable as? android.graphics.drawable.AnimatedImageDrawable)?.start()
                    }
                }
            },
            modifier = modifier.clip(shape),
        )
    } else {
        decodedBmp?.let {
            val ratio = if (!round && !fit && it.height > 0) {
                (it.width.toFloat() / it.height.toFloat()).coerceIn(0.8f, 1.91f)
            } else {
                null
            }
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = modifier
                    .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier)
                    .clip(shape),
                contentScale = when {
                    round -> ContentScale.Crop
                    fit -> ContentScale.Fit
                    else -> ContentScale.Crop
                },
            )
        }
    }
}

internal object XyVideoPlaybackCoordinator {
    val fullscreenOpenCount = androidx.compose.runtime.mutableIntStateOf(0)
}

internal fun extractCachedVideoAspectRatio(
    context: android.content.Context,
    cacheKey: String,
    videoBytes: ByteArray,
): Float = runCatching {
    extractVideoAspectRatio(prepareCachedVideoFile(context.cacheDir, videoBytes))
}.getOrDefault(9f / 16f)

internal fun extractVideoAspectRatio(file: java.io.File): Float = runCatching {
    val retriever = android.media.MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rot = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val effW = if (rot == 90 || rot == 270) h else w
        val effH = if (rot == 90 || rot == 270) w else h
        if (effW > 0 && effH > 0) {
            (effW.toFloat() / effH.toFloat()).coerceIn(0.5f, 2.2f)
        } else {
            9f / 16f
        }
    } finally {
        runCatching { retriever.release() }
    }
}.getOrDefault(9f / 16f)

/**
 * Video Autoplay Preview (berputar otomatis sesuai rasio asli 9:16 atau 16:9 tanpa bar hitam,
 * dengan tombol Speaker Mute/Unmute di pojok dan otomatis berhenti/mute saat Fullscreen dibuka).
 */
@Composable
internal fun FeedAutoplayVideoPreview(
    videoBytes: ByteArray,
    cacheKey: String,
    modifier: Modifier = Modifier,
    externalPaused: Boolean = false,
) {
    val context = LocalContext.current
    var muted by remember(cacheKey) { mutableStateOf(true) }
    var mediaPlayerRef by remember(cacheKey) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var videoViewRef by remember(cacheKey) { mutableStateOf<android.widget.VideoView?>(null) }
    val fullscreenActive = XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue > 0 || externalPaused

    val tempFile = androidx.compose.runtime.produceState<java.io.File?>(null, cacheKey, videoBytes.size) {
        value = withContext(Dispatchers.IO) {
            runCatching { prepareCachedVideoFile(context.cacheDir, videoBytes) }.getOrNull()
        }
    }.value

    LaunchedEffect(fullscreenActive, muted) {
        val mp = mediaPlayerRef
        val vv = videoViewRef
        if (fullscreenActive) {
            runCatching { mp?.setVolume(0f, 0f) }
            runCatching { if (vv?.isPlaying == true) vv.pause() }
        } else {
            val vol = if (muted) 0f else 1f
            runCatching { mp?.setVolume(vol, vol) }
            runCatching { if (vv != null && !vv.isPlaying) vv.start() }
        }
    }

    DisposableEffect(cacheKey) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
            runCatching { mediaPlayerRef?.stop() }
            runCatching { mediaPlayerRef?.release() }
            mediaPlayerRef = null
            videoViewRef = null
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (tempFile != null) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        videoViewRef = this
                        setVideoPath(tempFile.absolutePath)
                        setOnPreparedListener { mp ->
                            mediaPlayerRef = mp
                            mp.isLooping = true
                            val vol = if (muted || fullscreenActive) 0f else 1f
                            mp.setVolume(vol, vol)
                            if (!fullscreenActive) {
                                start()
                            }
                        }
                    }
                },
                update = { view ->
                    videoViewRef = view
                    if (fullscreenActive) {
                        runCatching { mediaPlayerRef?.setVolume(0f, 0f) }
                        if (view.isPlaying) {
                            runCatching { view.pause() }
                        }
                    } else {
                        val vol = if (muted) 0f else 1f
                        runCatching { mediaPlayerRef?.setVolume(vol, vol) }
                        if (!view.isPlaying) {
                            runCatching { view.start() }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Surface(
            onClick = { muted = !muted },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.68f),
            border = androidx.compose.foundation.BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .size(30.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    imageVector = if (muted) {
                        id.xydesk.remote.ui.components.XyIcons.VolumeOff
                    } else {
                        id.xydesk.remote.ui.components.XyIcons.VolumeOn
                    },
                    contentDescription = if (muted) "Suara Mati" else "Suara Aktif",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedImageViewer(url: String, mime: String?, onDismiss: () -> Unit) {
    DisposableEffect(Unit) {
        XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue += 1
        onDispose {
            XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue =
                (XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue - 1).coerceAtLeast(0)
        }
    }
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
