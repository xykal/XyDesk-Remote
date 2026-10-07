package id.xydesk.remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton

/**
 * Tab Musik di panel sesi: sumber, kontrol penuh, antrian, dan pustaka.
 *
 * Pemutar mengambang hanya punya ruang untuk satu lagu, jadi daftar (antrian
 * dan pustaka) hidup di sini. Semua tetap memakai pola panel yang sama — grid
 * kotak yang isinya MELEBAR di bawah kotaknya, bukan halaman baru dengan
 * tombol kembali.
 */
@Composable
internal fun MusicTab(
    openKey: String?,
    onOpenKey: (String?) -> Unit,
) {
    val context = LocalContext.current
    val playback by SpotifyMediaBridge.playback.collectAsState()
    val positionMs by SpotifyMediaBridge.positionMs.collectAsState()
    val library by MediaLibraryBrowser.state.collectAsState()

    // Pustaka dibaca ulang setiap tab dibuka: aplikasi musik bisa dipasang
    // atau ditutup kapan saja, dan daftar yang basi hanya membingungkan.
    LaunchedEffect(Unit) { MediaLibraryBrowser.discover(context) }
    DisposableEffect(Unit) {
        onDispose { MediaLibraryBrowser.disconnect() }
    }

    PanelSectionGrid(openKey = openKey, onOpenKey = onOpenKey) {
        section("sumber-musik", xy("Sumber & izin", "Source & access")) {
            PanelHint(
                xy(
                    "XyDesk mengendalikan sesi media Android, jadi pemutar apa pun bisa dipakai — Spotify, SoundCloud, YouTube Music, atau pemutar lokal. Audio dan login tetap di aplikasi itu.",
                    "XyDesk drives Android's media session, so any player works — Spotify, SoundCloud, YouTube Music, or a local player. Audio and sign-in stay inside that app.",
                ),
            )
            if (!playback.notificationAccessGranted) {
                XyPillButton(
                    text = xy("Beri akses notifikasi", "Allow notification access"),
                    onClick = { openSpotifyNotificationAccess(context) },
                    icon = XyIcons.Music,
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    xy(
                        "Akses notifikasi aktif. {0} sesi terdeteksi.",
                        "{0} session(s) detected.",
                        playback.sources.size,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }

            if (playback.sources.isEmpty() && playback.notificationAccessGranted) {
                Text(
                    xy(
                        "Belum ada aplikasi musik yang membuka sesi. Putar satu lagu dulu, lalu ketuk Segarkan.",
                        "No music app has opened a session yet. Play a song first, then tap Refresh.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            playback.sources.forEach { source ->
                SourceRow(
                    source = source,
                    onSelect = {
                        val ok = SpotifyMediaBridge.selectSource(context, source.packageName)
                        if (!ok) {
                            SpotifyMediaBridge.refresh(context)
                        }
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XyPillButton(
                    xy("Segarkan", "Refresh"),
                    { SpotifyMediaBridge.refresh(context) },
                    icon = XyIcons.Refresh,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                if (playback.sources.any { it.active }) {
                    XyPillButton(
                        xy("Pilih otomatis", "Auto pick"),
                        { SpotifyMediaBridge.clearSourcePreference(context) },
                        primary = false,
                        compact = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        section("kontrol-pemutar", xy("Kontrol penuh", "Full control")) {
            if (!playback.hasActiveSession) {
                PanelHint(
                    xy(
                        "Belum ada lagu aktif. Kontrol muncul begitu ada pemutar yang membuka sesi.",
                        "No active track yet. The controls appear as soon as a player opens a session.",
                    ),
                )
            } else {
                Text(
                    playback.title ?: xy("(tanpa judul)", "(untitled)"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(playback.artist, playback.album).joinToString(" · ")
                        .ifEmpty { playback.sourceLabel.orEmpty() },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MusicTransportControls(
                    playback = playback,
                    positionMs = positionMs,
                    onTogglePlayback = { SpotifyMediaBridge.playPause() },
                    onPrevious = { SpotifyMediaBridge.previous() },
                    onNext = { SpotifyMediaBridge.next() },
                    onSeek = { SpotifyMediaBridge.seekTo(it) },
                    onSeekBy = { SpotifyMediaBridge.seekBy(it) },
                    onStop = { SpotifyMediaBridge.stop() },
                    onToggleShuffle = { SpotifyMediaBridge.setShuffle(it) },
                    onCycleRepeat = { SpotifyMediaBridge.cycleRepeat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                val unsupported = buildList {
                    if (!playback.canSeek) add(xy("geser posisi", "seeking"))
                    if (!playback.canSkipNext || !playback.canSkipPrevious) add(xy("ganti lagu", "skipping tracks"))
                    if (!playback.canShuffle) add(xy("acak", "shuffle"))
                    if (!playback.canRepeat) add(xy("ulang", "repeat"))
                    if (!playback.canStop) add(xy("hentikan", "stop"))
                }
                if (unsupported.isNotEmpty()) {
                    Text(
                        xy(
                            "Pemutar ini tidak membuka: {0}. Tombolnya dimatikan, bukan disembunyikan.",
                            "This player does not expose: {0}. Those buttons are disabled rather than hidden.",
                            unsupported.joinToString(", "),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        }

        section("antrian-musik", xy("Antrian / playlist", "Queue / playlist")) {
            PanelHint(
                xy(
                    "Antrian adalah daftar yang sudah dimuat pemutar — playlist, album, atau radio yang sedang jalan. Ketuk satu lagu untuk langsung memutarnya.",
                    "The queue is what the player already loaded — the running playlist, album, or radio. Tap a track to play it now.",
                ),
            )
            playback.queueTitle?.let { title ->
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (playback.queue.isEmpty()) {
                Text(
                    xy(
                        "Pemutar ini tidak membagikan antriannya. Banyak pemutar hanya membukanya saat satu daftar sedang diputar.",
                        "This player does not share its queue. Many players only expose it while a list is playing.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    playback.queue.forEach { entry ->
                        QueueRow(
                            entry = entry,
                            onClick = { SpotifyMediaBridge.playQueueItem(entry.queueId) },
                        )
                    }
                }
                Text(
                    xy("{0} lagu di antrian", "{0} tracks in queue", playback.queue.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }

        section("pustaka-musik", xy("Pustaka", "Library")) {
            PanelHint(
                xy(
                    "Pustaka dibaca lewat MediaBrowserService: album, playlist, dan artis yang BELUM diputar. Tidak semua aplikasi membukanya — kalau ditolak, antrian tetap jalan.",
                    "The library is read through MediaBrowserService: albums, playlists, and artists that are NOT loaded yet. Not every app opens it — if refused, the queue still works.",
                ),
            )
            if (!library.discovered) {
                XyPillButton(
                    xy("Cari pemutar", "Find players"),
                    { MediaLibraryBrowser.discover(context) },
                    icon = XyIcons.Search,
                    primary = false,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (library.sources.isEmpty()) {
                Text(
                    xy(
                        "Tidak ada aplikasi terpasang yang membuka pustaka lewat MediaBrowserService.",
                        "No installed app exposes a library through MediaBrowserService.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    library.sources.forEach { source ->
                        XyPillButton(
                            source.label,
                            { MediaLibraryBrowser.connect(context, source) },
                            icon = XyIcons.Library,
                            primary = library.connectedPackage == source.packageName,
                            compact = true,
                        )
                    }
                }
            }

            if (library.connecting) {
                Text(
                    xy("Menyambungkan pustaka…", "Connecting to the library…"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }

            if (library.crumbs.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        library.crumbs.joinToString("  ›  ") { it.title },
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (library.crumbs.size > 1) {
                        XyPillButton(
                            xy("Naik", "Up"),
                            { MediaLibraryBrowser.up() },
                            icon = XyIcons.ChevronLeft,
                            primary = false,
                            compact = true,
                        )
                    }
                }
            }

            if (library.loading) {
                Text(
                    xy("Memuat folder…", "Loading folder…"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
            library.message?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }

            if (library.entries.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    library.entries.forEach { entry ->
                        LibraryRow(
                            entry = entry,
                            onClick = {
                                if (entry.browsable) {
                                    MediaLibraryBrowser.open(entry, entry.title)
                                } else if (entry.playable) {
                                    MediaLibraryBrowser.play(context, entry)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: MediaSourceEntry, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (source.active) XyIcons.Check else XyIcons.Music,
            contentDescription = null,
            tint = if (source.active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                source.label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    source.active && source.playing -> xy("Dikendalikan · sedang diputar", "Controlled · playing")
                    source.active -> xy("Sedang dikendalikan", "Being controlled")
                    source.playing -> xy("Sedang diputar", "Playing")
                    else -> xy("Sesi tersedia", "Session available")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun QueueRow(entry: MediaQueueEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (entry.active) XyIcons.Play else XyIcons.Music,
            contentDescription = null,
            tint = if (entry.active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
            modifier = Modifier.size(14.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.title ?: xy("(tanpa judul)", "(untitled)"),
                color = if (entry.active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontSize = 12.sp,
                fontWeight = if (entry.active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitle?.let { subtitle ->
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(entry: MediaLibraryEntry, onClick: () -> Unit) {
    val enabled = entry.browsable || entry.playable
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            when {
                entry.browsable -> XyIcons.Folder
                entry.playable -> XyIcons.Music
                else -> XyIcons.File
            },
            contentDescription = null,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            modifier = Modifier.size(15.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            entry.subtitle?.let { subtitle ->
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (entry.browsable) {
            Spacer(Modifier.width(2.dp))
            Icon(
                XyIcons.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        } else if (entry.playable) {
            Spacer(Modifier.width(2.dp))
            Icon(
                XyIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}
