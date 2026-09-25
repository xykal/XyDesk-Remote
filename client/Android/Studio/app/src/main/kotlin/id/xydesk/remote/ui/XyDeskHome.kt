@file:OptIn(ExperimentalMaterial3Api::class)

package id.xydesk.remote.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.xydesk.remote.core.ConnectionProfile
import id.xydesk.remote.sessions.SessionsRepository
import kotlinx.coroutines.launch

/**
 * M1.2 — layar home: favorit + form koneksi + pintu Cloud RDP.
 *
 * Data: [SessionsRepository] (Room + CredentialVault). Connect = layar
 * sesi XyDesk M2 (Compose + HUD); core SessionActivity M0 tetap
 * tersedia lewat "Form klasik".
 */
@Composable
fun XyDeskHome(
    onOpenCloudRdp: () -> Unit,
    onOpenClassicForm: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { SessionsRepository(context.applicationContext) }
    val favoritesFlow = remember { repo.favorites() }
    val favorites by favoritesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showForm by remember { mutableStateOf(false) }

    fun connectTo(profile: ConnectionProfile) {
        scope.launch { repo.touch(profile) }
        // M2: jalur sesi XyDesk (surface + HUD Compose)
        context.startActivity(XyDeskSessionActivity.connectIntent(profile))
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("XyDesk Remote") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Default.Add, contentDescription = "Koneksi baru")
            }
        },
    ) { padding ->
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Belum ada favorit", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tekan + untuk koneksi baru, atau\nCloud RDP buat mesin dari GitHub",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
            ) {
                items(favorites, key = { it.id }) { profile ->
                    FavoriteRow(
                        profile = profile,
                        onConnect = { connectTo(profile) },
                        onDelete = { scope.launch { repo.remove(profile.id) } },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = onOpenCloudRdp) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Cloud RDP (GitHub)")
                        }
                        TextButton(onClick = onOpenClassicForm) {
                            Text("Form klasik (M0)")
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text("Koneksi baru") },
            text = {
                ConnectFormFields(
                    onSaved = { profile, rememberPassword ->
                        showForm = false
                        scope.launch { repo.save(profile, rememberPassword) }
                        connectTo(profile)
                    },
                )
            },
            confirmButton = {}, // tombol aksi ada di dalam form
            dismissButton = {
                TextButton(onClick = { showForm = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun FavoriteRow(
    profile: ConnectionProfile,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = profile.label ?: profile.host,
                    style = MaterialTheme.typography.titleMedium,
                )
                val sub = buildString {
                    append(profile.host).append(':').append(profile.port)
                    if (!profile.username.isNullOrBlank()) {
                        append("  •  ").append(profile.username)
                    }
                }
                Text(text = sub, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Hapus favorit")
            }
        }
    }
}

/**
 * Form koneksi. [onSaved] dipanggil dengan profil valid + flag ingat-password.
 */
@Composable
private fun ConnectFormFields(
    onSaved: (profile: ConnectionProfile, rememberPassword: Boolean) -> Unit,
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("3389") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var rememberPass by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Column {
        OutlinedTextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Host / IP / tailnet (wajib)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit).take(5) },
            label = { Text("Port (default 3389)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = domain,
            onValueChange = { domain = it },
            label = { Text("Domain (opsional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Label (opsional, mis. 'Kantor')") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = rememberPass, onCheckedChange = { rememberPass = it })
            Text("Ingat password (tersimpan terenkripsi di Keystore)")
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(
                onClick = {
                    val h = host.trim()
                    val p = port.toIntOrNull() ?: 3389
                    when {
                        h.isEmpty() -> {
                            error = "Host wajib diisi"
                            return@Button
                        }
                        p !in 1..65535 -> {
                            error = "Port tidak valid (1-65535)"
                            return@Button
                        }
                        else -> {
                            error = null
                        }
                    }
                    val profile = try {
                        ConnectionProfile(
                            host = h,
                            port = p,
                            username = user.trim().ifEmpty { null },
                            password = pass.ifEmpty { null },
                            domain = domain.trim().ifEmpty { null },
                            label = label.trim().ifEmpty { null },
                        )
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                        return@Button
                    }
                    onSaved(profile, rememberPass)
                },
            ) { Text("Simpan & Connect") }
        }
    }
}
