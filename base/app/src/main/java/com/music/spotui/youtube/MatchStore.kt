package com.music.spotui.youtube

import android.content.Context

/** Only stable track/video identifiers are persisted; stream addresses expire separately. */
internal class MatchStore(context: Context) {
    // v2 invalidates old matches made without symmetric version checks.
    private val preferences = context.applicationContext.getSharedPreferences("melo_youtube_matches_v2", Context.MODE_PRIVATE)

    fun find(trackId: String): String? = preferences.getString(trackId, null)?.takeIf(::validVideoId)

    fun remember(trackId: String, videoId: String) {
        require(trackId.isNotBlank() && validVideoId(videoId))
        preferences.edit().putString(trackId, videoId).apply()
    }

    fun forget(trackId: String) { preferences.edit().remove(trackId).apply() }

    companion object {
        internal fun validVideoId(id: String): Boolean = id.length == 11 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
    }
}
