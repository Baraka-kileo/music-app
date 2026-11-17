package com.music.spotui.data.offline

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException

/** Does not publish a download: its caller validates and installs the staged file. */
@OptIn(UnstableApi::class)
internal object YouTubeTransfer {
    fun copy(data: DataSource, spec: DataSpec, out: File, yieldPlayback: () -> Unit) {
        val announced = data.open(spec)
        if (announced <= 0) throw IOException("YouTube did not provide a verifiable audio length")
        out.outputStream().buffered().use { target ->
            val bytes = ByteArray(128 * 1024)
            var received = 0L
            var yieldedAt = -900_000L
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("download cancelled")
                if (received - yieldedAt >= 900_000L) { yieldPlayback(); yieldedAt = received }
                val n = data.read(bytes, 0, bytes.size)
                if (n == C.RESULT_END_OF_INPUT) break
                if (n <= 0) throw IOException("empty audio read")
                target.write(bytes, 0, n)
                received += n
                if (received > announced) throw IOException("YouTube audio exceeded expected length")
            }
            if (received != announced) throw IOException("incomplete YouTube audio")
        }
    }
}
