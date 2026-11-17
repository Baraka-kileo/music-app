import sys, os
os.chdir('O:/base/app/src/main/java/com/music/spotui')

def load(p):
    s = open(p, 'rb').read().decode('utf-8'); return s, ('\r\n' if '\r\n' in s else '\n')
def edit(p, reps, append=None):
    s, nl = load(p)
    for a, b in reps:
        a, b = a.replace('\n', nl), b.replace('\n', nl)
        n = s.count(a)
        if n != 1: sys.exit(f"{p}: {n} for {a[:90]!r}")
        s = s.replace(a, b)
    if append: s += append.replace('\n', nl)
    open(p, 'wb').write(s.encode('utf-8'))

# ---------- MusicVideoController: is a video known for this song (no network) ----------
edit('video/MusicVideoController.kt', [("""    fun prefetch(trackId: String, title: String, artist: String, durationMs: Long) {""",
"""    /** Whether a music video is known for [trackId], from what was already looked up (memory, then disk). */
    suspend fun knownVideo(trackId: String): Boolean = trackId.isNotBlank() && withContext(Dispatchers.IO) {
        (recentAnswer(trackId) ?: runCatching { readCache(trackId) }.getOrNull()) is State.Ready
    }

    fun prefetch(trackId: String, title: String, artist: String, durationMs: Long) {""")])

# ---------- Immersive queue: video label, prefetch for the first rows, glass dock at the foot ----------
Q = 'ui/immersive/lyrics/ImmersiveQueuePanel.kt'
edit(Q, [
("""    explicit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handle: (@Composable () -> Unit)? = null,
) {""", """    explicit: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    handle: (@Composable () -> Unit)? = null,
    video: Boolean = false,
) {"""),
("""                if (explicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = artist,""", """                if (explicit) {
                    ExplicitBadge()
                    Spacer(Modifier.width(6.dp))
                }
                if (video) {
                    com.music.spotui.ui.components.VideoBadge()
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = artist,"""),
("""            explicit = item.song.explicit,
            onClick = { SongPlayer.skipTo(item) },""", """            explicit = item.song.explicit,
            video = com.music.spotui.ui.components.rememberHasVideo(item.song),
            onClick = { SongPlayer.skipTo(item) },"""),
("""    val bottomPad = contentPadding.calculateBottomPadding() + 24.dp""", """    val bottomPad = contentPadding.calculateBottomPadding() + 88.dp"""),
("""    val bottomPadPx = with(density) { (contentPadding.calculateBottomPadding() + 24.dp).toPx() }""", """    val bottomPadPx = with(density) { (contentPadding.calculateBottomPadding() + 88.dp).toPx() }"""),
("""    LazyColumn(
        state = listState,
        modifier = modifier
            .onSizeChanged""", """    Box(modifier) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
            .onSizeChanged"""),
("""        item(key = "header") {
            QueueHeader(
                shuffle = shuffle,
                repeatMode = repeatMode,""", """        item(key = "header") {
            QueueHeader(
                dock = false,
                shuffle = shuffle,
                repeatMode = repeatMode,"""),
("""private fun QueueHeader(
    shuffle: Boolean,""", """private fun QueueHeader(
    dock: Boolean,
    shuffle: Boolean,"""),
("""            style = Immersive.Title,
            modifier = Modifier.weight(1f),
        )
        QueueToggle(""", """            style = Immersive.Title,
            modifier = Modifier.weight(1f),
        )
        if (dock) QueueToggle("""),
])
# The dock: closes the Box before the end of ImmersiveQueuePanel.
s, nl = load(Q)
start = s.index('    Box(modifier) {' + nl + '    LazyColumn(')
end = s.index(nl + '}' + nl, start)
dock = nl.join([
"",
"    // The glass dock: shuffle, repeat and the sleep timer, floating over the foot of the queue.",
"    Row(",
"        horizontalArrangement = Arrangement.spacedBy(10.dp),",
"        verticalAlignment = Alignment.CenterVertically,",
"        modifier = Modifier.align(Alignment.BottomCenter)",
"            .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp)",
"            .clip(RoundedCornerShape(50))",
"            .background(Color(0xE61C1C22))",
"            .border(0.6.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(50))",
"            .padding(horizontal = 12.dp, vertical = 8.dp),",
"    ) {",
"        QueueToggle(com.music.spotui.ui.design.AppIcons.Shuffle, stringResource(R.string.immersive_queue_shuffle), shuffle) {",
"            SongPlayer.setShuffle(!shuffle); song.shuffle.value = !shuffle",
"        }",
"        QueueToggle(",
"            when (repeatMode) { 2 -> com.music.spotui.ui.design.AppIcons.RepeatOne; 3 -> RepeatIcons.once; else -> com.music.spotui.ui.design.AppIcons.Repeat },",
"            stringResource(R.string.immersive_queue_repeat), repeatMode != 0,",
"        ) { SongPlayer.cycleRepeat() }",
"        SleepToggle()",
"    }",
"    }",
])
s = s[:end] + dock + s[end:]
for imp in ['androidx.compose.foundation.border', 'androidx.compose.foundation.shape.RoundedCornerShape', 'androidx.compose.foundation.layout.Arrangement']:
    if ('import ' + imp + nl) not in s: s = s.replace(nl + 'import ', nl + 'import ' + imp + nl + 'import ', 1)
