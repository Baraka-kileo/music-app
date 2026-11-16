package com.music.spotui.ui.immersive.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import app.myspoti.engine.R
import com.music.spotui.di.Haptics
import com.music.spotui.di.SongPlayer
import com.music.spotui.ui.immersive.Immersive
import app.myspoti.host.android.MyspotiRuntime
import kotlinx.coroutines.launch

/** The chips of the info panel; Lyrics is the existing lyrics view, shown inside the panel. */
private enum class InfoTab { LYRICS, ARTIST, CREDITS, ABOUT }

/** What the panel read for the song shown: null fields are not there (their chip is hidden). */
internal class SongInfo(
    val artist: app.myspoti.api.ArtistPage?,
    val rank: Pair<app.myspoti.api.TimeRange, Int>?,
    val credits: app.myspoti.api.Credits?,
    val about: String?,
)

/**
 * The player's info panel, opened by a swipe up (or a tap) on the hint under the controls: one
 * glass panel with chips instead of a long scroll of cards. A chip shows only when the account has
 * something for it; the panel remembers the last chip. Works without a Spotify id (a YouTube Music
 * song): only the lyrics are offered then.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun ImmersiveInfoPanel(
    state: ImmersivePlayerState,
    actions: ImmersivePlayerActions,
    modifier: Modifier = Modifier,
    inset: PaddingValues = PaddingValues(),
) {
    val tint = LocalPlayerTint.current
    val info = rememberSongInfo(state)
    val tabs = buildList {
        add(InfoTab.LYRICS)
        if (info?.artist != null) add(InfoTab.ARTIST)
        if (info?.credits != null) add(InfoTab.CREDITS)
        if (info?.about != null) add(InfoTab.ABOUT)
    }
    PanelBody(info, tabs, actions, modifier, inset)
}

@Composable
private fun rememberSongInfo(state: ImmersivePlayerState): SongInfo? = rememberSongInfo(state.trackId, state.title, state.artist)

@Composable
internal fun rememberSongInfo(trackId: String, title: String, artist: String): SongInfo? {
    val info by produceState<SongInfo?>(null, trackId, title) {
        value = null
        if (trackId.isBlank()) { value = SongInfo(null, null, null, about(trackId, title, artist)); return@produceState }
        val refs = runCatching { com.music.spotui.ui.components.ArtistChooser.artistsOf(trackId, artist) }.getOrDefault(emptyList())
        val first = refs.firstOrNull { it.uri.startsWith("spotify:artist:") }
        val page = first?.let { runCatching { MyspotiRuntime.api.catalog.artistPage(it.uri) }.getOrNull() }
        val rank = first?.let { a ->
            runCatching {
                com.music.spotui.data.api.TopStats.rankOf(a.uri, app.myspoti.api.TimeRange.entries.associateWith { r ->
                    com.music.spotui.data.api.TopStats.get(r).artists.map { it.uri }
                })
            }.getOrNull()
        }
        val credits = runCatching { MyspotiRuntime.api.extras.credits("spotify:track:$trackId") }.getOrNull()?.takeIf { it.roles.isNotEmpty() }
        value = SongInfo(page, rank, credits, about(trackId, title, artist))
    }
    return info
}

/**
 * The cards under the player, reached by scrolling down: the lyrics, the artist, the credits and
 * the song's story, one under the other. Only the cards with something to show are drawn.
 */
