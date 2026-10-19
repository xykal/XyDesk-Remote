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
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
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

    const val ONESIGNAL_APP_ID = "e3d5adea-1c0f-4986-9ce5-e088d65f0998"
    const val CHAT_CHANNEL_ID = "xydesk.global.chat.system.v5"
    const val MENTION_CHANNEL_ID = "xydesk.mention.reply.system.v4"
    const val FEED_CHANNEL_ID = "xydesk.feed.social.system.v4"
    const val UPDATE_CHANNEL_ID = "xydesk.app.update.system.v4"
    const val SOCIAL_ANNOUNCEMENT_CHANNEL_ID = "xydesk.community.announcement.v1"
    const val SOCIAL_MENTION_CHANNEL_ID = "xydesk.community.mention.v1"
    const val SOCIAL_REPLY_CHANNEL_ID = "xydesk.community.reply.v1"
    const val REPLY_CHANNEL_ID = "xydesk.chat.reply.v1"
    const val ANNOUNCEMENT_CHANNEL_ID = "xydesk.announcement.v1"
    const val CHAT_NOTIF_ID = 2005
    const val UPDATE_NOTIF_ID = 2006
    const val SOCIAL_NOTIF_BASE_ID = 2100

    const val KEY_PUSH_MASTER = "push_master_enabled"
    const val KEY_PUSH_CHAT_ALL = "push_chat_all_enabled"
    const val KEY_PUSH_MENTION_REPLY = "push_mention_reply_enabled"
    const val KEY_PUSH_FEED_SOCIAL = "push_feed_social_enabled"
    const val KEY_PUSH_ANNOUNCEMENT = "push_announcement_enabled"

    val unreadChatCountState = androidx.compose.runtime.mutableIntStateOf(0)
    val unreadNotifCountState = androidx.compose.runtime.mutableIntStateOf(0)

    const val ACTION_DIRECT_REPLY = "id.xydesk.remote.push.ACTION_DIRECT_REPLY"
    const val ACTION_MARK_READ = "id.xydesk.remote.push.ACTION_MARK_READ"
    const val KEY_TEXT_REPLY = "key_text_reply"

    private const val REQ_OPEN_CHAT = 2050
    private const val REQ_DIRECT_REPLY = 2051
    private const val REQ_MARK_READ = 2052
    private const val REQ_OPEN_UPDATE = 2053

    private const val PREFS_NAME = "xydesk.global.chat"
    private const val KEY_UNREAD_STACK = "unread_notif_stack_v1"
    private const val KEY_LAST_SEEN_AT = "last_seen_msg_at_v1"
    private const val MAX_STACK_MESSAGES = 12

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
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) ensureChannel(nm)
            unreadChatCountState.intValue = loadStack(appContext).count { !it.isMe }
        }
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

    private fun accountKey(context: Context, base: String): String =
        id.xydesk.remote.ui.chatAccountPreferenceKey(base, id.xydesk.remote.ui.getVerifiedGitHubLogin(context))

    /** Called by the central identity writer, not just when the chat screen is visible. */
    fun onAccountChanged(context: Context) {
        synchronized(seenIds) { seenIds.clear() }
        clearNotification(context)
        unreadNotifCountState.intValue = 0
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_UNREAD_STACK).apply()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm?.activeNotifications?.filter { it.tag?.startsWith("social:") == true }
                ?.forEach { nm?.cancel(it.tag, it.id) }
        }
        id.xydesk.remote.ui.ensureFirstJoinedAtMs(context, id.xydesk.remote.ui.getVerifiedGitHubLogin(context))
        runCatching {
            if (OneSignal.isInitialized) OneSignal.User.addTag("nickname", chatNickname(context))
        }
    }

    fun isCategoryEnabled(context: Context, key: String): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PUSH_MASTER, true)) return false
        return prefs.getBoolean(key, true)
    }

    fun setCategoryEnabled(context: Context, key: String, enabled: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(key, enabled).apply()
    }

    fun openSystemNotificationSettings(context: Context) {
        runCatching {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }

    fun setChatOpen(context: Context, open: Boolean) {
        isChatOpen = open
        val appContext = context.applicationContext
        if (open) {
            unreadChatCountState.intValue = 0
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
            val prev = prefs.getLong(accountKey(context, KEY_LAST_SEEN_AT), 0L)
            if (at > prev) {
                prefs.edit().putLong(accountKey(context, KEY_LAST_SEEN_AT), at).apply()
            }
        }
    }

    fun clearNotification(context: Context) {
        val appContext = context.applicationContext
        unreadChatCountState.intValue = 0
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(accountKey(context, KEY_UNREAD_STACK)).apply()
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(CHAT_NOTIF_ID) // remove legacy combined notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm?.activeNotifications?.filter { it.tag?.startsWith("chat:") == true }
                ?.forEach { nm?.cancel(it.tag, it.id) }
        }
    }

    fun retractMessage(context: Context, id: String) {
        if (id.isBlank()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        for (channel in listOf(CHAT_CHANNEL_ID, MENTION_CHANNEL_ID, REPLY_CHANNEL_ID, ANNOUNCEMENT_CHANNEL_ID)) {
            nm?.cancel("chat:$channel:$id", CHAT_NOTIF_ID)
        }
        val remaining = loadStack(context).filterNot { it.id == id }
        saveStack(context, remaining)
        unreadChatCountState.intValue = remaining.count { !it.isMe }
    }

    fun onIncomingChatMessage(
        context: Context,
        msgId: String,
        senderName: String,
        text: String,
        at: Long,
        rawSender: String = "",
        githubLogin: String = "",
        replyToName: String = "",
        isAnnouncement: Boolean = false,
    ) {
        val appContext = context.applicationContext
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        val myNick = chatNickname(appContext).trim()
        val myGh = id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext).orEmpty().trim().removePrefix("@")
        val ts = if (at > 0L) at else System.currentTimeMillis()

        val senderBase = rawSender.ifBlank {
            senderName.substringBefore(" [").substringBefore(" (").trim()
        }
        val isFromMe = (myNick.isNotBlank() && senderBase.equals(myNick, ignoreCase = true)) ||
            (myGh.isNotBlank() && (senderBase.equals(myGh, ignoreCase = true) || githubLogin.equals(myGh, ignoreCase = true)))

        // Jangan tampilkan notifikasi jika pesan berasal dari pengguna sendiri.
        if (isFromMe) {
            markSeen(msgId, ts, appContext)
            return
        }

        if (isChatOpen) {
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
        unreadChatCountState.intValue = stack.count { !it.isMe }.coerceAtLeast(1)

        // Cek apakah pesan ini me-mention pengguna atau membalas pesan pengguna
        val isMentionedMe = (myNick.isNotBlank() && cleanText.contains("@$myNick", ignoreCase = true)) ||
            (myGh.isNotBlank() && cleanText.contains("@$myGh", ignoreCase = true))
        val isReplyToMe = replyToName.isNotBlank() && (
            (myNick.isNotBlank() && replyToName.removePrefix("@").equals(myNick, ignoreCase = true)) ||
                (myGh.isNotBlank() && replyToName.removePrefix("@").equals(myGh, ignoreCase = true))
            )

        if (isAnnouncement) {
            if (!isCategoryEnabled(appContext, KEY_PUSH_ANNOUNCEMENT)) return
        } else if (isMentionedMe || isReplyToMe) {
            if (!isCategoryEnabled(appContext, KEY_PUSH_MENTION_REPLY)) return
        } else {
            if (!isCategoryEnabled(appContext, KEY_PUSH_CHAT_ALL)) return
        }

        renderSystemChatNotification(
            context = appContext,
            stack = stack,
            isMention = isMentionedMe,
            isReply = isReplyToMe,
            isAnnouncement = isAnnouncement,
        )
    }

    fun notifySocialEvent(
        context: Context,
        notifId: String,
        category: String,
        title: String,
        body: String,
    ) {
        val appContext = context.applicationContext
        val cat = category.lowercase()
        val allowed = when (cat) {
            "announcement" -> isCategoryEnabled(appContext, KEY_PUSH_ANNOUNCEMENT)
            "mention", "reply" -> isCategoryEnabled(appContext, KEY_PUSH_MENTION_REPLY)
            "like", "reaction", "comment", "inbox" -> isCategoryEnabled(appContext, KEY_PUSH_FEED_SOCIAL)
            else -> isCategoryEnabled(appContext, KEY_PUSH_MASTER)
        }
        if (!allowed) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)
        val channelId = when (cat) {
            "announcement" -> SOCIAL_ANNOUNCEMENT_CHANNEL_ID
            "mention" -> SOCIAL_MENTION_CHANNEL_ID
            "reply" -> SOCIAL_REPLY_CHANNEL_ID
            else -> FEED_CHANNEL_ID
        }
        val openIntent = Intent(appContext, XyDeskHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_SKIP_AUTO_RESUME, true)
        val pending = PendingIntent.getActivity(
            appContext,
            (notifId.hashCode() and 0x7fffffff),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_xydesk)
            .setContentTitle(title)
            .setSubText("Komunitas · ${cat.replaceFirstChar { it.uppercase() }}")
            .setGroup("xydesk.community.$cat")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        nm.notify("social:$cat:$notifId", SOCIAL_NOTIF_BASE_ID, notif)
    }

    fun appendInlineReplyAndSend(context: Context, replyText: String) {
        val appContext = context.applicationContext
        val clean = replyText.trim().take(500)
        if (clean.isEmpty()) return
        val myNick = chatNickname(appContext)
        val accountSnapshot = id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext).orEmpty()
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
                instanceFollowRedirects = false
                id.xydesk.remote.ui.GlobalChatRepository.token(appContext)?.takeIf { it.isNotBlank() }
                    ?.let { setRequestProperty("Authorization", "Bearer $it") }
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 10_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject()
                .put("github_login", accountSnapshot)
                .put("name", myNick)
                .put("text", clean)
                .put("country", id.xydesk.remote.ui.chatCountryCode())
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
        renderSystemChatNotification(context, stack, isMention = false, isReply = false, isAnnouncement = false)
    }

    private fun renderSystemChatNotification(
        context: Context,
        stack: List<StackedMsg>,
        isMention: Boolean,
        isReply: Boolean,
        isAnnouncement: Boolean,
    ) {
        if (stack.isEmpty()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        ensureChannel(nm)

        val openPending = PendingIntent.getActivity(
            context,
            REQ_OPEN_CHAT,
            openChatIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(xyNow("Ketik balasan…", "Type a reply…"))
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
            R.drawable.ic_stat_xydesk,
            xyNow("Balas", "Reply"),
            replyPending,
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()

        val markReadPending = PendingIntent.getBroadcast(
            context,
            REQ_MARK_READ,
            Intent(context, ChatReplyReceiver::class.java).apply {
                action = ACTION_MARK_READ
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stat_xydesk,
            xyNow("Baca semua chat", "Read all chat"),
            markReadPending,
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val lastItem = stack.last()
        val unreadCount = stack.count { !it.isMe }
        val channelId = when {
            isAnnouncement -> ANNOUNCEMENT_CHANNEL_ID
            isMention -> MENTION_CHANNEL_ID
            isReply -> REPLY_CHANNEL_ID
            else -> CHAT_CHANNEL_ID
        }
        val titleText = when {
            isAnnouncement -> "Pengumuman · ${lastItem.name}"
            isMention -> "${lastItem.name} menyebut Anda"
            isReply -> "${lastItem.name} membalas pesan Anda"
            else -> lastItem.name
        }
        val conversation = when {
            isAnnouncement -> "Chat Global · Pengumuman"
            isMention -> "Chat Global · Sebutan"
            isReply -> "Chat Global · Balasan"
            else -> "Chat Global · Pesan"
        }
        val sender = androidx.core.app.Person.Builder().setName(lastItem.name).setKey(lastItem.name).build()
        val messaging = NotificationCompat.MessagingStyle(androidx.core.app.Person.Builder().setName("Anda").build())
            .setConversationTitle(conversation).setGroupConversation(true)
            .addMessage(lastItem.text, lastItem.at, sender)

        // Gunakan sistem notifikasi standar bawaan HP (mengikuti pengaturan suara/getar channel sistem HP)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_xydesk)
            .setContentTitle(titleText)
            .setContentText(lastItem.text)
            .setSubText(conversation)
            .setGroup("xydesk.chat.$channelId")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setStyle(messaging)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(lastItem.isMe)
            .setWhen(lastItem.at)
            .setShowWhen(true)
            .setContentIntent(openPending)
            .addAction(replyAction)
            .addAction(markReadAction)

        nm.cancel(CHAT_NOTIF_ID)
        nm.notify("chat:$channelId:${lastItem.id}", CHAT_NOTIF_ID, builder.build())
        // Keep notification tray bounded independently of the stored unread stack.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            nm.activeNotifications.filter { it.tag?.startsWith("chat:") == true }
                .sortedByDescending { it.postTime }.drop(MAX_STACK_MESSAGES)
                .forEach { nm.cancel(it.tag, it.id) }
        }
    }

    fun openChatIntent(context: Context): Intent =
        Intent(context, XyDeskHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(EXTRA_SKIP_AUTO_RESUME, true)
            .putExtra(EXTRA_OPEN_CHAT_GLOBAL, true)

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

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannelGroups(listOf(
                android.app.NotificationChannelGroup("xydesk.chat", "Chat Global"),
                android.app.NotificationChannelGroup("xydesk.community", "Komunitas & Feed"),
                android.app.NotificationChannelGroup("xydesk.app", "Aplikasi & Pembaruan"),
            ))
            val channels = listOf(
                NotificationChannel(SOCIAL_ANNOUNCEMENT_CHANNEL_ID, "Komunitas · Pengumuman", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(SOCIAL_MENTION_CHANNEL_ID, "Komunitas · Sebutan", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(SOCIAL_REPLY_CHANNEL_ID, "Komunitas · Balasan", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(REPLY_CHANNEL_ID, xyNow("Balasan Chat", "Chat Replies"), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(ANNOUNCEMENT_CHANNEL_ID, xyNow("Pengumuman", "Announcements"), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(
                    MENTION_CHANNEL_ID,
                    xyNow("Chat Global · Sebutan (@)", "Global Chat · Mentions (@)"),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = xyNow(
                        "Notifikasi saat nama Anda disebut (@mention) atau pesan/komentar Anda dibalas",
                        "Notifications when you are @mentioned or your message/comment is replied to",
                    )
                },
                NotificationChannel(
                    CHAT_CHANNEL_ID,
                    xyNow("Pesan Chat Global", "Global Chat Messages"),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = xyNow(
                        "Notifikasi pesan baru di ruang Chat Global XyDesk",
                        "Notifications for new messages in XyDesk Global Chat",
                    )
                },
                NotificationChannel(
                    FEED_CHANNEL_ID,
                    xyNow("Like, Reaksi & Komentar Feed", "Feed Likes, Reactions & Comments"),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = xyNow(
                        "Notifikasi saat postingan Anda disukai, diberi reaksi, atau dikomentari",
                        "Notifications when your posts receive likes, reactions, or comments",
                    )
                },
                NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    xyNow("Pembaruan aplikasi", "App updates"),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = xyNow(
                        "Notifikasi pengumuman resmi dan pembaruan aplikasi",
                        "Official announcements and app update notifications",
                    )
                },
            )
            channels.forEach {
                it.group = when (it.id) {
                    UPDATE_CHANNEL_ID -> "xydesk.app"
                    FEED_CHANNEL_ID, SOCIAL_MENTION_CHANNEL_ID, SOCIAL_REPLY_CHANNEL_ID, SOCIAL_ANNOUNCEMENT_CHANNEL_ID -> "xydesk.community"
                    else -> "xydesk.chat"
                }
                nm.createNotificationChannel(it)
            }
        }
    }

    @Volatile
    private var lastSoundPlayedAtMs: Long = 0L

    private fun playFallbackSoundIfNeeded(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastSoundPlayedAtMs < 1500L) return
        lastSoundPlayedAtMs = now
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (uri != null) {
                val r = RingtoneManager.getRingtone(context.applicationContext, uri)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    r?.isLooping = false
                }
                r?.play()
            }
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
        val raw = prefs.getString(accountKey(context, KEY_UNREAD_STACK), null) ?: return emptyList()
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
            .putString(accountKey(context, KEY_UNREAD_STACK), arr.toString())
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
                var tickCount = 0
                while (true) {
                    try {
                        Thread.sleep(if (seeded) 60_000L else 2_500L)
                        if (isChatOpen) continue
                        tickCount += 1
                        if (tickCount % 2 == 1) {
                            kotlinx.coroutines.runBlocking {
                                runCatching { id.xydesk.remote.ui.AppUpdate.fetch(appContext) }
                            }
                        }
                        val accountSnapshot = id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext)
                        val seenAtKey = id.xydesk.remote.ui.chatAccountPreferenceKey(KEY_LAST_SEEN_AT, accountSnapshot)
                        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        var lastSeenAt = prefs.getLong(seenAtKey, 0L)
                        val joinedAt = prefs.getLong("first_joined_at_ms", 0L)
                        if (joinedAt > lastSeenAt) lastSeenAt = joinedAt
                        val historyEndpoint = if (joinedAt > 0L) "$HISTORY_URL?joined_at=$joinedAt" else HISTORY_URL
                        val conn = (URL(historyEndpoint).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 8_000
                            readTimeout = 8_000
                            setRequestProperty("Accept", "application/json")
                        }
                        try {
                        if (conn.responseCode in 200..299) {
                            if (accountSnapshot != id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext)) {
                                continue
                            }
                            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                            if (accountSnapshot != id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext)) continue
                            val json = JSONObject(body)
                            val onlineCount = json.optInt("online_count", 0)
                            if (onlineCount > 0) {
                                id.xydesk.remote.ui.AppUpdate.updateOnlineCount(onlineCount)
                            }
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
                                prefs.edit().putLong(seenAtKey, maxAt).apply()
                                seeded = true
                            } else {
                                seeded = true
                                for (i in 0 until arr.length()) {
                                    val m = arr.optJSONObject(i) ?: continue
                                    val id = m.optString("id")
                                    if (m.optBoolean("deleted", false)) {
                                        retractMessage(appContext, id)
                                        continue
                                    }
                                    val rawName = m.optString("name", "Tamu")
                                    val cc = m.optString("country", "ID").ifBlank { "ID" }
                                    val text = m.optString("text", "")
                                    val translated = m.optString("translated", "")
                                    val targetLang = m.optString("target_lang", "EN").ifBlank { "EN" }
                                    val at = m.optLong("at", 0L)
                                    val displayText = if (translated.isNotBlank() && !translated.equals(text, ignoreCase = true)) {
                                        "$text\n[$targetLang] $translated"
                                    } else {
                                        text
                                    }
                                    val isFounder = m.optBoolean("is_founder", rawName.equals("xykal", ignoreCase = true))
                                    val senderLabel = if (isFounder) {
                                        "$rawName [FOUNDER] ($cc)"
                                    } else {
                                        "$rawName ($cc)"
                                    }
                                    if (at > lastSeenAt) {
                                        lastSeenAt = at
                                        onIncomingChatMessage(
                                            context = appContext,
                                            msgId = id,
                                            senderName = senderLabel,
                                            text = displayText,
                                            at = at,
                                            rawSender = rawName,
                                            githubLogin = m.optString("github_login"),
                                            replyToName = m.optString("reply_to_name"),
                                            isAnnouncement = m.optBoolean("is_announcement", false),
                                        )
                                    }
                                }
                            }
                        }
                        } finally {
                            conn.disconnect()
                        }
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
