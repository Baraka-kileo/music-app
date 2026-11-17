import sys, os, re
os.chdir('O:/base/app/src/main/java/com/music/spotui')

def edit(p, reps):
    s = open(p, 'rb').read().decode('utf-8'); nl = '\r\n' if '\r\n' in s else '\n'
    for a, b in reps:
        a, b = a.replace('\n', nl), b.replace('\n', nl)
        n = s.count(a)
        if n != 1: sys.exit(f"{p}: {n} for {a[:90]!r}")
        s = s.replace(a, b)
    open(p, 'wb').write(s.encode('utf-8'))

# ---- Shared loader: callable with plain ids (Classic) as well as the Immersive state ----
I = 'ui/immersive/player/ImmersiveInfoPanel.kt'
edit(I, [
("private class SongInfo(", "internal class SongInfo("),
("""private fun rememberSongInfo(state: ImmersivePlayerState): SongInfo? {
    val trackId = state.trackId
    val info by produceState<SongInfo?>(null, trackId, state.title) {""",
"""private fun rememberSongInfo(state: ImmersivePlayerState): SongInfo? = rememberSongInfo(state.trackId, state.title, state.artist)

@Composable
internal fun rememberSongInfo(trackId: String, title: String, artist: String): SongInfo? {
    val info by produceState<SongInfo?>(null, trackId, title) {"""),
("""        if (trackId.isBlank()) { value = SongInfo(null, null, null, about(state)); return@produceState }
        val refs = runCatching { com.music.spotui.ui.components.ArtistChooser.artistsOf(trackId, state.artist) }""",
"""        if (trackId.isBlank()) { value = SongInfo(null, null, null, about(trackId, title, artist)); return@produceState }
        val refs = runCatching { com.music.spotui.ui.components.ArtistChooser.artistsOf(trackId, artist) }"""),
("""        value = SongInfo(page, rank, credits, about(state))""", """        value = SongInfo(page, rank, credits, about(trackId, title, artist))"""),
("""private suspend fun about(state: ImmersivePlayerState): String? {
    if (!com.music.spotui.data.preferences.GeniusPref.on || state.title.isBlank()) return null
    val m = com.music.spotui.data.api.GeniusMeaning.currentFor(state.trackId)
        ?: com.music.spotui.data.api.GeniusMeaning.cached(state.title, state.artist)
        ?: runCatching { com.music.spotui.data.api.GeniusMeaning.find(state.title, state.artist) }.getOrNull()""",
"""private suspend fun about(trackId: String, title: String, artist: String): String? {
    if (!com.music.spotui.data.preferences.GeniusPref.on || title.isBlank()) return null
    val m = com.music.spotui.data.api.GeniusMeaning.currentFor(trackId)
        ?: com.music.spotui.data.api.GeniusMeaning.cached(title, artist)
        ?: runCatching { com.music.spotui.data.api.GeniusMeaning.find(title, artist) }.getOrNull()"""),
])

# ---- Classic player: the cards under the lyrics card ----
P = 'ui/screens/PlayerScreen.kt'
edit(P, [
("""                onExpand = { showLyrics = true },
            )
        }
        }""", """                onExpand = { showLyrics = true },
            )
        }
        // The cards under the lyrics: the artist, up next, credits, the song's story, explore.
        if (!immersive && !adPlaying) item(key = "infoCards") {
            com.music.spotui.ui.components.ClassicInfoCards(
                trackId = SongPlayer.currentTrackId, title = songTitle, artist = songSinger, coverUrl = songCoverUri,
                navController = navController,
                onOpenQueue = { showQueue = true },
                onOpenAlbum = { openNowPlayingAlbum(playerViewModel, songSinger, titleScope) { navController.navigate(it) } },
            )
        }
        }"""),
])

# ---- Classic queue: swipe right to play next ----
S = 'ui/components/PlayerSheets.kt'
edit(S, [
("""private fun SwipeLeftToRemove(enabled: Boolean, onRemove: () -> Unit, content: @Composable () -> Unit) {""",
"""private fun SwipeLeftToRemove(enabled: Boolean, onRemove: () -> Unit, onPlayNext: (() -> Unit)? = null, content: @Composable () -> Unit) {"""),
("""    val revealed by remember(offset) { derivedStateOf { offset.value < 0f } }""",
"""    val revealed by remember(offset) { derivedStateOf { offset.value < 0f } }
    val revealedRight by remember(offset) { derivedStateOf { offset.value > 0f } }
    var armedRight by remember { mutableStateOf(false) }"""),
("""            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description), tint = AppText, modifier = Modifier.padding(end = 24.dp).size(24.dp))
        }""", """            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.aligned_curation_first_save_default_row_remove_action_description), tint = AppText, modifier = Modifier.padding(end = 24.dp).size(24.dp))
        }
        // Swiped right: plays next.
        if (revealedRight) Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.matchParentSize().background(if (armedRight) Green else Color(0xFF1E5E35)),
        ) {
            Text(stringResource(R.string.notice_plays_next), color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp))
        }"""),
("""                            if (armed) {
                                scope.launch { offset.animateTo(-size.width.toFloat()) ; onRemove(); offset.snapTo(0f) }
                            } else scope.launch { offset.animateTo(0f) }
                            armed = false
                        },
                        onDragCancel = { armed = false; scope.launch { offset.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        val next = (offset.value + dx).coerceIn(-size.width.toFloat(), 0f)
                        scope.launch { offset.snapTo(next) }
                        val nowArmed = -next >= threshold
                        if (nowArmed && !armed) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate)
                        armed = nowArmed""", """                            if (armed) {
                                scope.launch { offset.animateTo(-size.width.toFloat()) ; onRemove(); offset.snapTo(0f) }
                            } else {
                                if (armedRight) onPlayNext?.invoke()
                                scope.launch { offset.animateTo(0f) }
                            }
                            armed = false; armedRight = false
                        },
                        onDragCancel = { armed = false; armedRight = false; scope.launch { offset.animateTo(0f) } },
                    ) { change, dx ->
                        change.consume()
                        val maxRight = if (onPlayNext != null) size.width * 0.5f else 0f
                        val next = (offset.value + dx).coerceIn(-size.width.toFloat(), maxRight)
                        scope.launch { offset.snapTo(next) }
                        val nowArmed = -next >= threshold
                        if (nowArmed && !armed) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate)
                        armed = nowArmed
                        val nowRight = next >= minimum
                        if (nowRight && !armedRight) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureThresholdActivate)
                        armedRight = nowRight"""),
("""                        SwipeLeftToRemove(enabled = !editing && dragging == null, onRemove = {
                            if (e.queued) queued = queued - item else nextUp = nextUp - item
                            SongPlayer.removeFromQueue(item)
                        }) {""", """                        SwipeLeftToRemove(enabled = !editing && dragging == null, onRemove = {
                            if (e.queued) queued = queued - item else nextUp = nextUp - item
                            SongPlayer.removeFromQueue(item)
                        }, onPlayNext = {
                            SongPlayer.playNext(item.uri.substringAfterLast(':'))
                            AppNotice.show(
                                title = com.music.spotui.di.Graph.app.getString(R.string.notice_plays_next), subtitle = item.song.title,
                                coverUrl = item.song.coverUri, tone = AppNotice.Tone.SUCCESS,
                            )
                        }) {"""),
])
print('ok')
