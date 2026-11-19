package com.music.spotui.di

import org.junit.Assert.*
import org.junit.Test

class PendingLibraryWritesTest {
    @Test fun `another song does not hide failure`() {
        val writes = PendingLibraryWrites()
        val first = writes.begin("a", false)
        val second = writes.begin("b", true)
        assertEquals(false, writes.finish("a", first, true, succeeded = false))
        assertNull(writes.finish("b", second, false, succeeded = true))
    }

    @Test fun `newest failure restores last confirmed value after rapid taps`() {
        val writes = PendingLibraryWrites()
        val first = writes.begin("a", false)
        val second = writes.begin("a", true)
        assertNull(writes.finish("a", first, true, succeeded = true))
        assertEquals(true, writes.finish("a", second, false, succeeded = false))
        assertFalse(writes.isPending("a"))
    }

    @Test fun `two failures restore original confirmed state`() {
        val writes = PendingLibraryWrites()
        val first = writes.begin("a", false)
        val second = writes.begin("a", true)
        assertNull(writes.finish("a", first, true, succeeded = false))
        assertEquals(false, writes.finish("a", second, false, succeeded = false))
    }

    @Test fun `old session completion cannot finish a new session write`() {
        val writes = PendingLibraryWrites()
        val old = writes.begin("a", false)
        writes.clear()
        val current = writes.begin("a", true)
        assertNull(writes.finish("a", old, true, succeeded = false))
        assertTrue(writes.isPending("a"))
        assertEquals(true, writes.finish("a", current, false, succeeded = false))
    }
}
