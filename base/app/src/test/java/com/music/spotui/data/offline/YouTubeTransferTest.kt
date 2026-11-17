package com.music.spotui.data.offline

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class YouTubeTransferTest {
    private class Source(val expected: Long, var remaining: Int) : DataSource {
        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long = expected
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0) return C.RESULT_END_OF_INPUT
            val n = minOf(length, remaining); remaining -= n; return n
        }
        override fun getUri(): Uri? = null
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun close() = Unit
    }
    private val spec get() = DataSpec.Builder().setUri("https://example.com/audio").build()
    @Test fun copiesCompleteStream() {
        val file = java.io.File.createTempFile("audio-test", ".tmp")
        try { YouTubeTransfer.copy(Source(120_000, 120_000), spec, file) {}; assertEquals(120_000L, file.length()) }
        finally { file.delete() }
    }
    @Test fun rejectsTruncatedAndUnknownLength() {
        val file = java.io.File.createTempFile("audio-test", ".tmp")
        try {
            for (source in listOf(Source(120_000, 80_000), Source(C.LENGTH_UNSET.toLong(), 120_000))) {
                try { YouTubeTransfer.copy(source, spec, file) {}; fail("incomplete audio must not be accepted") }
                catch (_: IOException) { }
            }
        } finally { file.delete() }
    }
}
