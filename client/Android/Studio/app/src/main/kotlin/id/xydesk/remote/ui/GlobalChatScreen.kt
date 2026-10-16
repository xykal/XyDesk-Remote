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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Ronde 47 — Chat Global (screen baru dari Feed).
 *
 * Chat publik realtime semua pengguna XyDesk Remote, digerakkan WebSocket
 * Durable Object `GlobalChat` di Cloudflare Worker (`/ws/chat`).
 *
 * Identitas visual: tema XyDesk (colorScheme Material3) — sengaja BUKAN
 * warna hijau khas aplikasi chat lain. Fitur: riwayat 100 pesan terakhir,
 * indikator "sedang mengetik", jumlah pengguna daring, auto-reconnect.
 *
 * Batasan saat ini: notifikasi hanya berlaku saat layar/app hidup
 * (tanpa FCM — butuh kunci Firebase dari pemilik untuk push sejati).
 */

private const val CHAT_WS_URL = "wss://api.xydeskremote.biz.id/ws/chat"
private const val CHAT_PREFS = "xydesk.global.chat"
private const val KEY_NICKNAME = "nickname"

internal data class ChatMessage(
    val id: String,
    val name: String,
    val text: String,
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

@Composable
internal fun GlobalChatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val nickname = remember { chatNickname(context) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var presence by remember { mutableIntStateOf(0) }
    var typingName by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val main = remember { Handler(Looper.getMainLooper()) }
    var typingSentAt by remember { mutableStateOf(0L) }

    androidx.compose.runtime.DisposableEffect(Unit) {
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
                        webSocket.send(JSONObject().put("type", "hello").put("name", nickname).toString())
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
                                        messages.add(
                                            ChatMessage(
                                                id = m.optString("id"),
                                                name = m.optString("name"),
                                                text = m.optString("text"),
                                                at = m.optLong("at"),
                                                mine = m.optString("name") == nickname,
                                            ),
                                        )
                                    }
                                }
                                "msg" -> {
                                    val from = data.optString("name")
                                    messages.removeAll { it.id.startsWith("local-pending-") && it.text == data.optString("text") && it.mine }
                                    messages.add(
                                        ChatMessage(
                                            id = data.optString("id"),
                                            name = from,
                                            text = data.optString("text"),
                                            at = data.optLong("at"),
                                            mine = from == nickname,
                                        ),
                                    )
                                    typingName = null
                                }
                                "typing" -> typingName = if (data.optBoolean("on")) data.optString("name") else null
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
                        if (connected) "$presence daring · kamu: $nickname"
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                        // Sinyal typing: sekali per 2,5 detik saat mulai mengetik,
                        // dan sinyal berhenti saat kolom dikosongkan.
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
                            chatSocketSend(JSONObject().put("type", "msg").put("text", text).toString())
                            chatSocketSend(JSONObject().put("type", "typing").put("on", false).toString())
                            messages.add(
                                ChatMessage(
                                    id = "local-pending-" + messages.size,
                                    name = nickname,
                                    text = text,
                                    at = System.currentTimeMillis(),
                                    mine = true,
                                ),
                            )
                            draft = TextFieldValue("")
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

// Socket aktif disimpan di companion-level sederhana agar komposer bisa mengirim.
private var activeSocket: WebSocket? = null

private fun chatSocketSend(text: String) {
    val payload = JSONObject().put("type", "msg").put("text", text).toString()
    activeSocket?.send(payload)
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val time = remember(msg.at) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.at))
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (msg.mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
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
            if (!msg.mine) {
                Text(
                    msg.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                msg.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (msg.mine) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                time,
                style = MaterialTheme.typography.labelSmall,
                color = if (msg.mine) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}
