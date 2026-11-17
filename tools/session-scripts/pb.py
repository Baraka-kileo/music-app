import io, sys, os
os.chdir('O:/base')
exec(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'pa.py')).read().split("P = 'app")[0].split("os.chdir('O:/base')")[1])

M = 'app/src/main/java/com/music/spotui/ui/immersive/player/ImmersiveMiniPlayer.kt'
edit(M, [
("""    val shape = RoundedCornerShape(MINI_HEIGHT / 2)
    val capsuleSource = remember { MutableInteractionSource() }
""", """    val shape = RoundedCornerShape(MINI_HEIGHT / 2)
    val capsuleSource = remember { MutableInteractionSource() }
    // How far the song is, for the thin line along the capsule's foot: read twice a second, drawn only.
    val progress = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val d = SongPlayer.getDuration()
            progress.floatValue = if (d > 0) (SongPlayer.getCurrentPosition().toFloat() / d).coerceIn(0f, 1f) else 0f
            kotlinx.coroutines.delay(500)
        }
    }
    val lineColor = Immersive.Accent
"""),
("""            .immersiveGlassSurface(shape, interactionSource = capsuleSource)
            .clip(shape)
""", """            .immersiveGlassSurface(shape, interactionSource = capsuleSource)
            .clip(shape)
            .drawWithContent {
                drawContent()
                val f = progress.floatValue
                if (f > 0f && !adPlaying) {
                    val inset = size.height / 2
                    val y = size.height - 1.5.dp.toPx()
                    val w = size.width - inset * 2
                    drawLine(Color.White.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(inset + w, y), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(lineColor, androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(inset + w * f, y), strokeWidth = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
"""),
])
s = io.open(M, newline='').read()
if 'import androidx.compose.ui.draw.drawWithContent' not in s:
    s = s.replace('import androidx.compose.ui.draw.clip', 'import androidx.compose.ui.draw.clip\nimport androidx.compose.ui.draw.drawWithContent'.replace('\n', '\r\n' if '\r\n' in s else '\n'), 1)
    io.open(M, 'w', newline='').write(s)
print('ok')
