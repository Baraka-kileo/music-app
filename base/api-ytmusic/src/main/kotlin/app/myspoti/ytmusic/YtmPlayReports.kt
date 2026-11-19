/* SPDX-License-Identifier: GPL-3.0-only
 *
 * The play-report sequence (playback, atr, watchtime, one client-playback nonce per play, the
 * player answer read with a current signature timestamp) is the one BitChord documents
 * (https://github.com/kushagrasinghx/BitChord, GPL-3.0, data/innertube/PlaybackTracker.kt).
 */
package app.myspoti.ytmusic

import app.myspoti.api.PlayerState
import app.myspoti.api.sync.ListenSync
import app.myspoti.api.sync.ListenSyncHost
import app.myspoti.api.sync.ListenSyncStatus
import app.myspoti.api.sync.LocalNow
import app.myspoti.api.sync.QueueTake
import app.myspoti.api.sync.SyncTrack
import app.myspoti.api.sync.TrackIdResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext

/** Where YouTube Music wants one play reported, from its player answer's `playbackTracking`. */
data class YtmTracking(
    val playbackUrl: String,
    val watchtimeUrl: String?,
    val atrUrl: String?,
    /** How far into the song the atr report is due. */
    val atrAfterSeconds: Long,
    /** The visitor id the answer carried, sent back with the reports. */
    val visitorData: String?,
) {
    companion object {
        private const val DEFAULT_ATR_SECONDS = 5L

        /** Null when the answer has no tracking (refused, unplayable, or a stale signature timestamp). */
        fun of(answer: JsonObject): YtmTracking? {
            val t = answer["playbackTracking"] as? JsonObject ?: return null
            fun url(k: String) = (t[k] as? JsonObject)?.get("baseUrl")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            val playback = url("videostatsPlaybackUrl") ?: return null
            val visitor = (answer["responseContext"] as? JsonObject)?.get("visitorData")?.jsonPrimitive?.contentOrNull
            return YtmTracking(
                playbackUrl = playback,
                watchtimeUrl = url("videostatsWatchtimeUrl"),
                atrUrl = url("atrUrl"),
                atrAfterSeconds = (t["atrUrl"] as? JsonObject)?.get("elapsedMediaTimeSeconds")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: DEFAULT_ATR_SECONDS,
                visitorData = visitor,
            )
        }
    }
}

/** What the reports need of YouTube Music: the play's tracking, and one report sent. */
interface YtmReportApi {
    suspend fun tracking(videoId: String, signatureTimestamp: Int?): YtmTracking?
    /** Sends one report; [asIs]: the url already carries the client's fields (atr). Answers the HTTP status. */
    suspend fun report(baseUrl: String, cpn: String, extra: Map<String, String>, visitorData: String?, asIs: Boolean = false): Int
}

/**
 * The listen sync of YouTube Music by reports: what the app's own player plays, when it is a
 * YouTube Music song, is reported to the account as the web app reports its own plays, so it lands
 * in the account's history and its recommendations. Three reports make one play, all with the same
 * client-playback nonce: "playback" once the song is heard (the history entry), "atr" a few
 * seconds in (a play that happened, not a start), and "watchtime" every [REPORT_EVERY_S] seconds
 * heard and once more when the song ends, is changed or paused (a play with no time behind it
 * reads as a skip).
 *
 * Nothing plays anywhere else: the hidden page that played each song a second time, unheard, for
 * the web app to count it stopped whenever Android held the page in the background, when the web
 * app paused to ask whether anyone was still there, or when the page lost its sign-in.
 *
 * Reports are best-effort: a failure never touches playback. Reading a play's tracking is tried
 * [OPEN_TRIES] times (a song's start is when the network is busiest), then that play is let go.
 * [main] is the thread the host is read on (the local player's).
 */
