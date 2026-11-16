package com.music.spotui.ui.components

import androidx.compose.material.icons.filled.PlayArrow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clips: a full-screen vertical feed of songs picked for the user (their liked songs, their top
 * songs, the top songs of their top artists), each shown with its looping Canvas video and played
 * from its middle. Swipe up for the next one; double-tap to like; swipe right to add to the queue;
 * "Play full song" opens the player on the same song at the same moment. A hashtag narrows the feed.
 * Drawn over the whole app (both looks); opened from Search and Home.
 */
object ClipsFeed {
    internal val open = mutableStateOf(false)
    internal val filter = mutableStateOf<String?>(null)
    fun show(tag: String? = null) { filter.value = tag; open.value = true }
}

internal class Clip(val song: SongsModel, val why: String, val tags: List<String>)

private fun tagOf(s: String) = "#" + s.filter { it.isLetterOrDigit() }

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

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ClipsFeedHost() {
    val shown = ClipsFeed.open.value
    AnimatedVisibility(shown, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        ClipsFeedScreen()
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ClipsFeedScreen() {
    val context = LocalContext.current
    BackHandler { ClipsFeed.open.value = false }
    val all by produceState<List<Clip>?>(null) { value = loadClips(context) }
    val tag = ClipsFeed.filter.value
    val clips = remember(all, tag) { all?.filter { tag == null || tag in it.tags } }
    val saved = remember { mutableStateListOf<SongsModel>() }
    var offerDismissed by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            clips == null -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(32.dp))
            clips.isEmpty() -> Text(stringResource(R.string.clips_empty), color = Color.White, fontSize = 16.sp, modifier = Modifier.align(Alignment.Center).padding(32.dp))
            else -> {
                val pager = rememberPagerState { clips.size }
                // The song of the page settled on plays from its middle: the clip and its sound together.
                LaunchedEffect(pager.settledPage, clips) {
                    val s = clips.getOrNull(pager.settledPage)?.song ?: return@LaunchedEffect
                    kotlinx.coroutines.delay(250)
                    SongPlayer.playSong(s.url, context, positionMs = (s.durationMs * 0.35).toLong(), songs = listOf(s), alone = true)
                }
                VerticalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { clips[it].song.url }) { page ->
                    ClipPage(clips[page], active = page == pager.settledPage, saved = saved)
                }
            }
        }
        // Top bar: close, the title, the active hashtag (tap it to clear).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                com.music.spotui.ui.design.AppIcons.Close, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { ClipsFeed.open.value = false }.padding(8.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.clips_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (tag != null) Text(
                "$tag  ✕", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                    .clickable { ClipsFeed.filter.value = null }.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        // After a few saves: a playlist from them.
        if (saved.size >= 3 && !offerDismissed) MakePlaylistCard(saved.toList()) { offerDismissed = true }
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
    var liked by remember(s.url) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(s.url) {
        liked = runCatching { MyspotiRuntime.api.library.contains(listOf("spotify:track:" + s.spotifyTrackId)).firstOrNull() }.getOrNull()
    }
    val heart = remember { Animatable(0f) }
    val swipe = remember { Animatable(0f) }
    fun like() {
        if (liked == true) return
        liked = true
        if (saved.none { it.url == s.url }) saved += s
        scope.launch { heart.snapTo(1f); heart.animateTo(0f, tween(700)) }
        scope.launch { runCatching { MyspotiRuntime.api.library.add(listOf("spotify:track:" + s.spotifyTrackId)) }.onFailure { liked = false } }
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
        // Full screen: ask for the 640 px cover, not the 300 / 64 px one the song row carries.
        val big = s.coverUri.replace("ab67616d00001e02", "ab67616d0000b273").replace("ab67616d00004851", "ab67616d0000b273")
        GlideImage(model = big, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.9f })
        val url = canvas
        if (active && url != null) com.music.spotui.ui.screens.CanvasVideo(url, playing = true, onFirstFrame = {}, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))))
        // The double-tap heart.
        Icon(
            com.music.spotui.ui.design.AppIcons.Star, contentDescription = null, tint = Color.White,
            modifier = Modifier.align(Alignment.Center).size(120.dp).graphicsLayer { alpha = heart.value; scaleX = 0.6f + heart.value * 0.6f; scaleY = scaleX },
        )
        // Swiped right: "Add to queue" shows on the left.
        if (swipe.value > 8f) Text(
            stringResource(R.string.clips_added_to_queue), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).graphicsLayer { alpha = (swipe.value / 240f).coerceIn(0f, 1f) },
        )
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            // Why this one.
            Text(clip.why, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlideImage(model = s.coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.singer, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(
                    if (liked == true) com.music.spotui.ui.design.AppIcons.Star else com.music.spotui.ui.design.AppIcons.StarBorder,
                    contentDescription = null, tint = if (liked == true) com.music.spotui.ui.immersive.Immersive.Accent else Color.White,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { if (liked == true) Unit else like() }.padding(10.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            // Hashtags: a tap narrows the feed to that tag.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(clip.tags.size) { i ->
                    val t = clip.tags[i]
                    Text(
                        t, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f))
                            .clickable { Haptics.tick(); ClipsFeed.filter.value = t }.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.clips_play_full), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White)
                    .clickable { Haptics.confirm(); ClipsFeed.open.value = false; com.music.spotui.ui.navigation.PlayerSheet.show() }
                    .padding(vertical = 13.dp),
            )
        }
    }
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
            Icon(com.music.spotui.ui.design.AppIcons.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp).clip(CircleShape).clickable { onDone() }.padding(6.dp))
        }
    }
}

/** The entry to the feed, for Search and Home: a wide tile with the latest covers. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ClipsEntryCard(modifier: Modifier = Modifier) {
    val accent = com.music.spotui.ui.immersive.Immersive.Accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.55f), Color(0xFF3A1C4A))))
            .clickable { Haptics.click(); ClipsFeed.show() }
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.clips_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.clips_entry_detail), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
        Icon(androidx.compose.material.icons.Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}
