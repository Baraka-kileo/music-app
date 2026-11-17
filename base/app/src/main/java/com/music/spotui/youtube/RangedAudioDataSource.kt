package com.music.spotui.youtube

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.music.spotui.extensions.MediaDataSource
import java.io.EOFException

/** Bounded reads, following Melo's YouTubeDataSource (GPL-3.0). */
@UnstableApi
internal class RangedAudioDataSource(private val total: Long, private val net: DataSource = MediaDataSource()) : DataSource {
    private lateinit var spec: DataSpec
    private var at = 0L
    private var end = 0L
    private var rangeLeft = 0L
    override fun addTransferListener(transferListener: TransferListener) = net.addTransferListener(transferListener)
    override fun open(dataSpec: DataSpec): Long {
        require(total > 0)
        spec = dataSpec
        at = dataSpec.position
        if (at > total) throw EOFException("Position past end of YouTube stream")
        end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total else minOf(total, at + dataSpec.length)
        if (at < end) openRange()
        return end - at
    }
    private fun openRange() {
        val size = minOf(900_000L, end - at)
        rangeLeft = net.open(spec.buildUpon().setPosition(at).setLength(size).build())
        if (rangeLeft <= 0) throw EOFException("Empty YouTube range")
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (at >= end) return C.RESULT_END_OF_INPUT
        if (rangeLeft == 0L) { net.close(); openRange() }
        val n = net.read(buffer, offset, minOf(length.toLong(), rangeLeft).toInt())
        if (n < 0) throw EOFException("YouTube range ended early")
        at += n; rangeLeft -= n
        return n
    }
    override fun getUri(): Uri? = net.uri
    override fun getResponseHeaders(): Map<String, List<String>> = net.responseHeaders
    override fun close() { net.close(); rangeLeft = 0 }
}
