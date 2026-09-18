package com.auraplay.app

import com.auraplay.app.service.PlaybackStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PlaybackStateHolderTest {

    @Before
    fun setup() {
        PlaybackStateHolder.resetMedia()
    }

    @Test
    fun testInitialState() {
        val state = PlaybackStateHolder.playbackState.value
        assertFalse(state.isPlaying)
        assertFalse(state.hasActiveMedia)
        assertFalse(state.isLowDataMode)
    }

    @Test
    fun testUpdatePlaybackState() {
        PlaybackStateHolder.updatePlaybackState(
            isPlaying = true,
            title = "Test Track",
            artist = "Aura Artist",
            durationMs = 180000L,
            positionMs = 30000L,
            artworkUrl = "https://example.com/art.jpg"
        )

        val state = PlaybackStateHolder.playbackState.value
        assertTrue(state.isPlaying)
        assertTrue(state.hasActiveMedia)
        assertEquals("Test Track", state.title)
        assertEquals("Aura Artist", state.artist)
        assertEquals(180000L, state.durationMs)
        assertEquals(30000L, state.positionMs)
        assertEquals("https://example.com/art.jpg", state.artworkUrl)
    }

    @Test
    fun testLowDataModeToggle() {
        PlaybackStateHolder.setLowDataMode(true)
        assertTrue(PlaybackStateHolder.playbackState.value.isLowDataMode)

        PlaybackStateHolder.setLowDataMode(false)
        assertFalse(PlaybackStateHolder.playbackState.value.isLowDataMode)
    }

    @Test
    fun testAdBlockedCountIncrement() {
        val initialCount = PlaybackStateHolder.playbackState.value.adBlockedCount
        PlaybackStateHolder.incrementAdBlockedCount()
        PlaybackStateHolder.incrementAdBlockedCount()
        assertEquals(initialCount + 2, PlaybackStateHolder.playbackState.value.adBlockedCount)
    }
}
