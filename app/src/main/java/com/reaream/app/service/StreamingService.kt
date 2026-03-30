package com.reaream.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.reaream.app.MainActivity
import com.reaream.app.R
import com.reaream.app.ReareamApplication
import com.reaream.app.data.SettingsRepository
import com.reaream.app.data.model.AppSettings
import com.reaream.app.data.model.AudioInputMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class StreamingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settingsRepo by lazy { SettingsRepository(applicationContext) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var notificationUpdatesStarted = false
    private var statusText: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundWithNotification()
            ACTION_UPDATE_INFO -> {
                statusText = intent.getStringExtra(EXTRA_STATUS_TEXT).orEmpty()
                serviceScope.launch {
                    val settings = settingsRepo.settings.first()
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(settings, createContentIntent()),
                    )
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val pendingIntent = createContentIntent()
        val initialSettings = runBlocking { settingsRepo.settings.first() }
        val notification = buildNotification(initialSettings, pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!notificationUpdatesStarted) {
            notificationUpdatesStarted = true
            serviceScope.launch {
                settingsRepo.settings.collect { settings ->
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(settings, pendingIntent),
                    )
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createContentIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildNotification(
        settings: AppSettings,
        contentIntent: PendingIntent,
    ): android.app.Notification {
        val sourceLabel = when (settings.audio.inputMode) {
            AudioInputMode.MICROPHONE -> getString(R.string.stream_notification_source_mic)
            AudioInputMode.TEST_TONE -> getString(R.string.stream_notification_source_tone)
        }
        val fallbackText = getString(R.string.stream_notification_text, sourceLabel)

        return NotificationCompat.Builder(this, ReareamApplication.STREAMING_CHANNEL_ID)
            .setContentTitle(getString(R.string.stream_notification_title))
            .setContentText(statusText.ifBlank { fallbackText })
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.reaream.app.action.START_STREAMING"
        const val ACTION_STOP = "com.reaream.app.action.STOP_STREAMING"
        const val ACTION_UPDATE_INFO = "com.reaream.app.action.UPDATE_STREAM_INFO"
        const val EXTRA_STATUS_TEXT = "status_text"

        private const val NOTIFICATION_ID = 1001
    }
}
