package app.myspoti.ytmusic

import app.myspoti.api.account.SignedOut
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The web app's API on a recorded session: requests checked, answers from fixtures, nothing goes out. */
class InnerTubeTest {
    private val cookie = "PREF=x; SAPISID=sapisid-test; SID=sid-test"

    /** Answers each request from [answer] (by endpoint and body) and keeps it. */
    private class Recorded(private val answer: (Request, String) -> String) : Interceptor {
        val requests = ArrayList<Pair<Request, String>>()
        override fun intercept(chain: Interceptor.Chain): Response {
            val r = chain.request()
            val body = Buffer().also { r.body!!.writeTo(it) }.readUtf8()
            requests += r to body
            return Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(answer(r, body).toResponseBody("application/json".toMediaType())).build()
        }
    }

    private fun fixtureText(name: String) = InnerTubeTest::class.java.getResourceAsStream("/fixtures/$name.json")!!.bufferedReader().readText()

    private fun api(rec: Recorded, cookies: String? = cookie) =
        InnerTube(OkHttpClient.Builder().addInterceptor(rec).build(), cookies = { cookies }, clientVersion = { "1.2.3" }, nowSeconds = { 1_700_000_000 })

    @Test fun `the auth header is the web app's SAPISIDHASH`() {
        assertEquals("SAPISIDHASH 1700000000_c0178091cdd52a25dadb4af9a2cf9979070658fc", Ytm.authorization("sapisid-test", 1_700_000_000))
        assertEquals("sapisid-test", Ytm.sapisid(cookie))
        assertEquals("b", Ytm.sapisid("__Secure-3PAPISID=b"))
        assertFalse(Ytm.signedIn("PREF=x; VISITOR_INFO1_LIVE=y"))
        assertTrue(Ytm.isVideoId("vidAAAAAAA1"))
        assertFalse(Ytm.isVideoId("spotify:track:4uLU6hMCjMI75M1A2tKUQC"))
        assertTrue(Ytm.signInDone("https://music.youtube.com/"))
        assertFalse(Ytm.signInDone("https://accounts.google.com/ServiceLogin?continue=https://music.youtube.com/"))
    }

    @Test fun `a browse carries the session, the auth header and the client, no API key`() = runTest {
        val rec = Recorded { _, _ -> fixtureText("liked") }
        api(rec).browse("VLLM")
        val (r, body) = rec.requests.single()
        assertEquals("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false", r.url.toString())
        assertNull(r.url.queryParameter("key"))
        assertEquals(cookie, r.header("Cookie"))
        assertEquals("SAPISIDHASH 1700000000_c0178091cdd52a25dadb4af9a2cf9979070658fc", r.header("Authorization"))
        assertEquals("https://music.youtube.com", r.header("X-Origin"))
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("VLLM", json["browseId"]!!.jsonPrimitive.content)
        val client = json["context"]!!.jsonObject["client"]!!.jsonObject
        assertEquals("WEB_REMIX", client["clientName"]!!.jsonPrimitive.content)
        assertEquals("1.2.3", client["clientVersion"]!!.jsonPrimitive.content)
    }

    @Test fun `signed out, nothing is asked`() = runTest {
        val rec = Recorded { _, _ -> "{}" }
        assertTrue(runCatching { api(rec, cookies = "PREF=x").browse("VLLM") }.exceptionOrNull() is SignedOut)
        assertTrue(runCatching { api(rec, cookies = null).search("q") }.exceptionOrNull() is SignedOut)
        assertTrue(rec.requests.isEmpty())
    }

    @Test fun `liked songs follow the next pages until enough are there`() = runTest {
        val rec = Recorded { r, body -> if (r.url.queryParameter("ctoken") == "TOKEN-PAGE-2" && "TOKEN-PAGE-2" in body) fixtureText("liked-more") else fixtureText("liked") }
        val lib = YtmLibrary(api(rec))
        val first = lib.likedTracks(0, 2)
        assertEquals(2, first.items.size)
        assertEquals(2, first.nextOffset)
        assertEquals(1, rec.requests.size)
        val all = lib.likedTracks(0, 10)
        assertEquals(listOf("Song One", "Song Two", "Song Three"), all.items.map { it.name })
        assertNull(all.nextOffset)
        val second = lib.likedTracks(2, 2)
        assertEquals(listOf("Song Three"), second.items.map { it.name })
    }

