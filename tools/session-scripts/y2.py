import io
B = 'O:/base/'
J = B + 'app/src/main/java/com/music/spotui/'

def edit(path, pairs):
    s = io.open(path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in s
    s = s.replace('\r\n', '\n')
    for p in pairs:
        a, b = p[0], p[1]
        n = p[2] if len(p) > 2 else 1
        assert s.count(a) == n, (path, s.count(a), a[:70])
        s = s.replace(a, b)
    if crlf: s = s.replace('\n', '\r\n')
    io.open(path, 'w', encoding='utf-8', newline='').write(s)

# ---- the page script: heard or unheard, the song's name and picture, next and previous
edit(B + 'api-ytmusic/src/main/resources/ytmusic/ytm-page.js', [
    ('''  let want = stored();
''', '''  let want = stored();

  // Heard: the page is the app's player of a YouTube Music song (its sound is let through and it
  // goes on with its own queue). Unheard, the default: the listen sync's mirror, as described above.
  // Kept in the tab's sessionStorage with the asked song, across the page load that plays it.
  const HEARD_KEY = 'mspHeard';
  let heard = (() => { try { return window.sessionStorage.getItem(HEARD_KEY) === '1'; } catch (e) { return false; } })();
  const setHeard = (on) => {
    heard = !!on;
    try { if (heard) window.sessionStorage.setItem(HEARD_KEY, '1'); else window.sessionStorage.removeItem(HEARD_KEY); } catch (e) { /* storage refused */ }
    document.querySelectorAll('video, audio').forEach((el) => {
      if (heard) { if (el.volume <= 0.01) el.volume = 1; } else if (el.volume > SILENT) el.volume = SILENT;
    });
  };
'''),
    ("    const quiet = () => { if (el.volume > SILENT) el.volume = SILENT; };",
     "    const quiet = () => { if (!heard && el.volume > SILENT) el.volume = SILENT; };"),
    ("  const unheard = () => { document.querySelectorAll('video, audio').forEach(hold); };",
     "  const unheard = () => { document.querySelectorAll('video, audio').forEach((el) => { hold(el); if (heard && el.volume <= 0.01) el.volume = 1; }); };"),
    ('''    const st = p.getPlayerState();
    return {
      trackId: d.video_id || null,
''', '''    const st = p.getPlayerState();
    // The song's name from the player itself (it changes with the song); the album and the picture
    // from the page's media session once it is on the same song.
    const m = (navigator.mediaSession && navigator.mediaSession.metadata) || null;
    const same = !!(m && d.title && m.title === d.title);
    const art = same && m.artwork && m.artwork.length ? m.artwork[m.artwork.length - 1].src : null;
    return {
      trackId: d.video_id || null,
      title: d.title || (m && m.title) || null,
      artist: (same && m.artist) || d.author || null,
      album: (same && m.album) || null,
      cover: art || (d.video_id ? 'https://i.ytimg.com/vi/' + d.video_id + '/hqdefault.jpg' : null),
'''),
    ('''    if (!s || s.ad || s.paused) return false;
''', '''    if (!s || s.ad || s.paused || heard) return false;
'''),
    ("    const key = [s.trackId, s.paused, s.buffering, s.ad].join('|');",
     "    const key = [s.trackId, s.paused, s.buffering, s.ad, s.title, s.cover].join('|');"),
    ('''    open: (a) => {
      want = String(a.videoId);
      store(want);
      const asked = want;
      const load = () => window.location.assign('/watch?v=' + encodeURIComponent(asked));
      const app = document.querySelector('ytmusic-app');
      if (!app) { setTimeout(load, 0); return 'load'; }
      try {
        app.dispatchEvent(new CustomEvent('yt-navigate', {
          bubbles: true, composed: true,
          detail: { endpoint: { watchEndpoint: { videoId: asked }, commandMetadata: { webCommandMetadata: { url: '/watch?v=' + asked } } } },
        }));
      } catch (e) { setTimeout(load, 0); return 'load'; }
''', '''    open: (a) => {
      setHeard(a.heard === true);
      want = a.videoId ? String(a.videoId) : null;
      store(want);
      const asked = want;
      const list = a.list ? String(a.list) : null;
      const path = '/watch?' + [asked ? 'v=' + encodeURIComponent(asked) : null, list ? 'list=' + encodeURIComponent(list) : null].filter(Boolean).join('&');
      const load = () => window.location.assign(path);
      const app = document.querySelector('ytmusic-app');
      // A list from its start is the page's own to open: loaded.
      if (!app || !asked) { setTimeout(load, 0); return 'load'; }
      try {
        const watch = { videoId: asked };
        if (list) watch.playlistId = list;
        app.dispatchEvent(new CustomEvent('yt-navigate', {
          bubbles: true, composed: true,
          detail: { endpoint: { watchEndpoint: watch, commandMetadata: { webCommandMetadata: { url: path } } } },
        }));
      } catch (e) { setTimeout(load, 0); return 'load'; }
'''),
    ("    want: (a) => { want = a.videoId ? String(a.videoId) : null; store(want); tick(); return want; },",
     "    want: (a) => { if ('heard' in a) setHeard(a.heard === true); want = a.videoId ? String(a.videoId) : null; store(want); tick(); return want; },"),
    ('''    seek: (a) => { needPlayer().seekTo(Math.max(0, Number(a.ms) || 0) / 1000, true); return true; },
''', '''    seek: (a) => { needPlayer().seekTo(Math.max(0, Number(a.ms) || 0) / 1000, true); return true; },
    // The page's own buttons, as a tap on them: its queue decides what comes.
    next: () => { const b = document.querySelector('ytmusic-player-bar .next-button'); if (b) b.click(); else needPlayer().nextVideo(); return true; },
    previous: () => { const b = document.querySelector('ytmusic-player-bar .previous-button'); if (b) b.click(); else needPlayer().previousVideo(); return true; },
'''),
])

# The listen sync's songs are unheard, said with each.
edit(B + 'api-ytmusic/src/main/kotlin/app/myspoti/ytmusic/YtmPage.kt', [
    ('private fun args(videoId: String?) = buildJsonObject { if (videoId != null) put("videoId", videoId) else put("videoId", JsonNull) }',
     'private fun args(videoId: String?) = buildJsonObject { if (videoId != null) put("videoId", videoId) else put("videoId", JsonNull); put("heard", false) }'),
])

edit(J + 'di/YtmRuntime.kt', [
    ('''    /** The page kept in the activity's window, hidden''', '''    /** The page is about to play ([YtmPlayback]), whatever the music account: kept in the activity's window. Main thread. */
    fun wake() { window?.get()?.let(host::attach) }

    /** The page kept in the activity's window, hidden'''),
])

edit(J + 'di/AccountApi.kt', [
    ('''    override val library: Library get()''', '''    /** Spotify's player, and YouTube Music's page for a song that is YouTube Music's own ([YtmPlayback]). */
    private val engine by lazy { YtmPlayback.Engine(base.player) }
    override val player: app.myspoti.api.Player get() = engine

    override val library: Library get()'''),
])

S = J + 'di/SongPlayer.kt'
edit(S, [
    # reconcile: Spotify's page is not read while YouTube Music's plays
    ('kotlinx.coroutines.withTimeoutOrNull(RECONCILE_READ_MS) { runCatching { MyspotiRuntime.spotify.player.refresh() } }',
     'if (!YtmPlayback.active) kotlinx.coroutines.withTimeoutOrNull(RECONCILE_READ_MS) { runCatching { MyspotiRuntime.spotify.player.refresh() } }', 2),
    # a YouTube Music song keeps its own play key
    ('''            url = if (n.kind == ItemKind.EPISODE) "episode:" + n.uri.substringAfterLast(':') else buildSpotifyPlayQuery(id, n.name, artists),''',
     '''            url = when {
                n.kind == ItemKind.EPISODE -> "episode:" + n.uri.substringAfterLast(':')
                n.uri.startsWith(YTM_TRACK_PREFIX) -> com.music.spotui.data.catalog.CatalogRuntime.PLAY_PREFIX + n.uri
                else -> buildSpotifyPlayQuery(id, n.name, artists)
            },'''),
    ('''        if (moved || AccountSync.listen.waitsBack) {''', '''        if ((moved || AccountSync.listen.waitsBack) && !uri.startsWith(YTM_TRACK_PREFIX)) {'''),
    ('''            if (playing && !adState.value) main.post {
                if (_nowPlaying.value.uri == uri) backToExtensions(''', '''            if (playing && !adState.value && !uri.startsWith(YTM_TRACK_PREFIX)) main.post {
                if (_nowPlaying.value.uri == uri) backToExtensions('''),
    # transport: YouTube Music's page does not wait for Spotify's
    ('if (waitReady && !api.ready.value && kotlinx.coroutines.withTimeoutOrNull(READY_WAIT_MS)',
     'if (waitReady && !YtmPlayback.wanted && !api.ready.value && kotlinx.coroutines.withTimeoutOrNull(READY_WAIT_MS)'),
    ('if (pullBack && !checkLater && api.ready.value) takeBackPlayback()', 'if (pullBack && !checkLater && api.ready.value && !YtmPlayback.wanted) takeBackPlayback()'),
    ('if (checkLater && gen == transportGen.get() && api.ready.value) takeBackPlayback()', 'if (checkLater && gen == transportGen.get() && api.ready.value && !YtmPlayback.wanted) takeBackPlayback()'),
    # playSong
    ('{ playCatalogSong(song, context, askExtensions); return }',
     '{ playCatalogSong(song, context, askExtensions, contextUri ?: contextRegistry[song], songs?.firstOrNull { it.url == song }, positionMs); return }'),
    ('''        routeAsk.incrementAndGet(); routingState.value = null
        LocalPlayer.stop()
        cancelBurst()
        val episodeId''', '''        routeAsk.incrementAndGet(); routingState.value = null
        YtmPlayback.asking = false
        LocalPlayer.stop()
        cancelBurst()
        val episodeId'''),
    ('''        if (accountPlays) return false
        app.myspoti.host.android.Logbook.log("audio: no extension plays''', '''        // The song on screen is YouTube Music's own, played by its page: play, pause and skips are the page's.
        if (accountPlays || (trackId.isBlank() && YtmPlayback.active)) return false
        app.myspoti.host.android.Logbook.log("audio: no extension plays'''),
    ('''        cancelBurst()
        if (LocalPlayer.active.value) LocalPlayer.pause()
        else if (playing) { intendPaused(); transport(pullBack = false) { api.player.pause() } }''', '''        cancelBurst()
        val leftYtm = YtmPlayback.leave()
        if (LocalPlayer.active.value) LocalPlayer.pause()
        else if (playing && !leftYtm) { intendPaused(); transport(pullBack = false) { api.player.pause() } }'''),
    # resume: a YouTube Music song the page no longer holds (the app was closed, something else played) is asked again
    ('''        if (!isEpisode && refusedWithoutAccountPlayer(null, trackIdOf(_nowPlaying.value.uri))) return
        Haptics.click(); intendPlaying(); playing = true; positionAt = SystemClock.elapsedRealtime()''', '''        if (!YtmPlayback.active && _nowPlaying.value.uri.startsWith(YTM_TRACK_PREFIX)) {
            Haptics.click(); playYtm(_nowPlaying.value.uri, null, null, getCurrentPosition()); return
        }
        if (!isEpisode && refusedWithoutAccountPlayer(null, trackIdOf(_nowPlaying.value.uri))) return
        Haptics.click(); intendPlaying(); playing = true; positionAt = SystemClock.elapsedRealtime()'''),
    # the catalog song
    ('''    private fun playCatalogSong(song: String, context: Context?, askExtensions: Boolean) {
        val uri = song.removePrefix(com.music.spotui.data.catalog.CatalogRuntime.PLAY_PREFIX)
        app.myspoti.host.android.MyspotiRuntime.scope.launch {''', '''    private fun playCatalogSong(
        song: String, context: Context?, askExtensions: Boolean,
        contextUri: String? = null, known: SongsModel? = null, positionMs: Long? = null,
    ) {
        val uri = song.removePrefix(com.music.spotui.data.catalog.CatalogRuntime.PLAY_PREFIX)
        val ytm = uri.startsWith(YTM_TRACK_PREFIX)
        // With YouTube Music as the music account its songs are its page's to play, in their list.
        if (ytm && YtmRuntime.chosen) { playYtm(uri, contextUri, known, positionMs); return }
        app.myspoti.host.android.MyspotiRuntime.scope.launch {'''),
    ('''            if (m !is com.music.spotui.data.catalog.SpotifyMatch.Found) {
                app.myspoti.host.android.Logbook.log("catalog: $uri not played on Spotify: $m")''', '''            if (m !is com.music.spotui.data.catalog.SpotifyMatch.Found && ytm) {
                // A song only YouTube Music has: its own page plays it.
                app.myspoti.host.android.Logbook.log("catalog: $uri is not on Spotify ($m): YouTube Music plays it")
                main.post { playYtm(uri, null, known, positionMs) }
                return@launch
            }
            if (m !is com.music.spotui.data.catalog.SpotifyMatch.Found) {
                app.myspoti.host.android.Logbook.log("catalog: $uri not played on Spotify: $m")'''),
    ('''    fun buildSpotifyPlayQuery(spotifyTrackId: String, title: String, artist: String): String {''', '''    private const val YTM_TRACK_PREFIX = "ytmusic:track:"

    /**
     * Plays the YouTube Music song [uri] in YouTube Music's own page ([YtmPlayback]), in the list
     * [ctx] when it is one of YouTube Music's (the page goes on with it; else with its own "up
     * next"). [known]: the song as its row showed it, on screen while the page loads. Main thread.
     */
    private fun playYtm(uri: String, ctx: String?, known: SongsModel?, positionMs: Long? = null) {
        userActed()
        routeAsk.incrementAndGet(); routingState.value = null
        LocalPlayer.stop()
        cancelBurst()
        val list = when {
            ctx == null -> null
            ctx.contains("ytmusic:playlist:") -> "ytmusic:playlist:" + ctx.substringAfter("ytmusic:playlist:")
            ctx == LIKED_CONTEXT && YtmRuntime.chosen -> "ytmusic:playlist:LM"
            else -> null
        }
        YtmPlayback.asking = true
        YtmPlayback.show(known?.let {
            app.myspoti.api.NowPlaying(
                kind = ItemKind.TRACK, uri = uri, uid = null, name = it.title,
                artists = listOf(app.myspoti.api.ArtistRef("", it.singer)),
                album = it.album.takeIf { a -> a.isNotBlank() }?.let { a -> app.myspoti.api.AlbumRef(it.albumId, a) },
                imageUrl = app.myspoti.host.android.Covers.large(it.coverUri), durationMs = it.durationMs.toLong(),
                explicit = it.explicit, provider = "context",
            )
        })
        playedByHand = uri
        markPending("", uri)
        app.myspoti.host.android.Logbook.log("ytmusic: $uri asked${list?.let { l -> " in $l" }.orEmpty()}")
        transport(pullBack = false) {
            if (list != null) api.player.play(list, trackUri = uri, positionMs = positionMs?.takeIf { it > 0 })
            else api.player.play(uri, positionMs = positionMs?.takeIf { it > 0 })
        }
    }

    fun buildSpotifyPlayQuery(spotifyTrackId: String, title: String, artist: String): String {'''),
])
print('ok')
