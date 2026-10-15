package id.xydesk.remote.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.sessions.SessionsRepository
import id.xydesk.remote.ui.theme.XyDeskTheme
import id.xydesk.remote.ui.theme.XyThemeState
import id.xydesk.remote.ui.theme.xyDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Konfigurasi widget quick-connect (dibuka launcher saat widget dipasang):
 * pilih salah satu perangkat tersimpan; pilihan diikat ke appWidgetId dan
 * tersimpan di prefs "xydesk.widgets".
 */
class XyWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED) // default: batal kalau tidak memilih

        var profiles by mutableStateOf<List<ConnectionProfile>?>(null)
        lifecycleScope.launch {
            profiles = runCatching {
                SessionsRepository(applicationContext).favorites().first()
            }.getOrDefault(emptyList())
        }

        setContent {
            XyThemeState.init(AppPrefs(this))
            XyDeskTheme(dark = xyDark()) {
                XyWidgetConfigScreen(
                    profiles = profiles,
                    onPick = { profile ->
                        val prefs = XyQuickConnectWidgetProvider.widgetPrefs(this)
                        prefs.edit()
                            .putString(appWidgetId.toString(), profile.id)
                            .putString("${appWidgetId}:label", profile.label ?: profile.host)
                            .apply()
                        // Render ulang widget sekarang supaya label langsung tampil.
                        runCatching {
                            val manager = AppWidgetManager.getInstance(this)
                            XyQuickConnectWidgetProvider()
                                .onUpdate(this, manager, intArrayOf(appWidgetId))
                        }
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun XyWidgetConfigScreen(
    profiles: List<ConnectionProfile>?,
    onPick: (ConnectionProfile) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        Text(xyNow("Widget Quick Connect", "Quick Connect Widget"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(xyNow("Pilih perangkat untuk widget ini", "Pick a device for this widget"), fontSize = 13.sp, color = Color(0xFFB8BFC9))
        Spacer(Modifier.height(14.dp))
        when {
            profiles == null -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                CircularProgressIndicator(Modifier.size(30.dp))
            }
            profiles.isEmpty() -> Text(
                xyNow(
                    "Belum ada perangkat tersimpan. Tambahkan dulu di app XyDesk.",
                    "No saved devices yet. Add one in the XyDesk app first.",
                ),
                fontSize = 14.sp,
                color = Color(0xFFB8BFC9),
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { profile ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(profile) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF232833)),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResourceXyDesktop(),
                                contentDescription = null,
                                tint = Color(0xFF9FB4FF),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    profile.label ?: profile.host,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    profile.host + if (profile.port != 3389) ":${profile.port}" else "",
                                    fontSize = 12.sp,
                                    color = Color(0xFFB8BFC9),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ikon desktop sederhana untuk baris daftar (murni compose, tanpa aset baru). */
@Composable
private fun painterResourceXyDesktop() = androidx.compose.ui.res.painterResource(
    id.xydesk.remote.R.drawable.ic_launcher_monochrome,
)
