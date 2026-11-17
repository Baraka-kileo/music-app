import io, sys, os
os.chdir('O:/base')

def edit(p, reps):
    s = io.open(p, newline='').read(); nl = '\r\n' if '\r\n' in s else '\n'
    for r in reps:
        a, b = r[0].replace('\n', nl), r[1].replace('\n', nl); c = r[2] if len(r) > 2 else 1
        n = s.count(a)
        if n != c: sys.exit(f"{p}: {n} != {c} for {a[:70]!r}")
        s = s.replace(a, b)
    io.open(p, 'w', newline='').write(s)

P = 'app/src/main/java/com/music/spotui/ui/immersive/player/ImmersivePlayer.kt'
edit(P, [
("""    const val QUEUE = 2
}""", """    const val QUEUE = 2
    /** The info panel: lyrics, artist, credits, about, as chips. */
    const val INFO = 3
}"""),
("""                            Panel.QUEUE -> ImmersiveQueuePanel(Modifier.fillMaxSize(), inset)
                            else -> Box(Modifier.fillMaxSize())""", """                            Panel.QUEUE -> ImmersiveQueuePanel(Modifier.fillMaxSize(), inset)
                            Panel.INFO -> ImmersiveInfoPanel(state, actions, Modifier.fillMaxSize(), inset)
                            else -> Box(Modifier.fillMaxSize())"""),
("""                    if ((videoReady || videoOn) && panel == Panel.NONE) Box(""", """                    if (false && (videoReady || videoOn) && panel == Panel.NONE) Box("""),
("""                            onSourceLong = { showNerd = true },
                        )
                    }
                }""", """                            onSourceLong = { showNerd = true },
                            video = if ((videoReady || videoOn) && panel == Panel.NONE) videoOn else null,
                            onSaveTo = { state.song?.let { com.music.spotui.ui.components.AppNotice.openSaveTo(it) } },
                        )
                    }
                }"""),
("""                    if (!showVolume) Spacer(Modifier.height(12.dp - 4.dp + VOLUME_ROW_HEIGHT + 6.dp))
                }""", """                    if (!showVolume) Spacer(Modifier.height(12.dp - 4.dp + VOLUME_ROW_HEIGHT + 6.dp))
                    InfoHint(open = panel == Panel.INFO) { panel = if (panel == Panel.INFO) Panel.NONE else Panel.INFO }
                }"""),
("""    onSource: () -> Unit,
    onSourceLong: () -> Unit = {},
) {
    // The header is a fixed HEADER_HEIGHT""", """    onSource: () -> Unit,
    onSourceLong: () -> Unit = {},
    video: Boolean? = null,
    onSaveTo: () -> Unit = {},
) {
    // The header is a fixed HEADER_HEIGHT"""),
("""    Spacer(Modifier.width(10.dp))
    // Beside the credits rather than in the toggle row: liking is about this song.
    CircleGlyph(
        icon = if (state.liked) com.music.spotui.ui.design.AppIcons.Star else com.music.spotui.ui.design.AppIcons.StarBorder,
        contentDescription = stringResource(if (state.liked) R.string.immersive_player_unlike else R.string.immersive_player_like),
        onClick = actions.toggleLike,
        active = state.liked,
    )""", """    Spacer(Modifier.width(10.dp))
    // The music video, when one is ready: beside the like, not floating over the cover.
    if (video != null) {
        CircleGlyph(
            icon = if (video) androidx.compose.material.icons.Icons.Default.MusicNote else androidx.compose.material.icons.Icons.Default.OndemandVideo,
            contentDescription = stringResource(if (video) R.string.av_switch_to_audio else R.string.av_switch_to_video),
            onClick = { com.music.spotui.video.MusicVideoController.setOn(!video) },
            active = video,
        )
        Spacer(Modifier.width(8.dp))
    }
    // Beside the credits rather than in the toggle row: liking is about this song. Held: Save to.
    CircleGlyph(
        icon = if (state.liked) com.music.spotui.ui.design.AppIcons.Star else com.music.spotui.ui.design.AppIcons.StarBorder,
        contentDescription = stringResource(if (state.liked) R.string.immersive_player_unlike else R.string.immersive_player_like),
        onClick = actions.toggleLike,
        active = state.liked,
        onLongClick = onSaveTo,
    )"""),
])
s = io.open(P, newline='').read(); nl = '\r\n' if '\r\n' in s else '\n'
hint = '''
/**
 * The hint under the controls: "Lyrics · Artist · Credits" and a chevron. A tap or a swipe up opens
 * the info panel; open, it closes it.
 */
@Composable
private fun InfoHint(open: Boolean, onToggle: () -> Unit) {
    val tint = LocalPlayerTint.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().height(26.dp)
            .pointerInput(open) {
                var dy = 0f
                androidx.compose.foundation.gestures.detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = { if ((!open && dy < -20f) || (open && dy > 20f)) onToggle() },
                    onVerticalDrag = { c, d -> c.consume(); dy += d },
                )
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Haptics.tick(); onToggle() },
    ) {
        Icon(
            imageVector = if (open) androidx.compose.material.icons.Icons.Default.KeyboardArrowDown else androidx.compose.material.icons.Icons.Default.KeyboardArrowUp,
            contentDescription = null, tint = tint.copy(alpha = 0.6f), modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.player_info_hint), style = Immersive.Caption, color = tint.copy(alpha = 0.6f))
    }
}
'''
s += hint.replace('\n', nl)
io.open(P, 'w', newline='').write(s)

