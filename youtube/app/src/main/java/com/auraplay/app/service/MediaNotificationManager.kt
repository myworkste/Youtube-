package com.auraplay.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.auraplay.app.AuraApplication
import com.auraplay.app.MainActivity
import com.auraplay.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MediaNotificationManager(
    private val service: MediaPlaybackService,
    private val mediaSession: MediaSessionCompat
) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var lastLoadedUrl: String? = null

    companion object {
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "com.auraplay.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.auraplay.app.ACTION_PAUSE"
        const val ACTION_NEXT = "com.auraplay.app.ACTION_NEXT"
        const val ACTION_PREV = "com.auraplay.app.ACTION_PREV"
        const val ACTION_STOP = "com.auraplay.app.ACTION_STOP"
    }

    fun buildNotification(data: MediaPlaybackData): Notification {
        // Fetch artwork in background if changed
        if (data.artworkUrl != null && data.artworkUrl != lastLoadedUrl) {
            fetchArtwork(data.artworkUrl)
        }

        val contentIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Notification Actions
        val prevPendingIntent = createActionPendingIntent(ACTION_PREV, 1)
        val playPauseAction = if (data.isPlaying) {
            NotificationCompat.Action.Builder(
                R.drawable.ic_pause,
                service.getString(R.string.media_action_pause),
                createActionPendingIntent(ACTION_PAUSE, 2)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                R.drawable.ic_play,
                service.getString(R.string.media_action_play),
                createActionPendingIntent(ACTION_PLAY, 2)
            ).build()
        }
        val nextPendingIntent = createActionPendingIntent(ACTION_NEXT, 3)
        val stopPendingIntent = createActionPendingIntent(ACTION_STOP, 4)

        // Subtext indicator: highlight low-data audio streaming
        val subtext = if (data.isLowDataMode) {
            service.getString(R.string.media_subtitle_audio_only)
        } else {
            service.getString(R.string.app_name)
        }

        // Spotify-style MediaStyle layout
        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopPendingIntent)

        val builder = NotificationCompat.Builder(service, AuraApplication.CHANNEL_ID_MEDIA)
            .setStyle(mediaStyle)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(data.artworkBitmap ?: getDefaultArtwork(service))
            .setContentTitle(data.title)
            .setContentText(data.artist)
            .setSubText(subtext)
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(data.isPlaying)
            .setOnlyAlertOnce(true)

        // Add actions in order: Prev, Play/Pause, Next, Stop
        builder.addAction(
            R.drawable.ic_skip_previous,
            service.getString(R.string.media_action_prev),
            prevPendingIntent
        )
        builder.addAction(playPauseAction)
        builder.addAction(
            R.drawable.ic_skip_next,
            service.getString(R.string.media_action_next),
            nextPendingIntent
        )
        builder.addAction(
            R.drawable.ic_close,
            service.getString(R.string.media_action_stop),
            stopPendingIntent
        )

        return builder.build()
    }

    private fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(service, MediaPlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            service,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun fetchArtwork(urlStr: String) {
        lastLoadedUrl = urlStr
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    val url = URL(urlStr)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    connection.doInput = true
                    connection.connect()
                    val stream = connection.inputStream
                    BitmapFactory.decodeStream(stream)
                } catch (e: Exception) {
                    null
                }
            }
            if (bitmap != null) {
                PlaybackStateHolder.updateArtworkBitmap(bitmap)
                service.updateNotification()
            }
        }
    }

    private fun getDefaultArtwork(context: Context): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = context.getColor(R.color.bg_card)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)

        val noteDrawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_music_note)
        noteDrawable?.setBounds(24, 24, size - 24, size - 24)
        noteDrawable?.draw(canvas)
        return bitmap
    }
}
