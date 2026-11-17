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
edit(J + 'ui/immersive/lyrics/ImmersiveLyricsPanel.kt', [('import com.music.spotui.di.Graph\n', 'import com.music.spotui.di.Graph\nimport kotlinx.coroutines.launch\n')])
edit(J + 'ui/components/ShareCard.kt', [('''        var size = 84f
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
''', '''        fun laid(size: Float): StaticLayout {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = size; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            return StaticLayout.Builder.obtain(words, 0, words.length, p, (W - pad * 2).toInt())
                .setLineSpacing(0f, 1.18f).build()
        }
        var size = 84f
        var layout = laid(size)
        while (layout.height > room && size > 40f) { size -= 4f; layout = laid(size) }
''')])
print('ok')
