package dev.locket.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) {
                context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
                    .edit().putLong("revealed_until", 0L).apply()
                MainScope().launch { StealthWidget().updateAll(context) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppContextHolder.context = applicationContext
        applyConnectionInvite(intent)
        PhotoSyncScheduler.schedule(applicationContext)
        if (ServerClient(applicationContext).baseUrl.isNotBlank()) DeliveryService.start(applicationContext)
        setContent { LocketApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyConnectionInvite(intent)) recreate()
    }

    private fun applyConnectionInvite(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (intent.action != Intent.ACTION_VIEW || data.scheme != "locket" || data.host != "connect") return false
        val serverUrl = data.getQueryParameter("server")?.trim()?.trimEnd('/') ?: return false
        if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) return false
        getSharedPreferences("connection", MODE_PRIVATE).edit()
            .putString("server_url", serverUrl)
            .putString("access_token", data.getQueryParameter("token").orEmpty().trim())
            .putString("media_key", data.getQueryParameter("key").orEmpty().trim())
            .putString("display_name", data.getQueryParameter("name").orEmpty().trim())
            .apply()
        DeliveryService.start(applicationContext)
        return true
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(screenReceiver)
        super.onStop()
    }
}
