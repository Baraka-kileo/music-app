package com.music.spotui.ui.screens

import com.music.spotui.ui.theme.tone
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppAccent

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import app.myspoti.power.BatteryExemption
import com.music.spotui.data.local.LocalMusic
import com.music.spotui.ui.theme.AppBackground
import com.music.spotui.ui.immersive.Immersive
import com.music.spotui.ui.navigation.isImmersive

/**
 * Shown once the music account is in place (signed in to Spotify, or another account chosen): what
 * Myspoti needs from the phone, each with its own button. Nothing is required; "Continue" goes on
 * with whatever was allowed.
 */
object Setup {
    private const val KEY = "SetupDone"
    private const val EXTENSIONS_KEY = "SetupExtensionsSeen"
    private const val ACCOUNT_KEY = "SetupAccountSeen"
    /** The permissions step, the sound quality one, the look one, the music account one or the extensions one (each new step is shown once after the update too). */
    fun needed(context: Context) = !permissionsDone(context) || !com.music.spotui.data.preferences.isQualityChosen(context) ||
        !com.music.spotui.data.preferences.LookPref.isChosen(context) || !extensionsSeen(context) || !accountSeen(context)
    fun accountSeen(context: Context) = prefs(context).getBoolean(ACCOUNT_KEY, false)
    fun setAccountSeen(context: Context) = prefs(context).edit().putBoolean(ACCOUNT_KEY, true).apply()
    fun permissionsDone(context: Context) = prefs(context).getBoolean(KEY, false)
    fun done(context: Context) = prefs(context).edit().putBoolean(KEY, true).apply()
    fun extensionsSeen(context: Context) = prefs(context).getBoolean(EXTENSIONS_KEY, false)
    /**
     * The steps still to show, in order: how Myspoti works (first setup only), the look, the music
     * account (only for someone who has not chosen it: a new install chooses it before signing in,
     * see [Onboarding]), the sound quality, the permissions, then the extensions (last, so the end of
     * the setup leads straight to their page); each skipped when done before an update.
     */
    internal fun steps(context: Context): List<Int> {
        val needPerms = !permissionsDone(context)
        return listOfNotNull(
            // No "How it works" step: the welcome screen says it before the sign-in.
            STEP_LOOK.takeIf { !com.music.spotui.data.preferences.LookPref.isChosen(context) },
            STEP_ACCOUNT.takeIf { !accountSeen(context) },
            STEP_QUALITY.takeIf { !com.music.spotui.data.preferences.isQualityChosen(context) },
            STEP_PERMISSIONS.takeIf { needPerms },
            STEP_EXTENSIONS.takeIf { !extensionsSeen(context) },
        ).ifEmpty { listOf(STEP_PERMISSIONS) }
    }
    fun setExtensionsSeen(context: Context) = prefs(context).edit().putBoolean(EXTENSIONS_KEY, true).apply()
    private fun prefs(context: Context) = context.getSharedPreferences("myspoti_prefs", Context.MODE_PRIVATE)

    /** A page to open once the app shows (the extensions step's "See the extensions"); taken by [App]. */
    var openAfter: String? = null
}