@Composable
internal fun ImmersiveInfoCards(
    state: ImmersivePlayerState,
    actions: ImmersivePlayerActions,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tint = LocalPlayerTint.current
    val info = rememberSongInfo(state)
    Column(modifier) {
        val shape = RoundedCornerShape(20.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(shape).background(Immersive.Accent.copy(alpha = 0.85f))
                .clickable { Haptics.tick(); onOpenLyrics() }.padding(horizontal = 18.dp, vertical = 22.dp),
        ) {
            Text(stringResource(R.string.immersive_player_lyrics), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("›", color = Color.White, fontSize = 26.sp)
        }
        Spacer(Modifier.height(16.dp))
        if (info == null) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            return@Column
        }
        if (info.artist != null) { SectionTitle(stringResource(R.string.artist_subtitle)); ArtistCard(info, actions) }
        UpNextCard(onOpenQueue)
        info.credits?.let { SectionTitle(stringResource(R.string.track_credits)); CreditsCard(it, actions) }
        info.artist?.let { ExploreCard(it, state, actions) }
        info.about?.let { a ->
            SectionTitle(stringResource(R.string.lyrics_meaning_about))
            GlassCard { Text(a, color = tint, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 12, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = LocalPlayerTint.current, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp))
}

@Composable
private fun PanelBody(
    info: SongInfo?,
    tabs: List<InfoTab>,
    actions: ImmersivePlayerActions,
    modifier: Modifier,
    inset: PaddingValues,
) {
    val tint = LocalPlayerTint.current
    var chosen by rememberSaveable { mutableStateOf(InfoTab.ARTIST.name) }
    val tab = tabs.firstOrNull { it.name == chosen } ?: if (info == null && chosen != InfoTab.LYRICS.name) null else InfoTab.LYRICS

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(start = inset.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tabs.forEach { t ->
                val on = t == tab
                Text(
                    text = stringResource(
                        when (t) {
                            InfoTab.LYRICS -> R.string.immersive_player_lyrics
                            InfoTab.ARTIST -> R.string.artist_subtitle
                            InfoTab.CREDITS -> R.string.track_credits
                            InfoTab.ABOUT -> R.string.lyrics_meaning_about
                        },
                    ),
                    color = if (on) Color.White else tint.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) Immersive.Accent else tint.copy(alpha = 0.12f))
                        .clickable { Haptics.tick(); chosen = t.name }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        Crossfade(targetState = tab, animationSpec = tween(200), modifier = Modifier.weight(1f), label = "infoTab") { t ->
            when (t) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = tint, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                InfoTab.LYRICS -> com.music.spotui.ui.immersive.lyrics.ImmersiveLyricsPanel(
                    Modifier.fillMaxSize(),
                    PaddingValues(start = inset.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), end = inset.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr), top = 8.dp, bottom = 12.dp),
                )
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(inset).padding(top = 4.dp),
                ) {
                    val i = info ?: return@Column
                    when (t) {
                        InfoTab.ARTIST -> ArtistCard(i, actions)
                        InfoTab.CREDITS -> CreditsCard(i.credits!!, actions)
                        InfoTab.ABOUT -> GlassCard { Text(i.about.orEmpty(), color = tint, fontSize = 15.sp, lineHeight = 22.sp) }
                        else -> Unit
                    }
                }
            }
        }
    }
}

