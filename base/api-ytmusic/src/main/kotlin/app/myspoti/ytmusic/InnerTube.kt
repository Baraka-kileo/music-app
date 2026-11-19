/* SPDX-License-Identifier: GPL-3.0-only
 *
 * The request shape (the WEB_REMIX client, the X-Origin and auth headers) follows InnerTune
 * (https://github.com/z-huang/InnerTune, GPL-3.0, innertube/.../InnerTube.kt). No API key is sent.
 */
package app.myspoti.ytmusic

import app.myspoti.api.account.SignedOut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The web app's own API (`music.youtube.com/youtubei/v1/…`) on the user's session: the cookies of
 * their sign-in ([cookies], read from the host's browser when asked) and the auth header made from
 * them. [clientVersion] is the web app's own, read from the page when it is up.
 */
class InnerTube(
    private val http: OkHttpClient,
    private val cookies: suspend () -> String?,
    private val clientVersion: () -> String = { DEFAULT_CLIENT_VERSION },
    private val language: () -> String = { "en" },
    private val country: () -> String = { "US" },
    private val userAgent: String = DESKTOP_USER_AGENT,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : YtmReportApi {
    private fun context() = buildJsonObject {
        putJsonObject("context") {
            putJsonObject("client") {
                put("clientName", "WEB_REMIX")
                put("clientVersion", clientVersion())
                put("hl", language())
                put("gl", country())
            }
        }
    }

    suspend fun browse(browseId: String): JsonObject = post("browse", body { put("browseId", browseId) })

    /** The next page of a list, by the token its previous page gave. */
    suspend fun browseMore(token: String): JsonObject = post("browse", body { put("continuation", token) }, mapOf("ctoken" to token, "continuation" to token, "type" to "next"))

    suspend fun search(query: String): JsonObject = post("search", body { put("query", query) })

    /** The watch-next list of a song: its own row first (title, artists, album, length). */
    suspend fun next(videoId: String): JsonObject = post("next", body { put("videoId", videoId); put("isAudioOnly", true) })

    /** A song-seeded music radio queue, separate from the normal track metadata request. */
    suspend fun radio(videoId: String): JsonObject {
        require(Ytm.isVideoId(videoId)) { "Invalid YouTube video ID" }
        return post("next", body { put("videoId", videoId); put("playlistId", "RDAMVM$videoId"); put("isAudioOnly", true) })
    }

    /** Rates a video on the signed-in account. A successful HTTP status can still contain an error. */
    suspend fun setLiked(videoId: String, liked: Boolean) {
        require(Ytm.isVideoId(videoId)) { "Invalid YouTube video ID" }
        val endpoint = if (liked) "like/like" else "like/removelike"
        val response = post(endpoint, body { putJsonObject("target") { put("videoId", videoId) } })
        if ("error" in response) throw IOException("YouTube Music refused the like change")
    }

    suspend fun setArtistSubscription(channelId: String, subscribed: Boolean) {
        require(Regex("UC[A-Za-z0-9_-]{22}").matches(channelId)) { "Invalid YouTube channel ID" }
        val response = post(if (subscribed) "subscription/subscribe" else "subscription/unsubscribe", body {
            putJsonArray("channelIds") { add(kotlinx.serialization.json.JsonPrimitive(channelId)) }
        })
        if ("error" in response) throw IOException("YouTube Music refused the subscription change")
    }

    /** YouTube Music represents adding a playlist to the library as a playlist rating. */
    suspend fun setPlaylistSaved(playlistId: String, saved: Boolean) {
        require(playlistId.matches(Regex("[A-Za-z0-9_-]{2,200}"))) { "Invalid YouTube playlist ID" }
        val response = post(if (saved) "like/like" else "like/removelike", body {
            putJsonObject("target") { put("playlistId", playlistId) }
        })
        if ("error" in response) throw IOException("YouTube Music refused the playlist library change")
    }

    /** Create a private playlist; return only an ID actually confirmed by the server. */
    suspend fun createPlaylist(title: String): String {
        require(title.isNotBlank()) { "Playlist name is empty" }
        val response = post("playlist/create", body {
            put("title", title)
            put("description", "")
            put("privacyStatus", "PRIVATE")
        })
        if ("error" in response) throw IOException("YouTube Music refused playlist creation")
        return (response["playlistId"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
            ?.takeIf { it.isNotBlank() } ?: throw IOException("YouTube Music did not confirm the playlist ID")
    }

    suspend fun addPlaylistTracks(playlistId: String, videoIds: List<String>) {
        require(playlistId.isNotBlank() && videoIds.all(Ytm::isVideoId)) { "Invalid playlist or video ID" }
        if (videoIds.isEmpty()) return
        val response = post("browse/edit_playlist", body {
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions") { videoIds.forEach { id -> addJsonObject { put("action", "ACTION_ADD_VIDEO"); put("addedVideoId", id) } } }
        })
        if ("error" in response || response["status"]?.jsonPrimitive?.contentOrNull?.let { it != "STATUS_SUCCEEDED" } == true)
            throw IOException("YouTube Music refused the playlist edit")
    }

    /** One edit of the user's playlist (name, description, privacy, order), as the web app sends it. */
    suspend fun editPlaylist(playlistId: String, action: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        require(playlistId.matches(Regex("[A-Za-z0-9_-]{2,200}"))) { "Invalid YouTube playlist ID" }
        val response = post("browse/edit_playlist", body {
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions") { addJsonObject(action) }
        })
        if ("error" in response || response["status"]?.jsonPrimitive?.contentOrNull?.let { it != "STATUS_SUCCEEDED" } == true)
            throw IOException("YouTube Music refused the playlist edit")
    }

    /** Deletes one of the user's playlists. */
    suspend fun deletePlaylist(playlistId: String) {
        require(playlistId.matches(Regex("[A-Za-z0-9_-]{2,200}"))) { "Invalid YouTube playlist ID" }
        val response = post("playlist/delete", body { put("playlistId", playlistId.removePrefix("VL")) })
        if ("error" in response) throw IOException("YouTube Music refused to delete the playlist")
    }

    /** Remove exact playlist entries; a video may appear more than once with different entry IDs. */
    suspend fun removePlaylistTracks(playlistId: String, entries: List<Pair<String, String>>) {
        require(playlistId.isNotBlank() && entries.all { (video, entry) -> Ytm.isVideoId(video) && entry.isNotBlank() })
        if (entries.isEmpty()) return
        val response = post("browse/edit_playlist", body {
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions") { entries.forEach { (video, entry) -> addJsonObject {
                put("action", "ACTION_REMOVE_VIDEO"); put("removedVideoId", video); put("setVideoId", entry)
            } } }
        })
        if ("error" in response || response["status"]?.jsonPrimitive?.contentOrNull != "STATUS_SUCCEEDED")
            throw IOException("YouTube Music did not confirm the playlist removal")
    }

    /**
     * The player's answer for [videoId] on the user's session, read only for where its play is to be
     * reported. The web client must quote the current player's [signatureTimestamp]: without it the
     * answer is "unplayable" and carries no tracking at all.
     */
    override suspend fun tracking(videoId: String, signatureTimestamp: Int?): YtmTracking? =
        YtmTracking.of(post("player", body {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    put("referer", Ytm.watchUrl(videoId))
                    signatureTimestamp?.let { put("signatureTimestamp", it) }
                }
            }
        }))

    /**
     * One play report (s.youtube.com's videostats and atr urls) on the user's session: the client's
     * fields, the play's nonce and [extra] added to the url the player answer gave ([asIs]: the
     * url already names the client, only the nonce is added). Answers the HTTP status.
     */
    override suspend fun report(baseUrl: String, cpn: String, extra: Map<String, String>, visitorData: String?, asIs: Boolean): Int {
        val cookie = cookies()
        val sapisid = Ytm.sapisid(cookie) ?: throw SignedOut(Ytm.PROVIDER)
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (!asIs) {
                setQueryParameter("ver", "2")
                setQueryParameter("c", "WEB_REMIX")
                setQueryParameter("cver", clientVersion())
                setQueryParameter("hl", language().replace('-', '_'))
                setQueryParameter("cr", country())
            }
            setQueryParameter("cpn", cpn)
            extra.forEach { (k, v) -> setQueryParameter(k, v) }
        }.build()
        val request = Request.Builder().url(url).get()
            .header("User-Agent", userAgent)
            .header("Origin", Ytm.ORIGIN)
            .header("X-Origin", Ytm.ORIGIN)
            .header("Referer", Ytm.HOME_URL)
            .header("X-Goog-AuthUser", "0")
            .header("Cookie", cookie!!)
            .header("Authorization", Ytm.authorization(sapisid, nowSeconds()))
            .apply { visitorData?.let { header("X-Goog-Visitor-Id", it) } }
            .build()
        return withContext(Dispatchers.IO) { http.newCall(request).execute().use { it.code } }
    }

    private inline fun body(fill: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject {
        val base = context()
        return buildJsonObject { base.forEach { (k, v) -> put(k, v) }; fill() }
    }

    private suspend fun post(endpoint: String, body: JsonObject, query: Map<String, String> = emptyMap()): JsonObject {
        val cookie = cookies()
        val sapisid = Ytm.sapisid(cookie) ?: throw SignedOut(Ytm.PROVIDER)
        val request = Request.Builder()
            .url("${Ytm.ORIGIN}/youtubei/v1/$endpoint".toHttpUrl().newBuilder().addQueryParameter("prettyPrint", "false")
                .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build())
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", userAgent)
            .header("Origin", Ytm.ORIGIN)
            .header("X-Origin", Ytm.ORIGIN)
            .header("Referer", Ytm.HOME_URL)
            .header("X-Goog-AuthUser", "0")
            .header("X-YouTube-Client-Name", CLIENT_NAME_NUMBER)
            .header("X-YouTube-Client-Version", clientVersion())
            .header("Cookie", cookie!!)
            .header("Authorization", Ytm.authorization(sapisid, nowSeconds()))
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { r ->
                if (r.code == 401 || r.code == 403) throw SignedOut(Ytm.PROVIDER)
                if (!r.isSuccessful) throw IOException("${Ytm.PROVIDER}: $endpoint answered ${r.code}")
                val text = r.body.string()
                runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse { throw IOException("${Ytm.PROVIDER}: $endpoint answer is not JSON") }
            }
        }
    }

    companion object {
        /** The web app's version when the page has not said its own yet (it answers older ones too). */
        const val DEFAULT_CLIENT_VERSION = "1.20251103.01.00"
        /** WEB_REMIX's number in the client-name header. */
        private const val CLIENT_NAME_NUMBER = "67"
        const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
    }
}
