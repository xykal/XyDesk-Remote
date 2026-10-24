package id.xydesk.remote.push

import androidx.annotation.Keep
import com.onesignal.notifications.INotificationReceivedEvent
import com.onesignal.notifications.INotificationServiceExtension

/**
 * Interseptor push OneSignal (2026-10-24): hanya menangani notifikasi
 * update aplikasi/data. Push chat/sosial sudah tidak ada.
 */
@Keep
class ChatNotificationExtension : INotificationServiceExtension {
    override fun onNotificationReceived(event: INotificationReceivedEvent) {
        val data = event.notification.additionalData
        val type = data?.optString("type").orEmpty()
        if (type == "app_update" || type == "data_update") {
            runCatching { event.preventDefault() }
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
        }
    }
}
