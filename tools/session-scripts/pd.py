import sys, os
os.chdir('O:/base/app/src/main/java/com/music/spotui')

def edit(p, reps, append=None):
    s = open(p, 'rb').read().decode('utf-8'); nl = '\r\n' if '\r\n' in s else '\n'
    for a, b in reps:
        a, b = a.replace('\n', nl), b.replace('\n', nl)
        n = s.count(a)
        if n != 1: sys.exit(f"{p}: {n} for {a[:90]!r}")
        s = s.replace(a, b)
    if append: s += append.replace('\n', nl)
    open(p, 'wb').write(s.encode('utf-8'))

# ---------- Player: video switch over a Canvas sits above the title; the live lyric line above it ----------
P = 'ui/immersive/player/ImmersivePlayer.kt'
edit(P, [
("""                                IntOffset((artStart() + inset).roundToPx(), (artTop() + artSize() - inset - 32.dp + 18.dp).roundToPx())""",
"""                                // Over a Canvas (no cover) it sits just above the title, at its start.
                                if (canvasOn) IntOffset(left.roundToPx(), (titleTop() - 46.dp).roundToPx())
                                else IntOffset((artStart() + inset).roundToPx(), (artTop() + artSize() - inset - 32.dp + 18.dp).roundToPx())"""),
("""                    // ---- Title + round glyphs ----""",
"""                    // ---- The line being sung, over a Canvas, above the title (a setting) ----
                    if (canvasOn && panel == Panel.NONE && com.music.spotui.data.preferences.LyricLinePref.on) Box(
                        Modifier
                            .offset {
                                val pill = if (videoReady || videoOn) 46.dp else 0.dp
                                IntOffset(left.roundToPx(), (titleTop() - pill - 8.dp - 64.dp).roundToPx())
                            }
                            .width(contentWidth).height(64.dp)
                            .graphicsLayer { alpha = 1f - p() },
                        contentAlignment = Alignment.BottomStart,
                    ) { LiveLyricLine(onOpen = { panel = Panel.LYRICS }) }

                    // ---- Title + round glyphs ----"""),
("""            ImmersiveInfoCards(
                state, actions, onOpenLyrics = { panel = Panel.LYRICS },""",
"""            ImmersiveInfoCards(
                state, actions, onOpenLyrics = { panel = Panel.LYRICS }, onOpenQueue = { panel = Panel.QUEUE },"""),
], append="""
/** The lyric line being sung now, large and bold, fading from line to line. Tap: the lyrics. */
@Composable
private fun LiveLyricLine(onOpen: () -> Unit) {
    val vm: com.music.spotui.ui.viewmodel.LyricsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val song = com.music.spotui.di.Graph.currentSongState
    val st = com.music.spotui.ui.screens.rememberLyricsState(vm, song.title.value, song.singer.value)
    val lyrics = (st as? com.music.spotui.ui.viewmodel.LyricsViewModel.State.Loaded)?.lyrics
    if (lyrics == null || !lyrics.synced) return
    val line by androidx.compose.runtime.produceState("", lyrics) {
        while (true) {
            val pos = com.music.spotui.di.SongPlayer.getCurrentPosition()
            value = lyrics.lines.lastOrNull { it.timeMs in 1..pos + 150 }?.text?.trim().orEmpty()
            kotlinx.coroutines.delay(200)
        }
    }
    androidx.compose.animation.Crossfade(targetState = line, animationSpec = tween(220), label = "liveLine") { t ->
        Text(
            t, color = Color.White, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen() },
        )
    }
}
""")

# ---------- Setting ----------
edit('data/preferences/SettingsPref.kt', [("""/** "Big video in the mini player\"""", """/** "Lyric line on the player": over a Canvas, the line being sung shows above the title. */
object LyricLinePref {
    private const val KEY = "player_lyric_line"
    var on by androidx.compose.runtime.mutableStateOf(prefs(com.music.spotui.di.Graph.app).getBoolean(KEY, true))
        private set

    fun set(c: Context, value: Boolean) {
        on = value
        prefs(c).edit().putBoolean(KEY, value).apply()
    }
}

/** "Big video in the mini player\"""")])
edit('ui/immersive/pages/ImmersiveSettingsScreen.kt', [("""                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_mini_video_title),""", """                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_lyric_line_title), checked = com.music.spotui.data.preferences.LyricLinePref.on,
                        onChecked = { com.music.spotui.data.preferences.LyricLinePref.set(ctx, it) }, detail = stringResource(R.string.settings_lyric_line_detail))
                }
                row {
                    ImmersiveSwitchRow(stringResource(R.string.settings_mini_video_title),""")])

