package com.music.spotui.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.Graph
import com.music.spotui.di.Haptics
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.design.AppIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Clips: a full-screen vertical feed of songs picked for the user (their liked songs, their top
 * songs, the top songs of their top artists), each shown with its looping Canvas video and played
 * from its middle. Swipe up for the next one; double-tap to like; swipe right to add to the queue;
 * a tap on the song card opens the player on it. Each hashtag is a playlist you can watch: Search
 * shows them as a row of tall tiles. Drawn over the whole app (both looks).
 */
object ClipsFeed {
    internal val open = mutableStateOf(false)
    internal val filter = mutableStateOf<String?>(null)
    /** The feed, loaded once for the session (Search's row and the feed share it). */
    internal val all = mutableStateOf<List<Clip>?>(null)
    private val lock = Mutex()
    fun show(tag: String? = null) { filter.value = tag; open.value = true }

    internal suspend fun ensure(context: android.content.Context) = lock.withLock {
        if (all.value == null) all.value = runCatching { loadClips(context) }.getOrDefault(emptyList())
    }
}

internal class Clip(val song: SongsModel, val why: String, val tags: List<String>)

private fun tagOf(s: String) = "#" + s.filter { it.isLetterOrDigit() }

/** The 640 px cover, not the 300 / 64 px one a song row carries: these are drawn full screen. */
private fun bigCover(url: String) = url.replace("ab67616d00001e02", "ab67616d0000b273").replace("ab67616d00004851", "ab67616d0000b273")

/** The songs of the feed, mixed: liked, top, top artists' songs. Nothing for an account without Spotify. */
private suspend fun loadClips(context: android.content.Context): List<Clip> = withContext(Dispatchers.IO) {
    val api = MyspotiRuntime.api
    val out = LinkedHashMap<String, Clip>()
    fun add(t: app.myspoti.api.Track, why: String, tag: String) {
        if (!t.uri.startsWith("spotify:track:") || out.containsKey(t.uri)) return
        val song = Graph.api.songOf(t)
        val artist = t.artists.firstOrNull()?.name.orEmpty()
        out[t.uri] = Clip(song, why, listOfNotNull(tag, artist.takeIf { it.isNotBlank() }?.let(::tagOf)))
    }
    val liked = runCatching { api.library.likedTracks(0, 50).items.map { it.track } }.getOrDefault(emptyList()).shuffled().take(15)
    val top = runCatching { api.users.top(app.myspoti.api.TimeRange.SHORT_TERM, 20) }.getOrNull()
    val topTracks = top?.tracks.orEmpty().shuffled().take(12)
    val artistTracks = top?.artists.orEmpty().take(4).flatMap { a ->
        runCatching { api.catalog.artistTopTracks(a.uri).take(4).map { it to a.name } }.getOrDefault(emptyList())
    }.shuffled()
    val lists = listOf(
        liked.map { Triple(it, context.getString(R.string.clips_why_liked), "#LikedSongs") },
        topTracks.map { Triple(it, context.getString(R.string.clips_why_top), "#OnRepeat") },
        artistTracks.map { (t, name) -> Triple(t, context.getString(R.string.clips_why_artist, name), "#ForYou") },
    )
    // Interleaved, so the feed is not a block of one kind.
    var i = 0
    while (lists.any { i < it.size }) { lists.forEach { l -> l.getOrNull(i)?.let { (t, why, tag) -> add(t, why, tag) } }; i++ }
    out.values.toList()
}

@Composable
fun ClipsFeedHost() {
    val shown = ClipsFeed.open.value
    AnimatedVisibility(shown, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        ClipsFeedScreen()
    }
}

@Composable
private fun ClipsFeedScreen() {
    val context = LocalContext.current
    BackHandler { ClipsFeed.open.value = false }
    LaunchedEffect(Unit) { ClipsFeed.ensure(context) }
    val all = ClipsFeed.all.value
    val tag = ClipsFeed.filter.value
    val clips = remember(all, tag) { all?.filter { tag == null || tag in it.tags } }
    val saved = remember { mutableStateListOf<SongsModel>() }
    var offerDismissed by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            clips == null -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(32.dp))
            clips.isEmpty() -> Text(stringResource(R.string.clips_empty), color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            else -> {
                val pager = rememberPagerState { clips.size }
                LaunchedEffect(pager.settledPage) { page = pager.settledPage }
                // The song of the page settled on plays from its middle: the clip and its sound together.
                LaunchedEffect(pager.settledPage, clips) {
                    val s = clips.getOrNull(pager.settledPage)?.song ?: return@LaunchedEffect
                    delay(250)
                    SongPlayer.playSong(s.url, context, positionMs = (s.durationMs * 0.35).toLong(), songs = listOf(s), alone = true)
                }
                VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { clips[it].song.url }) { p ->
                    ClipPage(clips[p], active = p == pager.settledPage, saved = saved)
                }
            }
        }
        // Top: back, the playlist's name centred, and dots for where you are in it.
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            val count = clips?.size ?: 0
            if (count > 1) PageDots(page, count, Modifier.align(Alignment.TopCenter).padding(top = 2.dp))
            Icon(
                AppIcons.Back, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.CenterStart).padding(top = 10.dp).size(44.dp).clip(CircleShape)
                    .clickable { ClipsFeed.open.value = false }.padding(10.dp),
            )
            Text(
                tag ?: stringResource(R.string.clips_for_you), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(top = 10.dp, start = 56.dp, end = 56.dp)
                    // A filtered feed: a tap on the name goes back to everything.
                    .clickable(enabled = tag != null) { Haptics.tick(); ClipsFeed.filter.value = null },
            )
        }
        // After a few saves: a playlist from them.
        if (saved.size >= 3 && !offerDismissed) MakePlaylistCard(saved.toList()) { offerDismissed = true }
    }
}

