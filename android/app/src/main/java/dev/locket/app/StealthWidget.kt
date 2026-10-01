package dev.locket.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.annotation.SuppressLint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.BitmapImageProvider
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.action.ActionParameters
import androidx.glance.currentState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val REVEAL_DURATION_MS = 30_000L
private const val PREFS = "widget_state"
private const val REVEAL_UNTIL = "revealed_until"
private const val LAST_TAP = "last_tap"
private const val DOUBLE_TAP_WINDOW_MS = 900L

@SuppressLint("RestrictedApi")
class StealthWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val state = currentState<Preferences>()
            val revealUntil = state[longPreferencesKey(REVEAL_UNTIL)] ?: 0L
            val image = if (revealUntil > System.currentTimeMillis()) latestPhotoBitmap(context) else null
            if (image != null) {
                Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(ComponentName(context, MainActivity::class.java)))) {
                    Image(provider = BitmapImageProvider(image), contentDescription = "Latest photo", modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Text("Visible · tap to open", modifier = GlanceModifier.padding(16.dp), style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp))
                }
            } else {
                Box(
                    GlanceModifier.fillMaxSize()
                        .background(ColorProvider(Color(0xFF171B18)))
                        .clickable(actionRunCallback<RevealCallback>())
                        .padding(18.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Column(horizontalAlignment = Alignment.Start, verticalAlignment = Alignment.Bottom) {
                        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), style = TextStyle(color = ColorProvider(Color(0xFFE8EEE9)), fontSize = 30.sp))
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
        if (now - lastTap > DOUBLE_TAP_WINDOW_MS) {
            preferences.edit().putLong(LAST_TAP, now).apply()
            return
        }
        val until = now + REVEAL_DURATION_MS
        preferences.edit().putLong(LAST_TAP, 0L).putLong(REVEAL_UNTIL, until).apply()
        updateAppWidgetState(context, glanceId) { state -> state[longPreferencesKey(REVEAL_UNTIL)] = until }
        val reset = PendingIntent.getBroadcast(context, 921, Intent(context, StealthResetReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val canUseExactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canUseExactAlarm) {
            runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, reset)
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, reset)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, reset)
        }
        // Update the instance that received the gesture, then keep duplicate instances in sync.
        StealthWidget().update(context, glanceId)
        StealthWidget().updateAll(context)
    }
}

class StealthResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        resetStealth(context)
    }
}

class StealthWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StealthWidget() }

private fun latestPhotoBitmap(context: Context): Bitmap? {
    val raw = context.getSharedPreferences("photo_archive", Context.MODE_PRIVATE).getString("photos", null) ?: return null
    return runCatching {
        val localDeviceId = DeviceIdentity.id(context)
        val photos = JSONArray(raw)
        val received = (0 until photos.length())
            .map { photos.getJSONObject(it) }
            .filter { storedSenderId(it.optString("senderDeviceId"), it.optString("filename"), localDeviceId) != localDeviceId }
            .maxByOrNull { it.optLong("capturedAt", 0L) }
            ?: return@runCatching null
        val file = File(received.getString("path"))
        if (!file.exists()) return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 640) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

internal fun resetStealth(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(LAST_TAP, 0L).putLong(REVEAL_UNTIL, 0L).apply()
    MainScope().launch {
        val ids = GlanceAppWidgetManager(context).getGlanceIds(StealthWidget::class.java)
        ids.forEach { id -> updateAppWidgetState(context, id) { state -> state[longPreferencesKey(REVEAL_UNTIL)] = 0L } }
        StealthWidget().updateAll(context)
    }
}
