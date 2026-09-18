package com.auraplay.app.webview

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.auraplay.app.service.PlaybackStateHolder

class WebMediaBridge(private val onPlaybackUpdate: () -> Unit) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPlaybackStateChanged(
        isPlaying: Boolean,
        title: String?,
        artist: String?,
        durationSec: Double,
        positionSec: Double,
        artworkUrl: String?
    ) {
        mainHandler.post {
            val durationMs = (durationSec * 1000).toLong().coerceAtLeast(0L)
            val positionMs = (positionSec * 1000).toLong().coerceAtLeast(0L)
            PlaybackStateHolder.updatePlaybackState(
                isPlaying = isPlaying,
                title = title,
                artist = artist,
                durationMs = durationMs,
                positionMs = positionMs,
                artworkUrl = artworkUrl
            )
            onPlaybackUpdate()
        }
    }

    @JavascriptInterface
    fun onAdBlocked(reason: String?) {
        android.util.Log.d("AuraPlay", "Ad blocked: ${reason ?: "unspecified"}")
        mainHandler.post {
            PlaybackStateHolder.incrementAdBlockedCount()
        }
    }

    companion object {
        const val JS_INTERFACE_NAME = "AuraBridge"

        /**
         * Injects JavaScript into the WebView to monitor HTML5 media playback,
         * extract metadata from MediaSession, and dispatch state to Kotlin.
         */
        fun getMediaObserverJs(): String {
            return """
                (function() {
                    if (window.__auraMediaHooked) return;
                    window.__auraMediaHooked = true;

                    function extractAndNotify() {
                        var video = document.querySelector('video') || document.querySelector('audio');
                        if (!video) return;

                        var isPlaying = !video.paused && !video.ended && video.readyState > 2;
                        var duration = video.duration || 0;
                        var position = video.currentTime || 0;

                        var title = "AuraPlay Media";
                        var artist = "Streaming";
                        var artwork = "";

                        // Check navigator.mediaSession metadata
                        if (navigator.mediaSession && navigator.mediaSession.metadata) {
                            var meta = navigator.mediaSession.metadata;
                            if (meta.title) title = meta.title;
                            if (meta.artist) artist = meta.artist;
                            if (meta.artwork && meta.artwork.length > 0) {
                                artwork = meta.artwork[meta.artwork.length - 1].src;
                            }
                        }

                        // Fallback title for YouTube mobile web
                        if (title === "AuraPlay Media" || title.length === 0) {
                            var ytTitleEl = document.querySelector('h2.slim-video-metadata-title') ||
                                            document.querySelector('ytm-slim-video-metadata-renderer h2') ||
                                            document.querySelector('meta[name="title"]');
                            if (ytTitleEl) {
                                title = ytTitleEl.innerText || ytTitleEl.getAttribute('content') || title;
                            } else if (document.title) {
                                title = document.title.replace(' - YouTube', '').trim();
                            }
                        }

                        // Fallback artist / channel for YouTube mobile web
                        if (artist === "Streaming" || artist.length === 0) {
                            var ytOwnerEl = document.querySelector('.slim-owner-channel-name') ||
                                            document.querySelector('ytm-channel-name');
                            if (ytOwnerEl) {
                                artist = ytOwnerEl.innerText.trim();
                            }
                        }

                        if (window.AuraBridge && typeof window.AuraBridge.onPlaybackStateChanged === 'function') {
                            window.AuraBridge.onPlaybackStateChanged(
                                isPlaying,
                                title,
                                artist,
                                duration,
                                position,
                                artwork
                            );
                        }
                    }

                    // Attach event listeners to any media element
                    function hookMediaElement(el) {
                        if (el.__auraListened) return;
                        el.__auraListened = true;

                        ['play', 'pause', 'playing', 'timeupdate', 'durationchange', 'ended'].forEach(function(ev) {
                            el.addEventListener(ev, extractAndNotify);
                        });
                    }

                    // Scan current elements
                    document.querySelectorAll('video, audio').forEach(hookMediaElement);

                    // Scan future elements
                    var obs = new MutationObserver(function() {
                        document.querySelectorAll('video, audio').forEach(hookMediaElement);
                    });
                    obs.observe(document.body || document.documentElement, { childList: true, subtree: true });

                    // Periodic sync
                    setInterval(extractAndNotify, 2000);
                })();
            """.trimIndent()
        }

        fun sendPlay(webView: WebView) {
            val js = """
                (function() {
                    var v = document.querySelector('video') || document.querySelector('audio');
                    if (v) v.play();
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }

        fun sendPause(webView: WebView) {
            val js = """
                (function() {
                    var v = document.querySelector('video') || document.querySelector('audio');
                    if (v) v.pause();
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }

        fun sendSeek(webView: WebView, secondsOffset: Int) {
            val js = """
                (function() {
                    var v = document.querySelector('video') || document.querySelector('audio');
                    if (v) {
                        v.currentTime = Math.max(0, Math.min(v.duration || 999999, v.currentTime + ($secondsOffset)));
                    }
                })();
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }

        /**
         * Switches media streaming to pure low-data audio mode when app is in the background:
         * 1. Forces player stream quality to lowest tier ('tiny' / 144p) to minimize data consumption.
         * 2. Reduces CSS video scale & opacity to halt rendering pipelines on low-spec Android 13 Go devices.
         */
        fun sendLowDataMode(webView: WebView, enabled: Boolean) {
            val js = if (enabled) {
                """
                (function() {
                    var v = document.querySelector('video');
                    if (v) {
                        // Switch YouTube video player quality to minimum 'tiny' (144p)
                        try {
                            var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                            if (p && typeof p.setPlaybackQualityRange === 'function') {
                                p.setPlaybackQualityRange('tiny', 'tiny');
                            } else if (p && typeof p.setPlaybackQuality === 'function') {
                                p.setPlaybackQuality('tiny');
                            }
                        } catch(e) {}

                        // Suspend visual decoding overhead
                        v.style.opacity = '0.001';
                        v.style.transform = 'scale(0.01)';
                    }
                })();
                """.trimIndent()
            } else {
                """
                (function() {
                    var v = document.querySelector('video');
                    if (v) {
                        // Restore normal video stream quality
                        try {
                            var p = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                            if (p && typeof p.setPlaybackQualityRange === 'function') {
                                p.setPlaybackQualityRange('auto', 'highres');
                            }
                        } catch(e) {}

                        v.style.opacity = '1';
                        v.style.transform = 'none';
                    }
                })();
                """.trimIndent()
            }
            webView.evaluateJavascript(js, null)
        }
    }
}
