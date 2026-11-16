package com.music.spotui.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.theme.AppSans
import kotlin.math.PI
import kotlin.math.sin

/*
 * The first launch's own style: a warm orange light falling into deep purple, covers drawn in
 * code (no picture is shipped or downloaded for them), an editorial headline and one gradient
 * button. Everything here is static but the cover wall's slow drift.
 */

internal val OnbOrange = com.music.spotui.ui.design.Ds.EmberStart
internal val OnbPink = com.music.spotui.ui.design.Ds.EmberMid
internal val OnbPurple = com.music.spotui.ui.design.Ds.EmberEnd
internal val OnbBrush: Brush get() = Brush.horizontalGradient(listOf(OnbOrange, OnbPink, OnbPurple))

/** A glass surface of the first launch: a faint white fill and a hairline edge. */
internal fun Modifier.onbGlass(shape: androidx.compose.ui.graphics.Shape, fill: Float = 0.08f, edge: Float = 0.14f): Modifier =
    this.clip(shape).background(Color.White.copy(alpha = fill)).border(1.dp, Color.White.copy(alpha = edge), shape)

/** The page behind every first-launch screen. [dim]: black laid over it, for pages of plain reading. */
@Composable
internal fun OnboardingBackdrop(modifier: Modifier = Modifier, dim: Float = 0f) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF8A3A14), 0.30f to Color(0xFF4A1A24), 0.62f to Color(0xFF1D1036), 1f to Color(0xFF09060F),
            ),
        )
        val sun = Offset(w * 0.98f, h * 0.04f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A3A).copy(alpha = 0.85f), Color.Transparent), sun, w * 0.95f), w * 0.95f, sun)
        val dusk = Offset(w * 0.05f, h * 0.80f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF6A2FE0).copy(alpha = 0.42f), Color.Transparent), dusk, w * 0.95f), w * 0.95f, dusk)
        // Line art: rings off the top corner, and a quieter set rising from the bottom.
        val line = Stroke(1.dp.toPx())
        for (i in 1..6) drawCircle(Color.White.copy(alpha = 0.09f), w * (0.16f + 0.13f * i), Offset(w * 1.02f, h * 0.10f), style = line)
        for (i in 1..4) drawCircle(Color.White.copy(alpha = 0.05f), w * (0.20f + 0.14f * i), Offset(-w * 0.08f, h * 1.02f), style = line)
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }
}

/**
 * A tilted wall of covers that fills the top of the welcome screen and fades into the page: three
 * columns, each drifting a little against its neighbour.
 */
@Composable
internal fun CoverWall(modifier: Modifier = Modifier) {
    val reduce = com.music.spotui.ui.look.rememberReduceMotion()
    val drift = if (reduce) 0.5f else {
        val t = rememberInfiniteTransition(label = "coverWall")
        val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
        p
    }
    BoxWithConstraints(modifier) {
        val tile = maxWidth * 0.37f
        val gap = 14.dp
        Row(
            horizontalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .wrapContentSize(align = Alignment.TopCenter, unbounded = true)
                .graphicsLayer { rotationZ = -13f; translationY = -tile.toPx() * 0.55f; translationX = tile.toPx() * 0.10f },
        ) {
            for (col in 0 until 3) {
                val dir = if (col % 2 == 0) 1f else -1f
                val start = floatArrayOf(-0.10f, -0.55f, -0.30f)[col]
                Column(
                    verticalArrangement = Arrangement.spacedBy(gap),
                    modifier = Modifier.graphicsLayer { translationY = tile.toPx() * start + dir * (drift - 0.5f) * 46.dp.toPx() },
                ) {
                    for (row in 0 until 5) Cover(kind = (col * 4 + row * 3) % COVERS, size = tile)
                }
            }
        }
    }
}

private const val COVERS = 9

/** One cover of the wall: art drawn in code on a rounded tile with a glass edge. */
@Composable
private fun Cover(kind: Int, size: Dp) {
    val shape = RoundedCornerShape(size * 0.17f)
    Canvas(
        Modifier.size(size).shadow(18.dp, shape, clip = false).clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape),
    ) { cover(kind) }
}