private fun granted(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    // The steps of [Setup.steps], fixed for this run. Every step after the look is drawn in the look chosen there.
    val order = remember { Setup.steps(context) }
    var index by remember { mutableIntStateOf(0) }
    val steps = order.size
    fun next() { if (index + 1 < order.size) index++ else onDone() }
    val reduce = com.music.spotui.ui.look.rememberReduceMotion()
    // Back goes to the previous step (the look can be changed again from there).
    androidx.activity.compose.BackHandler(enabled = index > 0) { index-- }
    val immersive = isImmersive()
    Box(Modifier.fillMaxSize().background(if (immersive) Immersive.Page else AppBackground)) {
        // The first launch's page, dimmed: the steps are for reading.
        OnboardingBackdrop(dim = 0.45f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Several steps to go through: dots say where the user is.
            if (steps > 1) StepHeader(count = steps, current = index, onBack = { index-- })
            // One step slides away to the left as the next comes in from the right.
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    if (reduce) EnterTransition.None togetherWith ExitTransition.None
                    else (slideInHorizontally(tween(420, easing = FastOutSlowInEasing)) { it / 2 } + fadeIn(tween(420))) togetherWith
                        (slideOutHorizontally(tween(420, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(250)))
                },
                label = "setupStep",
                modifier = Modifier.weight(1f),
            ) { i ->
                when (order[i]) {
                    STEP_INTRO -> IntroStep(onNext = ::next)
                    STEP_QUALITY -> QualityStep(onNext = {
                        com.music.spotui.data.preferences.setQualityChosen(context)
                        next()
                    })
                    STEP_LOOK -> com.music.spotui.ui.look.LookStep(onNext = {
                        com.music.spotui.data.preferences.LookPref.setChosen(context)
                        next()
                    })
                    STEP_ACCOUNT -> AccountStep(onNext = { Setup.setAccountSeen(context); next() })
                    STEP_EXTENSIONS -> ExtensionsStep(
                        onBrowse = {
                            Setup.setExtensionsSeen(context)
                            Setup.openAfter = com.music.spotui.ui.navigation.Routes.Extensions.route
                            next()
                        },
                        onLater = { Setup.setExtensionsSeen(context); next() },
                    )
                    else -> PermissionsStep(onNext = { Setup.done(context); next() })
                }
            }
        }
    }
}

internal const val STEP_INTRO = 0
internal const val STEP_LOOK = 1
internal const val STEP_QUALITY = 2
internal const val STEP_EXTENSIONS = 3
internal const val STEP_PERMISSIONS = 4
internal const val STEP_ACCOUNT = 5

/**
 * The first launch, before any sign-in: the music account, on the setup's backdrop. [onDone] once
 * chosen (YouTube Music signed in on its own page first); back returns to the welcome screen.
 */
@Composable
fun AccountChoiceScreen(onBack: () -> Unit, onDone: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val immersive = isImmersive()
    Box(Modifier.fillMaxSize().background(Color(0xFF09060F))) {
        OnboardingBackdrop(dim = 0.38f)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp).size(44.dp).onbGlass(CircleShape, fill = 0.12f, edge = 0.18f).clickable(onClick = onBack),
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Box(Modifier.weight(1f)) { AccountStep(onNext = onDone, beforeSignIn = true) }
        }
    }
}

/**
 * The music account: Spotify, YouTube Music (its own sign-in page, from here) or none. The same
 * choice as Settings > Music account. [beforeSignIn]: the first launch's, before any sign-in (Spotify's
 * comes next; YouTube Music's is asked on Continue while signed out); else Spotify is the account the
 * user signed in with, for someone who had not seen this step yet.
 */
