package id.xydesk.remote.cloud

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.xydesk.remote.R
import id.xydesk.remote.ui.AppPrefs
import id.xydesk.remote.ui.theme.XyDeskTheme
import id.xydesk.remote.ui.components.XyBrandButton
import id.xydesk.remote.ui.components.XyCard
import id.xydesk.remote.ui.components.XySectionTitle

/** Compose-only Cloud RDP view; keeps the GitHub/Tailscale pipeline in the Java activity. */
class CloudRdpComposeView(context: android.content.Context) : AbstractComposeView(context) {
    interface Listener {
        fun onLoginRequested()
        fun onCreateRequested(repo: String, user: String, password: String, tailscaleKey: String)
    }

    var listener: Listener? = null
    var busy by mutableStateOf(false)
    var statusMessage by mutableStateOf("Login ke GitHub milikmu, lalu buat PC cloud sendiri.")
    var loginMessage by mutableStateOf("Belum terhubung")

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val prefs = AppPrefs(context)
        val systemDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = when (prefs.themeMode) {
            1 -> true
            2 -> false
            else -> systemDark
        }
        XyDeskTheme(dark = dark) {
            CloudRdpForm(
                busy = busy,
                status = statusMessage,
                loginStatus = loginMessage,
                onLogin = { listener?.onLoginRequested() },
                onCreate = { repo, user, pass, key ->
                    listener?.onCreateRequested(repo, user, pass, key)
                },
            )
        }
    }
}

@Composable
private fun CloudRdpForm(
    busy: Boolean,
    status: String,
    loginStatus: String,
    onLogin: () -> Unit,
    onCreate: (String, String, String, String) -> Unit,
) {
    // Keep field state across recompositions of status/progress.
    val repoState = androidx.compose.runtime.remember { mutableStateOf("") }
    val userState = androidx.compose.runtime.remember { mutableStateOf("") }
    val passwordState = androidx.compose.runtime.remember { mutableStateOf("") }
    val keyState = androidx.compose.runtime.remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(
                painter = painterResource(R.drawable.xydesk_app_mark),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF171521)).padding(4.dp),
            )
            Column {
                Text("PC Cloud", style = MaterialTheme.typography.headlineSmall)
                Text("Buat dan kelola sesi RDP milikmu", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        XyCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Cloud RDP memakai akun GitHub dan Windows runner milikmu sendiri. XyDesk tidak menampung kredensial PC-mu.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        XySectionTitle("Akun GitHub")
        XyCard(modifier = Modifier.fillMaxWidth()) {
            Text(loginStatus, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            XyBrandButton("Hubungkan GitHub", onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }

        XySectionTitle("Konfigurasi PC")
        XyCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = repoState.value,
                onValueChange = { repoState.value = it },
                label = { Text("Nama PC / repo GitHub") },
                placeholder = { Text("contoh: desk-gaming-01") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = userState.value,
                onValueChange = { userState.value = it },
                label = { Text("Nama user Windows (opsional)") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = passwordState.value,
                onValueChange = { passwordState.value = it },
                label = { Text("Password Windows (opsional)") },
                placeholder = { Text("kosong = dibuat otomatis") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = keyState.value,
                onValueChange = { keyState.value = it },
                label = { Text("Tailscale auth key (opsional)") },
                placeholder = { Text("kosong = pakai key di runner") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Password minimal 12 karakter dan kuat. Key Tailscale hanya dipakai untuk setup; jangan bagikan log yang berisi rahasia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            XyBrandButton(
                "Buat & siapkan PC",
                onClick = { onCreate(repoState.value, userState.value, passwordState.value, keyState.value) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        XyCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
