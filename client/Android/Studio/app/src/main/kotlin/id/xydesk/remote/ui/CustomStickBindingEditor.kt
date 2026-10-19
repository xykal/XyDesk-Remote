package id.xydesk.remote.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import id.xydesk.remote.ui.components.XyField
import id.xydesk.remote.ui.components.XyOverlay
import id.xydesk.remote.ui.components.XyPillButton
import id.xydesk.remote.ui.input.XyCustomStickBindings
import id.xydesk.remote.ui.input.XyPadKey

@Composable
internal fun CustomStickBindingEditor(onApply: (String, XyCustomStickBindings) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SessionPrefs(context) }
    var name by remember { mutableStateOf(prefs.customStickProfile) }
    var bindings by remember { mutableStateOf(prefs.customStickBindings()) }
    var names by remember { mutableStateOf(prefs.customStickProfiles()) }
    var direction by remember { mutableStateOf<Int?>(null) }
    Text("Preset keyboard per game. Pilih empat tombol; ini bukan controller analog Windows. Perubahan aktif setelah Terapkan.")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { preset ->
            XyPillButton(text = preset, compact = true, primary = name == preset, onClick = {
                name = preset; bindings = prefs.customStickBindings(preset)
            })
        }
    }
    XyField(value = name, onValueChange = { name = it.take(40) }, label = "Nama preset game")
    listOf("Atas" to bindings.up, "Bawah" to bindings.down, "Kiri" to bindings.left, "Kanan" to bindings.right).forEachIndexed { index, (label, key) ->
        XyPillButton(text = "$label: ${key.name}", compact = true, onClick = { direction = index })
    }
    XyPillButton(text = "Simpan & terapkan preset", onClick = {
        try {
            val clean = name.trim()
            require(Regex("[A-Za-z0-9 _.-]{1,40}").matches(clean))
            onApply(clean, bindings)
            name = clean; names = prefs.customStickProfiles()
            Toast.makeText(context, "Preset keyboard diterapkan; tombol lama sudah dilepas.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Gunakan nama 1–40 huruf/angka/spasi/titik/tanda hubung; maksimal 30 preset.", Toast.LENGTH_LONG).show()
        }
    })
    direction?.let { selected ->
        XyOverlay(title = "Pilih tombol arah", onDismiss = { direction = null }) {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                XyPadKey.entries.chunked(3).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        group.forEach { key ->
                            XyPillButton(text = key.name.replace("ARROW_", "Arrow ").replace("NUMPAD_", "Num ").removePrefix("DIGIT_"),
                                compact = true, modifier = Modifier.weight(1f), onClick = {
                                    bindings = when (selected) {
                                        0 -> bindings.copy(up = key)
                                        1 -> bindings.copy(down = key)
                                        2 -> bindings.copy(left = key)
                                        else -> bindings.copy(right = key)
                                    }
                                    direction = null
                                })
                        }
                    }
                }
            }
        }
    }
}
