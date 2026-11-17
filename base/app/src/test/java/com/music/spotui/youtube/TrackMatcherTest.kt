package com.music.spotui.youtube

import org.junit.Assert.*
import org.junit.Test

class TrackMatcherTest {
    @Test fun spedUpSearchRetainsVersionAndRejectsOriginal() {
        val sped = MatchQuery("sp2", "Someone You Loved (Sped Up)", "Lewis Capaldi", 160_000)
        val original = YouTubeCandidate("original", "Someone You Loved", "Lewis Capaldi", 180_000)
        val version = YouTubeCandidate("sped", "Someone You Loved (Speed Up)", "Lewis Capaldi", 160_000)
        assertTrue(TrackMatcher.searchText(sped).contains("sped up"))
        assertEquals("sped", TrackMatcher.bestMatch(sped, listOf(original, version), emptyList())?.first?.id)
        assertNull(TrackMatcher.bestMatch(sped, listOf(original), emptyList()))
    }
    private val query = MatchQuery("sp1", "Halo", "Beyoncé", 240_000, "Album")
    private fun hit(id: String, title: String = "Halo", artist: String = "Beyoncé", duration: Int = 240_000) =
        YouTubeCandidate(id, title, artist, duration)

    @Test fun officialSongBeatsCover() {
        val result = TrackMatcher.bestMatch(query, listOf(hit("cover", "Halo cover"), hit("official")), emptyList())
        assertEquals("official", result?.first?.id)
        assertTrue(result!!.second >= TrackMatcher.CONFIDENT)
    }
    @Test fun candidateMustHaveRelatedTitle() {
        assertNull(TrackMatcher.bestMatch(query, listOf(hit("other", "Something Else")), emptyList()))
    }
    @Test fun durationAndArtistAffectRanking() {
        val exact = TrackMatcher.score(query, hit("a"))
        assertTrue(exact > TrackMatcher.score(query, hit("b", artist = "Other", duration = 270_000)))
    }
    @Test fun matchesArtistInLongerVideoTitle() {
        assertTrue(TrackMatcher.score(query, hit("v", "Beyonce - Halo (Official Video)", "Label")) >= 0.55)
    }
}