@Composable
private fun AccountStep(onNext: () -> Unit, beforeSignIn: Boolean = false) {
    val context = LocalContext.current
    val pref = com.music.spotui.data.preferences.AccountProviderPref
    val ytm = com.music.spotui.di.YtmRuntime
    var signingIn by remember { mutableStateOf(false) }
    // Continue with YouTube Music while signed out: its sign-in first, the next step once signed in.
    var nextOnceSignedIn by remember { mutableStateOf(false) }
    if (nextOnceSignedIn) LaunchedEffect(ytm.signedIn, pref.id) {
        if (pref.id != app.myspoti.api.account.AccountProviders.YTMUSIC) nextOnceSignedIn = false
        else if (ytm.signedIn) { nextOnceSignedIn = false; onNext() }
    }
    val next: () -> Unit = {
        if (beforeSignIn && Onboarding.afterChoice(pref.id, ytm.signedIn) == AfterChoice.YTM_SIGN_IN) {
            nextOnceSignedIn = true
            signingIn = true
        } else onNext()
    }
    val providers = app.myspoti.api.account.AccountProviders
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var row = 0
            OnbHeadline(stringResource(R.string.setup_account_title), stringResource(R.string.onb_account_em), Modifier.riseIn(row++), size = 36.sp)
            Text(
                stringResource(R.string.setup_account_subtitle), color = Color.White.copy(alpha = 0.72f),
                fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.riseIn(row++),
            )
            Spacer(Modifier.height(10.dp))
            for (id in providers.OFFERED) {
                val selected = pref.id == id
                val detail = when (id) {
                    providers.SPOTIFY ->
                        stringResource(if (beforeSignIn) R.string.setup_account_spotify_next else R.string.setup_account_spotify_detail)
                    providers.YTMUSIC ->
                        if (ytm.signedIn) stringResource(R.string.settings_ytm_signed_in) else stringResource(R.string.setup_account_ytm_detail)
                    else -> stringResource(R.string.setup_account_none_detail)
                }
                // A coloured tile per choice (plain icons, no service's logo).
                val icon = when (id) {
                    providers.SPOTIFY -> Icons.Rounded.GraphicEq
                    providers.YTMUSIC -> Icons.Rounded.PlayArrow
                    else -> Icons.Rounded.Headphones
                }
                val tile = when (id) {
                    providers.SPOTIFY -> listOf(Color(0xFF2BE07A), Color(0xFF0A8A45))
                    providers.YTMUSIC -> listOf(Color(0xFFFF5A5A), Color(0xFFB0122B))
                    else -> listOf(Color(0xFF9A95B5), Color(0xFF4B4664))
                }
                val shape = RoundedCornerShape(24.dp)
                val fill by animateColorAsState(Color.White.copy(alpha = if (selected) 0.15f else 0.07f), tween(250), label = "accountFill")
                Column(
                    Modifier.riseIn(row++).fillMaxWidth().clip(shape).background(fill)
                        .then(if (selected) Modifier.border(1.5.dp, OnbBrush, shape) else Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape))
                        .clickable { pref.set(context, id) }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(tile)),
                        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text(com.music.spotui.ui.components.accountProviderLabel(id), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text(detail, color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(26.dp).clip(CircleShape)
                                .then(if (selected) Modifier.background(OnbBrush) else Modifier.border(1.5.dp, Color.White.copy(alpha = 0.32f), CircleShape)),
                        ) { if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp)) }
                    }
                    if (selected && id == providers.YTMUSIC && !ytm.signedIn) {
                        TextButton(onClick = { signingIn = true }) {
                            Text(stringResource(R.string.settings_ytm_sign_in_title), color = Color(0xFFFFB07A), fontWeight = FontWeight.Bold)
                        }
                    }
                    // The listen mirror, offered with the provider it counts on (off unless ticked). Not on
                    // the first launch, before any sign-in: it stays in Settings > Music account.
                    if (!beforeSignIn && selected && id != providers.NONE) {
                        val mirror = com.music.spotui.data.preferences.ConnectMirrorPref
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 10.dp).fillMaxWidth().clickable { mirror.set(context, !mirror.on) },
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = mirror.on,
                                onCheckedChange = { mirror.set(context, it) },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = OnbPink),
                            )
                            Column(Modifier.weight(1f)) {
                                CardTitle(stringResource(R.string.settings_connect_mirror_title))
                                CardDetail(stringResource(R.string.settings_connect_mirror_detail))
                            }
                        }
                    }
                }
            }
        }
        OnboardingCta(stringResource(R.string.setup_continue), next, Modifier.fillMaxWidth().height(58.dp).riseIn(5))
        Spacer(Modifier.height(16.dp))
    }
    if (signingIn) com.music.spotui.ui.components.YtmSignInDialog(onDismiss = { signingIn = false })
}

/** Where the user is in the steps: the current one a longer green dash. */
@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        for (i in 0 until count) {
            val reduce = com.music.spotui.ui.look.rememberReduceMotion()
            val w by animateDpAsState(if (i == current) 22.dp else 8.dp, if (reduce) snap() else tween(350), label = "dot")
            val on = if (isImmersive()) Color.White else AppAccent
            val c by animateColorAsState(if (i == current) on else AppText.copy(alpha = 0.25f), if (reduce) snap() else tween(350), label = "dotColor")
            Box(Modifier.height(6.dp).width(w).clip(RoundedCornerShape(50)).background(c))
        }
    }
}

/** Where the user is: a back arrow after the first step, one bar per step, and "2 / 5". */
@Composable
private fun StepHeader(count: Int, current: Int, onBack: () -> Unit) {
    val reduce = com.music.spotui.ui.look.rememberReduceMotion()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 8.dp).height(48.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (current > 0) androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, tint = Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            for (i in 0 until count) {
                val a by androidx.compose.animation.core.animateFloatAsState(
                    if (i <= current) 1f else 0f, if (reduce) snap() else tween(350), label = "stepBar",
                )
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = a }.background(OnbBrush))
                }
            }
        }
        Text("${current + 1} / $count", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(start = 14.dp))
    }
}

