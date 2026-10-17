package id.xydesk.remote.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.components.XyGlassCard
import id.xydesk.remote.ui.components.XyGlassSectionLabel
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyTopBar
import kotlinx.coroutines.delay

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

    // Poll online count + notifications + report active usage for Leaderboard Top 1-100
    LaunchedEffect(api, pullSignal) {
        onlineCount = api.fetchOnlineCount()
        notifications = api.fetchNotifications()
        leaderboard = api.fetchLeaderboard(reportDeltaSec = 30L)
    }

    LaunchedEffect(api) {
        while (true) {
            delay(25_000L)
            onlineCount = api.fetchOnlineCount()
            notifications = api.fetchNotifications()
            leaderboard = api.fetchLeaderboard(reportDeltaSec = 25L)
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
                    XyFeedHeaderTab.LEADERBOARD -> xy("Leaderboard Top 100", "Top 100 Leaderboard")
                },
                actions = {
                    // Tombol Cepat Tab Notifikasi di Header
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
                            Color(0xFFF59E0B).copy(alpha = 0.22f)
                        } else {
                            Color(0xFF181B22)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS) Color(0xFFF59E0B) else Color(0xFF2A2F3A),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("🔔", fontSize = 13.sp)
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
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    // Tombol Cepat Leaderboard Top 100 di Header
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
                            Color(0xFFF59E0B).copy(alpha = 0.22f)
                        } else {
                            Color(0xFF181B22)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (activeHeaderTab == XyFeedHeaderTab.LEADERBOARD) Color(0xFFF59E0B) else Color(0xFF2A2F3A),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("🏆", fontSize = 13.sp)
                            Text(
                                "Top 100",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeHeaderTab == XyFeedHeaderTab.LEADERBOARD) Color(0xFFFBBF24) else Color(0xFFE2E8F0),
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    XyIconPill(XyIcons.Menu, onMenu, flat = true, contentDescription = "Menu")
                },
            )

            // Header Navigation Tabs: Feed · Notifikasi (Pengumuman/Kotak Masuk/Balasan) · Leaderboard Top 100
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedHeaderTabChip(
                    selected = activeHeaderTab == XyFeedHeaderTab.FEED,
                    label = xy("📰 Feed Utama", "📰 Main Feed"),
                    badge = null,
                    onClick = { activeHeaderTab = XyFeedHeaderTab.FEED },
                    modifier = Modifier.weight(1f),
                )
                FeedHeaderTabChip(
                    selected = activeHeaderTab == XyFeedHeaderTab.NOTIFICATIONS,
                    label = xy("🔔 Notifikasi", "🔔 Notifications"),
                    badge = notifications.size.takeIf { it > 0 }?.toString(),
                    onClick = { activeHeaderTab = XyFeedHeaderTab.NOTIFICATIONS },
                    modifier = Modifier.weight(1f),
                )
                FeedHeaderTabChip(
                    selected = activeHeaderTab == XyFeedHeaderTab.LEADERBOARD,
                    label = xy("🏆 Top 100", "🏆 Top 100"),
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
                        // Banner Chat Global Anti-Neon dengan Elemen Grafis, Animasi Halus & Status Online Realtime
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
    label: String,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFF1F2430) else Color(0xFF12151C),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFFF59E0B).copy(alpha = 0.7f) else Color(0xFF232834),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFFF8FAFC) else Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!badge.isNullOrBlank()) {
                Spacer(Modifier.width(5.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFF59E0B))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = badge,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                    )
                }
            }
        }
    }
}

/**
 * Banner Chat Global Anti-Neon di Feed — satu bahasa desain dengan Banner Free RDP:
 * Monokrom karbon matte + aksen emas/emerald halus, ilustrasi radar & gelombang obrolan
 * beranimasi di latar, serta indikator jumlah anggota online secara live.
 */
