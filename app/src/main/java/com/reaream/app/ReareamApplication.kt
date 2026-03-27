package com.reaream.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class ReareamApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                STREAMING_CHANNEL_ID,
                getString(R.string.stream_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for active streaming sessions"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val STREAMING_CHANNEL_ID = "streaming_channel"
    }
}
