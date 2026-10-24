package id.xydesk.remote.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import id.xydesk.remote.R
import id.xydesk.remote.ui.XyDeskHomeActivity
import id.xydesk.remote.ui.xyNow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Notifikasi aplikasi (2026-10-24): Chat Global & notifikasi sosial sudah
 * dihapus total. Yang tersisa hanya notifikasi UPDATE aplikasi/data, dipakai
 * AppUpdate dan ekstensi OneSignal.
 */
object ChatPushNotifier {
    const val UPDATE_CHANNEL_ID = "xydesk.app.update.v1"
    const val KEY_PUSH_ANNOUNCEMENT = "push_announcement_enabled"

    private const val UPDATE_NOTIF_ID = 4201
    private const val REQ_OPEN_UPDATE = 4202
    private const val EXTRA_SKIP_AUTO_RESUME = "id.xydesk.remote.extra.SKIP_AUTO_RESUME"

    val unreadNotifCountState = MutableStateFlow(0)

    fun isCategoryEnabled(context: Context, key: String): Boolean {
        val prefs = context.getSharedPreferences("xydesk.notif.prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(key, true)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    xyNow("Aplikasi & Pembaruan", "App & Updates"),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }

    fun notifyAppUpdateAvailable(
        context: Context,
        title: String,
        summary: String,
        isDataPack: Boolean = true,
    ) {
        val appContext = context.applicationContext
        if (!isCategoryEnabled(appContext, KEY_PUSH_ANNOUNCEMENT)) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)
        val openAppIntent = Intent(appContext, XyDeskHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_SKIP_AUTO_RESUME, true)
        val pending = PendingIntent.getActivity(
            appContext,
            REQ_OPEN_UPDATE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(appContext, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_xydesk)
            .setContentTitle(title)
            .setContentText(summary)
            .setSubText(if (isDataPack) xyNow("Update Data In-App", "In-App Data Update") else xyNow("Update APK", "APK Update"))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        nm.notify(UPDATE_NOTIF_ID, notif)
    }
}
