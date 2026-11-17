/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.ytmusic

import app.myspoti.api.AlbumRef
import app.myspoti.api.ArtistRef
import app.myspoti.api.ItemKind
import app.myspoti.api.NowPlaying
import app.myspoti.api.PlayerRestrictions
import app.myspoti.api.PlayerState
import app.myspoti.api.Repeat
import app.myspoti.web.PageBridge
import app.myspoti.web.PageHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The official YouTube Music web app in the hidden page ([host]), heard: the player of a song that
 * is YouTube Music's own. The page plays it as its site does (its own queue and "up next" after the
 * song, its ads left alone, the listen counted by the page itself); this class opens the song, sends
 * play, pause, seek, next and previous to the page's own controls, and reports what the page plays.
 *
 * The same page is the listen sync's unheard mirror ([YtmPage]): each says, with every song it
 * opens, whether the page is heard, so the two never play over one another.
 */
class YtmPlayer(
    private val host: PageHost,
    private val bridge: PageBridge,
    private val scope: CoroutineScope,
    private val loadMs: Long = 40_000,
) {
    private val _state = MutableStateFlow<PlayerState?>(null)
    /** What the page plays while it is heard; null before a song was asked and after [stop]. */
    val state: StateFlow<PlayerState?> = _state

    @Volatile private var heard = false
    @Volatile private var context: String? = null
    /** The song asked that the page has not shown yet: its states of the song before are not ours. */
    @Volatile private var awaited: String? = null
    private val reported = MutableStateFlow<String?>(null)
    private var opening: Job? = null

    init {
        scope.launch {
            bridge.events.collect { (type, value) -> if (type == "state" && heard) read(value)?.let { _state.value = it } }
        }
    }

    /**
     * Plays the song [videoId], in the list [listId] when given (the page then goes on with the
     * list), or the list from its start without a song. [shown]: what the caller knows of the song,
     * reported as loading until the page plays it. Returns once the page was asked: the song shows
     * and plays as the page loads it.
     */
    suspend fun play(videoId: String?, listId: String?, contextUri: String?, positionMs: Long?, shown: NowPlaying?) {
        require(videoId != null || listId != null) { "nothing to play" }
        opening?.cancel()
        heard = true
        context = contextUri
        awaited = videoId
        reported.value = null
        _state.value = shown?.let { state(it, paused = false, buffering = true, positionMs = positionMs ?: 0L, at = System.currentTimeMillis()) }
        val args = buildJsonObject {
            if (videoId != null) put("videoId", videoId) else put("videoId", JsonNull)
            if (listId != null) put("list", listId)
            put("heard", true)
        }
        val gen = bridge.generation
        val opened = if (bridge.ready.value) runCatching { bridge.call("open", args, timeoutMs = 3_000) }.getOrNull() else null
        val inApp = (opened as? JsonPrimitive)?.contentOrNull == "inapp"
        if (opened == null) host.navigate(url(videoId, listId))
        opening = scope.launch {
            val came = withTimeoutOrNull(loadMs) {
                if (!inApp) bridge.ready.first { it && bridge.generation != gen }
                // A page loaded afresh knows nothing of what was asked: told again.
                if (opened == null) runCatching { bridge.call("want", args) }
                reported.first { it != null && (videoId == null || it == videoId) }
            }
            if (came == null) {
                // The page never showed it (no network, a song not there): nothing plays.
                awaited = null
                _state.value = _state.value?.copy(isPaused = true, isBuffering = false)
                return@launch
            }
            if (positionMs != null && positionMs > SEEK_FROM_MS) runCatching { seek(positionMs) }
            runCatching { bridge.call("resume") }
        }
    }

    suspend fun pause() { bridge.call("pause") }
    suspend fun resume() { bridge.call("resume") }
    suspend fun seek(positionMs: Long) { bridge.call("seek", buildJsonObject { put("ms", positionMs) }) }
    suspend fun next() { bridge.call("next") }
    suspend fun previous() { bridge.call("previous") }

    /** The page stops and is unheard again; nothing is reported any more. */
    suspend fun stop() {
        opening?.cancel()
        heard = false
        awaited = null
        _state.value = null
        if (!bridge.ready.value) return
        runCatching { bridge.call("want", buildJsonObject { put("videoId", JsonNull); put("heard", false) }, timeoutMs = 3_000) }
        runCatching { bridge.call("pause", timeoutMs = 3_000) }
    }

    private fun read(e: JsonElement): PlayerState? {
        val o = e as? JsonObject ?: return null
        fun s(k: String) = o[k] as? JsonPrimitive
        val before = _state.value
        // An ad: the song stays shown, waiting, as the page plays the ad.
        if (s("ad")?.booleanOrNull == true) return before?.copy(isPaused = false, isBuffering = true)
        val id = s("trackId")?.contentOrNull ?: return null
        awaited?.let { if (id != it) return null }
        awaited = null
        reported.value = id
        val uri = "${Ytm.PROVIDER}:track:$id"
        val known = before?.item?.takeIf { it.uri == uri }
        val item = NowPlaying(
            kind = ItemKind.TRACK,
            uri = uri,
            uid = null,
            name = s("title")?.contentOrNull?.takeIf { it.isNotBlank() } ?: known?.name.orEmpty(),
            artists = known?.artists?.takeIf { it.isNotEmpty() }
                ?: s("artist")?.contentOrNull?.takeIf { it.isNotBlank() }?.let { listOf(ArtistRef("", it.removeSuffix(" - Topic"))) }.orEmpty(),
            album = known?.album ?: s("album")?.contentOrNull?.takeIf { it.isNotBlank() }?.let { AlbumRef("", it) },
            // The list's own square cover when the song was picked from one; else the page's picture.
            imageUrl = known?.imageUrl?.takeIf { it.isNotBlank() } ?: s("cover")?.contentOrNull,
            durationMs = s("durationMs")?.longOrNull?.takeIf { it > 0 } ?: known?.durationMs ?: 0L,
            provider = "context",
        )
        // The page's next song, shown at once when next is tapped (the page takes seconds to load it).
        val next = (o["next"] as? JsonObject)?.let { n ->
            fun ns(key: String) = (n[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ns("trackId")?.let { nid ->
                NowPlaying(
                    kind = ItemKind.TRACK, uri = "${Ytm.PROVIDER}:track:$nid", uid = null,
                    name = ns("title").orEmpty(),
                    artists = ns("artist")?.substringBefore(" • ")?.let { listOf(ArtistRef("", it.removeSuffix(" - Topic"))) }.orEmpty(),
                    album = null, imageUrl = ns("cover"), durationMs = 0L, provider = "context",
                )
            }
        }
        return state(
            item,
            next = next,
            paused = s("paused")?.booleanOrNull ?: true,
            buffering = s("buffering")?.booleanOrNull ?: false,
            positionMs = s("positionMs")?.longOrNull ?: 0L,
            at = s("at")?.longOrNull ?: System.currentTimeMillis(),
        )
    }

    private fun state(item: NowPlaying, paused: Boolean, buffering: Boolean, positionMs: Long, at: Long, next: NowPlaying? = null) = PlayerState(
        item = item, next = next, contextUri = context, isPaused = paused, isBuffering = buffering,
        positionMs = positionMs, timestampMs = at, speed = 1.0, shuffle = false, repeat = Repeat.OFF,
        restrictions = PlayerRestrictions(),
    )

    private fun url(videoId: String?, listId: String?) = buildString {
        append(Ytm.ORIGIN).append("/watch?")
        if (videoId != null) append("v=").append(videoId)
        if (listId != null) { if (videoId != null) append('&'); append("list=").append(listId) }
    }

    private companion object {
        /** A song asked from nearer its start than this just starts: a seek costs a rebuffer. */
        const val SEEK_FROM_MS = 1_500L
    }
}
