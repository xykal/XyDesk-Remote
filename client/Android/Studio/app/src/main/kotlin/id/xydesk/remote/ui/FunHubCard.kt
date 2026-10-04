package id.xydesk.remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import kotlinx.coroutines.launch

private const val COMMUNITY_JOKE_MAX_CHARS = 280
private const val INITIAL_JOKES_VISIBLE = 3

/** Flat home feed for anonymous, shared community jokes. */
@Composable
internal fun FunHubCard(onHide: () -> Unit) {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }
    val scope = rememberCoroutineScope()

    var jokes by remember { mutableStateOf<List<CommunityJoke>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var submitting by remember { mutableStateOf(false) }
    var activeReactionId by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<CommunityJoke?>(null) }
    var reportedIds by remember { mutableStateOf(emptySet<String>()) }
    var showAll by remember { mutableStateOf(false) }
    var composerOpen by remember { mutableStateOf(false) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var formMessage by remember { mutableStateOf<String?>(null) }
    var formMessageIsError by remember { mutableStateOf(false) }
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
        jokes = jokes.map { if (it.id == updated.id) updated else it }
    }

    LaunchedEffect(api, refreshKey) {
        refreshing = true
        feedError = null
        try {
            jokes = api.latest()
            showAll = false
        } catch (error: Exception) {
            feedError = showCommunityError(error, loadingFeed = true)
        } finally {
            refreshing = false
        }
    }

    val trimmedDraft = draft.trim()
    val characterCount = draft.codePointCount(0, draft.length)
    val trimmedCharacterCount = trimmedDraft.codePointCount(0, trimmedDraft.length)
    val containsLink = COMMUNITY_LINK_PATTERN.containsMatchIn(draft)
    val canSubmit = trimmedCharacterCount >= 3 && !containsLink && !submitting

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
                Text(xy("Jokes komunitas", "Community jokes"), style = MaterialTheme.typography.titleSmall)
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
                Text(
                    xy("Sembunyikan", "Hide"),
                    modifier = Modifier.clickable(onClick = onHide).padding(start = 8.dp, top = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                xy("Memuat jokes komunitas…", "Loading community jokes…"),
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
                val visibleJokes = if (showAll) jokes else jokes.take(INITIAL_JOKES_VISIBLE)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleJokes.forEach { joke ->
                        CommunityJokeItem(
                            joke = joke,
                            isReactionBusy = activeReactionId == joke.id,
                            alreadyReported = joke.id in reportedIds,
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
                            onReport = { reportTarget = joke },
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

        if (composerOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(xy("Kirim jokes", "Post a joke"), style = MaterialTheme.typography.labelLarge)
                    Text(
                        xy("Batal", "Cancel"),
                        modifier = Modifier.clickable(enabled = !submitting) { composerOpen = false }.padding(4.dp),
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
                                        val posted = api.submit(trimmedDraft)
                                        jokes = (listOf(posted) + jokes.filterNot { it.id == posted.id }).take(20)
                                        draft = ""
                                        showAll = false
                                        composerOpen = false
                                        formMessage = xyNow("Terkirim dan tampil di feed komunitas.", "Posted to the community feed.")
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
                text = xy("Tulis jokes", "Write a joke"),
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
    isReactionBusy: Boolean,
    alreadyReported: Boolean,
    onReact: (String) -> Unit,
    onReport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(joke.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunityJokesApi.REACTIONS.forEach { emoji ->
                val selected = joke.viewerReaction == emoji
                Row(
                    modifier = Modifier
                        .clickable(enabled = !isReactionBusy) { onReact(emoji) }
                        .padding(horizontal = 3.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$emoji ${joke.reactions[emoji] ?: 0}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                xy("Anonim", "Anonymous"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (alreadyReported) xy("Dilaporkan", "Reported") else xy("Laporkan", "Report"),
                modifier = if (alreadyReported) Modifier else Modifier
                    .clickable(onClick = onReport)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val COMMUNITY_LINK_PATTERN = Regex("(?:https?://|www\\.)", RegexOption.IGNORE_CASE)
