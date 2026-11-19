package com.music.spotui.data.local

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ListenHistoryPersistenceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun setup() { ListenHistory.init(context); ListenHistory.clear() }
    @After fun cleanup() { ListenHistory.clear() }

    @Test fun checkpointsRemainOneEntryAfterReopeningAndSeparateRepeatedPlays() {
        val one = ListenHistory.Listen(1, "ytmusic:track:dQw4w9WgXcQ", "Song", "Artist", "", "", 30_000, ListenHistory.Source.LOCAL, eventId = "one")
        val two = one.copy(atMs = 2, eventId = "two")
        ListenHistory.record(one)
        ListenHistory.record(one.copy(playedMs = 60_000))
        ListenHistory.record(two)
        ListenHistory.init(context)
        assertEquals(listOf(60_000L, 30_000L), ListenHistory.all().map { it.playedMs })
        assertEquals(listOf("one", "two"), ListenHistory.all().map { it.eventId })
    }
}
