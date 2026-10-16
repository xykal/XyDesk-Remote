package id.xydesk.remote.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Ronde 47–48 — Chat Global (screen dari Feed).
 *
 * Fitur:
 * - Deteksi negara otomatis (bendera + kode negara tiap pengirim, mis. 🇮🇩 ID, 🇺🇸 US).
 * - Terjemahan dua arah otomatis di dalam **1 bubble yang dipisah garis**:
 *   bagian atas = teks asli pengirim, garis pemisah tipis, bagian bawah = hasil terjemahan.
 * - Push notifikasi OneSignal + balas langsung dari bar notifikasi ala WhatsApp.
 */

private const val CHAT_WS_URL = "wss://api.xydeskremote.biz.id/ws/chat"
private const val CHAT_PREFS = "xydesk.global.chat"
private const val KEY_NICKNAME = "nickname"

internal data class ChatMessage(
    val id: String,
    val name: String,
    val country: String,
    val flag: String,
    val text: String,
    val translated: String,
    val targetLang: String,
    val at: Long,
    val mine: Boolean,
)

internal fun chatNickname(context: Context): String {
    val prefs = context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)
    var name = prefs.getString(KEY_NICKNAME, null)
    if (name.isNullOrBlank()) {
        name = "Tamu-" + (1000 + (UUID.randomUUID().toString().hashCode() and 0x8fff))
        prefs.edit().putString(KEY_NICKNAME, name).apply()
    }
    return name
}

internal fun chatCountryCode(): String {
    val raw = Locale.getDefault().country.orEmpty().trim().uppercase(Locale.ROOT)
    return if (raw.length == 2 && raw.all { it in 'A'..'Z' }) raw else "ID"
}

internal fun countryCodeToFlag(countryCode: String): String {
    val cc = countryCode.trim().uppercase(Locale.ROOT)
    if (cc.length != 2 || !cc.all { it in 'A'..'Z' }) return "🌐"
    val first = Character.toChars(0x1F1E6 + (cc[0].code - 'A'.code))
    val second = Character.toChars(0x1F1E6 + (cc[1].code - 'A'.code))
    return String(first) + String(second)
}

