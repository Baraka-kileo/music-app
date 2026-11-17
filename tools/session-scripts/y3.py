import io
J = 'O:/base/app/src/main/java/com/music/spotui/'
def edit(path, pairs):
    s = io.open(path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in s
    s = s.replace('\r\n', '\n')
    for a, b in pairs:
        assert s.count(a) == 1, (path, s.count(a), a[:70])
        s = s.replace(a, b)
    if crlf: s = s.replace('\n', '\r\n')
    io.open(path, 'w', encoding='utf-8', newline='').write(s)
edit(J + 'di/SongPlayer.kt', [('''            if (m !is com.music.spotui.data.catalog.SpotifyMatch.Found && ytm) {
                // A song only YouTube Music has: its own page plays it.
                app.myspoti.host.android.Logbook.log("catalog: $uri is not on Spotify ($m): YouTube Music plays it")
                main.post { playYtm(uri, null, known, positionMs) }
                return@launch
            }
''', '''            if (m !is com.music.spotui.data.catalog.SpotifyMatch.Found) {
                // Not played by Spotify (not there, no Spotify account): YouTube Music's own page plays
                // it, the song itself when it is YouTube Music's, else the same song found there.
                val onYtm = if (ytm) uri else com.music.spotui.data.catalog.CatalogRuntime.ytmUriFor(uri)
                if (onYtm != null && onYtm.startsWith(YTM_TRACK_PREFIX)) {
                    app.myspoti.host.android.Logbook.log("catalog: $uri is not played on Spotify ($m): YouTube Music plays $onYtm")
                    main.post { playYtm(onYtm, null, known, positionMs) }
                    return@launch
                }
            }
''')])
edit(J + 'data/catalog/CatalogRuntime.kt', [('''    suspend fun spotifyMatchFor(uri: String): SpotifyMatch =''', '''    /** The song [uri] (another provider's) as YouTube Music's own; null when YouTube Music is turned off or has none. */
    suspend fun ytmUriFor(uri: String): String? {
        val c = chain()
        val t = try { c.track(uri) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { return null }
        return try {
            c.counterpartOn("ytmusic", t.name, t.artists.firstOrNull()?.name.orEmpty(), t.durationMs)?.track?.uri
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
    }

    suspend fun spotifyMatchFor(uri: String): SpotifyMatch =''')])
print('ok')
