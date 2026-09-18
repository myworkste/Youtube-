package com.auraplay.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.ServiceCompat
import com.auraplay.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MediaPlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: MediaNotificationManager
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isForegroundServiceStarted = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Acquire WakeLock & WifiLock for reliable background streaming
        acquireLocks()

        // Initialize MediaSessionCompat
        initMediaSession()

        // Notification Manager
        notificationManager = MediaNotificationManager(this, mediaSession)

        // Listen for playback state updates from WebView / Bridge
        serviceScope.launch {
            PlaybackStateHolder.playbackState.collectLatest { data ->
                if (data.hasActiveMedia) {
                    syncMediaSession(data)
                    updateNotification()
                } else if (!data.isPlaying && isForegroundServiceStarted) {
                    // Update notification if media paused
                    updateNotification()
                }
            }
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "AuraPlayMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    requestAudioFocus()
                    PlaybackStateHolder.onPlayRequested?.invoke()
                }

                override fun onPause() {
                    PlaybackStateHolder.onPauseRequested?.invoke()
                }

                override fun onSkipToNext() {
                    // Seek forward 10 seconds or play next
                    PlaybackStateHolder.onSeekForwardRequested?.invoke()
                }

                override fun onSkipToPrevious() {
                    // Seek back 10 seconds or play previous
                    PlaybackStateHolder.onSeekBackwardRequested?.invoke()
                }

                override fun onStop() {
                    stopServiceGracefully()
                }

                override fun onSeekTo(pos: Long) {
                    // Seek to position
                }
            })

            isActive = true
        }
    }

    private fun syncMediaSession(data: MediaPlaybackData) {
        val state = if (data.isPlaying) {
            PlaybackStateCompat.STATE_PLAYING
        } else {
            PlaybackStateCompat.STATE_PAUSED
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, data.positionMs, 1.0f)
            .build()

        mediaSession.setPlaybackState(playbackState)

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, data.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, data.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, data.durationMs)

        if (data.artworkBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, data.artworkBitmap)
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    fun updateNotification() {
        val data = PlaybackStateHolder.playbackState.value
        val notification = notificationManager.buildNotification(data)

        if (!isForegroundServiceStarted) {
            val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                MediaNotificationManager.NOTIFICATION_ID,
                notification,
                foregroundType
            )
            isForegroundServiceStarted = true
        } else {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(MediaNotificationManager.NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MediaNotificationManager.ACTION_PLAY -> {
                requestAudioFocus()
                PlaybackStateHolder.onPlayRequested?.invoke()
            }
            MediaNotificationManager.ACTION_PAUSE -> {
                PlaybackStateHolder.onPauseRequested?.invoke()
            }
            MediaNotificationManager.ACTION_NEXT -> {
                PlaybackStateHolder.onSeekForwardRequested?.invoke()
            }
            MediaNotificationManager.ACTION_PREV -> {
                PlaybackStateHolder.onSeekBackwardRequested?.invoke()
            }
            MediaNotificationManager.ACTION_STOP -> {
                stopServiceGracefully()
            }
        }
        return START_NOT_STICKY
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()

            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app took full focus (e.g. phone call, other music app)
                PlaybackStateHolder.onPauseRequested?.invoke()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss (e.g. notification alert)
                PlaybackStateHolder.onPauseRequested?.invoke()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Focus regained
            }
        }
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AuraPlay:MediaPlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // Initial acquire timeout (refreshed while active)
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "AuraPlay:MediaPlaybackWifiLock"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {}
    }

    private fun stopServiceGracefully() {
        PlaybackStateHolder.onStopRequested?.invoke()
        PlaybackStateHolder.onPauseRequested?.invoke()
        mediaSession.isActive = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }

        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        isForegroundServiceStarted = false
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession.release()
        releaseLocks()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
