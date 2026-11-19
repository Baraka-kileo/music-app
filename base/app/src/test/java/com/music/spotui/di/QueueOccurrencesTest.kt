package com.music.spotui.di

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueOccurrencesTest {
    @Test fun `list growth retains repeated songs without readding existing occurrences`() {
        assertEquals(listOf("b", "c", "a"), QueueOccurrences.additions(listOf("a", "b", "a"), listOf("a", "b", "a", "b", "c", "a")) { it })
    }

    @Test fun `empty identities never become queue items`() {
        assertEquals(listOf("b"), QueueOccurrences.additions(listOf("a"), listOf("", "a", "b")) { it })
    }
}
