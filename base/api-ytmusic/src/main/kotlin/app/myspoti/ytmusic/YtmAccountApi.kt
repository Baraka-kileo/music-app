/* SPDX-License-Identifier: GPL-3.0-only */
package app.myspoti.ytmusic

import app.myspoti.api.Album
import app.myspoti.api.BrowsePage
import app.myspoti.api.Category
import app.myspoti.api.Home
import app.myspoti.api.HomeFeed
import app.myspoti.api.HomeFilter
import app.myspoti.api.Library
import app.myspoti.api.LibraryFilter
import app.myspoti.api.LibraryItem
import app.myspoti.api.LibrarySort
import app.myspoti.api.MediaCard
import app.myspoti.api.MediaKind
import app.myspoti.api.Page
import app.myspoti.api.Playlist
import app.myspoti.api.PlaylistAccess
import app.myspoti.api.PlaylistMember
import app.myspoti.api.PlaylistSort
import app.myspoti.api.Playlists
import app.myspoti.api.RecentEntry
import app.myspoti.api.RecentItem
import app.myspoti.api.Recents
import app.myspoti.api.RowAdder
import app.myspoti.api.SavedTrack
import app.myspoti.api.Shelf
import app.myspoti.api.Track
import app.myspoti.api.Uri
import app.myspoti.api.WhatsNewContent
import app.myspoti.api.WhatsNewItem
import app.myspoti.ytmusic.YtmParse.Item
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.JsonObject

/**
 * The app's own contracts ([Library], [Playlists], [Home], [Recents]) answered by YouTube Music, for
 * the screens to show the account's music when YouTube Music is the music account. Reads only, for
 * now: what changes the account (a like, a playlist's songs) is refused with [NotOnYtm].
 *
 * The screens were written for Spotify and build some uris themselves ("spotify:playlist:" + id):
 * with an id that is a whole YouTube Music uri, that prefix is taken off again here ([ytmUri]).
 */

/**
 * Something the app asked of YouTube Music that it does not do here. A cancellation, so a screen
 * that did not expect a refusal stops that load quietly instead of failing the app.
 */
class NotOnYtm(what: String) : kotlinx.coroutines.CancellationException("YouTube Music: $what is not available")

/** [uri] as YouTube Music's own, without a "spotify:playlist:" a Spotify-shaped screen put before it. */
fun ytmUri(uri: Uri): Uri = uri.substringAfter("spotify:playlist:", uri).takeIf { it.startsWith("${Ytm.PROVIDER}:") } ?: uri

private fun <T> onePage(items: List<T>, offset: Int): Page<T> =
    if (offset > 0) Page(emptyList(), offset, items.size, null) else Page(items, 0, items.size, null)

/** Lists read whole at their first page: the screens page by a total YouTube Music does not give. */
private const val WHOLE = 2000

class YtmUserLibrary(private val api: InnerTube, private val lib: YtmLibrary) : Library {
    override suspend fun contains(uris: List<Uri>): List<Boolean> = List(uris.size) { false }
    override suspend fun add(uris: List<Uri>): Unit = throw NotOnYtm("saving")
    override suspend fun remove(uris: List<Uri>): Unit = throw NotOnYtm("removing")

    private suspend fun playlists(): List<LibraryItem> = lib.playlists().map {
        LibraryItem(it.uri, MediaKind.PLAYLIST, it.name, it.owner, it.images, pinned = false, addedAt = null, count = it.trackCount.takeIf { n -> n > 0 })
    }

    private suspend fun albums(): List<LibraryItem> =
        YtmParse.cards(api.browse(LIKED_ALBUMS)).filterIsInstance<Item.AlbumItem>().map { a ->
            LibraryItem(a.album.uri, MediaKind.ALBUM, a.album.name, a.album.artists.joinToString(", ") { it.name }.ifEmpty { null }, a.album.images, pinned = false, addedAt = null)
        }

    private suspend fun artists(): List<LibraryItem> {
        val answer = api.browse(LIBRARY_ARTISTS)
        return (YtmParse.rows(answer) + YtmParse.cards(answer)).filterIsInstance<Item.ArtistItem>().distinctBy { it.artist.uri }.map {
            LibraryItem(it.artist.uri, MediaKind.ARTIST, it.artist.name, null, it.artist.images, pinned = false, addedAt = null)
        }
    }