class YtmPlayReports(
    private val api: YtmReportApi,
    private val host: ListenSyncHost,
    private val scope: CoroutineScope,
    private val main: CoroutineContext,
    private val signatureTimestamp: suspend (videoId: String) -> Int?,
    private val resolver: TrackIdResolver = TrackIdResolver.KnownIds,
    private val nonce: () -> String = ::clientPlaybackNonce,
    private val retryDelayMs: Long = 2_000,
    private val tickMs: Long = 5_000,
) : ListenSync {
    override val provider: String = Ytm.PROVIDER

    private class Play(val videoId: String, val cpn: String) {
        @Volatile var tracking: YtmTracking? = null
        @Volatile var heardS = 0L
        /** Watched time already sent or being sent (set before the request: no report stacks behind a slow one). */
        @Volatile var sentS = 0L
        @Volatile var atrSent = false
        @Volatile var closed = false
    }

    @Volatile private var play: Play? = null
    private var ticker: Job? = null
    /** The song started again from its start by repeat-one: the next event opens a new play. */
    private var repeated = false
    @Volatile private var lastNote: String? = null

    override val running: Boolean get() = play != null
    override val waitsBack: Boolean = false

    override fun attach() { refresh() }

    override fun onLocal(now: LocalNow) {
        if (!host.enabled || host.dataSaver) { end(null, "the setting is off, signed out or the data saver is on"); return }
        val id = resolver.idFor(now.track, provider)?.takeIf(Ytm::isVideoId)
        val current = play
        val heardS = now.positionMs.coerceAtLeast(0) / 1000
        if (current != null && (current.videoId != id || repeated)) end(current.heardS, if (repeated) "repeated" else "another song")
        repeated = false
        if (id == null) return
        val p = play ?: if (now.playing && now.audible) open(id) else return
        p.heardS = maxOf(p.heardS, heardS)
        if (now.playing) {
            progress(p)
            tick()
        } else {
            // Paused: the time heard so far is said now (a pause can be the last thing that happens).
            ticker?.cancel(); ticker = null
            watchtime(p, final = false, state = "paused")
        }
    }

    override fun onLocalRepeat() { repeated = true }

    private fun open(videoId: String): Play {
        val p = Play(videoId, nonce())
        play = p
        scope.launch {
            val tracking = trackingWithRetries(videoId) ?: return@launch
            // Skipped before its tracking came: nothing of it is reported.
            if (p.closed) return@launch
            p.tracking = tracking
            val status = runCatching { api.report(tracking.playbackUrl, p.cpn, emptyMap(), tracking.visitorData) }.getOrElse { if (it is CancellationException) throw it; -1 }
            note(if (status in 200..299) "play of $videoId reported" else "play of $videoId not reported (answer $status)")
            if (!p.closed) progress(p)
        }
        return p
    }

    private suspend fun trackingWithRetries(videoId: String): YtmTracking? {
        repeat(OPEN_TRIES) { attempt ->
            try {
                val sts = runCatching { signatureTimestamp(videoId) }.getOrNull()
                api.tracking(videoId, sts)?.let { return it }
                note("no tracking for $videoId (try ${attempt + 1}, signature timestamp ${sts ?: "unknown"})")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { note("tracking of $videoId failed (try ${attempt + 1}): ${e.javaClass.simpleName}") }
            if (attempt < OPEN_TRIES - 1) delay(retryDelayMs)
        }
        return null
    }

    /** The atr report once due, then the time heard every [REPORT_EVERY_S] seconds. */
    private fun progress(p: Play) {
        val t = p.tracking ?: return
        if (!p.atrSent && t.atrUrl != null && p.heardS >= t.atrAfterSeconds) {
            p.atrSent = true
            scope.launch { runCatching { api.report(t.atrUrl, p.cpn, emptyMap(), t.visitorData, asIs = true) } }
        }
        if (p.heardS - p.sentS >= REPORT_EVERY_S) watchtime(p, final = false, state = "playing")
    }

    private fun watchtime(p: Play, final: Boolean, state: String) {
        val t = p.tracking ?: return
        val url = t.watchtimeUrl ?: return
        val at = p.heardS
        if (!final && at <= p.sentS) return
        p.sentS = maxOf(p.sentS, at)
        val extra = buildMap {
            put("st", "0"); put("et", at.toString()); put("cmt", at.toString()); put("state", state)
            if (final) put("final", "1")
        }
        scope.launch { runCatching { api.report(url, p.cpn, extra, t.visitorData) } }
    }

    /** The play ends (another song, a stop): its time heard is said one last time. */
    private fun end(heardS: Long?, why: String) {
        val p = play ?: return
        play = null
        ticker?.cancel(); ticker = null
        if (heardS != null) p.heardS = maxOf(p.heardS, heardS)
        p.closed = true
        watchtime(p, final = true, state = "paused")
        note("play of ${p.videoId} closed at ${p.heardS} s ($why)")
    }

    /** While a song plays, its position is read every [tickMs] (the player says nothing as it goes on). */
    private fun tick() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive && play != null) {
                delay(tickMs)
                val now = withContext(main) { host.local() } ?: break
                withContext(main) { onLocal(now) }
                if (!now.playing) break
            }
        }
    }

    override fun onProviderState(s: PlayerState) {}
    override fun onProviderPlayback(s: PlayerState, moved: Boolean, intended: Boolean) {}

    override fun stop(reason: String, pauseRemote: Boolean): Boolean {
        val had = play != null
        end(null, reason)
        return had
    }

    override suspend fun awaitEnded() {}
    override fun leaving(device: String) {}
    override fun refresh() { host.local()?.let(::onLocal) }

    override fun status() = ListenSyncStatus(
        provider = provider, on = host.enabled, running = play != null, uri = play?.videoId?.let { "ytmusic:track:$it" },
        paused = null, landed = play?.tracking != null, remotePositionMs = null, handedAway = false, failedUri = null,
        remoteControl = false, note = lastNote,
    )

    override fun localQueueChanged(queued: List<SyncTrack>) {}
    override fun adoptRemoteQueue(localNext: List<SyncTrack>): List<QueueTake> = emptyList()
    override fun remoteQueueTaken(taken: List<SyncTrack>) {}

    private fun note(line: String) { lastNote = line; host.log("ytm reports: $line") }

    companion object {
        const val REPORT_EVERY_S = 30L
        const val OPEN_TRIES = 3
        private const val NONCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        /** A client-playback nonce: 16 characters of the web player's alphabet, new for each play. */
        fun clientPlaybackNonce(): String {
            val r = java.security.SecureRandom()
            return String(CharArray(16) { NONCE_CHARS[r.nextInt(NONCE_CHARS.length)] })
        }
    }
}
