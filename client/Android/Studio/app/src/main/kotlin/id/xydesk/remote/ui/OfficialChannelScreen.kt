package id.xydesk.remote.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val LocalChannelNavigator = staticCompositionLocalOf<(String) -> Unit> { {} }

/** One host per theme: tags work in feed, chat and floating chat without restarting sessions. */
@Composable
internal fun OfficialChannelNavigation(content: @Composable () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    CompositionLocalProvider(LocalChannelNavigator provides { id -> if (officialChannels.any { it.id == id }) open = id }) {
        content()
        open?.let { id ->
            Dialog(onDismissRequest = { open = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                Surface(Modifier.fillMaxSize()) {
                    OfficialChannelScreen(id, onClose = { open = null }, onSelect = { open = it })
                }
            }
        }
    }
}

@Composable
internal fun OfficialChannelDirectory(modifier: Modifier = Modifier) {
    val navigate = LocalChannelNavigator.current
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        officialChannels.forEach { channel ->
            AssistChip(onClick = { navigate(channel.id) }, label = { Text("#${channel.tag}", maxLines = 1) })
        }
    }
}

@Composable
private fun OfficialChannelScreen(id: String, onClose: () -> Unit, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val account = rememberVerifiedChatAccount(context)
    val channel = officialChannels.first { it.id == id }
    val scope = rememberCoroutineScope()
    val posts = remember(id, account) { mutableStateListOf<OfficialChannelPost>() }
    var canPost by remember(id, account) { mutableStateOf(false) }
    var loading by remember(id, account) { mutableStateOf(false) }
    var loaded by remember(id, account) { mutableStateOf(false) }
    var error by remember(id, account) { mutableStateOf<String?>(null) }
    var cursor by remember(id, account) { mutableStateOf<String?>(null) }
    var retryUntil by remember(id, account) { mutableStateOf(0L) }
    var draft by rememberSaveable(id, account) { mutableStateOf("") }
    var nonce by rememberSaveable(id, account) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var sending by remember(id, account) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    BackHandler { onClose() }
    fun load(older: Boolean = false) {
        if (loading || System.currentTimeMillis() < retryUntil) return
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { OfficialChannelRepository.load(context, id, if (older) cursor else null) } }
            if (!chatAccountSnapshotIsCurrent(account, getVerifiedGitHubLogin(context))) return@launch
            result.onSuccess { page ->
                val merged = (posts + page.posts).associateBy { it.id }.values.sortedBy { it.at }
                posts.clear(); posts.addAll(merged)
                cursor = page.cursor
                canPost = page.canPost; loaded = true; error = null; retryUntil = 0L
            }.onFailure { e ->
                error = e.message ?: "Channel belum tersinkron. Coba lagi."
                retryUntil = System.currentTimeMillis() + ((e as? ChannelRequestException)?.retrySeconds ?: 0L) * 1000L
            }
            loading = false
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(id, account) {
        load()
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) { delay(60_000L); load() }
        }
    }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(channel.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Tutup") }
        }
        Text("#${channel.tag} · Channel resmi", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            officialChannels.forEach { c -> FilterChip(selected = id == c.id, onClick = { onSelect(c.id) }, label = { Text(c.title) }) }
        }
        Text("Semua pengguna bisa membaca. Kirim pesan khusus admin, founder, dan tim berwenang.", style = MaterialTheme.typography.bodySmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { load() }, enabled = !loading) { Text("Muat ulang") }
            if (cursor != null) TextButton(onClick = { load(true) }, enabled = !loading) { Text("Pesan lebih lama") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (id == "panduan-pengguna") item(key = "bundled-guide") {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Panduan bawaan · tersedia offline", style = MaterialTheme.typography.labelLarge)
                        XyClickableLinkText(text = bundledUserGuide, color = MaterialTheme.colorScheme.onSurface)
                        Text("Dokumentasi aplikasi, bukan pesan pengguna. Pembaruan tim ditampilkan di bawah saat tersinkron.", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (loaded && posts.isEmpty() && error == null) item { Text("Belum ada pesan resmi di channel ini.") }
            items(posts, key = { it.id }) { post ->
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${post.login} · ${post.role}", style = MaterialTheme.typography.labelMedium)
                        XyClickableLinkText(text = post.text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                        Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(post.at)), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (canPost) {
            OutlinedTextField(value = draft, onValueChange = { if (it.length <= 4000) { draft = it; nonce = java.util.UUID.randomUUID().toString() } }, enabled = !sending,
                label = { Text("Pesan resmi") }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
            Button(enabled = draft.isNotBlank() && !sending && !loading, onClick = {
                if (System.currentTimeMillis() < retryUntil) return@Button
                sending = true
                val text = draft; val operation = nonce
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { OfficialChannelRepository.publish(context, id, text, operation, account.orEmpty()) } }
                    if (!chatAccountSnapshotIsCurrent(account, getVerifiedGitHubLogin(context))) return@launch
                    result.onSuccess { post ->
                        if (posts.none { it.id == post.id }) posts.add(post)
                        posts.sortBy { it.at }; draft = ""; nonce = java.util.UUID.randomUUID().toString(); error = null
                        if (posts.isNotEmpty()) listState.animateScrollToItem(posts.lastIndex + if (id == "panduan-pengguna") 1 else 0)
                    }.onFailure { e ->
                        error = e.message ?: "Kirim belum terkonfirmasi. Draf tetap tersedia; coba lagi."
                        retryUntil = System.currentTimeMillis() + ((e as? ChannelRequestException)?.retrySeconds ?: 0L) * 1000L
                        if ((e as? ChannelRequestException)?.status == 403) canPost = false
                    }
                    sending = false
                }
            }) { Text(if (sending) "Mengirim…" else "Kirim pesan resmi") }
        } else if (loaded) Text("Mode baca saja", style = MaterialTheme.typography.labelSmall)
    }
}
