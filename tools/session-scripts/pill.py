p=r"O:\base\app\src\main\java\com\music\spotui\ui\immersive\player\ImmersivePlayer.kt"
s=open(p,encoding="utf-8",newline="").read()
nl="\r\n" if "\r\n" in s else "\n"
def rep(o,n):
    global s
    o=o.replace("\n",nl); n=n.replace("\n",nl)
    assert o in s, o[:60]
    s=s.replace(o,n,1)
rep("""            val lineRoom by androidx.compose.animation.core.animateDpAsState(
                if (lineOn && !canvasOn) LYRIC_LINE_ROOM else 0.dp, tween(300), label = "lineRoom",""",
"""            // The video switch, as the official app's: one place in every case, under the cover (or the
            // video) at the title's start (reported: it moved between the cover's corner and the title).
            val pillOn = (videoReady || videoOn) && panel == Panel.NONE
            val lineRoom by androidx.compose.animation.core.animateDpAsState(
                (if (lineOn && !canvasOn) LYRIC_LINE_ROOM else 0.dp) + (if (pillOn && !canvasOn) VIDEO_PILL_ROOM else 0.dp),
                tween(300), label = "lineRoom",""")
rep("""                    // ---- Switch to video / to audio: on the cover's lower corner, gone with a panel open ----
                    if ((videoReady || videoOn) && panel == Panel.NONE) Box(
                        Modifier
                            .offset {
                                // Inside the cover as it is drawn: it steps back while paused.
                                val inset = artSize() * ((1f - pauseScale.value) / 2f) + 12.dp
                                // Over a Canvas (no cover) it sits just above the title, at its start.
                                if (canvasOn) IntOffset(left.roundToPx(), (titleTop() - 46.dp).roundToPx())
                                else IntOffset((artStart() + inset).roundToPx(), (artTop() + artSize() - inset - 32.dp + 18.dp).roundToPx())
                            }""",
"""                    // ---- Switch to video / to audio: just above the title, at its start; gone with a panel open ----
                    if (pillOn) Box(
                        Modifier
                            .offset { IntOffset(left.roundToPx(), (titleTop() - VIDEO_PILL_ROOM).roundToPx()) }""")
rep("""                                // Over a Canvas the video pill sits just above the title; over a cover it is on the cover.
                                val y = if (canvasOn) titleTop() - (if (videoReady || videoOn) 46.dp else 0.dp) - 8.dp - 64.dp
                                else titleTop() - 64.dp - 2.dp""",
"""                                // Above the video switch when there is one, else just above the title.
                                val y = titleTop() - (if (pillOn) VIDEO_PILL_ROOM else 0.dp) - 64.dp - 2.dp""")
rep("/** The transport's size while the queue is open","/** Room above the title for the video switch (the pill and its gap). */\nprivate val VIDEO_PILL_ROOM = 44.dp\n\n/** The transport's size while the queue is open")
open(p,"w",encoding="utf-8",newline="").write(s); print("ok")
