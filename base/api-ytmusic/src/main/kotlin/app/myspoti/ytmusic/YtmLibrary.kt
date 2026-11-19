/* SPDX-License-Identifier: GPL-3.0-only
 *
 * The browse ids (FEmusic_liked_playlists, VLLM, FEmusic_history) are those InnerTune and Metrolist use
 * (https://github.com/z-huang/InnerTune, GPL-3.0, innertube/.../YouTube.kt).
 */
package app.myspoti.ytmusic

import app.myspoti.api.Album
import app.myspoti.api.Artist
import app.myspoti.api.ArtistPage
import app.myspoti.api.Catalog
import app.myspoti.api.Episode
import app.myspoti.api.MediaCard
import app.myspoti.api.Page
import app.myspoti.api.Playlist
import app.myspoti.api.Search
import app.myspoti.api.SearchResults
import app.myspoti.api.TopTrack
import app.myspoti.api.Track
import app.myspoti.api.Uri
import app.myspoti.api.account.AccountLibrary
import app.myspoti.ytmusic.YtmParse.Item
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/** The account's library on YouTube Music, read on the user's session ([api]): Liked music, its playlists, its history. */
class YtmLibrary(private val api: InnerTube, private val maxPages: Int = 20) : AccountLibrary {
    override val provider: String get() = Ytm.PROVIDER

    /** The songs of a browse page and of its next pages, until [needed] are there (or the list ends). */
    private suspend fun songs(browseId: String, needed: Int): Pair<List<Track>, Boolean> {
        val out = ArrayList<Track>()
        val seenTokens = HashSet<String>()
        var answer: JsonObject = api.browse(browseId)
        var pages = 1
        while (true) {
            out += YtmParse.rows(answer).filterIsInstance<Item.Song>().map { it.track }
            val token = YtmParse.continuation(answer)
            if (token == null || out.size >= needed) return out to (token != null)
            if (pages >= maxPages) throw java.io.IOException("YouTube Music returned more pages than the safety limit")
            if (!seenTokens.add(token)) throw java.io.IOException("YouTube Music repeated a continuation token")
            answer = api.browseMore(token)
            pages++
        }
    }

    /**
     * Where the reading of a list stopped: the songs read so far and the token of the next page.
     * A page after the first goes on from here instead of reading the list again from its top
     * (a 1000-song playlist cost 55 requests, one more at each page, instead of 10).
     */
    private class Cursor(val tracks: ArrayList<Track>, var token: String?, val seen: HashSet<String>, var pages: Int, val at: Long) {
        val lock = kotlinx.coroutines.sync.Mutex()
    }
    private val cursors = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Cursor>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cursor>) = size > CURSORS_KEPT
    })

    /** A list's first page asked twice at once (its details and its songs): one request answers both. */
    private val shared = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, kotlinx.coroutines.CompletableDeferred<JsonObject>>>()

    internal suspend fun browseShared(browseId: String): JsonObject {
        val now = System.currentTimeMillis()
        val mine = now to kotlinx.coroutines.CompletableDeferred<JsonObject>()
        val got = shared.compute(browseId) { _, old -> if (old != null && now - old.first < SHARE_MS) old else mine }!!
        if (got !== mine) {
            return try { got.second.await() } catch (e: Throwable) {
                // The first asker failed or was cancelled: this one asks for itself.
                kotlinx.coroutines.currentCoroutineContext().let { if (it[kotlinx.coroutines.Job]?.isActive == false) throw e }
                api.browse(browseId)
            }
        }
        return try { api.browse(browseId).also { mine.second.complete(it) } } catch (e: Throwable) {
            shared.remove(browseId, mine); mine.second.completeExceptionally(e); throw e
        }
    }

    private suspend fun page(browseId: String, offset: Int, limit: Int): Page<Track> {
        val needed = offset + limit
        val now = System.currentTimeMillis()
        // The first page is always read anew (a reopened list shows its changes); the next ones
        // follow the reading under way when it is recent.
        val kept = if (offset == 0) null else cursors[browseId]?.takeIf { now - it.at < CURSOR_MS && it.tracks.size >= offset }
        val cursor = kept ?: run {
            val first = browseShared(browseId)
            Cursor(ArrayList(YtmParse.rows(first).filterIsInstance<Item.Song>().map { it.track }), YtmParse.continuation(first), HashSet(), 1, now)
                .also { cursors[browseId] = it }
        }
        val (all, more) = cursor.lock.withLock {
            while (cursor.tracks.size < needed) {
                val token = cursor.token ?: break
                if (cursor.pages >= maxPages) throw java.io.IOException("YouTube Music returned more pages than the safety limit")
                if (!cursor.seen.add(token)) throw java.io.IOException("YouTube Music repeated a continuation token")
                val answer = api.browseMore(token)
                cursor.pages++
                cursor.tracks += YtmParse.rows(answer).filterIsInstance<Item.Song>().map { it.track }
                cursor.token = YtmParse.continuation(answer)
            }
            cursor.tracks.toList() to (cursor.token != null)
        }
        val items = all.drop(offset).take(limit)
        val hasNext = all.size > needed || more
        return Page(items, offset, if (more) all.size + 1 else all.size, if (hasNext) offset + items.size else null)
    }

    override suspend fun likedTracks(offset: Int, limit: Int): Page<Track> = page(LIKED_MUSIC, offset, limit)

    /** A negative membership answer is trustworthy only if the full liked feed was read. */
    internal suspend fun likedTrackUris(): Set<Uri> {
        val (tracks, more) = songs(LIKED_MUSIC, Int.MAX_VALUE)
        if (more) throw java.io.IOException("YouTube Music liked songs exceeded the page limit")
        return tracks.mapTo(HashSet()) { it.uri }
    }

    override suspend fun playlists(): List<Playlist> {
        val out = LinkedHashMap<Uri, Playlist>()
        val seen = HashSet<String>()
        var answer = api.browse(LIKED_PLAYLISTS)
        var pages = 1
        while (true) {
            YtmParse.cards(answer).filterIsInstance<Item.PlaylistItem>().forEach { out[it.playlist.uri] = it.playlist }
            val token = YtmParse.continuation(answer) ?: return out.values.toList()
            if (pages >= maxPages) throw java.io.IOException("YouTube Music playlists exceeded the page limit")
            if (!seen.add(token)) throw java.io.IOException("YouTube Music repeated a playlist continuation token")
            answer = api.browseMore(token)
            pages++
        }
    }

    override suspend fun playlistTracks(uri: Uri, offset: Int, limit: Int): Page<Track> = page("VL" + idOf(uri, "playlist"), offset, limit)

    override suspend fun history(limit: Int): List<Track> =
        YtmParse.rows(api.browse(HISTORY)).filterIsInstance<Item.Song>().map { it.track }.distinctBy { it.uri }.take(limit)

    /** YouTube's radio for a native video; omit the seed itself and retain service order. */
    suspend fun radio(videoId: String): List<Track> =
        YtmParse.watchNextTracks(api.radio(videoId)).filterNot { it.uri == "${Ytm.PROVIDER}:track:$videoId" }

    companion object {
        const val LIKED_MUSIC = "VLLM"
        const val LIKED_PLAYLISTS = "FEmusic_liked_playlists"
        const val HISTORY = "FEmusic_history"

        /** The id of a `ytmusic:{kind}:{id}` uri (or the bare id). */
        fun idOf(uri: Uri, kind: String): String {
            val prefix = "${Ytm.PROVIDER}:$kind:"
            require(!uri.contains(':') || uri.startsWith(prefix)) { "not a YouTube Music $kind: $uri" }
            return uri.removePrefix(prefix)
        }
    }
}

