package com.music.spotui.extensions

import app.myspoti.extensions.AudioStream
import app.myspoti.extensions.TrackInfo
import com.music.spotui.di.Graph
import com.music.spotui.youtube.MatchQuery
import com.music.spotui.youtube.MatchStore
import com.music.spotui.youtube.MeloSearch
import com.music.spotui.youtube.MeloStreamResolver
import com.music.spotui.youtube.TrackMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** Melo's match and stream resolver, used as a built-in audio source. */
internal object YouTubeAudio {
    private val locks = Array(64) { Any() }
    private val refusedClients = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val store by lazy { MatchStore(Graph.app) }
    suspend fun resolve(track: TrackInfo): AudioStream? = try {
        runInterruptible(Dispatchers.IO) {
            synchronized(locks[(track.id.hashCode() and Int.MAX_VALUE) % locks.size]) {
                val started = android.os.SystemClock.elapsedRealtime()
                val q = MatchQuery(track.id, track.title, track.artist, track.durationMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(), track.album)
                if (q.key.isBlank() || q.title.isBlank() || q.artists.isBlank()) return@synchronized null
                var video = store.find(q.key)
                if (video == null) {
                    val text = TrackMatcher.searchText(q)
                    val songs = MeloSearch.search(text, false)
                    val best = TrackMatcher.bestMatch(q, songs, emptyList())
                    val chosen = if (best != null && best.second >= TrackMatcher.CONFIDENT) best else
                        TrackMatcher.bestMatch(q, songs, MeloSearch.search(text, true))
                    video = chosen?.first?.id ?: return@synchronized null
                    store.remember(q.key, video)
                }
                val excluded = refusedClients.remove(video)?.let(::setOf).orEmpty()
                val stream = MeloStreamResolver.stream(video, excluded)
                val remaining = (stream.expiresAtMs - System.currentTimeMillis() - 60_000).coerceAtLeast(1_000)
                app.myspoti.host.android.Logbook.log("melo_youtube: resolved ${q.key} in ${android.os.SystemClock.elapsedRealtime() - started} ms")
                AudioStream(url = stream.url, headers = mapOf("User-Agent" to stream.userAgent), mimeType = stream.mimeType.substringBefore(';'),
                    durationMs = null, providerId = YOUTUBE_SOURCE, expiresInMs = remaining,
                    codec = if (stream.mimeType.contains("opus", ignoreCase = true)) "opus" else "aac",
                    source = "YouTube:$video:${stream.contentLength}")
            }
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: InterruptedException) { Thread.currentThread().interrupt(); throw e }
    catch (e: Exception) {
        app.myspoti.host.android.Logbook.log("melo_youtube: lookup failed ${e.javaClass.simpleName}")
        null
    }
    fun refused(track: TrackInfo) {
        store.find(track.id)?.let {
            MeloStreamResolver.cachedStream(it)?.client?.let { client -> refusedClients[it] = client }
            MeloStreamResolver.forgetStream(it)
            MeloStreamResolver.renewIdentity()
        }
    }
}
