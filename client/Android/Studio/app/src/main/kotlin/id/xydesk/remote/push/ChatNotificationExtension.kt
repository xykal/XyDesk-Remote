package id.xydesk.remote.push

import androidx.annotation.Keep
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * Interseptor push OneSignal v5: mengubah notifikasi standar menjadi
 * `NotificationCompat.MessagingStyle` gaya WhatsApp lengkap dengan tombol
 * Balas langsung (`RemoteInput`) dan Tandai dibaca.
 */
@Keep
class ChatNotificationExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        val data = event.notification.additionalData
        val type = data?.optString("type").orEmpty()
        if (type == "global_chat" || data?.has("msg_id") == true) {
            event.preventDefault(true)
            val msgId = data?.optString("msg_id").orEmpty()
            val senderName = data?.optString("sender_name").orEmpty().ifBlank {
                event.notification.title?.substringAfter("·")?.trim().orEmpty().ifBlank { "Tamu" }
            }
            val text = data?.optString("text").orEmpty().ifBlank {
                event.notification.body.orEmpty()
            }
            val at = data?.optLong("at", 0L)?.takeIf { it > 0L } ?: System.currentTimeMillis()
            ChatPushNotifier.onIncomingChatMessage(
                context = event.context,
                msgId = msgId,
                senderName = senderName,
                text = text,
                at = at,
            )
        }
    }
}
