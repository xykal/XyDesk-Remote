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
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
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
    const val CHAT_CHANNEL_ID = "xydesk.global.chat.system.v6"
    const val MENTION_CHANNEL_ID = "xydesk.chat.mention.system.v6"
    const val REPLY_CHANNEL_ID = "xydesk.chat.reply.system.v6"
    const val ANNOUNCEMENT_CHANNEL_ID = "xydesk.chat.announcement.system.v6"
    const val SOCIAL_ANNOUNCEMENT_CHANNEL_ID = "xydesk.community.announcement.v6"
    const val SOCIAL_MENTION_CHANNEL_ID = "xydesk.community.mention.v6"
    const val SOCIAL_REPLY_CHANNEL_ID = "xydesk.community.reply.v6"
    const val SOCIAL_COMMENT_CHANNEL_ID = "xydesk.community.comment.v6"
    const val SOCIAL_LIKE_CHANNEL_ID = "xydesk.community.like.v6"
    const val SOCIAL_REACTION_CHANNEL_ID = "xydesk.community.reaction.v6"
    const val FEED_CHANNEL_ID = "xydesk.feed.social.system.v6"
    const val UPDATE_CHANNEL_ID = "xydesk.app.update.system.v6"
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
        val githubLogin: String = "",
        val avatarUrl: String = "",
    )

    @JvmStatic
    fun ensureChannel(context: Context) {
        runCatching {
            val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null) ensureChannel(nm)
        }
    }

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
            OneSignal.User.pushSubscription.optIn()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { OneSignal.Notifications.requestPermission(false) }
            }
            // Cegah OneSignal merender notifikasi standar (versi lama) di foreground
            // agar tidak pernah terjadi double notifikasi dengan kartu Grup WA MessagingStyle.
            OneSignal.Notifications.addForegroundLifecycleListener(
                object : INotificationLifecycleListener {
                    override fun onWillDisplay(event: INotificationWillDisplayEvent) {
                        val data = event.notification.additionalData
                        val type = data?.optString("type").orEmpty()
                        if (type == "global_chat" || data?.has("msg_id") == true || type == "feed_comment" || type == "feed_like" || type == "feed_reaction" || type == "social_notif" || type == "app_update") {
                            runCatching { event.preventDefault() }
                        }
                    }
                },
            )
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
        scheduleBackgroundSyncJob(appContext)
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

    fun isSelfUser(
        context: Context,
        actorName: String = "",
        actorGithub: String = "",
        actorClientId: String = "",
    ): Boolean {
        val appCtx = context.applicationContext
        val myNick = chatNickname(appCtx).trim().removePrefix("@")
        val myGh = id.xydesk.remote.ui.getVerifiedGitHubLogin(appCtx).orEmpty().trim().removePrefix("@")
        val feedPrefs = appCtx.getSharedPreferences("xydesk_community_jokes", Context.MODE_PRIVATE)
        val myFeedName = feedPrefs.getString("display_name", null).orEmpty().trim().removePrefix("@")
        val myClientId = feedPrefs.getString("client_id", null).orEmpty().trim()

        val cleanName = actorName.substringBefore(" [").substringBefore(" (").trim().removePrefix("@")
        val cleanGh = actorGithub.trim().removePrefix("@")
        val cleanCid = actorClientId.trim()

        if (cleanCid.isNotBlank() && myClientId.isNotBlank() && cleanCid.equals(myClientId, ignoreCase = true)) {
            return true
        }
        val selfIdentities = listOf(myGh, myNick, myFeedName).filter { it.isNotBlank() }
        for (me in selfIdentities) {
            if (cleanGh.isNotBlank() && cleanGh.equals(me, ignoreCase = true)) return true
            if (cleanName.isNotBlank() && cleanName.equals(me, ignoreCase = true)) return true
        }
        return false
    }

    private fun uniqueNotifId(channelId: String, itemId: String): Int =
        (((channelId.hashCode() * 31) xor itemId.hashCode()) and 0x3fffffff) + 3000

    fun retractMessage(context: Context, id: String) {
        if (id.isBlank()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        for (channel in listOf(CHAT_CHANNEL_ID, MENTION_CHANNEL_ID, REPLY_CHANNEL_ID, ANNOUNCEMENT_CHANNEL_ID)) {
            nm?.cancel("chat:$channel:$id", uniqueNotifId(channel, id))
            nm?.cancel("chat:$channel:$id", CHAT_NOTIF_ID)
        }
        val remaining = loadStack(context).filterNot { it.id == id }
        saveStack(context, remaining)
        unreadChatCountState.intValue = remaining.count { !it.isMe }
        if (remaining.isEmpty()) {
            nm?.cancel("chat:global_group", CHAT_NOTIF_ID)
            nm?.cancel(CHAT_NOTIF_ID)
        } else {
            renderMessagingNotification(context.applicationContext, remaining)
        }
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
        avatarUrl: String = "",
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
        val isFromMe = isSelfUser(
            context = appContext,
            actorName = senderBase,
            actorGithub = githubLogin,
        )

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

        val cleanGh = githubLogin.trim().removePrefix("@").ifBlank {
            senderBase.removePrefix("@").takeIf {
                it.isNotBlank() && !it.contains(" ") && !it.startsWith("Tamu", ignoreCase = true) && !it.startsWith("Guest", ignoreCase = true)
            }.orEmpty()
        }
        val resolvedAvatar = id.xydesk.remote.ui.ChatAvatarLoader.normalizeAvatarUrl(avatarUrl, cleanGh, senderBase)

        val stack = loadStack(appContext).toMutableList()
        if (msgId.isNotBlank() && stack.any { it.id == msgId }) return
        stack.add(
            StackedMsg(
                id = msgId.ifBlank { "msg-$ts" },
                name = senderName.ifBlank { "Tamu" },
                text = cleanText,
                at = ts,
                isMe = false,
                githubLogin = cleanGh,
                avatarUrl = resolvedAvatar,
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
        actorName: String = "",
        actorGithub: String = "",
        actorClientId: String = "",
    ) {
        val appContext = context.applicationContext
        if (isSelfUser(appContext, actorName, actorGithub, actorClientId)) return
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
            "comment", "inbox" -> SOCIAL_COMMENT_CHANNEL_ID
            "like" -> SOCIAL_LIKE_CHANNEL_ID
            "reaction" -> SOCIAL_REACTION_CHANNEL_ID
            else -> FEED_CHANNEL_ID
        }
        val categoryLabel = when (cat) {
            "announcement" -> "Pengumuman Resmi"
            "mention" -> "Sebutan @"
            "reply" -> "Balasan Komentar"
            "comment", "inbox" -> "Komentar Feed"
            "like" -> "Suka (Like)"
            "reaction" -> "Reaksi Emoji"
            else -> "Komunitas"
        }
        val now = System.currentTimeMillis()
        val distinctId = uniqueNotifId(channelId, notifId)
        val openIntent = Intent(appContext, XyDeskHomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_SKIP_AUTO_RESUME, true)
        val pending = PendingIntent.getActivity(
            appContext,
            distinctId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_xydesk)
            .setContentTitle(title)
            .setSubText("Komunitas · $categoryLabel")
            .setGroup("xydesk_sep_social_${cat}_$notifId")
            .setSortKey(String.format("%019d", Long.MAX_VALUE - now))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setSummaryText("Komunitas · $categoryLabel"))
            .setWhen(now)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setContentIntent(pending)
            .build()
        nm.notify("social:$cat:$notifId", distinctId, notif)
    }

    fun appendInlineReplyAndSend(context: Context, replyText: String) {
        val appContext = context.applicationContext
        val clean = replyText.trim().take(500)
        if (clean.isEmpty()) return
        val myNick = chatNickname(appContext)
        val accountSnapshot = id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext).orEmpty()
        val now = System.currentTimeMillis()

        // Durable commit before showing a local echo. No legacy HTTP fallback.
        val localId = "local-${java.util.UUID.randomUUID()}"
        val queued = runCatching {
            val snapshot = id.xydesk.remote.ui.GlobalChatOutbox.snapshot(appContext)
            val payload = JSONObject().put("type", "msg").put("client_msg_id", localId)
                .put("github_login", accountSnapshot).put("name", myNick).put("text", clean)
                .put("country", id.xydesk.remote.ui.chatCountryCode())
            id.xydesk.remote.ui.GlobalChatOutbox.enqueue(appContext, accountSnapshot, snapshot.binding, payload, now)
        }.isSuccess
        if (!queued) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(appContext, "Balasan belum tersimpan. Buka Chat Global untuk memeriksa akun/penyimpanan.", android.widget.Toast.LENGTH_LONG).show()
            }
            return
        }
        // 1. Update tampilan MessagingStyle seketika agar spinner "mengirim" di
        //    notifikasi Android berhenti dan balasan tampil seperti WhatsApp.
        val stack = loadStack(appContext).toMutableList()
        stack.add(
            StackedMsg(
                id = localId,
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
        val unreadCount = stack.count { !it.isMe }.coerceAtLeast(1)
        val channelId = when {
            isAnnouncement -> ANNOUNCEMENT_CHANNEL_ID
            isMention -> MENTION_CHANNEL_ID
            isReply -> REPLY_CHANNEL_ID
            else -> CHAT_CHANNEL_ID
        }
        val groupTitle = when {
            isAnnouncement -> "Chat Global · Pengumuman"
            isMention -> if (unreadCount > 1) "Chat Global · XyDesk ($unreadCount pesan · menyebut Anda)" else "Chat Global · XyDesk (menyebut Anda)"
            isReply -> if (unreadCount > 1) "Chat Global · XyDesk ($unreadCount pesan · membalas Anda)" else "Chat Global · XyDesk (membalas Anda)"
            unreadCount > 1 -> "Chat Global · XyDesk ($unreadCount pesan baru)"
            else -> "Chat Global · XyDesk"
        }
        val subSummary = when {
            unreadCount > 1 -> "$unreadCount pesan belum dibaca di grup Chat Global"
            else -> "Grup Chat Global · XyDesk"
        }

        val myGh = id.xydesk.remote.ui.getVerifiedGitHubLogin(context).orEmpty()
        val myAvatarUrl = id.xydesk.remote.ui.getVerifiedGitHubAvatar(context).orEmpty()
        val meBmp = resolveSenderAvatarBitmap(context, xyNow("Anda", "You"), myGh, myAvatarUrl, true)
        val mePerson = Person.Builder()
            .setName(xyNow("Anda", "You"))
            .setKey("me_self")
            .setIcon(IconCompat.createWithBitmap(meBmp))
            .build()

        val personCache = HashMap<String, Person>()
        val messaging = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(groupTitle)
            .setGroupConversation(true)

        // Tampilkan hingga 8 pesan terakhir secara berurutan di dalam 1 kartu grup ala Grup WhatsApp
        for (item in stack.takeLast(8)) {
            val senderPerson = if (item.isMe) {
                mePerson
            } else {
                personCache.getOrPut(item.name) {
                    val senderBmp = resolveSenderAvatarBitmap(
                        context = context,
                        name = item.name,
                        githubLogin = item.githubLogin,
                        avatarUrl = item.avatarUrl,
                        isMe = false,
                    )
                    Person.Builder()
                        .setName(item.name)
                        .setKey(item.name)
                        .setIcon(IconCompat.createWithBitmap(senderBmp))
                        .build()
                }
            }
            messaging.addMessage(item.text, item.at, senderPerson)
        }

        val now = System.currentTimeMillis()
        val lastSenderBmp = resolveSenderAvatarBitmap(
            context = context,
            name = lastItem.name,
            githubLogin = lastItem.githubLogin,
            avatarUrl = lastItem.avatarUrl,
            isMe = lastItem.isMe,
        )
        // Satu kartu percakapan Grup WhatsApp terpadu (menumpuk semua chat baru di 1 notifikasi grup)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_xydesk)
            .setLargeIcon(lastSenderBmp)
            .setContentTitle(groupTitle)
            .setContentText("${lastItem.name}: ${lastItem.text}")
            .setSubText(subSummary)
            .setNumber(unreadCount)
            .setGroup("xydesk_global_chat_group")
            .setGroupSummary(true)
            .setSortKey(String.format("%019d", Long.MAX_VALUE - now))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(messaging)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(lastItem.isMe)
            .setWhen(lastItem.at.takeIf { it > 0L } ?: now)
            .setShowWhen(true)
            .setContentIntent(openPending)
            .addAction(replyAction)
            .addAction(markReadAction)

        // Bersihkan notifikasi chat versi lama / duplikat (termasuk notifikasi bawaan OneSignal tanpa tag)
        // agar selalu hanya ada 1 kartu Grup WA (chat:global_group).
        nm.cancel(CHAT_NOTIF_ID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val protectedIds = setOf(CHAT_NOTIF_ID, UPDATE_NOTIF_ID, 1001, 4401)
            nm.activeNotifications
                .filter { sbn ->
                    val t = sbn.tag
                    (t != null && t.startsWith("chat:") && t != "chat:global_group") ||
                        (t != null && t.startsWith("social:") && t.contains(":chat-")) ||
                        (t == null && sbn.id !in protectedIds)
                }
                .forEach { nm.cancel(it.tag, it.id) }
        }
        nm.notify("chat:global_group", CHAT_NOTIF_ID, builder.build())
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
                NotificationChannel(SOCIAL_MENTION_CHANNEL_ID, "Komunitas · Sebutan (@)", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(SOCIAL_REPLY_CHANNEL_ID, "Komunitas · Balasan Komentar", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(SOCIAL_COMMENT_CHANNEL_ID, "Komunitas · Komentar Postingan", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(SOCIAL_LIKE_CHANNEL_ID, "Komunitas · Suka (Like)", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(SOCIAL_REACTION_CHANNEL_ID, "Komunitas · Reaksi Emoji", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(REPLY_CHANNEL_ID, xyNow("Chat Global · Balasan Pesan", "Global Chat · Replies"), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(ANNOUNCEMENT_CHANNEL_ID, xyNow("Chat Global · Pengumuman", "Global Chat · Announcements"), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(
                    MENTION_CHANNEL_ID,
                    xyNow("Chat Global · Sebutan (@)", "Global Chat · Mentions (@)"),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = xyNow(
                        "Notifikasi saat nama Anda disebut (@mention) di Chat Global",
                        "Notifications when you are @mentioned in Global Chat",
                    )
                },
                NotificationChannel(
                    CHAT_CHANNEL_ID,
                    xyNow("Chat Global · Pesan Baru", "Global Chat · New Messages"),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = xyNow(
                        "Notifikasi pesan baru di ruang Chat Global XyDesk",
                        "Notifications for new messages in XyDesk Global Chat",
                    )
                },
                NotificationChannel(
                    FEED_CHANNEL_ID,
                    xyNow("Komunitas · Aktivitas Feed", "Community · Feed Activity"),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = xyNow(
                        "Notifikasi aktivitas komunitas lainnya di Feed",
                        "Other community activity notifications on Feed",
                    )
                },
                NotificationChannel(
                    UPDATE_CHANNEL_ID,
                    xyNow("Pembaruan Aplikasi & Info", "App Updates & Info"),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = xyNow(
                        "Notifikasi pembaruan aplikasi dan paket data",
                        "App and data pack update notifications",
                    )
                },
            )
            channels.forEach { ch ->
                ch.enableVibration(true)
                ch.vibrationPattern = longArrayOf(0, 220, 100, 220)
                ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                ch.group = when (ch.id) {
                    UPDATE_CHANNEL_ID -> "xydesk.app"
                    FEED_CHANNEL_ID,
                    SOCIAL_MENTION_CHANNEL_ID,
                    SOCIAL_REPLY_CHANNEL_ID,
                    SOCIAL_ANNOUNCEMENT_CHANNEL_ID,
                    SOCIAL_COMMENT_CHANNEL_ID,
                    SOCIAL_LIKE_CHANNEL_ID,
                    SOCIAL_REACTION_CHANNEL_ID,
                    -> "xydesk.community"
                    else -> "xydesk.chat"
                }
                nm.createNotificationChannel(ch)
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

    private fun resolveSenderAvatarBitmap(
        context: Context,
        name: String,
        githubLogin: String,
        avatarUrl: String,
        isMe: Boolean,
    ): Bitmap {
        val loaded = runCatching {
            id.xydesk.remote.ui.ChatAvatarLoader.loadCachedOrSyncForNotification(
                context = context,
                rawAvatarUrl = avatarUrl,
                githubLogin = githubLogin,
                fallbackName = name,
                isMe = isMe,
            )
        }.getOrNull()
        return loaded ?: createAvatarBitmap(name, isMe)
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
                        githubLogin = o.optString("githubLogin"),
                        avatarUrl = o.optString("avatarUrl"),
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
                    .put("isMe", m.isMe)
                    .put("githubLogin", m.githubLogin)
                    .put("avatarUrl", m.avatarUrl),
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(accountKey(context, KEY_UNREAD_STACK), arr.toString())
            .apply()
    }

    @JvmStatic
    fun scheduleBackgroundSyncJob(context: Context, delayMs: Long = 45_000L) {
        runCatching {
            val appContext = context.applicationContext
            val js = appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? android.app.job.JobScheduler ?: return
            val comp = android.content.ComponentName(appContext, id.xydesk.remote.ui.ChatOutboxJobService::class.java)
            val jobInfo = android.app.job.JobInfo.Builder(8401, comp)
                .setRequiredNetworkType(android.app.job.JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delayMs.coerceAtLeast(15_000L))
                .setOverrideDeadline((delayMs + 60_000L).coerceAtLeast(45_000L))
                .build()
            js.schedule(jobInfo)
        }
    }

    @JvmStatic
    fun pollOnceFromBackground(appContext: Context, allowSeedIfZero: Boolean = true) {
        if (isChatOpen) return
        ensureChannel(appContext)
        val accountSnapshot = id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext)
        val seenAtKey = id.xydesk.remote.ui.chatAccountPreferenceKey(KEY_LAST_SEEN_AT, accountSnapshot)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var lastSeenAt = prefs.getLong(seenAtKey, 0L)
        val joinedAt = prefs.getLong("first_joined_at_ms", 0L)
        if (joinedAt > lastSeenAt) lastSeenAt = joinedAt
        val historyEndpoint = "$HISTORY_URL?fast=1&limit=20" + (if (joinedAt > 0L) "&joined_at=$joinedAt" else "")
        val conn = (URL(historyEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_500
            readTimeout = 7_500
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode in 200..299) {
                if (accountSnapshot != id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext)) return
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                if (accountSnapshot != id.xydesk.remote.ui.getVerifiedGitHubLogin(appContext)) return
                val json = JSONObject(body)
                val onlineCount = json.optInt("online_count", 0)
                if (onlineCount > 0) {
                    id.xydesk.remote.ui.AppUpdate.updateOnlineCount(onlineCount)
                }
                val arr = json.optJSONArray("messages") ?: JSONArray()
                if (allowSeedIfZero && lastSeenAt == 0L) {
                    var maxAt = System.currentTimeMillis()
                    for (i in 0 until arr.length()) {
                        val m = arr.optJSONObject(i) ?: continue
                        val id = m.optString("id")
                        val at = m.optLong("at")
                        if (id.isNotBlank()) seenIds.add(id)
                        if (at > maxAt) maxAt = at
                    }
                    prefs.edit().putLong(seenAtKey, maxAt).apply()
                } else {
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
                                avatarUrl = m.optString("avatar_url"),
                            )
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
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
                        Thread.sleep(if (seeded) 35_000L else 2_000L)
                        if (isChatOpen) continue
                        tickCount += 1
                        if (tickCount % 2 == 1) {
                            kotlinx.coroutines.runBlocking {
                                runCatching { id.xydesk.remote.ui.AppUpdate.fetch(appContext) }
                            }
                        }
                        pollOnceFromBackground(appContext, allowSeedIfZero = !seeded)
                        seeded = true
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

/**
 * JobService Android yang menjaga sinkronisasi push notifikasi Chat Global & Komunitas
 * tetap berjalan meskipun aplikasi ditutup / di-swipe dari Recent Apps.
 */
@androidx.annotation.Keep
class ChatBackgroundSyncJobService : android.app.job.JobService() {
    override fun onStartJob(params: android.app.job.JobParameters?): Boolean {
        val appCtx = applicationContext
        Thread {
            try {
                ChatPushNotifier.pollOnceFromBackground(appCtx, allowSeedIfZero = true)
            } catch (_: Throwable) {
            } finally {
                ChatPushNotifier.scheduleBackgroundSyncJob(appCtx, delayMs = 45_000L)
                jobFinished(params, false)
            }
        }.start()
        return true
    }

    override fun onStopJob(params: android.app.job.JobParameters?): Boolean {
        ChatPushNotifier.scheduleBackgroundSyncJob(applicationContext, delayMs = 45_000L)
        return true
    }
}

