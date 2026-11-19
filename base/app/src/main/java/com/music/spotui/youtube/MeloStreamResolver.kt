// Adapted from Melo, GPL-3.0 (https://github.com/aarush-io/melo, commit 0674e5d).
// Player resolution follows Resona, MIT, Copyright (c) 2026 Code.Saksham.
package com.music.spotui.youtube
import android.content.Context
import androidx.core.content.edit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

internal class YouTubeFailure(message: String, val status: String? = null) : IOException(message)
internal data class YouTubeFormat(val itag: Int, val url: String?, val signatureCipher: String?, val mimeType: String, val bitrate: Long, val contentLength: Long, val sampleRate: Int)
internal data class YouTubeStream(val videoId: String, val url: String, val userAgent: String, val client: String, val contentLength: Long, val mimeType: String, val bitrate: Long, val sampleRate: Int, val expiresAtMs: Long)
internal object MeloStreamResolver {
    private val app: Context get() = com.music.spotui.di.Graph.app
    private val preferences get() = app.getSharedPreferences("melo_youtube_visitor", Context.MODE_PRIVATE)
    internal val http = OkHttpClient.Builder().addInterceptor(com.music.spotui.data.DataSaver.interceptor).connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
    private val streams = HashMap<String, YouTubeStream>()
    private var playerJs: Pair<String, String>? = null
    private val decipher by lazy { YouTubeDecipher(app) }
    @Volatile private var visitor: String? = null
    internal val visitorData: String? get() = visitor ?: preferences.getString(KEY_VISITOR, null)?.also { visitor = it }
    internal fun learnVisitor(response: JSONObject) {
        val token = response.optJSONObject("responseContext")?.optString("visitorData").orEmpty()
        if (token.isBlank()) return
        val decoded = URLDecoder.decode(token, "UTF-8")
        if (decoded != visitor) { visitor = decoded; preferences.edit { putString(KEY_VISITOR, decoded) } }
    }
    @Synchronized internal fun cachedStream(videoId: String): YouTubeStream? =
        streams[videoId]?.takeIf { it.expiresAtMs > System.currentTimeMillis() + STREAM_MARGIN_MS }

    @Synchronized internal fun forgetStream(videoId: String) { streams.remove(videoId) }

    /** Resolves the best audio-only format, trying each client until one hands back a usable address. */
    internal fun stream(videoId: String, excluded: Set<String> = emptySet()): YouTubeStream {
        if (excluded.isEmpty()) cachedStream(videoId)?.let { return it }
        val clients = YouTubeClient.entries.filterNot { it.clientName in excluded }
        if (clients.isEmpty()) throw YouTubeFailure("No client could open this track.", "ALL_CLIENTS_EXCLUDED")
        val initial = visitorData
        val (response, client) = try {
            playerWithFallback(videoId, clients)
        } catch (failure: YouTubeFailure) {
            // A gated first pass usually hands back a fresh identity: retry the chain once with it.
            val fresh = visitorData?.takeIf { it != initial } ?: mintVisitor()?.takeIf { it != initial } ?: throw failure
            visitor = fresh
            playerWithFallback(videoId, clients)
        }
        val formats = audioFormats(response)
        if (formats.isEmpty()) throw YouTubeFailure("No audio format is available.", "NO_AUDIO_FORMAT")
        val js = if (formats.any { it.url == null }) runCatching { playerScript(videoId) }.getOrNull() else null
        var last: Exception? = null
        for (format in preferredOrder(formats)) {
            try {
                val url = format.url ?: decipher.playableUrl(format, js)
                val expires = url.toHttpUrlOrNull()?.queryParameter("expire")?.toLongOrNull()?.times(1000)
                    ?: (System.currentTimeMillis() + DEFAULT_LIFETIME_MS)
                val resolved = YouTubeStream(videoId, url, client.userAgent, client.clientName, format.contentLength,
                    format.mimeType, format.bitrate, format.sampleRate, expires)
                synchronized(this) { streams[videoId] = resolved }
                return resolved
            } catch (failure: Exception) { last = failure }
        }
        throw (last as? YouTubeFailure) ?: YouTubeFailure("No audio format could be opened.", "NO_AUDIO_FORMAT")
    }

    /** After the CDN refused an address, the anonymous identity that obtained it is the likely cause. */
    internal fun renewIdentity() {
        visitor = null
        preferences?.edit { remove(KEY_VISITOR) }
        mintVisitor()?.let { token -> visitor = token; preferences?.edit { putString(KEY_VISITOR, token) } }
    }

    private fun playerWithFallback(videoId: String, clients: List<YouTubeClient>): Pair<JSONObject, YouTubeClient> {
        var status = "UNKNOWN"
        var reason = "No client answered."
        var timestamp: Int? = null
        var timestampLoaded = false
        for (client in clients) {
            if (client.sendsSignatureTimestamp && !timestampLoaded) {
                timestampLoaded = true
                timestamp = runCatching { signatureTimestamp(playerScript(videoId)) }.getOrNull()
            }
            val response = try { player(videoId, client, timestamp) } catch (failure: IOException) {
                reason = failure.message ?: reason; continue
            }
            learnVisitor(response)
            val playability = response.optJSONObject("playabilityStatus")
            val current = playability?.optString("status").orEmpty()
            val playable = audioFormats(response).any { it.url != null || it.signatureCipher != null }
            if (current == "OK" && playable) return response to client
            status = current.ifBlank { "UNKNOWN" }
            reason = if (current == "OK") "This client needs a proof-of-origin token." else playability?.optString("reason").orEmpty().ifBlank { reason }
        }
        throw YouTubeFailure(reason, status)
    }

