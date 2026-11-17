/**
 * The bar at the top of the scrolled player, in the look's own shape: a floating rounded bar in the
 * song's colour with the cover, title and artist, the star like and play/pause in the player's round
 * glyphs, and the progress as a line along its foot. Tap: back up to the player.
 */
@Composable
private fun ScrolledHeader(state: ImmersivePlayerState, actions: ImmersivePlayerActions, tone: Color, onBackToTop: () -> Unit = {}) {
    val ground = androidx.compose.ui.graphics.lerp(tone, Color.Black, 0.45f)
    val shape = RoundedCornerShape(20.dp)
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .shadow(16.dp, shape, clip = false)
                .clip(shape)
                .background(ground.copy(alpha = 0.94f))
                .border(0.6.dp, Color.White.copy(alpha = 0.14f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { Haptics.tick(); onBackToTop() },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                com.music.spotui.ui.immersive.components.ImmersiveCover(
                    url = state.coverUrl, modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp),
                    contentDescription = null, border = false,
                )
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.title, style = Immersive.BodyStrong, color = Color.White,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(state.artist, style = Immersive.Caption, color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                // The same star as beside the credits: one like across the look.
                CircleGlyph(
                    icon = if (state.liked) com.music.spotui.ui.design.AppIcons.Star else com.music.spotui.ui.design.AppIcons.StarBorder,
                    contentDescription = stringResource(if (state.liked) R.string.immersive_player_unlike else R.string.immersive_player_like),
                    onClick = actions.toggleLike,
                    active = state.liked,
                )
                Spacer(Modifier.width(8.dp))
                CircleGlyph(
                    icon = if (state.playing) com.music.spotui.ui.design.AppIcons.Pause else com.music.spotui.ui.design.AppIcons.PlayArrow,
                    contentDescription = null,
                    onClick = actions.playPause,
                )
            }
            // The progress line, read twice a second.
            val now by androidx.compose.runtime.rememberUpdatedState(state)
            val pos by androidx.compose.runtime.produceState(0L) {
                while (true) { value = now.positionMs(); kotlinx.coroutines.delay(500) }
            }
            Box(
                Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.18f)).drawBehind {
                    val d = now.durationMs.coerceAtLeast(1L)
                    drawRect(Color.White, size = size.copy(width = size.width * (pos.toFloat() / d).coerceIn(0f, 1f)))
                },
            )
        }
    }
}
