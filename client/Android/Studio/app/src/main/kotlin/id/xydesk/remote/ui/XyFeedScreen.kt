package id.xydesk.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyEmptyState
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyLoadingSkeletonCard
import id.xydesk.remote.ui.components.XyTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class XyFeedHeaderTab {
    FEED,
    LEADERBOARD,
    NOTIFICATIONS,
}

@Composable
internal fun XyFeedScreen(
    onMenu: () -> Unit,
    onOpenChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }

    var activeHeaderTab by remember { mutableStateOf(XyFeedHeaderTab.FEED) }
    var notifCategory by remember { mutableStateOf("all") }
    var notifications by remember { mutableStateOf<List<XyNotificationItem>>(emptyList()) }
    var leaderboard by remember { mutableStateOf<List<XyLeaderboardEntry>>(emptyList()) }
    var onlineCount by remember { mutableIntStateOf(3) }
    var pullSignal by remember { mutableIntStateOf(0) }
    var pullBusy by remember { mutableStateOf(false) }

    LaunchedEffect(api, pullSignal) {
        launch { onlineCount = api.fetchOnlineCount() }
        launch { notifications = api.fetchNotifications() }
        launch { leaderboard = api.fetchLeaderboard(reportDeltaSec = 30L) }
    }

    LaunchedEffect(api) {
        while (true) {
            delay(30_000L)
            launch { onlineCount = api.fetchOnlineCount() }
            launch { notifications = api.fetchNotifications() }
            launch { leaderboard = api.fetchLeaderboard(reportDeltaSec = 30L) }
        }
    }

    PullToRefreshBox(
        isRefreshing = pullBusy,
        onRefresh = { pullSignal += 1 },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            XyTopBar(
                title = when (activeHeaderTab) {
                    XyFeedHeaderTab.FEED -> xy("Komunitas", "Community")
                    XyFeedHeaderTab.LEADERBOARD -> xy("Leaderboard", "Leaderboard")
                    XyFeedHeaderTab.NOTIFICATIONS -> xy("Notifikasi", "Notifications")
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .requiredSize(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                activeHeaderTab = if (activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS) {
                                    XyFeedHeaderTab.FEED
                                } else {
                                    XyFeedHeaderTab.NOTIFICATIONS
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = XyIcons.Bell,
                            contentDescription = "Notifikasi",
                            tint = if (activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                            modifier = Modifier.size(19.dp),
                        )
                        if (notifications.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .requiredSize(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444)),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    XyIconPill(XyIcons.Menu, onMenu, flat = true, contentDescription = "Menu")
                },
            )

            // Tab navigasi modern flat dengan garis indikator bawah (tanpa kotak/border bertumpuk)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeedUnderlineTab(
                        selected = activeHeaderTab == XyFeedHeaderTab.FEED,
                        icon = XyIcons.Feed,
                        label = xy("Feed", "Feed"),
                        badge = null,
                        onClick = { activeHeaderTab = XyFeedHeaderTab.FEED },
                    )
                    FeedUnderlineTab(
                        selected = activeHeaderTab == XyFeedHeaderTab.LEADERBOARD,
                        icon = XyIcons.Trophy,
                        label = xy("Leaderboard", "Leaderboard"),
                        badge = leaderboard.size.takeIf { it > 0 }?.toString(),
                        onClick = { activeHeaderTab = XyFeedHeaderTab.LEADERBOARD },
                    )
                    FeedUnderlineTab(
                        selected = activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS,
                        icon = XyIcons.Bell,
                        label = xy("Notifikasi", "Notifications"),
                        badge = notifications.size.takeIf { it > 0 }?.toString(),
                        onClick = { activeHeaderTab = XyFeedHeaderTab.NOTIFICATIONS },
                    )
                }
                Box(Modifier.fillMaxWidth().height(0.75.dp).background(Color(0xFF1E222C)))
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (activeHeaderTab) {
                    XyFeedHeaderTab.FEED -> {
                        GlobalChatAntiNeonHeroBanner(
                            onlineCount = onlineCount,
                            onClick = onOpenChat,
                        )

                        if (leaderboard.isNotEmpty()) {
                            FeedCompactLeaderboardStrip(
                                entries = leaderboard.take(3),
                                totalRanked = leaderboard.size,
                                onOpenLeaderboard = { activeHeaderTab = XyFeedHeaderTab.LEADERBOARD },
                            )
                        }

                        FunHubCard(
                            onHide = null,
                            refreshSignal = pullSignal,
                            onBusyChange = { pullBusy = it },
                        )
                    }

                    XyFeedHeaderTab.LEADERBOARD -> {
                        FeedLeaderboardTop100Panel(
                            entries = leaderboard,
                            onRefresh = { pullSignal += 1 },
                        )
                    }

                    XyFeedHeaderTab.NOTIFICATIONS -> {
                        FeedNotificationsPanel(
                            items = notifications,
                            selectedCategory = notifCategory,
                            onSelectCategory = { notifCategory = it },
                            onOpenPost = {
                                activeHeaderTab = XyFeedHeaderTab.FEED
                                pullSignal += 1
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedUnderlineTab(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    badge: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFFF8FAFC) else Color(0xFF64748B),
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFFF8FAFC) else Color(0xFF64748B),
                maxLines = 1,
            )
            if (!badge.isNullOrBlank()) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color(0xFF94A3B8) else Color(0xFF475569),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (selected) Color(0xFFF8FAFC) else Color.Transparent),
        )
    }
}