    override suspend fun items(filter: LibraryFilter, sort: LibrarySort, folderUri: Uri?, offset: Int, limit: Int): Page<LibraryItem> {
        if (offset > 0 || folderUri != null) return Page(emptyList(), offset, 0, null)
        val all = when (filter) {
            // The playlists must be read; the albums beside them may fail without taking the library with them.
            LibraryFilter.ALL -> playlists() + runCatching { albums() }.getOrElse { if (it is kotlinx.coroutines.CancellationException) throw it; emptyList() }
            LibraryFilter.PLAYLISTS -> playlists()
            LibraryFilter.ALBUMS -> albums()
            LibraryFilter.ARTISTS -> artists()
            else -> emptyList()
        }
        return onePage(all, 0)
    }

    override suspend fun likedTracks(offset: Int, limit: Int): Page<SavedTrack> =
        if (offset > 0) Page(emptyList(), offset, 0, null)
        else onePage(lib.likedTracks(0, WHOLE).items.map { SavedTrack(it, it.addedAt) }, 0)

    override suspend fun pin(uri: Uri): Unit = throw NotOnYtm("pinning")
    override suspend fun unpin(uri: Uri): Unit = throw NotOnYtm("pinning")
    override suspend fun createFolder(name: String, parentFolderUri: Uri?): Uri = throw NotOnYtm("folders")
    override suspend fun renameFolder(folderUri: Uri, name: String): Unit = throw NotOnYtm("folders")
    override suspend fun deleteFolder(folderUri: Uri): Unit = throw NotOnYtm("folders")
    override suspend fun moveToFolder(uri: Uri, folderUri: Uri?): Unit = throw NotOnYtm("folders")
    override val changes: Flow<Unit> = emptyFlow()

    private companion object {
        const val LIKED_ALBUMS = "FEmusic_liked_albums"
        const val LIBRARY_ARTISTS = "FEmusic_library_corpus_track_artists"
    }
}

class YtmPlaylists(private val api: InnerTube, private val lib: YtmLibrary) : Playlists {
    override suspend fun playlist(uri: Uri): Playlist {
        val own = ytmUri(uri)
        val answer = api.browse("VL" + YtmLibrary.idOf(own, "playlist"))
        val h = YtmParse.header(answer)
        val name = h?.title ?: throw java.io.IOException("YouTube Music: the playlist was not read")
        val count = h.subtitle.firstNotNullOfOrNull { COUNT.find(it)?.groupValues?.get(1)?.filter(Char::isDigit)?.toIntOrNull() }
        return Playlist(
            uri = own, name = name, description = h.description,
            owner = h.artists.firstOrNull()?.name, images = h.images,
            trackCount = count ?: YtmParse.rows(answer).count { it is Item.Song },
        )
    }

    override suspend fun tracks(uri: Uri, offset: Int, limit: Int): Page<Track> =
        if (offset > 0) Page(emptyList(), offset, 0, null) else onePage(lib.playlistTracks(ytmUri(uri), 0, WHOLE).items, 0)

    override suspend fun sortedTracks(uri: Uri, sort: PlaylistSort, descending: Boolean): List<Track> = tracks(uri, 0, WHOLE).items
    override suspend fun mine(): List<Playlist> = lib.playlists()
    /** None yet: nothing is written to the account from the app. */
    override suspend fun editable(): List<Playlist> = emptyList()

    override suspend fun create(name: String, folderUri: Uri?): Uri = throw NotOnYtm("creating a playlist")
    override suspend fun rename(uri: Uri, name: String): Unit = throw NotOnYtm("renaming a playlist")
    override suspend fun setDescription(uri: Uri, description: String): Unit = throw NotOnYtm("editing a playlist")
    override suspend fun access(uri: Uri): PlaylistAccess = PlaylistAccess.PRIVATE
    override suspend fun setAccess(uri: Uri, access: PlaylistAccess): Unit = throw NotOnYtm("editing a playlist")
    override suspend fun published(uri: Uri): Boolean = false
    override suspend fun setPublished(uri: Uri, published: Boolean): Unit = throw NotOnYtm("editing a playlist")
    override suspend fun setCover(uri: Uri, jpeg: ByteArray?): Unit = throw NotOnYtm("editing a playlist")
    override suspend fun delete(uri: Uri): Unit = throw NotOnYtm("deleting a playlist")
    override suspend fun follow(uri: Uri): Unit = throw NotOnYtm("saving a playlist")
    override suspend fun unfollow(uri: Uri): Unit = throw NotOnYtm("removing a playlist")
    override suspend fun join(uri: Uri, invitationToken: String): PlaylistAccess = throw NotOnYtm("shared playlists")
    override suspend fun collaboratorInvite(uri: Uri): String = throw NotOnYtm("shared playlists")
    override suspend fun members(uri: Uri): List<PlaylistMember> = emptyList()
    override suspend fun leave(uri: Uri): Unit = throw NotOnYtm("shared playlists")
    override suspend fun setMemberCollaborator(uri: Uri, username: String, collaborator: Boolean): Unit = throw NotOnYtm("shared playlists")
    override suspend fun removeMember(uri: Uri, username: String): Unit = throw NotOnYtm("shared playlists")
    override suspend fun stopCollaboration(uri: Uri): Unit = throw NotOnYtm("shared playlists")
    override suspend fun addedBy(uri: Uri): List<RowAdder> = emptyList()
    override suspend fun addTracks(uri: Uri, trackUris: List<Uri>): Unit = throw NotOnYtm("adding to a playlist")
    override suspend fun removeTracks(uri: Uri, uids: List<String>): Unit = throw NotOnYtm("editing a playlist")
    override suspend fun moveTracks(uri: Uri, uids: List<String>, beforeUid: String?): Unit = throw NotOnYtm("editing a playlist")
    override suspend fun recommended(uri: Uri, limit: Int, skip: List<Uri>): List<Track> = emptyList()

