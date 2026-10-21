package id.xydesk.remote.push

import androidx.annotation.Keep
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * Interseptor push OneSignal v5:
 * 1. Mengubah notifikasi `global_chat` menjadi `NotificationCompat.MessagingStyle` gaya WhatsApp
 *    lengkap dengan kode negara, terjemahan otomatis, tombol Balas langsung (`RemoteInput`), dan Tandai dibaca,
 *    serta memisahkannya ke channel khusus (`Pesan Baru`, `Sebutan (@)`, `Balasan`, `Pengumuman`).
 * 2. Menangani notifikasi aktivitas komunitas (`feed_comment`, `feed_like`, `feed_reaction`, `social_notif`)
 *    ke channel terpisah per kategori dan memblokir notifikasi yang berasal dari diri sendiri (`isSelfUser`).
 * 3. Menangani notifikasi `app_update` (Update Data Ringan OTA / Update APK Baru).
 */
@Keep
class ChatNotificationExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        val data = event.notification.additionalData
        val type = data?.optString("type").orEmpty()
        if (type == "app_update") {
            event.preventDefault(true)
            val version = data?.optString("version", "v1.1.23").orEmpty()
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
            runCatching { event.preventDefault(true) }
            runCatching { event.preventDefault() }
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
            val rawSender = data?.optString("raw_sender").orEmpty()
            val githubLogin = data?.optString("github_login").orEmpty()
            val avatarUrl = data?.optString("avatar_url").orEmpty()
            val replyToName = data?.optString("reply_to_name").orEmpty()
            val isAnnouncement = data?.optString("category") == "announcement"
            ChatPushNotifier.onIncomingChatMessage(
                context = event.context,
                msgId = msgId,
                senderName = senderName,
                text = fullText,
                at = at,
                rawSender = rawSender,
                githubLogin = githubLogin,
                replyToName = replyToName,
                isAnnouncement = isAnnouncement,
                avatarUrl = avatarUrl,
            )
            return
        }
        if (type == "feed_comment" || type == "feed_like" || type == "feed_reaction" || type == "social_notif" || data?.has("category") == true) {
            runCatching { event.preventDefault(true) }
            runCatching { event.preventDefault() }
            val actorName = data?.optString("actor").orEmpty().ifBlank { data?.optString("actor_name").orEmpty() }
            val actorGh = data?.optString("actor_github").orEmpty()
            val actorClientId = data?.optString("actor_id").orEmpty()
            if (ChatPushNotifier.isSelfUser(event.context, actorName, actorGh, actorClientId)) {
                return
            }
            val cat = data?.optString("category").orEmpty().ifBlank {
                when (type) {
                    "feed_like" -> "like"
                    "feed_reaction" -> "reaction"
                    "feed_comment" -> "comment"
                    else -> "inbox"
                }
            }
            val notifId = data?.optString("notif_id").orEmpty()
                .ifBlank { data?.optString("joke_id").orEmpty() }
                .ifBlank { "onesignal-${System.currentTimeMillis()}" }
            val title = event.notification.title?.takeIf { it.isNotBlank() } ?: "Komunitas XyDesk"
            val body = event.notification.body?.takeIf { it.isNotBlank() } ?: ""
            if (body.isNotBlank()) {
                ChatPushNotifier.notifySocialEvent(
                    context = event.context,
                    notifId = notifId,
                    category = cat,
                    title = title,
                    body = body,
                    actorName = actorName,
                    actorGithub = actorGh,
                    actorClientId = actorClientId,
                )
            }
        }
    }
}
