package id.xydesk.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.theme.XyDisplay
import id.xydesk.remote.ui.theme.XyPill

/**
 * Primitif XyDesk. Semua kontrol digambar sendiri: border hairline,
 * radius kecil untuk panel, pil penuh untuk tombol. Tidak ada shadow,
 * tidak ada elevation, tidak ada gradient dekoratif.
 */

@Composable
fun XyCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(padding),
        content = content,
    )
}

/** Tombol pil. `primary` = isi penuh warna teks; `false` = outline saja. */
@Composable
fun XyPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        primary -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .clip(XyPill)
            .background(container)
            .then(
                if (primary && enabled) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, XyPill)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) 16.dp else 20.dp,
                vertical = if (compact) 9.dp else 13.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tombol ikon bulat (dipakai di bar sesi). */
@Composable
fun XyIconPill(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    active: Boolean = false,
    contentDescription: String? = null,
) {
    val bg = if (active) MaterialTheme.colorScheme.primary else Color(0xCC14171B)
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (active) Color.Transparent else Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = fg, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun XySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = XyDisplay,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.4.sp,
    )
}

/** Baris daftar: judul + subjudul opsional + trailing. */
@Composable
fun XyRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    leadingTint: Color? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            Icon(
                leading,
                contentDescription = null,
                tint = leadingTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Toggle custom: track pill + knob, tanpa komponen bawaan OS. */
@Composable
fun XySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "track",
    )
    val knobColor by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "knob",
    )
    val knobOffset by animateDpAsState(if (checked) 20.dp else 2.dp, label = "knobOffset")
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(XyPill)
            .background(trackColor)
            .border(
                1.dp,
                if (checked) Color.Transparent else MaterialTheme.colorScheme.outline,
                XyPill,
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(start = knobOffset, top = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(knobColor),
        )
    }
}

@Composable
fun XyToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) {
            Icon(
                leading,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        XySwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Segmented pill: pilihan tunggal dari beberapa opsi. */
@Composable
fun XySegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(XyPill)
            .border(1.dp, MaterialTheme.colorScheme.outline, XyPill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(XyPill)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Input teks: label kecil di atas, kotak border hairline, radius kecil. */
@Composable
fun XyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = XyDisplay,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (isPassword) PasswordVisualTransformation()
            else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && hint != null) {
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
    }
}

/** Bar header minimal: tombol kembali + judul + aksi kanan. */
@Composable
fun XyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onBack != null) {
            XyIconPill(
                icon = XyIcons.ChevronLeft,
                onClick = onBack,
                size = 42.dp,
                contentDescription = "Kembali",
            )
        }
        Text(
            title,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 12.dp else 4.dp),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions?.invoke()
    }
}

@Composable
fun XyStatusDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun XyWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
) {
    Text(
        "XyDesk",
        modifier = modifier,
        fontFamily = XyDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = fontSize,
        letterSpacing = (-0.4).sp,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

/**
 * Mark XyDesk dalam Compose (geometri sama dengan ikon launcher):
 * layar + kursor di dalamnya. Satu warna, jadi bisa di-tint sesuai tema.
 */
@Composable
fun XyLogo(
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    androidx.compose.foundation.Canvas(modifier) {
        val s = size.minDimension / 48f
        fun point(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * s, y * s)

        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.7f * s)
        drawRoundRect(
            color = tint,
            topLeft = point(6.8f, 13.4f),
            size = androidx.compose.ui.geometry.Size(34.4f * s, 20.6f * s),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * s),
            style = stroke,
        )
        drawLine(
            color = tint,
            start = point(19.5f, 40.2f),
            end = point(28.5f, 40.2f),
            strokeWidth = 2.7f * s,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = point(24f, 34.2f),
            end = point(24f, 40.2f),
            strokeWidth = 2.7f * s,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        val cursor = androidx.compose.ui.graphics.Path().apply {
            val xs = floatArrayOf(18.6f, 18.6f, 21.7f, 23.9f, 26.2f, 24f, 28.1f)
            val ys = floatArrayOf(17.6f, 28.4f, 25.3f, 30.1f, 29f, 24.2f, 23.8f)
            val first = point(xs[0], ys[0])
            moveTo(first.x, first.y)
            for (i in 1 until xs.size) {
                val q = point(xs[i], ys[i])
                lineTo(q.x, q.y)
            }
            close()
        }
        drawPath(cursor, tint)
    }
}

/**
 * Shell dialog milik XyDesk.
 *
 * Bukan AlertDialog bawaan: panel gelap ber-border tipis dengan radius kecil,
 * tombol pil buatan sendiri, dan scrim yang bisa dimatikan (mis. untuk prompt
 * kredensial/sertifikat yang tidak boleh ditutup begitu saja).
 */
@Composable
fun XyOverlay(
    title: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    maxWidth: Dp = 380.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .then(
                if (onDismiss != null) Modifier.clickable(onClick = onDismiss) else Modifier,
            )
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .clickable(enabled = false) { }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title.uppercase(),
                fontFamily = XyDisplay,
                fontSize = 11.sp,
                letterSpacing = 1.3.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

/** Dialog teks sederhana: judul + isi (+ opsional tombol kedua). */
@Composable
fun XyDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    mono: Boolean = false,
) {
    XyOverlay(title = title, onDismiss = onDismiss) {
        if (mono) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                body.lineSequence().forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (dismissLabel != null && onDismiss != null) {
                XyPillButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    primary = false,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            XyPillButton(
                text = confirmLabel,
                onClick = onConfirm,
                compact = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
