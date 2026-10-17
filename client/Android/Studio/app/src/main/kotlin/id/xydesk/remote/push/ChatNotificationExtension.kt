package id.xydesk.remote.push

import androidx.annotation.Keep
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * Interseptor push OneSignal v5:
 * 1. Mengubah notifikasi `global_chat` menjadi `NotificationCompat.MessagingStyle` gaya WhatsApp
 *    lengkap dengan kode negara, terjemahan otomatis, tombol Balas langsung (`RemoteInput`), dan Tandai dibaca.
 * 2. Menangani notifikasi `app_update` (Update Data Ringan OTA / Update APK Baru).
 */
@Keep
class ChatNotificationExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        val data = event.notification.additionalData
        val type = data?.optString("type").orEmpty()
        if (type == "app_update") {
            event.preventDefault(true)
            val version = data?.optString("version", "v1.1.17").orEmpty()
            val mode = data?.optString("update_mode", "data_pack").orEmpty()
            val summary = data?.optString("summary").orEmpty().ifBlank {
                event.notification.body.orEmpty()
            }
            val title = event.notification.title?.takeIf { it.isNotBlank() }
                ?: if (mode == "data_pack") "Update Data Tersedia · XyDesk $version" else "Update Aplikasi · XyDesk $version"
            ChatPushNotifier.notifyAppUpdateAvailable(
                context = event.context,
                title = title,
                summary = summary,
                isDataPack = mode != "apk",
            )
            return
        }
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
            val fullText = if (translated.isNotBlank() && !translated.equals(rawText, ignoreCase = true) && !rawText.contains("[$targetLang]")) {
                "$rawText\n[$targetLang] $translated"
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