# ---------- Cards: Up next and Explore ----------
I = 'ui/immersive/player/ImmersiveInfoPanel.kt'
edit(I, [
("""    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = LocalPlayerTint.current
    val info = rememberSongInfo(state)""", """    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tint = LocalPlayerTint.current
    val info = rememberSongInfo(state)"""),
("""        if (info.artist != null) { SectionTitle(stringResource(R.string.artist_subtitle)); ArtistCard(info, actions) }
        info.credits?.let { SectionTitle(stringResource(R.string.track_credits)); CreditsCard(it, actions) }""",
"""        if (info.artist != null) { SectionTitle(stringResource(R.string.artist_subtitle)); ArtistCard(info, actions) }
        UpNextCard(onOpenQueue)
        info.credits?.let { SectionTitle(stringResource(R.string.track_credits)); CreditsCard(it, actions) }
        info.artist?.let { ExploreCard(it, state, actions) }"""),
], append="""
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
""")

# ---------- Queue: swipe right = play next; a sleep timer beside shuffle and repeat ----------
Q = 'ui/immersive/lyrics/ImmersiveQueuePanel.kt'
edit(Q, [
("""        if (value == SwipeToDismissBoxValue.EndToStart) {
            removed += row.key
            SongPlayer.removeFromQueue(item)
            true
        } else {
            false
        }""", """        if (value == SwipeToDismissBoxValue.EndToStart) {
            removed += row.key
            SongPlayer.removeFromQueue(item)
            true
        } else if (value == SwipeToDismissBoxValue.StartToEnd) {
            // Swiped right: this song plays next (the row springs back).
            SongPlayer.playNext(item.uri.substringAfterLast(':'))
            com.music.spotui.ui.components.AppNotice.show(
                title = com.music.spotui.di.Graph.app.getString(R.string.notice_plays_next),
                subtitle = item.song.title, coverUrl = item.song.coverUri, tone = com.music.spotui.ui.components.AppNotice.Tone.SUCCESS,
            )
            false
        } else {
            false
        }"""),
("""        enableDismissFromStartToEnd = false,""", """        enableDismissFromStartToEnd = true,"""),
("""            if (dismiss.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    Modifier.fillMaxSize().padding(end = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        com.music.spotui.ui.design.AppIcons.Close,
                        contentDescription = stringResource(R.string.immersive_queue_remove),
                        tint = Immersive.TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }""", """            if (dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    Modifier.fillMaxSize().padding(end = 4.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        com.music.spotui.ui.design.AppIcons.Close,
                        contentDescription = stringResource(R.string.immersive_queue_remove),
                        tint = Immersive.TextSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else if (dismiss.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Immersive.Accent.copy(alpha = 0.35f)).padding(start = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(stringResource(R.string.notice_plays_next), color = Color.White, style = Immersive.BodyStrong)
                }
            }"""),
("""            stringResource(R.string.immersive_queue_repeat),
            repeatMode != 0,
            onRepeat,
        )
    }
}""", """            stringResource(R.string.immersive_queue_repeat),
            repeatMode != 0,
            onRepeat,
        )
        Spacer(Modifier.width(8.dp))
        SleepToggle()
    }
}

/**
 * The sleep timer, beside shuffle and repeat: each tap steps off, 15, 30, 60 min, end of song;
 * while it runs the minutes left count down inside the button.
 */
@Composable
private fun SleepToggle() {
    val endsAt = SongPlayer.sleepEndsAt.value
    val steps = listOf<Int?>(15, 30, 60, 0, null)
    var step by remember { mutableStateOf(-1) }
    val left by androidx.compose.runtime.produceState(0L, endsAt) {
        while (endsAt != null && endsAt > 0) { value = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0); delay(1000) }
    }
    val on = endsAt != null
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(36.dp).widthIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                com.music.spotui.di.Haptics.tick()
                step = (step + 1) % steps.size
                SongPlayer.setSleepTimer(steps[step])
            }
            .padding(horizontal = if (on) 10.dp else 0.dp),
    ) {
        if (!on) Icon(androidx.compose.material.icons.Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.notice_sleep_timer), tint = Color.White, modifier = Modifier.size(20.dp))
        else Text(
            if (endsAt!! < 0) stringResource(R.string.notice_end_of_song) else "${(left / 60000) + 1}m",
            color = Color.Black, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}"""),
])

open('O:/base/app/src/main/res/values/notice_strings.xml', 'wb').write('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="notice_change">Change</string>
    <string name="player_info_hint">Lyrics \u00b7 Artist \u00b7 Credits</string>
    <string name="settings_mini_video_title">Big video in the mini player</string>
    <string name="settings_mini_video_detail">With a music video on, show it large above the mini player instead of small inside it</string>
    <string name="settings_lyric_line_title">Lyric line on the player</string>
    <string name="settings_lyric_line_detail">Over a Canvas, the line being sung shows above the song title. Tap it for the full lyrics</string>
    <string name="notice_up_next">Up next</string>
    <string name="notice_open_queue">Open queue</string>
    <string name="notice_explore">Explore %1$s</string>
    <string name="notice_songs_by">Songs by %1$s</string>
    <string name="notice_from_album">From the album</string>
    <string name="notice_plays_next">Plays next</string>
    <string name="notice_sleep_timer">Sleep timer</string>
    <string name="notice_end_of_song">End of song</string>
</resources>
'''.encode('utf-8'))
print('ok')
