package com.music.spotui.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.music.spotui.di.Haptics
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.design.AppIcons
import com.music.spotui.video.MusicVideoController
import com.music.spotui.video.YouTubeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A video picked from "Related videos", played inside the app (it used to open YouTube): the clip
 * full width at the top with its own controls, its title and channel under it. The song pauses
 * while it plays. Drawn over the whole app (both looks); back or the chevron closes it.
 */
object InAppVideo {
    data class Item(val url: String, val title: String, val channel: String)
    internal val current = mutableStateOf<Item?>(null)
    fun open(url: String, title: String, channel: String) {
        SongPlayer.pause(by = "related video")
        current.value = Item(url, title, channel)
    }
}

@Composable
fun InAppVideoHost() {
    val item = InAppVideo.current.value
    var shown by remember { mutableStateOf<InAppVideo.Item?>(null) }
    if (item != null) shown = item
    AnimatedVisibility(item != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        shown?.let { InAppVideoScreen(it) }
    }
}

/** The sources for [url]: the sharpest H.264 picture up to 720p with the best AAC sound, or one stream with both. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private suspend fun sourceFor(url: String): MediaSource = withContext(Dispatchers.IO) {
    val info = YouTubeSource.info(url)
    fun factory(content: String) = ProgressiveMediaSource.Factory(
        DefaultHttpDataSource.Factory().apply { MusicVideoController.streamHeaders(content)["User-Agent"]?.let { setUserAgent(it) } },
    )
    fun progressive(content: String) = factory(content).createMediaSource(MediaItem.fromUri(content))
    val video = info.videoOnlyStreams
        .filter { it.height in 1..720 && it.codec.orEmpty().startsWith("avc") }
        .maxByOrNull { it.height }
    val audio = info.audioStreams
        .filter { it.format == org.schabi.newpipe.extractor.MediaFormat.M4A }
        .maxByOrNull { it.averageBitrate }
        ?: info.audioStreams.maxByOrNull { it.averageBitrate }
    if (video != null && audio != null) MergingMediaSource(progressive(video.content), progressive(audio.content))
    else progressive(info.videoStreams.maxByOrNull { it.height }?.content ?: error("no stream"))
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun InAppVideoScreen(item: InAppVideo.Item) {
    val context = LocalContext.current
    val close = { InAppVideo.current.value = null }
    BackHandler { close() }
    val player = remember(item.url) { ExoPlayer.Builder(context).build() }
    var aspect by remember(item.url) { mutableFloatStateOf(16f / 9f) }
    var loading by remember(item.url) { mutableStateOf(true) }
    var failed by remember(item.url) { mutableStateOf(false) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onVideoSizeChanged(v: VideoSize) {
                if (v.width > 0 && v.height > 0) aspect = (v.width * v.pixelWidthHeightRatio / v.height).coerceIn(0.5f, 2.4f)
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) loading = false }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) { failed = true; loading = false }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(item.url) {
        runCatching { sourceFor(item.url) }
            .onSuccess { player.setMediaSource(it); player.prepare(); player.playWhenReady = true }
            .onFailure { failed = true; loading = false }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF121212)).clickable(enabled = false) {}.statusBarsPadding()) {
        // Top bar: close (down chevron), "Playing video" centred.
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            Icon(
                AppIcons.Back, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(44.dp).clip(CircleShape)
                    .clickable { Haptics.click(); close() }.padding(10.dp),
            )
            Text(androidx.compose.ui.res.stringResource(app.myspoti.engine.R.string.in_app_video_title), color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
        }
        // The clip, full width, at its own shape (a tall one is capped so its words stay on screen).
        Box(Modifier.fillMaxWidth().aspectRatio(maxOf(aspect, 0.8f)).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply { this.player = player; setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER) } },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            if (failed) Text(
                androidx.compose.ui.res.stringResource(app.myspoti.engine.R.string.in_app_video_failed),
                color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(24.dp),
            )
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(item.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row { Text(item.channel, color = Color(0xFFB3B3B3), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
