package id.xydesk.remote.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import com.onesignal.OneSignal
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import id.xydesk.remote.R
import id.xydesk.remote.ui.EXTRA_OPEN_CHAT_GLOBAL
import id.xydesk.remote.ui.EXTRA_SKIP_AUTO_RESUME
import id.xydesk.remote.ui.XyDeskHomeActivity
import id.xydesk.remote.ui.chatNickname
import id.xydesk.remote.ui.xyNow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Ronde 48 — Push Notification Chat Global (OneSignal v5 + Direct Reply gaya WhatsApp).
 *
 * Fitur:
 * 1. Registrasi OneSignal (`ffaa87d6-8e1d-42b6-bb4f-ebbbfc0bccbb`) + tag `chat_open` dan `nickname`.
 * 2. Notifikasi percakapan bertumpuk (`NotificationCompat.MessagingStyle`) lengkap dengan
 *    avatar inisial warna per pengirim, judul grup "Chat Global · XyDesk", serta hingga
 *    6 pesan terakhir yang belum dibaca.
 * 3. Tombol **Balas** langsung dari bar notifikasi (`RemoteInput` -> `ChatReplyReceiver`
 *    -> `POST /api/chat/send`) tanpa perlu membuka aplikasi, persis seperti WhatsApp.
 * 4. Tombol **Tandai dibaca** untuk membersihkan tumpukan pesan.
 * 5. Ketuk notifikasi langsung membuka layar **Chat Global** di dalam aplikasi.
 * 6. Watcher ringan cadangan (`/api/chat/history`) agar perangkat tanpa layanan GMS
 *    atau saat push tertunda tetap menerima notifikasi percakapan secara instan.
 */
object ChatPushNotifier {

    const val ONESIGNAL_APP_ID = "ffaa87d6-8e1d-42b6-bb4f-ebbbfc0bccbb"
    const val CHAT_CHANNEL_ID = "xydesk.global.chat.push"
    const val CHAT_NOTIF_ID = 2005

    const val ACTION_DIRECT_REPLY = "id.xydesk.remote.push.ACTION_DIRECT_REPLY"
    const val ACTION_MARK_READ = "id.xydesk.remote.push.ACTION_MARK_READ"
    const val KEY_TEXT_REPLY = "key_text_reply"

    private const val REQ_OPEN_CHAT = 2050
    private const val REQ_DIRECT_REPLY = 2051
    private const val REQ_MARK_READ = 2052

    private const val PREFS_NAME = "xydesk.global.chat"
    private const val KEY_UNREAD_STACK = "unread_notif_stack_v1"
    private const val KEY_LAST_SEEN_AT = "last_seen_msg_at_v1"
    private const val MAX_STACK_MESSAGES = 6

    private const val HISTORY_URL = "https://api.xydeskremote.biz.id/api/chat/history"
    private const val SEND_URL = "https://api.xydeskremote.biz.id/api/chat/send"

    @Volatile
    var isChatOpen: Boolean = false
        private set

    @Volatile
    private var watcherStarted: Boolean = false

    private val seenIds: MutableSet<String> =
        Collections.synchronizedSet( LinkedHashSet<String>() )

    private data class StackedMsg(
        val id: String,
        val name: String,
        val text: String,
        val at: Long,
        val isMe: Boolean,
    )

