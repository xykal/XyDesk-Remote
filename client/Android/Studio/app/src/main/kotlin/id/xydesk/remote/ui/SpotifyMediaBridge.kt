package id.xydesk.remote.ui

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads the active Spotify Android media session after the user grants Android's
 * notification-listener access. Audio and Spotify sign-in remain inside Spotify;
 * XyDesk only shows metadata and sends media transport controls.
 */
internal object SpotifyMediaBridge {
    const val SPOTIFY_PACKAGE = "com.spotify.music"

    private val mutablePlayback = MutableStateFlow(SpotifyPlaybackState())
    val playback: StateFlow<SpotifyPlaybackState> = mutablePlayback.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var activeController: MediaController? = null
    private var activeCallback: MediaController.Callback? = null
    @Volatile private var notificationAccess = false
    private var refreshRunnable: Runnable? = null
    @Volatile private var appContext: Context? = null

    fun hasNotificationAccess(context: Context): Boolean = runCatching {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }.getOrDefault(false)

    /** Paket aplikasi sumber yang sedang terpasang, untuk listener notifikasi. */
    fun currentSourcePackage(): String? = activeController?.packageName

    /**
     * Versi refresh yang ditunda sebentar. Listener notifikasi kini menerima
     * notifikasi media dari aplikasi mana pun, jadi panggilan bisa beruntun;
     * tanpa jeda, getActiveSessions() dipanggil berkali-kali untuk satu kejadian.
     */
    fun refreshDebounced(context: Context) {
        val application = context.applicationContext
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        val task = Runnable {
            refreshRunnable = null
            refresh(application)
        }
        refreshRunnable = task
        mainHandler.postDelayed(task, 200L)
    }

    private fun MediaController.isPlayingNow(): Boolean {
        val state = playbackState?.state ?: return false
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    private fun sourceLabelFor(packageName: String?): String? {
        val context = appContext ?: return null
        val pkg = packageName?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun refresh(context: Context) {
        val application = context.applicationContext
        appContext = application
        val access = hasNotificationAccess(application)
        notificationAccess = access
        if (!access) {
            attach(null)
            mutablePlayback.value = SpotifyPlaybackState()
            return
        }

        runCatching {
            val manager = application.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val listener = ComponentName(application, SpotifyNotificationListenerService::class.java)
            manager.getActiveSessions(listener)
                .filter { it.packageName != application.packageName }
                .let { sessions ->
                    // Sumber mana pun boleh: yang sedang diputar diprioritaskan,
                    // lalu yang punya metadata, baru sesi pertama yang tersedia.
                    sessions.firstOrNull { it.isPlayingNow() }
                        ?: sessions.firstOrNull {
                            it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null
                        }
                        ?: sessions.firstOrNull()
                }
        }.onSuccess { attach(it) }
            .onFailure {
                attach(null)
                mutablePlayback.value = SpotifyPlaybackState(notificationAccessGranted = true)
            }
    }

    fun playPause() {
        val controller = activeController ?: return
        runCatching {
            when (controller.playbackState?.state) {
                PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING -> controller.transportControls.pause()
                else -> controller.transportControls.play()
            }
        }
    }

    fun previous() {
        runCatching { activeController?.transportControls?.skipToPrevious() }
    }

    fun next() {
        runCatching { activeController?.transportControls?.skipToNext() }
    }

    private fun attach(next: MediaController?) {
        val current = activeController
        if (current != null && next != null && current.sessionToken == next.sessionToken) {
            publish(next)
            return
        }
        if (current != null) {
            activeCallback?.let { callback -> runCatching { current.unregisterCallback(callback) } }
        }
        activeController = next
        activeCallback = null

        if (next == null) {
            mutablePlayback.value = SpotifyPlaybackState(notificationAccessGranted = notificationAccess)
            return
        }

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = publish(next)
            override fun onPlaybackStateChanged(state: PlaybackState?) = publish(next)
            override fun onSessionDestroyed() {
                mainHandler.post {
                    if (activeController?.sessionToken == next.sessionToken) {
                        attach(null)
                        appContext?.let(::refresh)
                    }
                }
            }
        }
        activeCallback = callback
        runCatching { next.registerCallback(callback, mainHandler) }
        publish(next)
    }

    private fun publish(controller: MediaController) {
        if (activeController?.sessionToken != controller.sessionToken) return
        val metadata = controller.metadata
        val state = controller.playbackState
        val artwork = runCatching {
            (metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))?.let(::smallArtwork)
        }.getOrNull()
        mutablePlayback.value = SpotifyPlaybackState(
            notificationAccessGranted = notificationAccess,
            hasActiveSession = true,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING ||
                state?.state == PlaybackState.STATE_BUFFERING,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).cleanMetadata(),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).cleanMetadata(),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM).cleanMetadata(),
            artwork = artwork,
            sourceLabel = sourceLabelFor(controller.packageName),
        )
    }

    private fun smallArtwork(source: Bitmap): Bitmap {
        val maxSide = 192
        if (source.width <= maxSide && source.height <= maxSide) return source
        val scale = maxSide.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun String?.cleanMetadata(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

internal data class SpotifyPlaybackState(
    val notificationAccessGranted: Boolean = false,
    val hasActiveSession: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: Bitmap? = null,
    /** Nama aplikasi sumber, misalnya "Spotify" atau "Poweramp". */
    val sourceLabel: String? = null,
)

/** System service used only as the trusted NotificationListener component token. */
class SpotifyNotificationListenerService : android.service.notification.NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        SpotifyMediaBridge.refresh(this)
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification) {
        if (isMediaRelated(sbn)) SpotifyMediaBridge.refreshDebounced(this)
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification) {
        if (isMediaRelated(sbn)) SpotifyMediaBridge.refreshDebounced(this)
    }

    /**
     * Notifikasi media dari aplikasi mana pun, atau notifikasi apa pun dari
     * aplikasi yang sedang jadi sumber. Sebelumnya hanya paket Spotify yang
     * diterima, jadi pemutar lain tidak pernah terdeteksi.
     */
    private fun isMediaRelated(sbn: android.service.notification.StatusBarNotification): Boolean {
        if (sbn.packageName == SpotifyMediaBridge.currentSourcePackage()) return true
        return runCatching {
            sbn.notification?.extras?.getString(android.app.Notification.EXTRA_TEMPLATE) ==
                "android.app.Notification\$MediaStyle"
        }.getOrDefault(false)
    }

    override fun onListenerDisconnected() {
        SpotifyMediaBridge.refresh(this)
        super.onListenerDisconnected()
    }
}
