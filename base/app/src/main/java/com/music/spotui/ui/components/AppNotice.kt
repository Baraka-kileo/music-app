package com.music.spotui.ui.components

import android.os.Handler
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.scaleIn
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.music.spotui.data.entity.SongsModel
import com.music.spotui.di.Haptics
import com.music.spotui.ui.navigation.PlayerSheet
import kotlinx.coroutines.delay

/**
 * The app's own notification: a glass pill that springs up above the mini player (above the
 * player's controls while the player is open), with the song's cover or an icon, one or two lines
 * and at most one action. It replaces the system toast (the app icon and a grey line) for what the
 * user just did. A new one replaces the one shown; a swipe down closes it. Any thread.
 */
object AppNotice {
    enum class Tone { NORMAL, LIKE, SUCCESS, ERROR }

    data class Item(
        val id: Long,
        val title: String,
        val subtitle: String?,
        val icon: ImageVector?,
        val coverUrl: String?,
        val tone: Tone,
        val actionLabel: String?,
        val onAction: (() -> Unit)?,
    )

    internal val current = mutableStateOf<Item?>(null)

    /** The song whose "Save to" sheet an action asked for (the host draws the sheet). */
    internal val saveTo = mutableStateOf<SongsModel?>(null)

    private val main = Handler(Looper.getMainLooper())
    private var seq = 0L

    fun show(
        title: String,
        subtitle: String? = null,
        icon: ImageVector? = null,
        coverUrl: String? = null,
        tone: Tone = Tone.NORMAL,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        main.post {
            current.value = Item(++seq, title, subtitle, icon, coverUrl?.takeIf { it.isNotBlank() }, tone, actionLabel, onAction)
            when (tone) {
                Tone.ERROR -> Haptics.thud()
                Tone.LIKE, Tone.SUCCESS -> Haptics.confirm()
                Tone.NORMAL -> Haptics.tick()
            }
        }
    }

    /** A plain message (what a system toast said before). */
    fun text(text: String, tone: Tone = Tone.NORMAL) = show(title = text, tone = tone)

    /** Opens the "Save to" sheet for [song] (where the song is saved, ticks to change it). */
    fun openSaveTo(song: SongsModel) { main.post { current.value = null; saveTo.value = song } }

    fun dismiss() { main.post { current.value = null } }
}

private const val SHOWN_MS = 3200L

/** Draws [AppNotice]: put once over the whole app (both looks). */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AppNoticeHost() {
    val item = AppNotice.current.value
    // The last item stays drawn while the pill slides away.
    var shown by remember { mutableStateOf<AppNotice.Item?>(null) }
    if (item != null) shown = item
    LaunchedEffect(item?.id) {
        val id = item?.id ?: return@LaunchedEffect
        delay(SHOWN_MS)
        if (AppNotice.current.value?.id == id) AppNotice.current.value = null
    }
    // Where: over the full player it drops from the top, under the handle, so it never covers the
    // controls the user is touching; everywhere else it rises just above the mini player, near the
    // thumb and the thing that was just done (as on most music apps).
    val playerOpen = PlayerSheet.open.value
    Box(
        if (playerOpen) Modifier.fillMaxSize().statusBarsPadding().padding(top = 28.dp, start = 16.dp, end = 16.dp)
        else Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 140.dp, start = 16.dp, end = 16.dp),
        contentAlignment = if (playerOpen) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = item != null,
            enter = slideInVertically(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) { if (playerOpen) -it else it } +
                fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.92f),
            exit = slideOutVertically(tween(220)) { if (playerOpen) -it else it } + fadeOut(tween(180)),
        ) {
            val n = shown ?: return@AnimatedVisibility
            NoticePill(n)
        }
    }

    val song = AppNotice.saveTo.value
    if (song != null) {
        val context = LocalContext.current
        SavedInSheet(song = song, context = context, onDismiss = { AppNotice.saveTo.value = null })
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun NoticePill(n: AppNotice.Item) {
    val accent = com.music.spotui.ui.immersive.Immersive.Accent
    // A floating glass capsule: deep smoky fill with a faint accent wash from the left, a hairline
    // light edge on top, and a soft shadow so it floats over covers and videos alike.
    val shape = RoundedCornerShape(50)
    var dragged by remember(n.id) { mutableStateOf(0f) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .wrapContentWidth()
            .pointerInput(n.id) {
                detectVerticalDragGestures(
                    onDragEnd = { if (kotlin.math.abs(dragged) > 24.dp.toPx()) AppNotice.dismiss(); dragged = 0f },
                    onVerticalDrag = { _, dy -> dragged += dy },
                )
            }
            .shadow(18.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(Color(0xF2141418))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), endX = 420f))
            .border(0.6.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f))), shape)
            .padding(start = 7.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
    ) {
        val lead = Modifier.size(38.dp).clip(RoundedCornerShape(50))
        if (n.coverUrl != null) {
            Box(lead.background(Color.White.copy(alpha = 0.08f))) {
                GlideImage(model = n.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (n.icon != null) Box(
                    Modifier.align(Alignment.BottomEnd).size(16.dp).clip(RoundedCornerShape(50)).background(accent),
                    contentAlignment = Alignment.Center,
                ) { Icon(n.icon, null, tint = Color.White, modifier = Modifier.size(11.dp)) }
            }
        } else {
            val bg = when (n.tone) {
                AppNotice.Tone.ERROR -> Color(0xFF8A2E36)
                AppNotice.Tone.LIKE, AppNotice.Tone.SUCCESS -> accent
                AppNotice.Tone.NORMAL -> Color.White.copy(alpha = 0.12f)
            }
            Box(lead.background(bg), contentAlignment = Alignment.Center) {
                Icon(
                    n.icon ?: when (n.tone) {
                        AppNotice.Tone.ERROR -> com.music.spotui.ui.design.AppIcons.Info
                        AppNotice.Tone.LIKE -> com.music.spotui.ui.design.AppIcons.Star
                        else -> com.music.spotui.ui.design.AppIcons.Check
                    },
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(n.title, color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!n.subtitle.isNullOrBlank()) Text(
                n.subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (n.actionLabel != null && n.onAction != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                n.actionLabel,
                color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.14f))
                    .clickable { Haptics.click(); AppNotice.dismiss(); n.onAction.invoke() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
