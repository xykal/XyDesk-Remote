package id.xydesk.remote.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalDensity
import id.xydesk.remote.ui.theme.XySpacing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.telephony.TelephonyManager
import android.util.Base64
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import id.xydesk.remote.ui.components.XyEmptyState
import id.xydesk.remote.ui.components.XyLoadingSkeletonCard
import id.xydesk.remote.ui.components.XyLoadingSpinnerState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import id.xydesk.remote.R
import id.xydesk.remote.ui.components.XyIcons
import id.xydesk.remote.ui.components.XyIconPill
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.components.XyTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

// Komponen presentasi chat; ekstraksi mekanis tanpa perubahan perilaku.
@Composable
internal fun FullScreenChatMediaViewer(
    msg: ChatMessage,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var zoomScale by remember(msg.id) { mutableFloatStateOf(1f) }
    var panOffsetX by remember(msg.id) { mutableFloatStateOf(0f) }
    var panOffsetY by remember(msg.id) { mutableFloatStateOf(0f) }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val dismissNow by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    DisposableEffect(msg.id, msg.viewOnce, lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (msg.viewOnce && event == androidx.lifecycle.Lifecycle.Event.ON_STOP) dismissNow()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    DisposableEffect(msg.id) {
        XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue += 1
        onDispose {
            XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue =
                (XyVideoPlaybackCoordinator.fullscreenOpenCount.intValue - 1).coerceAtLeast(0)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            securePolicy = if (msg.viewOnce) androidx.compose.ui.window.SecureFlagPolicy.SecureOn else androidx.compose.ui.window.SecureFlagPolicy.Inherit,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (msg.attachmentType == "video" && msg.attachmentData.isNotBlank()) {
                val prepared = rememberPreparedChatVideo(context, msg.id, msg.attachmentData, msg.viewOnce)
                val videoFile = prepared.video?.file
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(msg.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                zoomScale = nextScale
                                if (nextScale > 1.01f) {
                                    panOffsetX += pan.x
                                    panOffsetY += pan.y
                                } else {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffsetX
                            translationY = panOffsetY
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (videoFile != null) {
                        AndroidView(
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    val mc = MediaController(ctx)
                                    mc.setAnchorView(this)
                                    setMediaController(mc)
                                    setVideoPath(videoFile.absolutePath)
                                    setOnPreparedListener { mp ->
                                        mp.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = if (prepared.loading) xyNow("Menyiapkan video…", "Preparing video…") else xyNow("Gagal memuat berkas video.", "Unable to load video."),
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            } else {
                val bmp = rememberChatBitmap(msg.id, msg.attachmentData, cacheResult = !msg.viewOnce)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(msg.id) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.25f) {
                                        zoomScale = 1f
                                        panOffsetX = 0f
                                        panOffsetY = 0f
                                    } else {
                                        zoomScale = 2.75f
                                    }
                                },
                            )
                        }
                        .pointerInput(msg.id) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val nextScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                zoomScale = nextScale
                                if (nextScale > 1.01f) {
                                    panOffsetX = (panOffsetX + pan.x).coerceIn(-1400f, 1400f)
                                    panOffsetY = (panOffsetY + pan.y).coerceIn(-1800f, 1800f)
                                } else {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = msg.attachmentName.ifBlank { "Foto Layar Penuh" },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffsetX
                                    translationY = panOffsetY
                                },
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            // Header Liquid Glass Atas (Tutup, Info Pengirim, Zoom Reset, Simpan ke Galeri, Bagikan)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black.copy(alpha = 0.42f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(0.8.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = XyIcons.ChevronLeft,
                            contentDescription = "Tutup Pratinjau",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = msg.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (msg.viewOnce) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "1× SEKALI LIHAT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF09090B),
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (msg.attachmentType == "video") "Video Layar Penuh · Cubit untuk Zoom" else "Cubit 2 Jari / Ketuk 2× untuk Zoom (${(zoomScale * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.5.sp,
                            color = Color(0xFFD4D4D8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(XySpacing.small),
                ) {
                    if (zoomScale > 1.05f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(50))
                                .clickable {
                                    zoomScale = 1f
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Reset ${(zoomScale * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                        }
                    }
                    if (!msg.viewOnce) {
                        // Tombol Simpan ke Galeri Perangkat
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White)
                                .clickable { saveChatMediaToGallery(context, msg) }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = XyIcons.Download,
                                contentDescription = "Simpan ke Galeri",
                                tint = Color(0xFF09090B),
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = "Simpan",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF09090B),
                            )
                        }
                        // Tombol Bagikan Media
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.28f), CircleShape)
                                .clickable { saveOrShareChatAttachment(context, msg) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = XyIcons.Share,
                                contentDescription = "Bagikan",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Caption Bawah (bila ada teks pesan pada foto/video)
            if (msg.text.isNotBlank() && !msg.text.startsWith("[Foto") && !msg.text.startsWith("[Video")) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.88f),
                                ),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ContextMenuActionRow(
    label: String,
    icon: ImageVector? = null,
    tint: Color = Color.White,
    customIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
        if (customIcon != null) {
            customIcon()
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Bubble Pesan Chat Global gaya WhatsApp Modern:
 * - **TANPA BORDER** untuk chat pengguna biasa.
 * - **Hitam-Putih Monokrom** untuk chat biasa (`#F4F4F6` dengan teks `#09090B` untuk pesanku,
 *   `#1A1A20` dengan teks `#F4F4F5` untuk pesan orang lain) sesuai tema utama XyDesk.
 * - Khusus **Founder (`@xykal`)**: menggunakan `FounderAnimatedBubbleBox` + `XyFounderBadge`
 *   beranimasi penuh dengan sapuan Liquid Gold & partikel bintang.
 * - Menampilkan **Foto Profil (Avatar)**, **Nama Jelas + `@github`**, **Kode Negara**,
 *   serta **Centang 1 / Centang 2 Vektor (`ChatVectorDeliveryTicks`)**.
 */
private val CHAT_TIME_FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
}

@Composable
internal fun MessageBubble(
    msg: ChatMessage,
    reaction: String?,
    isPinned: Boolean = false,
    viewedOnce: Boolean = false,
    liquidGlassMode: Boolean = false,
    isCompactOverlay: Boolean = false,
    bubbleStyleMode: String = "monochrome",
    bubbleShapePreset: String = "TAIL_CLASSIC",
    floatingElementPreset: String = "NONE",
    autoDownloadAllowed: Boolean = true,
    autoplayVideoEnabled: Boolean = true,
    favoriteStickerKeys: Set<String> = emptySet(),
    onToggleFavoriteSticker: (String) -> Unit = {},
    onManualDownloadMedia: () -> Unit = {},
    onReply: () -> Unit,
    onLongPress: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val context = LocalContext.current
    val time = remember(msg.at) {
        CHAT_TIME_FORMATTER.get()?.format(Date(msg.at)) ?: ""
    }
    val founderMsg = msg.isFounder || isFounderAccount(msg.name) || isFounderAccount(msg.githubLogin)
    val isAnnouncementMsg = msg.isAnnouncement || (founderMsg && isPinned)
    val isPureSticker = msg.attachmentType == "sticker"
    val isDarkUi = id.xydesk.remote.ui.theme.xyDark() || liquidGlassMode
    val isLightBubble = when {
        founderMsg || isAnnouncementMsg -> false
        bubbleStyleMode == "glass" || bubbleStyleMode == "amber" -> false
        isDarkUi -> msg.mine
        else -> !msg.mine
    }

    val density = LocalDensity.current
    val replyThresholdPx = with(density) { 56.dp.toPx() }
    val maxSwipePx = with(density) { 110.dp.toPx() }

    // Swipe-to-Reply State
    var swipeOffsetX by remember(msg.id) { mutableFloatStateOf(0f) }
    var swipeTriggered by remember(msg.id) { mutableStateOf(false) }

    val primaryTextColor = when {
        founderMsg || isAnnouncementMsg -> Color(0xFFF8F8F8)
        isLightBubble -> Color(0xFF0A0A0E)
        else -> Color(0xFFF4F4F6)
    }
    val secondaryTextColor = when {
        founderMsg || isAnnouncementMsg -> Color(0xFFE0E0E0).copy(alpha = 0.90f)
        isLightBubble -> Color(0xFF4A4A52)
        else -> Color(0xFFA8A8B2)
    }
    val dividerColor = when {
        founderMsg || isAnnouncementMsg -> Color(0xFF808080).copy(alpha = 0.36f)
        isLightBubble -> Color(0xFF09090B).copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.10f)
    }

    val bubbleShape = remember(bubbleShapePreset, msg.mine, bubbleStyleMode, isCompactOverlay) {
        resolveCustomBubbleShape(
            shapePreset = if (isCompactOverlay && bubbleShapePreset == "TAIL_CLASSIC") "TAIL_CLASSIC" else bubbleShapePreset,
            isMine = msg.mine,
            compact = isCompactOverlay,
        )
    }

    val transLabel = msg.targetLang.ifBlank { if (msg.country == "ID") "EN" else "ID" }
    val transBody = msg.translated.trim()
    val isAudioAttachment = remember(msg.attachmentType, msg.attachmentMime, msg.attachmentName) {
        msg.attachmentType == "audio" ||
            msg.attachmentMime.startsWith("audio/", ignoreCase = true) ||
            msg.attachmentName.lowercase(Locale.ROOT).let { n ->
                n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".wav") ||
                    n.endsWith(".ogg") || n.endsWith(".flac") || n.endsWith(".aac") || n.endsWith(".opus")
            }
    }
    val hasMediaAttachment = msg.attachmentType == "image" || msg.attachmentType == "video" || msg.attachmentType == "file" || isAudioAttachment
    val isFramelessMedia = (msg.attachmentType == "image" || msg.attachmentType == "video" || isAudioAttachment) &&
        msg.text.isBlank() && msg.replyToName.isBlank() && !isAnnouncementMsg
    val roleRev = XySpecialRoleRegistry.roleRevision
    val specialRole = remember(msg.name, msg.githubLogin, roleRev) {
        resolveSpecialUserRole(msg.name, msg.githubLogin, context)
    }
    val detectedLinkUrl = remember(msg.id, msg.text) { extractFirstUrl(msg.text) }
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat()
    val maxBubbleWidth = remember(screenWidthDp, isCompactOverlay) {
        chatBubbleMaxWidth(screenWidthDp, isCompactOverlay)
            .coerceAtMost(if (isCompactOverlay) 204f else 286f)
            .dp
    }
    val minBubbleWidth = (when {
        isCompactOverlay -> if (hasMediaAttachment) 144.dp else 64.dp
        hasMediaAttachment -> 184.dp
        isPureSticker -> 92.dp
        else -> 68.dp
    }).coerceAtMost(maxBubbleWidth)

        Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isCompactOverlay) 8.dp else 16.dp)
            .offset { IntOffset(swipeOffsetX.roundToInt(), 0) }
            .pointerInput(msg.id, replyThresholdPx, maxSwipePx) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(swipeOffsetX) >= replyThresholdPx) {
                            onReply()
                        }
                        swipeOffsetX = 0f
                        swipeTriggered = false
                    },
                    onDragCancel = {
                        swipeOffsetX = 0f
                        swipeTriggered = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeOffsetX = (swipeOffsetX + dragAmount).coerceIn(-maxSwipePx, maxSwipePx)
                    },
                )
            },
        horizontalArrangement = if (msg.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!msg.mine) {
            ChatSenderAvatar(
                name = msg.name,
                githubLogin = msg.githubLogin,
                avatarUrl = msg.avatarUrl,
                isFounder = founderMsg,
                compact = isCompactOverlay,
            )
            Spacer(Modifier.width(if (isCompactOverlay) 4.dp else 6.dp))
        }

        Column(
            horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(min = minBubbleWidth, max = maxBubbleWidth),
        ) {
            val gestureMod = Modifier.pointerInput(msg.id) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                )
            }

            // STIKER MURNI: Dirender TANPA Bubble Card & TANPA Label + Tombol Favorit untuk Siapa Saja
            if (isPureSticker) {
                val appContext = LocalContext.current
                val isFavSticker = remember(msg.attachmentData, favoriteStickerKeys) {
                    when {
                        msg.attachmentData.startsWith("sticker:") ->
                            "preset:${msg.attachmentData.removePrefix("sticker:")}" in favoriteStickerKeys
                        msg.attachmentData.startsWith("sticker_crypto15:") -> {
                            val rest = msg.attachmentData.removePrefix("sticker_crypto15:")
                            val fname = rest.substringBefore(':')
                            "custom:$fname" in favoriteStickerKeys
                        }
                        else -> false
                    }
                }
                Column(
                    modifier = gestureMod.padding(vertical = 2.dp),
                    horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(XySpacing.compact),
                    ) {
                        Text(
                            text = msg.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (founderMsg) Color(0xFFD7D7D7) else Color(0xFFD4D4D8),
                        )
                        XyUserRoleBadges(name = msg.name, githubLogin = msg.githubLogin, compact = true)
                    }
                    Spacer(Modifier.height(4.dp))
                    if (msg.attachmentData.startsWith("sticker_crypto15:")) {
                        val customDecoded = rememberChatSticker(appContext, msg.id, msg.attachmentData)
                        if (customDecoded != null) {
                            Image(
                                bitmap = customDecoded.second.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(142.dp),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    } else {
                        val presetId = msg.attachmentData.removePrefix("sticker:")
                        val preset = XY_STICKERS.firstOrNull { it.id == presetId } ?: XY_STICKERS.first()
                        Image(
                            painter = painterResource(preset.drawableRes),
                            contentDescription = null,
                            modifier = Modifier.size(142.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // Bar Bawah Stiker: Jam + Tombol Favoritkan Stiker (Bisa Dipakai Siapa Saja)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(XySpacing.small),
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFF1D1D1D))
                                .clickable { onToggleFavoriteSticker(msg.attachmentData) }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(XySpacing.compact),
                        ) {
                            Icon(
                                imageVector = if (isFavSticker) XyIcons.StarFilled else XyIcons.Star,
                                contentDescription = null,
                                tint = if (isFavSticker) Color(0xFFBFBFBF) else Color(0xFFE7E7E7),
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = if (isFavSticker) "Favorit" else "+Favorit",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFavSticker) Color(0xFFBFBFBF) else Color(0xFFE7E7E7),
                            )
                        }
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color(0xFFA2A2A2),
                        )
                    }
                }
            } else if (isFramelessMedia) {
                // =========================================================
                // MEDIA TANPA BUBBLE / CARD (GAYA WHATSAPP iOS FRAMELESS)
                // Sesuai rasio asli (aspect ratio) + cukup Rounded 16.dp
                // =========================================================
                Column(
                    modifier = gestureMod
                        .widthIn(min = 165.dp, max = maxBubbleWidth)
                        .padding(vertical = 2.dp),
                    horizontalAlignment = if (msg.mine) Alignment.End else Alignment.Start,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = msg.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (founderMsg) Color(0xFFE0E0E0) else Color(0xFFE4E4E7),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (msg.githubLogin.isNotBlank() && !msg.githubLogin.equals(msg.name, ignoreCase = true)) {
                            Text(
                                text = "@${msg.githubLogin}",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = Color(0xFFA2A2A2),
                                maxLines = 1,
                            )
                        }
                        XyUserRoleBadges(
                            name = msg.name,
                            githubLogin = msg.githubLogin,
                            compact = true,
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable { onReply() },
                            contentAlignment = Alignment.Center,
                        ) {
                            ChatVectorReplyIcon(size = 12.dp, tint = Color(0xFFA2A2A2))
                        }
                    }

                    ChatMediaAttachmentBox(
                        msg = msg,
                        isAudioAttachment = isAudioAttachment,
                        autoDownloadAllowed = autoDownloadAllowed,
                        viewedOnce = viewedOnce,
                        autoplayVideoEnabled = autoplayVideoEnabled,
                        floatingTime = time,
                        onManualDownloadMedia = onManualDownloadMedia,
                        onOpenMedia = onOpenMedia,
                        onOpenFile = onOpenFile,
                    )
                }
            } else {
                val padH = if (isCompactOverlay) 8.5.dp else 11.dp
                val padV = if (isCompactOverlay) 5.dp else 6.5.dp
                val topTenRanks by LeaderboardPodium.topTen.collectAsState()
                val senderTopRank = remember(msg.githubLogin, msg.name, topTenRanks) {
                    val gh = normalizedChatAccount(msg.githubLogin)
                    val nm = normalizedChatAccount(msg.name)
                    (if (gh.isNotEmpty()) topTenRanks[gh] else null)
                        ?: (if (nm.isNotEmpty()) topTenRanks[nm] else null)
                }

                val bubbleContent: @Composable () -> Unit = {
                    Column(
                        modifier = Modifier
                            .width(IntrinsicSize.Max)
                            .widthIn(min = minBubbleWidth, max = maxBubbleWidth)
                            .padding(
                                horizontal = padH,
                                vertical = padV,
                            ),
                    ) {
                        // Status lipat/tampilkan kotak balasan (per pesan)
                        val replyExpanded by remember(msg.id) { mutableStateOf(true) }
                        // Pita Khusus Pengumuman Admin @xykal yang Otomatis Tersemat
                        if (isAnnouncementMsg) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF808080).copy(alpha = 0.24f))
                                    .border(0.8.dp, Color(0xFFA4A4A4), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(XySpacing.compact),
                                ) {
                                    Icon(
                                        imageVector = XyIcons.Pin,
                                        contentDescription = null,
                                        tint = Color(0xFFE0E0E0),
                                        modifier = Modifier.size(10.dp),
                                    )
                                    Text(
                                        text = "PENGUMUMAN",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFE0E0E0),
                                    )
                                }
                            }
                        }

                        // Header Pengirim: Hanya untuk pesan orang lain (atau Founder/Announcement) seperti WhatsApp
                        if (!msg.mine || founderMsg || isAnnouncementMsg) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.weight(1f, fill = false),
                                ) {
                                    Text(
                                        text = msg.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (founderMsg) Color(0xFFE0E0E0) else primaryTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    XyUserRoleBadges(
                                        name = msg.name,
                                        githubLogin = msg.githubLogin,
                                        compact = true,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (isLightBubble) Color(0xFF09090B).copy(alpha = 0.08f)
                                                else Color.White.copy(alpha = 0.10f),
                                            )
                                            .padding(horizontal = 5.dp, vertical = 0.5.dp),
                                    ) {
                                        Text(
                                            text = msg.country,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = secondaryTextColor,
                                        )
                                    }
                                    if (isPinned) {
                                        ChatVectorPinIcon(
                                            size = 11.dp,
                                            tint = if (founderMsg) Color(0xFFC7C7C7) else secondaryTextColor,
                                        )
                                    }
                                }
                                if (!msg.mine) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .clickable { onReply() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        ChatVectorReplyIcon(
                                            size = 11.dp,
                                            tint = secondaryTextColor,
                                        )
                                    }
                                }
                            }
                        }

                        // Kotak Kutipan Balasan (Quoted Reply ala WhatsApp) — avatar pengirim + > kutip + lipat/tampilkan
                        if (msg.replyToName.isNotBlank() || msg.replyToText.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            AnimatedVisibility(
                                visible = replyExpanded,
                                enter = fadeIn(),
                                exit = fadeOut(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isLightBubble) Color(0xFF09090B).copy(alpha = 0.06f)
                                            else Color.Black.copy(alpha = 0.24f),
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Avatar siapa yang balas
                                    ChatSenderAvatar(
                                        name = msg.name,
                                        githubLogin = msg.githubLogin,
                                        avatarUrl = msg.avatarUrl,
                                        isFounder = founderMsg,
                                        compact = true,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.Start,
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = msg.name,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (founderMsg) Color(0xFFE0E0E0) else primaryTextColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            XyUserRoleBadges(
                                                name = msg.name,
                                                githubLogin = msg.githubLogin,
                                                compact = true,
                                            )
                                        }
                                        // > Kutip nama org yang dibalas
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .clip(RoundedCornerShape(1.dp))
                                                    .background(primaryTextColor.copy(alpha = 0.5f)),
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = msg.replyToName.ifBlank { "Pesan" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = primaryTextColor.copy(alpha = 0.85f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = msg.replyToText.ifBlank { "Media / Stiker" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    color = secondaryTextColor.copy(alpha = 0.8f),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    // Tombol lipat/tampilkan balasan
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(if (isLightBubble) Color(0xFF09090B).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.10f))
                                            .clickable { replyExpanded = !replyExpanded },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = if (replyExpanded) XyIcons.ChevronDown else XyIcons.ChevronUp,
                                            contentDescription = null,
                                            tint = secondaryTextColor,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Lampiran Foto / Video / Audio / Berkas sesuai rasio asli
                        if (hasMediaAttachment) {
                            Spacer(Modifier.height(4.dp))
                            if (msg.attachmentData.isBlank()) {
                                Text(xyNow("Media belum tersedia offline", "Media not available offline"),
                                    style = MaterialTheme.typography.bodySmall, color = primaryTextColor)
                            } else ChatMediaAttachmentBox(
                                msg = msg,
                                isAudioAttachment = isAudioAttachment,
                                autoDownloadAllowed = autoDownloadAllowed,
                                viewedOnce = viewedOnce,
                                autoplayVideoEnabled = autoplayVideoEnabled,
                                floatingTime = null,
                                onManualDownloadMedia = onManualDownloadMedia,
                                onOpenMedia = onOpenMedia,
                                onOpenFile = onOpenFile,
                            )
                        }

                        if (msg.deleted) Text("Pesan ini dihapus", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            style = MaterialTheme.typography.bodySmall, color = secondaryTextColor)
                        if (msg.mutationPending || msg.mutationError.isNotBlank()) Text(
                            if (msg.mutationPending) "Menyinkronkan perubahan…" else msg.mutationError,
                            style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)
                        if (msg.editedAt > 0 && !msg.deleted) Text("diedit", style = MaterialTheme.typography.labelSmall, color = secondaryTextColor)

                        // Teks Pesan (Semua link/URL berwarna biru cerah & bisa diklik!)
                        if (!msg.deleted && msg.text.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            val parts = remember(msg.text) { splitChatCode(msg.text) }
                            for (part in parts) {
                                if (part.language != null) {
                                    ChatCodeBlock(part.text, part.language)
                                } else {
                            XyClickableLinkText(
                                text = part.text,
                                color = primaryTextColor,
                                linkColor = if (isLightBubble) Color(0xFF606060) else Color(0xFF9C9C9C),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = if (isCompactOverlay) 12.sp else 13.sp,
                                    lineHeight = if (isCompactOverlay) 15.5.sp else 17.sp,
                                ),
                                onLongPress = onLongPress,
                            )
                                }
                            }
                            if (transBody.isNotBlank() && !transBody.equals(msg.text, ignoreCase = true)) {
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(0.75.dp)
                                        .background(dividerColor),
                                )
                                Spacer(Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(XySpacing.compact),
                                ) {
                                    Icon(
                                        imageVector = XyIcons.Globe,
                                        contentDescription = null,
                                        tint = if (founderMsg) Color(0xFFC7C7C7) else secondaryTextColor,
                                        modifier = Modifier.size(10.dp),
                                    )
                                    XyClickableLinkText(
                                        text = "[$transLabel] · $transBody",
                                        color = if (founderMsg) Color(0xFFE0E0E0).copy(alpha = 0.94f) else secondaryTextColor,
                                        linkColor = if (isLightBubble) Color(0xFF606060) else Color(0xFF9C9C9C),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = if (isCompactOverlay) 10.5.sp else 11.5.sp,
                                            fontStyle = FontStyle.Italic,
                                        ),
                                        onLongPress = onLongPress,
                                    )
                                }
                            }
                        }

                        // Kartu OG Banner / Link Preview untuk Semua Link di Chat Global
                        if (!detectedLinkUrl.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            XyOpenGraphPreviewCard(url = detectedLinkUrl)
                        }

                        // Baris Bawah Ringkas: Jam + Vektor Centang 1 / 2
                        Spacer(Modifier.height(1.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = secondaryTextColor,
                            )
                            if (msg.mine) {
                                ChatVectorDeliveryTicks(
                                    ticks = msg.deliveryTicks,
                                    tint = when {
                                        founderMsg -> Color(0xFFC7C7C7)
                                        isLightBubble -> Color(0xFF18181B)
                                        else -> Color(0xFFE4E4E7)
                                    },
                                )
                            }
                        }
                    }
                }

                // Animasi fase untuk elemen melayang AI di dalam bubble (hanya jika diaktifkan di Pengaturan Chat)
                val activeFloatingPreset = floatingElementPreset
                val floatAnimPhase = if (activeFloatingPreset != "NONE" && id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled.current) {
                    val infFloat = rememberInfiniteTransition(label = "bubbleFloatElem")
                    val p by infFloat.animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(4200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "bubbleFloatPhase",
                    )
                    p
                } else 0f

                // Kontainer Bubble sesuai Role (Founder Lightning Storm / XYTEAM Cyber / TESTER Matrix / Reguler)
                if (founderMsg || isAnnouncementMsg) {
                    FounderAnimatedBubbleBox(
                        shape = bubbleShape,
                        modifier = gestureMod,
                    ) {
                        if (activeFloatingPreset != "NONE") {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawBubbleFloatingElements(
                                    preset = activeFloatingPreset,
                                    phase = floatAnimPhase,
                                    accentColor = Color(0xFFC7C7C7),
                                )
                            }
                        }
                        bubbleContent()
                    }
                } else if (specialRole != null) {
                    XyRoleAnimatedBubbleBox(
                        role = specialRole,
                        shape = bubbleShape,
                        modifier = gestureMod,
                    ) {
                        if (activeFloatingPreset != "NONE") {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawBubbleFloatingElements(
                                    preset = activeFloatingPreset,
                                    phase = floatAnimPhase,
                                    accentColor = if (specialRole == "XYTEAM") Color(0xFF9C9C9C) else Color(0xFF9D9D9D),
                                )
                            }
                        }
                        bubbleContent()
                    }
                } else {
                    val bubbleBgColor = when {
                        bubbleStyleMode == "glass" -> if (msg.mine) Color(0xFF2F2F2F) else Color(0xFF191919)
                        bubbleStyleMode == "amber" -> if (msg.mine) Color(0xFF232323) else Color(0xFF181613)
                        isDarkUi -> if (msg.mine) Color(0xFFF2F2F5) else Color(0xFF1C1C22)
                        else -> if (msg.mine) Color(0xFF18181D) else Color(0xFFEAECEF)
                    }
                    val topRankBorderColor = when (senderTopRank) {
                        1 -> Color(0xFFBFBFBF)
                        2 -> Color(0xFFD3D3D3)
                        3 -> Color(0xFFA8A8A8)
                        in 4..10 -> Color(0xFF959595).copy(alpha = 0.72f)
                        else -> null
                    }
                    Box(
                        modifier = gestureMod
                            .clip(bubbleShape)
                            .background(
                                color = bubbleBgColor,
                                shape = bubbleShape,
                            )
                            .then(
                                if (topRankBorderColor != null) {
                                    Modifier.border(
                                        width = if (senderTopRank == 1) 1.3.dp else 0.9.dp,
                                        color = topRankBorderColor,
                                        shape = bubbleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (activeFloatingPreset != "NONE") {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                drawBubbleFloatingElements(
                                    preset = activeFloatingPreset,
                                    phase = floatAnimPhase,
                                    accentColor = if (isLightBubble) Color(0xFF656565) else Color(0xFFA4A4A4),
                                )
                            }
                        }
                        bubbleContent()
                    }
                }
            }

            // Pill Reaksi Visual (menampilkan ikon vektor berwarna + jumlah reaksi, bukan sekadar tulisan)
            if (!reaction.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(XySpacing.compact),
                ) {
                    ChatReactionVisualIcon(tag = reaction, size = 15.dp)
                    Text(
                        text = "1",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChatMediaAttachmentBox(
    msg: ChatMessage,
    isAudioAttachment: Boolean,
    autoDownloadAllowed: Boolean,
    viewedOnce: Boolean,
    autoplayVideoEnabled: Boolean,
    floatingTime: String?,
    onManualDownloadMedia: () -> Unit,
    onOpenMedia: () -> Unit,
    onOpenFile: () -> Unit,
) {
    val context = LocalContext.current
    val mediaShape = RoundedCornerShape(16.dp)

    @Composable
    fun FloatingTimePill(modifier: Modifier = Modifier) {
        if (floatingTime == null) return
        Row(
            modifier = modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 7.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(XySpacing.compact),
        ) {
            Text(
                text = floatingTime,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            if (msg.mine) {
                ChatVectorDeliveryTicks(
                    ticks = msg.deliveryTicks,
                    tint = Color.White,
                )
            }
        }
    }

    if ((msg.attachmentType == "image" || msg.attachmentType == "video") && msg.viewOnce) {
        // =========================================================
        // 1× SEKALI LIHAT (FROSTED SPOILER SHIMMER CARD)
        // =========================================================
        val inf = if (!viewedOnce && id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled.current) rememberInfiniteTransition(label = "viewOnceShimmer") else null
        val shimmerPhase = if (inf != null) {
            val animated by inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "viewOnceShimmerPhase",
        )
            animated
        } else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (viewedOnce) 72.dp else 138.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = if (viewedOnce) {
                            listOf(Color(0xFF141418), Color(0xFF191919))
                        } else {
                            listOf(Color(0xFF181818), Color(0xFF232323), Color(0xFF181818))
                        },
                        start = Offset.Zero,
                        end = Offset(420f * (0.4f + shimmerPhase), 260f),
                    ),
                )
                .border(
                    width = 1.dp,
                    color = if (viewedOnce) Color.White.copy(alpha = 0.08f) else Color(0xFFA4A4A4).copy(alpha = 0.45f),
                    shape = RoundedCornerShape(18.dp),
                )
                .clickable(enabled = !viewedOnce) { onOpenMedia() },
            contentAlignment = Alignment.Center,
        ) {
            if (!viewedOnce) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val w = size.width
                    val h = size.height
                    for (i in 0..18) {
                        val px = ((i * 53f + shimmerPhase * w) % w)
                        val py = ((i * 37f) % h)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f + (i % 3) * 0.04f),
                            radius = (2.2f + (i % 4)).dp.toPx(),
                            center = Offset(px, py),
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(if (viewedOnce) 30.dp else 44.dp)
                        .clip(CircleShape)
                        .background(
                            if (viewedOnce) Color.White.copy(alpha = 0.06f)
                            else Color(0xFF808080).copy(alpha = 0.24f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ChatVectorViewOnceIcon(
                        size = if (viewedOnce) 18.dp else 24.dp,
                        tint = if (viewedOnce) Color(0xFF727272) else Color(0xFFEEEEEE),
                    )
                }
                Text(
                    text = when {
                        viewedOnce -> "Telah Dibuka · 1×"
                        msg.attachmentType == "video" -> "Video Sekali Lihat"
                        else -> "Foto Sekali Lihat"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (viewedOnce) Color(0xFF727272) else Color.White,
                )
                if (!viewedOnce) {
                    Text(
                        text = "Ketuk untuk membuka · Privasi 1×",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color(0xFFC7C7C7),
                    )
                }
            }
            FloatingTimePill(modifier = Modifier.align(Alignment.BottomEnd))
        }
    } else if (msg.attachmentType == "image" && msg.attachmentData.isNotBlank()) {
        val bmp = rememberChatBitmap(msg.id, msg.attachmentData)
        val ratio = remember(bmp) {
            if (bmp != null && bmp.height > 0) {
                (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.62f, 1.85f)
            } else 1.33f
        }
        if (!autoDownloadAllowed) {
            // =========================================================
            // GAMBAR BELUM DIUNDUH: TAMPIL BURIK / BLUR SESUAI RASIO
            // + TOMBOL DOWNLOAD BULAT DI TENGAH
            // =========================================================
            val lowResBmp = remember(bmp, ratio) {
                if (bmp != null) {
                    val targetW = 18
                    val targetH = (18f / ratio).toInt().coerceIn(10, 32)
                    runCatching { Bitmap.createScaledBitmap(bmp, targetW, targetH, true) }.getOrDefault(bmp)
                } else null
            }
            val lowResImageBitmap = remember(lowResBmp) { lowResBmp?.asImageBitmap() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(mediaShape)
                    .background(Color(0xFF141418))
                    .clickable { onManualDownloadMedia() },
                contentAlignment = Alignment.Center,
            ) {
                if (lowResImageBitmap != null) {
                    Image(
                        bitmap = lowResImageBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(18.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.34f)),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.68f))
                            .border(1.2.dp, Color.White.copy(alpha = 0.55f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = XyIcons.Download,
                            contentDescription = "Unduh Gambar",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "${(msg.attachmentSize / 1024).coerceAtLeast(1)} KB",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                FloatingTimePill(modifier = Modifier.align(Alignment.BottomEnd))
            }
        } else if (bmp != null) {
            // =========================================================
            // GAMBAR SUDAH DIUNDUH: TAMPIL PENUH SESUAI ASPECT RATIO ASLI
            // =========================================================
            val fullImageBitmap = remember(bmp) { bmp.asImageBitmap() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(mediaShape)
                    .clickable { onOpenMedia() },
            ) {
                Image(
                    bitmap = fullImageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                FloatingTimePill(modifier = Modifier.align(Alignment.BottomEnd))
            }
        }
    } else if (msg.attachmentType == "video" && msg.attachmentData.isNotBlank()) {
        val prepared = rememberPreparedChatVideo(context, msg.id, msg.attachmentData, msg.viewOnce)
        val videoBytes = prepared.video?.bytes
        val videoRatio = prepared.video?.ratio ?: (9f / 16f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(videoRatio)
                .clip(mediaShape)
                .background(Color(0xFF0C0C10))
                .clickable { if (autoDownloadAllowed) onOpenMedia() else onManualDownloadMedia() },
            contentAlignment = Alignment.Center,
        ) {
            if (autoDownloadAllowed && autoplayVideoEnabled && videoBytes != null && videoBytes.isNotEmpty()) {
                FeedAutoplayVideoPreview(
                    videoBytes = videoBytes,
                    cacheKey = "chat_vid_${msg.id}",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.72f))
                            .border(1.2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (autoDownloadAllowed) {
                            ChatVectorVideoPlayIcon(size = 20.dp, tint = Color.White)
                        } else {
                            Icon(
                                imageVector = XyIcons.Download,
                                contentDescription = "Unduh Video",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Text(
                        text = if (autoDownloadAllowed) "Putar Video" else "${(msg.attachmentSize / 1024).coerceAtLeast(1)} KB",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
            FloatingTimePill(modifier = Modifier.align(Alignment.BottomEnd))
        }
    } else if (isAudioAttachment) {
        // =========================================================
        // AUDIO / LAGU / VOICE NOTE (FRAMELESS ROUNDED WAVEFORM PLAYER)
        // =========================================================
        val audio = rememberChatAudioController(context, msg.id, msg.attachmentData)
        val isPlaying = audio.playing
        val infWave = if (isPlaying && id.xydesk.remote.ui.theme.LocalDecorativeMotionEnabled.current) rememberInfiniteTransition(label = "audioWave") else null
        val wavePhase = if (infWave != null) {
            val animated by infWave.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "audioWavePhase",
        )
            animated
        } else 0f

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(mediaShape)
                .background(Color(0xFF191919))
                .border(1.dp, Color.White.copy(alpha = 0.10f), mediaShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = !audio.preparing) { audio.toggle(onOpenFile) },
                contentAlignment = Alignment.Center,
            ) {
                if (audio.preparing) {
                    Text("…", color = Color(0xFF09090B), style = MaterialTheme.typography.labelLarge)
                } else if (isPlaying) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color(0xFF09090B)),
                        )
                        Box(
                            modifier = Modifier
                                .width(3.5.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color(0xFF09090B)),
                        )
                    }
                } else {
                    ChatVectorVideoPlayIcon(size = 18.dp, tint = Color(0xFF09090B))
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = msg.attachmentName.ifBlank { "Audio Track" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                ) {
                    val bars = 24
                    val gap = size.width / bars
                    for (i in 0 until bars) {
                        val seed = ((i * 37 + 13) % 10) / 10f
                        val animBoost = if (isPlaying) kotlin.math.abs(kotlin.math.sin((wavePhase * 6.28f) + i * 0.55f)).toFloat() else 0.35f
                        val frac = (0.22f + seed * 0.45f + animBoost * 0.33f).coerceIn(0.2f, 1f)
                        val bh = size.height * frac
                        drawRoundRect(
                            color = if (isPlaying) Color.White else Color(0xFFA2A2A2),
                            topLeft = Offset(i * gap + 1.dp.toPx(), (size.height - bh) / 2f),
                            size = Size(2.6.dp.toPx(), bh),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${(msg.attachmentSize / 1024).coerceAtLeast(1)} KB",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.5.sp,
                        color = Color(0xFFA2A2A2),
                    )
                    if (floatingTime != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(XySpacing.compact),
                        ) {
                            Text(
                                text = floatingTime,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                color = Color(0xFFA2A2A2),
                            )
                            if (msg.mine) {
                                ChatVectorDeliveryTicks(ticks = msg.deliveryTicks, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    } else if (msg.attachmentType == "file") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable { onOpenFile() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(XySpacing.small),
        ) {
            Icon(
                imageVector = XyIcons.Clip,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    msg.attachmentName.ifBlank { "Dokumen" },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${(msg.attachmentSize / 1024).coerceAtLeast(1)} KB · Ketuk untuk Buka",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFA2A2A2),
                )
            }
        }
    }
}

/**
 * Avatar Foto Profil Pengirim di Samping Bubble Chat.
 * Memuat foto dari URL GitHub / komunitas di latar belakang dengan cache memori,
 * atau menampilkan inisial monokrom bersih jika belum ada foto.
 */
@Composable
internal fun ChatSenderAvatar(
    name: String,
    githubLogin: String,
    avatarUrl: String,
    isFounder: Boolean,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val avSize = if (compact) 25.dp else 32.dp
    val topTenRanks by LeaderboardPodium.topTen.collectAsState()
    val normGh = normalizedChatAccount(githubLogin)
    val normName = normalizedChatAccount(name)
    val rank = (if (normGh.isNotEmpty()) topTenRanks[normGh] else null)
        ?: (if (normName.isNotEmpty()) topTenRanks[normName] else null)
    val resolvedUrl = remember(avatarUrl, githubLogin, name) {
        ChatAvatarLoader.normalizeAvatarUrl(avatarUrl, githubLogin, name)
    }
    var bitmap by remember(resolvedUrl) {
        mutableStateOf(if (resolvedUrl.isNotBlank()) avatarMemoryCache.get(resolvedUrl) else null)
    }

    LaunchedEffect(resolvedUrl, githubLogin, name) {
        if (bitmap == null && (resolvedUrl.isNotBlank() || githubLogin.isNotBlank() || name.isNotBlank())) {
            ChatAvatarLoader.load(
                url = resolvedUrl,
                context = context,
                githubLogin = githubLogin,
                fallbackName = name,
            )?.let { bitmap = it }
        }
    }

    Box(
        modifier = Modifier.size(avSize),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(avSize)
                .clip(CircleShape)
                .background(if (isFounder) Color(0xFF131313) else MaterialTheme.colorScheme.surfaceVariant)
                .let { mod ->
                    if (isFounder) mod.border(1.5.dp, founderBorderBrush(), CircleShape) else mod
                }
                .podiumAvatarFrame(githubLogin),
            contentAlignment = Alignment.Center,
        ) {
            val bmp = bitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = name,
                    modifier = Modifier
                        .size(avSize)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                val initials = name.removePrefix("@").trim().take(2).uppercase(Locale.ROOT).ifEmpty { "U" }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (compact) 9.sp else 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isFounder) Color(0xFFE0E0E0) else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if ((rank != null && rank in 1..10) || isFounder) {
            AnimatedAvatarEffectOverlay(
                style = if (rank == 1 || isFounder) XyAnimatedAvatarStyle.ORBITAL_CROWN else XyAnimatedAvatarStyle.STARLIGHT_AURA,
                modifier = Modifier.size(avSize + 4.dp),
            )
        }
    }
}

/**
 * Ikon Vektor Centang 1 (`✓`) & Centang 2 (`✓✓`) gaya WhatsApp di atas Canvas (Tanpa Karakter Emoji).
 */
@Composable
internal fun ChatVectorDeliveryTicks(
    ticks: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val description = when {
        ticks < 0 -> xyNow("Belum dikonfirmasi; periksa koneksi", "Unconfirmed; check connection")
        ticks == 0 -> xyNow("Menunggu konfirmasi", "Awaiting confirmation")
        ticks == 1 -> xyNow("Dalam antrean", "Queued")
        else -> xyNow("Diterima ruang chat, bukan tanda dibaca", "Room acknowledged, not a read receipt")
    }
    Canvas(modifier = modifier.semantics { contentDescription = description }.size(width = if (ticks >= 2) 15.dp else 11.dp, height = 11.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = h * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        if (ticks <= 0) {
            drawCircle(color = tint, radius = h * 0.4f, center = androidx.compose.ui.geometry.Offset(w / 2, h / 2), style = stroke)
            if (ticks < 0) {
                drawLine(tint, androidx.compose.ui.geometry.Offset(w / 2, h * 0.27f), androidx.compose.ui.geometry.Offset(w / 2, h * 0.53f), strokeWidth = h * 0.12f)
                drawCircle(tint, radius = h * 0.06f, center = androidx.compose.ui.geometry.Offset(w / 2, h * 0.72f))
            } else {
                drawLine(tint, androidx.compose.ui.geometry.Offset(w / 2, h * 0.22f), androidx.compose.ui.geometry.Offset(w / 2, h / 2), strokeWidth = h * 0.12f)
                drawLine(tint, androidx.compose.ui.geometry.Offset(w / 2, h / 2), androidx.compose.ui.geometry.Offset(w * 0.72f, h / 2), strokeWidth = h * 0.12f)
            }
        } else if (ticks == 1) {
            val p1 = Path().apply {
                moveTo(w * 0.18f, h * 0.55f)
                lineTo(w * 0.42f, h * 0.80f)
                lineTo(w * 0.84f, h * 0.24f)
            }
            drawPath(p1, color = tint, style = stroke)
        } else {
            val p1 = Path().apply {
                moveTo(w * 0.08f, h * 0.55f)
                lineTo(w * 0.28f, h * 0.78f)
                lineTo(w * 0.62f, h * 0.24f)
            }
            val p2 = Path().apply {
                moveTo(w * 0.38f, h * 0.55f)
                lineTo(w * 0.58f, h * 0.78f)
                lineTo(w * 0.92f, h * 0.24f)
            }
            drawPath(p1, color = tint, style = stroke)
            drawPath(p2, color = tint, style = stroke)
        }
    }
}

/**
 * Ikon Vektor Panah Balas (Reply Arrow) di atas Canvas.
 */
@Composable
internal fun ChatVectorReplyIcon(
    size: Dp = 14.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val arrow = Path().apply {
            moveTo(w * 0.42f, h * 0.22f)
            lineTo(w * 0.16f, h * 0.46f)
            lineTo(w * 0.42f, h * 0.70f)
        }
        val curve = Path().apply {
            moveTo(w * 0.18f, h * 0.46f)
            lineTo(w * 0.62f, h * 0.46f)
            quadraticTo(w * 0.86f, h * 0.46f, w * 0.86f, h * 0.76f)
        }
        drawPath(arrow, color = tint, style = stroke)
        drawPath(curve, color = tint, style = stroke)
    }
}

/**
 * Ikon Vektor Stiker (Lembar Sudut Terlipat) di atas Canvas.
 */
@Composable
internal fun ChatVectorStickerIcon(
    size: Dp = 18.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.14f, h * 0.14f),
            size = Size(w * 0.72f, h * 0.72f),
            cornerRadius = CornerRadius(w * 0.20f, h * 0.20f),
            style = stroke,
        )
        val fold = Path().apply {
            moveTo(w * 0.56f, h * 0.86f)
            lineTo(w * 0.56f, h * 0.60f)
            lineTo(w * 0.86f, h * 0.60f)
        }
        drawPath(fold, color = tint, style = stroke)
        drawCircle(color = tint, radius = w * 0.06f, center = Offset(w * 0.36f, h * 0.42f))
        drawCircle(color = tint, radius = w * 0.06f, center = Offset(w * 0.62f, h * 0.42f))
    }
}

/**
 * Ikon Vektor Pin Sematkan Pesan (Pushpin) di atas Canvas.
 */
@Composable
internal fun ChatVectorPinIcon(
    size: Dp = 16.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val pinBody = Path().apply {
            moveTo(w * 0.32f, h * 0.18f)
            lineTo(w * 0.68f, h * 0.18f)
            lineTo(w * 0.62f, h * 0.46f)
            lineTo(w * 0.76f, h * 0.60f)
            lineTo(w * 0.24f, h * 0.60f)
            lineTo(w * 0.38f, h * 0.46f)
            close()
        }
        drawPath(pinBody, color = tint, style = stroke)
        drawLine(
            color = tint,
            start = Offset(w * 0.50f, h * 0.60f),
            end = Offset(w * 0.50f, h * 0.88f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Ikon Vektor Media 1× Sekali Lihat (Lingkaran Putus-Putus + Angka 1 Vektor) di atas Canvas.
 */
@Composable
internal fun ChatVectorViewOnceIcon(
    size: Dp = 18.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawArc(
            color = tint,
            startAngle = -70f,
            sweepAngle = 210f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = stroke,
        )
        drawArc(
            color = tint,
            startAngle = 165f,
            sweepAngle = 45f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = stroke,
        )
        drawArc(
            color = tint,
            startAngle = 230f,
            sweepAngle = 40f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.12f),
            size = Size(w * 0.76f, h * 0.76f),
            style = stroke,
        )
        val onePath = Path().apply {
            moveTo(w * 0.42f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.30f)
            lineTo(w * 0.52f, h * 0.68f)
        }
        drawPath(onePath, color = tint, style = stroke)
    }
}

/**
 * Ikon Vektor Play Video Segitiga di atas Canvas.
 */
@Composable
internal fun ChatVectorVideoPlayIcon(
    size: Dp = 16.dp,
    tint: Color = Color.White,
) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val play = Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.82f, h * 0.50f)
            lineTo(w * 0.28f, h * 0.82f)
            close()
        }
        drawPath(play, color = tint)
    }
}

