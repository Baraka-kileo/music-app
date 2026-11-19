/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.data.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class PagedFetchTest {
    private fun page(offset: Int, size: Int, total: Int) = (offset until minOf(total, offset + size)).toList()

    @Test fun pagesComeInOrderFourAtATime() = runBlocking {
        val total = 523
        val running = AtomicInteger(); var most = 0
        val batches = mutableListOf<Int>()
        val all = PagedFetch.inOrder(page(0, 50, total), total, 50, fetch = { o ->
            most = maxOf(most, running.incrementAndGet())
            delay((50 - o % 50 + (o / 50) * 7 % 13).toLong()) // answers out of order
            running.decrementAndGet()
            page(o, 50, total)
        }, onBatch = { batches += it.size })
        assertEquals((0 until total).toList(), all)
        assertTrue("at most 4 at a time, saw $most", most in 2..4)
        // The first page, then one update per round of four: 1 + ceil(10 / 4).
        assertEquals(listOf(50, 250, 450, 523), batches)
    }

    @Test fun aShortFirstPageIsTheWholeList() = runBlocking {
        var asked = 0
        val all = PagedFetch.inOrder(page(0, 50, 30), total = 400, pageSize = 50, fetch = { asked++; emptyList<Int>() })
        assertEquals(30, all.size); assertEquals(0, asked)
    }

    @Test fun aListShorterThanItsTotalStops() = runBlocking {
        val all = PagedFetch.inOrder(page(0, 50, 120), total = 1000, pageSize = 50, fetch = { o -> page(o, 50, 120) })
        assertEquals((0 until 120).toList(), all)
    }

    @Test fun followsNextOffsetsWhenTotalsAreOnlyEstimates() = runBlocking {
        val batches = mutableListOf<Int>()
        fun response(offset: Int) = app.myspoti.api.Page(page(offset, 2, 5), offset, offset + 1, (offset + 2).takeIf { it < 5 })
        val all = PagedFetch.following(response(0), fetch = ::response, onPage = { batches += it.offset })
        assertEquals(listOf(0, 1, 2, 3, 4), all)
        assertEquals(listOf(0, 2, 4), batches)
    }

    @Test fun rejectsARepeatedOffset() = runBlocking {
        val first = app.myspoti.api.Page(listOf(1), 0, 2, 1)
        val failure = runCatching { PagedFetch.following(first, fetch = { app.myspoti.api.Page(listOf(2), it, 2, it) }) }.exceptionOrNull()
        assertTrue(failure is java.io.IOException)
    }
}