@Composable
internal fun GlobalChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val nickname = remember { chatNickname(context) }
    val myCountry = remember { chatCountryCode() }
    val myFlag = remember(myCountry) { countryCodeToFlag(myCountry) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var presence by remember { mutableIntStateOf(0) }
    var typingName by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val main = remember { Handler(Looper.getMainLooper()) }
    var typingSentAt by remember { mutableStateOf(0L) }

    fun enrichTranslationIfNeeded(msg: ChatMessage) {
        Thread {
            val result = translateClientSide(msg.text, msg.country) ?: return@Thread
            if (result.first.isBlank()) return@Thread
            main.post {
                val idx = messages.indexOfFirst { it.id == msg.id }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(
                        translated = result.first,
                        targetLang = result.second,
                    )
                }
            }
        }.start()
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        id.xydesk.remote.push.ChatPushNotifier.setChatOpen(context, true)
        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
        var ws: WebSocket? = null
        var attempt = 0
        var closed = false

        fun connect(): WebSocket {
            val socket = client.newWebSocket(
                Request.Builder().url(CHAT_WS_URL).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        attempt = 0
                        main.post {
                            connected = true
                            Toast.makeText(context, "Terhubung ke chat global", Toast.LENGTH_SHORT).show()
                        }
                        webSocket.send(
                            JSONObject()
                                .put("type", "hello")
                                .put("name", nickname)
                                .put("country", myCountry)
                                .toString(),
                        )
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val data = try { JSONObject(text) } catch (e: Exception) { return }
                        main.post {
                            when (data.optString("type")) {
                                "history" -> {
                                    val arr: JSONArray = data.optJSONArray("messages") ?: return@post
                                    messages.clear()
                                    for (i in 0 until arr.length()) {
                                        val m = arr.optJSONObject(i) ?: continue
                                        val msgId = m.optString("id")
                                        val msgAt = m.optLong("at")
                                        val cc = m.optString("country", "ID").ifBlank { "ID" }.uppercase(Locale.ROOT)
                                        val flag = m.optString("flag").ifBlank { countryCodeToFlag(cc) }
                                        id.xydesk.remote.push.ChatPushNotifier.markSeen(msgId, msgAt, context)
                                        val item = ChatMessage(
                                            id = msgId,
                                            name = m.optString("name", "Tamu"),
                                            country = cc,
                                            flag = flag,
                                            text = m.optString("text"),
                                            translated = m.optString("translated", ""),
                                            targetLang = m.optString("target_lang", "EN").ifBlank { "EN" },
                                            at = msgAt,
                                            mine = m.optString("name") == nickname,
                                        )
                                        messages.add(item)
                                        enrichTranslationIfNeeded(item)
                                    }
                                }
                                "msg" -> {
                                    val from = data.optString("name", "Tamu")
                                    val msgId = data.optString("id")
                                    val msgAt = data.optLong("at")
                                    val cc = data.optString("country", "ID").ifBlank { "ID" }.uppercase(Locale.ROOT)
                                    val flag = data.optString("flag").ifBlank { countryCodeToFlag(cc) }
                                    id.xydesk.remote.push.ChatPushNotifier.markSeen(msgId, msgAt, context)
                                    messages.removeAll {
                                        it.id.startsWith("local-pending-") && it.text == data.optString("text") && it.mine
                                    }
                                    val item = ChatMessage(
                                        id = msgId,
                                        name = from,
                                        country = cc,
                                        flag = flag,
                                        text = data.optString("text"),
                                        translated = data.optString("translated", ""),
                                        targetLang = data.optString("target_lang", "EN").ifBlank { "EN" },
                                        at = msgAt,
                                        mine = from == nickname,
                                    )
                                    messages.add(item)
                                    enrichTranslationIfNeeded(item)
                                    typingName = null
                                }
                                "typing" -> {
                                    if (data.optBoolean("on")) {
                                        val tName = data.optString("name")
                                        val tFlag = data.optString("flag")
                                        typingName = if (tFlag.isNotBlank()) "$tFlag $tName" else tName
                                    } else {
                                        typingName = null
                                    }
                                }
                                "presence" -> presence = data.optInt("count")
                            }
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        main.post { connected = false }
                        if (closed) return
                        attempt += 1
                        val delay = (1000L shl (attempt - 1).coerceAtMost(4))
                        Thread {
                            try {
                                Thread.sleep(delay)
                            } catch (e: InterruptedException) {
                                return@Thread
                            }
                            if (!closed) ws = connect()
                        }.start()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        main.post { connected = false }
                    }
                },
            )
            activeSocket = socket
            return socket
        }

        ws = connect()
        onDispose {
            closed = true
            id.xydesk.remote.push.ChatPushNotifier.setChatOpen(context, false)
            try {
                ws?.close(1000, "bye")
            } catch (e: Exception) {
                // diabaikan
            }
        }
    }

    LaunchedEffect(messages.size, typingName) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        // Header
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹ Feed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Chat Global", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (connected) "$presence daring · $myFlag $nickname ($myCountry)"
                        else "menyambungkan…",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(Modifier.width(56.dp))
            }
        }

        // Daftar pesan
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Belum ada pesan. Jadilah yang pertama menyapa 👋",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }
            if (typingName != null) {
                item {
                    Text(
                        "$typingName sedang mengetik…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        // Komposer
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { value ->
                        val wasEmpty = draft.text.isEmpty()
                        draft = value
                        val now = System.currentTimeMillis()
                        if (value.text.isNotEmpty() && now - typingSentAt > 2500) {
                            typingSentAt = now
                            chatSocketSend(JSONObject().put("type", "typing").put("on", true).toString())
                        } else if (value.text.isEmpty() && !wasEmpty) {
                            chatSocketSend(JSONObject().put("type", "typing").put("on", false).toString())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Tulis pesan…") },
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                )
                Text(
                    "Kirim",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (draft.text.isNotBlank() && connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = draft.text.isNotBlank() && connected) {
                            val text = draft.text.trim()
                            if (text.isEmpty()) return@clickable
                            chatSocketSend(
                                JSONObject()
                                    .put("type", "msg")
                                    .put("text", text)
                                    .put("country", myCountry)
                                    .toString(),
                            )
                            chatSocketSend(JSONObject().put("type", "typing").put("on", false).toString())
                            val pendingItem = ChatMessage(
                                id = "local-pending-" + System.currentTimeMillis(),
                                name = nickname,
                                country = myCountry,
                                flag = myFlag,
                                text = text,
                                translated = "Menerjemahkan…",
                                targetLang = if (myCountry == "ID") "EN" else "ID",
                                at = System.currentTimeMillis(),
                                mine = true,
                            )
                            messages.add(pendingItem)
                            enrichTranslationIfNeeded(pendingItem.copy(translated = ""))
                            draft = TextFieldValue("")
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private var activeSocket: WebSocket? = null

private fun chatSocketSend(rawJson: String) {
    activeSocket?.send(rawJson)
}

/**
 * Terjemahan sisi klien (cadangan cepat jika riwayat lama belum punya field translated).
 */
private fun translateClientSide(text: String, country: String): Pair<String, String>? {
    val clean = text.trim()
    if (clean.isEmpty()) return null
    return runCatching {
        val toId = callGoogleTranslate(clean, "id") ?: return null
        val det = toId.second.lowercase(Locale.ROOT)
        val isId = det == "id" || det == "ms" ||
            (toId.first.equals(clean, ignoreCase = true) && country.equals("ID", ignoreCase = true))
        if (isId) {
            val toEn = callGoogleTranslate(clean, "en") ?: return null
            Pair(toEn.first, "EN")
        } else {
            Pair(toId.first, "ID")
        }
    }.getOrNull()
}

private fun callGoogleTranslate(text: String, targetLang: String): Pair<String, String>? {
    val q = URLEncoder.encode(text, "UTF-8")
    val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$q"
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 6_000
        readTimeout = 6_000
        setRequestProperty("User-Agent", "Mozilla/5.0")
    }
    if (conn.responseCode !in 200..299) {
        conn.disconnect()
        return null
    }
    val raw = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
    conn.disconnect()
    val root = JSONArray(raw)
    val parts = root.optJSONArray(0) ?: return null
    val sb = StringBuilder()
    for (i in 0 until parts.length()) {
        val seg = parts.optJSONArray(i) ?: continue
        val chunk = seg.optString(0, "")
        if (chunk.isNotEmpty()) sb.append(chunk)
    }
    val detected = root.optString(2, "auto")
    val out = sb.toString().trim()
    return if (out.isNotEmpty()) Pair(out, detected) else null
}

/**
 * Satu bubble memuat:
 * 1. Header: bendera negara + nama pengirim + kode negara (`🇮🇩 Tamu-1234 · ID`).
 * 2. Bagian atas: teks asli pengirim.
 * 3. Garis pemisah tipis di tengah bubble.
 * 4. Bagian bawah: hasil terjemahan otomatis (`🌐 EN · ...` atau `🌐 ID · ...`).
 */
@Composable
private fun ChatBubble(msg: ChatMessage) {
    val time = remember(msg.at) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.at))
    }
    val dividerColor = if (msg.mine) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
    }
    val transLabel = msg.targetLang.ifBlank { "EN" }.uppercase(Locale.ROOT)
    val transBody = msg.translated.ifBlank { msg.text }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 140.dp, max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (msg.mine) 16.dp else 4.dp,
                        bottomEnd = if (msg.mine) 4.dp else 16.dp,
                    ),
                )
                .background(
                    if (msg.mine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Header pengirim + bendera & kode negara
            Text(
                text = "${msg.flag} ${msg.name} · ${msg.country}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (msg.mine) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.height(3.dp))

            // Baris 1: Teks asli
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (msg.mine) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Garis pemisah di dalam 1 bubble
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(dividerColor),
            )
            Spacer(Modifier.height(5.dp))

            // Baris 2: Terjemahan otomatis di bawah garis
            Text(
                text = "🌐 $transLabel · $transBody",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = if (msg.mine) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.90f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
                },
            )

            Spacer(Modifier.height(2.dp))
            Text(
                text = time,
                style = MaterialTheme.typography.labelSmall,
                color = if (msg.mine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
