package id.xydesk.remote.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import id.xydesk.remote.R

/**
 * Widget homescreen "quick connect" (permintaan pemilik 2026-10-15): satu tap
 * dari launcher langsung menyambung ke perangkat pilihan tanpa membuka app.
 *
 * Perangkat dipilih saat widget dipasang (activity konfigurasi) dan disimpan
 * per appWidgetId. Tap widget membuka HomeActivity dengan extra profil —
 * jalur autentikasi biometric (kalau aktif) tetap dilewati seperti connect
 * dari daftar perangkat.
 */
class XyQuickConnectWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            val profileId = widgetPrefs(context).getString(id.toString(), null)
            val views = RemoteViews(context.packageName, R.layout.widget_quick_connect)
            if (profileId == null) {
                views.setTextViewText(R.id.widget_device, xyNow("Pilih perangkat", "Pick a device"))
                views.setTextViewText(R.id.widget_hint, xyNow("Buka untuk atur", "Open to set up"))
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    pendingOpen(context, id, null),
                )
            } else {
                // Label disimpan bersama mapping saat konfigurasi, supaya
                // update widget cepat tanpa membaca repo (bisa lambat saat boot).
                val label = widgetPrefs(context).getString("${id}:label", null)
                views.setTextViewText(
                    R.id.widget_device,
                    label ?: xyNow("Perangkat tersimpan", "Saved device"),
                )
                views.setTextViewText(R.id.widget_hint, xyNow("Ketuk untuk menyambung", "Tap to connect"))
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    pendingOpen(context, id, profileId),
                )
            }
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = widgetPrefs(context).edit()
        for (id in appWidgetIds) {
            editor.remove(id.toString())
            editor.remove("${id}:label")
        }
        editor.apply()
    }

    private fun pendingOpen(context: Context, appWidgetId: Int, profileId: String?): android.app.PendingIntent {
        val intent = Intent(context, XyDeskHomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_CONNECT_PROFILE_ID, profileId)
            putExtra(EXTRA_CONNECT_WIDGET_ID, appWidgetId)
        }
        return android.app.PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        fun widgetPrefs(context: Context) =
            context.getSharedPreferences("xydesk.widgets", Context.MODE_PRIVATE)
    }
}
