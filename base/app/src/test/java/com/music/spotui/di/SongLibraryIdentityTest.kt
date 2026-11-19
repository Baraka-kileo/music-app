package com.music.spotui.di

import com.music.spotui.data.entity.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongLibraryIdentityTest {
    private fun song(url: String, spotify: String = "") = SongsModel(1, "song", "album", "artist", "", url, spotify)

    @Test fun keepsIdentitiesWithinTheirAccount() {
        val yt = song("catalog:ytmusic:track:dQw4w9WgXcQ")
        assertEquals("ytmusic:track:dQw4w9WgXcQ", songLibraryUri(yt, true))
        assertNull(songLibraryUri(yt, false))
        val spotify = song("spotify:track:abc", "abc")
        assertEquals("spotify:track:abc", songLibraryUri(spotify, false))
        assertNull(songLibraryUri(spotify, true))
        assertNull(songLibraryUri(song("catalog:ytmusic:track:not-valid"), true))
    }
}
