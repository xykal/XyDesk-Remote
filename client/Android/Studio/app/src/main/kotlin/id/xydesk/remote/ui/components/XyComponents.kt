package id.xydesk.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.xydesk.remote.ui.theme.XyDisplay

/** Tombol brand XyDesk: violet penuh, radius besar. */
@Composable
fun XyBrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Tombol sekunder: outline. */
@Composable
fun XyGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
    ) { Text(text) }
}

/** Kartu brand: surface variant + border halus, padding 16. */
@Composable
fun XyCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(16.dp),
        content = content,
    )
}

/** Judul seksi: Space Grotesk, uppercase, spasi lebar, warna sekunder. */
@Composable
fun XySectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(bottom = 8.dp),
        style = MaterialTheme.typography.labelMedium,
        fontFamily = XyDisplay,
        color = MaterialTheme.colorScheme.secondary,
        letterSpacing = 1.2.sp,
    )
}

/** Chip status: dot warna + label. */
@Composable
fun XyStatusChip(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/** Baris menu drawer: ikon + label, highlight saat aktif. */
@Composable
fun XyMenuItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (active) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Wordmark XyDesk (Space Grotesk). */
@Composable
fun XyWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
) {
    Text(
        "XyDesk",
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = fontSize),
        fontFamily = XyDisplay,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