    @Test fun `account adapters preserve pages and playlist sorting`() = runTest {
        val rec = Recorded { r, _ -> if (r.url.queryParameter("ctoken") == "TOKEN-PAGE-2") fixtureText("liked-more") else fixtureText("liked") }
        val service = api(rec)
        val lib = YtmLibrary(service)
        val liked = YtmUserLibrary(service, lib)
        val playlists = YtmPlaylists(service, lib)
        val first = liked.likedTracks(0, 2)
        assertEquals(2, first.nextOffset)
        assertEquals(listOf("Song Three"), liked.likedTracks(2, 2).items.map { it.track.name })
        assertEquals(listOf("Song Three"), playlists.tracks("ytmusic:playlist:PLtest", 2, 2).items.map { it.name })
        assertEquals(listOf("Song Two", "Song Three", "Song One"), playlists.sortedTracks("ytmusic:playlist:PLtest", app.myspoti.api.PlaylistSort.TITLE, true).map { it.name })
    }

    @Test fun `repeated continuation or page limit is reported rather than truncating`() = runTest {
        val rec = Recorded { _, _ -> fixtureText("liked") }
        val service = api(rec)
        assertTrue(runCatching { YtmLibrary(service, maxPages = 4).likedTracks(0, 100) }.exceptionOrNull() is java.io.IOException)
        assertTrue(runCatching { YtmLibrary(service, maxPages = 1).likedTracks(2, 2) }.exceptionOrNull() is java.io.IOException)
    }

    @Test fun `liked membership includes later pages and preserves requested order`() = runTest {
        val rec = Recorded { r, _ -> if (r.url.queryParameter("ctoken") == "TOKEN-PAGE-2") fixtureText("liked-more") else fixtureText("liked") }
        val service = api(rec)
        val library = YtmUserLibrary(service, YtmLibrary(service))
        assertEquals(listOf(true, false, true), library.contains(listOf("ytmusic:track:vidAAAAAAA3", "ytmusic:track:vidUNKNOWN0", "ytmusic:track:vidAAAAAAA1")))
        assertEquals(2, rec.requests.size)
    }

    @Test fun `incomplete liked feed does not mark tracks unliked`() = runTest {
        val rec = Recorded { _, _ -> fixtureText("liked") }
        val service = api(rec)
        val library = YtmUserLibrary(service, YtmLibrary(service, maxPages = 1))
        assertTrue(runCatching { library.contains(listOf("ytmusic:track:vidUNKNOWN0")) }.exceptionOrNull() is java.io.IOException)
    }

    @Test fun `like and unlike send the native video identity`() = runTest {
        val rec = Recorded { _, _ -> "{}" }
        val service = api(rec)
        val library = YtmUserLibrary(service, YtmLibrary(service))
        library.add(listOf("ytmusic:track:vidAAAAAAA1"))
        library.remove(listOf("ytmusic:track:vidAAAAAAA1"))
        assertTrue(rec.requests[0].first.url.encodedPath.endsWith("/like/like"))
        assertTrue(rec.requests[1].first.url.encodedPath.endsWith("/like/removelike"))
        assertEquals("vidAAAAAAA1", Json.parseToJsonElement(rec.requests[0].second).jsonObject["target"]!!.jsonObject["videoId"]!!.jsonPrimitive.content)
    }