/** Each part rising into place a beat after the one above it. */
@Composable
private fun Modifier.riseIn(index: Int): Modifier {
    val a = remember { Animatable(0f) }
    val reduce = com.music.spotui.ui.look.rememberReduceMotion()
    LaunchedEffect(Unit) { if (reduce) a.snapTo(1f) else { delay(80L + index * 70L); a.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) } }
    return graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 18.dp.toPx() }
}

@Composable
private fun PermissionsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    // Re-read when coming back from a system dialog or the settings.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(800); tick++ } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var row = 0
        SetupTitle(stringResource(R.string.setup_title), Modifier.riseIn(row++))
        SetupSubtitle(stringResource(R.string.setup_subtitle), Modifier.riseIn(row++))
        Spacer(Modifier.height(8.dp))
        @Suppress("UNUSED_EXPRESSION") tick
        if (Build.VERSION.SDK_INT >= 33) SetupRow(
            stringResource(R.string.setup_notifications_title), stringResource(R.string.setup_notifications_detail),
            granted(context, Manifest.permission.POST_NOTIFICATIONS), Modifier.riseIn(row++),
        ) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        SetupRow(
            stringResource(R.string.setup_local_title), stringResource(R.string.setup_local_detail),
            LocalMusic.hasPermission(context), Modifier.riseIn(row++),
        ) { launcher.launch(LocalMusic.permission) }
        if (Build.VERSION.SDK_INT >= 31) SetupRow(
            stringResource(R.string.setup_bluetooth_title), stringResource(R.string.setup_bluetooth_detail),
            granted(context, Manifest.permission.BLUETOOTH_CONNECT), Modifier.riseIn(row++),
        ) { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
        SetupRow(
            stringResource(R.string.setup_battery_title), stringResource(R.string.setup_battery_detail),
            BatteryExemption.isExempt(context), Modifier.riseIn(row++),
        ) { BatteryExemption.request(context) }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.riseIn(row)) {
            SetupButton(stringResource(R.string.setup_continue), onNext)
        }
    }
}

/** Onboarding, step one: how Myspoti streams and saves music (changed later in Settings). */
@Composable
private fun QualityStep(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetupTitle(stringResource(R.string.setup_quality_title), Modifier.padding(horizontal = 20.dp).riseIn(0))
        SetupSubtitle(stringResource(R.string.setup_quality_subtitle), Modifier.padding(horizontal = 20.dp).riseIn(1))
        Box(Modifier.riseIn(2)) { com.music.spotui.ui.components.StreamingQualityPicker(Modifier.padding(top = 8.dp)) }
        Box(Modifier.riseIn(3)) { com.music.spotui.ui.components.DownloadQualityPicker() }
        SetupButton(stringResource(R.string.setup_continue), onNext, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(56.dp).riseIn(4))
    }
}

/** First setup, step one: what Myspoti is made of, in three cards. */
@Composable
private fun IntroStep(onNext: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var row = 0
        SetupTitle(stringResource(R.string.setup_intro_title), Modifier.riseIn(row++))
        SetupSubtitle(stringResource(R.string.setup_intro_subtitle), Modifier.riseIn(row++))
        Spacer(Modifier.height(8.dp))
        InfoRow(Icons.Rounded.GraphicEq, stringResource(R.string.setup_intro_spotify_title), stringResource(R.string.setup_intro_spotify_detail), Modifier.riseIn(row++))
        InfoRow(Icons.Rounded.AutoAwesome, stringResource(R.string.setup_intro_more_title), stringResource(R.string.setup_intro_more_detail), Modifier.riseIn(row++))
        InfoRow(Icons.Rounded.Extension, stringResource(R.string.setup_intro_ext_title), stringResource(R.string.setup_intro_ext_detail), Modifier.riseIn(row++))
        Spacer(Modifier.height(12.dp))
        // Or the whole setup of another device of the user's, by its QR code (same Wi-Fi): the app
        // starts again with it, and only what is this device's own (its permissions) is asked.
        var passing by remember { mutableStateOf(false) }
        Column(Modifier.riseIn(row), horizontalAlignment = Alignment.CenterHorizontally) {
            SetupButton(stringResource(R.string.setup_continue), onNext, Modifier.fillMaxWidth().height(56.dp))
            SetupLinkButton(stringResource(R.string.pass_receive), { passing = true }, Modifier.fillMaxWidth().height(48.dp))
        }
        if (passing) com.music.spotui.pass.PassReceiveDialog(onDismiss = { passing = false })
    }
}

