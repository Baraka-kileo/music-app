package com.music.spotui.ui.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The design system: the one place the app's colours, shapes and spacing are named. Screens take
 * their values from here (and their icons from [AppIcons]) so they read as one app.
 *
 * Colour is kept to a dark page, three levels of glass, one gradient ("Ember") and one red. The
 * gradient marks only what is on, selected or the main action of a screen.
 */
object Ds {
    // Colour.
    val EmberStart = Color(0xFFFF6B2C)
    val EmberMid = Color(0xFFE5467C)
    val EmberEnd = Color(0xFF7C3DF2)
    val Ember: Brush get() = Brush.horizontalGradient(listOf(EmberStart, EmberMid, EmberEnd))

    /**
     * What "on" and "selected" are drawn with: the Ember gradient, or the plain accent colour once
     * the user (Settings > Display) or an extension's look has chosen one.
     */
    val Accent: Brush
        get() {
            val chosen = com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.ACCENT)
                ?: com.music.spotui.data.api.SupportReminder.run { if (immersiveAccentChosen) immersiveAccent.color else null }
            return chosen?.let { SolidColor(it) } ?: Ember
        }

    val Glass1 = Color.White.copy(alpha = 0.06f)
    val Glass2 = Color.White.copy(alpha = 0.10f)
    val Glass3 = Color.White.copy(alpha = 0.16f)
    val Edge = Color.White.copy(alpha = 0.12f)
    val Text = Color.White
    val Text2 = Color.White.copy(alpha = 0.70f)
    val Text3 = Color.White.copy(alpha = 0.46f)
    val Danger = Color(0xFFFF5A5F)

    // Shape: five corner sizes and no others.
    val Tile: Shape = RoundedCornerShape(11.dp)
    val Control: Shape = RoundedCornerShape(16.dp)
    val Card: Shape = RoundedCornerShape(22.dp)
    val Sheet: Shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val Pill: Shape = CircleShape

    // Spacing, in steps of 4.
    val SpaceXs = 4.dp
    val SpaceS = 8.dp
    val SpaceM = 12.dp
    val SpaceL = 16.dp
    val SpaceXl = 24.dp
}

/** A glass surface: a faint white fill with a hairline edge. */
fun Modifier.dsGlass(shape: Shape, fill: Color = Ds.Glass1, edge: Color = Ds.Edge): Modifier =
    this.clip(shape).background(fill).border(1.dp, edge, shape)

/** A grouped card: glass, or an extension look's own surface colour when it sets one. */
@Composable
fun Modifier.dsCard(): Modifier {
    val own = com.music.spotui.extensions.ExtensionLook.color(app.myspoti.extensions.UiStyle.SURFACE)
    return if (own != null) this.clip(Ds.Card).background(own) else this.dsGlass(Ds.Card)
}

/**
 * The light behind a page: a little of the Ember colours in two corners, so glass cards have
 * something to sit on. Nothing is drawn when [on] is false (pure black for OLED, an extension's look).
 */
fun Modifier.dsAmbient(on: Boolean = true): Modifier = if (!on) this else drawBehind {
    val w = size.width
    val warm = Offset(w * 1.0f, 0f)
    drawCircle(Brush.radialGradient(listOf(Ds.EmberStart.copy(alpha = 0.20f), Color.Transparent), warm, w * 0.95f), w * 0.95f, warm)
    val cool = Offset(0f, size.height * 0.92f)
    drawCircle(Brush.radialGradient(listOf(Ds.EmberEnd.copy(alpha = 0.16f), Color.Transparent), cool, w * 1.0f), w * 1.0f, cool)
}

/** An icon on its tile: the way every row and tile of the app shows one. */
@Composable
fun DsIconTile(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Ds.Text, size: Dp = 36.dp) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size).dsGlass(Ds.Tile, fill = Ds.Glass2)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.56f))
    }
}

/** The app's switch (drawn only: the row that holds it takes the tap). On is the accent. */
@Composable
fun DsSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val on by animateFloatAsState(if (checked) 1f else 0f, spring(stiffness = 500f), label = "dsSwitchOn")
    val x by animateDpAsState(if (checked) 23.dp else 3.dp, spring(dampingRatio = 0.7f, stiffness = 500f), label = "dsSwitchThumb")
    val accent = Ds.Accent
    Box(
        modifier.size(width = 50.dp, height = 30.dp).graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(Ds.Pill).background(Ds.Glass3),
    ) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = on }.background(accent))
        Box(Modifier.align(Alignment.CenterStart).offset(x = x).size(24.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
    }
}

/** A filled disc with a check: the selected choice of a list, and "done". */
@Composable
fun DsCheck(selected: Boolean, modifier: Modifier = Modifier) {
    val on by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dsCheck")
    val accent = Ds.Accent
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(24.dp).graphicsLayer { alpha = on; scaleX = 0.6f + 0.4f * on; scaleY = 0.6f + 0.4f * on }
            .clip(CircleShape).background(accent),
    ) { Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)) }
}
