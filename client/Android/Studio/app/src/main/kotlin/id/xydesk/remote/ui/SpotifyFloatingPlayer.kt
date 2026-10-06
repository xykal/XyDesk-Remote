package id.xydesk.remote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton

@Composable
internal fun SpotifyFloatingPlayer(
    playback: SpotifyPlaybackState,
    expanded: Boolean,
    onExpand: () -> Unit,
    onMinimize: () -> Unit,
    onTogglePlayback: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRequestAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (!expanded) {
        // Compact drag-handle style: only a small line is visible until tapped.
        Box(
            modifier = modifier
                .size(width = 72.dp, height = 36.dp)
                .clickable(
                    onClickLabel = xyNow("Buka pemutar musik", "Open music player"),
                    onClick = onExpand,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)),
            )
        }
    } else {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
            modifier = modifier,
        ) {
            // Tampilan "liquid glass". Blur latar yang sesungguhnya tidak
            // dipakai karena dua alasan: Modifier.blur memburamkan kontennya
            // sendiri (bukan yang di belakangnya), dan backdrop blur tidak
            // tersedia di atas SurfaceView tempat layar RDP digambar -- selain
            // itu blur baru ada di API 31 sedangkan minSdk proyek 23.
            // Jadi efek kacanya dibangun dari lapisan transparan + kilau
            // vertikal + tepi terang, yang tetap terbaca di atas desktop apa
            // pun karena tidak bergantung tema.
            Column(
                Modifier
                    .widthIn(min = 236.dp, max = 280.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.14f),
                            0.45f to Color.White.copy(alpha = 0.03f),
                            1f to Color.Black.copy(alpha = 0.07f),
                        ),
                    )
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.40f),
                            1f to Color.White.copy(alpha = 0.10f),
                        ),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(playback, Modifier.size(44.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            // Nama aplikasi sumber, bukan tulisan "SPOTIFY" tetap:
                            // bridge sekarang menerima pemutar apa pun.
                            (playback.sourceLabel ?: xyNow("Pemutar", "Player")).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.1.sp,
                        )
                        Text(
                            playback.title
                                ?: if (playback.hasActiveSession) {
                                    playback.sourceLabel ?: xyNow("Sedang diputar", "Now playing")
                                } else xyNow("Pemutar musik", "Music player"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            playback.artist ?: playback.album ?: when {
                                playback.hasActiveSession -> xyNow("Menunggu info lagu…", "Waiting for track info…")
                                playback.notificationAccessGranted -> xyNow("Putar lagu dari aplikasi musik mana pun di HP", "Play a song from any music app on your phone")
                                else -> xyNow("Kontrol pemutar di HP dari sesi ini", "Control the phone player from this session")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PlayerIconButton(
                        icon = XyIcons.ChevronUp,
                        description = xyNow("Kecilkan pemutar musik", "Minimize music player"),
                        onClick = onMinimize,
                        modifier = Modifier.size(32.dp),
                    )
                }

                if (!playback.notificationAccessGranted) {
                    Text(
                        xyNow(
                            "Izinkan akses notifikasi Android agar XyDesk bisa membaca info lagu dan mengirim kontrol putar/jeda. Pemutar apa pun didukung; login dan audio tetap di aplikasi musik itu.",
                            "Allow Android notification access so XyDesk can read track details and send playback controls. Any music player works; sign-in and audio stay inside that app.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    XyPillButton(
                        text = xyNow("Beri akses notifikasi", "Allow notification access"),
                        onClick = onRequestAccess,
                        modifier = Modifier.fillMaxWidth(),
                        icon = XyIcons.Music,
                        compact = true,
                    )
                } else if (!playback.hasActiveSession) {
                    Text(
                        xyNow(
                            "Belum ada musik yang diputar. Putar lagu dari aplikasi musik mana pun di HP, lalu kontrol dari sini.",
                            "Nothing is playing yet. Start a song in any music app on your phone, then control it from here.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Tombol hanya ditawarkan kalau Spotify memang terpasang.
                    // Jalur "Pasang Spotify" dibuang: sumbernya tidak lagi
                    // harus Spotify, jadi XyDesk tidak mendorong satu aplikasi.
                    if (isSpotifyInstalled(context)) {
                        XyPillButton(
                            text = xyNow("Buka Spotify", "Open Spotify"),
                            onClick = { openSpotify(context) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = XyIcons.Music,
                            compact = true,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerIconButton(
                            xyNow("Sebelumnya", "Previous"),
                            XyIcons.SkipPrevious,
                            onPrevious,
                        )
                        Spacer(Modifier.width(10.dp))
                        PlayerIconButton(
                            description = if (playback.isPlaying) xyNow("Jeda", "Pause") else xyNow("Putar", "Play"),
                            icon = if (playback.isPlaying) XyIcons.Pause else XyIcons.Play,
                            onClick = onTogglePlayback,
                            emphasized = true,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        PlayerIconButton(
                            xyNow("Berikutnya", "Next"),
                            XyIcons.SkipNext,
                            onNext,
                        )
                    }
                    Text(
                        xyNow("Audio tetap di HP · kontrol dari XyDesk", "Audio stays on your phone · controls from XyDesk"),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Artwork(playback: SpotifyPlaybackState, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        val art = playback.artwork
        if (art != null) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = xyNow("Sampul album", "Album artwork"),
                modifier = Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Icon(
                XyIcons.Music,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun PlayerIconButton(
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(42.dp),
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    val shape = CircleShape
    val background = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val tint = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .clip(shape)
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (emphasized) 0.25f else 0.7f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.25f))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(19.dp),
        )
    }
}

private fun isSpotifyInstalled(context: Context): Boolean = runCatching {
    context.packageManager.getPackageInfo(SpotifyMediaBridge.SPOTIFY_PACKAGE, 0)
}.isSuccess

private fun openSpotify(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(SpotifyMediaBridge.SPOTIFY_PACKAGE)
    val intent = launch ?: Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://play.google.com/store/apps/details?id=${SpotifyMediaBridge.SPOTIFY_PACKAGE}"),
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

internal fun openSpotifyNotificationAccess(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
