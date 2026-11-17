package com.music.spotui.ui.screens

import com.music.spotui.ui.theme.tone
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppAccent

import com.music.spotui.ui.components.SkeletonKind
import com.music.spotui.ui.components.pressable

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import app.myspoti.engine.R
import com.music.spotui.data.api.Response
import com.music.spotui.data.entity.HomeFeedModel
import com.music.spotui.data.entity.HomeItem
import com.music.spotui.data.entity.homeShortcuts
import com.music.spotui.data.entity.withoutDj
import com.music.spotui.data.entity.withoutPodcastShows
import com.music.spotui.data.entity.HomeSection
import com.music.spotui.ui.components.Loader
import com.music.spotui.ui.components.onHomeItemClick
import com.music.spotui.ui.components.prefetchHomeItem
import com.music.spotui.ui.components.prefetchHomeShortcuts
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.GridBackground
import com.music.spotui.ui.viewmodel.HomeViewModel


@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(navController: NavController){

    val homeViewModel : HomeViewModel = viewModel()
    // An old feed is read again when Home shows again (see HomeViewModel.refreshIfStale).
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { homeViewModel.refreshIfStale() }
    val home by homeViewModel.home.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        val feed = (home as? Response.Success)?.data
        val hasFeed = feed != null && feed.sections.isNotEmpty()
        // The feed fades in over the placeholder instead of replacing it in one frame.
        com.music.spotui.ui.components.LoadedFade(!hasFeed) { waiting ->
        when {
            // Preferred: the real personalized Spotify home feed.
            !waiting && feed != null -> {
                HomeFeedContent(navController, feed, homeViewModel.facet.value) { homeViewModel.selectFacet(it) }
            }

            // No feed to show (loading, empty chip, error): keep the chips on screen
            // so the user can always switch back to "Tout".
            else -> androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                HomeHeaderRow(navController, homeViewModel.facet.value) { homeViewModel.selectFacet(it) }
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        // Fading out as the feed fades in: still the placeholder.
                        home is Response.Loading || hasFeed -> Loader(SkeletonKind.Home)
                        else -> androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (home is Response.Error) stringResource(R.string.home_error) else stringResource(R.string.home_empty),
                                color = AppText,
                                textAlign = TextAlign.Center,
                            )
                            if (home is Response.Error) androidx.compose.material3.TextButton(onClick = { homeViewModel.retry() }) {
                                Text(stringResource(R.string.home_retry), color = AppAccent)
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun HomeFeedContent(navController: NavController, feed: HomeFeedModel, facet: String = "", onFacet: (String) -> Unit = {}) {
    // Mirror open.spotify.com exactly: sections render in the order the feed
    // returns them. The 2-column "shortcuts" grid is only used for the UNTITLED
    // section the web home starts with — if the feed leads with a titled section
    // ("Jump back in", "Made For …"), it renders as a titled carousel first,
    // not force-squeezed into the grid.
    // With podcasts off, their shows and episodes leave the feed (and a section left empty goes).
    // Kept between recompositions: a new list for every one of them would restart the rows below.
    val hideDj = com.music.spotui.data.preferences.HideDjPref.hidden
    val sections = remember(feed, hideDj) {
        val all = feed.sections.withoutPodcastShows()
        if (hideDj) all.withoutDj() else all
    }
    val gridSection = remember(sections) { sections.firstOrNull()?.takeIf { it.title.isBlank() } }
    val carousels = remember(sections, gridSection) { if (gridSection != null) sections.drop(1) else sections }
    val carouselKeys = remember(carousels) { uniqueKeys(carousels.map { it.title }) }
    // Liked Songs leads the shortcuts when Spotify's grid does not carry it.
    val likedName = stringResource(R.string.collection_liked_songs_title)
    val shortcuts = remember(gridSection, sections, facet, likedName) { homeShortcuts(gridSection?.items, sections.isEmpty(), facet, likedName) }
    // The shortcuts at the top load in the background, ready before a tap.
    androidx.compose.runtime.LaunchedEffect(shortcuts) { prefetchHomeShortcuts(shortcuts) }

    // Re-tapping the Home tab while on Home scrolls this list back to its top (official
    // behaviour): an animation over the existing items, so nothing is fetched again.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.spotui.ui.navigation.TabReselect.home.collect {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = com.music.spotui.ui.components.bottomBarsSpace()),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(AppBackground.toArgb()))
    ) {
        // Sticky filter row: content scrolls underneath it, like the official app.
        stickyHeader {
            // Fade (not a solid band): opaque under the status bar, transparent at the bottom.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(AppBackground.toArgb()),
                            0.55f to Color(AppBackground.toArgb()).copy(alpha = 0.85f),
                            1f to Color.Transparent,
                        )
                    )
                    .statusBarsPadding()
                    .padding(bottom = 10.dp)
            ) {
                HomeHeaderRow(navController, facet, onFacet)
            }
        }
        shortcuts?.let { tiles ->
            item {
                HomeShortcutGrid(navController, tiles)
            }
        }
        items(carousels.size, key = { carouselKeys[it] }) { i ->
            HomeFeedSection(navController, carousels[i])
        }
        // Sections from extensions (home.sections), after Spotify's own, each under its extension's name.
        item { com.music.spotui.extensions.ExtensionBlocks(app.myspoti.extensions.Hook.HOME, app.myspoti.extensions.Surface.HOME, "{}") }
    }
}

