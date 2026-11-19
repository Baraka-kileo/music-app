package com.music.spotui.extensions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeVideoIdTest {
    @Test fun acceptsOnlyVideoIds() {
        assertTrue(YouTubeAudio.isVideoId("dQw4w9WgXcQ"))
        assertTrue(YouTubeAudio.isVideoId("aB_-1234567"))
        assertFalse(YouTubeAudio.isVideoId("ytmusic:track:dQw4w9WgXcQ"))
        assertFalse(YouTubeAudio.isVideoId("dQw4w9WgXcQ/other"))
        assertFalse(YouTubeAudio.isVideoId(""))
    }
}