/**
 * What extensions are, shown once to everyone (new installs and updates) as the last step: optional,
 * written by the community, permission-checked, and where downloads come from (community
 * extensions, installed from the store). "See the extensions" ends the setup on their page (the
 * Store, in the chosen look); "Skip to Home" ends it on Home.
 */
@Composable
private fun ExtensionsStep(onBrowse: () -> Unit, onLater: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var row = 0
        SetupTitle(stringResource(R.string.setup_ext_title), Modifier.riseIn(row++))
        SetupSubtitle(stringResource(R.string.setup_ext_subtitle), Modifier.riseIn(row++))
        Spacer(Modifier.height(8.dp))
        InfoRow(Icons.Rounded.Download, stringResource(R.string.setup_ext_download_title), stringResource(R.string.setup_ext_download_detail), Modifier.riseIn(row++))
        InfoRow(Icons.Rounded.Headphones, stringResource(R.string.setup_ext_audio_title), stringResource(R.string.setup_ext_audio_detail), Modifier.riseIn(row++))
        InfoRow(Icons.Rounded.Widgets, stringResource(R.string.setup_ext_more_title), stringResource(R.string.setup_ext_more_detail), Modifier.riseIn(row++))
        InfoRow(Icons.Rounded.VerifiedUser, stringResource(R.string.setup_ext_safe_title), stringResource(R.string.setup_ext_safe_detail), Modifier.riseIn(row++))
        SetupSubtitle(stringResource(R.string.setup_ext_where), Modifier.padding(horizontal = 4.dp).riseIn(row++), size = 13.sp)
        Spacer(Modifier.height(4.dp))
        Column(Modifier.riseIn(row), horizontalAlignment = Alignment.CenterHorizontally) {
            SetupButton(stringResource(R.string.setup_ext_browse), onBrowse, Modifier.fillMaxWidth().height(56.dp))
            SetupLinkButton(stringResource(R.string.setup_ext_home), onLater, Modifier.fillMaxWidth().height(48.dp))
        }
    }
}

/** The step's title. */
@Composable
private fun SetupTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Color.White, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = modifier)
}

@Composable
private fun SetupSubtitle(text: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = 15.sp) {
    Text(text, color = Color.White.copy(alpha = 0.72f), fontSize = size, lineHeight = size * 1.4f, modifier = modifier)
}

/** The step's main button: the first launch's gradient pill. */
@Composable
private fun SetupButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth().height(56.dp)) {
    OnboardingCta(text, onClick, modifier)
}

@Composable
private fun SetupLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = Color.White.copy(alpha = 0.86f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A card of the setup: glass on the first launch's page. */
@Composable
private fun Modifier.setupCard(): Modifier = this.fillMaxWidth().onbGlass(RoundedCornerShape(22.dp))

/** A card of the intro and extensions steps: an accent icon, a title and a line of detail. */
@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.setupCard().padding(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(OnbOrange, OnbPink, OnbPurple))),
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp)) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            CardTitle(title)
            CardDetail(detail)
        }
    }
}

@Composable
private fun SetupRow(title: String, detail: String, on: Boolean, modifier: Modifier = Modifier, onEnable: () -> Unit) {
    val accent = Color(0xFFFFB07A)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.setupCard().padding(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            CardTitle(title)
            CardDetail(detail, lineHeight = 17.sp)
        }
        // Allowed: the button gives way to a green check that pops in.
        AnimatedContent(
            targetState = on,
            transitionSpec = { (scaleIn(spring(dampingRatio = 0.5f)) + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "allowed",
        ) { allowed ->
            if (allowed) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.padding(start = 12.dp).size(28.dp))
            else TextButton(onClick = onEnable) { Text(stringResource(R.string.setup_enable), color = accent, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CardDetail(text: String, lineHeight: androidx.compose.ui.unit.TextUnit = 18.sp) {
    Text(text, color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp, lineHeight = lineHeight)
}
