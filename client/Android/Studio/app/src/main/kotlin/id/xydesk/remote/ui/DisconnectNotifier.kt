package id.xydesk.remote.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Notifikasi "koneksi terputus tak terduga" (backlog pemilik 2026-10-15).
 *
 * Dipost saat sesi yang tadinya tersambung mati sendiri ketika app ada di
 * background (keep-alive). Tidak dipost untuk disconnect yang user minta
 * sendiri — cek kondisi ada di XyDeskSessionScreen.
 */

internal const val DISCONNECT_CHANNEL_ID = "xydesk.disconnect"
internal const val DISCONNECT_NOTIF_ID = 2001

internal fun postDisconnectNotification(context: Context, deviceLabel: String) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
            NotificationChannel(
                DISCONNECT_CHANNEL_ID,
                xyNow("Koneksi terputus", "Connection lost"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = xyNow(
                    "Pemberitahuan saat sesi remote putus saat app di background",
                    "Alerts when a remote session drops while the app is in the background",
                )
            },
        )
    }
    val open = PendingIntent.getActivity(
        context,
        DISCONNECT_NOTIF_ID,
        Intent(context, XyDeskHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SKIP_AUTO_RESUME, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val builder = NotificationCompat.Builder(context, DISCONNECT_CHANNEL_ID)
        .setSmallIcon(id.xydesk.remote.R.drawable.ic_stat_xydesk)
        .setContentTitle(xyNow("Koneksi terputus", "Connection lost"))
        .setContentText(
            xyNow(
                "Sesi ke $deviceLabel berakhir tanpa sengaja. Ketuk untuk membuka.",
                "Session to $deviceLabel ended unexpectedly. Tap to open.",
            ),
        )
        .setStyle(
            NotificationCompat.BigTextStyle().bigText(
                xyNow(
                    "Sesi ke $deviceLabel berakhir tanpa sengaja. Ketuk untuk membuka.",
                    "Session to $deviceLabel ended unexpectedly. Tap to open.",
                ),
            ),
        )
        .setAutoCancel(true)
        .setContentIntent(open)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    // Sama seperti XySessionService: BitmapFactory tidak membaca vektor, tapi
    // resource ini PNG di drawable-nodpi.
    runCatching {
        BitmapFactory.decodeResource(context.resources, id.xydesk.remote.R.drawable.xy_mark_white)
    }.getOrNull()?.let { builder.setLargeIcon(it) }
    nm.notify(DISCONNECT_NOTIF_ID, builder.build())
}
