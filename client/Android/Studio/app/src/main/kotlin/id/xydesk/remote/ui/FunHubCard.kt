package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import kotlinx.coroutines.launch

private const val COMMUNITY_JOKE_MAX_CHARS = 280
private const val INITIAL_JOKES_VISIBLE = 3

/** Flat home feed for anonymous, shared community jokes. */
@Composable
internal fun FunHubCard(
    onHide: (() -> Unit)? = null,
    refreshSignal: Int = 0,
    onBusyChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }
    val scope = rememberCoroutineScope()

    // Cache-first (permintaan pemilik: buka feed langsung ada isinya, tidak
    // ada layar "Memuat…"): cache lokal dirender dulu, jaringan menyegar di
    // belakang layar.
    var jokes by remember(api) { mutableStateOf(api.cachedFeed()) }
    var draft by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    // Lampiran yang dibuka fullscreen (url ke mime) — pemutar zoom sederhana.
    var fullscreenImage by remember { mutableStateOf<Pair<String, String?>?>(null) }
    // "Profil bisa di-view": ketuk nama penulis = saring feed ke postingannya.
    var authorFilter by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var activeReactionId by remember { mutableStateOf<String?>(null) }
    var commentingId by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var reportedIds by remember { mutableStateOf(emptySet<String>()) }
    var showAll by remember { mutableStateOf(false) }
    var composerOpen by remember { mutableStateOf(false) }
    var formMessage by remember { mutableStateOf<String?>(null) }
    var formMessageIsError by remember { mutableStateOf(false) }
    var pendingAttachment by remember { mutableStateOf<FeedAttachment?>(null) }
    var editingJoke by remember { mutableStateOf<CommunityJoke?>(null) }
    var editRemoveAttachment by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var deleting by remember { mutableStateOf(false) }
    // Bottom sheet komentar ala TikTok + sheet aksi postingan (permintaan
    // pemilik 2026-10-15: list komentar & hapus/komentar lewat bottom sheet).
    var commentsTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var actionsTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var deletingCommentId by remember { mutableStateOf<String?>(null) }
    val pickVisual = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val att = encodePickedFeedImage(context, uri)
                if (att == null) {
                    formMessage = xyNow(
                        "Gambar tidak didukung atau melebihi 1,5MB.",
                        "Image unsupported or over 1.5MB.",
                    )
                    formMessageIsError = true
                } else {
                    pendingAttachment = att
                    editRemoveAttachment = false
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
            reason == "invalid_text" -> xyNow("Teks harus 3–280 karakter dan tidak boleh berisi tautan.", "Text must be 3–280 characters and cannot contain links.")
            reason == "invalid_client_id" -> xyNow("Identitas anonim tidak valid. Coba muat ulang aplikasi.", "The anonymous ID is invalid. Try reopening the app.")
            !loadingFeed && reason == "not_found" -> xyNow("Postingan sudah tidak tersedia.", "This post is no longer available.")
            reason == "network" -> xyNow("Jaringan gagal menjangkau server. Periksa internet lalu coba lagi.", "Couldn't reach the server. Check your connection and retry.")
            loadingFeed -> xyNow("Feed gagal dimuat. Periksa jaringan lalu coba lagi.", "Couldn't load the feed. Check your connection and retry.")
            else -> xyNow("Aksi gagal. Coba lagi.", "That action failed. Please retry.")
        }
    }

    fun updateJoke(updated: CommunityJoke) {
        jokes = jokes.map { old ->
            if (old.id != updated.id) return@map old
            // Respons reaksi tidak memuat komentar; jangan sampai hilang.
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
            showAll = false
        } catch (error: Exception) {
            if (jokes.isEmpty()) feedError = showCommunityError(error, loadingFeed = true)
        } finally {
            refreshing = false
        }
    }

    val trimmedDraft = draft.trim()
    val characterCount = draft.codePointCount(0, draft.length)
    val trimmedCharacterCount = trimmedDraft.codePointCount(0, trimmedDraft.length)
    val containsLink = COMMUNITY_LINK_PATTERN.containsMatchIn(draft)
    val hasAttachment = pendingAttachment != null
    val canSubmit = !containsLink && !submitting && (trimmedCharacterCount >= 3 || hasAttachment)

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(xy("Feed komunitas", "Community feed"), style = MaterialTheme.typography.titleSmall)
                Text(
                    xy("Anonim · dibagikan ke semua pengguna", "Anonymous · shared with everyone"),
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
                // Hanya di kartu Beranda. Di layar Feed (tujuan nav sendiri)
                // onHide bernilai null karena feed bukan kartu opsional.
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

        feedMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (feedMessageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            modifier = Modifier.clickable {
                                authorFilter = null
                            }.padding(horizontal = 6.dp, vertical = 2.dp),
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleJokes.forEach { joke ->
                        CommunityJokeItem(
                            joke = joke,
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

        if (composerOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (editingJoke != null) xy("Edit postingan", "Edit post") else xy("Kirim postingan", "Post to feed"),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        xy("Batal", "Cancel"),
                        modifier = Modifier.clickable(enabled = !submitting) {
                            composerOpen = false
                            editingJoke = null
                            pendingAttachment = null
                            editRemoveAttachment = false
                        }.padding(4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    placeholder = xy("Tulis singkat. Tanpa nama atau tautan.", "Keep it short. No names or links."),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    XyPillButton(
                        text = xy(" Foto / stiker / GIF", "📷 Photo / sticker / GIF"),
                        onClick = {
                            pickVisual.launch("image/*")
                        },
                        primary = false,
                        compact = true,
                    )
                    pendingAttachment?.let { att ->
                        val bmp = remember(att) {
                            android.graphics.BitmapFactory.decodeByteArray(att.bytes, 0, att.bytes.size)
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(
                            xy("Hapus", "Remove"),
                            modifier = Modifier.clickable {
                                pendingAttachment = null
                                if (editingJoke != null) editRemoveAttachment = true
                            }.padding(4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val helper = when {
                        containsLink -> xy("Tautan tidak diterima", "Links aren't allowed")
                        else -> xy("{0}/280 karakter", "{0}/280 characters", characterCount)
                    }
                    Text(
                        helper,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (containsLink) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    XyPillButton(
                        text = if (submitting) xy("Mengirim…", "Posting…") else xy("Kirim anonim", "Post anonymously"),
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
                                            api.submit(trimmedDraft, pendingAttachment)
                                        }
                                        jokes = (listOf(posted) + jokes.filterNot { it.id == posted.id }).take(20)
                                        api.cacheFeed(jokes)
                                        draft = ""
                                        pendingAttachment = null
                                        editingJoke = null
                                        editRemoveAttachment = false
                                        showAll = false
                                        composerOpen = false
                                        formMessage = xyNow(
                                            if (target != null) "Postingan diperbarui." else "Terkirim dan tampil di feed komunitas.",
                                            if (target != null) "Post updated." else "Posted to the community feed.",
                                        )
                                        formMessageIsError = false
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
                Text(
                    xy("Tanpa akun. Spam atau konten bermasalah dapat disembunyikan.", "No account needed. Spam or abusive posts may be hidden."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            XyPillButton(
                text = xy("Tulis postingan", "Write a post"),
                onClick = { composerOpen = true },
                primary = false,
                compact = true,
            )
        }
        formMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (formMessageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }

    // Bottom sheet komentar ala TikTok: list + input menempel di bawah,
    // dibuka ulang tiap mau berkomentar (permintaan pemilik 2026-10-15).
    commentsTarget?.let { target ->
        val current = jokes.firstOrNull { it.id == target.id } ?: target
        CommentsBottomSheet(
            joke = current,
            isSending = commentingId == current.id,
            deletingCommentId = deletingCommentId,
            onSend = { text ->
                if (commentingId == null) {
                    scope.launch {
                        commentingId = current.id
                        feedMessage = null
                        try {
                            val posted = api.comment(current.id, text)
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

    // Bottom sheet aksi postingan: edit / hapus / laporkan dari satu titik "···".
    actionsTarget?.let { target ->
        ActionsBottomSheet(
            joke = target,
            alreadyReported = target.id in reportedIds,
            onEdit = {
                actionsTarget = null
                editingJoke = target
            },
            onDelete = {
                actionsTarget = null
                deleteTarget = target
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
                    xy("Postingan dan lampirannya dihapus permanen dari feed komunitas.",
                       "The post and its attachment are permanently removed from the community feed."),
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
                                    feedMessage = xyNow("Postingan dihapus.", "Post deleted.")
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
                xy("Laporan anonim membantu menjaga feed. Postingan yang mendapat cukup laporan akan disembunyikan otomatis.", "Anonymous reports help keep the feed useful. Posts with enough reports are hidden automatically."),
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
                                    feedMessage = xyNow("Laporan anonim diterima.", "Anonymous report received.")
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

@Composable
private fun CommunityJokeInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(vertical = 8.dp),
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp, max = 116.dp),
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
            maxLines = 4,
        )
    }
}

@Composable
private fun CommunityJokeItem(
    joke: CommunityJoke,
    onImageClick: (String, String?) -> Unit,
    onAuthorClick: (String) -> Unit,
    isReactionBusy: Boolean,
    onReact: (String) -> Unit,
    onOpenComments: () -> Unit,
    onOpenActions: () -> Unit,
) {
    var pickerOpen by remember(joke.id) { mutableStateOf(false) }

    @Composable
    fun ReactionChip(emoji: String) {
        val selected = joke.viewerReaction == emoji
        val count = joke.reactions[emoji] ?: 0
        Row(
            modifier = Modifier
                .clickable(enabled = !isReactionBusy) { onReact(emoji) }
                .padding(horizontal = 3.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$emoji $count",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (joke.text.isNotBlank()) {
            Text(joke.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        joke.attachmentUrl?.let { url ->
            FeedBinaryImage(
                path = url,
                mime = joke.attachmentMime,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .clickable { onImageClick(url, joke.attachmentMime) },
            )
        }

        // Reaksi cepat + tombol "+" untuk 24 emoji (grid 8x3, tanpa scroll).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunityJokesApi.QUICK_REACTIONS.forEach { emoji -> ReactionChip(emoji) }
            Text(
                if (pickerOpen) "−" else "+",
                modifier = Modifier
                    .clickable(enabled = !isReactionBusy) { pickerOpen = !pickerOpen }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (pickerOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CommunityJokesApi.REACTIONS.chunked(8).forEach { rowEmojis ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowEmojis.forEach { emoji ->
                            Text(
                                emoji,
                                modifier = Modifier
                                    .clickable(enabled = !isReactionBusy) {
                                        onReact(emoji)
                                        pickerOpen = false
                                    }
                                    .padding(vertical = 4.dp),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            joke.authorAvatarUrl?.let { avatar ->
                FeedBinaryImage(path = avatar, modifier = Modifier.size(20.dp), round = true)
                Spacer(Modifier.width(6.dp))
            }
            Text(
                joke.authorName ?: xy("Anonim", "Anonymous"),
                modifier = Modifier
                    .clickable { onAuthorClick(joke.authorName ?: xyNow("Anonim", "Anonymous")) }
                    .padding(vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            feedTimeAgo(joke.createdAtMillis).takeIf { it.isNotEmpty() }?.let { ago ->
                Text(
                    " · $ago",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (joke.editedAtMillis > 0) {
                Text(
                    xy(" · diedit", " · edited"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                xy("Komentar ({0})", "Comments ({0})", joke.commentsTotal),
                modifier = Modifier
                    .clickable(onClick = onOpenComments)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // Semua aksi (edit/hapus/laporkan) kini di bottom sheet — kartu
            // tetap bersih, cukup satu titik "···".
            Spacer(Modifier.weight(1f))
            Text(
                "···",
                modifier = Modifier
                    .clickable(onClick = onOpenActions)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Bottom sheet komentar ala TikTok: daftar komentar + kolom tulis menempel
 * di bawah. Draft direset per postingan. Komentar milik sendiri bisa dihapus
 * langsung dari list (butuh worker r37 yang menyimpan author_hash).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CommentsBottomSheet(
    joke: CommunityJoke,
    isSending: Boolean,
    deletingCommentId: String?,
    onSend: (String) -> Unit,
    onDeleteComment: (JokeComment) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(joke.id) { mutableStateOf("") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                xy("{0} komentar", "{0} comments", joke.commentsTotal),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (joke.comments.isEmpty()) {
                    item {
                        Text(
                            xy("Belum ada komentar. Jadilah yang pertama.", "No comments yet. Be the first."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(joke.comments, key = { it.id }) { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                c.name ?: xy("Anonim", "Anonymous"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            feedTimeAgo(c.createdAtMillis).takeIf { it.isNotEmpty() }?.let { ago ->
                                Text(
                                    " · $ago",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (c.isOwn) {
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
                        Text(c.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.codePointCount(0, it.length) <= 140) draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodySmall.fontSize,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 3,
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                xy("Tulis komentar…", "Write a comment…"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
                XyPillButton(
                    text = if (isSending) "…" else xy("Kirim", "Send"),
                    onClick = {
                        val clean = draft.trim()
                        if (clean.isNotEmpty() && !isSending) {
                            onSend(clean)
                            draft = ""
                        }
                    },
                    enabled = draft.trim().isNotEmpty() && !isSending,
                    compact = true,
                )
            }
        }
    }
}

/** Bottom sheet aksi postingan: edit/hapus (milik sendiri) + laporkan. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ActionsBottomSheet(
    joke: CommunityJoke,
    alreadyReported: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (joke.isOwn) {
                Text(
                    xy("Edit postingan", "Edit post"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onEdit)
                        .padding(horizontal = 12.dp, vertical = 13.dp),
                    style = MaterialTheme.typography.bodyMedium,
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
                    color = MaterialTheme.colorScheme.error,
                )
            }
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

private val COMMUNITY_LINK_PATTERN = Regex("(?:https?://|www\\.)", RegexOption.IGNORE_CASE)

/**
 * Kompres gambar pilihan jadi stiker webp/gif ≤1,5MB untuk lampiran feed.
 * Batas lama 500KB menolak kebanyakan foto/GIF HP mentah-mentah sehingga
 * fitur terasa "belum bisa" (laporan pemilik 2026-10-13). GIF kini lolos apa
 * adanya sampai 1,5MB; gambar lain dikompres bertahap (kualitas turun dulu,
 * baru dimensi) sampai muat.
 */
private fun encodePickedFeedImage(context: android.content.Context, uri: android.net.Uri): FeedAttachment? {
    val limit = 1_500_000
    return runCatching {
        val resolver = context.contentResolver
        val type = resolver.getType(uri).orEmpty()
        val raw = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (type == "image/gif" && raw.size <= limit) return FeedAttachment("image/gif", raw)
        val src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        val maxSide = 1080
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
        var quality = 85
        var guard = 0
        while (guard++ < 8) {
            val stream = java.io.ByteArrayOutputStream()
            out.compress(fmt, quality, stream)
            bytes = stream.toByteArray()
            if (bytes.size <= limit) break
            if (quality > 45) {
                quality -= 20
            } else {
                out = android.graphics.Bitmap.createScaledBitmap(
                    out,
                    (out.width * 0.7f).toInt().coerceAtLeast(1),
                    (out.height * 0.7f).toInt().coerceAtLeast(1),
                    true,
                )
            }
        }
        if (bytes.isEmpty() || bytes.size > limit) null else FeedAttachment("image/webp", bytes)
    }.getOrNull()
}

/**
 * Cache byte lampiran/avatar feed di memori (maks 4MB) — tanpa ini setiap
 * scroll balik mengunduh ulang gambar yang sama (boros kuota & lambat).
 * Dikosongkan tiap feed dimuat ulang agar lampiran yang diedit ikut segar.
 */
private val feedImageCache = object : android.util.LruCache<String, ByteArray>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size
}

/** Gambar biner feed (lampiran postingan / avatar) — dimuat async dari worker. */
@Composable
private fun FeedBinaryImage(
    path: String,
    modifier: Modifier = Modifier,
    round: Boolean = false,
    mime: String? = null,
    fit: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bytes by remember(path) { mutableStateOf<ByteArray?>(null) }
    androidx.compose.runtime.LaunchedEffect(path) {
        bytes = feedImageCache.get(path)
            ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { CommunityJokesApi(context).fetchBinary(path) }.getOrNull()
                    ?.also { feedImageCache.put(path, it) }
            }
    }
    val data = bytes
    val shape = if (round) CircleShape else RoundedCornerShape(12.dp)
    // GIF dianimasikan lewat AnimatedImageDrawable (API 28+); di bawah itu,
    // dan untuk webp/foto, tetap bitmap statis seperti sebelumnya.
    if (mime == "image/gif" &&
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
 * Penampil lampiran feed fullscreen: cubit untuk zoom (1x–5x), ketuk dua kali
 * untuk zoom/reset, ketuk sekali untuk menutup. Dialog dengan
 * `usePlatformDefaultWidth = false` membuatnya benar-benar seluas layar,
 * bukan sekadar overlay di dalam kartu feed.
 */
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
            .background(androidx.compose.ui.graphics.Color.Black)
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
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.72f),
        )
    }
}

/**
 * Label waktu relatif postingan feed: baru saja / X mnt / X jam / kemarin /
 * tanggal pendek. Tanpa ini feed terasa mati — tidak ada rasa kronologi
 * (laporan pemilik 2026-10-12: "feed masih kureng").
 */
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
