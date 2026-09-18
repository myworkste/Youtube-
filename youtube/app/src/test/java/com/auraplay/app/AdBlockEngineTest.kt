package com.auraplay.app

import com.auraplay.app.webview.AdBlockEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AdBlockEngineTest {

    @Test
    fun testAdBlockEngineBlocksKnownAdUrls() {
        // DoubleClick and Google ad services
        assertTrue(AdBlockEngine.shouldBlockUrl("https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub-123"))
        assertTrue(AdBlockEngine.shouldBlockUrl("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlockEngine.shouldBlockUrl("https://adservice.google.com/adsid/integrator.sync"))
        assertTrue(AdBlockEngine.shouldBlockUrl("https://securepubads.g.doubleclick.net/gampad/ads"))

        // YouTube specific ad endpoints
        assertTrue(AdBlockEngine.shouldBlockUrl("https://m.youtube.com/api/stats/ads?adformat=1_2_3"))
        assertTrue(AdBlockEngine.shouldBlockUrl("https://www.youtube.com/pagead/parallel/rr"))
        assertTrue(AdBlockEngine.shouldBlockUrl("https://m.youtube.com/ptracking?adformat=standard"))
        assertTrue(AdBlockEngine.shouldBlockUrl("https://m.youtube.com/youtubei/v1/player/ad_break"))

        // Third party ad networks
        assertTrue(AdBlockEngine.shouldBlockUrl("https://aax.amazon-adsystem.com/e/dtb/bid"))
        assertTrue(AdBlockEngine.shouldBlockUrl("https://ib.adnxs.com/seg?add=1"))
    }

    @Test
    fun testAdBlockEngineAllowsLegitimateContent() {
        assertFalse(AdBlockEngine.shouldBlockUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(AdBlockEngine.shouldBlockUrl("https://music.youtube.com/browse/FEmusic_home"))
        assertFalse(AdBlockEngine.shouldBlockUrl("https://soundcloud.com/discover"))
        assertFalse(AdBlockEngine.shouldBlockUrl("https://m.twitch.tv/directory"))
        assertFalse(AdBlockEngine.shouldBlockUrl("https://en.wikipedia.org/wiki/Kotlin_(programming_language)"))
        assertFalse(AdBlockEngine.shouldBlockUrl(null))
        assertFalse(AdBlockEngine.shouldBlockUrl(""))
    }

    @Test
    fun testAdStripperScriptsGenerated() {
        val css = AdBlockEngine.getAdHidingCss()
        assertTrue(css.contains(".ytp-ad-module"))
        assertTrue(css.contains("#player-ads"))
        assertTrue(css.contains("display: none !important"))

        val js = AdBlockEngine.getAdStripperJs()
        assertTrue(js.contains("processVideoAds"))
        assertTrue(js.contains(".ytp-ad-skip-button"))
        assertTrue(js.contains("video.playbackRate = 16.0"))
    }
}
