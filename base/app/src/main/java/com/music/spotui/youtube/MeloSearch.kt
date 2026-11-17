// Adapted from Melo YouTubeMusic.kt, GPL-3.0, https://github.com/aarush-io/melo.
// Its catalogue requests follow Resona, MIT, Copyright (c) 2026 Code.Saksham.
package com.music.spotui.youtube

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal object MeloSearch {
    private const val KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30"
    private const val SONGS = "EgWKAQIIAWoKEAoQAxAEEAkQBQ%3D%3D"
    private const val VIDEOS = "EgWKAQIQAWoKEAoQAxAEEAkQBQ%3D%3D"
    private val duration = Regex("""^\d{1,2}(:\d{2}){1,2}$""")
    @Volatile private var blockedUntil = 0L
    @Volatile private var blockMs = 15_000L
    private fun runs(column: JSONObject?) = column?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")?.optJSONArray("runs")

    fun search(query: String, videos: Boolean): List<YouTubeCandidate> {
        val wait = (blockedUntil - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)
        if (wait > 0) throw IOException("YouTube search is temporarily rate limited")
        return try { searchOnce(query, videos).also { blockMs = 15_000L } } catch (e: YouTubeFailure) {
            if (e.status != "403" && e.status != "429") throw e
            MeloStreamResolver.renewIdentity()
            try { searchOnce(query, videos).also { blockMs = 15_000L } } catch (again: YouTubeFailure) {
                if (again.status == "403" || again.status == "429") {
                    blockedUntil = android.os.SystemClock.elapsedRealtime() + blockMs
                    blockMs = (blockMs * 2).coerceAtMost(120_000L)
                }
                throw again
            }
        }
    }
    private fun searchOnce(query: String, videos: Boolean): List<YouTubeCandidate> {
        val context = JSONObject().put("client", JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", "1.20250101.01.00").put("hl", "en").put("gl", java.util.Locale.getDefault().country.ifBlank { "US" }))
        MeloStreamResolver.visitorData?.let { context.getJSONObject("client").put("visitorData", it) }
        val body = JSONObject().put("context", context).put("query", query).put("params", if (videos) VIDEOS else SONGS)
        val req = Request.Builder().url("https://music.youtube.com/youtubei/v1/search?prettyPrint=false&key=$KEY")
            .header("Origin", "https://music.youtube.com").header("Referer", "https://music.youtube.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36")
            .header("X-Goog-Api-Format-Version", "1")
            .apply { MeloStreamResolver.visitorData?.let { header("X-Goog-Visitor-Id", it) } }
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        val json = MeloStreamResolver.http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw YouTubeFailure("YouTube search HTTP ${r.code}", r.code.toString())
            JSONObject(r.body?.string().orEmpty()).also(MeloStreamResolver::learnVisitor)
        }
        val out = ArrayList<YouTubeCandidate>()
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> {
                    value.optJSONObject("musicResponsiveListItemRenderer")?.let { row(it)?.let(out::add) }
                    value.keys().forEach { walk(value.opt(it)) }
                }
                is JSONArray -> for (i in 0 until value.length()) walk(value.opt(i))
            }
        }
        walk(json)
        return out.distinctBy { it.id }
    }

    private fun row(row: JSONObject): YouTubeCandidate? {
        val flex = row.optJSONArray("flexColumns") ?: return null
        val titleRun = runs(flex.optJSONObject(0))?.optJSONObject(0) ?: return null
        val title = titleRun.optString("text").takeIf(String::isNotBlank) ?: return null
        val id = titleRun.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.optString("videoId")?.takeIf(String::isNotBlank)
            ?: row.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf(String::isNotBlank) ?: return null
        val groups = ArrayList<List<JSONObject>>()
        for (column in 1 until flex.length()) {
            val r = runs(flex.optJSONObject(column)) ?: continue
            var group = ArrayList<JSONObject>()
            for (i in 0 until r.length()) {
                val item = r.optJSONObject(i) ?: continue
                if (item.optString("text") == " • ") { if (group.isNotEmpty()) groups.add(group); group = ArrayList() } else group.add(item)
            }
            if (group.isNotEmpty()) groups.add(group)
        }
        val texts = groups.map { g -> g.joinToString("") { it.optString("text") }.trim() }
        val album = groups.indexOfFirst { g -> g.any { it.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optJSONObject("browseEndpointContextSupportedConfigs")?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType") == "MUSIC_PAGE_TYPE_ALBUM" } }
        val fixed = row.optJSONArray("fixedColumns")?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        val length = texts.lastOrNull { duration.matches(it) } ?: fixed?.takeIf { duration.matches(it) }
        val artist = texts.withIndex().firstOrNull { (i, t) -> t.isNotBlank() && !duration.matches(t) && i != album && !(i == 0 && t in setOf("Song", "Video", "Episode")) && !Regex("^[\\d.,]+[KMB]? (?:plays|views)$").matches(t) }?.value.orEmpty()
        val ms = length?.split(':')?.fold(0) { n, part -> n * 60 + (part.toIntOrNull() ?: 0) }?.times(1000) ?: 0
        return YouTubeCandidate(id, title, artist, ms, if (album >= 0) texts[album] else "")
    }
}
