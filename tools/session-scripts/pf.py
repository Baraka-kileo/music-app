import sys, os
os.chdir('O:/base/app/src/main/java/com/music/spotui')

def edit(p, reps):
    s = open(p, 'rb').read().decode('utf-8'); nl = '\r\n' if '\r\n' in s else '\n'
    for a, b in reps:
        a, b = a.replace('\n', nl), b.replace('\n', nl)
        n = s.count(a)
        if n != 1: sys.exit(f"{p}: {n} for {a[:90]!r}")
        s = s.replace(a, b)
    open(p, 'wb').write(s.encode('utf-8'))

S = 'ui/components/ShareCard.kt'
edit(S, [
# Requests go to a preview sheet first (theme chips), then out.
("""    /** Renders and shares the card; does nothing if the image cannot be produced. */
    suspend fun share(context: Context, title: String, artist: String, coverUri: String, chooserTitle: String) {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val cover = loadCover(context, coverUri)
                val card = render(title, artist, cover)""",
"""    /** The card's background: the blurred cover, the cover's colours as a gradient, or one solid colour. */
    enum class Theme { GLASS, GRADIENT, SOLID }

    /** What the preview sheet shows: one card waiting for a theme and the share button. */
    class Request(
        val title: String, val artist: String, val coverUri: String, val chooserTitle: String,
        val lines: List<String>? = null, val text: String? = null,
    )
    internal val pending = androidx.compose.runtime.mutableStateOf<Request?>(null)

    /** Opens the preview: the card in each theme, then shared from there. */
    suspend fun share(context: Context, title: String, artist: String, coverUri: String, chooserTitle: String) {
        pending.value = Request(title, artist, coverUri, chooserTitle)
    }

    /** The card for [r] in [theme] (off the main thread). */
    internal suspend fun renderFor(context: Context, r: Request, theme: Theme): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val cover = loadCover(context, r.coverUri)
            val mark = context.applicationInfo.loadLabel(context.packageManager).toString()
            if (r.lines != null) renderLyrics(r.lines, r.title, r.artist, cover, theme, mark) else render(r.title, r.artist, cover, theme, mark)
        }.getOrNull()
    }

    /** Writes [card] to the cache and opens the share sheet (with the direct-share row). */
    internal suspend fun send(context: Context, r: Request, card: Bitmap) {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                File(dir, "share_card.png").also { f -> f.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            }.getOrNull()
        } ?: return
        send(context, file, r.text, r.chooserTitle)
    }

    /** The old direct path, kept for callers that render without a preview. */
    private suspend fun shareNow(context: Context, title: String, artist: String, coverUri: String, chooserTitle: String) {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val cover = loadCover(context, coverUri)
                val card = render(title, artist, cover, Theme.GLASS, "")"""),
("""    suspend fun shareLyrics(context: Context, lines: List<String>, title: String, artist: String, coverUri: String, text: String, chooserTitle: String) {
        if (lines.isEmpty()) return
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val card = renderLyrics(lines, title, artist, loadCover(context, coverUri))""",
"""    suspend fun shareLyrics(context: Context, lines: List<String>, title: String, artist: String, coverUri: String, text: String, chooserTitle: String) {
        if (lines.isEmpty()) return
        pending.value = Request(title, artist, coverUri, chooserTitle, lines, text)
    }

    private suspend fun shareLyricsNow(context: Context, lines: List<String>, title: String, artist: String, coverUri: String, text: String, chooserTitle: String) {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val card = renderLyrics(lines, title, artist, loadCover(context, coverUri), Theme.GLASS, "")"""),
# Shared background painter.
("""    private fun renderLyrics(lines: List<String>, title: String, artist: String, cover: Bitmap?): Bitmap {
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
""", """    /** A colour of the cover at ([fx], [fy]) of its width and height, darkened so white text reads on it. */
    private fun coverColor(cover: Bitmap?, fx: Float, fy: Float, dark: Float): Int {
        if (cover == null) return Color.rgb(30, 30, 36)
        val tiny = Bitmap.createScaledBitmap(cover, 4, 4, true)
        val px = tiny.getPixel((fx * 3).toInt().coerceIn(0, 3), (fy * 3).toInt().coerceIn(0, 3))
        val hsv = FloatArray(3); Color.colorToHSV(px, hsv)
        hsv[1] = (hsv[1] * 1.15f).coerceAtMost(1f); hsv[2] = (hsv[2] * dark).coerceIn(0.12f, 0.62f)
        return Color.HSVToColor(hsv)
    }

    private fun paintBackground(c: Canvas, cover: Bitmap?, theme: Theme, paint: Paint) {
        when (theme) {
            Theme.GLASS -> {
                c.drawColor(Color.rgb(18, 18, 18))
                if (cover != null) {
                    val tiny = Bitmap.createScaledBitmap(cover, 16, 16, true)
                    c.drawBitmap(tiny, null, RectF(-200f, -200f, W + 200f, H + 200f), paint)
                }
                paint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(),
                    intArrayOf(Color.argb(110, 0, 0, 0), Color.argb(160, 0, 0, 0), Color.argb(220, 0, 0, 0)), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
                paint.shader = null
            }
            Theme.GRADIENT -> {
                paint.shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
                    intArrayOf(coverColor(cover, 0f, 0f, 1f), coverColor(cover, 1f, 1f, 0.55f)), null, Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
                paint.shader = null
            }
            Theme.SOLID -> c.drawColor(coverColor(cover, 0.5f, 0.5f, 0.8f))
        }
    }

    private fun drawMark(c: Canvas, mark: String, y: Float) {
        if (mark.isBlank()) return
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255); textSize = 40f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f
        }
        c.drawText(mark, W / 2f, y, p)
    }

    private fun renderLyrics(lines: List<String>, title: String, artist: String, cover: Bitmap?, theme: Theme, mark: String): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paintBackground(c, cover, theme, paint)
        drawMark(c, mark, 140f)
"""),
("""    private fun render(title: String, artist: String, cover: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Background: the cover shrunk to a few pixels and stretched back is a cheap blur.
        c.drawColor(Color.rgb(18, 18, 18))
        if (cover != null) {
            val tiny = Bitmap.createScaledBitmap(cover, 16, 16, true)
            c.drawBitmap(tiny, null, RectF(-200f, -200f, W + 200f, H + 200f), paint)
        }
        paint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(),
            intArrayOf(Color.argb(120, 0, 0, 0), Color.argb(170, 0, 0, 0), Color.argb(230, 0, 0, 0)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null
""", """    private fun render(title: String, artist: String, cover: Bitmap?, theme: Theme, markText: String): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paintBackground(c, cover, theme, paint)
"""),
("""        // Wordmark.
        val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255); textSize = 40f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f
        }
        c.drawText("Myspoti", W / 2f, H - 150f, mark)
        return out""", """        // Wordmark: the app's own name.
        drawMark(c, markText, H - 150f)
        return out"""),
])
print('ok')