/** Spotify-style top row: profile avatar on the left (opens Settings, like the
 *  official app's profile drawer), then the filter pills — one single row. */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeHeaderRow(navController: NavController, facet: String, onFacet: (String) -> Unit) {
    var showProfile by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showProfile) ProfileSheet(onOpen = { route -> showProfile = false; navController.navigate(route) }) { showProfile = false }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.music.spotui.data.api.ProfileCache.ensure(context)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The row's 10 dp above and below sit in each child instead, so the avatar's tap area
        // takes them in (and the margin up to the separator) without moving anything.
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Same avatar as everywhere else: picture, else the name's letter, else a person icon.
        Box(
            modifier = Modifier
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { showProfile = true }
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        ) {
            com.music.spotui.ui.components.ProfileAvatar(
                url = com.music.spotui.data.api.ProfileCache.imageUrl,
                name = com.music.spotui.data.api.ProfileCache.name,
                size = 34.dp,
            )
        }
        WhatsNewBell { navController.navigate(com.music.spotui.ui.navigation.Routes.WhatsNew.route) }
        // B31: a separator between the profile and the options, reported missing from this
        // header (the sheets already carry one).
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(width = 1.dp, height = 22.dp)
                .background(tone(0xFF3E3E3E)),
        )
        // Scrolls sideways if the pills don't fit, like Spotify's.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            // Spotify's home chips. Once one is picked, as in Spotify: a close chip, the picked one,
            // and joined to it the chips it opens (Following: only what the user follows).
            val labels = mapOf(
                "music-chip" to stringResource(R.string.recents_content_filter_music),
                "podcasts-chip" to stringResource(R.string.ad_category_title_podcasts),
                "audiobooks-chip" to stringResource(R.string.audiobooks_title),
            )
            if (facet.isEmpty()) {
                HomePill(stringResource(R.string.filter_chip_all_label), selected = true) {}
                HomePill(labels.getValue("music-chip")) { onFacet("music-chip") }
                if (com.music.spotui.data.api.Features.PODCASTS) {
                    HomePill(labels.getValue("podcasts-chip")) { onFacet("podcasts-chip") }
                    HomePill(labels.getValue("audiobooks-chip")) { onFacet("audiobooks-chip") }
                }
            } else {
                val parent = com.music.spotui.data.entity.HomeSubChips.parentOf(facet) ?: facet
                val subs = com.music.spotui.data.entity.HomeSubChips.byParent[parent].orEmpty()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clip(CircleShape).background(tone(0xFF2A2A2A)).clickable { onFacet("") }.padding(8.dp),
                ) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = stringResource(R.string.filter_chip_all_label),
                        tint = AppText, modifier = Modifier.size(18.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    HomePill(labels[parent].orEmpty(), selected = true, shape = if (subs.isEmpty()) RoundedCornerShape(50) else JoinedStart) { onFacet(parent) }
                    subs.forEachIndexed { i, (id, label) ->
                        val shown = remember(id) { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = shown,
                            enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.Start) + androidx.compose.animation.fadeIn(),
                        ) {
                            HomePill(label, selected = facet == id, shape = if (i == subs.lastIndex) JoinedEnd else RoundedCornerShape(4.dp)) { onFacet(id) }
                        }
                    }
                }
            }
        }
        // Friend activity, out of the tab bar: at the end of the header, as Spotify has it.
        IconButton(onClick = { navController.navigate(com.music.spotui.ui.navigation.Routes.Friends.route) }) {
            Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_friends_big), contentDescription = stringResource(R.string.friends_title),
                tint = AppText, modifier = Modifier.size(24.dp))
        }
    }
}

/** The left half of two pills joined (a picked chip and the one it opened), and the right one. */
private val JoinedStart = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 15, bottomEndPercent = 15)
private val JoinedEnd = RoundedCornerShape(topStartPercent = 15, bottomStartPercent = 15, topEndPercent = 50, bottomEndPercent = 50)

