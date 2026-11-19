package app.myspoti.ytmusic

import app.myspoti.api.NowPlaying
import app.myspoti.api.sync.ListenSyncHost
import app.myspoti.api.sync.LocalNow
import app.myspoti.api.sync.SyncTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YtmPlayReportsTest {
    private class Sent(val url: String, val cpn: String, val extra: Map<String, String>)

    private class Api(var trackingAnswers: MutableList<YtmTracking?> = mutableListOf()) : YtmReportApi {
        val sent = mutableListOf<Sent>()
        val asked = mutableListOf<Pair<String, Int?>>()
        override suspend fun tracking(videoId: String, signatureTimestamp: Int?): YtmTracking? {
            asked += videoId to signatureTimestamp
            return if (trackingAnswers.isEmpty()) tracking(videoId) else trackingAnswers.removeAt(0)
        }
        override suspend fun report(baseUrl: String, cpn: String, extra: Map<String, String>, visitorData: String?, asIs: Boolean): Int {
            sent += Sent(baseUrl, cpn, extra); return 204
        }
        companion object {
            fun tracking(id: String) = YtmTracking("https://s/playback?v=$id", "https://s/watchtime?v=$id", "https://s/atr?v=$id", 5, "VIS")
        }
    }

    private class Host(var now: LocalNow?) : ListenSyncHost {
        var on = true
        override fun local() = now
        override fun queued(): List<SyncTrack> = emptyList()
        override val localActive get() = now != null
        override val routing = false
        override val enabled get() = on
        override val dataSaver = false
        override val engineIntended = false
        override fun pause() {}
        override fun resume() {}
        override fun seekTo(ms: Long) {}
        override fun next() {}
        override fun previous() {}
        override fun stop() {}
        override fun playRemotePick(item: NowPlaying, contextUri: String?, positionMs: Long) {}
        override fun playHereAgain() {}
        override fun syncRunning(on: Boolean) {}
        override fun log(line: String) {}
    }

    private fun song(id: String?, at: Long, playing: Boolean = true) = LocalNow(
        track = SyncTrack(ids = id?.let { mapOf(Ytm.PROVIDER to it) }.orEmpty(), title = "t"),
        live = true, playing = playing, positionMs = at, durationMs = 200_000,
    )

    private fun TestScope.reports(api: Api, host: Host): YtmPlayReports {
        val d = StandardTestDispatcher(testScheduler)
        var n = 0
        return YtmPlayReports(api, host, TestScope(d), d, signatureTimestamp = { 20_000 }, nonce = { "cpn${++n}" }, retryDelayMs = 100, tickMs = 5_000)
    }

    @Test fun `one play - playback once heard, atr when due, watchtime as it goes and once at the end`() = runTest {
        val api = Api()
        val host = Host(song("vidAAAAAAA1", 0))
        val r = reports(api, host)
        r.onLocal(host.now!!)
        runCurrent()
        assertEquals(listOf("vidAAAAAAA1" to 20_000), api.asked)
        assertEquals("https://s/playback?v=vidAAAAAAA1", api.sent.single().url)
        // The player goes on: its position is read every 5 s.
        host.now = song("vidAAAAAAA1", 35_000)
        advanceTimeBy(5_001)
        runCurrent()
        val urls = api.sent.map { it.url.substringBefore('?') }
        assertTrue("https://s/atr" in urls, urls.toString())
        val watched = api.sent.single { it.url.startsWith("https://s/watchtime") }
        assertEquals("35", watched.extra["et"])
        assertEquals("playing", watched.extra["state"])
        // Another song: the first is closed with its time, the second opens with its own nonce.
        r.onLocal(song("vidBBBBBBB2", 0))
        runCurrent()
        val last = api.sent.filter { it.url.startsWith("https://s/watchtime?v=vidAAAAAAA1") }.last()
        assertEquals("1", last.extra["final"])
        val second = api.sent.single { it.url == "https://s/playback?v=vidBBBBBBB2" }
        assertTrue(second.cpn != api.sent.first().cpn)
        assertTrue(api.sent.filter { it.url.contains("vidAAAAAAA1") }.all { it.cpn == "cpn1" })
        r.stop("test")
        runCurrent()
    }

    @Test fun `a pause says the time heard, and a song without a YouTube id is not reported`() = runTest {
        val api = Api()
        val host = Host(song("vidAAAAAAA1", 0))
        val r = reports(api, host)
        r.onLocal(host.now!!)
        runCurrent()
        r.onLocal(song("vidAAAAAAA1", 12_000, playing = false))
        runCurrent()
        val paused = api.sent.last()
        assertEquals("12", paused.extra["et"])
        assertEquals("paused", paused.extra["state"])
        r.onLocal(song(null, 0))
        runCurrent()
        assertEquals(1, api.sent.count { it.url.startsWith("https://s/playback") })
        assertEquals(false, r.running)
    }

    @Test fun `tracking is asked again after a refusal, and nothing is sent with the setting off`() = runTest {
        val api = Api(mutableListOf(null, null))
        val host = Host(song("vidAAAAAAA1", 0))
        val r = reports(api, host)
        r.onLocal(host.now!!)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, api.asked.size)
        assertEquals(1, api.sent.size)
        r.stop("test"); runCurrent()
        val before = api.sent.size
        host.on = false
        r.onLocal(song("vidBBBBBBB2", 0))
        runCurrent()
        assertEquals(before, api.sent.size)
    }
}