    private fun player(videoId: String, client: YouTubeClient, timestamp: Int?): JSONObject {
        val visitor = visitorData
        val request = Request.Builder().url(client.playerUrl)
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.clientNumber)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("Origin", "https://www.youtube.com").header("Referer", "https://www.youtube.com/")
            .apply { visitor?.let { header("X-Goog-Visitor-Id", it) } }
            .post(client.playerBody(videoId, timestamp, visitor).toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw YouTubeFailure("The player endpoint answered ${response.code}.", response.code.toString())
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun playerScript(videoId: String): String {
        val html = http.newCall(Request.Builder().url("https://www.youtube.com/watch?v=$videoId")
            .header("User-Agent", BROWSER_AGENT).build()).execute().use { it.body?.string().orEmpty() }
        Regex(""""VISITOR_DATA":"([^"]+)"""").find(html)?.groupValues?.get(1)?.let { token ->
            visitor = token; preferences?.edit { putString(KEY_VISITOR, token) }
        }
        val path = Regex(""""jsUrl"\s*:\s*"(/s/player/[a-f0-9]+/[^"]+base\.js)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""(/s/player/[a-f0-9]+/player_ias\.vflset/[^"]+base\.js)""").find(html)?.groupValues?.get(1)
            ?: throw YouTubeFailure("The player script address was not found.")
        synchronized(this) { playerJs?.takeIf { it.first == path }?.let { return it.second } }
        val script = http.newCall(Request.Builder().url("https://www.youtube.com$path").header("User-Agent", BROWSER_AGENT).build())
            .execute().use { it.body?.string().orEmpty() }
        synchronized(this) { playerJs = path to script }
        return script
    }

    @Volatile private var knownTimestamp: Pair<Long, Int>? = null

    /**
     * The current web player's signature timestamp (read from its script, kept [TIMESTAMP_KEPT_MS]),
     * which YouTube Music's play reports need; [videoId] is any song, for the page the script is named on.
     */
    internal fun currentSignatureTimestamp(videoId: String): Int? {
        knownTimestamp?.takeIf { System.currentTimeMillis() - it.first < TIMESTAMP_KEPT_MS }?.let { return it.second }
        val sts = signatureTimestamp(playerScript(videoId)) ?: return null
        knownTimestamp = System.currentTimeMillis() to sts
        return sts
    }
    private const val TIMESTAMP_KEPT_MS = 6 * 60 * 60_000L

    private fun signatureTimestamp(script: String): Int? =
        Regex("""(?:signatureTimestamp|sts)\s*[=:]\s*(\d{5,6})""").find(script)?.groupValues?.get(1)?.toIntOrNull()

    private fun mintVisitor(): String? = runCatching {
        val body = JSONObject().put("context", JSONObject().put("client", JSONObject()
            .put("clientName", "WEB").put("clientVersion", YouTubeClient.WEB.clientVersion).put("hl", "en").put("gl", "US")))
        http.newCall(Request.Builder().url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
            .header("User-Agent", YouTubeClient.WEB.userAgent).post(body.toString().toRequestBody(JSON)).build())
            .execute().use { response ->
                JSONObject(response.body?.string().orEmpty()).optJSONObject("responseContext")?.optString("visitorData")
                    ?.let { URLDecoder.decode(it, "UTF-8") }?.takeIf(String::isNotBlank)
            }
    }.getOrNull()

    private const val KEY_VISITOR = "visitor"
    private const val BROWSER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val STREAM_MARGIN_MS = 10 * 60 * 1000L
    private const val DEFAULT_LIFETIME_MS = 5 * 60 * 60 * 1000L
    private val JSON = "application/json".toMediaType()
}
internal fun audioFormats(response: JSONObject): List<YouTubeFormat> {
    val data = response.optJSONObject("streamingData") ?: return emptyList()
    return listOfNotNull(data.optJSONArray("adaptiveFormats"), data.optJSONArray("formats")).flatMap { array ->
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.filter { it.optString("mimeType").startsWith("audio/") }.map { format ->
        YouTubeFormat(
            itag = format.optInt("itag"), url = format.optString("url").takeIf(String::isNotBlank),
            signatureCipher = format.optString("signatureCipher").takeIf(String::isNotBlank) ?: format.optString("cipher").takeIf(String::isNotBlank),
            mimeType = format.optString("mimeType"), bitrate = format.optLong("bitrate"),
            contentLength = format.optString("contentLength").toLongOrNull() ?: -1,
            sampleRate = format.optString("audioSampleRate").toIntOrNull() ?: 0,
        )
    }
}

/** Highest bitrate first; formats with a known length come first, since the player can then seek and cache them. */
internal fun preferredOrder(formats: List<YouTubeFormat>): List<YouTubeFormat> =
    formats.sortedWith(compareByDescending<YouTubeFormat> { it.contentLength > 0 }.thenByDescending { it.bitrate })

