package id.xydesk.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import id.xydesk.remote.ui.AppPrefs
import androidx.core.content.ContextCompat
import id.xydesk.remote.ui.xyNow
import id.xydesk.remote.ui.XyDeskHomeActivity
import id.xydesk.remote.ui.XySessionRegistry

/**
 * Aksi notifikasi diarahkan ke registry sesi dalam proses ini.
 */
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

    /** Kapan sesi dimulai; dipakai kronometer di notifikasi. */
    private var startedAt = 0L

    // Status terakhir (connected/connecting/reconnecting) — notifikasi ikut
    // merefleksikan keadaan sesi, bukan cuma "aktif" statis (revisi 2026-10-13).
    private var lastLabel: String? = null
    private var lastStatus: String = STATUS_CONNECTED

    override fun onCreate() {
        super.onCreate()
        startedAt = System.currentTimeMillis()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_LABEL)?.let { lastLabel = it }
        intent?.getStringExtra(EXTRA_STATUS)?.let { lastStatus = it }
        when (intent?.action) {
            ACTION_STOP -> {
                if (XySessionRegistry.count() == 0) stopSelf()
                else XySessionRegistry.disconnectAll()
                return START_NOT_STICKY
            }

            ACTION_PING -> {
                // Sudah jalan: cukup perbarui teksnya.
                startForeground(NOTIF_ID, buildNotification(intent.getStringExtra(EXTRA_LABEL)))
                return START_NOT_STICKY
            }

            // Tombol notifikasi tambahan (permintaan pemilik 2026-10-10):
            // Beranda tanpa memutus sesi.
            ACTION_HOME -> {
                runCatching {
                    startActivity(
                        Intent(this, XyDeskHomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                return START_NOT_STICKY
            }

            else -> {
                startForeground(NOTIF_ID, buildNotification(intent?.getStringExtra(EXTRA_LABEL)))
                return START_NOT_STICKY
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
            Intent(this, XyDeskHomeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, XySessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val home = PendingIntent.getService(
            this,
            2,
            Intent(this, XySessionService::class.java).setAction(ACTION_HOME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val host = label ?: lastLabel ?: "XyDesk Remote"
        val prefs = AppPrefs(this)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            // Ikon app sendiri (mark monokrom) — dulu pakai
            // android.R.drawable.stat_sys_upload_done, ikon sistem panah
            // upload yang tidak ada hubungannya dengan remote desktop.
            .setSmallIcon(id.xydesk.remote.R.drawable.ic_launcher_monochrome)
            .setContentTitle(
                when (lastStatus) {
                    STATUS_CONNECTING -> xyNow("Menyambungkan…", "Connecting…")
                    STATUS_RECONNECTING -> xyNow("Menyambungkan ulang…", "Reconnecting…")
                    else -> xyNow("Sesi remote aktif", "Remote session active")
                },
            )
            .setContentText(host)
            .setSubText(versionLabel())
            .setContentIntent(open)
            // Ikon aksi pakai vektor khusus, bukan 0 (yang di sebagian HP
            // dirender jadi logo app — laporan pemilik 2026-10-12). Total aksi
            // dijaga maksimal 3: Android menyembunyikan aksi ke-4, dan dulu
            // "Putuskan" yang kena sembunyi.
            .addAction(
                id.xydesk.remote.R.drawable.ic_notif_open,
                xyNow("Buka", "Open"),
                open,
            )
        if (prefs.notifHomeButton) {
            builder.addAction(
                id.xydesk.remote.R.drawable.ic_notif_home,
                xyNow("Beranda", "Home"),
                home,
            )
        }
        builder
            .addAction(
                id.xydesk.remote.R.drawable.ic_notif_disconnect,
                xyNow("Putuskan", "Disconnect"),
                stop,
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    when (lastStatus) {
                        STATUS_CONNECTING -> xyNow("Menghubungkan ke ", "Connecting to ") + host + "…"
                        STATUS_RECONNECTING -> xyNow("Koneksi terputus — mencoba menyambung ulang ke ", "Connection lost — retrying ") + host + "…"
                        else -> xyNow("Terhubung ke ", "Connected to ") + host
                    },
                ),
            )

        // Tampilan penuh warna: hanya berlaku untuk foreground service, dan
        // service ini memang foreground. Tanpa ini notifikasi memakai kartu
        // putih/abu bawaan Android yang tidak mirip app sama sekali.
        // Progress tak tentu selama menyambung/menyambung ulang; hilang saat
        // tersambung. setOnlyAlertOnce menjaga pembaruan ini tanpa bunyi.
        if (lastStatus == STATUS_CONNECTED) {
            builder.setProgress(0, 0, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        builder.setColor(NOTIF_ACCENT)
            .setColorized(true)

        // Kronometer = lama sesi berjalan, tanpa perlu memperbarui notifikasi.
        if (startedAt > 0L) {
            builder.setWhen(startedAt).setUsesChronometer(true)
        }

        // Ikon besar di sisi kiri. Dipakai PNG (bukan vektor) karena
        // BitmapFactory tidak membaca drawable vektor.
        runCatching {
            BitmapFactory.decodeResource(resources, id.xydesk.remote.R.drawable.xy_mark_white)
        }.getOrNull()?.let { builder.setLargeIcon(it) }

        // Layar kunci tidak menampilkan host yang sedang disambungkan.
        builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(id.xydesk.remote.R.drawable.ic_launcher_monochrome)
                    .setContentTitle(xyNow("Sesi remote aktif", "Remote session active"))
                    .setContentText("XyDesk Remote")
                    .setColor(NOTIF_ACCENT)
                    .setColorized(true)
                    .build(),
            )

        return builder.build()
    }

    /** `v1.1.1 (63)` untuk subteks notifikasi; fallback kalau tidak terbaca. */
    private fun versionLabel(): String = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "v${info.versionName} ($code)"
    }.getOrDefault("XyDesk Remote")

    companion object {
        /** Warna aksen monokrom app, dipakai notifikasi berwarna. */
        private const val NOTIF_ACCENT = 0xFF111114.toInt()

        private const val CHANNEL_ID = "xydesk_sesi_aktif"
        private const val NOTIF_ID = 4711
        const val ACTION_STOP = "id.xydesk.remote.action.STOP_SESSION"
        const val ACTION_PING = "id.xydesk.remote.action.PING_SESSION"
        const val ACTION_HOME = "id.xydesk.remote.action.GO_HOME"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_STATUS = "status"
        const val STATUS_CONNECTED = "connected"
        const val STATUS_CONNECTING = "connecting"
        const val STATUS_RECONNECTING = "reconnecting"

        /** Mulai/perbarui service. Aman dipanggil berulang. */
        fun start(
            context: Context,
            label: String?,
            ping: Boolean = false,
            status: String = STATUS_CONNECTED,
        ) {
            val intent = Intent(context, XySessionService::class.java).apply {
                putExtra(EXTRA_LABEL, label)
                putExtra(EXTRA_STATUS, status)
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
