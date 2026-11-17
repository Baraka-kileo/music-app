import io
J = 'O:/base/app/src/main/java/com/music/spotui/'

def edit(path, pairs):
    s = io.open(path, encoding='utf-8', newline='').read()
    crlf = '\r\n' in s
    s = s.replace('\r\n', '\n')
    for a, b in pairs:
        assert s.count(a) == 1, (path, s.count(a), a[:70])
        s = s.replace(a, b)
    if crlf: s = s.replace('\n', '\r\n')
    io.open(path, 'w', encoding='utf-8', newline='').write(s)

# ---- Immersive player: the music video, as the Classic player offers it
P = J + 'ui/immersive/player/ImmersivePlayer.kt'
edit(P, [
    ('    val canvasUrl = rememberImmersiveCanvas(state)\n', '''    // The music video (a clip kept in time with the song): looked up for the song shown, offered by
    // a pill on the cover once it is ready, played in the cover's place, full screen on a tap.
    val mv = com.music.spotui.video.MusicVideoController
    androidx.compose.runtime.DisposableEffect(mv) {
        mv.attachPlayerScreen()
        onDispose { mv.detachPlayerScreen() }
    }
    val videoAllowed = com.music.spotui.data.preferences.MusicVideoPref.on && state.deviceName == null &&
        !state.adPlaying && !state.isEpisode
    LaunchedEffect(state.trackId, state.title, state.buffering) {
        if (!state.buffering && videoAllowed && state.trackId.isNotBlank()) {
            mv.prepare(state.trackId, state.title, state.artist, com.music.spotui.di.SongPlayer.getDuration())
        }
    }
    val readyVideo = mv.state.value as? com.music.spotui.video.MusicVideoController.State.Ready
    val videoMatches = state.trackId.isNotBlank() && readyVideo?.trackId == state.trackId
    val videoReady = videoMatches && videoAllowed && !state.buffering && !com.music.spotui.data.DataSaver.active
    LaunchedEffect(com.music.spotui.data.DataSaver.active) {
        if (com.music.spotui.data.DataSaver.active && mv.on.value) mv.setOn(false)
    }
    val videoOn = mv.on.value && videoMatches && videoAllowed
    val showVideo = videoOn && panel == Panel.NONE && !mv.fullscreen.value
    LaunchedEffect(mv.state.value, videoReady) { if (videoReady) mv.warm() }

    val canvasUrl = rememberImmersiveCanvas(state)
'''),
    ('    val canvasOn = canvasUrl != null && canvasShown && !state.adPlaying && panel == Panel.NONE\n',
     '    val canvasOn = canvasUrl != null && canvasShown && !state.adPlaying && panel == Panel.NONE && !videoOn\n'),
    ('''                        SongBlendCover(blend, Modifier.fillMaxSize()) { it.override(COVER_DECODE_PX) }
                    }
''', '''                        SongBlendCover(blend, Modifier.fillMaxSize()) { it.override(COVER_DECODE_PX) }
                        if (showVideo) Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            com.music.spotui.ui.components.VideoSurface(onTap = { mv.fullscreen.value = true })
                        }
                    }

                    // ---- Switch to video / to audio: on the cover's lower corner, gone with a panel open ----
                    if ((videoReady || videoOn) && panel == Panel.NONE) Box(
                        Modifier
                            .offset {
                                // Inside the cover as it is drawn: it steps back while paused.
                                val inset = artSize() * ((1f - pauseScale.value) / 2f) + 12.dp
                                IntOffset((artStart() + inset).roundToPx(), (artTop() + artSize() - inset - 32.dp).roundToPx())
                            }
                            .graphicsLayer { alpha = 1f - p() },
                    ) {
                        com.music.spotui.ui.components.VideoSwitchPill(on = videoOn)
                    }
'''),
    ('''        }
        }
    }

    if (showDevices) DevicesSheet''', '''        }
        }
        if (videoOn && mv.fullscreen.value) com.music.spotui.ui.screens.VideoFullscreen()
    }

    if (showDevices) DevicesSheet'''),
])
edit(J + 'ui/screens/PlayerScreen.kt', [('@Composable\nprivate fun VideoFullscreen() {', '@Composable\ninternal fun VideoFullscreen() {')])