/** A short line of dots, the current one a wider pill (at most five shown, sliding along). */
@Composable
private fun PageDots(page: Int, count: Int, modifier: Modifier) {
    val shown = minOf(count, 5)
    val first = (page - 2).coerceIn(0, count - shown)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in first until first + shown) {
            val on = i == page
            val edge = !on && (i == first && first > 0 || i == first + shown - 1 && first + shown < count)
            Box(
                Modifier.height(if (edge) 4.dp else 6.dp).width(if (on) 22.dp else if (edge) 4.dp else 6.dp)
                    .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (on) 1f else 0.45f)),
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ClipPage(clip: Clip, active: Boolean, saved: MutableList<SongsModel>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = clip.song
    val canvas by produceState<String?>(null, s.spotifyTrackId, active) {
        if (active && !com.music.spotui.data.DataSaver.active && s.spotifyTrackId.isNotBlank()) value = runCatching { Graph.api.getCanvasUrl(s.spotifyTrackId) }.getOrNull()
    }
    // The artist: photo, followers, follow (from the artist's page, once the page is shown).
    val artist by produceState<app.myspoti.api.Artist?>(null, s.spotifyTrackId, active) {
        if (!active || value != null || s.spotifyTrackId.isBlank()) return@produceState
        value = runCatching {
            val ref = ArtistChooser.artistsOf(s.spotifyTrackId, s.singer).firstOrNull { it.uri.startsWith("spotify:artist:") } ?: return@runCatching null
            MyspotiRuntime.api.catalog.artistPage(ref.uri).artist
        }.getOrNull()
    }
    var liked by remember(s.url) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(s.url) {
        liked = runCatching { MyspotiRuntime.api.library.contains(listOf("spotify:track:" + s.spotifyTrackId)).firstOrNull() }.getOrNull()
    }
    // How far into the song the clip is (it starts at about a third).
    var progress by remember(s.url) { mutableFloatStateOf(0.35f) }
    LaunchedEffect(active, s.url) {
        if (!active || s.durationMs <= 0) return@LaunchedEffect
        progress = 0.35f
        val step = 500L
        while (progress < 1f) { delay(step); progress = (progress + step.toFloat() / s.durationMs).coerceAtMost(1f) }
    }
    val heart = remember { Animatable(0f) }
    val swipe = remember { Animatable(0f) }
    fun like() {
        if (liked == true) return
        liked = true
        if (saved.none { it.url == s.url }) saved += s
        scope.launch { heart.snapTo(1f); heart.animateTo(0f, tween(700)) }
        scope.launch {
            runCatching { MyspotiRuntime.api.library.add(listOf("spotify:track:" + s.spotifyTrackId)) }
                .onSuccess {
                    com.music.spotui.data.offline.OfflineCollections.refreshOne(context.applicationContext,
                        "spotify:collection:tracks", mapOf(s.spotifyTrackId to true))
                }.onFailure { liked = false }
        }
        AppNotice.show(context.getString(R.string.immersive_player_added_to_liked), s.title, coverUrl = s.coverUri, tone = AppNotice.Tone.LIKE)
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(s.url) { detectTapGestures(onDoubleTap = { like() }) }
            .pointerInput(s.url) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (swipe.value > 120.dp.toPx()) {
                            SongPlayer.addToQueue(s.spotifyTrackId)
                            AppNotice.show(context.getString(R.string.clips_added_to_queue), s.title, coverUrl = s.coverUri, tone = AppNotice.Tone.SUCCESS)
                        }
                        scope.launch { swipe.animateTo(0f) }
                    },
                ) { c, dx -> c.consume(); scope.launch { swipe.snapTo((swipe.value + dx).coerceIn(0f, 400f)) } }
            }
            .graphicsLayer { translationX = swipe.value * 0.5f },
    ) {
        // The cover always (under the clip while it loads, alone without one or with data saver on).
        GlideImage(model = bigCover(s.coverUri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val url = canvas
        if (active && url != null) com.music.spotui.ui.screens.CanvasVideo(url, playing = true, onFirstFrame = {}, modifier = Modifier.fillMaxSize())
        // A shade at the top for the bar and a deeper one at the foot for the words.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.45f), 0.14f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.88f),
        )))
        // The double-tap like.
        Icon(
            AppIcons.Star, contentDescription = null, tint = Color.White,
            modifier = Modifier.align(Alignment.Center).size(120.dp).graphicsLayer { alpha = heart.value; scaleX = 0.6f + heart.value * 0.6f; scaleY = scaleX },
        )
        // Swiped right: "Added to queue" shows on the left.
        if (swipe.value > 8f) Text(
            stringResource(R.string.clips_added_to_queue), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).graphicsLayer { alpha = (swipe.value / 240f).coerceIn(0f, 1f) },
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
            // The artist, followers, Follow; share at the end.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
                val a = artist
                val photo = a?.images?.minByOrNull { kotlin.math.abs((it.width ?: 300) - 160) }?.url
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    if (photo != null) GlideImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text(s.singer.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(a?.name ?: s.singer.substringBefore(","), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    a?.followers?.let { Text(stringResource(R.string.clips_followers, compact(it)), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp) }
                }
                if (a != null) { Spacer(Modifier.width(12.dp)); FollowPill(a.uri) }
                Spacer(Modifier.weight(1f))
                Icon(
                    AppIcons.Share, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable {
                        Haptics.click()
                        scope.launch { ShareCard.share(context, s.title, s.singer, s.coverUri, context.getString(R.string.share_card_send)) }
                    }.padding(10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            // Hashtags (a tap plays that playlist), more at the end.
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(clip.tags.size) { i ->
                        val t = clip.tags[i]
                        Text(
                            t, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { Haptics.tick(); ClipsFeed.filter.value = t }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                    }
                }
                Icon(
                    AppIcons.MoreVert, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { Haptics.click(); AppNotice.openSaveTo(s) }.padding(10.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            // The song: a tap plays it in full (the player opens on it); + saves it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xCC2A2A2E))
                    .clickable { Haptics.confirm(); ClipsFeed.open.value = false; com.music.spotui.ui.navigation.PlayerSheet.show() }
                    .padding(10.dp),
            ) {
                GlideImage(model = s.coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = Color.White, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(s.singer, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    if (liked == true) AppIcons.Check else AppIcons.AddCircleOutline,
                    contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                        .then(if (liked == true) Modifier.background(com.music.spotui.ui.immersive.Immersive.Accent) else Modifier)
                        .clickable { if (liked == true) AppNotice.openSaveTo(s) else like() }
                        .padding(if (liked == true) 12.dp else 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            // Where the clip is in the song.
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color.White))
            }
        }
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> String.format(java.util.Locale.getDefault(), "%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 1_000 -> String.format(java.util.Locale.getDefault(), "%.1fK", n / 1_000.0).replace(".0K", "K")
    else -> n.toString()
}

/** "Make a playlist from these": the songs liked in the feed, in a new playlist. */
@Composable
private fun MakePlaylistCard(songs: List<SongsModel>, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xF2202026)).padding(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.clips_make_playlist), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.clips_make_playlist_detail, songs.size), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            }
            Text(
                stringResource(R.string.clips_make), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable {
                    Haptics.confirm(); onDone()
                    scope.launch {
                        runCatching {
                            val api = MyspotiRuntime.api
                            val uri = api.playlists.create(context.getString(R.string.clips_playlist_name))
                            api.playlists.addTracks(uri, songs.map { "spotify:track:" + it.spotifyTrackId })
                        }.onSuccess { AppNotice.show(context.getString(R.string.clips_playlist_made), tone = AppNotice.Tone.SUCCESS) }
                            .onFailure { AppNotice.show(context.getString(R.string.library_change_failed), tone = AppNotice.Tone.ERROR) }
                    }
                }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(6.dp))
            Icon(AppIcons.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onDone() }.padding(6.dp))
        }
    }
}

