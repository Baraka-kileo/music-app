package com.music.spotui.ui.components

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
private fun FollowPill(uri: String) {
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
            Text(stringResource(R.string.artist_subtitle), color = CardText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
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
