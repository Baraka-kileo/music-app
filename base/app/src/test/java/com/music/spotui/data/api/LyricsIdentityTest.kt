package com.music.spotui.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LyricsIdentityTest {
    @Test fun nativeTracksKeepSeparateLyricsAndOfflineKeys() {
        val first = LyricsApi.Track("", "Song", "Artist", "Album", 180_000, catalogUri = "ytmusic:track:dQw4w9WgXcQ")
        val second = first.copy(catalogUri = "ytmusic:track:vidAAAAAAA1")
        assertNotEquals(first.key, second.key)
        assertEquals("ytm_dQw4w9WgXcQ", first.offlineKey)
        assertEquals("", first.trackId)
    }

    @Test fun unidentifiedVersionsDoNotShareAnIdenticalCacheKey() {
        val one = LyricsApi.Track("", "Song", "Artist", "Original", 180_000)
        assertNotEquals(one.key, one.copy(album = "Remix", durationMs = 160_000).key)
    }
}
