package id.xydesk.remote.push

import androidx.annotation.Keep
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * Interseptor push OneSignal v5: mengubah notifikasi standar menjadi
 * `NotificationCompat.MessagingStyle` gaya WhatsApp lengkap dengan bendera
 * negara, terjemahan otomatis, tombol Balas langsung (`RemoteInput`), dan
 * Tandai dibaca.
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
                event.notification.title?.substringBefore("·")?.trim().orEmpty().ifBlank { "Tamu" }
            }
            val rawText = data?.optString("text").orEmpty().ifBlank {
                event.notification.body.orEmpty()
            }
            val translated = data?.optString("translated").orEmpty()
            val targetLang = data?.optString("target_lang", "EN").orEmpty().ifBlank { "EN" }
            val fullText = if (translated.isNotBlank() && !translated.equals(rawText, ignoreCase = true) && !rawText.contains("🌐")) {
                "$rawText\n🌐 $targetLang: $translated"
            } else {
                rawText
            }
            val at = data?.optLong("at", 0L)?.takeIf { it > 0L } ?: System.currentTimeMillis()
            ChatPushNotifier.onIncomingChatMessage(
                context = event.context,
                msgId = msgId,
                senderName = senderName,
                text = fullText,
                at = at,
            )
        }
    }
}
