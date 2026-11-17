package com.music.spotui.youtube

import android.content.Context

/** Only stable track/video identifiers are persisted; stream addresses expire separately. */
internal class MatchStore(context: Context) {
    // v2 invalidates old matches made without symmetric version checks.
    private val preferences = context.applicationContext.getSharedPreferences("melo_youtube_matches_v2", Context.MODE_PRIVATE)

    fun find(trackId: String, minimumConfidence: Float = 0f): String? {
        if (preferences.getFloat("$trackId.confidence", 0f) < minimumConfidence) return null
        return preferences.getString(trackId, null)?.takeIf(::validVideoId)
    }

    fun remember(trackId: String, videoId: String, confidence: Float) {
        require(trackId.isNotBlank() && validVideoId(videoId))
        preferences.edit().putString(trackId, videoId).putFloat("$trackId.confidence", confidence.coerceIn(0f, 1f)).apply()
    }

    fun forget(trackId: String) { preferences.edit().remove(trackId).remove("$trackId.confidence").apply() }

    companion object {
        internal fun validVideoId(id: String): Boolean = id.length == 11 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
    }
}