@Composable
internal fun GlobalChatAntiNeonHeroBanner(
    onlineCount: Int,
    onClick: () -> Unit,
) {
    val unreadChatCount = id.xydesk.remote.push.ChatPushNotifier.unreadChatCountState.intValue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF12151D))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.requiredSize(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1B202C)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = XyIcons.Chat,
                        contentDescription = null,
                        tint = Color(0xFFF8FAFC),
                        modifier = Modifier.size(17.dp),
                    )
                }
                // Titik notifikasi pada ikon Chat Global
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .requiredSize(9.dp)
                        .clip(CircleShape)
                        .background(if (unreadChatCount > 0) Color(0xFFEF4444) else Color(0xFF38BDF8))
                        .border(1.5.dp, Color(0xFF12151D), CircleShape),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = xy("Chat Global Komunitas", "Global Community Chat"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF8FAFC),
                    )
                    if (unreadChatCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFEF4444))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = xy("$unreadChatCount baru", "$unreadChatCount new"),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .requiredSize(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981)),
                        )
                        Text(
                            text = "$onlineCount online",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF10B981),
                        )
                    }
                }
                Text(
                    text = xy(
                        "Diskusi langsung, stiker, media HD & terjemahan otomatis",
                        "Live discussion, stickers, HD media & auto-translate",
                    ),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = XyIcons.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FeedCompactLeaderboardStrip(
    entries: List<XyLeaderboardEntry>,
    totalRanked: Int,
    onOpenLeaderboard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10131A))
            .clickable(onClick = onOpenLeaderboard)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = XyIcons.Trophy,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = xy("Peringkat Aktif Komunitas", "Community Active Leaderboard"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFF8FAFC),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = xy("Semua ($totalRanked)", "All ($totalRanked)"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                )
                Icon(
                    imageVector = XyIcons.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { item ->
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    FeedAvatarThumb(
                        avatarUrl = item.avatarUrl,
                        name = item.name,
                        isFounder = item.isFounder || isFounderAccount(item.githubLogin) || isFounderAccount(item.name),
                        size = 28.dp,
                        durationRank = item.rank.takeIf { item.totalSeconds > 0 },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "#${item.rank} ${item.name}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F5F9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.rank in 1..10) LeaderboardDurationBadge(item.rank, compact = true)
                        Text(
                            text = formatLeaderboardDuration(item.totalSeconds),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FeedNotificationsPanel(
    items: List<XyNotificationItem>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onOpenPost: (String) -> Unit,
) {
    val context = LocalContext.current
    var showPushSettings by remember { mutableStateOf(false) }
    var masterPush by remember {
        mutableStateOf(id.xydesk.remote.push.ChatPushNotifier.isCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_MASTER))
    }
    var mentionPush by remember {
        mutableStateOf(id.xydesk.remote.push.ChatPushNotifier.isCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_MENTION_REPLY))
    }
    var chatPush by remember {
        mutableStateOf(id.xydesk.remote.push.ChatPushNotifier.isCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_CHAT_ALL))
    }
    var feedPush by remember {
        mutableStateOf(id.xydesk.remote.push.ChatPushNotifier.isCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_FEED_SOCIAL))
    }
    var announcePush by remember {
        mutableStateOf(id.xydesk.remote.push.ChatPushNotifier.isCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_ANNOUNCEMENT))
    }

    LaunchedEffect(Unit) {
        id.xydesk.remote.push.ChatPushNotifier.unreadNotifCountState.intValue = 0
    }

    val filtered = remember(items, selectedCategory) {
        items.filter { notificationCategoryMatches(selectedCategory, it.category) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Panel Pengaturan Push Notifikasi Bawaan HP
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF12151D))
                .border(0.75.dp, Color(0xFF1E222C), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPushSettings = !showPushSettings },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = XyIcons.Bell,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp),
                    )
                    Column {
                        Text(
                            text = xy("Pengaturan Push Notifikasi HP", "Device Push Notification Settings"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF8FAFC),
                        )
                        Text(
                            text = xy(
                                "Atur kategori notif yang aktif & suara bawaan HP",
                                "Choose active notification categories & native device sound",
                            ),
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                        )
                    }
                }
                Text(
                    text = if (showPushSettings) xy("Tutup", "Hide") else xy("Atur", "Configure"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8),
                )
            }

            if (showPushSettings) {
                Box(Modifier.fillMaxWidth().height(0.75.dp).background(Color(0xFF1E222C)))
                val toggleItems = listOf(
                    Triple(
                        xy("Aktifkan Semua Push Notifikasi", "Enable All Push Notifications"),
                        masterPush,
                    ) { v: Boolean ->
                        masterPush = v
                        id.xydesk.remote.push.ChatPushNotifier.setCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_MASTER, v)
                    },
                    Triple(
                        xy("Mention (@) & Balasan Pesan", "Mentions (@) & Message Replies"),
                        mentionPush && masterPush,
                    ) { v: Boolean ->
                        mentionPush = v
                        id.xydesk.remote.push.ChatPushNotifier.setCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_MENTION_REPLY, v)
                    },
                    Triple(
                        xy("Pesan Baru Chat Global", "New Global Chat Messages"),
                        chatPush && masterPush,
                    ) { v: Boolean ->
                        chatPush = v
                        id.xydesk.remote.push.ChatPushNotifier.setCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_CHAT_ALL, v)
                    },
                    Triple(
                        xy("Like, Reaksi & Komentar Feed", "Feed Likes, Reactions & Comments"),
                        feedPush && masterPush,
                    ) { v: Boolean ->
                        feedPush = v
                        id.xydesk.remote.push.ChatPushNotifier.setCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_FEED_SOCIAL, v)
                    },
                    Triple(
                        xy("Pengumuman & Pembaruan", "Announcements & Updates"),
                        announcePush && masterPush,
                    ) { v: Boolean ->
                        announcePush = v
                        id.xydesk.remote.push.ChatPushNotifier.setCategoryEnabled(context, id.xydesk.remote.push.ChatPushNotifier.KEY_PUSH_ANNOUNCEMENT, v)
                    },
                )
                toggleItems.forEach { (label, checked, onToggle) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(!checked) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = if (checked) Color(0xFFF1F5F9) else Color(0xFF64748B),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (checked) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF1E222C))
                                .border(
                                    0.75.dp,
                                    if (checked) Color(0xFF10B981) else Color(0xFF334155),
                                    RoundedCornerShape(999.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = if (checked) "ON" else "OFF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (checked) Color(0xFF10B981) else Color(0xFF94A3B8),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B202C))
                        .clickable { id.xydesk.remote.push.ChatPushNotifier.openSystemNotificationSettings(context) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = xy("Buka Setelan Notifikasi Bawaan HP", "Open Native System Notification Settings"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF8FAFC),
                    )
                    Icon(
                        imageVector = XyIcons.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val categories = listOf(
                Triple("all", XyIcons.Bell, xy("Semua (${items.size})", "All (${items.size})")),
                Triple("announcement", XyIcons.Megaphone, xy("Pengumuman", "Announcements")),
                Triple("like", XyIcons.Heart, xy("Suka", "Likes")),
                Triple("reaction", XyIcons.Emoji, xy("Reaksi", "Reactions")),
                Triple("inbox", XyIcons.Inbox, xy("Komentar", "Comments")),
                Triple("reply", XyIcons.Chat, xy("Balasan", "Replies")),
                Triple("mention", XyIcons.Chat, xy("Sebutan @", "Mentions @")),
            )
            categories.forEach { (key, icon, label) ->
                val active = selectedCategory == key
                val categoryCount = items.count { notificationCategoryMatches(key, it.category) }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Color(0xFF1F2430) else Color.Transparent)
                        .clickable { onSelectCategory(key) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (active) Color(0xFFF8FAFC) else Color(0xFF64748B),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = if (key == "all") label else "$label ($categoryCount)",
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(0.75.dp).background(Color(0xFF1E222C)))

        if (filtered.isEmpty()) {
            XyEmptyState(
                title = xy("Belum Ada Notifikasi", "No Notifications Yet"),
                subtitle = xy("Belum ada pengumuman, komentar, atau balasan baru di kategori ini.", "No announcements, comments, or replies in this category yet."),
                icon = XyIcons.Bell,
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                filtered.forEach { item ->
                    val itemIcon = when (item.category) {
                        "announcement" -> XyIcons.Megaphone
                        "like" -> XyIcons.HeartFilled
                        "reaction" -> XyIcons.Emoji
                        "reply", "mention" -> XyIcons.Chat
                        else -> XyIcons.Inbox
                    }
                    val itemTint = when (item.category) {
                        "like" -> Color(0xFFFB7185)
                        "reaction" -> Color(0xFFFBBF24)
                        "announcement" -> Color(0xFF38BDF8)
                        else -> Color(0xFFCBD5E1)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPost(item.jokeId) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .requiredSize(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF161A24)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = itemIcon,
                                contentDescription = null,
                                tint = itemTint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFF8FAFC),
                                )
                                XyUserRoleBadges(
                                    name = item.actorName,
                                    githubLogin = item.actorGithub,
                                    compact = true,
                                )
                            }
                            val categoryLabel = notificationCategoryLabel(item.category)
                            Text(
                                text = xy(categoryLabel.first, categoryLabel.second),
                                style = MaterialTheme.typography.labelSmall,
                                color = itemTint,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(itemTint.copy(alpha = 0.08f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                            XyClickableLinkText(
                                text = item.body,
                                color = Color(0xFFCBD5E1),
                                linkColor = Color(0xFF38BDF8),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "${item.actorName} · ${feedTimeAgo(item.createdAtMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF64748B),
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(0.75.dp).background(Color(0xFF1A1E28)))
                }
            }
        }
    }
}

@Composable
internal fun FeedLeaderboardTop100Panel(
    entries: List<XyLeaderboardEntry>,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val mySeconds = remember(entries) { recordAndGetLocalUsageSeconds(context, 0L) }
    val myGithub = remember(entries) { getVerifiedGitHubLogin(context).orEmpty().lowercase() }
    val myRank = remember(entries, myGithub) {
        entries.firstOrNull {
            myGithub.isNotBlank() && it.githubLogin.lowercase() == myGithub
        }?.rank
    }
    val top3 = entries.take(3)
    val rest = entries.drop(3).take(97)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    xy("Peringkat Pengguna Aktif", "Active Users Leaderboard"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                )
                Text(
                    xy(
                        "Berdasarkan durasi sesi remote, penggunaan aplikasi & kontribusi komunitas",
                        "Ranked by remote session time, app usage & community activity",
                    ),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRefresh)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = XyIcons.Refresh,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = xy("Segarkan", "Refresh"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                )
            }
        }

        // Baris ringkasan waktu aktif pengguna sendiri (flat modern)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF12151E))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    xy("Waktu Aktif Kamu", "Your Active Time"),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                )
                Text(
                    formatLeaderboardDuration(mySeconds),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC),
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = if (myRank != null) xy("Peringkat #$myRank", "Rank #$myRank") else xy("Tersinkron Otomatis", "Auto-Synced"),
                fontSize = 12.sp,
                color = Color(0xFF10B981),
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Kartu Hadiah Eksklusif Top 1–10 Leaderboard + Badge Prestasi Otomatis
        run {
            val chatPrefs = remember(context) {
                context.getSharedPreferences("xydesk.global.chat", android.content.Context.MODE_PRIVATE)
            }
            var claimedRank by remember {
                mutableIntStateOf(chatPrefs.getInt("top_reward_claimed_rank", 0))
            }
            val eligibleTop10 = myRank != null && myRank in 1..10
            val autoEarned = remember(mySeconds, myGithub) {
                resolveAutoAchievementBadges(
                    totalSeconds = mySeconds,
                    sessionsCount = entries.firstOrNull { myGithub.isNotBlank() && it.githubLogin.lowercase() == myGithub }?.sessionsCount ?: 1,
                    hasVerifiedGithub = myGithub.isNotBlank(),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF181528), Color(0xFF111420)),
                        ),
                    )
                    .border(
                        0.9.dp,
                        if (eligibleTop10) Color(0xFFF59E0B) else Color(0xFF2B3147),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Icon(
                            imageVector = XyIcons.Trophy,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = xy("Hadiah Top 1–10 & Badge Otomatis", "Top 1–10 Rewards & Auto Badges"),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFEF08A),
                        )
                    }
                    if (eligibleTop10) {
                        LeaderboardDurationBadge(rank = myRank!!, compact = true)
                    }
                }

                // Daftar Hadiah Top 1-10
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = xy(
                            "• TOP #1: Imperial Gold Bubble + Bingkai Mahkota Emas + Badge TOP #1 + Preset Gaming 120FPS",
                            "• TOP #1: Imperial Gold Bubble + Gold Crown Frame + TOP #1 Badge + 120FPS Gaming Preset",
                        ),
                        fontSize = 10.5.sp,
                        color = Color(0xFFFDE68A),
                    )
                    Text(
                        text = xy(
                            "• TOP #2–#3: Podium Platinum/Bronze Bubble + Bingkai Avatar Podium + Efek Bintang Chat",
                            "• TOP #2–#3: Platinum/Bronze Podium Bubble + Podium Avatar Frame + Star Chat Effect",
                        ),
                        fontSize = 10.5.sp,
                        color = Color(0xFFE2E8F0),
                    )
                    Text(
                        text = xy(
                            "• TOP #4–#10: Cyber Indigo Top-10 Bubble Glow + Bingkai Avatar Top-10 + Badge TOP #4..#10",
                            "• TOP #4–#10: Cyber Indigo Top-10 Bubble Glow + Top-10 Avatar Frame + TOP #4..#10 Badge",
                        ),
                        fontSize = 10.5.sp,
                        color = Color(0xFFC7D2FE),
                    )
                }

                if (autoEarned.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = xy("Badge Otomatis Aktif:", "Active Auto Badges:"),
                            fontSize = 10.5.sp,
                            color = Color(0xFF94A3B8),
                        )
                        autoEarned.forEach { b ->
                            XyAutoAchievementBadge(spec = b, compact = true)
                        }
                    }
                }

                if (eligibleTop10) {
                    val isClaimed = claimedRank == myRank
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isClaimed) Color(0xFF065F46) else Color(0xFFD97706))
                            .clickable {
                                chatPrefs.edit()
                                    .putInt("top_reward_claimed_rank", myRank!!)
                                    .putString("bubble_style_mode", if (myRank == 1) "amber" else "glass")
                                    .putString("bubble_floating_element", "STARS")
                                    .apply()
                                claimedRank = myRank!!
                                android.widget.Toast.makeText(
                                    context,
                                    "Hadiah Eksklusif TOP #$myRank berhasil diklaim & diaktifkan di Chat Global & Profil!",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (isClaimed) {
                                xy("Hadiah TOP #$myRank Aktif (Ketuk untuk Terapkan Ulang)", "TOP #$myRank Reward Active (Tap to Re-Apply)")
                            } else {
                                xy("Klaim & Aktifkan Hadiah Eksklusif TOP #$myRank Sekarang", "Claim & Activate TOP #$myRank Exclusive Reward Now")
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }
                } else {
                    Text(
                        text = xy(
                            "Masuk Peringkat Top 1–10 untuk membuka klaim hadiah Bubble & Bingkai Eksklusif secara otomatis!",
                            "Reach Top 1–10 on the leaderboard to automatically unlock Exclusive Bubble & Avatar Frame rewards!",
                        ),
                        fontSize = 10.5.sp,
                        color = Color(0xFF94A3B8),
                    )
                }
            }
        }

        if (top3.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                val second = top3.getOrNull(1)
                val first = top3.getOrNull(0)
                val third = top3.getOrNull(2)

                if (second != null) {
                    PodiumColumn(
                        entry = second,
                        rankLabel = "#2",
                        accent = Color(0xFF94A3B8),
                        modifier = Modifier.weight(1f),
                        elevated = false,
                    )
                }
                if (first != null) {
                    PodiumColumn(
                        entry = first,
                        rankLabel = "#1",
                        accent = Color(0xFFFBBF24),
                        modifier = Modifier.weight(1.1f),
                        elevated = true,
                    )
                }
                if (third != null) {
                    PodiumColumn(
                        entry = third,
                        rankLabel = "#3",
                        accent = Color(0xFFD97706),
                        modifier = Modifier.weight(1f),
                        elevated = false,
                    )
                }
            }
        } else {
            XyLoadingSkeletonCard(count = 3, itemHeight = 56.dp)
        }

        Box(Modifier.fillMaxWidth().height(0.75.dp).background(Color(0xFF1E222C)))

        // Daftar flat tanpa kartu/border bertumpuk
        Column(modifier = Modifier.fillMaxWidth()) {
            rest.forEach { item ->
                val isFounder = item.isFounder || isFounderAccount(item.githubLogin) || isFounderAccount(item.name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "#${item.rank}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (item.rank <= 10) Color(0xFFF8FAFC) else Color(0xFF64748B),
                        modifier = Modifier.width(32.dp),
                    )
                    FeedAvatarThumb(
                        avatarUrl = item.avatarUrl,
                        name = item.name,
                        isFounder = isFounder,
                        size = 36.dp,
                        durationRank = item.rank.takeIf { item.totalSeconds > 0 },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = item.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF8FAFC),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            XyUserRoleBadges(
                                name = item.name,
                                githubLogin = item.githubLogin,
                                compact = true,
                            )
                        }
                        val sub = buildString {
                            if (item.githubLogin.isNotBlank()) append("@${item.githubLogin} · ")
                            append(xy("${item.sessionsCount} aktivitas", "${item.sessionsCount} activities"))
                        }
                        Text(
                            text = sub,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = formatLeaderboardDuration(item.totalSeconds),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCBD5E1),
                    )
                }
                Box(Modifier.fillMaxWidth().height(0.75.dp).background(Color(0xFF171B24)))
            }
        }
    }
}

