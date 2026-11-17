p=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\player\ImmersivePlayer.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
o=("    PlayerScrubber(state = state, onSeek = actions.seekTo)"+nl+"    Spacer(Modifier.height(8.dp))"+nl+"    TransportRow("+nl)
assert o in s
n=("    // The queue open: the transport steps down (smaller, less room around it) so more of the list"+nl+
"    // shows; reported as the controls taking too much of the screen under the queue."+nl+
"    val small by androidx.compose.animation.core.animateFloatAsState(if (queueOpen) QUEUE_CONTROLS_SCALE else 1f, tween(PANEL_TRAVEL_MS), label = \"controlsSmall\")"+nl+
"    PlayerScrubber(state = state, onSeek = actions.seekTo)"+nl+"    Spacer(Modifier.height(8.dp * small))"+nl+
"    Box(Modifier.fillMaxWidth().layout { m, c ->"+nl+
"        val pl = m.measure(c)"+nl+
"        val h = (pl.height * small).roundToInt()"+nl+
"        layout(pl.width, h) { pl.placeWithLayer(0, (h - pl.height) / 2) { scaleX = small; scaleY = small } }"+nl+
"    }) {"+nl+"    TransportRow("+nl)
s=s.replace(o,n,1)
o2=("        onNext = actions.next,"+nl+"    )"+nl+"    Spacer(Modifier.height(if (showVolume) 12.dp else 4.dp))")
assert o2 in s
s=s.replace(o2,("        onNext = actions.next,"+nl+"    )"+nl+"    }"+nl+"    Spacer(Modifier.height(if (showVolume) 12.dp else 4.dp))"),1)
o3="    com.music.spotui.extensions.ExtensionPlayerButtons(extTrack)"+nl+"    Spacer(Modifier.height(18.dp))"
assert o3 in s
s=s.replace(o3,"    com.music.spotui.extensions.ExtensionPlayerButtons(extTrack)"+nl+"    Spacer(Modifier.height(18.dp - 12.dp * ((1f - small) / (1f - QUEUE_CONTROLS_SCALE))))",1)
o4="/** Room above the title for the lyric line"
s=s.replace(o4,"/** The transport's size while the queue is open (see PlayerControlsBlock). */"+nl+"private const val QUEUE_CONTROLS_SCALE = 0.72f"+nl+nl+o4,1)
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
