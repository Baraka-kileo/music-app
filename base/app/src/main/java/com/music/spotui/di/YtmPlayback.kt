package com.music.spotui.di

import app.myspoti.api.NowPlaying
import app.myspoti.api.Player
import app.myspoti.api.PlayerState
import app.myspoti.api.Queue
import app.myspoti.api.Repeat
import app.myspoti.host.android.Logbook
import app.myspoti.host.android.MyspotiRuntime
import app.myspoti.ytmusic.NotOnYtm
import app.myspoti.ytmusic.YtmPlayer
import app.myspoti.ytmusic.ytmUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A song that is YouTube Music's own, played by YouTube Music's official page ([YtmPlayer]), with
 * either music account: the account's player the app talks to ([Engine], [app.myspoti.api.MyspotiApi.player])
 * is Spotify's until such a song is asked, the page's while it plays, and Spotify's again at the
 * next Spotify play. Nothing of YouTube Music is made (no WebView) before the first such song.
 */
object YtmPlayback {
    private const val TRACK = "ytmusic:track:"
    private const val PLAYLIST = "ytmusic:playlist:"

    private val on = MutableStateFlow(false)
    /** YouTube Music's page is the player now. */
    val active: Boolean get() = on.value
    /** A YouTube Music song is being asked (the page is not the player yet). */
    @Volatile var asking = false
    val wanted: Boolean get() = asking || on.value

    @Volatile private var shown: NowPlaying? = null
    /** What the app knows of the song it is about to ask ([uri]), shown while the page loads it. */
    fun show(song: NowPlaying?) { shown = song }

    private val player: YtmPlayer by lazy { YtmRuntime.account.let { YtmPlayer(it.host, it.bridge, MyspotiRuntime.scope) } }
    private val ytmState = MutableStateFlow<PlayerState?>(null)
    private var piped = false

    /** The page stops being the player (something else plays): true when it was. */
    fun leave(): Boolean {
        asking = false
        if (!on.value) return false
        on.value = false
        ytmState.value = null
        MyspotiRuntime.scope.launch { runCatching { player.stop() } }
        return true
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    class Engine(private val base: Player) : Player {
        // Unconfined: a state of Spotify's player is this player's state at once, as it was read before.
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        override val state: StateFlow<PlayerState?> =
            on.flatMapLatest { if (it) ytmState else base.state }.stateIn(scope, SharingStarted.Eagerly, base.state.value)
        private val noQueue = MutableStateFlow<Queue?>(null)
        override val queue: StateFlow<Queue?> =
            on.flatMapLatest { if (it) noQueue else base.queue }.stateIn(scope, SharingStarted.Eagerly, base.queue.value)

        override suspend fun play(contextUri: String, trackUri: String?, index: Int?, positionMs: Long?, order: List<String>?) {
            asking = false
            val ctx = ytmUri(contextUri)
            val video = (trackUri ?: ctx).takeIf { it.startsWith(TRACK) }?.removePrefix(TRACK)
            val list = ctx.takeIf { it.startsWith(PLAYLIST) }?.removePrefix(PLAYLIST)?.removePrefix("VL")
            if (video == null && list == null) {
                if (ctx.startsWith("ytmusic:") || trackUri?.startsWith("ytmusic:") == true) throw NotOnYtm("playing $contextUri")
                leave()
                base.play(contextUri, trackUri, index, positionMs, order)
                return
            }
            // Spotify's page stops when it plays; one that is not up is not woken for it.
            if (!on.value && base.state.value?.isPaused == false) runCatching { base.pause() }
            YtmRuntime.wake()
            val p = player
            if (!piped) { piped = true; MyspotiRuntime.scope.launch { p.state.collect { st -> if (st?.item?.uri != ytmState.value?.item?.uri || st?.isPaused != ytmState.value?.isPaused || st?.isBuffering != ytmState.value?.isBuffering) Logbook.log("ytmusic: page state ${st?.item?.uri} paused=${st?.isPaused} waiting=${st?.isBuffering} at ${st?.positionMs}/${st?.item?.durationMs}"); ytmState.value = st } } }
            val song = shown?.takeIf { it.uri == TRACK + video }
            shown = null
            Logbook.log("ytmusic: its page plays ${video ?: "the list"}${list?.let { " in $it" }.orEmpty()}")
            on.value = true
            p.play(video, list, ctx.takeIf { list != null }, positionMs, song)
        }

        override suspend fun pause() { if (active) player.pause() else base.pause() }
        override suspend fun pauseSlowly(fadeMs: Long) { if (active) player.pause() else base.pauseSlowly(fadeMs) }
        override suspend fun resume() { if (active) player.resume() else base.resume() }
        override suspend fun seek(positionMs: Long) { if (active) player.seek(positionMs) else base.seek(positionMs) }
        override suspend fun next() { if (active) player.next() else base.next() }
        override suspend fun previous() { if (active) player.previous() else base.previous() }

        // The page keeps its own queue, shuffle and repeat: not driven from here yet.
        override suspend fun setShuffle(on: Boolean) { if (!active) base.setShuffle(on) }
        override suspend fun setRepeat(mode: Repeat) { if (!active) base.setRepeat(mode) }
        override suspend fun addToQueue(uris: List<String>) { if (!active) base.addToQueue(uris) }
        override suspend fun removeFromQueue(items: List<NowPlaying>) { if (!active) base.removeFromQueue(items) }
        override suspend fun clearQueue() { if (!active) base.clearQueue() }
        override suspend fun skipTo(item: NowPlaying) { if (!active) base.skipTo(item) }
        override suspend fun playNext(uris: List<String>) { if (!active) base.playNext(uris) }
        override suspend fun insertInQueue(uris: List<String>, before: NowPlaying) { if (!active) base.insertInQueue(uris, before) }
        override suspend fun moveInQueue(item: NowPlaying, target: NowPlaying, after: Boolean) { if (!active) base.moveInQueue(item, target, after) }

        override suspend fun volume(): Double = if (active) 1.0 else base.volume()
        override suspend fun setVolume(level: Double) { if (!active) base.setVolume(level) }
        override suspend fun setSpeed(speed: Double) { if (!active) base.setSpeed(speed) }

        /** An extension plays and mirrors its song on Spotify: the page is not the player any more. */
        override suspend fun setLocalMute(on: Boolean) { if (on) leave(); base.setLocalMute(on) }
        override val localMute: Boolean get() = !active && base.localMute
    }
}
