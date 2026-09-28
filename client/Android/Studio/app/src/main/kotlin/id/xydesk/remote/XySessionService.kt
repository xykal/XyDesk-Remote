package id.xydesk.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import id.xydesk.remote.ui.xyNow
import id.xydesk.remote.ui.XyDeskSessionActivity

/**
 * Jembatan dalam satu proses: notifikasi "Putus" minta activity menutup sesi.
 *
 * Dipakai karena service dan activity hidup di proses yang sama; callback
 * langsung lebih jujur daripada broadcast yang bisa bocor keluar app.
 */
object XySessionBridge {
    @Volatile
    var onStopRequested: (() -> Unit)? = null
}

/**
 * Foreground service sesi XyDesk.
 *
 * Kenapa ada: Android mematikan proses di latar kalau tidak ada service yang
 * berjalan di depan. Sesi RDP adalah pekerjaan yang sedang dilihat user, jadi
 * statusnya dinaikkan ke foreground + notifikasi yang jelas (bukan tersembunyi
 * — user harus bisa memutuskan sesi dari notifikasi).
 *
 * Tipe `specialUse`: sesuai definisi Android, untuk kasus yang tidak masuk
 * kategori bawaan (sesi remote desktop interaktif yang jalan terus di latar).
 * Aplikasi ini di-sideload, jadi tidak ada ketergantungan kebijakan store.
 */
class XySessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                XySessionBridge.onStopRequested?.invoke()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PING -> {
                // Sudah jalan: cukup perbarui teksnya.
                startForeground(NOTIF_ID, buildNotification(intent.getStringExtra(EXTRA_LABEL)))
                return START_NOT_STICKY
            }

            else -> {
                startForeground(NOTIF_ID, buildNotification(intent?.getStringExtra(EXTRA_LABEL)))
                return START_NOT_STICKY
            }
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            xyNow("Sesi aktif", "Active session"),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = xyNow(
                "Notifikasi selama sesi remote berjalan di latar",
                "Notification while a remote session runs in the background",
            )
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(label: String?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, XyDeskSessionActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, XySessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            // Ikon app sendiri (mark monokrom) — dulu pakai
            // android.R.drawable.stat_sys_upload_done, ikon sistem panah
            // upload yang tidak ada hubungannya dengan remote desktop.
            .setSmallIcon(id.xydesk.remote.R.drawable.ic_launcher_monochrome)
            .setContentTitle(xyNow("Sesi remote aktif", "Remote session active"))
            .setContentText(label ?: "XyDesk Remote")
            .setContentIntent(open)
            .addAction(0, xyNow("Buka", "Open"), open)
            .addAction(0, xyNow("Putuskan", "Disconnect"), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "xydesk_sesi_aktif"
        private const val NOTIF_ID = 4711
        const val ACTION_STOP = "id.xydesk.remote.action.STOP_SESSION"
        const val ACTION_PING = "id.xydesk.remote.action.PING_SESSION"
        private const val EXTRA_LABEL = "label"

        /** Mulai/perbarui service. Aman dipanggil berulang. */
        fun start(context: Context, label: String?, ping: Boolean = false) {
            val intent = Intent(context, XySessionService::class.java).apply {
                putExtra(EXTRA_LABEL, label)
                if (ping) action = ACTION_PING
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, XySessionService::class.java))
            }
        }
    }
}
