package com.music.spotui.ui.screens

import com.music.spotui.ui.theme.tone
import com.music.spotui.ui.theme.AppText

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.ui.components.pressable
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.theme.AppSans
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * First screen of an app with no account in place: a wall of covers under a warm light, what the
 * app is in one headline and one line, then the music account to use ([AccountChoiceScreen]; on a
 * TV, Spotify's own login page, shown by the engine), once [onLogin] is tapped. The wall, the
 * headline, the line and the button come in one after the other.
 */
@Composable
fun WelcomeScreen(onLogin: () -> Unit) {
    // One entrance per part, a beat apart: wall and mark, headline, line, button.
    val parts = remember { List(4) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        parts.forEachIndexed { i, a -> launch { delay(150L + i * 180L); a.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) } }
    }
    fun Modifier.rise(i: Int) = graphicsLayer {
        val v = parts[i].value
        alpha = v
        translationY = (1f - v) * 24.dp.toPx()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF09060F))) {
        OnboardingBackdrop()
        CoverWall(Modifier.fillMaxSize().graphicsLayer { alpha = parts[0].value })
        // The wall sinks into the page where the words are, and dims under the status bar.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color(0x8C0D0714), 0.13f to Color.Transparent, 0.34f to Color.Transparent,
                    0.55f to Color(0xEB160B28), 0.70f to Color(0xFF0F0819), 1f to Color(0xFF09060F),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopStart).rise(0).onbGlass(RoundedCornerShape(50), fill = 0.16f, edge = 0.22f)
                    .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            ) {
                OnbMark(30.dp)
                Text(stringResource(R.string.brand_name), color = Color.White, fontFamily = AppSans, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    modifier = Modifier.padding(start = 9.dp))
            }
            Column(Modifier.fillMaxWidth().align(Alignment.BottomStart)) {
                OnbHeadline(
                    stringResource(R.string.welcome_headline), stringResource(R.string.welcome_headline_em),
                    Modifier.rise(1), wave = true,
                )
                Text(
                    stringResource(R.string.welcome_body), color = Color.White.copy(alpha = 0.74f), fontFamily = AppSans,
                    fontSize = 16.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 14.dp, end = 24.dp).rise(2),
                )
                Spacer(Modifier.height(28.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().rise(3)) {
                    // A TV remote: the button takes the focus at once and shows it (a white ring); a
                    // focus with no look left nothing on screen to say what OK would do.
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val tv = remember { com.music.spotui.ui.tv.Tv.isTv(ctx) }
                    // The account is chosen before any sign-in: Spotify's is no longer the first thing asked.
                    val choosesAccount = Onboarding.welcomeChoosesAccount(tv)
                    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
                    var focused by remember { mutableStateOf(false) }
                    if (tv) LaunchedEffect(Unit) { delay(300); runCatching { focus.requestFocus() } }
                    OnboardingCta(
                        stringResource(if (choosesAccount) R.string.welcome_start else R.string.welcome_login), onLogin,
                        Modifier.fillMaxWidth().height(68.dp)
                            .then(if (tv) Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused } else Modifier),
                        slide = 0.70f, focused = focused,
                    )
                    // Or the whole setup of another device of the user's, by its QR code (same Wi-Fi).
                    var passing by remember { mutableStateOf(false) }
                    var passFocused by remember { mutableStateOf(false) }
                    Text(
                        stringResource(R.string.pass_sign_in_with_device),
                        color = Color.White.copy(alpha = 0.86f), fontFamily = AppSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp)
                            .onFocusChanged { passFocused = it.isFocused }
                            .pressable(0.96f, onClick = { passing = true })
                            .clip(RoundedCornerShape(50))
                            // The remote's focus shows on a TV, as on the button above.
                            .then(if (passFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(50)) else Modifier)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                    if (passing) com.music.spotui.pass.PassReceiveDialog(onDismiss = { passing = false })
                    // Spotify's sign-in is the account choice's to explain when it is not the next page.
                    if (!choosesAccount) Text(
                        stringResource(R.string.welcome_privacy),
                        color = Color.White.copy(alpha = 0.5f), fontFamily = AppSans, fontSize = 13.sp, lineHeight = 18.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

/** A soft green light behind the logo, drifting slowly. */
@Composable
internal fun Glow(modifier: Modifier = Modifier, alpha: Float = 0.30f) {
    // "Remove animations": the light stays where it starts.
    val reduce = com.music.spotui.ui.look.rememberReduceMotion()
    val phase = if (reduce) 0f else {
        val t = rememberInfiniteTransition(label = "glow")
        val p by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "phase")
        p
    }
    Canvas(modifier) {
        val c = Offset(size.width * (0.5f + 0.12f * sin(phase)), size.height * (0.30f + 0.05f * cos(phase)))
        val r = size.width * 1.05f
        drawCircle(Brush.radialGradient(listOf(AppAccent.copy(alpha = alpha), Color.Transparent), c, r), r, c)
    }
}
