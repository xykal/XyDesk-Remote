package id.xydesk.remote.ui

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
        Row(
            modifier = modifier
                .widthIn(max = 276.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
                .clickable(onClick = onExpand)
                .padding(start = 10.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Artwork(playback, Modifier.size(34.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playback.title ?: "Spotify",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        playback.hasActiveSession -> playback.artist ?: xyNow("Sedang diputar di Spotify", "Playing in Spotify")
                        playback.notificationAccessGranted -> xyNow("Buka Spotify untuk mulai", "Open Spotify to start")
                        else -> xyNow("Ketuk untuk menghubungkan", "Tap to connect")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayerIconButton(
                icon = if (playback.isPlaying) XyIcons.Pause else XyIcons.Play,
                description = if (playback.isPlaying) xyNow("Jeda Spotify", "Pause Spotify")
                else xyNow("Putar Spotify", "Play Spotify"),
                enabled = playback.notificationAccessGranted && playback.hasActiveSession,
                onClick = onTogglePlayback,
                modifier = Modifier.size(36.dp),
            )
        }
    } else {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
            modifier = modifier,
        ) {
            Column(
                Modifier
                    .widthIn(min = 248.dp, max = 304.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.98f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f), RoundedCornerShape(22.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Artwork(playback, Modifier.size(48.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "SPOTIFY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.1.sp,
                        )
                        Text(
                            playback.title ?: if (playback.hasActiveSession) "Spotify"
                            else xyNow("Pemutar musik", "Music player"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            playback.artist ?: playback.album ?: when {
                                playback.hasActiveSession -> xyNow("Menunggu info lagu…", "Waiting for track info…")
                                playback.notificationAccessGranted -> xyNow("Buka Spotify lalu putar lagu", "Open Spotify and start playing")
                                else -> xyNow("Kontrol Spotify di HP dari sesi ini", "Control phone Spotify from this session")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PlayerIconButton(
                        icon = XyIcons.ChevronUp,
                        description = xyNow("Kecilkan pemutar Spotify", "Minimize Spotify player"),
                        onClick = onMinimize,
                        modifier = Modifier.size(36.dp),
                    )
                }

                if (!playback.notificationAccessGranted) {
                    Text(
                        xyNow(
                            "Izinkan akses notifikasi Android agar XyDesk bisa membaca info lagu Spotify dan mengirim kontrol putar/jeda. Notifikasi aplikasi lain diabaikan; login dan audio tetap di Spotify.",
                            "Allow Android notification access so XyDesk can read Spotify track details and send playback controls. Other apps' notifications are ignored; sign-in and audio stay in Spotify.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    XyPillButton(
                        text = xyNow("Beri akses Spotify", "Allow Spotify control"),
                        onClick = onRequestAccess,
                        modifier = Modifier.fillMaxWidth(),
                        icon = XyIcons.Music,
                        compact = true,
                    )
                } else if (!playback.hasActiveSession) {
                    Text(
                        xyNow(
                            "Belum ada sesi Spotify aktif. Buka Spotify di HP dan mulai putar lagu.",
                            "No active Spotify session. Open Spotify on your phone and start playing a song.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    XyPillButton(
                        text = if (isSpotifyInstalled(context)) xyNow("Buka Spotify", "Open Spotify")
                        else xyNow("Pasang Spotify", "Install Spotify"),
                        onClick = { openSpotify(context) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = XyIcons.Music,
                        compact = true,
                    )
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
                        xyNow("Audio tetap di Spotify · kontrol dari XyDesk", "Audio stays in Spotify · controls from XyDesk"),
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
