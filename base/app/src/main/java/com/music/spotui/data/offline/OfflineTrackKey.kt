package com.music.spotui.data.offline

import com.music.spotui.data.entity.SongsModel

/** Filesystem-safe, provider-qualified identities; legacy Spotify IDs stay unchanged. */
internal object OfflineTrackKey {
    private const val YOUTUBE = "ytm_"
    private const val CATALOG = "catalog:ytmusic:track:"
    private val videoId = Regex("[A-Za-z0-9_-]{11}")

    fun youtube(id: String): String? = id.takeIf(videoId::matches)?.let { YOUTUBE + it }
    fun youtubeId(key: String): String? = key.takeIf { it.startsWith(YOUTUBE) }
        ?.removePrefix(YOUTUBE)?.takeIf(videoId::matches)

    fun of(song: SongsModel): String? {
        if (song.spotifyTrackId.isNotBlank()) return song.spotifyTrackId
        return song.url.takeIf { it.startsWith(CATALOG) }?.removePrefix(CATALOG)?.let(::youtube)
    }
}