# ---- Lyrics as a picture: the lines picked, on the song's colours
S = J + 'ui/components/ShareCard.kt'
edit(S, [
    ('''        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
''', '''        send(context, file, null, chooserTitle)
    }

    /**
     * The lyric [lines] picked as a picture: the lines large over the cover's colours, the song
     * under them. [text] goes along for the apps that take words with a picture.
     */
    suspend fun shareLyrics(context: Context, lines: List<String>, title: String, artist: String, coverUri: String, text: String, chooserTitle: String) {
        if (lines.isEmpty()) return
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val card = renderLyrics(lines, title, artist, loadCover(context, coverUri))
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                File(dir, "lyrics_card.png").also { f ->
                    f.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }.getOrNull()
        } ?: return
        send(context, file, text, chooserTitle)
    }

    private fun send(context: Context, file: File, text: String?, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (text != null) putExtra(Intent.EXTRA_TEXT, text)
            clipData = android.content.ClipData.newRawUri(null, uri)
'''),
    ('''    private fun render(title: String, artist: String, cover: Bitmap?): Bitmap {''', '''    private fun renderLyrics(lines: List<String>, title: String, artist: String, cover: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        c.drawColor(Color.rgb(18, 18, 18))
        if (cover != null) {
            val tiny = Bitmap.createScaledBitmap(cover, 16, 16, true)
            c.drawBitmap(tiny, null, RectF(-200f, -200f, W + 200f, H + 200f), paint)
        }
        paint.color = Color.argb(120, 0, 0, 0)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)

        // The song, at the foot: a small cover, the title and the artist beside it.
        val pad = 96f
        val thumb = 150f
        val footTop = H - 240f - thumb
        val box = RectF(pad, footTop, pad + thumb, footTop + thumb)
        if (cover != null) {
            val layer = c.saveLayer(box, null)
            c.drawRoundRect(box, 22f, 22f, paint.apply { color = Color.BLACK })
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cover, null, box, paint)
            paint.xfermode = null
            c.restoreToCount(layer)
        }
        val creditW = (W - pad * 2 - thumb - 36f).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 46f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255); textSize = 38f }
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, creditW)
            .setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build()
        val artistLayout = StaticLayout.Builder.obtain(artist, 0, artist.length, artistPaint, creditW)
            .setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build()
        val creditTop = footTop + (thumb - titleLayout.height - 8f - artistLayout.height) / 2f
        c.save(); c.translate(pad + thumb + 36f, creditTop); titleLayout.draw(c); c.restore()
        c.save(); c.translate(pad + thumb + 36f, creditTop + titleLayout.height + 8f); artistLayout.draw(c); c.restore()

        // The lines: as large as fits the room above the song, centred in it.
        val words = lines.joinToString("\\n")
        val room = footTop - 200f - 120f
        var size = 84f
        var layout: StaticLayout
        while (true) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = size; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            layout = StaticLayout.Builder.obtain(words, 0, words.length, p, (W - pad * 2).toInt())
                .setLineSpacing(0f, 1.18f).build()
            if (layout.height <= room || size <= 40f) break
            size -= 4f
        }
        c.save()
        c.clipRect(0f, 200f, W.toFloat(), 200f + room)
        c.translate(pad, 200f + ((room - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(c)
        c.restore()
        return out
    }

    private fun render(title: String, artist: String, cover: Bitmap?): Bitmap {'''),
])

# The text share, also as a value: the picture carries it.
L = J + 'ui/components/LyricsShare.kt'
edit(L, [(
'''fun shareLyricLines(context: Context, lyrics: Lyrics, range: IntRange, title: String, artist: String) {
    val lines = range.mapNotNull { lyrics.lines.getOrNull(it)?.text?.trim() }.filter { it.isNotEmpty() && it != "\u266a" }
    if (lines.isEmpty()) return
''', '''/** The lines [range] of [lyrics] that are words: no empty line, no note mark. */
fun lyricLinesOf(lyrics: Lyrics, range: IntRange): List<String> =
    range.mapNotNull { lyrics.lines.getOrNull(it)?.text?.trim() }.filter { it.isNotEmpty() && it != "\u266a" }

/** The lines picked as a picture ([ShareCard.shareLyrics]), with the song's name and link as its words. */
suspend fun shareLyricCard(context: Context, lyrics: Lyrics, range: IntRange, title: String, artist: String, coverUri: String) {
    val lines = lyricLinesOf(lyrics, range)
    val id = SongPlayer.currentTrackId
    val text = buildString {
        append(title).append(" \u00b7 ").append(artist)
        if (id.isNotBlank()) append('\\n').append("https://open.spotify.com/track/").append(id)
    }
    ShareCard.shareLyrics(context, lines, title, artist, coverUri, text, context.getString(R.string.lyrics_share))
}

fun shareLyricLines(context: Context, lyrics: Lyrics, range: IntRange, title: String, artist: String) {
    val lines = lyricLinesOf(lyrics, range)
    if (lines.isEmpty()) return
''')])

# ---- Immersive lyrics: the share bar sends the picture
I = J + 'ui/immersive/lyrics/ImmersiveLyricsPanel.kt'
edit(I, [(
    'onShare = { picked?.let { shareLyricLines(context, current.lyrics, it, title, artist); vm.stopSelecting() } },',
    '''onShare = {
                            picked?.let { range ->
                                val words = current.lyrics
                                shareScope.launch {
                                    com.music.spotui.ui.components.shareLyricCard(
                                        context, words, range, title, artist, Graph.currentSongState.coverUri.value,
                                    )
                                }
                                vm.stopSelecting()
                            }
                        },'''),
    ('    val title = song.title.value\n', '    val title = song.title.value\n    val shareScope = androidx.compose.runtime.rememberCoroutineScope()\n'),
])
print('ok')