@Composable
private fun HomePill(label: String, selected: Boolean = false, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(50), onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) com.music.spotui.ui.theme.AppAccent else tone(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = if (selected) Color.Black else AppText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeShortcutGrid(navController: NavController, items: List<HomeItem>) {
    // Large windows: more tiles per row (two on phones).
    val cols = com.music.spotui.ui.components.windowSize().columns(2, 280.dp)
    Column(modifier = Modifier.padding(8.dp, 4.dp)) {
        items.chunked(cols).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp),
            ) {
                rowItems.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.pressable(0.96f, onPressHint = { prefetchHomeItem(item) }) { onHomeItemClick(navController, item) }
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(GridBackground.toArgb()))
                            ,
                    ) {
                        GlideImage(
                            modifier = Modifier.size(48.dp),
                            contentScale = ContentScale.Crop,
                            model = item.imageUrl,
                            loading = placeholder(R.drawable.placeholder),
                            failure = placeholder(R.drawable.placeholder),
                            contentDescription = "",
                        )
                        Text(
                            text = item.name,
                            color = AppText,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            // Two balanced lines ("Daily / Mix 2") rather than a lone "2" under the rest.
                            style = androidx.compose.ui.text.TextStyle(lineBreak = androidx.compose.ui.text.style.LineBreak.Heading),
                            modifier = Modifier.padding(8.dp, 4.dp),
                        )
                    }
                }
                repeat(cols - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/** A key per entry that stays with it while the others around it change: [raw], with a counter on repeats. */
private fun uniqueKeys(raw: List<String>): List<String> {
    val seen = HashMap<String, Int>()
    return raw.map { k ->
        val n = (seen[k] ?: 0) + 1
        seen[k] = n
        if (n == 1) k else "$k#$n"
    }
}

@Composable
private fun HomeFeedSection(navController: NavController, section: HomeSection) {
    Text(
        text = section.title,
        color = AppText,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier.padding(16.dp, 20.dp, 16.dp, 4.dp),
    )
    val keys = remember(section) { uniqueKeys(section.items.map { it.name + "|" + it.imageUrl }) }
    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp, 0.dp)) {
        items(section.items.size, key = { keys[it] }) { i ->
            HomeFeedCard(section.items[i]) { onHomeItemClick(navController, section.items[i]) }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun HomeFeedCard(item: HomeItem, onClick: () -> Unit) {
    val isArtist = item is HomeItem.Artist
    val subtitle = when (item) {
        is HomeItem.Album -> item.subtitle
        is HomeItem.Playlist -> item.subtitle
        is HomeItem.Artist -> stringResource(R.string.artist_subtitle)
        is HomeItem.Show -> item.subtitle
    }
    Column(
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier.pressable(0.96f, onPressHint = { prefetchHomeItem(item) }) { onClick() }
            .width(150.dp)
            .padding(6.dp)
            ,
    ) {
        Box {
            GlideImage(
                modifier = Modifier
                    .size(150.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                model = item.imageUrl,
                loading = placeholder(R.drawable.placeholder),
                failure = placeholder(R.drawable.placeholder),
                contentDescription = "",
            )
            if (item is HomeItem.Show && !item.isEpisode) {
                com.music.spotui.ui.components.NewEpisodeDot(item.id, AppAccent, Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            color = AppText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
        // Always two lines tall: a long artist mix no longer makes one card taller than its row.
        Text(
            text = subtitle,
            color = AppMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            minLines = 2,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = if (isArtist) TextAlign.Center else TextAlign.Start,
        )
    }
}

/** The bell beside the avatar: opens "What's New". */
@Composable
private fun WhatsNewBell(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(end = 10.dp, top = 10.dp, bottom = 10.dp)
            .size(34.dp),
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Outlined.Notifications,
            contentDescription = stringResource(R.string.whats_new_title),
            tint = AppText,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The profile drawer behind the avatar, as the official app's: it slides in from the left over the
 * page, the account at the top (picture, name, "View profile"), then one plain row per place, an
 * icon and its name. With no music account, the rows about an account are left out.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun ProfileSheet(onOpen: (route: String) -> Unit = {}, onDismiss: () -> Unit) {
    val name = com.music.spotui.data.api.ProfileCache.name.orEmpty()
    val avatar = com.music.spotui.data.api.ProfileCache.imageUrl
    val hasAccount = com.music.spotui.data.preferences.AccountProviderPref.id != app.myspoti.api.account.AccountProviders.NONE
    // In, then out before the drawer leaves the composition.
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true } }
    var leaving by remember { androidx.compose.runtime.mutableStateOf<(() -> Unit)?>(null) }
    fun close(then: () -> Unit = {}) { leaving = then; shown.targetState = false }
    androidx.compose.runtime.LaunchedEffect(shown.currentState, shown.targetState) {
        if (!shown.targetState && !shown.currentState) { onDismiss(); leaving?.invoke() }
    }
    var support by remember { androidx.compose.runtime.mutableStateOf(false) }
    var about by remember { androidx.compose.runtime.mutableStateOf(false) }
    var perks by remember { androidx.compose.runtime.mutableStateOf(false) }
    var accounts by remember { androidx.compose.runtime.mutableStateOf(false) }
    var askLogout by remember { androidx.compose.runtime.mutableStateOf(false) }
    if (support) com.music.spotui.ui.components.SupportSheet(onDismiss = { support = false })
    if (about) com.music.spotui.ui.components.AboutSheet(onDismiss = { about = false })
    if (perks) com.music.spotui.ui.components.SupporterSheet(onDismiss = { perks = false })
    if (accounts) com.music.spotui.ui.components.AccountsSheet(onDismiss = { accounts = false }, onLeave = { accounts = false; close() })
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { close() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            androidx.compose.animation.AnimatedVisibility(shown, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close() },
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                shown,
                enter = androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(260)) { -it },
                exit = androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(200)) { -it },
            ) {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxHeight().fillMaxWidth(0.86f).background(tone(0xFF1F1F1F))
                        .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    // Name and picture open your public profile, as in Spotify.
                    val myId = com.music.spotui.data.api.ProfileCache.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable(enabled = myId != null) { myId?.let { id -> close { onOpen(com.music.spotui.ui.navigation.profileRoute(id, name)) } } }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        com.music.spotui.ui.components.ProfileAvatar(url = avatar, name = name, size = 44.dp)
                        androidx.compose.foundation.layout.Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    name.ifBlank { stringResource(if (hasAccount) R.string.spotify_account else R.string.app_name) },
                                    color = AppText, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                                )
                                if (com.music.spotui.data.api.SupportReminder.level != com.music.spotui.data.api.SupportReminder.Level.NONE)
                                    com.music.spotui.ui.components.SupporterBadge(Modifier.padding(start = 8.dp))
                            }
                            if (myId != null) Text(stringResource(R.string.view_profile), color = AppMuted, fontSize = 13.sp)
                        }
                    }
                    androidx.compose.material3.HorizontalDivider(color = tone(0xFF333333))
                    Spacer(Modifier.height(8.dp))
                    if (hasAccount) DrawerItem(Icons.Outlined.PersonAdd, stringResource(R.string.accounts_switch_row)) { accounts = true }
                    DrawerItem(Icons.Outlined.Insights, stringResource(R.string.recap_title)) { close { onOpen(com.music.spotui.ui.navigation.Routes.Recap.route) } }
                    DrawerItem(Icons.Outlined.History, stringResource(R.string.recents_title)) { close { onOpen(com.music.spotui.ui.navigation.Routes.Recent.route) } }
                    DrawerItem(Icons.Outlined.Extension, stringResource(R.string.extensions_title)) { close { onOpen(com.music.spotui.ui.navigation.Routes.Extensions.route) } }
                    DrawerItem(Icons.Outlined.Settings, stringResource(R.string.settings_title)) { close { onOpen(com.music.spotui.ui.navigation.Routes.Settings.route) } }
                    if (hasAccount) DrawerItem(Icons.Outlined.ManageAccounts, stringResource(R.string.account_page_title)) {
                        close { com.music.spotui.ui.screens.AccountBrowser.open(com.music.spotui.di.Graph.app, com.music.spotui.ui.screens.AccountBrowser.ACCOUNT_URL) }
                    }
                    DrawerItem(Icons.Outlined.Info, stringResource(R.string.creator_biography_section_title)) { about = true }
                    if (com.music.spotui.data.api.SupportReminder.PERKS_ENABLED) DrawerItem(Icons.Outlined.StarOutline, stringResource(R.string.supporter_perks_title)) { perks = true }
                    DrawerItem(Icons.Outlined.FavoriteBorder, stringResource(R.string.support_title), tint = com.music.spotui.ui.theme.AppAccent) { support = true }
                    if (hasAccount) DrawerItem(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.connect_device_menu_logout)) { askLogout = true }
                }
            }
        }
    }
    if (askLogout) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askLogout = false },
        title = { Text(stringResource(R.string.connect_device_menu_logout), color = AppText) },
        text = { Text(stringResource(R.string.settings_logout_confirm), color = AppMuted) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                askLogout = false
                close { com.music.spotui.di.SessionActions.logout(com.music.spotui.di.Graph.app) }
            }) { Text(stringResource(R.string.connect_device_menu_logout), color = com.music.spotui.ui.theme.AppAccent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { askLogout = false }) { Text(stringResource(android.R.string.cancel), color = AppText) }
        },
        containerColor = tone(0xFF282828),
    )
}

/** A row of the profile drawer: an icon and its name. */
@Composable
private fun DrawerItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color = AppText,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { com.music.spotui.di.Haptics.tick(); onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
    }
}


