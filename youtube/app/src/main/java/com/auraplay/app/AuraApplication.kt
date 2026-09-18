package com.auraplay.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class AuraApplication : Application() {

    companion object {
        const val CHANNEL_ID_MEDIA = "auraplay_media_playback"
        lateinit var instance: AuraApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    /**
     * Initializes notification channel for foreground media service.
     * Uses IMPORTANCE_LOW matching Spotify / media player standards so that
     * metadata/state updates do not make intrusive sounds or vibrations.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = getString(R.string.notification_channel_name)
            val channelDesc = getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_LOW

            val channel = NotificationChannel(CHANNEL_ID_MEDIA, channelName, importance).apply {
                description = channelDesc
                setShowBadge(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
