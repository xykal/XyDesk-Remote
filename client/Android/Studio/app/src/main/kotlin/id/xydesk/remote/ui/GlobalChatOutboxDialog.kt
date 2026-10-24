package id.xydesk.remote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GlobalChatOutboxDialog(
    expectedLogin: String,
    expectedBinding: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by GlobalChatOutbox.revision.collectAsState()
    var snapshot by remember(expectedLogin, expectedBinding) {
        mutableStateOf(OutboxSnapshot(expectedBinding, emptyList(), null))
    }
    var busyId by remember { mutableStateOf<String?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(revision, expectedLogin, expectedBinding) {
        val loaded = withContext(Dispatchers.IO) { GlobalChatOutbox.snapshot(context) }
        if (loaded.binding == expectedBinding) {
            snapshot = loaded
        }
    }

    val pending = remember(snapshot.entries) {
        snapshot.entries.filter { it.state == "pending" }.sortedBy { it.createdAt }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141824),
        border = BorderStroke(1.dp, Color(0xFF2B354F)),
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Antrean Outbox Chat Global (${pending.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Text(
                        text = snapshot.issue ?: "Pesan yang tertunda akan dikirim dengan ID pesan asli tanpa duplikat.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        enabled = busyId == null,
                        onClick = {
                            busyId = "__check__"
                            scope.launch {
                                val msg = withContext(Dispatchers.IO) {
                                    runCatching {
                                        GlobalChatOutbox.checkAccount(context, expectedLogin, expectedBinding)
                                        "Identitas akun terverifikasi."
                                    }.getOrElse { it.message ?: "Verifikasi gagal." }
                                }
                                statusBanner = msg
                                busyId = null
                            }
                        },
                    ) {
                        Text("Verifikasi", fontSize = 11.sp, color = Color(0xFFA78BFA))
                    }
                    TextButton(onClick = onClose) {
                        Text("Tutup", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
                    }
                }
            }

            if (!statusBanner.isNullOrBlank()) {
                Text(
                    text = statusBanner.orEmpty(),
                    fontSize = 11.sp,
                    color = Color(0xFFA78BFA),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (pending.isEmpty()) {
                Text(
                    text = "Tidak ada pesan yang tertahan di antrean lokal.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                )
            } else {
                pending.forEach { entry ->
                    val retryState = ChatOutboxControlPolicy.retryState(
                        entry,
                        expectedBinding,
                        snapshot.verifiedOwner,
                        now,
                    )
                    val preview = remember(entry.id, entry.payload, entry.state) {
                        runCatching { ChatOutboxControlPolicy.preview(entry) }
                            .getOrElse { OutboxTextPreview("(Pesan terenkripsi)", "", false) }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "ID ${entry.id.takeLast(8)}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF64748B),
                            )
                            Text(
                                text = "${retryState.name} · percobaan #${entry.attempts}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (retryState == OutboxRetryState.READY) Color(0xFF10B981) else Color(0xFFF59E0B),
                            )
                        }
                        Text(
                            text = preview.text.ifBlank { "[Lampiran ${preview.media.ifBlank { "media" }}]" },
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 2,
                        )
                        if (entry.issue.isNotBlank()) {
                            Text(
                                text = "Status: ${entry.issue}",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            if (!preview.viewOnce && preview.text.isNotBlank()) {
                                OutlinedButton(
                                    enabled = busyId == null,
                                    onClick = {
                                        scope.launch {
                                            val copied = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    GlobalChatOutbox.pendingText(
                                                        context,
                                                        entry.id,
                                                        expectedLogin,
                                                        expectedBinding,
                                                    )
                                                }.getOrNull()
                                            }
                                            if (!copied.isNullOrBlank()) {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                cm?.setPrimaryClip(ClipData.newPlainText("xydesk_outbox", copied))
                                                statusBanner = "Teks pesan disalin ke clipboard."
                                            }
                                        }
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                ) {
                                    Text("Salin", fontSize = 10.sp, color = Color.White)
                                }
                            }
                            OutlinedButton(
                                enabled = busyId == null,
                                onClick = {
                                    busyId = entry.id
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                GlobalChatOutbox.cancelPending(
                                                    context,
                                                    entry.id,
                                                    expectedLogin,
                                                    expectedBinding,
                                                )
                                            }
                                        }
                                        busyId = null
                                    }
                                },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) {
                                Text("Batalkan", fontSize = 10.sp, color = Color(0xFFD946EF))
                            }
                            Button(
                                enabled = busyId == null && retryState in setOf(OutboxRetryState.READY, OutboxRetryState.NEEDS_LOGIN),
                                onClick = {
                                    busyId = entry.id
                                    scope.launch {
                                        val msg = withContext(Dispatchers.IO) {
                                            runCatching {
                                                GlobalChatOutbox.retryPending(
                                                    context,
                                                    entry.id,
                                                    expectedLogin,
                                                    expectedBinding,
                                                )
                                                "Mencoba mengirim ulang..."
                                            }.getOrElse { it.message ?: "Gagal mengirim ulang." }
                                        }
                                        statusBanner = msg
                                        busyId = null
                                    }
                                },
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFA78BFA),
                                    contentColor = Color.Black,
                                ),
                            ) {
                                Text("Kirim Ulang", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