private fun DrawScope.cover(kind: Int) {
    val w = size.width
    val h = size.height
    fun ground(vararg c: Long) = drawRect(Brush.linearGradient(c.map { Color(it) }, Offset.Zero, Offset(w, h)))
    when (kind) {
        0 -> { // A low sun over dark water.
            ground(0xFFFFB347, 0xFFFF5E62, 0xFF7B2D8E)
            drawCircle(Color(0xFFFFF1C1), w * 0.24f, Offset(w * 0.5f, h * 0.52f))
            for (i in 0 until 5) {
                val y = h * (0.58f + 0.085f * i)
                drawRect(Color(0xFF3A1247).copy(alpha = 0.92f), Offset(0f, y), Size(w, h * (0.022f + 0.012f * i)))
            }
        }
        1 -> { // A record.
            ground(0xFF15131C, 0xFF2A2140)
            val c = Offset(w * 0.5f, h * 0.5f)
            drawCircle(Color(0xFF0B0A10), w * 0.40f, c)
            for (i in 1..6) drawCircle(Color.White.copy(alpha = 0.10f), w * (0.14f + 0.04f * i), c, style = Stroke(w * 0.006f))
            drawCircle(Brush.linearGradient(listOf(OnbOrange, OnbPink), Offset(w * 0.4f, h * 0.4f), Offset(w * 0.6f, h * 0.6f)), w * 0.13f, c)
            drawCircle(Color(0xFF0B0A10), w * 0.025f, c)
        }
        2 -> { // Level bars.
            ground(0xFF6D28D9, 0xFF2E1065)
            val bars = floatArrayOf(0.30f, 0.55f, 0.82f, 0.48f, 0.68f, 0.36f, 0.58f)
            val bw = w * 0.07f
            val step = w * 0.105f
            val x0 = (w - (step * (bars.size - 1) + bw)) / 2f
            bars.forEachIndexed { i, v ->
                val bh = h * 0.62f * v
                drawRoundRect(Color.White.copy(alpha = 0.92f), Offset(x0 + i * step, (h - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f))
            }
        }
        3 -> { // Waves.
            ground(0xFF0EA5A4, 0xFF1D4ED8, 0xFF1E1B4B)
            for (k in 0 until 5) {
                val path = Path()
                val y0 = h * (0.26f + 0.13f * k)
                path.moveTo(0f, y0)
                var x = 0f
                while (x <= w) {
                    path.lineTo(x, y0 + sin(x / w * 2f * PI.toFloat() * 1.5f + k * 0.9f) * h * 0.055f)
                    x += w / 40f
                }
                drawPath(path, Color.White.copy(alpha = 0.85f - 0.13f * k), style = Stroke(w * 0.022f, cap = StrokeCap.Round))
            }
        }
        4 -> { // Three discs on paper.
            ground(0xFFF4E9D8, 0xFFE9D5B5)
            drawCircle(Color(0xFFE4572E).copy(alpha = 0.90f), w * 0.26f, Offset(w * 0.38f, h * 0.40f))
            drawCircle(Color(0xFF2E5EAA).copy(alpha = 0.85f), w * 0.26f, Offset(w * 0.62f, h * 0.46f))
            drawCircle(Color(0xFFF2B134).copy(alpha = 0.88f), w * 0.22f, Offset(w * 0.48f, h * 0.66f))
        }
        5 -> { // Slanted bands.
            ground(0xFFFF4D8D, 0xFFB5179E)
            for (i in -2..6) {
                val x = w * 0.22f * i
                drawLine(Color.White.copy(alpha = if (i % 2 == 0) 0.85f else 0.25f), Offset(x, h * 1.1f), Offset(x + w * 0.6f, -h * 0.1f), w * 0.055f)
            }
        }
        6 -> { // A crescent and a few stars.
            ground(0xFF0B1437, 0xFF1E2A78)
            drawCircle(Color(0xFFFDE9B0), w * 0.27f, Offset(w * 0.52f, h * 0.50f))
            drawCircle(Color(0xFF14205A), w * 0.25f, Offset(w * 0.63f, h * 0.43f))
            val stars = floatArrayOf(0.16f, 0.22f, 0.80f, 0.20f, 0.24f, 0.74f, 0.84f, 0.78f, 0.34f, 0.14f, 0.70f, 0.88f)
            for (i in stars.indices step 2) drawCircle(Color.White.copy(alpha = 0.85f), w * 0.012f, Offset(w * stars[i], h * stars[i + 1]))
        }
        7 -> { // A field of dots, growing.
            ground(0xFF134E4A, 0xFF16A34A, 0xFFBEF264)
            for (r in 0 until 6) for (c in 0 until 6) {
                val k = (r + c) / 10f
                drawCircle(Color.White.copy(alpha = 0.30f + 0.6f * k), w * (0.012f + 0.045f * k), Offset(w * (0.14f + 0.144f * c), h * (0.14f + 0.144f * r)))
            }
        }
        else -> { // Arches.
            ground(0xFFFDE2C4, 0xFFF7B787)
            val colors = longArrayOf(0xFFB23A48, 0xFFE4572E, 0xFFF2B134, 0xFF2E5EAA, 0xFF1D1036)
            colors.forEachIndexed { i, c ->
                val r = w * (0.44f - 0.075f * i)
                drawCircle(Color(c), r, Offset(w * 0.5f, h * 0.86f))
            }
            drawRect(Brush.linearGradient(listOf(Color(0xFFFDE2C4), Color(0xFFF7B787)), Offset(0f, h * 0.86f), Offset(w, h)), Offset(0f, h * 0.86f), Size(w, h * 0.14f))
        }
    }
}

/** The app's mark on the first launch: level bars on a disc of the gradient. */
@Composable
internal fun OnbMark(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(OnbOrange, OnbPink, OnbPurple)))) {
        val w = this.size.width
        val bars = floatArrayOf(0.26f, 0.50f, 0.34f)
        val bw = w * 0.11f
        bars.forEachIndexed { i, v ->
            val bh = w * v
            drawRoundRect(Color.White, Offset(w * (0.305f + 0.14f * i), (w - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f))
        }
    }
}

