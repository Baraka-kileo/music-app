package com.music.spotui.ui.components

import android.content.Context
import android.widget.Toast

/**
 * A message names the music account in use. Most were written when Spotify was the only one:
 * with YouTube Music as the account, a refused playlist edit said "Spotify refused", as if the
 * wrong service had been asked. While YouTube Music is the account, "Spotify" in a message reads
 * "YouTube Music"; with Spotify, nothing changes.
 */
object AccountWords {
    private const val SPOTIFY = "Spotify"
    private const val YOUTUBE_MUSIC = "YouTube Music"

    fun fit(text: CharSequence): CharSequence =
        if (com.music.spotui.di.YtmRuntime.chosen && text.contains(SPOTIFY)) text.toString().replace(SPOTIFY, YOUTUBE_MUSIC) else text

    fun fit(text: String): String = fit(text as CharSequence).toString()
}

/** [Toast.makeText] with the message naming the account in use ([AccountWords]). */
object AccountToast {
    fun makeText(context: Context, text: CharSequence, duration: Int): Toast =
        Toast.makeText(context, AccountWords.fit(text), duration)

    fun makeText(context: Context, resId: Int, duration: Int): Toast =
        Toast.makeText(context, AccountWords.fit(context.getText(resId)), duration)
}
