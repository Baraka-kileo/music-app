import io
B = 'O:/base/'
J = B + 'app/src/main/java/com/music/spotui/'
def edit(path, pairs):
    s = io.open(path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in s
    s = s.replace('\r\n', '\n')
    for a, b in pairs:
        assert s.count(a) == 1, (path, s.count(a), a[:70])
        s = s.replace(a, b)
    if crlf: s = s.replace('\n', '\r\n')
    io.open(path, 'w', encoding='utf-8', newline='').write(s)

edit(B + 'api-ytmusic/src/main/kotlin/app/myspoti/ytmusic/YtmParse.kt', [(
'''    /** Every card of an answer (grids and carousels), in order. */
''', '''    /** The carousels of a page (the home): each one's title, and its cards and rows in order. */
    fun shelves(answer: JsonElement): List<Pair<String?, List<Item>>> =
        deep(answer, "musicCarouselShelfRenderer").map { shelf ->
            val title = deep(shelf.o("header"), "title").firstNotNullOfOrNull(::text)
            title to shelf.a("contents").orEmpty().mapNotNull { c ->
                val o = c.o() ?: return@mapNotNull null
                o.o("musicTwoRowItemRenderer")?.let(::twoRow) ?: o.o("musicResponsiveListItemRenderer")?.let(::row)
            }
        }

    /** Every card of an answer (grids and carousels), in order. */
''')])

edit(J + 'data/catalog/CatalogRuntime.kt', [(
    'MyspotiRuntime.useApi(MetadataApi(MyspotiRuntime.spotify, ::chain, signedIn = { spotify.takeIf { chain().providerOf(it.id) === it } }))',
    '// Over it, the parts of the music account chosen (YouTube Music\'s own library, playlists and home): see AccountApi.\n'
    '        MyspotiRuntime.useApi(com.music.spotui.di.AccountApi(MetadataApi(MyspotiRuntime.spotify, ::chain, signedIn = { spotify.takeIf { chain().providerOf(it.id) === it } })))')])

edit(J + 'data/api/Api.kt', [
    ('''    private suspend fun ready(): Boolean =
        withTimeoutOrNull(30_000) { api.ready.first { it } } != null
''', '''    private suspend fun ready(): Boolean =
        if (com.music.spotui.di.YtmRuntime.chosen) ytmReady() else withTimeoutOrNull(30_000) { api.ready.first { it } } != null

    /**
     * YouTube Music as the music account: its reads need its session, not Spotify's player (waited
     * for 30 s, every screen then said "not signed in"). The session is read at the app's start: a
     * few seconds are left for that.
     */
    private suspend fun ytmReady(): Boolean =
        com.music.spotui.di.YtmRuntime.signedIn ||
            withTimeoutOrNull(5_000) { androidx.compose.runtime.snapshotFlow { com.music.spotui.di.YtmRuntime.signedIn }.first { it } } != null
'''),
    ('''    private suspend fun readyToRead(): Boolean =
        api.ready.value ||''', '''    private suspend fun readyToRead(): Boolean =
        if (com.music.spotui.di.YtmRuntime.chosen) ytmReady() else api.ready.value ||'''),
    ('''        fun invalidatePlaylist(playlistId: String) = PlaylistFresh.drop("spotify:playlist:$playlistId")''',
     '''        fun invalidatePlaylist(playlistId: String) = PlaylistFresh.drop(playlistUri(playlistId))

        /** The uri of the playlist [id]: Spotify's for a bare id, [id] itself when it is another provider's whole uri. */
        fun playlistUri(id: String): String = if (id.contains(':')) id else "spotify:playlist:$id"'''),
    ('''    private fun playlistEntry(p: Playlist) = LibraryEntry(
        spotifyId = p.uri.substringAfterLast(':'),''', '''    private fun playlistEntry(p: Playlist) = LibraryEntry(
        spotifyId = idOf(p.uri),'''),
    ('''    private fun homeItem(c: MediaCard): HomeItem? {
        val id = c.uri.substringAfterLast(':')''', '''    private fun homeItem(c: MediaCard): HomeItem? {
        // Spotify's bare id; another provider's item keeps its whole uri (as its albums already do).
        val id = idOf(c.uri)'''),
    ('''        MediaKind.PLAYLIST -> LibraryEntry(item.uri.substringAfterLast(':'), item.name,''', '''        MediaKind.PLAYLIST -> LibraryEntry(idOf(item.uri), item.name,'''),
    ('''        MediaKind.ALBUM -> LibraryEntry(item.uri.substringAfterLast(':'), item.name,''', '''        MediaKind.ALBUM -> LibraryEntry(idOf(item.uri), item.name,'''),
    ('''        val uri = "spotify:playlist:$playlistId"
        val fresh = if (force) null else PlaylistFresh.get(uri)''', '''        val uri = playlistUri(playlistId)
        val fresh = if (force) null else PlaylistFresh.get(uri)'''),
])

edit(J + 'data/preferences/AccountProviderPref.kt', [(
'''        prefs(c).edit().putString(KEY, v).apply()
''', '''        prefs(c).edit().putString(KEY, v).apply()
        // What the screens kept (home, library, liked songs) is the other account's.
        runCatching { com.music.spotui.data.api.Api.HomeCache.clear() }
''')])
print('ok')
