package com.music.spotui.di

/**
 * What the music account chosen can do, for the screens to leave out what it cannot instead of
 * showing an entry that leads nowhere. Everything is there with Spotify (and with no account, as
 * before); with YouTube Music only what [AccountApi] answers is.
 */
object AccountFeatures {
    private val ytm: Boolean get() = YtmRuntime.chosen

    /** Friend activity, profiles, followers. */
    val social: Boolean get() = !ytm
    /** "What's New": the releases of the artists followed. */
    val whatsNew: Boolean get() = !ytm
    /** Spotify's account page in the signed-in browser. */
    val accountPage: Boolean get() = !ytm
    /** Jam, Blends, Spotify Connect devices. */
    val together: Boolean get() = !ytm
    /** Likes, playlists made or changed from the app. */
    val libraryEdits: Boolean get() = !ytm
}