    private companion object {
        val COUNT = Regex("""([\d,.\s]+)\s+(songs?|tracks?|titres?|chansons?)""", RegexOption.IGNORE_CASE)
    }
}

class YtmHome(private val api: InnerTube) : Home {
    private fun card(i: Item): MediaCard = when (i) {
        is Item.Song -> MediaCard(i.track.uri, MediaKind.TRACK, i.track.name, i.track.artists.joinToString(", ") { it.name }.ifEmpty { null }, i.track.album?.images.orEmpty())
        is Item.AlbumItem -> MediaCard(i.album.uri, MediaKind.ALBUM, i.album.name, i.album.artists.joinToString(", ") { it.name }.ifEmpty { null }, i.album.images)
        is Item.ArtistItem -> MediaCard(i.artist.uri, MediaKind.ARTIST, i.artist.name, null, i.artist.images)
        is Item.PlaylistItem -> MediaCard(i.playlist.uri, MediaKind.PLAYLIST, i.playlist.name, i.playlist.owner, i.playlist.images)
    }

    override suspend fun feed(filter: HomeFilter, itemsPerShelf: Int): HomeFeed {
        if (filter != HomeFilter.ALL && filter != HomeFilter.MUSIC) return HomeFeed("", listOf(HomeFilter.ALL), emptyList())
        var answer: JsonObject = api.browse(HOME)
        val found = ArrayList(YtmParse.shelves(answer))
        // The home comes in parts: a few more are read, and one failing keeps what was read.
        repeat(MORE_PARTS) {
            val token = YtmParse.continuation(answer) ?: return@repeat
            answer = try { api.browseMore(token) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { return@repeat }
            found += YtmParse.shelves(answer)
        }
        val shelves = found.mapNotNull { (title, items) ->
            val cards = items.map(::card).distinctBy { it.uri }
            if (cards.isEmpty()) null else Shelf(null, title, cards.take(itemsPerShelf), cards.size)
        }
        return HomeFeed("", listOf(HomeFilter.ALL), shelves)
    }

    override suspend fun categories(): List<Category> = emptyList()
    override suspend fun category(uri: Uri): BrowsePage = BrowsePage(uri, "", emptyList())
    override suspend fun whatsNew(offset: Int, limit: Int): Page<Album> = Page(emptyList(), offset, 0, null)
    override suspend fun whatsNewFeed(offset: Int, limit: Int, content: WhatsNewContent): Page<WhatsNewItem> = Page(emptyList(), offset, 0, null)
    override suspend fun markWhatsNewSeen(ids: List<String>) {}

    private companion object {
        const val HOME = "FEmusic_home"
        const val MORE_PARTS = 2
    }
}

class YtmRecents(private val lib: YtmLibrary) : Recents {
    override suspend fun recentEntries(offset: Int, limit: Int): Page<RecentEntry> =
        if (offset > 0) Page(emptyList(), offset, 0, null)
        else onePage(lib.history(limit).map { t ->
            RecentEntry(RecentItem(
                t.uri, MediaKind.TRACK, t.name, t.artists.joinToString(", ") { it.name }.ifEmpty { null },
                t.album?.images.orEmpty(), durationMs = t.durationMs, explicit = t.explicit,
            ))
        }, 0)
}
