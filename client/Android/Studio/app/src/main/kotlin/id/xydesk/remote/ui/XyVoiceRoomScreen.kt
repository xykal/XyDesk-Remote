package id.xydesk.remote.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyPillButton

@Composable
fun XyVoiceRoomScreen(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { XyDiscordVoiceStageManager.ensureBackgroundPolling(context) }
    val joined by XyDiscordVoiceStageManager.joined.collectAsState()
    val micMuted by XyDiscordVoiceStageManager.micMuted.collectAsState()
    val deafened by XyDiscordVoiceStageManager.speakerDeafened.collectAsState()
    val sharingMyRdp by XyDiscordVoiceStageManager.sharingMyRdp.collectAsState()
    val participants by XyDiscordVoiceStageManager.participants.collectAsState()
    val liveScreen by XyDiscordVoiceStageManager.liveScreen.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> XyDiscordVoiceStageManager.joinVoiceParty(context, startWithMic = granted) }

    var showMicSettings by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF08080A))
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "XyVoice Room",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "${participants.size} peserta · ${if (joined) "Kamu di dalam" else "Belum gabung"}",
                            color = Color(0xFFA7A7AF),
                            fontSize = 12.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1E))
                            .border(1.dp, Color(0xFF232329), CircleShape)
                            .clickable { onDismiss() }
                            .padding(8.dp),
                    ) {
                        Icon(XyIcons.Close, contentDescription = "Tutup", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Live RDP preview if active
                if (liveScreen.active) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0E0E12))
                            .border(1.2.dp, Color(0xFFEF4444), RoundedCornerShape(16.dp))
                            .padding(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("🔴 LIVE ${liveScreen.streamerName}", color = Color(0xFFEF4444), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("${liveScreen.width}×${liveScreen.height}", color = Color(0xFF7B7B84), fontSize = 10.sp)
                            }
                            if (liveScreen.bitmap != null && !liveScreen.bitmap!!.isRecycled) {
                                androidx.compose.foundation.Image(
                                    bitmap = liveScreen.bitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 160.dp, max = 240.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF15151A)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("Menunggu stream...", color = Color(0xFF7B7B84), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Participants grid
                if (participants.isNotEmpty()) {
                    Text("Peserta Voice", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(participants) { p ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0E0E12))
                                    .border(
                                        width = if (p.speaking) 1.4.dp else 1.dp,
                                        color = when {
                                            p.sharingScreen -> Color(0xFFEF4444)
                                            p.speaking -> Color(0xFF22C55E)
                                            p.muted -> Color(0xFF3F3F46)
                                            else -> Color(0xFF232329)
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF15151A)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(p.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        when {
                                            p.sharingScreen -> "🔴 LIVE"
                                            p.muted -> "🔇 Muted"
                                            p.speaking -> "🎙️ Berbicara"
                                            else -> "🎧 Mendengarkan"
                                        },
                                        color = Color(0xFFA7A7AF),
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0E0E12))
                            .border(1.dp, Color(0xFF232329), RoundedCornerShape(14.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Belum ada peserta lain — jadi yang pertama gabung!", color = Color(0xFF7B7B84), fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }

                // Control buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!joined) {
                        XyPillButton(
                            text = "Gabung Voice Room",
                            onClick = {
                                val hasPerm = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) XyDiscordVoiceStageManager.joinVoiceParty(context, startWithMic = true)
                                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            icon = XyIcons.Volume,
                            primary = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (micMuted) Color(0xFF3F3F46) else Color(0xFF22C55E))
                                    .clickable { XyDiscordVoiceStageManager.toggleMic(context) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (micMuted) XyIcons.VolumeOff else XyIcons.Volume, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(if (micMuted) "Mic Mati" else "Mic Hidup", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (deafened) Color(0xFFEF4444) else Color(0xFF232329))
                                    .border(1.dp, Color(0xFF34343C), RoundedCornerShape(50))
                                    .clickable { XyDiscordVoiceStageManager.toggleDeafen() }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(XyIcons.Volume, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(if (deafened) "Speaker Off" else "Speaker On", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF1A1A1E))
                                    .border(1.dp, Color(0xFF232329), RoundedCornerShape(14.dp))
                                    .clickable { showMicSettings = true }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(XyIcons.Gear, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text("Pengaturan Mic", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (sharingMyRdp) Color(0xFFEF4444) else Color(0xFF4F46E5))
                                    .clickable { XyDiscordVoiceStageManager.toggleShareActiveRdpSession(context) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (sharingMyRdp) "Stop Share" else "Share Screen", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        XyPillButton(
                            text = "Disconnect Voice",
                            onClick = {
                                XyDiscordVoiceStageManager.leaveVoiceParty(context)
                                onDismiss()
                            },
                            icon = XyIcons.Power,
                            primary = false,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (showMicSettings) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF15151A))
                            .border(1.dp, Color(0xFF232329), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Pengaturan Mic", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("• Noise suppression otomatis aktif\n• Echo cancellation via VOICE_COMMUNICATION\n• Sensitivitas mic: bicara normal langsung terdeteksi\n• Izin mic: ${if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Diizinkan" else "Belum diizinkan"}", color = Color(0xFFA7A7AF), fontSize = 11.sp)
                            XyPillButton(text = "Tutup", onClick = { showMicSettings = false }, primary = false, compact = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}