open(Q, 'wb').write(s.encode('utf-8'))
# The header's own toggles: the timer moved to the dock too.
edit(Q, [("""            onRepeat,
        )
        Spacer(Modifier.width(8.dp))
        SleepToggle()
    }
}""", """            onRepeat,
        )
    }
}""")])

# ---------- Classic queue rows: the video label ----------
S = 'ui/components/PlayerSheets.kt'
edit(S, [("""                if (song.explicit) { ExplicitBadge(); Spacer(Modifier.width(6.dp)) }
                Text(song.singer,""", """                if (song.explicit) { ExplicitBadge(); Spacer(Modifier.width(6.dp)) }
                if (!current && rememberHasVideo(song)) { VideoBadge(); Spacer(Modifier.width(6.dp)) }
                Text(song.singer,""")])

# ---------- Immersive cards: solid, readable ----------
I = 'ui/immersive/player/ImmersiveInfoPanel.kt'
edit(I, [
("""        Modifier.fillMaxWidth().clip(shape).background(tint.copy(alpha = 0.08f))""", """        Modifier.fillMaxWidth().clip(shape).background(cardGround())"""),
("""    Column(Modifier.fillMaxWidth().clip(shape).background(tint.copy(alpha = 0.08f)).border(0.5.dp, tint.copy(alpha = 0.14f), shape)) {""",
 """    Column(Modifier.fillMaxWidth().clip(shape).background(cardGround()).border(0.5.dp, tint.copy(alpha = 0.14f), shape)) {"""),
("""    val tint = LocalPlayerTint.current
    val info = rememberSongInfo(state)
    Column(modifier) {""", """    val info = rememberSongInfo(state)
    // Solid cards with white words: over the moving backdrop the see-through ones were hard to read.
    androidx.compose.runtime.CompositionLocalProvider(LocalPlayerTint provides Color.White, LocalSolidCards provides true) {
    val tint = LocalPlayerTint.current
    Column(modifier) {"""),
("""        info.about?.let { a ->
            SectionTitle(stringResource(R.string.lyrics_meaning_about))
            GlassCard { Text(a, color = tint, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 12, overflow = TextOverflow.Ellipsis) }
        }
    }
}""", """        info.about?.let { a ->
            SectionTitle(stringResource(R.string.lyrics_meaning_about))
            GlassCard { Text(a, color = tint, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 12, overflow = TextOverflow.Ellipsis) }
        }
        GlassCard { com.music.spotui.ui.components.RelatedVideos(state.artist, state.title, Color.White, Color.White.copy(alpha = 0.65f)) }
        info.credits?.let { c -> GlassCard { com.music.spotui.ui.components.Contributors(c, info.artist, Color.White, Color.White.copy(alpha = 0.65f)) { actions.openArtistRef(it) } } }
    }
    }
}

internal val LocalSolidCards = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
private fun cardGround(): Color = if (LocalSolidCards.current) Color(0xFF1E1E25) else LocalPlayerTint.current.copy(alpha = 0.08f)"""),
])