C = 'app/src/main/java/com/music/spotui/ui/immersive/player/PlayerControls.kt'
edit(C, [
("""    onClick: () -> Unit,
    active: Boolean = false,
) {
    val discAlpha by animateFloatAsState(""", """    onClick: () -> Unit,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val discAlpha by animateFloatAsState("""),
("""            .clickable(
                interactionSource = source,
                indication = null,
            ) {
                Haptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = icon,
            animationSpec = tween(durationMillis = 180),
            label = "playerMenuGlyph",""", """            .combinedClickable(
                interactionSource = source,
                indication = null,
                onLongClick = onLongClick?.let { l -> { Haptics.confirm(); l() } },
            ) {
                Haptics.click()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = icon,
            animationSpec = tween(durationMillis = 180),
            label = "playerMenuGlyph","""),
])

B = 'app/src/main/java/com/music/spotui/ui/immersive/player/PlayerBridge.kt'
edit(B, [
("""                if (on) Toast.makeText(context, context.getString(R.string.immersive_player_added_to_liked), Toast.LENGTH_SHORT).show()""",
"""                val song = state.song
                if (on) com.music.spotui.ui.components.AppNotice.show(
                    title = context.getString(R.string.immersive_player_added_to_liked),
                    subtitle = state.title.takeIf { it.isNotBlank() }?.let { t -> if (state.artist.isBlank()) t else "$t · ${state.artist}" },
                    icon = com.music.spotui.ui.design.AppIcons.Star,
                    coverUrl = state.coverUrl,
                    tone = com.music.spotui.ui.components.AppNotice.Tone.LIKE,
                    actionLabel = if (song != null) context.getString(R.string.notice_change) else null,
                    onAction = song?.let { s -> { com.music.spotui.ui.components.AppNotice.openSaveTo(s) } },
                ) else com.music.spotui.ui.components.AppNotice.show(
                    title = context.getString(R.string.snackbar_removed_from_liked_songs).trimEnd('.'),
                    subtitle = state.title.takeIf { it.isNotBlank() },
                    coverUrl = state.coverUrl,
                )"""),
])

for f in ['app/src/main/java/com/music/spotui/ui/immersive/sheets/ImmersiveSongOptionsSheet.kt', 'app/src/main/java/com/music/spotui/ui/components/SongOptionsSheet.kt']:
    edit(f, [("fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()",
              "fun toast(text: String) = com.music.spotui.ui.components.AppNotice.text(text.trimEnd('.'))")])

S = 'app/src/main/java/com/music/spotui/di/SongPlayer.kt'
edit(S, [
("""                    android.widget.Toast.makeText(Graph.app, app.myspoti.engine.R.string.why_source_none_chosen, android.widget.Toast.LENGTH_SHORT).show()""",
"""                    com.music.spotui.ui.components.AppNotice.show(Graph.app.getString(app.myspoti.engine.R.string.why_source_none_chosen), song.title, tone = com.music.spotui.ui.components.AppNotice.Tone.ERROR)"""),
("""        main.post { android.widget.Toast.makeText(Graph.app, app.myspoti.engine.R.string.why_source_none_chosen, android.widget.Toast.LENGTH_SHORT).show() }""",
"""        com.music.spotui.ui.components.AppNotice.show(Graph.app.getString(app.myspoti.engine.R.string.why_source_none_chosen), title, tone = com.music.spotui.ui.components.AppNotice.Tone.ERROR)"""),
])

A = 'app/src/main/java/com/music/spotui/App.kt'
edit(A, [("""        com.music.spotui.ui.components.HiddenSongUndo()""", """        com.music.spotui.ui.components.HiddenSongUndo()
        // The app's own notification pill (likes, saves, errors), over everything.
        com.music.spotui.ui.components.AppNoticeHost()""")])

io.open('app/src/main/res/values/notice_strings.xml', 'w', newline='').write('''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="notice_change">Change</string>
    <string name="player_info_hint">Lyrics · Artist · Credits</string>
</resources>
''')
print("ok")
