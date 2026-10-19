package id.xydesk.remote.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val LocalDecorativeMotionEnabled = staticCompositionLocalOf { false }

internal fun shouldRunDecorativeMotion(started: Boolean, systemMotion: Boolean, powerSaver: Boolean): Boolean =
    started && systemMotion && !powerSaver

/** One observer per theme subtree, not one receiver per chat badge. */
@Composable
internal fun rememberDecorativeMotionEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun current(): Boolean {
        val scale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        val saving = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ?: false
        return shouldRunDecorativeMotion(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), scale > 0f, saving)
    }
    var enabled by remember(context, lifecycle) { mutableStateOf(current()) }
    DisposableEffect(context, lifecycle) {
        val update = { enabled = current() }
        val lifecycleObserver = LifecycleEventObserver { _, _ -> update() }
        val powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { update() }
        }
        val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { update() }
        }
        lifecycle.addObserver(lifecycleObserver)
        runCatching { ContextCompat.registerReceiver(context, powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED) }
        runCatching { context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, settingsObserver) }
        update()
        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
            runCatching { context.unregisterReceiver(powerReceiver) }
            runCatching { context.contentResolver.unregisterContentObserver(settingsObserver) }
        }
    }
    return enabled
}