/**
 * YouTube Music's metadata on the user's session, through the app's contract (the catalog's
 * [app.myspoti.catalog.ApiProvider] wraps it): searches, songs, albums and artists. YouTube Music
 * gives no ISRC; kinds it does not have (shows, audiobooks, users, genres) answer empty. One page per
 * search: a later page is empty.
 */
class YtmMetadata(private val api: InnerTube) : Search, Catalog {
    private fun <T> one(items: List<T>, offset: Int, limit: Int) =
        if (offset > 0) Page(emptyList<T>(), offset, items.size, null) else Page(items.take(limit), 0, items.size, null)

    private suspend fun found(query: String) = YtmParse.search(api.search(query))

    override suspend fun all(query: String, limit: Int): SearchResults {
        val items = found(query)
        return SearchResults(
            one(items.filterIsInstance<Item.Song>().map { it.track }, 0, limit),
            one(items.filterIsInstance<Item.ArtistItem>().map { it.artist }, 0, limit),
            one(items.filterIsInstance<Item.AlbumItem>().map { it.album }, 0, limit),
            one(items.filterIsInstance<Item.PlaylistItem>().map { it.playlist }, 0, limit),
        )
    }

    /**
     * One tab of YouTube Music's search, as its web app asks it (the tab's code in `params`), page by
     * page: one plain search only gave the few results of each kind its "All" page shows.
     */
    enum class Tab(val params: String) {
        SONGS("EgWKAQIIAWoMEA4QChADEAQQCRAF"),
        VIDEOS("EgWKAQIQAWoMEA4QChADEAQQCRAF"),
        ALBUMS("EgWKAQIYAWoMEA4QChADEAQQCRAF"),
        ARTISTS("EgWKAQIgAWoMEA4QChADEAQQCRAF"),
        PLAYLISTS("EgeKAQQoAEABagwQDhAKEAMQBBAJEAU="),
    }

