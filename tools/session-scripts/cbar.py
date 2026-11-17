p=r"O:\base\app\src\main\java\com\music\spotui\ui\screens\PlayerScreen.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
anchor="        // Immersive: one compact strip at the bottom of the full-bleed artwork, the official"
assert s.count(anchor)==1
ins='''        // Scrolled past the controls: the official app's slim bar at the top (title, artist, add,
        // play/pause, the progress line), in the song's colour.
        val headerOn by remember { androidx.compose.runtime.derivedStateOf { pageList.firstVisibleItemIndex > 0 || pageList.firstVisibleItemScrollOffset > pagePx * 0.7f } }
        if (!immersive) androidx.compose.animation.AnimatedVisibility(
            visible = headerOn && !showLyrics && !showQueue,
            enter = androidx.compose.animation.fadeIn(tween(180)),
            exit = androidx.compose.animation.fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ClassicScrolledBar(
                title = songTitle, artist = songSinger, liked = isLiked.value, playing = songPlayingState, tone = dominentColor,
                onLike = { playerViewModel.updateLikeState(!isLiked.value) },
                onPlayPause = { if (songPlayingState) SongPlayer.pause() else SongPlayer.play() },
            )
        }
'''
s=s.replace(anchor,ins.replace("\n",nl)+anchor)
fn='''
/** The Classic player's bar once scrolled down, as the official app's: title and artist, add, play/pause, progress. */
@Composable
private fun ClassicScrolledBar(
    title: String, artist: String, liked: Boolean, playing: Boolean, tone: Color,
    onLike: () -> Unit, onPlayPause: () -> Unit,
) {
    val ground = androidx.compose.ui.graphics.lerp(tone, Color.Black, 0.35f)
    Column(Modifier.fillMaxWidth().background(ground).statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            // The look's own like: the plus and the green tick.
            LikeButton(liked = liked, size = 24.dp, touch = 44.dp, onClick = onLike)
            Spacer(Modifier.width(4.dp))
            androidx.compose.material3.IconButton(onClick = { com.music.spotui.di.Haptics.tick(); onPlayPause() }) {
                androidx.compose.material3.Icon(
                    if (playing) com.music.spotui.ui.design.AppIcons.Pause else com.music.spotui.ui.design.AppIcons.PlayArrow,
                    contentDescription = null, tint = Color.White,
                )
            }
        }
        val pos by androidx.compose.runtime.produceState(0f) {
            while (true) {
                val d = SongPlayer.getDuration()
                value = if (d > 0) (SongPlayer.getCurrentPosition().toFloat() / d).coerceIn(0f, 1f) else 0f
                kotlinx.coroutines.delay(500)
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.25f)).drawBehind {
            drawRect(Color.White, size = size.copy(width = size.width * pos))
        })
    }
}
'''
s=s.rstrip()+nl+fn.replace("\n",nl)
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
