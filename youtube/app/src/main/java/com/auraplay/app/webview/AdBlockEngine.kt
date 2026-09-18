package com.auraplay.app.webview

import android.net.Uri

object AdBlockEngine {

    // Common ad, tracking and telemetry domains
    private val AD_HOST_KEYWORDS = hashSetOf(
        "googleads",
        "doubleclick.net",
        "googlesyndication.com",
        "adservice.google.com",
        "pubads.g.doubleclick.net",
        "securepubads.g.doubleclick.net",
        "ad.doubleclick.net",
        "amazon-adsystem.com",
        "adnxs.com",
        "moatads.com",
        "googletagservices.com",
        "serving-sys.com",
        "criteo.com",
        "outbrain.com",
        "taboola.com",
        "scorecardresearch.com",
        "adsafeprotected.com"
    )

    // YouTube specific ad tracking and video ad request paths
    private val YOUTUBE_AD_PATHS = arrayOf(
        "/api/stats/ads",
        "/pagead/",
        "/ptracking",
        "/youtubei/v1/player/ad_break",
        "/youtubei/v1/att/"
    )

    /**
     * Checks if a request URL matches known advertising, tracking or video ad endpoints.
     */
    fun shouldBlockUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false

        var host = ""
        var path = ""
        var query = ""

        try {
            val uri = java.net.URI(url)
            host = uri.host?.lowercase() ?: ""
            path = uri.path?.lowercase() ?: ""
            query = uri.query?.lowercase() ?: ""
        } catch (e: Exception) {
            try {
                val androidUri = android.net.Uri.parse(url)
                host = androidUri.host?.lowercase() ?: ""
                path = androidUri.path?.lowercase() ?: ""
                query = androidUri.query?.lowercase() ?: ""
            } catch (ex: Exception) {
                return false
            }
        }

        // Check general ad hosts
        for (adKeyword in AD_HOST_KEYWORDS) {
            if (host.contains(adKeyword)) {
                return true
            }
        }

        // Check YouTube ad endpoints
        if (host.contains("youtube.com") || host.contains("googlevideo.com")) {
            for (adPath in YOUTUBE_AD_PATHS) {
                if (path.contains(adPath)) {
                    return true
                }
            }
            // Check adformat query parameter
            if (query.contains("adformat=") || query.contains("&ad_type=")) {
                return true
            }
        }

        return false
    }

    /**
     * CSS stylesheet injected into every page to instantly collapse and hide
     * banner ads, sponsored items, overlay cards, and promotional modals.
     */
    fun getAdHidingCss(): String {
        return """
            /* Hide YouTube Video Ads & Banner Overlays */
            .ytp-ad-module,
            .ytp-ad-player-overlay,
            .video-ads,
            .ytp-ad-overlay-container,
            .ytp-ad-message-container,
            #player-ads,
            ytm-promoted-sparkles-web-renderer,
            ytm-companion-ad-renderer,
            ytm-promoted-video-renderer,
            ytm-ad-slot-renderer,
            ytm-mealbar-promo-renderer,
            .ad-container,
            .ad-display,
            .badge-style-type-ad,
            .ytd-action-companion-ad-renderer,
            ytd-banner-promo-renderer,
            /* Generic Web Ad Elements */
            ins.adsbygoogle,
            iframe[id^="google_ads_"],
            div[id^="dfp-ad-"],
            .ad-banner,
            .advertisement {
                display: none !important;
                visibility: hidden !important;
                height: 0 !important;
                width: 0 !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }
        """.trimIndent()
    }

    /**
     * Injected JavaScript that runs on modern media sites (including YouTube mobile).
     * It continuously inspects video playback to detect ad-showing states,
     * fast-forwards through video ads, auto-clicks 'Skip Ad', and dismisses popups.
     */
    fun getAdStripperJs(): String {
        val css = getAdHidingCss().replace("\n", " ").replace("\"", "\\\"")
        return """
            (function() {
                // 1. Inject ad-hiding CSS
                if (!document.getElementById('auraplay-ad-css')) {
                    var style = document.createElement('style');
                    style.id = 'auraplay-ad-css';
                    style.innerHTML = "$css";
                    (document.head || document.documentElement).appendChild(style);
                }

                // 2. Video Ad Fast-Forward & Auto-Skip Engine
                function processVideoAds() {
                    var video = document.querySelector('video');
                    var player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                    var isAdShowing = player && (
                        player.classList.contains('ad-showing') ||
                        player.classList.contains('ad-interrupting')
                    );

                    // If ad is playing on YouTube
                    if (isAdShowing && video) {
                        try {
                            // Mute during ad
                            video.muted = true;
                            // Fast-forward video to end of ad
                            if (!isNaN(video.duration) && video.duration > 0) {
                                video.currentTime = video.duration;
                            } else {
                                video.currentTime = 99999;
                            }
                            // Accelerate playback
                            video.playbackRate = 16.0;
                            if (window.AuraBridge && typeof window.AuraBridge.onAdBlocked === 'function') {
                                window.AuraBridge.onAdBlocked('Video Ad Fast-Forwarded');
                            }
                        } catch(e) {}
                    }

                    // Click any skip button immediately
                    var skipSelectors = [
                        '.ytp-ad-skip-button',
                        '.ytp-ad-skip-button-modern',
                        '.ytp-skip-ad-button',
                        '.ytp-ad-skip-button-slot',
                        'button[class*="skip-button"]'
                    ];
                    for (var i = 0; i < skipSelectors.length; i++) {
                        var btn = document.querySelector(skipSelectors[i]);
                        if (btn && btn.offsetParent !== null) {
                            try {
                                btn.click();
                                if (window.AuraBridge && typeof window.AuraBridge.onAdBlocked === 'function') {
                                    window.AuraBridge.onAdBlocked('Skip Button Clicked');
                                }
                            } catch(e) {}
                            break;
                        }
                    }

                    // Auto-dismiss "Try Premium" or "Open in App" popups
                    var dismissButtons = document.querySelectorAll(
                        'ytm-mealbar-promo-renderer button, .yt-spec-button-shape-next--tonal'
                    );
                    dismissButtons.forEach(function(b) {
                        var text = (b.innerText || '').toLowerCase();
                        if (text.includes('dismiss') || text.includes('no thanks') || text.includes('not now')) {
                            b.click();
                        }
                    });
                }

                // Run continuously via fast interval
                if (!window.__auraAdInterval) {
                    window.__auraAdInterval = setInterval(processVideoAds, 300);
                }

                // Also observe DOM mutations for newly added ad containers
                if (!window.__auraAdObserver) {
                    window.__auraAdObserver = new MutationObserver(function(mutations) {
                        processVideoAds();
                    });
                    window.__auraAdObserver.observe(document.body || document.documentElement, {
                        childList: true,
                        subtree: true
                    });
                }
            })();
        """.trimIndent()
    }
}
