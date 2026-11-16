package com.music.spotui.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.myspoti.engine.R
import com.music.spotui.di.Haptics
import kotlinx.coroutines.launch

/** The last theme picked, offered first next time. */
private var lastTheme = ShareCard.Theme.GLASS

/**
 * The share preview: the card as it will be sent, three backgrounds to pick from (the blurred
 * cover, its colours as a gradient, one solid colour), and Share, which opens Android's share
 * sheet with its row of recent contacts. Both looks (drawn by the app's notice host).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardSheet() {
    val r = ShareCard.pending.value ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var theme by remember(r) { mutableStateOf(lastTheme) }
    val card by produceState<android.graphics.Bitmap?>(null, r, theme) { value = null; value = ShareCard.renderFor(context, r, theme) }
    val accent = com.music.spotui.ui.immersive.Immersive.Accent
    ModalBottomSheet(
        onDismissRequest = { ShareCard.pending.value = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF18181C),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.height(380.dp).aspectRatio(1080f / 1920f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2A30)),
                contentAlignment = Alignment.Center,
            ) {
                val b = card
                if (b == null) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                else Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ShareCard.Theme.GLASS to R.string.share_theme_glass,
                    ShareCard.Theme.GRADIENT to R.string.share_theme_gradient,
                    ShareCard.Theme.SOLID to R.string.share_theme_solid,
                ).forEach { (t, label) ->
                    val on = t == theme
                    Text(
                        stringResource(label), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) accent else Color.White.copy(alpha = 0.1f))
                            .clickable { Haptics.tick(); theme = t; lastTheme = t }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.share_card_send), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Color.White)
                    .clickable(enabled = card != null) {
                        val b = card ?: return@clickable
                        Haptics.confirm()
                        // Not this sheet's scope: closing the sheet would cancel the send.
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch { ShareCard.send(context, r, b) }
                        ShareCard.pending.value = null
                    }
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