@Composable
private fun PodiumColumn(
    entry: XyLeaderboardEntry,
    rankLabel: String,
    accent: Color,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    val isFounder = entry.isFounder || isFounderAccount(entry.githubLogin) || isFounderAccount(entry.name)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (elevated) Color(0xFF151923) else Color(0xFF10131B))
            .padding(horizontal = 8.dp, vertical = if (elevated) 14.dp else 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = rankLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = accent,
        )
        FeedAvatarThumb(
            avatarUrl = entry.avatarUrl,
            name = entry.name,
            isFounder = isFounder,
            size = if (elevated) 48.dp else 38.dp,
            durationRank = entry.rank.takeIf { entry.totalSeconds > 0 },
        )
        Text(
            text = entry.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF8FAFC),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        XyUserRoleBadges(
            name = entry.name,
            githubLogin = entry.githubLogin,
            compact = true,
            durationRank = entry.rank,
        )
        Text(
            text = formatLeaderboardDuration(entry.totalSeconds),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF94A3B8),
        )
    }
}

internal fun formatLeaderboardDuration(totalSeconds: Long): String {
    val sec = totalSeconds.coerceAtLeast(60L)
    val hours = sec / 3600L
    val mins = (sec % 3600L) / 60L
    return if (hours > 0L) {
        "${hours}j ${mins}m"
    } else {
        "${mins} mnt"
    }
}
