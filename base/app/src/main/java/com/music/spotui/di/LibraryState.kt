package com.music.spotui.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Saved state of a library item (track in Liked Songs, saved album, followed artist)
 * as Spotify reports it. Every screen reads the same [SongPlayer.library], so a like
 * made anywhere (here or on another device) shows everywhere.
 */
@Composable
fun rememberSaved(uri: String, fallback: Boolean = false): Boolean {
    LaunchedEffect(uri) { SongPlayer.requestSaved(uri) }
    return SongPlayer.library[uri] ?: fallback
}

@Composable
fun rememberTrackSaved(trackId: String): Boolean =
    if (trackId.isBlank()) false else rememberSaved("spotify:track:$trackId")

/** Account-qualified identity for a song's library action; local files have none. */
fun songLibraryUri(song: com.music.spotui.data.entity.SongsModel, youtubeAccount: Boolean): String? {
    val native = song.url.removePrefix("catalog:")
    if (youtubeAccount && native.startsWith("ytmusic:track:") && app.myspoti.ytmusic.Ytm.isVideoId(native.removePrefix("ytmusic:track:"))) return native
    return song.spotifyTrackId.takeIf { it.isNotBlank() && !youtubeAccount }?.let { "spotify:track:$it" }
}

@Composable
fun rememberSongSaved(song: com.music.spotui.data.entity.SongsModel): Boolean {
    val uri = songLibraryUri(song, YtmRuntime.chosen) ?: return false
    return rememberSaved(uri)
}

/**
 * Whether a track is liked, for a row of a list: what Spotify last reported ([SongPlayer.library], which a like
 * made anywhere moves at once), else the list's own batch check [known] (the uris it asked about in one go).
 */
fun trackSaved(trackId: String, known: Set<String>): Boolean {
    val uri = "spotify:track:$trackId"
    return SongPlayer.library[uri] ?: (uri in known)
}

/** Saved state using the selected account's identity, including native YouTube tracks. */
fun songSaved(song: com.music.spotui.data.entity.SongsModel, known: Set<String>, youtubeAccount: Boolean): Boolean {
    val uri = songLibraryUri(song, youtubeAccount) ?: return false
    return SongPlayer.library[uri] ?: (uri in known)
}
