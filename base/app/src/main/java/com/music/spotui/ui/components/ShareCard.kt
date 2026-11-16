package com.music.spotui.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Share as image": a 1080x1920 story card (blurred cover background, large cover, title,
 * artist, Myspoti wordmark) written to cache/shared/ and handed out through the FileProvider,
 * so Instagram / WhatsApp stories and the like can pick it up.
 */
object ShareCard {
    private const val W = 1080
    private const val H = 1920

    /** Renders and shares the card; does nothing if the image cannot be produced. */
    suspend fun share(context: Context, title: String, artist: String, coverUri: String, chooserTitle: String) {
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val cover = loadCover(context, coverUri)
                val card = render(title, artist, cover)
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                File(dir, "myspoti_card.png").also { f ->
                    f.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }.getOrNull()
        } ?: return
        send(context, file, null, chooserTitle)
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun loadCover(context: Context, uri: String): Bitmap? {
        if (uri.isBlank()) return null
        val bmp = runCatching {
            Glide.with(context.applicationContext).asBitmap().load(app.myspoti.host.android.Covers.large(uri)).submit(1000, 1000).get()
        }.getOrNull() ?: return null
        return if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
    }

    private fun renderLyrics(lines: List<String>, title: String, artist: String, cover: Bitmap?): Bitmap {
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
        val words = lines.joinToString("\n")
        val room = footTop - 200f - 120f
        fun laid(size: Float): StaticLayout {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = size; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            return StaticLayout.Builder.obtain(words, 0, words.length, p, (W - pad * 2).toInt())
                .setLineSpacing(0f, 1.18f).build()
        }
        var size = 84f
        var layout = laid(size)
        while (layout.height > room && size > 40f) { size -= 4f; layout = laid(size) }
        c.save()
        c.clipRect(0f, 200f, W.toFloat(), 200f + room)
        c.translate(pad, 200f + ((room - layout.height) / 2f).coerceAtLeast(0f))
        layout.draw(c)
        c.restore()
        return out
    }

    private fun render(title: String, artist: String, cover: Bitmap?): Bitmap {
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

        // Cover: large, centred, rounded.
        val side = 840f
        val left = (W - side) / 2f
        val top = 380f
        val box = RectF(left, top, left + side, top + side)
        if (cover != null) {
            val layer = c.saveLayer(box, null)
            c.drawRoundRect(box, 36f, 36f, paint.apply { color = Color.BLACK })
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            c.drawBitmap(cover, null, box, paint)
            paint.xfermode = null
            c.restoreToCount(layer)
        } else {
            paint.color = Color.rgb(40, 40, 40)
            c.drawRoundRect(box, 36f, 36f, paint)
        }

        // Title (up to two lines) and artist.
        val textW = (W - 160)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 62f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save(); c.translate(80f, top + side + 80f); titleLayout.draw(c); c.restore()
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255); textSize = 46f }
        val artistLayout = StaticLayout.Builder.obtain(artist, 0, artist.length, artistPaint, textW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(1).setEllipsize(TextUtils.TruncateAt.END).build()
        c.save(); c.translate(80f, top + side + 80f + titleLayout.height + 24f); artistLayout.draw(c); c.restore()

        // Wordmark.
        val mark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 255, 255, 255); textSize = 40f; textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.12f
        }
        c.drawText("Myspoti", W / 2f, H - 150f, mark)
        return out
    }
}
