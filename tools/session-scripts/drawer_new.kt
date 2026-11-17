/**
 * The profile drawer behind the avatar, as the official app's: it slides in from the left over the
 * page, the account at the top (picture, name, "View profile"), then one plain row per place, an
 * icon and its name. With no music account, the rows about an account are left out.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ProfileSheet(onOpen: (route: String) -> Unit = {}, onDismiss: () -> Unit) {
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
