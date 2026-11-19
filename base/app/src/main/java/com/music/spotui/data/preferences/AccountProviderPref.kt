package com.music.spotui.data.preferences

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import app.myspoti.api.account.AccountProviders

/**
 * "Music account": the account provider the listen sync, the account's library and the first metadata
 * provider use (Spotify, YouTube Music, or none), one at a time. Spotify for an install from before,
 * which only had Spotify. Changing it ends the running sync and binds the chosen provider's
 * ([com.music.spotui.di.AccountSync.providerChanged]).
 */
object AccountProviderPref {
    private const val KEY = "account_provider"
    private fun prefs(c: Context) = c.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)

    var id by androidx.compose.runtime.mutableStateOf(initial(com.music.spotui.di.Graph.app))
        private set

    private fun initial(c: Context): String =
        AccountProviders.normalized(prefs(c).getString(KEY, null))

    fun set(c: Context, value: String) {
        val v = AccountProviders.normalized(value)
        if (v == id) return
        id = v
        prefs(c).edit().putString(KEY, v).apply()
        // What the screens kept (home, library, liked songs) is the other account's.
        runCatching { com.music.spotui.data.api.Api.HomeCache.clear() }
        com.music.spotui.data.api.Api.LikedCache.tracks = null
        com.music.spotui.data.api.EditablePlaylists.clear()
        com.music.spotui.data.api.SpotifySync.clearPlaylistMembership()
        com.music.spotui.di.SongPlayer.accountProviderChanged()
        com.music.spotui.di.AccountSync.providerChanged()
        com.music.spotui.di.YtmRuntime.startIfChosen()
    }
}
