package id.xydesk.remote.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyGlassCard
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class XyFeedHeaderTab {
    FEED,
    NOTIFICATIONS,
    LEADERBOARD,
}

@Composable
internal fun XyFeedScreen(
    onMenu: () -> Unit,
    onOpenChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val api = remember(context) { CommunityJokesApi(context) }

    var activeHeaderTab by remember { mutableStateOf(XyFeedHeaderTab.FEED) }
    var notifCategory by remember { mutableStateOf("all") } // "all" | "announcement" | "inbox" | "reply"
    var notifications by remember { mutableStateOf<List<XyNotificationItem>>(emptyList()) }
    var leaderboard by remember { mutableStateOf<List<XyLeaderboardEntry>>(emptyList()) }
    var onlineCount by remember { mutableIntStateOf(3) }
    var pullSignal by remember { mutableIntStateOf(0) }
    var pullBusy by remember { mutableStateOf(false) }

    // Fetch online count & notifications in parallel without blocking initial Feed render
    LaunchedEffect(api, pullSignal) {
        launch { onlineCount = api.fetchOnlineCount() }
        launch { notifications = api.fetchNotifications() }
        if (activeHeaderTab == XyFeedHeaderTab.LEADERBOARD) {
            launch { leaderboard = api.fetchLeaderboard(reportDeltaSec = 30L) }
        }
    }

    LaunchedEffect(api, activeHeaderTab) {
        if (activeHeaderTab == XyFeedHeaderTab.LEADERBOARD && leaderboard.isEmpty()) {
            leaderboard = api.fetchLeaderboard(reportDeltaSec = 15L)
        }
    }

    LaunchedEffect(api) {
        while (true) {
            delay(30_000L)
            launch { onlineCount = api.fetchOnlineCount() }
            launch { notifications = api.fetchNotifications() }
            if (activeHeaderTab == XyFeedHeaderTab.LEADERBOARD) {
                launch { leaderboard = api.fetchLeaderboard(reportDeltaSec = 30L) }
            }
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
                    XyFeedHeaderTab.FEED -> xy("Feed Komunitas", "Community Feed")
                    XyFeedHeaderTab.NOTIFICATIONS -> xy("Kotak Notifikasi", "Notifications")
                    XyFeedHeaderTab.LEADERBOARD -> xy("Peringkat Top 100", "Top 100 Leaderboard")
                },
                actions = {
                    Surface(
                        onClick = {
                            activeHeaderTab = if (activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS) {
                                XyFeedHeaderTab.FEED
                            } else {
                                XyFeedHeaderTab.NOTIFICATIONS
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = if (activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS) {
                            Color(0xFF27272A)
                        } else {
                            Color(0xFF14161C)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS) Color(0xFFE4E4E7) else Color(0xFF272B36),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = XyIcons.Bell,
                                contentDescription = "Notifikasi",
                                tint = Color(0xFFF4F4F5),
                                modifier = Modifier.size(14.dp),
                            )
                            if (notifications.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color(0xFFEF4444))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = notifications.size.coerceAtMost(99).toString(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        onClick = {
                            activeHeaderTab = if (activeHeaderTab == XyFeedHeaderTab.LEADERBOARD) {
                                XyFeedHeaderTab.FEED
                            } else {
                                XyFeedHeaderTab.LEADERBOARD
                            }
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = if (activeHeaderTab == XyFeedHeaderTab.LEADERBOARD) {
                            Color(0xFF27272A)
                        } else {
                            Color(0xFF14161C)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activeHeaderTab == XyFeedHeaderTab.LEADERBOARD) Color(0xFFE4E4E7) else Color(0xFF272B36),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = XyIcons.Trophy,
                                contentDescription = "Top 100",
                                tint = Color(0xFFF4F4F5),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "Top 100",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF4F4F5),
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    XyIconPill(XyIcons.Menu, onMenu, flat = true, contentDescription = "Menu")
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedHeaderTabChip(
                    selected = activeHeaderTab == XyFeedHeaderTab.FEED,
                    icon = XyIcons.Feed,
                    label = xy("Feed Utama", "Main Feed"),
                    badge = null,
                    onClick = { activeHeaderTab = XyFeedHeaderTab.FEED },
                    modifier = Modifier.weight(1f),
                )
                FeedHeaderTabChip(
                    selected = activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS,
                    icon = XyIcons.Bell,
                    label = xy("Notifikasi", "Notifications"),
                    badge = notifications.size.takeIf { it > 0 }?.toString(),
                    onClick = { activeHeaderTab = XyFeedHeaderTab.NOTIFICATIONS },
                    modifier = Modifier.weight(1f),
                )
                FeedHeaderTabChip(
                    selected = activeHeaderTab == XyFeedHeaderTab.LEADERBOARD,
                    icon = XyIcons.Trophy,
                    label = xy("Top 100", "Top 100"),
                    badge = null,
                    onClick = { activeHeaderTab = XyFeedHeaderTab.LEADERBOARD },
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (activeHeaderTab) {
                    XyFeedHeaderTab.FEED -> {
                        GlobalChatAntiNeonHeroBanner(
                            onlineCount = onlineCount,
                            onClick = onOpenChat,
                        )

                        XyGlassCard(padding = 14.dp) {
                            FunHubCard(
                                onHide = null,
                                refreshSignal = pullSignal,
                                onBusyChange = { pullBusy = it },
                            )
                        }
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

                    XyFeedHeaderTab.LEADERBOARD -> {
                        FeedLeaderboardTop100Panel(
                            entries = leaderboard,
                            onRefresh = { pullSignal += 1 },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedHeaderTabChip(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) Color(0xFF1F232E) else Color(0xFF111319),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF52525B) else Color(0xFF222631),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!badge.isNullOrBlank()) {
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEF4444))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GlobalChatAntiNeonHeroBanner(
    onlineCount: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111318),
        border = BorderStroke(1.dp, Color(0xFF262A34)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF151821),
                            Color(0xFF101217),
                            Color(0xFF14161D),
                        ),
                    ),
                ),
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .height(160.dp),
            ) {
                val w = size.width
                val h = size.height
                val cx = w * 0.85f
                val cy = h * 0.45f
                drawCircle(
                    color = Color(0xFF94A3B8).copy(alpha = 0.08f),
                    radius = 52.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(
                    color = Color(0xFF64748B).copy(alpha = 0.12f),
                    radius = 30.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawRoundRect(
                    color = Color(0xFF1E232E).copy(alpha = 0.65f),
                    topLeft = Offset(w - 114.dp.toPx(), 24.dp.toPx()),
                    size = Size(88.dp.toPx(), 30.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
                drawRoundRect(
                    color = Color(0xFF252B38).copy(alpha = 0.75f),
                    topLeft = Offset(w - 98.dp.toPx(), 62.dp.toPx()),
                    size = Size(78.dp.toPx(), 28.dp.toPx()),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1A1D26),
                        border = BorderStroke(1.dp, Color(0xFF2E3442)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = XyIcons.Chat,
                                contentDescription = null,
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                "GLOBAL LOUNGE · XYSHIELD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E8F0),
                                letterSpacing = 0.6.sp,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF0F241D),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981)),
                            )
                            Text(
                                text = xy("$onlineCount Online", "$onlineCount Online"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF6EE7B7),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = xy("Chat Global Komunitas XyDesk", "XyDesk Global Community Chat"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC),
                    )
                    Text(
                        text = xy(
                            "Diskusi realtime lintas negara, berbagi stiker, media HD, pratinjau tautan, dan proteksi XyShield.",
                            "Real-time cross-region lounge with custom stickers, HD media, link previews, and XyShield protection.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 2,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val badges = listOf(
                        XyIcons.Globe to "Auto-Translate",
                        XyIcons.Shield to "XyShield",
                        XyIcons.Camera to "Media HD",
                        XyIcons.Star to "Stiker Kustom",
                    )
                    badges.forEach { (icon, label) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF161921),
                            border = BorderStroke(1.dp, Color(0xFF262B36)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFCBD5E1),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = xy("Terverifikasi GitHub · Bebas Spam", "GitHub Verified · Spam-Free"),
                        fontSize = 11.sp,
                        color = Color(0xFFA1A1AA),
                        fontWeight = FontWeight.Medium,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF4F4F5),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = xy("Buka Ruang Chat", "Open Lounge"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF09090B),
                            )
                            Icon(
                                imageVector = XyIcons.ArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF09090B),
                                modifier = Modifier.size(13.dp),
                            )
                        }
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
    val filtered = remember(items, selectedCategory) {
        when (selectedCategory) {
            "announcement" -> items.filter { it.category == "announcement" }
            "inbox" -> items.filter { it.category == "inbox" }
            "reply" -> items.filter { it.category == "reply" || it.category == "mention" }
            else -> items
        }
    }

    XyGlassCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        xy("Pusat Notifikasi", "Notification Center"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        xy("Pengumuman resmi, kotak masuk komentar, dan balasan", "Official announcements, comment inbox, and replies"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    Triple("inbox", XyIcons.Inbox, xy("Kotak Masuk", "Inbox")),
                    Triple("reply", XyIcons.Chat, xy("Balasan & Mention", "Replies & Mentions")),
                )
                categories.forEach { (key, icon, label) ->
                    val active = selectedCategory == key
                    Surface(
                        onClick = { onSelectCategory(key) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) Color(0xFF27272A) else Color(0xFF14171F),
                        border = BorderStroke(
                            1.dp,
                            if (active) Color(0xFF71717A) else Color(0xFF262B36),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (active) Color(0xFFF4F4F5) else Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) Color(0xFFF4F4F5) else Color(0xFFCBD5E1),
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = xy("Belum ada notifikasi di kategori ini.", "No notifications in this category yet."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filtered.forEach { item ->
                        val isAnn = item.category == "announcement"
                        val itemIcon = when (item.category) {
                            "announcement" -> XyIcons.Megaphone
                            "reply", "mention" -> XyIcons.Chat
                            else -> XyIcons.Inbox
                        }
                        Surface(
                            onClick = { onOpenPost(item.jokeId) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isAnn) Color(0xFF181A22) else Color(0xFF13161E),
                            border = BorderStroke(
                                1.dp,
                                if (isAnn) Color(0xFF52525B) else Color(0xFF242936),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF202531)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = itemIcon,
                                        contentDescription = null,
                                        tint = Color(0xFFE4E4E7),
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
                                        if (isFounderAccount(item.actorGithub) || isFounderAccount(item.actorName)) {
                                            XyFounderBadge(compact = true)
                                        }
                                    }
                                    Text(
                                        text = item.body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFCBD5E1),
                                    )
                                    Text(
                                        text = "${item.actorName} · ${feedTimeAgo(item.createdAtMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                    )
                                }
                            }
                        }
                    }
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
    val top3 = entries.take(3)
    val rest = entries.drop(3).take(97)

    XyGlassCard(padding = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = XyIcons.Trophy,
                            contentDescription = null,
                            tint = Color(0xFFF4F4F5),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            xy("Peringkat Top 1–100 XyDesk", "XyDesk Top 1–100 Leaderboard"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        xy(
                            "Peringkat pengguna paling aktif menggunakan aplikasi dan sesi remote",
                            "Ranked by total active application and remote session hours",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1A1E28),
                    border = BorderStroke(1.dp, Color(0xFF2D3342)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = XyIcons.Refresh,
                            contentDescription = null,
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = xy("Segarkan", "Refresh"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF141821),
                border = BorderStroke(1.dp, Color(0xFF272D3B)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            xy("Total Waktu Aktif Kamu", "Your Total Active Time"),
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                        )
                        Text(
                            formatLeaderboardDuration(mySeconds),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        text = xy("Tersinkron Otomatis", "Auto-Synced"),
                        fontSize = 11.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (top3.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val second = top3.getOrNull(1)
                    val first = top3.getOrNull(0)
                    val third = top3.getOrNull(2)

                    if (second != null) {
                        PodiumCard(
                            entry = second,
                            rankLabel = "RANK #2",
                            accent = Color(0xFFCBD5E1),
                            modifier = Modifier.weight(1f),
                            elevated = false,
                        )
                    }
                    if (first != null) {
                        PodiumCard(
                            entry = first,
                            rankLabel = "RANK #1",
                            accent = Color(0xFFE4E4E7),
                            modifier = Modifier.weight(1.1f),
                            elevated = true,
                        )
                    }
                    if (third != null) {
                        PodiumCard(
                            entry = third,
                            rankLabel = "RANK #3",
                            accent = Color(0xFFA1A1AA),
                            modifier = Modifier.weight(1f),
                            elevated = false,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rest.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF13161E),
                        border = BorderStroke(1.dp, Color(0xFF222733)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "#${item.rank}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (item.rank <= 10) Color(0xFFF4F4F5) else Color(0xFF94A3B8),
                                modifier = Modifier.width(34.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF232938)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = item.name.take(1).uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        text = item.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF8FAFC),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (item.isFounder || isFounderAccount(item.githubLogin) || isFounderAccount(item.name)) {
                                        XyFounderBadge(compact = true)
                                    }
                                }
                                Text(
                                    text = xy("${item.sessionsCount} sesi aktif", "${item.sessionsCount} active sessions"),
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                )
                            }
                            Text(
                                text = formatLeaderboardDuration(item.totalSeconds),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE2E8F0),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumCard(
    entry: XyLeaderboardEntry,
    rankLabel: String,
    accent: Color,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (elevated) Color(0xFF1B1F2A) else Color(0xFF141821),
        border = BorderStroke(1.dp, accent.copy(alpha = if (elevated) 0.55f else 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = if (elevated) 14.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = rankLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accent,
            )
            Box(
                modifier = Modifier
                    .size(if (elevated) 42.dp else 34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.name.take(1).uppercase(),
                    fontSize = if (elevated) 15.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Text(
                text = entry.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF8FAFC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (entry.isFounder || isFounderAccount(entry.githubLogin) || isFounderAccount(entry.name)) {
                XyFounderBadge(compact = true)
            }
            Text(
                text = formatLeaderboardDuration(entry.totalSeconds),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accent,
            )
        }
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
