package id.xydesk.remote.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.Keep
import androidx.core.app.RemoteInput

/**
 * Penerima aksi tombol notifikasi Chat Global:
 * - `ACTION_DIRECT_REPLY`: mengambil teks dari `RemoteInput` ("Balas" langsung
 *   di dalam notifikasi ala WhatsApp) lalu mengirimkannya ke Chat Global.
 * - `ACTION_MARK_READ`: menghapus tumpukan pesan belum dibaca & menutup notifikasi.
 */
@Keep
class ChatReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            ChatPushNotifier.ACTION_MARK_READ -> {
                ChatPushNotifier.clearNotification(context)
            }
            ChatPushNotifier.ACTION_DIRECT_REPLY -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                val reply = results?.getCharSequence(ChatPushNotifier.KEY_TEXT_REPLY)?.toString()?.trim()
                if (reply.isNullOrEmpty()) return
                val pending = goAsync()
                Thread {
                    try {
                        ChatPushNotifier.appendInlineReplyAndSend(context.applicationContext, reply)
                    } finally {
                        runCatching { pending.finish() }
                    }
                }.start()
            }
        }
    }
}