    @Test fun `artist subscriptions use the channel endpoint and reject bad ids`() = runTest {
        val rec = Recorded { _, _ -> "{}" }
        val service = api(rec)
        val library = YtmUserLibrary(service, YtmLibrary(service))
        val channel = "UC" + "a".repeat(22)
        library.add(listOf("ytmusic:artist:$channel"))
        library.remove(listOf("ytmusic:artist:$channel"))
        assertTrue(rec.requests[0].first.url.encodedPath.endsWith("/subscription/subscribe"))
        assertTrue(rec.requests[1].first.url.encodedPath.endsWith("/subscription/unsubscribe"))
        assertTrue(rec.requests[0].second.contains(channel))
        assertTrue(runCatching { library.add(listOf("ytmusic:artist:bad")) }.isFailure)
        assertEquals(2, rec.requests.size)
    }

    @Test fun `playlist library state and writes use playlist identity`() = runTest {
        val rec = Recorded { _, body -> if ("FEmusic_liked_playlists" in body) fixtureText("playlists") else "{}" }
        val service = api(rec)
        val library = YtmUserLibrary(service, YtmLibrary(service))
        assertEquals(listOf(true, false), library.contains(listOf("ytmusic:playlist:PLroadmix0001", "ytmusic:playlist:PLmissing0001")))
        library.add(listOf("ytmusic:playlist:PLroadmix0001"))
        library.remove(listOf("ytmusic:playlist:PLroadmix0001"))
        val writes = rec.requests.takeLast(2)
        assertTrue(writes[0].first.url.encodedPath.endsWith("/like/like"))
        assertTrue(writes[1].first.url.encodedPath.endsWith("/like/removelike"))
        assertEquals("PLroadmix0001", Json.parseToJsonElement(writes[0].second).jsonObject["target"]!!.jsonObject["playlistId"]!!.jsonPrimitive.content)
        assertTrue(runCatching { library.add(listOf("ytmusic:playlist:../bad")) }.isFailure)
    }

    @Test fun `an error in a successful response is not reported as a like`() = runTest {
        val rec = Recorded { _, _ -> """{"error":{"message":"refused"}}""" }
        assertTrue(runCatching { api(rec).setLiked("vidAAAAAAA1", true) }.exceptionOrNull() is java.io.IOException)
    }

    @Test fun `unsupported bulk items do not cause a partial write`() = runTest {
        val rec = Recorded { _, _ -> "{}" }
        val service = api(rec)
        val library = YtmUserLibrary(service, YtmLibrary(service))
        assertTrue(runCatching { library.add(listOf("ytmusic:track:vidAAAAAAA1", "spotify:track:abc")) }.exceptionOrNull() is NotOnYtm)
        assertTrue(rec.requests.isEmpty())
    }

    @Test fun `create and add playlist tracks use native identities`() = runTest {
        val rec = Recorded { r, _ -> if (r.url.encodedPath.endsWith("/playlist/create")) """{"playlistId":"PLtest"}""" else """{"status":"STATUS_SUCCEEDED"}""" }
        val service = api(rec)
        val playlists = YtmPlaylists(service, YtmLibrary(service))
        val uri = playlists.create("Road", null)
        assertEquals("ytmusic:playlist:PLtest", uri)
        playlists.addTracks(uri, listOf("ytmusic:track:vidAAAAAAA1"))
        val body = Json.parseToJsonElement(rec.requests.last().second).jsonObject
        assertEquals("PLtest", body["playlistId"]!!.jsonPrimitive.content)
        assertTrue(rec.requests.last().second.contains("ACTION_ADD_VIDEO"))
    }

    @Test fun `refused playlist edit reports failure`() = runTest {
        val rec = Recorded { _, _ -> """{"status":"STATUS_FAILED"}""" }
        assertTrue(runCatching { api(rec).addPlaylistTracks("PLtest", listOf("vidAAAAAAA1")) }.exceptionOrNull() is java.io.IOException)
    }

