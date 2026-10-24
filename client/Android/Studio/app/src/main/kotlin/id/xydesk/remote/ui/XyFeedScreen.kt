package id.xydesk.remote.ui

import android.util.Xml
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XyTopBar
import id.xydesk.remote.ui.components.XyIconPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Feed Berita (2026-10-24): menggantikan Feed komunitas/jokes atas keputusan
 * pengguna. Isi = berita teknologi & umum dari RSS resmi media Indonesia,
 * diambil langsung oleh aplikasi (tanpa server perantara).
 */
internal data class XyNewsItem(
    val title: String,
    val source: String,
    val link: String,
    val publishedAt: Long,
    val summary: String,
)

private val XY_NEWS_FEEDS = listOf(
    "ANTARA" to "https://www.antaranews.com/rss/terkini.xml",
    "Detik" to "https://rss.detik.com/index.php/detikcom",
    "CNBC Indonesia" to "https://www.cnbcindonesia.com/news/rss",
    "Kompas" to "https://rss.kompas.com/kompascom2023",
)

private suspend fun fetchXyNews(): List<XyNewsItem> = withContext(Dispatchers.IO) {
    val all = mutableListOf<XyNewsItem>()
    for ((source, url) in XY_NEWS_FEEDS) {
        runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "XyDeskRemote-Android")
            }
            if (conn.responseCode in 200..299) {
                val xml = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                all += parseXyRss(xml, source)
            }
            conn.disconnect()
        }
    }
    all.sortedByDescending { it.publishedAt }.take(60)
}

private fun parseXyRss(xml: String, source: String): List<XyNewsItem> {
    val items = mutableListOf<XyNewsItem>()
    val parser = Xml.newPullParser()
    parser.setInput(StringReader(xml))
    var event = parser.eventType
    var inItem = false
    var title = ""
    var link = ""
    var date = ""
    var desc = ""
    var tag = ""
    val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH),
    )
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                tag = parser.name.lowercase(Locale.ROOT)
                if (tag == "item" || tag == "entry") inItem = true
            }
            XmlPullParser.TEXT -> {
                if (inItem) {
                    val text = parser.text?.trim().orEmpty()
                    when (tag) {
                        "title" -> title = text
                        "link" -> if (link.isBlank()) link = text
                        "pubdate", "published", "updated" -> date = text
                        "description", "summary" -> desc = text
                    }
                }
            }
            XmlPullParser.END_TAG -> {
                val endTag = parser.name.lowercase(Locale.ROOT)
                if ((endTag == "item" || endTag == "entry") && inItem) {
                    val ts = dateFormats.firstNotNullOfOrNull { fmt ->
                        runCatching { fmt.parse(date)?.time }.getOrNull()
                    } ?: 0L
                    if (title.isNotBlank() && link.isNotBlank()) {
                        items += XyNewsItem(
                            title = title,
                            source = source,
                            link = link,
                            publishedAt = ts,
                            summary = desc
                                .replace(Regex("<[^>]+>"), " ")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(180),
                        )
                    }
                    inItem = false
                    title = ""; link = ""; date = ""; desc = ""
                }
            }
        }
        event = parser.next()
    }
    return items.take(25)
}

private fun newsTimeLabel(ts: Long): String {
    if (ts <= 0L) return ""
    val diffMin = ((System.currentTimeMillis() - ts) / 60_000L).coerceAtLeast(0L)
    return when {
        diffMin < 60 -> "$diffMin mnt lalu"
        diffMin < 60 * 24 -> "${diffMin / 60} jam lalu"
        else -> "${diffMin / (60 * 24)} hari lalu"
    }
}

@Composable
internal fun XyFeedScreen(
    onMenu: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<XyNewsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        failed = false
        val result = fetchXyNews()
        items = result
        failed = result.isEmpty()
        loading = false
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        XyTopBar(
            title = xy("Berita", "News"),
            actions = {
                XyIconPill(XyIcons.Menu, onMenu, flat = true, contentDescription = "Menu")
            },
        )
        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            xy("Memuat berita terbaru...", "Loading latest news..."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            failed -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(horizontal = 32.dp),
                    ) {
                        Text(
                            xy("Berita belum bisa dimuat. Periksa koneksi internet.", "News could not be loaded. Check your connection."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        XyPillButton(
                            text = xy("Coba lagi", "Retry"),
                            onClick = { scope.launch { load() } },
                            compact = true,
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.link }) { news ->
                        XyCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openInAppCustomTab(context, news.link) },
                            padding = 16.dp,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Text(
                                        news.source.uppercase(Locale.ROOT),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    newsTimeLabel(news.publishedAt),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                news.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (news.summary.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    news.summary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    item {
                        XyPillButton(
                            text = xy("Muat ulang berita", "Reload news"),
                            onClick = { scope.launch { load() } },
                            primary = false,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
