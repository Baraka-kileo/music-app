/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0-or-later,
 * sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/HomeScreen.kt at edc1e6f.
 * The sheet's rows are those of Myspoti's Classic Home profile sheet. Modified for Myspoti, 2026-10.
 */
package com.music.spotui.ui.immersive.screens

import com.music.spotui.ui.immersive.sheets.ImmersiveGlassSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.music.spotui.ui.design.AppIcons
import com.music.spotui.ui.design.Ds
import com.music.spotui.ui.design.dsCard
import com.music.spotui.ui.design.dsGlass
import com.music.spotui.ui.immersive.pages.ImmersiveGroupRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myspoti.engine.R
import com.music.spotui.data.api.ProfileCache
import com.music.spotui.data.api.SupportReminder
import com.music.spotui.di.Graph
import com.music.spotui.di.SessionActions
import com.music.spotui.ui.components.AboutSheet
import com.music.spotui.ui.components.SupporterBadge
import com.music.spotui.ui.components.SupporterSheet
import com.music.spotui.ui.components.SupportSheet
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.immersive.components.ImmersiveCover
import com.music.spotui.ui.immersive.components.immersiveClickable
import com.music.spotui.ui.navigation.Routes
import com.music.spotui.ui.navigation.profileRoute
import com.music.spotui.ui.screens.AccountBrowser

/**
 * The profile picture as a disc (the first letter of the name until it loads), opening [onClick];
 * null when the caller takes the tap on a wider area around it.
 */
@Composable
internal fun ProfileDisc(onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val avatar = ProfileCache.imageUrl
    val initial = ProfileCache.name?.trim()?.firstOrNull()?.uppercase() ?: "•"
    AvatarDisc(
        url = avatar,
        initial = initial,
        contentDescription = stringResource(R.string.recently_played_user_profile),
        onClick = onClick,
        modifier = modifier,
    )
}

/** A round avatar: the picture at [url], or [initial] on the accent while there is none. */
@Composable
internal fun AvatarDisc(
    url: String?,
    initial: String,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .let { if (onClick != null) it.immersiveClickable(onClick = onClick) else it }
            .clip(CircleShape)
            .background(Immersive.Accent),
    ) {
        if (!url.isNullOrBlank()) {
            ImmersiveCover(
                url = url,
                modifier = Modifier.size(size),
                shape = CircleShape,
                contentDescription = contentDescription,
                border = false,
            )
        } else {
            Text(text = initial, style = Immersive.BodyStrong.copy(color = Immersive.Page, fontWeight = FontWeight.Bold))
        }
    }
}

