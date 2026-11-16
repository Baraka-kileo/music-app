package com.music.spotui.ui.components

import androidx.compose.material.icons.filled.OndemandVideo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import app.myspoti.engine.R
import app.myspoti.host.android.MyspotiRuntime
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.di.Haptics
import com.music.spotui.di.SongPlayer
import kotlinx.coroutines.launch

private val CardGround = Color(0xFF2A2A2A)
private val CardText = Color.White
private val CardSub = Color(0xFFB3B3B3)
private val CardGreen = Color(0xFF1ED760)
private val CardShape = RoundedCornerShape(12.dp)

/**
 * The Classic player's cards under the lyrics card, in the layout of the official app: about the
 * artist, up next, credits, the song's story and "explore". Only cards with something to show.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ClassicInfoCards(
    trackId: String,
    title: String,
    artist: String,
    coverUrl: String,
    navController: NavController,
    onOpenQueue: () -> Unit,
    onOpenAlbum: () -> Unit,
) {
    val info = com.music.spotui.ui.immersive.player.rememberSongInfo(trackId, title, artist)
    val openArtist: (app.myspoti.api.ArtistRef) -> Unit = { navController.navigate(ArtistChooser.routeOf(it)) }
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        info?.artist?.let { ArtistAbout(it, openArtist) }
        UpNext(onOpenQueue)
        info?.credits?.let { Credits(it, info.artist?.artist?.uri, openArtist) }
        info?.about?.let { a ->
            Card {
                CardTitle(stringResource(R.string.lyrics_meaning_about))
                Spacer(Modifier.height(10.dp))
                Text(a, color = CardSub, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
        }
        Card { RelatedVideos(artist, title, CardText, CardSub) }
        info?.credits?.let { c -> Card { Contributors(c, info.artist, CardText, CardSub, openArtist) } }
        info?.artist?.let { page ->
            val a = page.artist
            val photo = (page.headerImages.ifEmpty { a.images }).maxByOrNull { it.width ?: 0 }?.url
            Card {
                CardTitle(stringResource(R.string.notice_explore, a.name))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(stringResource(R.string.notice_songs_by, a.name), photo, Modifier.weight(1f)) { openArtist(app.myspoti.api.ArtistRef(a.uri, a.name)) }
                    Tile(stringResource(R.string.notice_from_album), coverUrl, Modifier.weight(1f)) { onOpenAlbum() }
                }
            }
        }
    }
}

@Composable
private fun Card(padding: Boolean = true, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(CardShape).background(CardGround).then(if (padding) Modifier.padding(16.dp) else Modifier)) { content() }
}

@Composable
private fun CardTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = CardText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) Text(
            action, color = CardGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { Haptics.tick(); onAction() }.padding(4.dp),
        )
    }
}

@Composable
internal fun FollowPill(uri: String) {
    val scope = rememberCoroutineScope()
    var following by remember(uri) { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(uri) {
        following = runCatching { MyspotiRuntime.api.library.contains(listOf(uri)).firstOrNull() }.getOrNull()
    }
    val on = following ?: return
    Text(
        stringResource(if (on) R.string.curation_button_following_state_text else R.string.curation_button_follow_state_text),
        color = CardText, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, CardSub, RoundedCornerShape(50))
            .clickable {
                Haptics.confirm()
                following = !on
                scope.launch {
                    runCatching { if (on) MyspotiRuntime.api.library.remove(listOf(uri)) else MyspotiRuntime.api.library.add(listOf(uri)) }
                        .onFailure { following = on }
                }
            }
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ArtistAbout(page: app.myspoti.api.ArtistPage, openArtist: (app.myspoti.api.ArtistRef) -> Unit) {
    val a = page.artist
    val photo = (page.headerImages.ifEmpty { a.images }).maxByOrNull { it.width ?: 0 }?.url
    var expanded by remember(a.uri) { mutableStateOf(false) }
    Card(padding = false) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.25f).clickable { openArtist(app.myspoti.api.ArtistRef(a.uri, a.name)) }) {
            if (photo != null) GlideImage(model = photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().height(80.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent))))
            Text(stringResource(R.string.about_the_artist), color = CardText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = CardText, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    a.monthlyListeners?.let {
                        Text(stringResource(R.string.monthly_listeners_text, java.text.NumberFormat.getIntegerInstance().format(it)), color = CardSub, fontSize = 14.sp)
                    }
                }
                FollowPill(a.uri)
            }
            page.biography?.let { bio ->
                val plain = androidx.core.text.HtmlCompat.fromHtml(bio, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                if (plain.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        plain, color = CardSub, fontSize = 14.sp, lineHeight = 20.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun UpNext(onOpenQueue: () -> Unit) {
    val q = SongPlayer.engineQueue.value
    val next = (q.queued.firstOrNull() ?: q.nextUp.firstOrNull())?.song ?: return
    Card {
        CardTitle(stringResource(R.string.notice_up_next), stringResource(R.string.notice_open_queue), onOpenQueue)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onOpenQueue() }) {
            GlideImage(model = next.coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(next.title, color = CardText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(next.singer, color = CardSub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Credits(credits: app.myspoti.api.Credits, mainArtist: String?, openArtist: (app.myspoti.api.ArtistRef) -> Unit) {
    var all by remember { mutableStateOf(false) }
    // One line per person, with every role they had (as the official card lists them).
    val people = remember(credits) {
        val byName = LinkedHashMap<String, Pair<app.myspoti.api.ArtistRef, MutableList<String>>>()
        credits.roles.forEach { r -> r.artists.forEach { p -> byName.getOrPut(p.name) { p to mutableListOf() }.second += r.role } }
        byName.values.toList()
    }
    Card {
        CardTitle(stringResource(R.string.track_credits), if (people.size > 3 && !all) stringResource(R.string.notice_show_all) else null) { all = true }
        (if (all) people else people.take(3)).forEach { (p, roles) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                    .then(if (p.uri.isNotBlank()) Modifier.clickable { openArtist(p) } else Modifier),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = CardText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(roles.distinct().joinToString(" • "), color = CardSub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (p.uri.startsWith("spotify:artist:") && p.uri == mainArtist) FollowPill(p.uri)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun Tile(label: String, image: String?, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.aspectRatio(0.75f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A3A3A)).clickable { Haptics.click(); onClick() }) {
        GlideImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
        Text(label, color = CardText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
    }
}

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
                    // The artist's own channel (or its VEVO / Topic one), or a title that starts with the artist's name:
                    // a plain "contains" took "Finesse" by Bruno Mars for the artist Finess.
                    .filter { h ->
                        val up = h.uploader.lowercase().replace("vevo", "").replace(" - topic", "").replace("official", "").filter { it.isLetterOrDigit() }
                        val k = key.filter { it.isLetterOrDigit() }
                        up == k || h.name.lowercase().startsWith("$key -") || h.name.lowercase().startsWith("$key –")
                    }
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
                // Played here, in the app's own video player (not handed to YouTube).
                InAppVideo.open(v.url, v.name, v.uploader)
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
