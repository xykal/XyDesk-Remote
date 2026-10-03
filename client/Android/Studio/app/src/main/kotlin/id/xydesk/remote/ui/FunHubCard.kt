package id.xydesk.remote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XyPillButton

/** Optional home card with offline, nonpartisan text memes and short jokes. */
@Composable
internal fun FunHubCard(onHide: () -> Unit) {
    val jokes = listOf(
        xy(
            "Politik jaringan: semua menjanjikan koneksi cepat; rapat dimulai saat ping merah.",
            "Network politics: everyone promises speed; the meeting starts when ping turns red.",
        ),
        xy(
            "Rapat anggaran: bandwidth minta naik, resolusi malah dipotong. Kompromi klasik.",
            "Budget meeting: bandwidth asks for a raise; resolution gets cut. Classic compromise.",
        ),
        xy("Debat boleh panas, buffering jangan.", "Debates can run hot; buffering shouldn't."),
        xy(
            "Kalau remote terasa lambat, jangan salahkan kabinet—cek ping dulu.",
            "If remote feels slow, don't blame the cabinet—check ping first.",
        ),
        xy(
            "Di pemerintahan paket data, semua sepakat: kuota habis itu darurat nasional.",
            "In the data-plan government, everyone agrees: low quota is a national emergency.",
        ),
    )
    var jokeIndex by remember { mutableIntStateOf(0) }

    XyCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(xy("Ruang Santai", "Fun corner"), style = MaterialTheme.typography.titleSmall)
                Text(
                    xy("Meme teks & satire ringan · nonpartisan", "Text memes & light satire · nonpartisan"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                xy("Sembunyikan", "Hide"),
                modifier = Modifier.clickable(onClick = onHide).padding(start = 8.dp, top = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(jokes[jokeIndex % jokes.size], style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        XyPillButton(
            text = xy("Jokes berikutnya", "Next joke"),
            onClick = { jokeIndex = (jokeIndex + 1) % jokes.size },
            primary = false,
            compact = true,
            modifier = Modifier.align(Alignment.End),
        )
    }
}