/**
 * The profile sheet behind the avatar: account name and picture (open the public profile),
 * settings, recap, history, the account page, support, about and sign-out. The same rows and
 * actions as the Classic Home screen's sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImmersiveProfileSheet(onOpen: (route: String) -> Unit, onDismiss: () -> Unit) {
    val name = ProfileCache.name.orEmpty()
    val avatar = ProfileCache.imageUrl
    val myId = ProfileCache.id
    // Opened in full: half-open, the sheet and its list both moved under the finger.
    // The sheets some rows open.
    var support by remember { mutableStateOf(false) }
    if (support) SupportSheet(onDismiss = { support = false })
    var about by remember { mutableStateOf(false) }
    if (about) AboutSheet(onDismiss = { about = false })
    var perks by remember { mutableStateOf(false) }
    if (perks) SupporterSheet(onDismiss = { perks = false })
    // The other Spotify accounts kept on the phone: switch, add, remove.
    var accounts by remember { mutableStateOf(false) }
    if (accounts) com.music.spotui.ui.components.AccountsSheet(onDismiss = { accounts = false }, onLeave = { accounts = false; onDismiss() })
    ImmersiveGlassSheet(onDismiss, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Ds.SpaceM),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Immersive.Gutter)
                .padding(top = 6.dp, bottom = 20.dp),
        ) {
            // Who is signed in: the picture in a ring of the accent, the name; opens the public profile.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Ds.Card)
                    .clickable(enabled = myId != null, role = Role.Button) { myId?.let { onOpen(profileRoute(it, name)) } }
                    .padding(vertical = 6.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp).background(Ds.Accent, CircleShape).padding(2.5.dp)
                        .background(Color(0xFF17131C), CircleShape).padding(2.5.dp),
                ) {
                    AvatarDisc(url = avatar, initial = name.trim().firstOrNull()?.uppercase() ?: "•", contentDescription = null, onClick = null, size = 54.dp)
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name.ifBlank { stringResource(R.string.immersive_screens_account) },
                            style = Immersive.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (SupportReminder.level != SupportReminder.Level.NONE) SupporterBadge(Modifier.padding(start = 10.dp))
                    }
                    if (myId != null) Text(stringResource(R.string.view_profile), style = Immersive.Subhead)
                }
                if (myId != null) Icon(AppIcons.Chevron, contentDescription = null, tint = Ds.Text3, modifier = Modifier.padding(end = 6.dp).size(14.dp))
            }
            // What is opened most, one tap away.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(AppIcons.Settings, stringResource(R.string.settings_title), Modifier.weight(1f)) { onOpen(Routes.Settings.route) }
                QuickTile(AppIcons.Stats, stringResource(R.string.recap_title), Modifier.weight(1f)) { onOpen(Routes.Recap.route) }
                QuickTile(AppIcons.Extensions, stringResource(R.string.extensions_title), Modifier.weight(1f)) { onOpen(Routes.Extensions.route) }
            }
            SheetCard(
                { ImmersiveGroupRow(stringResource(R.string.recents_title), icon = AppIcons.History, chevron = true, onClick = { onOpen(Routes.Recent.route) }) },
                // The account page (plan, privacy, devices), in the app's signed-in browser.
                {
                    ImmersiveGroupRow(stringResource(R.string.immersive_screens_account_page), icon = AppIcons.AccountPage, chevron = true, onClick = {
                        onDismiss()
                        AccountBrowser.open(Graph.app, AccountBrowser.ACCOUNT_URL)
                    })
                },
                {
                    ImmersiveGroupRow(
                        stringResource(R.string.accounts_switch_row), detail = com.music.spotui.ui.components.switchAccountDetail(),
                        icon = AppIcons.SwitchAccount, chevron = true, onClick = { accounts = true },
                    )
                },
            )
            SheetCard(
                // Donations: always here, never in the way.
                { ImmersiveGroupRow(stringResource(R.string.support_title), icon = AppIcons.Support, chevron = true, onClick = { support = true }) },
                (@Composable { ImmersiveGroupRow(stringResource(R.string.supporter_perks_title), icon = AppIcons.Perks, chevron = true, onClick = { perks = true }) })
                    .takeIf { SupportReminder.PERKS_ENABLED },
                // About: version, Discord, source code and Ko-fi.
                { ImmersiveGroupRow(stringResource(R.string.creator_biography_section_title), icon = AppIcons.About, chevron = true, onClick = { about = true }) },
            )
            SheetCard(
                {
                    ImmersiveGroupRow(stringResource(R.string.connect_device_menu_logout), icon = AppIcons.LogOut, destructive = true, onClick = {
                        com.music.spotui.di.Haptics.confirm()
                        onDismiss()
                        SessionActions.logout(Graph.app)
                    })
                },
            )
        }
    }
}

/** One of the three shortcuts under the name: an icon over a word, on glass. */
@Composable
private fun QuickTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.immersiveClickable(onClick = { com.music.spotui.di.Haptics.click(); onClick() }).dsGlass(Ds.Card, fill = Ds.Glass2).padding(horizontal = 6.dp, vertical = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Ds.Text, modifier = Modifier.size(26.dp))
        Text(
            label, style = Immersive.Caption.copy(color = Ds.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** A card of rows with a hairline between them; a null row is left out. */
@Composable
private fun SheetCard(vararg rows: (@Composable () -> Unit)?) {
    val shown = rows.filterNotNull()
    Column(Modifier.fillMaxWidth().dsCard()) {
        shown.forEachIndexed { i, row ->
            row()
            if (i < shown.lastIndex) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(0.5.dp).background(Immersive.Separator))
        }
    }
}
