package dev.locket.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.BitmapImageProvider
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val REVEAL_DURATION_MS = 30_000L
private const val PREFS = "widget_state"
private const val REVEAL_UNTIL = "revealed_until"
private const val LAST_TAP = "last_tap"
private const val LAST_SEEN_RECEIVED = "last_seen_received_at"
private const val TAP_WINDOW_MS = 2_500L

@SuppressLint("RestrictedApi")
class StealthWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val background = if (dark) Color(0xFF201A19) else Color(0xFFFFF8F7)
        val primaryText = if (dark) Color(0xFFEDE0DE) else Color(0xFF201A19)
        val mutedText = if (dark) Color(0xFFD8C2BF) else Color(0xFF857370)
        val indicator = if (dark) Color(0xFFFFB4AB) else Color(0xFFE8756D)
        provideContent {
            val state = currentState<Preferences>()
            val revealUntil = state[longPreferencesKey(REVEAL_UNTIL)] ?: 0L
            val latest = latestReceivedPhoto(context)
            val stealthEnabled = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE).getBoolean("double_tap_reveal", true)
            val image = if (!stealthEnabled || revealUntil > System.currentTimeMillis()) latestPhotoBitmap(context, latest) else null
            if (image != null && latest != null) {
                Box(GlanceModifier.fillMaxSize().clickable(actionRunCallback<RevealCallback>())) {
                    Image(provider = BitmapImageProvider(image), contentDescription = "Latest received moment", modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Column(GlanceModifier.padding(16.dp), verticalAlignment = Alignment.Bottom) {
                        Text("${latest.optString("senderName").ifBlank { "Partner" }} · ${widgetRelativeTime(latest.optLong("capturedAt"))}", style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp))
                    }
                }
            } else {
                Box(
                    GlanceModifier.fillMaxSize()
                        .background(ColorProvider(background))
                        .clickable(actionRunCallback<RevealCallback>())
                        .padding(20.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(horizontalAlignment = Alignment.Start, verticalAlignment = Alignment.Bottom) {
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), style = TextStyle(color = ColorProvider(primaryText), fontSize = 32.sp))
                        Text(SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date()), style = TextStyle(color = ColorProvider(mutedText), fontSize = 13.sp), modifier = GlanceModifier.padding(top = 3.dp))
                    }
                    if (stealthEnabled && latest != null && latest.optLong("capturedAt") > context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_SEEN_RECEIVED, 0L)) {
                        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                            Text("•", modifier = GlanceModifier.padding(10.dp), style = TextStyle(color = ColorProvider(indicator), fontSize = 20.sp))
                        }
                    }
                }
            }
        }
    }
}

class RevealCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE).getBoolean("double_tap_reveal", true)) return
        val now = System.currentTimeMillis()
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTap = preferences.getLong(LAST_TAP, 0L)
        val revealed = preferences.getLong(REVEAL_UNTIL, 0L) > now
        if (now - lastTap > TAP_WINDOW_MS) {
            preferences.edit().putLong(LAST_TAP, now).apply()
            return
        }
        preferences.edit().putLong(LAST_TAP, 0L).apply()
        if (revealed) {
            resetStealth(context)
            return
        }
        val latest = latestReceivedPhoto(context)
        val until = now + REVEAL_DURATION_MS
        preferences.edit().putLong(REVEAL_UNTIL, until).putLong(LAST_SEEN_RECEIVED, latest?.optLong("capturedAt", 0L) ?: 0L).apply()
        updateAppWidgetState(context, glanceId) { state -> state[longPreferencesKey(REVEAL_UNTIL)] = until }
        val reset = PendingIntent.getBroadcast(context, 921, Intent(context, StealthResetReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canUseExactAlarm) {
            runCatching { alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, reset) }
                .onFailure { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, reset) }
        } else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, reset)
        StealthWidget().update(context, glanceId)
        StealthWidget().updateAll(context)
    }
}

class StealthResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) = resetStealth(context)
}

class StealthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StealthWidget()
}

private fun latestReceivedPhoto(context: Context): JSONObject? {
    val raw = context.getSharedPreferences("photo_archive", Context.MODE_PRIVATE).getString("photos", null) ?: return null
    return runCatching {
        val localDeviceId = DeviceIdentity.id(context)
        val photos = JSONArray(raw)
        (0 until photos.length()).map { photos.getJSONObject(it) }
            .filter { storedSenderId(it.optString("senderDeviceId"), it.optString("filename"), localDeviceId) != localDeviceId }
            .maxByOrNull { it.optLong("capturedAt", 0L) }
    }.getOrNull()
}

private fun latestPhotoBitmap(context: Context, latest: JSONObject?): Bitmap? {
    return runCatching {
        val file = latest?.optString("path")?.takeIf { it.isNotBlank() }?.let(::File) ?: return@runCatching null
        if (!file.exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 640) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

private fun widgetRelativeTime(timestamp: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L))
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 24 * 60 -> "${minutes / 60}h"
        else -> "${minutes / (24 * 60)}d"
    }
}

internal fun resetStealth(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_TAP, 0L).putLong(REVEAL_UNTIL, 0L).apply()
    MainScope().launch {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(StealthWidget::class.java)
        ids.forEach { id -> updateAppWidgetState(context, id) { state -> state[longPreferencesKey(REVEAL_UNTIL)] = 0L } }
        StealthWidget().updateAll(context)
    }
}
