package com.music.spotui.data.local

import org.junit.Assert.*
import org.junit.Test

class QueueHistoryIdentityTest {
    private fun listen(key: String) = ListenHistory.Listen(1, key, "Song", "Artist", "Album", "cover", 31_000, ListenHistory.Source.LOCAL)

    @Test fun youtubeHistoryRetainsNativePlaybackIdentity() {
        val song = ListenHistory.playSong(listen("ytmusic:track:dQw4w9WgXcQ"))!!
        assertEquals("catalog:ytmusic:track:dQw4w9WgXcQ", song.url)
        assertEquals("", song.spotifyTrackId)
    }

    @Test fun spotifyHistoryRetainsSpotifyPlaybackIdentity() {
        val song = ListenHistory.playSong(listen("spotify:track:abc"))!!
        assertEquals("spotify:track:abc", song.url)
        assertEquals("abc", song.spotifyTrackId)
        assertNull(ListenHistory.playSong(listen("local:123")))
    }

    @Test fun checkpointsUpdateOneListenWithoutMergingRepeatedPlays() {
        val first = listen("ytmusic:track:dQw4w9WgXcQ").copy(eventId = "first", playedMs = 30_000)
        val repeat = first.copy(eventId = "second", playedMs = 31_000)
        val update = first.copy(playedMs = 60_000)
        assertEquals(listOf(update, repeat), ListenHistory.collapse(listOf(first, repeat, update)))
        val legacy = first.copy(eventId = "")
        assertEquals(listOf(legacy, legacy), ListenHistory.collapse(listOf(legacy, legacy)))
    }
}