/** The song's story from Genius, when the lyrics meaning is on and Genius has one. */
private suspend fun about(trackId: String, title: String, artist: String): String? {
    if (!com.music.spotui.data.preferences.GeniusPref.on || title.isBlank()) return null
    val m = com.music.spotui.data.api.GeniusMeaning.currentFor(trackId)
        ?: com.music.spotui.data.api.GeniusMeaning.cached(title, artist)
        ?: runCatching { com.music.spotui.data.api.GeniusMeaning.find(title, artist) }.getOrNull()
    return m?.about?.trim()?.takeIf { it.length > 40 }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    val tint = LocalPlayerTint.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(tint.copy(alpha = 0.08f))
            .border(0.5.dp, tint.copy(alpha = 0.14f), shape).padding(16.dp),
    ) { content() }
    Spacer(Modifier.height(12.dp))
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ArtistCard(i: SongInfo, actions: ImmersivePlayerActions) {
    val page = i.artist ?: return
    val a = page.artist
    val tint = LocalPlayerTint.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var following by remember(a.uri) { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(a.uri) {
        following = runCatching { MyspotiRuntime.api.library.contains(listOf(a.uri)).firstOrNull() }.getOrNull()
    }
    val photo = (page.headerImages.ifEmpty { a.images }).maxByOrNull { it.width ?: 0 }?.url
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(tint.copy(alpha = 0.08f)).border(0.5.dp, tint.copy(alpha = 0.14f), shape)) {
        if (photo != null) GlideImage(
            model = photo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).clickable { actions.openArtistRef(app.myspoti.api.ArtistRef(a.uri, a.name)) },
        )
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, color = tint, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    a.monthlyListeners?.let {
                        Text(
                            stringResource(R.string.monthly_listeners_text, java.text.NumberFormat.getIntegerInstance().format(it)),
                            color = tint.copy(alpha = 0.6f), fontSize = 13.sp,
                        )
                    }
                }
                following?.let { on ->
                    Text(
                        stringResource(if (on) R.string.curation_button_following_state_text else R.string.curation_button_follow_state_text),
                        color = tint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, tint.copy(alpha = if (on) 0.25f else 0.6f), RoundedCornerShape(50))
                            .clickable {
                                Haptics.confirm()
                                following = !on
                                scope.launch {
                                    runCatching { if (on) MyspotiRuntime.api.library.remove(listOf(a.uri)) else MyspotiRuntime.api.library.add(listOf(a.uri)) }
                                        .onFailure { following = on }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            i.rank?.let { (range, n) ->
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        when (range) {
                            app.myspoti.api.TimeRange.SHORT_TERM -> R.string.artist_top_short
                            app.myspoti.api.TimeRange.MEDIUM_TERM -> R.string.artist_top_medium
                            app.myspoti.api.TimeRange.LONG_TERM -> R.string.artist_top_long
                        },
                        n,
                    ),
                    color = Immersive.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            val top = page.topTracks.take(3)
            if (top.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.artist_popular_tracks), color = tint.copy(alpha = 0.6f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                val songs = remember(top) { top.map { com.music.spotui.di.Graph.api.songOf(it.track) } }
                songs.forEachIndexed { idx, s ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { Haptics.click(); SongPlayer.playSong(s.url, context, songs = songs.drop(idx)) }
                            .padding(vertical = 6.dp),
                    ) {
                        Text("${idx + 1}", color = tint.copy(alpha = 0.5f), fontSize = 14.sp, modifier = Modifier.width(22.dp))
                        GlideImage(
                            model = s.coverUri, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(s.title, color = tint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
            }
            page.biography?.let { bio ->
                val plain = androidx.core.text.HtmlCompat.fromHtml(bio, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
                if (plain.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(plain, color = tint.copy(alpha = 0.7f), fontSize = 14.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun CreditsCard(credits: app.myspoti.api.Credits, actions: ImmersivePlayerActions) {
    val tint = LocalPlayerTint.current
    GlassCard {
        credits.roles.forEachIndexed { idx, role ->
            if (idx > 0) Spacer(Modifier.height(14.dp))
            Text(role.role, color = tint.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            role.artists.forEach { p ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                        .then(if (p.uri.isNotBlank()) Modifier.clickable { actions.openArtistRef(p) } else Modifier),
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) { Text(p.name.take(1).uppercase(), color = tint, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(10.dp))
                    Text(p.name, color = tint, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** The next song, with a way into the queue. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun UpNextCard(onOpenQueue: () -> Unit) {
    val q = SongPlayer.engineQueue.value
    val next = (q.queued.firstOrNull() ?: q.nextUp.firstOrNull())?.song ?: return
    val tint = LocalPlayerTint.current
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.notice_up_next), color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.notice_open_queue), color = Immersive.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { Haptics.tick(); onOpenQueue() }.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpenQueue() }) {
            GlideImage(model = next.coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(next.title, color = tint, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(next.singer, color = tint.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** "Explore <artist>": big tiles into the artist and the album. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ExploreCard(page: app.myspoti.api.ArtistPage, state: ImmersivePlayerState, actions: ImmersivePlayerActions) {
    val tint = LocalPlayerTint.current
    val a = page.artist
    val photo = (page.headerImages.ifEmpty { a.images }).maxByOrNull { it.width ?: 0 }?.url
    GlassCard {
        Text(stringResource(R.string.notice_explore, a.name), color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                Triple(stringResource(R.string.notice_songs_by, a.name), photo) { actions.openArtistRef(app.myspoti.api.ArtistRef(a.uri, a.name)) },
                Triple(stringResource(R.string.notice_from_album), state.coverUrl) { actions.openAlbum() },
            ).forEach { (label, img, go) ->
                Box(Modifier.weight(1f).aspectRatio(0.78f).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.1f)).clickable { Haptics.click(); go() }) {
                    GlideImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))))
                    Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
                }
            }
        }
    }
}