@Composable
internal fun GlobalChatAntiNeonHeroBanner(
    onlineCount: Int,
    onClick: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "chatHero")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val waveShift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waveShift",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF111318),
        border = BorderStroke(1.dp, Color(0xFF282D37)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF14171F),
                            Color(0xFF101217),
                            Color(0xFF171512),
                        ),
                    ),
                ),
        ) {
            // Elemen Grafis Arsitektural Anti-Neon (Grid, Gelombang Sinyal, & Siluet Bubble)
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .height(178.dp),
            ) {
                val w = size.width
                val h = size.height
                // Lingkaran orbit radar halus di sisi kanan
                val cx = w * 0.84f
                val cy = h * 0.46f
                drawCircle(
                    color = Color(0xFFF59E0B).copy(alpha = 0.06f),
                    radius = 66.dp.toPx() * pulse,
                    center = Offset(cx, cy),
                )
                drawCircle(
                    color = Color(0xFF94A3B8).copy(alpha = 0.10f),
                    radius = 48.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.2.dp.toPx()),
                )
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = 0.14f),
                    radius = 28.dp.toPx(),
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.dp.toPx()),
                )
                // Kartu siluet bubble bertumpuk elegan
                val offsetY = (waveShift - 0.5f) * 8.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF1E232E).copy(alpha = 0.72f),
                    topLeft = Offset(w - 118.dp.toPx(), 22.dp.toPx() + offsetY),
                    size = Size(92.dp.toPx(), 34.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
                drawRoundRect(
                    color = Color(0xFF252B38).copy(alpha = 0.85f),
                    topLeft = Offset(w - 102.dp.toPx(), 64.dp.toPx() - offsetY),
                    size = Size(82.dp.toPx(), 30.dp.toPx()),
                    cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                )
                // Bar sinyal audio/typing halus
                for (i in 0 until 5) {
                    val barH = (10 + ((i * 7 + (waveShift * 14).toInt()) % 18)).dp.toPx()
                    drawRoundRect(
                        color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                        topLeft = Offset(w - (88 - i * 8).dp.toPx(), 79.dp.toPx() - barH / 2f - offsetY),
                        size = Size(3.5.dp.toPx(), barH),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    )
                }
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
                    // Badge Anti-Neon Hub Komunitas
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E222B),
                        border = BorderStroke(1.dp, Color(0xFF323846)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("💬", fontSize = 11.sp)
                            Text(
                                "GLOBAL LOUNGE · XYSHIELD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE2E8F0),
                                letterSpacing = 0.7.sp,
                            )
                        }
                    }

                    // Indikator Status Online Berapa Orang (Live Pulse)
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF0F241D),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size((8f * pulse).dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.32f)),
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981)),
                                )
                            }
                            Text(
                                text = xy("$onlineCount Online", "$onlineCount Online"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6EE7B7),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = xy("Chat Global Komunitas XyDesk", "XyDesk Global Community Chat"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFF8FAFC),
                    )
                    Text(
                        text = xy(
                            "Diskusi realtime lintas negara, berbagi stiker favorit, media HD, OG preview, & proteksi XyShield.",
                            "Real-time cross-region lounge with favorite stickers, HD media, OG link previews & XyShield protection.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 2,
                    )
                }

                // Elemen Fitur Mini Pills (Anti-Neon Matte)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "🌍 Auto-Translate",
                        "🛡️ Anti-Virus",
                        "🖼️ Media HD & Video",
                        "🎭 Stiker Favorit",
                    ).forEach { badge ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF171A21),
                            border = BorderStroke(1.dp, Color(0xFF262B36)),
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFCBD5E1),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = xy("Wajib Login Akun · Bebas Spam", "Account Login Required · Spam-Free"),
                        fontSize = 11.sp,
                        color = Color(0xFFF59E0B),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF8FAFC),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = xy("Gabung Chat", "Enter Lounge"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                            )
                            Text("→", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Panel Tab Notifikasi di Header:
 * Kategori: Semua · Pengumuman · Kotak Masuk · Balasan & Mention
 */
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
                        xy("Pengumuman resmi, kotak masuk komentar, & balasan/mention", "Official announcements, comment inbox, & replies/mentions"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Filter Kategori: Semua, Pengumuman, Kotak Masuk, Balasan
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val categories = listOf(
                    "all" to xy("Semua (${items.size})", "All (${items.size})"),
                    "announcement" to xy("📢 Pengumuman", "📢 Announcements"),
                    "inbox" to xy("📥 Kotak Masuk", "📥 Inbox"),
                    "reply" to xy("💬 Balasan & @Mention", "💬 Replies & @Mentions"),
                )
                categories.forEach { (key, label) ->
                    val active = selectedCategory == key
                    Surface(
                        onClick = { onSelectCategory(key) },
                        shape = RoundedCornerShape(999.dp),
                        color = if (active) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF161922),
                        border = BorderStroke(
                            1.dp,
                            if (active) Color(0xFFF59E0B) else Color(0xFF282D3A),
                        ),
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) Color(0xFFFBBF24) else Color(0xFFCBD5E1),
                        )
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
                        Surface(
                            onClick = { onOpenPost(item.jokeId) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isAnn) Color(0xFF1B1710) else Color(0xFF14171F),
                            border = BorderStroke(
                                1.dp,
                                if (isAnn) Color(0xFFF59E0B).copy(alpha = 0.55f) else Color(0xFF252A36),
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAnn) Color(0xFFF59E0B).copy(alpha = 0.2f)
                                            else Color(0xFF222734),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = when (item.category) {
                                            "announcement" -> "📢"
                                            "reply" -> "↩️"
                                            "mention" -> "@"
                                            else -> "📥"
                                        },
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF8FAFC),
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
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAnn) Color(0xFFFBBF24) else Color(0xFFF8FAFC),
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