    /** The next page's token of a tab's search, by "tab|query|offset" (the screens page by offset). */
    private val nextPages = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) = size > 64
    }

    /** The items of one page of [tab] for [query] from [offset], and the next page's offset. */
    suspend fun tab(query: String, tab: Tab, offset: Int, limit: Int): Pair<List<Item>, Int?> {
        val answer = if (offset == 0) api.search(query, tab.params)
            else synchronized(nextPages) { nextPages["${tab.name}|$query|$offset"] }?.let { api.searchMore(it) } ?: return emptyList<Item>() to null
        val items = YtmParse.rows(answer).ifEmpty { YtmParse.search(answer) }
        val token = YtmParse.continuation(answer)
        val next = if (token != null && items.isNotEmpty()) offset + items.size else null
        if (next != null) synchronized(nextPages) { nextPages["${tab.name}|$query|$next"] = token!! }
        return items.take(limit.coerceAtLeast(items.size)) to next
    }

    private suspend inline fun <reified I : Item, T> tabPage(query: String, tab: Tab, offset: Int, limit: Int, map: (I) -> T): Page<T> {
        val (items, next) = tab(query, tab, offset, limit)
        val list = items.filterIsInstance<I>().map(map)
        return Page(list, offset, next?.let { it + 1 } ?: (offset + list.size), next)
    }

    override suspend fun tracks(query: String, offset: Int, limit: Int) = tabPage<Item.Song, Track>(query, Tab.SONGS, offset, limit) { it.track }
    override suspend fun videos(query: String, offset: Int, limit: Int) = tabPage<Item.Song, Track>(query, Tab.VIDEOS, offset, limit) { it.track }
    override suspend fun artists(query: String, offset: Int, limit: Int) = tabPage<Item.ArtistItem, Artist>(query, Tab.ARTISTS, offset, limit) { it.artist }
    override suspend fun albums(query: String, offset: Int, limit: Int) = tabPage<Item.AlbumItem, Album>(query, Tab.ALBUMS, offset, limit) { it.album }
    override suspend fun playlists(query: String, offset: Int, limit: Int) = tabPage<Item.PlaylistItem, Playlist>(query, Tab.PLAYLISTS, offset, limit) { it.playlist }
    override suspend fun shows(query: String, offset: Int, limit: Int) = Page(emptyList<MediaCard>(), offset, 0, null)
    override suspend fun episodes(query: String, offset: Int, limit: Int) = Page(emptyList<Episode>(), offset, 0, null)
    override suspend fun audiobooks(query: String, offset: Int, limit: Int) = Page(emptyList<MediaCard>(), offset, 0, null)
    override suspend fun users(query: String, offset: Int, limit: Int) = Page(emptyList<MediaCard>(), offset, 0, null)
    override suspend fun genres(query: String, offset: Int, limit: Int) = Page(emptyList<MediaCard>(), offset, 0, null)
    override suspend fun suggestions(query: String, limit: Int): List<String> = emptyList()
    override suspend fun recent(): List<MediaCard> = emptyList()
    override suspend fun clearRecent() {}

    override suspend fun track(uri: Uri): Track {
        val id = YtmLibrary.idOf(uri, "track")
        return YtmParse.watchNextTrack(id, api.next(id)) ?: throw NoSuchElementException("${Ytm.PROVIDER} has no track $id")
    }

    private suspend fun albumPage(uri: Uri) = YtmLibrary.idOf(uri, "album").let { YtmParse.album(it, api.browse(it)) }
    private suspend fun artistOf(uri: Uri) = YtmLibrary.idOf(uri, "artist").let { YtmParse.artist(it, api.browse(it)) }

    override suspend fun album(uri: Uri): Album = albumPage(uri).first
    override suspend fun albumTracks(uri: Uri, offset: Int, limit: Int): Page<Track> {
        val songs = albumPage(uri).second
        return Page(songs.drop(offset).take(limit), offset, songs.size, (offset + limit).takeIf { it < songs.size })
    }
    override suspend fun artist(uri: Uri): Artist = artistOf(uri).artist
    override suspend fun artistTopTracks(uri: Uri): List<Track> = artistOf(uri).songs
    override suspend fun artistPage(uri: Uri): ArtistPage {
        val p = artistOf(uri)
        return ArtistPage(p.artist, p.about, p.artist.images, p.songs.map { TopTrack(it, null) }, p.albums, emptyList(), p.related)
    }
    override suspend fun isrc(trackUri: Uri): String? = null
    override suspend fun discography(uri: Uri, offset: Int, limit: Int): Page<Album> {
        val albums = artistOf(uri).albums
        return Page(albums.drop(offset).take(limit), offset, albums.size, (offset + limit).takeIf { it < albums.size })
    }
    override suspend fun relatedArtists(uri: Uri): List<Artist> = artistOf(uri).related
    override suspend fun appearsOn(uri: Uri): List<Album> = emptyList()
    override suspend fun similarAlbums(trackUri: Uri, limit: Int): List<Album> = emptyList()
}

/** Readings of lists kept to go on from, and how long one stays usable. */
private const val CURSORS_KEPT = 6
private const val CURSOR_MS = 5 * 60_000L
/** How long a first page just asked for answers another ask for it. */
private const val SHARE_MS = 10_000L
