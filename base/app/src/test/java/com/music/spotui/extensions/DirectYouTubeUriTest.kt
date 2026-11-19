package com.music.spotui.extensions

import android.net.Uri
import com.music.spotui.data.entity.SongsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DirectYouTubeUriTest {
    @Test fun nativeVideoHasDistinctCacheIdentity() {
        val uri = ExtensionAudio.youtubeUri("dQw4w9WgXcQ")
        assertEquals("ytm:dQw4w9WgXcQ", uri.lastPathSegment)
        assertEquals("dQw4w9WgXcQ", ExtensionAudio.youtubeVideoId(uri))
        assertNull(ExtensionAudio.youtubeVideoId(Uri.parse("myspoti-ext://track/ytm%3AdQw4w9WgXcQ")))
        assertNull(ExtensionAudio.youtubeVideoId(Uri.parse("myspoti-ext://youtube/ytm%3Abad")))
    }

    @Test fun nativeCatalogTrackKeepsItsOwnVideoAndMetadata() {
        val song = SongsModel(id = 1, title = "Song", album = "Album", singer = "Artist", coverUri = "https://example.test/art", url = "catalog:ytmusic:track:dQw4w9WgXcQ", durationMs = 123000,
            albumId = "ytmusic:album:album1", artistIds = listOf("ytmusic:artist:artist1"), explicit = true)
        val track = ExtensionAudio.youtubeTrack(song)!!
        assertEquals("dQw4w9WgXcQ", ExtensionAudio.youtubeVideoId(track.uri))
        assertEquals("Song", track.title)
        assertEquals(123000L, track.durationMs)
        assertEquals(song.albumId, track.catalogAlbumId)
        assertEquals(song.artistIds, track.catalogArtistIds)
        org.junit.Assert.assertTrue(track.explicit)
        assertNull(ExtensionAudio.youtubeTrack(song.copy(url = "catalog:ytmusic:track:bad")))
        assertNull(ExtensionAudio.youtubeTrack(song.copy(url = "spotify:track:123")))
    }
}