/**
 * Panel Leaderboard Top 1, 2, 3 sampai 100 — Pengguna Paling Lama Menggunakan APK / Bermain Sesi Remote.
 */
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
                    Text(
                        xy("🏆 Leaderboard Top 1–100 XyDesk", "🏆 XyDesk Top 1–100 Leaderboard"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        xy(
                            "Peringkat pengguna paling aktif menggunakan APK & bermain sesi remote",
                            "Ranked by total active APK & remote gaming session hours",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1A1E28),
                    border = BorderStroke(1.dp, Color(0xFF2D3342)),
                ) {
                    Text(
                        text = xy("Segarkan", "Refresh"),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE2E8F0),
                    )
                }
            }

            // Kartu Statistik Durasi Aktif Pengguna Sendiri
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF151922),
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
                            xy("Total Waktu Aktif & Bermain Kamu", "Your Active & Play Time"),
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                        )
                        Text(
                            formatLeaderboardDuration(mySeconds),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFBBF24),
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        text = xy("Otomatis Tersinkron", "Auto-Synced"),
                        fontSize = 11.sp,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Podium Top 1, Top 2, Top 3
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
                            medal = "🥈 #2",
                            accent = Color(0xFFCBD5E1),
                            modifier = Modifier.weight(1f),
                            elevated = false,
                        )
                    }
                    if (first != null) {
                        PodiumCard(
                            entry = first,
                            medal = "👑 #1",
                            accent = Color(0xFFF59E0B),
                            modifier = Modifier.weight(1.12f),
                            elevated = true,
                        )
                    }
                    if (third != null) {
                        PodiumCard(
                            entry = third,
                            medal = "🥉 #3",
                            accent = Color(0xFFD97706),
                            modifier = Modifier.weight(1f),
                            elevated = false,
                        )
                    }
                }
            }

            // Daftar Peringkat #4 sampai #100
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rest.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
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
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                color = if (item.rank <= 10) Color(0xFFFBBF24) else Color(0xFF94A3B8),
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
                                        text = "${item.flag} ${item.name}",
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
                                    text = xy("${item.sessionsCount} sesi dimainkan", "${item.sessionsCount} sessions played"),
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
    medal: String,
    accent: Color,
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (elevated) Color(0xFF1C1810) else Color(0xFF141821),
        border = BorderStroke(if (elevated) 1.5.dp else 1.dp, accent.copy(alpha = if (elevated) 0.8f else 0.45f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = if (elevated) 14.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = medal,
                fontSize = if (elevated) 14.sp else 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accent,
            )
            Box(
                modifier = Modifier
                    .size(if (elevated) 44.dp else 36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.2f))
                    .border(1.dp, accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.name.take(1).uppercase(),
                    fontSize = if (elevated) 16.sp else 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
            Text(
                text = "${entry.flag} ${entry.name}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
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
                fontWeight = FontWeight.ExtraBold,
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
