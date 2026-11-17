package com.music.spotui.youtube

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RangedAudioDataSourceTest {
    private class Fake : DataSource {
        val ranges = mutableListOf<Pair<Long, Long>>()
        var left = 0L
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long { ranges.add(dataSpec.position to dataSpec.length); left = dataSpec.length; return left }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (left == 0L) return C.RESULT_END_OF_INPUT
            val count = minOf(left, length.toLong()).toInt(); left -= count; return count
        }
        override fun getUri(): Uri? = null
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun close() = Unit
    }
    @Test fun readsSuccessiveRangesAndRespectsRequestedLength() {
        val fake = Fake()
        val source = RangedAudioDataSource(2_500_000, fake)
        val spec = DataSpec.Builder().setUri("https://example.com/audio").setPosition(100_000).setLength(1_900_000).build()
        assertEquals(1_900_000L, source.open(spec))
        val bytes = ByteArray(131_072)
        var total = 0
        while (true) { val n = source.read(bytes, 0, bytes.size); if (n == C.RESULT_END_OF_INPUT) break; total += n }
        assertEquals(1_900_000, total)
        assertEquals(listOf(100_000L to 900_000L, 1_000_000L to 900_000L, 1_900_000L to 100_000L), fake.ranges)
        source.close()
    }
}
