package com.auraplay.app.service

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaPlaybackData(
    val isPlaying: Boolean = false,
    val title: String = "AuraPlay Media",
    val artist: String = "Ready to play",
    val artworkUrl: String? = null,
    val artworkBitmap: Bitmap? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isLowDataMode: Boolean = false,
    val adBlockedCount: Int = 0,
    val hasActiveMedia: Boolean = false
)

object PlaybackStateHolder {

    private val _playbackState = MutableStateFlow(MediaPlaybackData())
    val playbackState: StateFlow<MediaPlaybackData> = _playbackState.asStateFlow()

    // Callbacks for media command dispatch back to WebView
    var onPlayRequested: (() -> Unit)? = null
    var onPauseRequested: (() -> Unit)? = null
    var onSeekForwardRequested: (() -> Unit)? = null
    var onSeekBackwardRequested: (() -> Unit)? = null
    var onStopRequested: (() -> Unit)? = null

    fun updatePlaybackState(
        isPlaying: Boolean,
        title: String?,
        artist: String?,
        durationMs: Long,
        positionMs: Long,
        artworkUrl: String?
    ) {
        val current = _playbackState.value
        val cleanTitle = if (!title.isNullOrBlank()) title else current.title
        val cleanArtist = if (!artist.isNullOrBlank()) artist else current.artist
        val newArtUrl = if (!artworkUrl.isNullOrBlank()) artworkUrl else current.artworkUrl

        _playbackState.value = current.copy(
            isPlaying = isPlaying,
            title = cleanTitle,
            artist = cleanArtist,
            durationMs = durationMs,
            positionMs = positionMs,
            artworkUrl = newArtUrl,
            hasActiveMedia = true
        )
    }

    fun updateArtworkBitmap(bitmap: Bitmap?) {
        _playbackState.value = _playbackState.value.copy(artworkBitmap = bitmap)
    }

    fun setLowDataMode(isLowData: Boolean) {
        _playbackState.value = _playbackState.value.copy(isLowDataMode = isLowData)
    }

    fun incrementAdBlockedCount() {
        val current = _playbackState.value
        _playbackState.value = current.copy(adBlockedCount = current.adBlockedCount + 1)
    }

    fun resetMedia() {
        val count = _playbackState.value.adBlockedCount
        _playbackState.value = MediaPlaybackData(adBlockedCount = count)
    }
}