    @JvmStatic
    fun init(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            OneSignal.initWithContext(appContext, ONESIGNAL_APP_ID)
            val nick = chatNickname(appContext)
            OneSignal.User.addTag("nickname", nick)
            OneSignal.User.addTag("chat_open", "false")
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { OneSignal.Notifications.requestPermission(false) }
            }
            OneSignal.Notifications.addClickListener(
                object : INotificationClickListener {
                    override fun onClick(event: INotificationClickEvent) {
                        val data = event.notification.additionalData
                        if (data == null || data.optString("type") == "global_chat") {
                            clearNotification(appContext)
                            val launch = openChatIntent(appContext)
                            runCatching { appContext.startActivity(launch) }
                        }
                    }
                },
            )
        }
        startBackgroundWatcherIfNeeded(appContext)
    }

    fun setChatOpen(context: Context, open: Boolean) {
        isChatOpen = open
        val appContext = context.applicationContext
        if (open) {
            clearNotification(appContext)
        }
        runCatching {
            if (OneSignal.isInitialized) {
                OneSignal.User.addTag("chat_open", if (open) "true" else "false")
                OneSignal.User.addTag("nickname", chatNickname(appContext))
            }
        }
    }

    fun markSeen(msgId: String, at: Long = 0L, context: Context? = null) {
        if (msgId.isNotBlank()) {
            synchronized(seenIds) {
                seenIds.add(msgId)
                while (seenIds.size > 300) {
                    val first = seenIds.iterator().next()
                    seenIds.remove(first)
                }
            }
        }
        if (at > 0L && context != null) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val prev = prefs.getLong(KEY_LAST_SEEN_AT, 0L)
            if (at > prev) {
                prefs.edit().putLong(KEY_LAST_SEEN_AT, at).apply()
            }
        }
    }

    fun clearNotification(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_UNREAD_STACK).apply()
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(CHAT_NOTIF_ID)
    }

    fun onIncomingChatMessage(
        context: Context,
        msgId: String,
        senderName: String,
        text: String,
        at: Long,
    ) {
        val appContext = context.applicationContext
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val myNick = chatNickname(appContext)
        val ts = if (at > 0L) at else System.currentTimeMillis()

        // Jangan tampilkan notifikasi jika layar Chat Global sedang dibuka
        // atau jika pesan berasal dari pengguna sendiri.
        if (isChatOpen || senderName.equals(myNick, ignoreCase = false)) {
            markSeen(msgId, ts, appContext)
            return
        }

        if (msgId.isNotBlank()) {
            synchronized(seenIds) {
                if (!seenIds.add(msgId)) return
                while (seenIds.size > 300) {
                    val first = seenIds.iterator().next()
                    seenIds.remove(first)
                }
            }
        }
        markSeen(msgId, ts, appContext)

        val stack = loadStack(appContext).toMutableList()
        if (msgId.isNotBlank() && stack.any { it.id == msgId }) return
        stack.add(
            StackedMsg(
                id = msgId.ifBlank { "msg-$ts" },
                name = senderName.ifBlank { "Tamu" },
                text = cleanText,
                at = ts,
                isMe = false,
            ),
        )
        while (stack.size > MAX_STACK_MESSAGES) {
            stack.removeAt(0)
        }
        saveStack(appContext, stack)
        renderMessagingNotification(appContext, stack)
    }

    fun appendInlineReplyAndSend(context: Context, replyText: String) {
        val appContext = context.applicationContext
        val clean = replyText.trim().take(500)
        if (clean.isEmpty()) return
        val myNick = chatNickname(appContext)
        val now = System.currentTimeMillis()

        // 1. Update tampilan MessagingStyle seketika agar spinner "mengirim" di
        //    notifikasi Android berhenti dan balasan tampil seperti WhatsApp.
        val stack = loadStack(appContext).toMutableList()
        stack.add(
            StackedMsg(
                id = "local-reply-$now",
                name = myNick,
                text = clean,
                at = now,
                isMe = true,
            ),
        )
        while (stack.size > MAX_STACK_MESSAGES) {
            stack.removeAt(0)
        }
        saveStack(appContext, stack)
        renderMessagingNotification(appContext, stack)

        // 2. Kirim ke backend Cloudflare Worker (/api/chat/send)
        runCatching {
            val conn = (URL(SEND_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject()
                .put("name", myNick)
                .put("text", clean)
                .toString()
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload) }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val json = JSONObject(body)
                val msgObj = json.optJSONObject("message")
                if (msgObj != null) {
                    val serverId = msgObj.optString("id")
                    val serverAt = msgObj.optLong("at", now)
                    markSeen(serverId, serverAt, appContext)
                }
            }
            conn.disconnect()
        }
    }

    private fun renderMessagingNotification(context: Context, stack: List<StackedMsg>) {
        if (stack.isEmpty()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)

        val myNick = chatNickname(context)
        val mePerson = Person.Builder()
            .setName(xyNow("Kamu ($myNick)", "You ($myNick)"))
            .setKey("me:$myNick")
            .setIcon(IconCompat.createWithBitmap(createAvatarBitmap(myNick, isMe = true)))
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(xyNow("Chat Global · XyDesk", "Global Chat · XyDesk"))
            .setGroupConversation(true)

        for (item in stack) {
            val senderPerson = if (item.isMe) {
                mePerson
            } else {
                Person.Builder()
                    .setName(item.name)
                    .setKey("user:${item.name}")
                    .setIcon(IconCompat.createWithBitmap(createAvatarBitmap(item.name, isMe = false)))
                    .build()
            }
            messagingStyle.addMessage(item.text, item.at, senderPerson)
        }

        val openPending = PendingIntent.getActivity(
            context,
            REQ_OPEN_CHAT,
            openChatIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Tombol 1: Balas langsung dari notifikasi (RemoteInput memerlukan FLAG_MUTABLE pada API 31+)
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(xyNow("Ketik balasan ke Chat Global…", "Type a reply to Global Chat…"))
            .build()

        val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val replyPending = PendingIntent.getBroadcast(
            context,
            REQ_DIRECT_REPLY,
            Intent(context, ChatReplyReceiver::class.java).apply {
                action = ACTION_DIRECT_REPLY
            },
            replyFlags,
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_monochrome,
            xyNow("Balas", "Reply"),
            replyPending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        // Tombol 2: Tandai dibaca
        val markReadPending = PendingIntent.getBroadcast(
            context,
            REQ_MARK_READ,
            Intent(context, ChatReplyReceiver::class.java).apply {
                action = ACTION_MARK_READ
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_monochrome,
            xyNow("Tandai dibaca", "Mark as read"),
            markReadPending,
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val lastItem = stack.last()
        val unreadCount = stack.count { !it.isMe }
        val summarySubText = if (unreadCount > 1) {
            xyNow("$unreadCount pesan baru", "$unreadCount new messages")
        } else {
            xyNow("Chat Global", "Global Chat")
        }

        val builder = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("${lastItem.name} · Chat Global")
            .setContentText(lastItem.text)
            .setSubText(summarySubText)
            .setStyle(messagingStyle)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setOnlyAlertOnce(lastItem.isMe)
            .setWhen(lastItem.at)
            .setShowWhen(true)
            .setColor(0xFF4F7CFF.toInt())
            .setGroup("xydesk_global_chat")
            .setContentIntent(openPending)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        nm.notify(CHAT_NOTIF_ID, builder.build())
    }

    fun openChatIntent(context: Context): Intent =
        Intent(context, XyDeskHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SKIP_AUTO_RESUME, true)
            .putExtra(EXTRA_OPEN_CHAT_GLOBAL, true)

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHAT_CHANNEL_ID,
                xyNow("Chat Global", "Global Chat"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = xyNow(
                    "Notifikasi pesan baru dari Chat Global XyDesk Remote",
                    "New message notifications from XyDesk Remote Global Chat",
                )
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun createAvatarBitmap(name: String, isMe: Boolean): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val palette = intArrayOf(
            0xFF4F7CFF.toInt(),
            0xFF26A69A.toInt(),
            0xFF7E57C2.toInt(),
            0xFFEC407A.toInt(),
            0xFFFF7043.toInt(),
            0xFF42A5F5.toInt(),
        )
        val bgColor = if (isMe) {
            0xFF3949AB.toInt()
        } else {
            palette[(name.hashCode() and 0x7fffffff) % palette.size]
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)

        val cleaned = name.removePrefix("Tamu-").trim().ifEmpty { name.trim() }
        val initials = cleaned.take(2).uppercase()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(initials, 0, initials.length, bounds)
        val y = size / 2f - bounds.exactCenterY()
        canvas.drawText(initials, size / 2f, y, textPaint)
        return bmp
    }

    private fun loadStack(context: Context): List<StackedMsg> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_UNREAD_STACK, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<StackedMsg>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list.add(
                    StackedMsg(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        text = o.optString("text"),
                        at = o.optLong("at"),
                        isMe = o.optBoolean("isMe", false),
                    ),
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun saveStack(context: Context, stack: List<StackedMsg>) {
        val arr = JSONArray()
        for (m in stack) {
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("text", m.text)
                    .put("at", m.at)
                    .put("isMe", m.isMe),
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_UNREAD_STACK, arr.toString())
            .apply()
    }

    private fun startBackgroundWatcherIfNeeded(appContext: Context) {
        if (watcherStarted) return
        synchronized(this) {
            if (watcherStarted) return
            watcherStarted = true
        }
        Thread(
            {
                var seeded = false
                while (true) {
                    try {
                        Thread.sleep(if (seeded) 15_000L else 4_000L)
                        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        var lastSeenAt = prefs.getLong(KEY_LAST_SEEN_AT, 0L)
                        val conn = (URL(HISTORY_URL).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            setRequestProperty("Accept", "application/json")
                        }
                        if (conn.responseCode in 200..299) {
                            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                            val json = JSONObject(body)
                            val arr = json.optJSONArray("messages") ?: JSONArray()
                            if (!seeded && lastSeenAt == 0L) {
                                // Seed awal: tandai pesan lama agar tidak membanjiri notif saat baru instal/buka
                                var maxAt = System.currentTimeMillis()
                                for (i in 0 until arr.length()) {
                                    val m = arr.optJSONObject(i) ?: continue
                                    val id = m.optString("id")
                                    val at = m.optLong("at")
                                    if (id.isNotBlank()) seenIds.add(id)
                                    if (at > maxAt) maxAt = at
                                }
                                prefs.edit().putLong(KEY_LAST_SEEN_AT, maxAt).apply()
                                seeded = true
                            } else {
                                seeded = true
                                for (i in 0 until arr.length()) {
                                    val m = arr.optJSONObject(i) ?: continue
                                    val id = m.optString("id")
                                    val name = m.optString("name", "Tamu")
                                    val text = m.optString("text", "")
                                    val at = m.optLong("at", 0L)
                                    if (at > lastSeenAt) {
                                        lastSeenAt = at
                                        onIncomingChatMessage(
                                            context = appContext,
                                            msgId = id,
                                            senderName = name,
                                            text = text,
                                            at = at,
                                        )
                                    }
                                }
                            }
                        }
                        conn.disconnect()
                    } catch (_: Throwable) {
                        // Abaikan gangguan jaringan sementara
                    }
                }
            },
            "xydesk-chat-push-watcher",
        ).apply {
            isDaemon = true
            start()
        }
    }
}
