package com.music.spotui.data.offline

import com.music.spotui.data.entity.SongsModel
import org.junit.Assert.*
import org.junit.Test

class OfflineTrackKeyTest {
    private fun song(url: String, spotify: String = "") = SongsModel(1, "Song", "Album", "Artist", "", url, spotify)

    @Test fun `youtube keys are validated and do not collide with legacy spotify keys`() {
        val youtube = song("catalog:ytmusic:track:dQw4w9WgXcQ")
        assertEquals("ytm_dQw4w9WgXcQ", OfflineTrackKey.of(youtube))
        assertEquals("dQw4w9WgXcQ", OfflineTrackKey.youtubeId(OfflineTrackKey.of(youtube)!!))
        assertEquals("legacySpotifyId", OfflineTrackKey.of(song("spotify:track:legacySpotifyId", "legacySpotifyId")))
        assertNull(OfflineTrackKey.of(song("catalog:ytmusic:track:../bad")))
        assertNull(OfflineTrackKey.youtubeId("legacySpotifyId"))
    }
}