# ---------- Classic cards: related videos and contributors ----------
C = 'ui/components/ClassicInfoCards.kt'
edit(C, [("""        info?.artist?.let { page ->
            val a = page.artist
            val photo = (page.headerImages.ifEmpty { a.images }).maxByOrNull { it.width ?: 0 }?.url
            Card {
                CardTitle(stringResource(R.string.notice_explore, a.name))""", """        Card { RelatedVideos(artist, title, CardText, CardSub) }
        info?.credits?.let { c -> Card { Contributors(c, info.artist, CardText, CardSub, openArtist) } }
        info?.artist?.let { page ->
            val a = page.artist
            val photo = (page.headerImages.ifEmpty { a.images }).maxByOrNull { it.width ?: 0 }?.url
            Card {
                CardTitle(stringResource(R.string.notice_explore, a.name))""")],
append="""
/** A small "video" mark beside a song that has a music video. */
@Composable
fun VideoBadge() {
    androidx.compose.material3.Icon(
        androidx.compose.material.icons.Icons.Default.OndemandVideo, contentDescription = stringResource(R.string.notice_has_video),
        tint = Color(0xFFB3B3B3), modifier = Modifier.size(15.dp),
    )
}

/** Whether [song] has a music video: what is already known, and a lookup in the background (same limits as the player's). */
@Composable
fun rememberHasVideo(song: com.music.spotui.data.entity.SongsModel): Boolean {
    val id = song.spotifyTrackId
    val known by androidx.compose.runtime.produceState(false, id) {
        if (id.isBlank()) return@produceState
        value = com.music.spotui.video.MusicVideoController.knownVideo(id)
        if (!value) {
            com.music.spotui.video.MusicVideoController.prefetch(id, song.title, song.singer, song.durationMs.toLong())
            repeat(6) { kotlinx.coroutines.delay(5_000); if (!value) value = com.music.spotui.video.MusicVideoController.knownVideo(id) }
        }
    }
    return known
}

private class RelatedVideo(val url: String, val name: String, val uploader: String, val seconds: Long) {
    val thumb: String get() = "https://i.ytimg.com/vi/" + url.substringAfter("v=").substringBefore('&') + "/hqdefault.jpg"
}

/** "Related videos": the artist's other music videos; a tap opens it in the YouTube app or the browser. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun RelatedVideos(artist: String, title: String, text: Color, sub: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val main = artist.substringBefore(',').trim()
    val videos by androidx.compose.runtime.produceState<List<RelatedVideo>>(emptyList(), main, title) {
        if (main.isBlank() || com.music.spotui.data.DataSaver.active) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val key = main.lowercase()
                com.music.spotui.video.YouTubeSource.searchVideos("$main official music video")
                    .filter { it.uploader.lowercase().contains(key) || it.name.lowercase().contains(key) }
                    .filterNot { title.isNotBlank() && it.name.lowercase().contains(title.lowercase()) }
                    .filter { it.durationSec in 60..900 }
                    .distinctBy { it.url }
                    .take(8)
                    .map { RelatedVideo(it.url, it.name, it.uploader, it.durationSec) }
            }.getOrDefault(emptyList())
        }
    }
    if (videos.isEmpty()) return
    Text(stringResource(R.string.notice_related_videos), color = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos.size) { i ->
            val v = videos[i]
            Column(Modifier.width(220.dp).clickable {
                Haptics.click()
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(v.url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
                    GlideImage(model = v.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Text(
                        "%d:%02d".format(v.seconds / 60, v.seconds % 60), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(v.name, color = text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(v.uploader, color = sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Contributors": everyone who made the song, as round portraits with their roles; a tap opens the artist. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun Contributors(credits: app.myspoti.api.Credits, page: app.myspoti.api.ArtistPage?, text: Color, sub: Color, open: (app.myspoti.api.ArtistRef) -> Unit) {
    val people = remember(credits) {
        val byName = LinkedHashMap<String, Pair<app.myspoti.api.ArtistRef, MutableList<String>>>()
        credits.roles.forEach { r -> r.artists.forEach { p -> byName.getOrPut(p.name) { p to mutableListOf() }.second += r.role } }
        byName.values.toList()
    }
    if (people.isEmpty()) return
    val mainPhoto = page?.artist?.images?.maxByOrNull { it.width ?: 0 }?.url
    Text(stringResource(R.string.notice_contributors), color = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(people.size) { i ->
            val (p, roles) = people[i]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(112.dp).then(if (p.uri.isNotBlank()) Modifier.clickable { open(p) } else Modifier),
            ) {
                Box(Modifier.size(96.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFF3A3A44)), contentAlignment = Alignment.Center) {
                    if (p.uri == page?.artist?.uri && mainPhoto != null) GlideImage(model = mainPhoto, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text(p.name.take(1).uppercase(), color = text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Text(p.name, color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(roles.distinct().joinToString(", "), color = sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
""")

# ---------- Immersive player: the top of the first card peeks under the controls ----------
P = 'ui/immersive/player/ImmersivePlayer.kt'
edit(P, [("""            Column(Modifier.fillMaxWidth().height(pageH).statusBarsPadding().navigationBarsPadding()) {""",
"""            // The top of the lyrics card shows at the foot of the player (as the official app), so the
            // page reads as one that scrolls; gone while a panel is open.
            val peek by androidx.compose.animation.core.animateDpAsState(if (panel == Panel.NONE) 56.dp else 0.dp, tween(PANEL_TRAVEL_MS), label = "peek")
            Column(Modifier.fillMaxWidth().height(pageH - peek).statusBarsPadding().then(if (panel == Panel.NONE) Modifier.padding(bottom = 8.dp) else Modifier.navigationBarsPadding())) {""")])

open('O:/base/app/src/main/res/values/notice_strings.xml', 'rb').read()
s = open('O:/base/app/src/main/res/values/notice_strings.xml', 'rb').read().decode('utf-8')
s = s.replace('</resources>', '''    <string name="notice_has_video">Has a music video</string>
    <string name="notice_related_videos">Related videos</string>
    <string name="notice_contributors">Contributors</string>
    <string name="share_theme_glass">Glass</string>
    <string name="share_theme_gradient">Gradient</string>
    <string name="share_theme_solid">Solid</string>
    <string name="share_card_send">Share</string>
</resources>''')
open('O:/base/app/src/main/res/values/notice_strings.xml', 'wb').write(s.encode('utf-8'))
print('ok')
