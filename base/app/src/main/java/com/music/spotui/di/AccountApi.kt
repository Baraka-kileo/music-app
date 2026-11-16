package com.music.spotui.di

import app.myspoti.api.Blends
import app.myspoti.api.Devices
import app.myspoti.api.Home
import app.myspoti.api.Jam
import app.myspoti.api.Library
import app.myspoti.api.MyspotiApi
import app.myspoti.api.Playlists
import app.myspoti.api.Recents
import app.myspoti.api.TrackExtras
import app.myspoti.api.Users
import app.myspoti.ytmusic.NotOnYtm
import app.myspoti.ytmusic.YtmHome
import app.myspoti.ytmusic.YtmPlaylists
import app.myspoti.ytmusic.YtmRecents
import app.myspoti.ytmusic.YtmUserLibrary
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * What the screens read, by the music account chosen
 * ([com.music.spotui.data.preferences.AccountProviderPref]). With Spotify, or none, every part is
 * [base]'s own object, exactly as before this class: nothing of Spotify's path changes. With YouTube
 * Music the account's own parts (library, playlists, home, recently played) are YouTube Music's, and
 * the parts only Spotify has (profiles, Jam, Blends, Connect devices, a song's Spotify extras) are
 * refused at once ([NotOnYtm]) instead of waking Spotify's page for an account that is not signed in.
 * Podcasts stay Spotify's (an episode is Spotify's own content, whatever the music account).
 *
 * The choice is read each time a part is asked for: changing the music account needs no restart.
 */
class AccountApi(private val base: MyspotiApi) : MyspotiApi by base {
    private val ytm: Boolean get() = YtmRuntime.chosen

    private val ytmLibrary by lazy { YtmRuntime.account.let { YtmUserLibrary(it.api, it.library) } }
    private val ytmPlaylists by lazy { YtmRuntime.account.let { YtmPlaylists(it.api, it.library) } }
    private val ytmHome by lazy { YtmHome(YtmRuntime.account.api) }
    private val ytmRecents by lazy { YtmRecents(YtmRuntime.account.library) }

    override val library: Library get() = if (ytm) ytmLibrary else base.library
    override val playlists: Playlists get() = if (ytm) ytmPlaylists else base.playlists
    override val home: Home get() = if (ytm) ytmHome else base.home
    override val recents: Recents get() = if (ytm) ytmRecents else base.recents

    private val noUsers by lazy { refusing(base.users, "profiles") }
    private val noExtras by lazy { refusing(base.extras, "Spotify's song extras") }
    private val noJam by lazy { refusing(base.jam, "Jam") }
    private val noBlends by lazy { refusing(base.blends, "Blends") }
    private val noDevices by lazy { refusing(base.devices, "Spotify Connect") }

    override val users: Users get() = if (ytm) noUsers else base.users
    override val extras: TrackExtras get() = if (ytm) noExtras else base.extras
    override val jam: Jam get() = if (ytm) noJam else base.jam
    override val blends: Blends get() = if (ytm) noBlends else base.blends
    override val devices: Devices get() = if (ytm) noDevices else base.devices

    private companion object {
        /**
         * [spotify]'s part with its requests (the suspending calls) refused; what is only read from
         * memory (a flow, a state) stays Spotify's own. Only ever handed out with YouTube Music chosen.
         */
        inline fun <reified T : Any> refusing(spotify: T, what: String): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, m, args ->
                if (m.parameterTypes.lastOrNull() == kotlin.coroutines.Continuation::class.java) throw NotOnYtm(what)
                try { m.invoke(spotify, *(args ?: emptyArray())) } catch (e: InvocationTargetException) { throw e.targetException }
            } as T
    }
}
