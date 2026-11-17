package com.music.spotui.youtube

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MatchStoreTest {
    @Test fun downloadsRejectUnscoredAndLowConfidenceCachedMatches() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("melo_youtube_matches_v2", Context.MODE_PRIVATE).edit().clear().putString("legacy", "AbCdEfGhI_1").commit()
        val store = MatchStore(context)
        assertEquals("AbCdEfGhI_1", store.find("legacy"))
        assertNull(store.find("legacy", 0.8f))
        store.remember("song", "AbCdEfGhI_1", 0.6f)
        assertNull(store.find("song", 0.8f))
        store.remember("song", "AbCdEfGhI_1", 0.9f)
        assertEquals("AbCdEfGhI_1", store.find("song", 0.8f))
        store.forget("song")
        assertNull(store.find("song"))
    }
}
