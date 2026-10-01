package dev.locket.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeliveryService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_PRESENT) resetStealth(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        serviceScope.launch { listenForEvents() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun listenForEvents() {
        while (serviceScope.isActive) {
            val connection = ServerClient(this).openEventStream()
            if (connection == null) {
                delay(RETRY_DELAY_MS)
                continue
            }
            try {
                connection.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("data:")) PhotoSyncScheduler.runNow(this)
                    }
                }
            } catch (_: CancellationException) {
                throw CancellationException()
            } catch (_: Exception) {
                // Network changes and server restarts are expected; reconnect below.
            } finally {
                connection.disconnect()
            }
            delay(RETRY_DELAY_MS)
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setContentTitle("Locket delivery active")
        .setContentText("Listening for new photos from your private server")
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Photo delivery", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "photo_delivery"
        private const val NOTIFICATION_ID = 401
        private const val RETRY_DELAY_MS = 10_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DeliveryService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DeliveryService::class.java))
        }
    }
}