/**
 * "Playlists you can watch", for Search: a row of tall tiles, each a playlist of clips (all of
 * them, then one per hashtag), with the cover of its first song. A tap plays it in the feed.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ClipsEntryCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ClipsFeed.ensure(context) }
    val all = ClipsFeed.all.value
    if (all != null && all.isEmpty()) return
    val forYou = stringResource(R.string.clips_for_you)
    // All, then the hashtags with the most songs (at least two each).
    val lists: List<Pair<String?, Clip>>? = remember(all) {
        all?.let { clips ->
            val tags = clips.flatMap { it.tags }.groupingBy { it }.eachCount().filter { it.value >= 2 }.entries
                .sortedByDescending { it.value }.take(8).map { it.key }
            listOf<Pair<String?, Clip>>(null to clips.first()) + tags.mapNotNull { t ->
                // A different song on each tile where it can be.
                clips.firstOrNull { t in it.tags && it !== clips.first() }?.let { t to it }
            }
        }
    }
    Column(modifier.fillMaxWidth()) {
        Text(stringResource(R.string.clips_watch_row), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (lists == null) items(3) {
                Box(Modifier.size(width = 150.dp, height = 222.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)))
            } else items(lists.size) { i ->
                val (tag, clip) = lists[i]
                Box(
                    Modifier.size(width = 150.dp, height = 222.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A))
                        .clickable { Haptics.click(); ClipsFeed.show(tag) },
                ) {
                    GlideImage(model = bigCover(clip.song.coverUri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
                    Text(
                        tag ?: forYou, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                    )
                }
            }
        }
    }
}