    @Test fun `playlist removal names exact entry and rejects unknown entries before editing`() = runTest {
        val rec = Recorded { r, _ -> when {
            r.url.encodedPath.endsWith("/edit_playlist") -> """{"status":"STATUS_SUCCEEDED"}"""
            r.url.queryParameter("ctoken") == "TOKEN-PAGE-2" -> fixtureText("liked-more")
            else -> fixtureText("liked")
        } }
        val service = api(rec)
        val playlists = YtmPlaylists(service, YtmLibrary(service))
        playlists.removeTracks("ytmusic:playlist:PLtest", listOf("set1"))
        val action = Json.parseToJsonElement(rec.requests.last().second).jsonObject["actions"]!!.toString()
        assertTrue(action.contains("ACTION_REMOVE_VIDEO"))
        assertTrue(action.contains("vidAAAAAAA1"))
        assertTrue(action.contains("set1"))
        val edits = rec.requests.count { it.first.url.encodedPath.endsWith("/edit_playlist") }
        assertTrue(runCatching { playlists.removeTracks("ytmusic:playlist:PLtest", listOf("missing")) }.exceptionOrNull() is java.io.IOException)
        assertEquals(edits, rec.requests.count { it.first.url.encodedPath.endsWith("/edit_playlist") })
    }

    @Test fun `native video radio requests a YouTube mix and excludes its seed`() = runTest {
        val rec = Recorded { _, _ -> fixtureText("next") }
        val service = api(rec)
        val tracks = YtmLibrary(service).radio("vidAAAAAAA1")
        assertEquals(listOf("ytmusic:track:vidOTHER001"), tracks.map { it.uri })
        val body = Json.parseToJsonElement(rec.requests.single().second).jsonObject
        assertEquals("RDAMVMvidAAAAAAA1", body["playlistId"]!!.jsonPrimitive.content)
        assertEquals("vidAAAAAAA1", body["videoId"]!!.jsonPrimitive.content)
    }

    @Test fun `playlists, a playlist's songs, the history`() = runTest {
        val rec = Recorded { _, body ->
            when {
                "FEmusic_liked_playlists" in body -> fixtureText("playlists")
                "VLPLroadmix0001" in body || "FEmusic_history" in body -> fixtureText("liked-more")
                else -> "{}"
            }
        }
        val lib = YtmLibrary(api(rec))
        assertEquals(2, lib.playlists().size)
        assertEquals(1, lib.playlistTracks("ytmusic:playlist:PLroadmix0001").items.size)
        assertTrue("VLPLroadmix0001" in rec.requests.last().second)
        assertEquals(1, lib.history().size)
        assertTrue(runCatching { lib.playlistTracks("spotify:playlist:x") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun `playlist library reads continuation pages`() = runTest {
        val first = fixtureText("playlists").trim().dropLast(1) + ",\"continuations\":[{\"nextContinuationData\":{\"continuation\":\"MORE\"}}]}"
        val second = fixtureText("playlists").replace("PLroadmix0001", "PLanother0001").replace("Road Mix", "Another Mix")
        val rec = Recorded { r, _ -> if (r.url.queryParameter("ctoken") == "MORE") second else first }
        val service = api(rec)
        val lists = YtmLibrary(service).playlists()
        assertEquals(3, lists.size)
        assertTrue(lists.any { it.uri == "ytmusic:playlist:PLanother0001" })
        assertEquals(2, rec.requests.size)
    }

    @Test fun `metadata on the session, search, song, album, artist`() = runTest {
        val rec = Recorded { r, body ->
            when {
                r.url.encodedPath.endsWith("/search") -> fixtureText("search")
                r.url.encodedPath.endsWith("/next") -> fixtureText("next")
                "MPREb_albumX" in body -> fixtureText("album")
                else -> fixtureText("artist")
            }
        }
        val m = YtmMetadata(api(rec))
        val all = m.all("artist a song one")
        assertEquals(2, all.tracks.items.size)
        assertEquals(1, all.albums.items.size)
        assertTrue(m.tracks("q", offset = 30).items.isEmpty())
        assertEquals("Song One", m.track("ytmusic:track:vidAAAAAAA1").name)
        assertEquals(2, m.albumTracks("ytmusic:album:MPREb_albumX").items.size)
        assertEquals("Artist A", m.artistPage("ytmusic:artist:UCartistA000000000000000").artist.name)
        assertNull(m.isrc("ytmusic:track:vidAAAAAAA1"))
    }
}
