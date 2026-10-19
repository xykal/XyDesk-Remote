package id.xydesk.remote.ui

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/** Small reusable system tone, no asset/network/TTS; never bypass silent/DND. */
internal class ChatSendSoundPlayer(context: Context) : AutoCloseable {
    private val app = context.applicationContext
    private var tone: ToneGenerator? = null
    private var closed = false
    fun play(enabled: Boolean) {
        if (!enabled || closed) return
        runCatching {
            val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) return
            if (notifications != null && notifications.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) return
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35).also { tone = it }
            generator.startTone(ToneGenerator.TONE_PROP_ACK, 55)
        }
    }
    override fun close() { closed = true; runCatching { tone?.release() }; tone = null }
}