/** A few level bars in the gradient, set in a line of the headline. */
@Composable
private fun WaveGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val bars = floatArrayOf(0.35f, 0.70f, 1f, 0.55f, 0.85f, 0.40f)
        val step = size.width / bars.size
        val bw = step * 0.5f
        bars.forEachIndexed { i, v ->
            val bh = size.height * v
            val t = i / (bars.size - 1f)
            val c = if (t < 0.5f) androidx.compose.ui.graphics.lerp(OnbOrange, OnbPink, t * 2f) else androidx.compose.ui.graphics.lerp(OnbPink, OnbPurple, (t - 0.5f) * 2f)
            drawRoundRect(c, Offset(i * step + (step - bw) / 2f, (size.height - bh) / 2f), Size(bw, bh), CornerRadius(bw / 2f))
        }
    }
}

/**
 * The first launch's headline: [text] heavy and tight, its word [em] set in a light italic serif,
 * and (with [wave]) the level bars after the last word.
 */
@Composable
internal fun OnbHeadline(text: String, em: String, modifier: Modifier = Modifier, size: TextUnit = 44.sp, wave: Boolean = false) {
    val at = if (em.isEmpty()) -1 else text.indexOf(em)
    val emStyle = SpanStyle(
        fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal,
        brush = Brush.linearGradient(listOf(Color(0xFFFFE1C4), Color(0xFFFFB07A))),
    )
    val string = buildAnnotatedString {
        if (at < 0) append(text) else {
            append(text.substring(0, at))
            withStyle(emStyle) { append(em) }
            append(text.substring(at + em.length))
        }
        if (wave) { append(" "); appendInlineContent("wave", "~") }
    }
    val inline = if (!wave) emptyMap() else mapOf(
        "wave" to InlineTextContent(Placeholder(size * 1.05f, size * 0.62f, PlaceholderVerticalAlign.TextCenter)) { WaveGlyph(Modifier.fillMaxSize()) },
    )
    Text(
        string, inlineContent = inline, color = Color.White, fontFamily = AppSans, fontWeight = FontWeight.ExtraBold,
        fontSize = size, lineHeight = size * 1.1f, letterSpacing = (-0.8).sp, modifier = modifier,
    )
}

/**
 * The first launch's button. [slide] under 1: a glass track with the gradient pill filling that
 * part of it and arrows running on to the end (the welcome screen's); 1: the pill alone.
 * [focused]: a TV remote's focus is on it.
 */
@Composable
internal fun OnboardingCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, slide: Float = 1f, focused: Boolean = false) {
    val track = slide < 1f
    Box(
        modifier
            .pressable(0.97f, onClick = { com.music.spotui.di.Haptics.click(); onClick() })
            .then(if (track) Modifier.onbGlass(CircleShape, fill = 0.09f, edge = 0.18f).padding(6.dp) else Modifier)
            .then(if (focused) Modifier.border(3.dp, Color.White, CircleShape) else Modifier),
    ) {
        if (track) Row(
            horizontalArrangement = Arrangement.spacedBy((-12).dp), verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
        ) {
            for (a in floatArrayOf(0.18f, 0.32f, 0.5f)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = a), modifier = Modifier.size(26.dp))
            }
        }
        Row(
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight().fillMaxWidth(slide).shadow(if (track) 10.dp else 0.dp, CircleShape, clip = false)
                .clip(CircleShape).background(OnbBrush)
                .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        ) {
            Text(text, color = Color.White, fontFamily = AppSans, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Row(horizontalArrangement = Arrangement.spacedBy((-13).dp), modifier = Modifier.padding(start = 6.dp)) {
                for (a in floatArrayOf(0.45f, 0.72f, 1f)) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White.copy(alpha = a), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
